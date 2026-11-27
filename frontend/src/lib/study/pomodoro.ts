/**
 * Pomodoro state machine: focus blocks separated by short breaks, with a long break after every
 * `longBreakEvery` completed focus blocks.
 *
 * Time is tracked as a wall-clock deadline (`endsAt`), not by counting ticks, so a throttled background tab
 * or a slow interval never makes the timer drift: on every tick the remaining time is `endsAt - now`.
 * All functions are pure; the hook only supplies `now`.
 */

export type PomodoroPhase = 'focus' | 'shortBreak' | 'longBreak';

export interface PomodoroConfig {
  focusMinutes: number;
  shortBreakMinutes: number;
  longBreakMinutes: number;
  longBreakEvery: number;
}

export interface PomodoroState {
  phase: PomodoroPhase;
  running: boolean;
  /** Epoch ms when the running phase ends; null while paused or idle. */
  endsAt: number | null;
  /** Time left while paused or idle. */
  remainingMs: number;
  /** Focus blocks finished (skips do not count). */
  completedFocus: number;
}

export const DEFAULT_POMODORO: PomodoroConfig = {
  focusMinutes: 25,
  shortBreakMinutes: 5,
  longBreakMinutes: 15,
  longBreakEvery: 4,
};

export const PHASE_LABELS: Record<PomodoroPhase, string> = {
  focus: 'Focus',
  shortBreak: 'Short break',
  longBreak: 'Long break',
};

export function phaseDurationMs(phase: PomodoroPhase, config: PomodoroConfig): number {
  const minutes = phase === 'focus' ? config.focusMinutes
    : phase === 'shortBreak' ? config.shortBreakMinutes
      : config.longBreakMinutes;
  return minutes * 60_000;
}

export function initialPomodoro(config: PomodoroConfig = DEFAULT_POMODORO): PomodoroState {
  return { phase: 'focus', running: false, endsAt: null, remainingMs: phaseDurationMs('focus', config), completedFocus: 0 };
}

export function remainingMs(state: PomodoroState, now: number): number {
  return state.running && state.endsAt !== null ? Math.max(0, state.endsAt - now) : state.remainingMs;
}

export function start(state: PomodoroState, now: number): PomodoroState {
  if (state.running) return state;
  return { ...state, running: true, endsAt: now + state.remainingMs };
}

export function pause(state: PomodoroState, now: number): PomodoroState {
  if (!state.running) return state;
  return { ...state, running: false, endsAt: null, remainingMs: remainingMs(state, now) };
}

export function reset(state: PomodoroState, config: PomodoroConfig = DEFAULT_POMODORO): PomodoroState {
  return { ...state, running: false, endsAt: null, remainingMs: phaseDurationMs(state.phase, config) };
}

/** Moves to the next phase, stopped, so the student chooses when the break or next block begins. */
function advance(state: PomodoroState, countFocus: boolean, config: PomodoroConfig): PomodoroState {
  if (state.phase === 'focus') {
    const completedFocus = state.completedFocus + (countFocus ? 1 : 0);
    const phase: PomodoroPhase = countFocus && completedFocus % config.longBreakEvery === 0 ? 'longBreak' : 'shortBreak';
    return { phase, running: false, endsAt: null, remainingMs: phaseDurationMs(phase, config), completedFocus };
  }
  return { ...state, phase: 'focus', running: false, endsAt: null, remainingMs: phaseDurationMs('focus', config) };
}

/** Ends the current phase early. A skipped focus block is not counted towards the long break. */
export function skip(state: PomodoroState, config: PomodoroConfig = DEFAULT_POMODORO): PomodoroState {
  return advance(state, false, config);
}

/**
 * Called on every tick. Returns the same object while nothing changed, or the next phase once the deadline
 * passes, so callers can detect the transition with a reference check.
 */
export function tick(state: PomodoroState, now: number, config: PomodoroConfig = DEFAULT_POMODORO): PomodoroState {
  if (!state.running || state.endsAt === null || now < state.endsAt) return state;
  return advance(state, true, config);
}

/** "24:05". Rounds up, so the display reads 25:00 at the start and 00:00 only at the very end. */
export function formatClock(ms: number): string {
  const totalSeconds = Math.ceil(ms / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
}

/** Screen-reader announcement for the phase that just started. */
export function transitionMessage(next: PomodoroState, config: PomodoroConfig = DEFAULT_POMODORO): string {
  if (next.phase === 'focus') return `Break over. Ready for a ${config.focusMinutes}-minute focus block.`;
  const minutes = next.phase === 'longBreak' ? config.longBreakMinutes : config.shortBreakMinutes;
  return `Focus block done. Time for a ${minutes}-minute ${next.phase === 'longBreak' ? 'long ' : ''}break.`;
}
