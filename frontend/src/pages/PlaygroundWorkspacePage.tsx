import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import * as Tabs from '@radix-ui/react-tabs';
import { ArrowLeft, ExternalLink, Loader2, Lock, Trophy } from 'lucide-react';
import { toast } from 'sonner';
import { Avatar } from '../components/collab/Avatar';
import { DecisionLog } from '../components/playgrounds/DecisionLog';
import { KanbanBoard } from '../components/playgrounds/KanbanBoard';
import { MilestoneTimeline } from '../components/playgrounds/MilestoneTimeline';
import { errorMessage, useCollabSource } from '../hooks/useCollabSource';
import { pluralize } from '../lib/format';
import type { PlaygroundDetail } from '../types/collab';

const TAB_TRIGGER =
  'rounded-md px-3 py-1.5 text-xs font-medium text-slate-300 transition-colors duration-150 ease-out hover:text-white data-[state=active]:bg-emerald-600 data-[state=active]:text-white';

/** One team workspace: header (members, linked contests) and the Board / Milestones / Decisions tabs. */
export function PlaygroundWorkspacePage() {
  const { id } = useParams();
  const playgroundId = Number(id);
  const { source, demo } = useCollabSource();
  const [detail, setDetail] = useState<PlaygroundDetail | null>(null);
  const [missing, setMissing] = useState(false);

  const load = useCallback(async () => {
    try {
      setDetail(await source.getPlayground(playgroundId));
      setMissing(false);
    } catch (err) {
      setMissing(true);
      toast.error(errorMessage(err, 'Could not load this playground'));
    }
  }, [source, playgroundId]);

  useEffect(() => {
    void load();
  }, [load]);

  if (missing) {
    return (
      <div className="rounded-2xl border border-dashed border-slate-800 p-10 text-center">
        <p className="text-sm text-slate-300">This playground does not exist or is private.</p>
        <Link to="/playgrounds" className="mt-3 inline-block text-xs text-emerald-300 hover:underline">Back to my playgrounds</Link>
      </div>
    );
  }
  if (!detail) {
    return <Loader2 className="h-4 w-4 animate-spin text-slate-400" aria-label="Loading playground" />;
  }

  const canEdit = (detail.myRole === 'LEAD' || detail.myRole === 'CONTRIBUTOR') && detail.status !== 'ARCHIVED';

  return (
    <div className="space-y-5">
      <Link to="/playgrounds" className="inline-flex items-center gap-1.5 text-xs text-slate-400 hover:text-slate-200">
        <ArrowLeft className="h-3.5 w-3.5" aria-hidden="true" /> My playgrounds
      </Link>

      <header className="rounded-2xl border border-slate-800 bg-slate-900/60 p-5">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div className="min-w-0">
            <h2 className="break-words text-2xl font-bold tracking-tight text-white">{detail.title}</h2>
            {detail.problemStatement && (
              <p className="mt-1 max-w-[70ch] text-sm leading-relaxed text-slate-400">{detail.problemStatement}</p>
            )}
            <div className="mt-3 flex flex-wrap items-center gap-1.5">
              {detail.visibility === 'PRIVATE' && (
                <span className="inline-flex items-center gap-1 rounded-full border border-slate-700 px-2 py-0.5 text-[11px] text-slate-300">
                  <Lock className="h-3 w-3" aria-hidden="true" /> Private
                </span>
              )}
              <span className="rounded-full border border-slate-700 px-2 py-0.5 text-[11px] text-slate-300">
                {detail.status.replace('_', ' ').toLowerCase()}
              </span>
              {detail.tags.map((tag) => (
                <span key={tag} className="rounded-full bg-slate-800 px-2 py-0.5 text-[11px] text-slate-300">{tag}</span>
              ))}
              {detail.repoUrl && (
                <a href={detail.repoUrl} target="_blank" rel="noopener noreferrer" className="inline-flex items-center gap-1 text-[11px] text-emerald-300 hover:underline">
                  <ExternalLink className="h-3 w-3" aria-hidden="true" /> Repository
                </a>
              )}
            </div>
          </div>
          <div className="shrink-0 text-right">
            <p className="text-[11px] text-slate-500 tabular-nums">{pluralize(detail.members.length, 'member', 'members')}</p>
            <div className="mt-1 flex justify-end -space-x-1.5">
              {detail.members.slice(0, 6).map((m) => (
                <Avatar key={m.userId} name={`${m.displayName} (${m.role})`} className="ring-2 ring-slate-900" />
              ))}
              {detail.members.length > 6 && (
                <span className="inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-slate-800 text-[10px] text-slate-300 ring-2 ring-slate-900 tabular-nums">
                  +{detail.members.length - 6}
                </span>
              )}
            </div>
            <p className="mt-1 font-mono text-[10px] text-emerald-300">{detail.myRole ?? 'VISITOR'}</p>
          </div>
        </div>

        {detail.linkedOpportunities.length > 0 && (
          <div className="mt-4 flex flex-wrap items-center gap-2 border-t border-slate-800 pt-3">
            <Trophy className="h-3.5 w-3.5 shrink-0 text-amber-300" aria-hidden="true" />
            <span className="text-[11px] text-slate-500">Formed for</span>
            {detail.linkedOpportunities.map((opp) => (
              <Link key={opp.id} to={`/opportunities/${opp.id}`} className="max-w-full truncate text-xs text-emerald-300 hover:underline" title={opp.title}>
                {opp.title}
              </Link>
            ))}
          </div>
        )}
      </header>

      <Tabs.Root defaultValue="board">
        <Tabs.List aria-label="Playground sections" className="inline-flex rounded-lg border border-slate-800 bg-slate-900 p-1">
          <Tabs.Trigger value="board" className={TAB_TRIGGER}>
            Board <span className="tabular-nums text-[10px] opacity-70">({Object.values(detail.taskCounts).reduce((a, b) => a + b, 0)})</span>
          </Tabs.Trigger>
          <Tabs.Trigger value="milestones" className={TAB_TRIGGER}>Milestones</Tabs.Trigger>
          <Tabs.Trigger value="decisions" className={TAB_TRIGGER}>Decision log</Tabs.Trigger>
        </Tabs.List>
        <Tabs.Content value="board" className="mt-4 focus:outline-none">
          <KanbanBoard
            playgroundId={detail.id}
            members={detail.members}
            canEdit={canEdit}
            source={source}
            demo={demo}
          />
        </Tabs.Content>
        <Tabs.Content value="milestones" className="mt-4 focus:outline-none">
          <MilestoneTimeline playgroundId={detail.id} canEdit={canEdit} source={source} />
        </Tabs.Content>
        <Tabs.Content value="decisions" className="mt-4 focus:outline-none">
          <DecisionLog playgroundId={detail.id} canEdit={canEdit} source={source} />
        </Tabs.Content>
      </Tabs.Root>
    </div>
  );
}
