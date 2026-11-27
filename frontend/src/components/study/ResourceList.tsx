import { useEffect, useState, type FormEvent } from 'react';
import { ExternalLink, Library, Loader2, Plus } from 'lucide-react';
import { toast } from 'sonner';
import { errorMessage } from '../../hooks/useCollabSource';
import { formatSmartTimestamp } from '../../lib/format';
import type { StudySource } from '../../lib/study/studySource';
import { RESOURCE_CATEGORIES, type ResourceCategory, type StudyResource } from '../../types/study';

export const RESOURCE_LABELS: Record<ResourceCategory, string> = {
  NOTES: 'Notes',
  QUESTION_BANK: 'Question bank',
  SLIDES: 'Slides',
  RECORDING: 'Recording',
};

const RESOURCE_STYLES: Record<ResourceCategory, string> = {
  NOTES: 'bg-emerald-500/10 text-emerald-300',
  QUESTION_BANK: 'bg-amber-500/10 text-amber-300',
  SLIDES: 'bg-sky-500/10 text-sky-300',
  RECORDING: 'bg-violet-500/10 text-violet-300',
};

function hostOf(url: string): string {
  try {
    return new URL(url).hostname.replace(/^www\./, '');
  } catch {
    return url;
  }
}

/** Vetted external links shared by members; they open in a new tab without handing over window.opener. */
export function ResourceList({ circleId, source }: { circleId: number; source: StudySource }) {
  const [items, setItems] = useState<StudyResource[] | null>(null);
  const [title, setTitle] = useState('');
  const [url, setUrl] = useState('');
  const [category, setCategory] = useState<ResourceCategory>('NOTES');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    source.listResources(circleId)
      .then(setItems)
      .catch((err) => {
        setItems([]);
        toast.error(errorMessage(err, 'Could not load resources'));
      });
  }, [circleId, source]);

  const urlValid = /^https?:\/\/\S+\.\S+/.test(url.trim());

  const share = async (event: FormEvent) => {
    event.preventDefault();
    if (!title.trim() || !urlValid) return;
    setSaving(true);
    try {
      const created = await source.shareResource(circleId, { title: title.trim(), url: url.trim(), category });
      setItems((prev) => [created, ...(prev ?? [])]);
      setTitle('');
      setUrl('');
      toast.success('Resource shared');
    } catch (err) {
      toast.error(errorMessage(err, 'Could not share the resource'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <section aria-labelledby="resources-heading" className="rounded-xl border border-slate-800 bg-slate-900/60 p-4">
      <h3 id="resources-heading" className="flex items-center gap-1.5 text-sm font-semibold text-white">
        <Library className="h-4 w-4 text-emerald-300" aria-hidden="true" /> Shared resources
      </h3>

      {items === null ? (
        <Loader2 className="mt-3 h-4 w-4 animate-spin text-slate-400" aria-label="Loading resources" />
      ) : items.length === 0 ? (
        <p className="mt-3 rounded-lg border border-dashed border-slate-800 p-4 text-center text-xs text-slate-400">
          Nothing shared yet. Past papers and good notes help everyone.
        </p>
      ) : (
        <ul className="mt-3 divide-y divide-slate-800" aria-label="Resources">
          {items.map((r) => (
            <li key={r.id} className="flex items-start gap-3 py-2.5">
              <span className={`mt-0.5 shrink-0 rounded px-1.5 py-0.5 text-[10px] font-medium ${RESOURCE_STYLES[r.category]}`}>
                {RESOURCE_LABELS[r.category]}
              </span>
              <div className="min-w-0 flex-1">
                <a
                  href={r.url}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="inline-flex max-w-full items-center gap-1 text-sm text-slate-100 hover:text-emerald-300"
                >
                  <span className="truncate">{r.title}</span>
                  <ExternalLink className="h-3 w-3 shrink-0" aria-hidden="true" />
                </a>
                <p className="truncate text-[11px] text-slate-500 tabular-nums">
                  {hostOf(r.url)}
                  {r.contributor && ` · ${r.contributor.displayName}`} · {formatSmartTimestamp(r.createdAt)}
                </p>
              </div>
            </li>
          ))}
        </ul>
      )}

      <form onSubmit={share} className="mt-3 grid gap-2 sm:grid-cols-[1fr_1fr_auto_auto]">
        <input
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          maxLength={150}
          placeholder="Title"
          aria-label="Resource title"
          className="min-w-0 rounded-lg border border-slate-800 bg-slate-950 px-3 py-2 text-sm text-slate-100 placeholder:text-slate-500 focus:border-emerald-500/50 focus:outline-none"
        />
        <input
          value={url}
          onChange={(e) => setUrl(e.target.value)}
          maxLength={500}
          type="url"
          placeholder="https://"
          aria-label="Resource link"
          aria-invalid={url.trim() !== '' && !urlValid}
          className="min-w-0 rounded-lg border border-slate-800 bg-slate-950 px-3 py-2 text-sm text-slate-100 placeholder:text-slate-500 focus:border-emerald-500/50 focus:outline-none aria-[invalid=true]:border-rose-500/60"
        />
        <select
          aria-label="Resource type"
          value={category}
          onChange={(e) => setCategory(e.target.value as ResourceCategory)}
          className="rounded-lg border border-slate-800 bg-slate-950 px-2 py-2 text-xs text-slate-200"
        >
          {RESOURCE_CATEGORIES.map((c) => <option key={c} value={c}>{RESOURCE_LABELS[c]}</option>)}
        </select>
        <button
          type="submit"
          disabled={saving || !title.trim() || !urlValid}
          className="inline-flex min-w-[5.5rem] items-center justify-center gap-1 rounded-lg bg-emerald-600 px-3 py-2 text-xs font-medium text-white hover:bg-emerald-500 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {saving ? <Loader2 className="h-3.5 w-3.5 animate-spin" aria-hidden="true" /> : <Plus className="h-3.5 w-3.5" aria-hidden="true" />}
          Share
        </button>
      </form>
    </section>
  );
}
