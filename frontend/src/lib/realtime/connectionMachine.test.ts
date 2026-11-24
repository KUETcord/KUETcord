import { describe, expect, it } from 'vitest';
import { INITIAL_STATE, isResync, transition, type ConnectionEvent, type MachineState } from './connectionMachine';

function run(events: ConnectionEvent['type'][], from: MachineState = INITIAL_STATE): MachineState[] {
  const states: MachineState[] = [];
  let state = from;
  for (const type of events) {
    state = transition(state, { type } as ConnectionEvent);
    states.push(state);
  }
  return states;
}

describe('connection state machine', () => {
  it('walks DISCONNECTED -> CONNECTING -> CONNECTED -> RECONNECTING -> CONNECTED', () => {
    const states = run(['CONNECT', 'CONNECTED', 'CONNECTION_LOST', 'CONNECTED']);
    expect(states.map((s) => s.status)).toEqual(['CONNECTING', 'CONNECTED', 'RECONNECTING', 'CONNECTED']);
  });

  it('counts consecutive failed attempts for backoff and resets after success', () => {
    const states = run(['CONNECT', 'CONNECTION_LOST', 'CONNECTION_LOST', 'CONNECTION_LOST', 'CONNECTED', 'CONNECTION_LOST']);
    expect(states.map((s) => s.attempt)).toEqual([0, 1, 2, 3, 0, 0]);
  });

  it('flags a resync only when reconnecting after an earlier successful connection', () => {
    const [connecting, firstConnected, lost, reconnected] = run(['CONNECT', 'CONNECTED', 'CONNECTION_LOST', 'CONNECTED']);
    expect(isResync(connecting, firstConnected)).toBe(false);
    expect(isResync(lost, reconnected)).toBe(true);
  });

  it('DISCONNECT wins from any state and ignores invalid events', () => {
    for (const path of [['CONNECT'], ['CONNECT', 'CONNECTED'], ['CONNECT', 'CONNECTED', 'CONNECTION_LOST']] as const) {
      const last = run([...path, 'DISCONNECT']).at(-1)!;
      expect(last.status).toBe('DISCONNECTED');
    }
    expect(transition(INITIAL_STATE, { type: 'CONNECTED' })).toBe(INITIAL_STATE);
    expect(transition(INITIAL_STATE, { type: 'CONNECTION_LOST' })).toBe(INITIAL_STATE);
  });
});
