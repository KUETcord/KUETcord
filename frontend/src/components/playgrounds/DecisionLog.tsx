import { useCallback, useEffect, useId, useState, type FormEvent } from 'react';
import { ChevronDown, Loader2, Plus } from 'lucide-react';
import { toast } from 'sonner';
import { Avatar } from '../collab/Avatar';
import type { CollabSource } from '../../lib/collab/collabSource';
import { cn, formatExactDateTime, formatSmartTimestamp } from '../../lib/format';
import type { Decision } from '../../types/collab';

/**
 * Architectural decision log (Week 4, Day 5): an accordion, newest decision first. Each header shows the
 * decision and its author; expanding it reveals the rationale. Entries are append-only, matching the API.
 */
export function DecisionLog({ playgroundId, canEdit, source }: { playgroundId: number; canEdit: boolean; source: CollabSource }) {
  const [decisions, setDecisions] = useState<Decision[] | null>(null);
  const [openId, setOpenId] = useState<number | null>(null);

  const load = useCallback(async () => {
    setDecisions(await source.listDecisions(playgroundId));
  }, [source, playgroundId]);

  useEffect(() => {
    load().catch((err) => toast.error(err instanceof Error ? err.message : 'Could not load decisions'));
  }, [load]);

  if (!decisions) {
    return <Loader2 className="h-4 w-4 animate-spin text-slate-400" aria-label="Loading decisions" />;
  }

  return (
    <div className="space-y-4">
      {canEdit && (
        <DecisionForm
          onRecord={async (payload) => {
            const created = await source.recordDecision(playgroundId, payload);
            setDecisions((current) => [created, ...(current ?? [])]);
            setOpenId(created.id);
            toast.success('Decision recorded');
          }}
        />
      )}
      {decisions.length === 0 ? (
        <p className="rounded-lg border border-dashed border-slate-700 px-4 py-8 text-center text-xs text-slate-500">
          No decisions recorded yet. Log why you chose a stack or approach so the next teammate knows.
        </p>
      ) : (
        <ul className="divide-y divide-slate-800 overflow-hidden rounded-xl border border-slate-800">
          {decisions.map((decision) => (
            <DecisionItem
              key={decision.id}
              decision={decision}
              open={openId === decision.id}
              onToggle={() => setOpenId(openId === decision.id ? null : decision.id)}
            />
          ))}
        </ul>
      )}
    </div>
  );
}

function DecisionItem({ decision, open, onToggle }: { decision: Decision; open: boolean; onToggle: () => void }) {
  const panelId = useId();
  return (
    <li className="bg-slate-900/50">
      <button
        type="button"
        aria-expanded={open}
        aria-controls={panelId}
        onClick={onToggle}
        className="flex w-full items-center gap-3 px-4 py-3 text-left hover:bg-slate-900"
      >
        {decision.author && <Avatar name={decision.author.displayName} size="xs" />}
        <span className="min-w-0 flex-1">
          <span className="block break-words text-sm font-medium text-slate-100">{decision.decision}</span>
          <span className="block text-[11px] tabular-nums text-slate-500" title={formatExactDateTime(decision.createdAt)}>
            {decision.author?.displayName ?? 'Former member'} · {formatSmartTimestamp(decision.createdAt)}
          </span>
        </span>
        <ChevronDown
          className={cn('h-4 w-4 shrink-0 text-slate-500 transition-transform duration-150 ease-out', open && 'rotate-180')}
          aria-hidden="true"
        />
      </button>
      <div id={panelId} hidden={!open} className="px-4 pb-4 pl-[3.25rem]">
        <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Rationale</p>
        <p className="mt-1 max-w-[70ch] whitespace-pre-line text-sm leading-relaxed text-slate-300">
          {decision.rationale ?? 'No rationale recorded.'}
        </p>
      </div>
    </li>
  );
}

function DecisionForm({ onRecord }: { onRecord: (payload: { decision: string; rationale?: string }) => Promise<void> }) {
  const [decision, setDecision] = useState('');
  const [rationale, setRationale] = useState('');
  const [saving, setSaving] = useState(false);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (!decision.trim()) return;
    setSaving(true);
    try {
      await onRecord({ decision: decision.trim(), rationale: rationale.trim() || undefined });
      setDecision('');
      setRationale('');
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Could not record the decision');
    } finally {
      setSaving(false);
    }
  };

  const inputClass = 'w-full rounded-lg border border-slate-800 bg-slate-950 px-3 py-2 text-sm text-slate-100 placeholder:text-slate-500 focus:border-emerald-500/50 focus:outline-none';
  return (
    <form onSubmit={submit} className="space-y-2 rounded-xl border border-slate-800 bg-slate-900/60 p-3">
      <input value={decision} onChange={(e) => setDecision(e.target.value)} maxLength={2000} placeholder="Decision, e.g. Use PostgreSQL 16" aria-label="Decision" className={inputClass} />
      <textarea value={rationale} onChange={(e) => setRationale(e.target.value)} maxLength={5000} rows={2} placeholder="Why? (optional)" aria-label="Rationale" className={inputClass} />
      <button
        type="submit"
        disabled={saving || !decision.trim()}
        className="inline-flex min-w-[9rem] items-center justify-center gap-1.5 rounded-lg bg-emerald-600 px-3 py-2 text-xs font-medium text-white hover:bg-emerald-500 disabled:cursor-not-allowed disabled:opacity-50"
      >
        {saving ? <Loader2 className="h-3.5 w-3.5 animate-spin" aria-hidden="true" /> : <Plus className="h-3.5 w-3.5" aria-hidden="true" />}
        Record decision
      </button>
    </form>
  );
}
