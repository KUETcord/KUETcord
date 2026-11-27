import { Pause, Play, RotateCcw, SkipForward, Timer } from 'lucide-react';
import { usePomodoro } from '../../hooks/usePomodoro';
import { cn } from '../../lib/format';
import { DEFAULT_POMODORO, formatClock, PHASE_LABELS, phaseDurationMs, type PomodoroPhase } from '../../lib/study/pomodoro';

const PHASE_STYLES: Record<PomodoroPhase, { ring: string; pill: string }> = {
  focus: { ring: 'stroke-emerald-400', pill: 'bg-emerald-500/15 text-emerald-300 border-emerald-500/30' },
  shortBreak: { ring: 'stroke-sky-400', pill: 'bg-sky-500/15 text-sky-300 border-sky-500/30' },
  longBreak: { ring: 'stroke-violet-400', pill: 'bg-violet-500/15 text-violet-300 border-violet-500/30' },
};

const RADIUS = 52;
const CIRCUMFERENCE = 2 * Math.PI * RADIUS;

/**
 * 25/5 Pomodoro with a long break after every fourth focus block. Digits use tabular-nums so the clock
 * never jitters; the ring animates stroke-dashoffset only; phase changes are announced politely.
 */
export function PomodoroTimer() {
  const { state, remaining, announcement, start, pause, reset, skip } = usePomodoro();
  const total = phaseDurationMs(state.phase, DEFAULT_POMODORO);
  const progress = total === 0 ? 0 : 1 - remaining / total;
  const styles = PHASE_STYLES[state.phase];
  const untilLong = DEFAULT_POMODORO.longBreakEvery - (state.completedFocus % DEFAULT_POMODORO.longBreakEvery);

  return (
    <section aria-labelledby="pomodoro-heading" className="rounded-xl border border-slate-800 bg-slate-900/60 p-4">
      <div className="flex items-center justify-between gap-2">
        <h3 id="pomodoro-heading" className="flex items-center gap-1.5 text-sm font-semibold text-white">
          <Timer className="h-4 w-4 text-emerald-300" aria-hidden="true" /> Pomodoro
        </h3>
        <span className={cn('rounded-full border px-2 py-0.5 text-[11px] font-medium', styles.pill)} data-testid="pomodoro-phase">
          {PHASE_LABELS[state.phase]}
        </span>
      </div>

      <div className="mt-4 flex flex-col items-center">
        <div className="relative h-36 w-36">
          <svg viewBox="0 0 120 120" className="h-full w-full -rotate-90" aria-hidden="true">
            <circle cx="60" cy="60" r={RADIUS} className="fill-none stroke-slate-800" strokeWidth="8" />
            <circle
              cx="60"
              cy="60"
              r={RADIUS}
              className={cn('fill-none [transition:stroke-dashoffset_250ms_linear]', styles.ring)}
              strokeWidth="8"
              strokeLinecap="round"
              strokeDasharray={CIRCUMFERENCE}
              strokeDashoffset={CIRCUMFERENCE * (1 - progress)}
            />
          </svg>
          <div className="absolute inset-0 flex flex-col items-center justify-center">
            <span role="timer" aria-label={`${PHASE_LABELS[state.phase]} time remaining`} className="font-mono text-3xl font-semibold text-white tabular-nums">
              {formatClock(remaining)}
            </span>
            <span className="mt-0.5 text-[11px] text-slate-400">{state.running ? 'running' : 'paused'}</span>
          </div>
        </div>

        <p className="mt-3 text-xs text-slate-400 tabular-nums">
          {state.completedFocus} focus {state.completedFocus === 1 ? 'block' : 'blocks'} done
          {' · '}
          {untilLong === 1 ? 'long break after the next one' : `long break after ${untilLong} more`}
        </p>

        <div className="mt-3 flex items-center gap-2">
          <button
            type="button"
            onClick={state.running ? pause : start}
            className="inline-flex min-w-[6.5rem] items-center justify-center gap-1.5 rounded-lg bg-emerald-600 px-3 py-2 text-xs font-medium text-white hover:bg-emerald-500 focus-visible:outline focus-visible:outline-2 focus-visible:outline-emerald-400"
          >
            {state.running
              ? <><Pause className="h-3.5 w-3.5" aria-hidden="true" /> Pause</>
              : <><Play className="h-3.5 w-3.5" aria-hidden="true" /> Start</>}
          </button>
          <button
            type="button"
            onClick={reset}
            aria-label="Reset timer"
            className="rounded-lg border border-slate-700 p-2 text-slate-300 hover:bg-slate-800 hover:text-white"
          >
            <RotateCcw className="h-3.5 w-3.5" aria-hidden="true" />
          </button>
          <button
            type="button"
            onClick={skip}
            aria-label={state.phase === 'focus' ? 'Skip to break' : 'Skip to focus'}
            className="rounded-lg border border-slate-700 p-2 text-slate-300 hover:bg-slate-800 hover:text-white"
          >
            <SkipForward className="h-3.5 w-3.5" aria-hidden="true" />
          </button>
        </div>
      </div>

      <p aria-live="polite" className="sr-only">{announcement}</p>
    </section>
  );
}
