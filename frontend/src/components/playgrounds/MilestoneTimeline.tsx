import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { CheckCircle2, Circle, Loader2, Plus, RotateCcw } from 'lucide-react';
import { toast } from 'sonner';
import type { CollabSource } from '../../lib/collab/collabSource';
import { cn, formatExactDateTime, formatSmartTimestamp } from '../../lib/format';
import type { Milestone } from '../../types/collab';

/**
 * Project timeline (Week 4, Day 5): milestones soonest first on a vertical rail. Completing one calls the
 * PATCH that emits MILESTONE_REACHED for the rest of the team.
 */
export function MilestoneTimeline({
  playgroundId,
  canEdit,
  source,
}: {
  playgroundId: number;
  canEdit: boolean;
  source: CollabSource;
}) {
  const [milestones, setMilestones] = useState<Milestone[] | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);

  const load = useCallback(async () => {
    setMilestones(await source.listMilestones(playgroundId));
  }, [source, playgroundId]);

  useEffect(() => {
    load().catch((err) => toast.error(err instanceof Error ? err.message : 'Could not load milestones'));
  }, [load]);

  const toggle = async (milestone: Milestone) => {
    setBusyId(milestone.id);
    try {
      const next = milestone.status === 'COMPLETED' ? 'PENDING' : 'COMPLETED';
      const updated = await source.setMilestoneStatus(playgroundId, milestone.id, next);
      setMilestones((current) => current?.map((m) => (m.id === updated.id ? updated : m)) ?? current);
      toast.success(next === 'COMPLETED' ? `"${updated.title}" reached. The team has been notified.` : `Reopened "${updated.title}"`);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Could not update the milestone');
    } finally {
      setBusyId(null);
    }
  };

  if (!milestones) {
    return <Loader2 className="h-4 w-4 animate-spin text-slate-400" aria-label="Loading milestones" />;
  }

  return (
    <div className="space-y-4">
      {canEdit && (
        <MilestoneForm
          onCreate={async (payload) => {
            const created = await source.createMilestone(playgroundId, payload);
            setMilestones((current) =>
              [...(current ?? []), created].sort((a, b) => a.dueDate.localeCompare(b.dueDate) || a.id - b.id));
            toast.success(`Milestone "${created.title}" added`);
          }}
        />
      )}

      {milestones.length === 0 ? (
        <p className="rounded-lg border border-dashed border-slate-700 px-4 py-8 text-center text-xs text-slate-500">
          No milestones yet. Add the first checkpoint for your team.
        </p>
      ) : (
        <ol className="relative ml-2 space-y-4 border-l border-slate-800 pl-6" aria-label="Milestone timeline">
          {milestones.map((milestone) => {
            const done = milestone.status === 'COMPLETED';
            return (
              <li key={milestone.id} className="relative">
                <span className="absolute -left-[33px] top-0.5 rounded-full bg-slate-950 p-0.5" aria-hidden="true">
                  {done ? (
                    <CheckCircle2 className="h-4 w-4 text-emerald-400" />
                  ) : (
                    <Circle className={cn('h-4 w-4', milestone.overdue ? 'text-rose-400' : 'text-slate-500')} />
                  )}
                </span>
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div className="min-w-0">
                    <p className={cn('break-words text-sm font-medium', done ? 'text-slate-400 line-through' : 'text-slate-100')}>
                      {milestone.title}
                    </p>
                    <p className="mt-0.5 text-xs tabular-nums text-slate-500" title={formatExactDateTime(milestone.dueDate)}>
                      Due {formatSmartTimestamp(milestone.dueDate)}
                      {milestone.overdue && <span className="ml-2 rounded bg-rose-500/15 px-1.5 py-0.5 font-semibold text-rose-300">Overdue</span>}
                      {done && milestone.completedAt && <span className="ml-2 text-emerald-400">Reached {formatSmartTimestamp(milestone.completedAt)}</span>}
                    </p>
                    {milestone.description && (
                      <p className="mt-1 max-w-[65ch] text-xs leading-relaxed text-slate-400">{milestone.description}</p>
                    )}
                  </div>
                  {canEdit && (
                    <button
                      type="button"
                      onClick={() => void toggle(milestone)}
                      disabled={busyId === milestone.id}
                      className="inline-flex min-w-[8.5rem] shrink-0 items-center justify-center gap-1.5 rounded-lg border border-slate-700 px-2.5 py-1 text-xs text-slate-200 hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-50"
                    >
                      {busyId === milestone.id ? (
                        <Loader2 className="h-3.5 w-3.5 animate-spin" aria-hidden="true" />
                      ) : done ? (
                        <RotateCcw className="h-3.5 w-3.5" aria-hidden="true" />
                      ) : (
                        <CheckCircle2 className="h-3.5 w-3.5" aria-hidden="true" />
                      )}
                      {done ? 'Reopen' : 'Mark reached'}
                    </button>
                  )}
                </div>
              </li>
            );
          })}
        </ol>
      )}
    </div>
  );
}

function MilestoneForm({ onCreate }: { onCreate: (payload: { title: string; dueDate: string }) => Promise<void> }) {
  const [title, setTitle] = useState('');
  const [date, setDate] = useState('');
  const [saving, setSaving] = useState(false);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (!title.trim() || !date) return;
    setSaving(true);
    try {
      // <input type="date"> is local; send the end of that local day as a UTC instant.
      await onCreate({ title: title.trim(), dueDate: new Date(`${date}T23:59:00`).toISOString() });
      setTitle('');
      setDate('');
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Could not add the milestone');
    } finally {
      setSaving(false);
    }
  };

  return (
    <form onSubmit={submit} className="flex flex-wrap items-center gap-2">
      <input
        value={title}
        onChange={(e) => setTitle(e.target.value)}
        maxLength={100}
        placeholder="Milestone title"
        aria-label="Milestone title"
        className="min-w-[12rem] flex-1 rounded-lg border border-slate-800 bg-slate-950 px-3 py-2 text-sm text-slate-100 placeholder:text-slate-500 focus:border-emerald-500/50 focus:outline-none"
      />
      <input
        type="date"
        value={date}
        onChange={(e) => setDate(e.target.value)}
        aria-label="Milestone due date"
        className="rounded-lg border border-slate-800 bg-slate-950 px-2 py-2 text-xs text-slate-200 focus:border-emerald-500/50 focus:outline-none"
      />
      <button
        type="submit"
        disabled={saving || !title.trim() || !date}
        className="inline-flex min-w-[8rem] items-center justify-center gap-1.5 rounded-lg bg-emerald-600 px-3 py-2 text-xs font-medium text-white hover:bg-emerald-500 disabled:cursor-not-allowed disabled:opacity-50"
      >
        {saving ? <Loader2 className="h-3.5 w-3.5 animate-spin" aria-hidden="true" /> : <Plus className="h-3.5 w-3.5" aria-hidden="true" />}
        Add milestone
      </button>
    </form>
  );
}
