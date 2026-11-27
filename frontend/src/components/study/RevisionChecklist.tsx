import { useEffect, useState, type FormEvent } from 'react';
import { Check, ListChecks, Loader2, Plus } from 'lucide-react';
import { toast } from 'sonner';
import { errorMessage } from '../../hooks/useCollabSource';
import { cn } from '../../lib/format';
import type { StudySource } from '../../lib/study/studySource';
import type { RevisionItem } from '../../types/study';

interface Props {
  circleId: number;
  source: StudySource;
}

/**
 * Shared revision checklist. Ticking is optimistic: the box flips at once and rolls back (with a toast) only
 * if the server refuses, so ticking through a list feels instant.
 */
export function RevisionChecklist({ circleId, source }: Props) {
  const [items, setItems] = useState<RevisionItem[] | null>(null);
  const [title, setTitle] = useState('');
  const [adding, setAdding] = useState(false);

  useEffect(() => {
    source.listRevisionItems(circleId)
      .then(setItems)
      .catch((err) => {
        setItems([]);
        toast.error(errorMessage(err, 'Could not load the checklist'));
      });
  }, [circleId, source]);

  const toggle = async (item: RevisionItem) => {
    const done = !item.done;
    setItems((prev) => prev?.map((i) => (i.id === item.id ? { ...i, done } : i)) ?? prev);
    try {
      const saved = await source.setRevisionItemDone(circleId, item.id, done);
      setItems((prev) => prev?.map((i) => (i.id === saved.id ? saved : i)) ?? prev);
    } catch (err) {
      setItems((prev) => prev?.map((i) => (i.id === item.id ? item : i)) ?? prev);
      toast.error(errorMessage(err, 'Could not update the item'));
    }
  };

  const add = async (event: FormEvent) => {
    event.preventDefault();
    if (!title.trim()) return;
    setAdding(true);
    try {
      const created = await source.addRevisionItem(circleId, title);
      setItems((prev) => [...(prev ?? []), created]);
      setTitle('');
    } catch (err) {
      toast.error(errorMessage(err, 'Could not add the item'));
    } finally {
      setAdding(false);
    }
  };

  const doneCount = items?.filter((i) => i.done).length ?? 0;
  const total = items?.length ?? 0;
  const percent = total === 0 ? 0 : Math.round((doneCount / total) * 100);

  return (
    <section aria-labelledby="checklist-heading" className="rounded-xl border border-slate-800 bg-slate-900/60 p-4">
      <div className="flex items-center justify-between gap-2">
        <h3 id="checklist-heading" className="flex items-center gap-1.5 text-sm font-semibold text-white">
          <ListChecks className="h-4 w-4 text-emerald-300" aria-hidden="true" /> Revision checklist
        </h3>
        <span className="text-xs text-slate-400 tabular-nums">{doneCount} of {total} revised</span>
      </div>
      <div
        role="progressbar"
        aria-label="Revision progress"
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={percent}
        className="mt-2 h-1.5 overflow-hidden rounded-full bg-slate-800"
      >
        <div className="h-full origin-left rounded-full bg-emerald-500 [transition:transform_200ms_ease-out]" style={{ transform: `scaleX(${percent / 100})` }} />
      </div>

      {items === null ? (
        <Loader2 className="mt-3 h-4 w-4 animate-spin text-slate-400" aria-label="Loading checklist" />
      ) : items.length === 0 ? (
        <p className="mt-3 rounded-lg border border-dashed border-slate-800 p-4 text-center text-xs text-slate-400">
          No topics yet. Add the syllabus for the next CT, one topic per line.
        </p>
      ) : (
        <ul className="mt-3 space-y-1" aria-label="Revision topics">
          {items.map((item) => (
            <li key={item.id}>
              <label className="flex cursor-pointer items-start gap-2.5 rounded-lg px-2 py-1.5 hover:bg-slate-800/60">
                <input type="checkbox" checked={item.done} onChange={() => toggle(item)} className="peer sr-only" />
                <span
                  aria-hidden="true"
                  className={cn(
                    'mt-0.5 flex h-4 w-4 shrink-0 items-center justify-center rounded border peer-focus-visible:ring-2 peer-focus-visible:ring-emerald-400',
                    item.done ? 'border-emerald-500 bg-emerald-500 text-slate-950' : 'border-slate-600',
                  )}
                >
                  {item.done && <Check className="h-3 w-3" strokeWidth={3} />}
                </span>
                <span className="min-w-0 flex-1">
                  <span className={cn('block break-words text-sm', item.done ? 'text-slate-500 line-through' : 'text-slate-200')}>
                    {item.title}
                  </span>
                  {item.done && item.completedBy && (
                    <span className="block text-[11px] text-slate-500">ticked by {item.completedBy.displayName}</span>
                  )}
                </span>
              </label>
            </li>
          ))}
        </ul>
      )}

      <form onSubmit={add} className="mt-3 flex gap-2">
        <input
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          maxLength={200}
          placeholder="Add a topic, e.g. Floyd-Warshall"
          aria-label="New revision topic"
          className="min-w-0 flex-1 rounded-lg border border-slate-800 bg-slate-950 px-3 py-2 text-sm text-slate-100 placeholder:text-slate-500 focus:border-emerald-500/50 focus:outline-none"
        />
        <button
          type="submit"
          disabled={adding || !title.trim()}
          className="inline-flex min-w-[5.5rem] items-center justify-center gap-1 rounded-lg bg-emerald-600 px-3 py-2 text-xs font-medium text-white hover:bg-emerald-500 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {adding ? <Loader2 className="h-3.5 w-3.5 animate-spin" aria-hidden="true" /> : <Plus className="h-3.5 w-3.5" aria-hidden="true" />}
          Add
        </button>
      </form>
    </section>
  );
}
