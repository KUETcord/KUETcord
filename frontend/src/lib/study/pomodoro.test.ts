import { describe, expect, it } from 'vitest';
import {
  formatClock,
  initialPomodoro,
  pause,
  remainingMs,
  reset,
  skip,
  start,
  tick,
  transitionMessage,
} from './pomodoro';

const MIN = 60_000;

describe('Pomodoro state machine', () => {
  it('counts down from a wall-clock deadline, so pausing and resuming keeps the exact time left', () => {
    let s = start(initialPomodoro(), 0);
    expect(remainingMs(s, 10 * MIN)).toBe(15 * MIN);

    s = pause(s, 10 * MIN);
    // Time passing while paused changes nothing.
    expect(remainingMs(s, 99 * MIN)).toBe(15 * MIN);

    s = start(s, 100 * MIN);
    expect(s.endsAt).toBe(115 * MIN);
  });

  it('does not drift when ticks are late: one tick long after the deadline still ends the block once', () => {
    const running = start(initialPomodoro(), 0);
    expect(tick(running, 25 * MIN - 1)).toBe(running);

    const next = tick(running, 40 * MIN); // a throttled background tab woke up 15 minutes late
    expect(next).toMatchObject({ phase: 'shortBreak', running: false, completedFocus: 1, remainingMs: 5 * MIN });
    expect(transitionMessage(next)).toBe('Focus block done. Time for a 5-minute break.');
  });

  it('gives a long break after every fourth completed focus block', () => {
    let s = initialPomodoro();
    const phases: string[] = [];
    let now = 0;
    for (let i = 0; i < 8; i += 1) {
      s = start(s, now);
      now = s.endsAt!;
      s = tick(s, now);
      phases.push(s.phase);
    }
    expect(phases).toEqual(['shortBreak', 'focus', 'shortBreak', 'focus', 'shortBreak', 'focus', 'longBreak', 'focus']);
    expect(s.completedFocus).toBe(4);
  });

  it('skipping a focus block does not count it, and reset restores the full phase length', () => {
    const skipped = skip(start(initialPomodoro(), 0));
    expect(skipped).toMatchObject({ phase: 'shortBreak', completedFocus: 0, running: false });

    const paused = pause(start(skipped, 0), 2 * MIN);
    expect(reset(paused).remainingMs).toBe(5 * MIN);
  });

  it('formats as mm:ss and rounds up so the last second shows 00:01, not 00:00', () => {
    expect(formatClock(25 * MIN)).toBe('25:00');
    expect(formatClock(24 * MIN + 5_000)).toBe('24:05');
    expect(formatClock(1)).toBe('00:01');
    expect(formatClock(0)).toBe('00:00');
  });
});
