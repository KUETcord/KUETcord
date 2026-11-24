import { Client, type IMessage, type StompSubscription } from '@stomp/stompjs';
import { computeBackoffDelay } from './backoff';
import { INITIAL_STATE, isResync, transition, type ConnectionEvent, type ConnectionStatus, type MachineState } from './connectionMachine';

export const HEARTBEAT_MS = 10_000;
export const RECEIPT_TIMEOUT_MS = 10_000;

type StatusListener = (status: ConnectionStatus) => void;
type Listener = () => void;

interface Registration {
  destination: string;
  callback: (body: unknown) => void;
  active: StompSubscription | null;
}

export interface RealtimeClientOptions {
  url: string;
  getToken: () => string | null;
  random?: () => number;
  /** Injection point for tests. */
  createClient?: (config: ConstructorParameters<typeof Client>[0]) => Client;
}

/**
 * STOMP client with our own reconnect policy (stompjs auto-reconnect disabled):
 * exponential backoff + jitter, explicit state machine, durable subscriptions that are
 * re-established after every reconnect, and a "reconnected" signal for /sync gap catch-up.
 */
export class RealtimeClient {
  private machine: MachineState = INITIAL_STATE;
  private client: Client | null = null;
  private retryTimer: ReturnType<typeof setTimeout> | null = null;
  private nextRegistrationId = 0;
  private readonly registrations = new Map<number, Registration>();
  private readonly statusListeners = new Set<StatusListener>();
  private readonly reconnectListeners = new Set<Listener>();
  private readonly fatalListeners = new Set<(reason: string) => void>();

  constructor(private readonly options: RealtimeClientOptions) {}

  get status(): ConnectionStatus {
    return this.machine.status;
  }

  start(): void {
    if (this.machine.status !== 'DISCONNECTED') return;
    this.dispatch({ type: 'CONNECT' });
    this.open();
  }

  stop(): void {
    this.dispatch({ type: 'DISCONNECT' });
    this.clearRetry();
    this.closeCurrentClient();
  }

  /** Durable subscription: survives reconnects. Returns an unsubscribe function. */
  subscribe<T>(destination: string, callback: (body: T) => void): () => void {
    const id = this.nextRegistrationId++;
    const registration: Registration = { destination, callback: callback as (body: unknown) => void, active: null };
    this.registrations.set(id, registration);
    if (this.client?.connected) {
      registration.active = this.attach(this.client, registration);
    }
    return () => {
      registration.active?.unsubscribe();
      this.registrations.delete(id);
    };
  }

  /**
   * Sends a frame. With `receipt`, resolves only when the server's RECEIPT arrives (i.e. after
   * the message is durably committed) and rejects on disconnect or after RECEIPT_TIMEOUT_MS.
   */
  publish(destination: string, body: unknown, receipt?: string): Promise<void> {
    const client = this.client;
    if (!client || !client.connected) {
      return Promise.reject(new Error('Not connected'));
    }
    if (!receipt) {
      client.publish({ destination, body: JSON.stringify(body) });
      return Promise.resolve();
    }
    return new Promise<void>((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error('Receipt timeout')), RECEIPT_TIMEOUT_MS);
      client.watchForReceipt(receipt, () => {
        clearTimeout(timer);
        resolve();
      });
      client.publish({ destination, body: JSON.stringify(body), headers: { receipt } });
    });
  }

  onStatus(listener: StatusListener): () => void {
    this.statusListeners.add(listener);
    return () => this.statusListeners.delete(listener);
  }

  /** Fires after a *re*connect so consumers can run /sync for the gap. */
  onReconnect(listener: Listener): () => void {
    this.reconnectListeners.add(listener);
    return () => this.reconnectListeners.delete(listener);
  }

  /** Fires when the server rejects our credentials; retrying would be pointless. */
  onFatal(listener: (reason: string) => void): () => void {
    this.fatalListeners.add(listener);
    return () => this.fatalListeners.delete(listener);
  }

  // ------------------------------------------------------------------ internals

  private open(): void {
    const token = this.options.getToken();
    if (!token) {
      this.stop();
      return;
    }
    const config = {
      brokerURL: this.options.url,
      connectHeaders: { Authorization: `Bearer ${token}` },
      heartbeatIncoming: HEARTBEAT_MS,
      heartbeatOutgoing: HEARTBEAT_MS,
      reconnectDelay: 0,
    };
    const client = this.options.createClient ? this.options.createClient(config) : new Client(config);
    this.client = client;

    client.onConnect = () => {
      if (this.client !== client) return;
      for (const registration of this.registrations.values()) {
        registration.active = this.attach(client, registration);
      }
      const previous = this.machine;
      this.dispatch({ type: 'CONNECTED' });
      if (isResync(previous, this.machine)) {
        this.reconnectListeners.forEach((listener) => listener());
      }
    };
    client.onStompError = (frame) => {
      if (this.client !== client) return;
      const message = frame.headers.message ?? '';
      if (message.startsWith('UNAUTHORIZED')) {
        this.fatalListeners.forEach((listener) => listener(message));
        this.stop();
        return;
      }
      this.handleConnectionLost(client);
    };
    client.onWebSocketClose = () => this.handleConnectionLost(client);
    client.onWebSocketError = () => this.handleConnectionLost(client);
    client.activate();
  }

  private handleConnectionLost(client: Client): void {
    if (this.client !== client || this.machine.status === 'DISCONNECTED') return;
    this.closeCurrentClient();
    this.dispatch({ type: 'CONNECTION_LOST' });
    const delay = computeBackoffDelay(this.machine.attempt, this.options.random);
    this.clearRetry();
    this.retryTimer = setTimeout(() => {
      this.retryTimer = null;
      if (this.machine.status === 'RECONNECTING') this.open();
    }, delay);
  }

  private attach(client: Client, registration: Registration): StompSubscription {
    return client.subscribe(registration.destination, (message: IMessage) => {
      registration.callback(message.body ? JSON.parse(message.body) : null);
    });
  }

  private closeCurrentClient(): void {
    const client = this.client;
    this.client = null; // detach first so its close callback is ignored
    for (const registration of this.registrations.values()) registration.active = null;
    if (client) void client.deactivate();
  }

  private clearRetry(): void {
    if (this.retryTimer) {
      clearTimeout(this.retryTimer);
      this.retryTimer = null;
    }
  }

  private dispatch(event: ConnectionEvent): void {
    const next = transition(this.machine, event);
    if (next === this.machine) return;
    const changed = next.status !== this.machine.status;
    this.machine = next;
    if (changed) this.statusListeners.forEach((listener) => listener(next.status));
  }
}

export function defaultWebSocketUrl(): string {
  const configured = import.meta.env.VITE_WS_URL as string | undefined;
  if (configured) return configured;
  const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws';
  return `${protocol}://${window.location.host}/ws/websocket`;
}
