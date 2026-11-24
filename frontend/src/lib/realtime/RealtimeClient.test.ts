import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Client } from '@stomp/stompjs';
import { RealtimeClient } from './RealtimeClient';

/** Minimal stand-in for @stomp/stompjs Client: lets tests drive connect/close/error events. */
class FakeStompClient {
  connected = false;
  onConnect: () => void = () => undefined;
  onStompError: (frame: { headers: Record<string, string> }) => void = () => undefined;
  onWebSocketClose: () => void = () => undefined;
  onWebSocketError: () => void = () => undefined;
  readonly subscriptions: string[] = [];
  readonly published: { destination: string; body: string; headers?: Record<string, string> }[] = [];
  private receiptCallbacks = new Map<string, () => void>();
  activated = false;
  deactivated = false;

  constructor(readonly config: { connectHeaders: Record<string, string> }) {}

  activate() {
    this.activated = true;
  }
  deactivate() {
    this.deactivated = true;
    this.connected = false;
    return Promise.resolve();
  }
  subscribe(destination: string) {
    this.subscriptions.push(destination);
    return { unsubscribe: () => undefined };
  }
  publish(frame: { destination: string; body: string; headers?: Record<string, string> }) {
    this.published.push(frame);
  }
  watchForReceipt(id: string, callback: () => void) {
    this.receiptCallbacks.set(id, callback);
  }
  // --- test drivers
  serverConnected() {
    this.connected = true;
    this.onConnect();
  }
  serverDropped() {
    this.connected = false;
    this.onWebSocketClose();
  }
  serverReceipt(id: string) {
    this.receiptCallbacks.get(id)?.();
  }
}

describe('RealtimeClient reconnect policy', () => {
  let created: FakeStompClient[];
  let client: RealtimeClient;

  beforeEach(() => {
    vi.useFakeTimers();
    created = [];
    client = new RealtimeClient({
      url: 'ws://test/ws/websocket',
      getToken: () => 'jwt-token',
      random: () => 0.5,
      createClient: (config) => {
        const fake = new FakeStompClient(config as unknown as { connectHeaders: Record<string, string> });
        created.push(fake);
        return fake as unknown as Client;
      },
    });
  });

  afterEach(() => {
    client.stop();
    vi.useRealTimers();
  });

  it('authenticates CONNECT with the bearer token and re-subscribes durable subscriptions after reconnect', () => {
    client.subscribe('/topic/conversations/7', () => undefined);
    client.start();
    expect(created[0].config.connectHeaders.Authorization).toBe('Bearer jwt-token');

    created[0].serverConnected();
    expect(created[0].subscriptions).toEqual(['/topic/conversations/7']);

    created[0].serverDropped();
    expect(client.status).toBe('RECONNECTING');
    vi.advanceTimersByTime(1_000);
    created[1].serverConnected();
    expect(created[1].subscriptions).toEqual(['/topic/conversations/7']);
  });

  it('retries with exponential backoff and signals onReconnect only for re-connections', () => {
    const reconnects = vi.fn();
    const statuses: string[] = [];
    client.onReconnect(reconnects);
    client.onStatus((s) => statuses.push(s));

    client.start();
    created[0].serverConnected();
    expect(reconnects).not.toHaveBeenCalled();

    created[0].serverDropped(); // attempt 0 -> 1 s
    vi.advanceTimersByTime(999);
    expect(created).toHaveLength(1);
    vi.advanceTimersByTime(1);
    expect(created).toHaveLength(2);

    created[1].serverDropped(); // attempt 1 -> 2 s
    vi.advanceTimersByTime(1_999);
    expect(created).toHaveLength(2);
    vi.advanceTimersByTime(1);
    expect(created).toHaveLength(3);

    created[2].serverConnected();
    expect(reconnects).toHaveBeenCalledTimes(1);
    expect(statuses).toEqual(['CONNECTING', 'CONNECTED', 'RECONNECTING', 'CONNECTED']);
  });

  it('stops retrying on UNAUTHORIZED and ignores late events from a closed socket', () => {
    const fatal = vi.fn();
    client.onFatal(fatal);
    client.start();
    created[0].onStompError({ headers: { message: 'UNAUTHORIZED: Invalid or expired access token' } });

    expect(fatal).toHaveBeenCalledOnce();
    expect(client.status).toBe('DISCONNECTED');
    created[0].onWebSocketClose();
    vi.advanceTimersByTime(60_000);
    expect(created).toHaveLength(1);
  });

  it('resolves publish only when the RECEIPT arrives and rejects while offline', async () => {
    await expect(client.publish('/app/x', {}, 'msg-1')).rejects.toThrow('Not connected');

    client.start();
    created[0].serverConnected();
    let settled = false;
    const pending = client.publish('/app/conversations/7/messages', { content: 'hi' }, 'msg-2').then(() => {
      settled = true;
    });
    await Promise.resolve();
    expect(settled).toBe(false);
    expect(created[0].published[0].headers).toEqual({ receipt: 'msg-2' });

    created[0].serverReceipt('msg-2');
    await pending;
    expect(settled).toBe(true);
  });
});
