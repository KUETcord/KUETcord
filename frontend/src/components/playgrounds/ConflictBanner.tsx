import { AlertTriangle, Loader2, RefreshCw } from 'lucide-react';

/**
 * Non-modal 409 banner (DESIGN_GUIDELINES section 7, Member C): the board stays usable, and the primary
 * action reloads the teammate's version instead of silently overwriting it.
 */
export function ConflictBanner({
  message,
  reloading,
  onReload,
}: {
  message: string;
  reloading: boolean;
  onReload: () => void;
}) {
  return (
    <div
      role="alert"
      className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-amber-500/40 bg-amber-500/10 px-4 py-3"
    >
      <div className="flex min-w-0 items-start gap-2.5">
        <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-amber-300" aria-hidden="true" />
        <div className="min-w-0">
          <p className="text-sm font-semibold text-amber-100">A teammate changed this card first</p>
          <p className="text-xs text-amber-200/80">{message} Your change was not applied.</p>
        </div>
      </div>
      <button
        type="button"
        onClick={onReload}
        disabled={reloading}
        className="inline-flex min-w-[11rem] shrink-0 items-center justify-center gap-2 rounded-lg bg-amber-500 px-3 py-1.5 text-xs font-semibold text-slate-950 hover:bg-amber-400 disabled:cursor-not-allowed disabled:opacity-50"
      >
        {reloading ? <Loader2 className="h-3.5 w-3.5 animate-spin" aria-hidden="true" /> : <RefreshCw className="h-3.5 w-3.5" aria-hidden="true" />}
        Load Latest Changes
      </button>
    </div>
  );
}
