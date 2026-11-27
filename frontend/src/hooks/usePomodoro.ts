import { useCallback, useEffect, useRef, useState } from 'react';
import {
  DEFAULT_POMODORO,
  initialPomodoro,
  pause as pauseState,
  remainingMs,
  reset as resetState,
  skip as skipState,
  start as startState,
  tick,
  transitionMessage,
  type PomodoroConfig,
  type PomodoroState,
} from '../lib/study/pomodoro';

/** How often the display refreshes while running. Accuracy comes from the deadline, not from this. */
const TICK_MS = 250;

export interface PomodoroHandle {
  state: PomodoroState;
  remaining: number;
  /** Last phase-change announcement, for an aria-live region. */
  announcement: string;
  start: () => void;
  pause: () => void;
  reset: () => void;
  skip: () => void;
}

export function usePomodoro(config: PomodoroConfig = DEFAULT_POMODORO): PomodoroHandle {
  const [state, setState] = useState<PomodoroState>(() => initialPomodoro(config));
  const [now, setNow] = useState(() => Date.now());
  const [announcement, setAnnouncement] = useState('');
  const stateRef = useRef(state);
  stateRef.current = state;

  useEffect(() => {
    if (!state.running) return undefined;
    const id = window.setInterval(() => {
      const current = Date.now();
      setNow(current);
      const next = tick(stateRef.current, current, config);
      if (next !== stateRef.current) {
        setState(next);
        setAnnouncement(transitionMessage(next, config));
      }
    }, TICK_MS);
    return () => window.clearInterval(id);
  }, [state.running, config]);

  const start = useCallback(() => {
    const current = Date.now();
    setNow(current);
    setState((s) => startState(s, current));
  }, []);
  const pause = useCallback(() => setState((s) => pauseState(s, Date.now())), []);
  const reset = useCallback(() => setState((s) => resetState(s, config)), [config]);
  const skip = useCallback(() => {
    const next = skipState(stateRef.current, config);
    setState(next);
    setAnnouncement(transitionMessage(next, config));
  }, [config]);

  return { state, remaining: remainingMs(state, now), announcement, start, pause, reset, skip };
}
