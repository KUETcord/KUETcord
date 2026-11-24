/**
 * Explicit client connection state machine (pure, no I/O).
 *
 *   DISCONNECTED --CONNECT--> CONNECTING --CONNECTED--> CONNECTED
 *        ^                        |                         |
 *        |                 CONNECTION_LOST           CONNECTION_LOST
 *    DISCONNECT                   v                         v
 *     (any state)            RECONNECTING <----------- RECONNECTING
 *                              |    ^  CONNECTION_LOST (attempt + 1)
 *                         CONNECTED |
 *                              v
 *                          CONNECTED (resync = true)
 *
 * `attempt` feeds the backoff delay; `hasConnectedBefore` tells consumers that a CONNECTED
 * transition is a *re*connect, so they must run /sync for the gap instead of a full reload.
 */
export type ConnectionStatus = 'DISCONNECTED' | 'CONNECTING' | 'CONNECTED' | 'RECONNECTING';

export type ConnectionEvent =
  | { type: 'CONNECT' }
  | { type: 'CONNECTED' }
  | { type: 'CONNECTION_LOST' }
  | { type: 'DISCONNECT' };

export interface MachineState {
  status: ConnectionStatus;
  /** Consecutive failed attempts since the last successful connection. */
  attempt: number;
  hasConnectedBefore: boolean;
}

export const INITIAL_STATE: MachineState = {
  status: 'DISCONNECTED',
  attempt: 0,
  hasConnectedBefore: false,
};

export function transition(state: MachineState, event: ConnectionEvent): MachineState {
  switch (event.type) {
    case 'DISCONNECT':
      return { ...INITIAL_STATE, hasConnectedBefore: state.hasConnectedBefore };

    case 'CONNECT':
      return state.status === 'DISCONNECTED' ? { ...state, status: 'CONNECTING', attempt: 0 } : state;

    case 'CONNECTED':
      if (state.status === 'CONNECTING' || state.status === 'RECONNECTING') {
        return { status: 'CONNECTED', attempt: 0, hasConnectedBefore: true };
      }
      return state;

    case 'CONNECTION_LOST':
      if (state.status === 'CONNECTED') {
        return { ...state, status: 'RECONNECTING', attempt: 0 };
      }
      if (state.status === 'CONNECTING' || state.status === 'RECONNECTING') {
        return { ...state, status: 'RECONNECTING', attempt: state.attempt + 1 };
      }
      return state;

    default:
      return state;
  }
}

/** True when this CONNECTED transition follows an earlier connection (gap catch-up needed). */
export function isResync(previous: MachineState, next: MachineState): boolean {
  return next.status === 'CONNECTED' && previous.status !== 'CONNECTED' && previous.hasConnectedBefore;
}
