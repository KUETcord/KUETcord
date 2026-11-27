import { useEffect, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Briefcase, Loader2, Lock, Plus } from 'lucide-react';
import { toast } from 'sonner';
import { errorMessage, useCollabSource } from '../hooks/useCollabSource';
import { formatSmartTimestamp, pluralize } from '../lib/format';
import type { PlaygroundSummary, PlaygroundVisibility } from '../types/collab';

/** "My playgrounds" plus a quick create form. Teams can also be started from a contest page. */
export function PlaygroundsPage() {
  const { source } = useCollabSource();
  const navigate = useNavigate();
  const [items, setItems] = useState<PlaygroundSummary[] | null>(null);
  const [title, setTitle] = useState('');
  const [visibility, setVisibility] = useState<PlaygroundVisibility>('PUBLIC');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    source.listPlaygrounds()
      .then(setItems)
      .catch((err) => {
        setItems([]);
        toast.error(errorMessage(err, 'Could not load your playgrounds'));
      });
  }, [source]);

  const create = async (event: FormEvent) => {
    event.preventDefault();
    if (!title.trim()) return;
    setSaving(true);
    try {
      const created = await source.createPlayground({ title: title.trim(), visibility });
      toast.success(`Created "${created.title}"`);
      navigate(`/playgrounds/${created.id}`);
    } catch (err) {
      toast.error(errorMessage(err, 'Could not create the playground'));
      setSaving(false);
    }
  };

  return (
    <div className="space-y-5">
      <div>
        <h2 className="text-2xl font-bold tracking-tight text-white">Project Playgrounds</h2>
        <p className="mt-1 max-w-[65ch] text-sm text-slate-400">
          Team workspaces with a shared Kanban board, milestones and a decision log.{' '}
          <Link to="/opportunities" className="text-emerald-300 hover:underline">Start one from a contest</Link>.
        </p>
      </div>

      <form onSubmit={create} className="flex flex-wrap items-center gap-2 rounded-xl border border-slate-800 bg-slate-900/60 p-3">
        <input
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          maxLength={150}
          placeholder="New playground title"
          aria-label="New playground title"
          className="min-w-[14rem] flex-1 rounded-lg border border-slate-800 bg-slate-950 px-3 py-2 text-sm text-slate-100 placeholder:text-slate-500 focus:border-emerald-500/50 focus:outline-none"
        />
        <select
          aria-label="Visibility"
          value={visibility}
          onChange={(e) => setVisibility(e.target.value as PlaygroundVisibility)}
          className="rounded-lg border border-slate-800 bg-slate-950 px-2 py-2 text-xs text-slate-200"
        >
          <option value="PUBLIC">Public</option>
          <option value="PRIVATE">Private</option>
        </select>
        <button
          type="submit"
          disabled={saving || !title.trim()}
          className="inline-flex min-w-[9rem] items-center justify-center gap-1.5 rounded-lg bg-emerald-600 px-3 py-2 text-xs font-medium text-white hover:bg-emerald-500 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {saving ? <Loader2 className="h-3.5 w-3.5 animate-spin" aria-hidden="true" /> : <Plus className="h-3.5 w-3.5" aria-hidden="true" />}
          Create playground
        </button>
      </form>

      {items === null ? (
        <Loader2 className="h-4 w-4 animate-spin text-slate-400" aria-label="Loading playgrounds" />
      ) : items.length === 0 ? (
        <div className="rounded-2xl border border-dashed border-slate-800 p-10 text-center">
          <Briefcase className="mx-auto mb-3 h-8 w-8 text-slate-500" aria-hidden="true" />
          <p className="text-sm text-slate-300">You are not in any playground yet.</p>
        </div>
      ) : (
        <ul className="grid gap-3 md:grid-cols-2">
          {items.map((pg) => (
            <li key={pg.id}>
              <Link to={`/playgrounds/${pg.id}`} className="block rounded-xl border border-slate-800 bg-slate-900/60 p-4 hover:border-slate-700">
                <div className="flex items-start justify-between gap-3">
                  <h3 className="min-w-0 truncate text-sm font-semibold text-white" title={pg.title}>{pg.title}</h3>
                  <span className="shrink-0 rounded bg-slate-800 px-1.5 py-0.5 font-mono text-[10px] text-emerald-300">{pg.myRole}</span>
                </div>
                <p className="mt-2 flex items-center gap-2 text-[11px] text-slate-400 tabular-nums">
                  {pg.visibility === 'PRIVATE' && <Lock className="h-3 w-3 shrink-0" aria-label="Private" />}
                  <span>{pg.status.replace('_', ' ').toLowerCase()}</span>
                  <span>·</span>
                  <span>{pluralize(pg.memberCount, 'member', 'members')}</span>
                  <span>·</span>
                  <span>updated {formatSmartTimestamp(pg.updatedAt)}</span>
                </p>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
