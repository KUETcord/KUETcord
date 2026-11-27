import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft, Briefcase, CalendarDays, ExternalLink, Loader2, Rocket, Users } from 'lucide-react';
import { toast } from 'sonner';
import { BookmarkButton } from '../components/opportunities/BookmarkButton';
import { CategoryPill, DeadlinePill, VerifiedPill } from '../components/opportunities/OpportunityBadges';
import { errorMessage, useCollabSource } from '../hooks/useCollabSource';
import { MODE_LABELS } from '../lib/collab/kanban';
import { formatExactDateTime, pluralize } from '../lib/format';
import type { LinkedPlayground, OpportunityDetail } from '../types/collab';

/**
 * Opportunity detail (Week 4, Day 2): eligibility, dates, the official source link, bookmarking, the teams
 * already formed, and "Start Project Playground", which creates a team workspace linked to this contest.
 */
export function OpportunityDetailPage() {
  const { id } = useParams();
  const opportunityId = Number(id);
  const navigate = useNavigate();
  const { source } = useCollabSource();
  const [detail, setDetail] = useState<OpportunityDetail | null>(null);
  const [teams, setTeams] = useState<LinkedPlayground[]>([]);
  const [notFound, setNotFound] = useState(false);
  const [starting, setStarting] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setNotFound(false);
    Promise.all([source.getOpportunity(opportunityId), source.listTeams(opportunityId)])
      .then(([loaded, linked]) => {
        if (cancelled) return;
        setDetail(loaded);
        setTeams(linked);
      })
      .catch((err) => {
        if (cancelled) return;
        setNotFound(true);
        toast.error(errorMessage(err, 'Could not load this opportunity'));
      });
    return () => {
      cancelled = true;
    };
  }, [source, opportunityId]);

  const toggleBookmark = async () => {
    if (!detail) return;
    const previous = detail;
    const next = !detail.bookmarked;
    setDetail({ ...detail, bookmarked: next, bookmarkCount: detail.bookmarkCount + (next ? 1 : -1) });
    try {
      const status = await source.setBookmark(detail.id, next);
      setDetail((current) => (current ? { ...current, bookmarked: status.bookmarked, bookmarkCount: status.bookmarkCount } : current));
    } catch (err) {
      setDetail(previous);
      toast.error(errorMessage(err, 'Could not update bookmark'));
    }
  };

  const startPlayground = async () => {
    if (!detail) return;
    setStarting(true);
    try {
      const link = await source.startPlayground(detail.id);
      toast.success(`Created "${link.playground.title}". You are the team lead.`);
      navigate(`/playgrounds/${link.playground.id}`);
    } catch (err) {
      toast.error(errorMessage(err, 'Could not start a playground'));
      setStarting(false);
    }
  };

  if (notFound) {
    return (
      <div className="rounded-2xl border border-dashed border-slate-800 p-10 text-center">
        <p className="text-sm text-slate-300">This opportunity does not exist or was removed.</p>
        <Link to="/opportunities" className="mt-3 inline-block text-xs text-emerald-300 hover:underline">Back to the Contest Board</Link>
      </div>
    );
  }

  if (!detail) {
    return (
      <div className="flex items-center gap-2 text-sm text-slate-400">
        <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" /> Loading opportunity…
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <Link to="/opportunities" className="inline-flex items-center gap-1.5 text-xs text-slate-400 hover:text-slate-200">
        <ArrowLeft className="h-3.5 w-3.5" aria-hidden="true" /> Contest Board
      </Link>

      <section className="rounded-2xl border border-slate-800 bg-slate-900/60 p-5 sm:p-6">
        <div className="flex items-start justify-between gap-4">
          <div className="min-w-0">
            <div className="mb-2 flex flex-wrap gap-1.5">
              <DeadlinePill deadline={detail.registrationDeadline} />
              <CategoryPill category={detail.category} />
              <VerifiedPill status={detail.status} />
            </div>
            <h2 className="break-words text-2xl font-bold tracking-tight text-white">{detail.title}</h2>
            <p className="mt-1 text-sm text-slate-400">{detail.organizer}</p>
          </div>
          <div className="flex shrink-0 flex-col items-center gap-1">
            <BookmarkButton bookmarked={detail.bookmarked} title={detail.title} onToggle={() => void toggleBookmark()} />
            <span className="text-[11px] text-slate-500 tabular-nums">{detail.bookmarkCount}</span>
          </div>
        </div>

        {detail.description && (
          <p className="mt-4 max-w-[70ch] text-sm leading-relaxed text-slate-300">{detail.description}</p>
        )}

        <dl className="mt-5 grid gap-4 text-sm sm:grid-cols-2 lg:grid-cols-4">
          <Fact icon={CalendarDays} label="Registration closes" value={formatExactDateTime(detail.registrationDeadline)} />
          <Fact icon={CalendarDays} label="Event" value={`${formatExactDateTime(detail.startDate)} – ${formatExactDateTime(detail.endDate)}`} />
          <Fact icon={Users} label="Team size" value={detail.teamSizeMin === detail.teamSizeMax ? `${detail.teamSizeMin}` : `${detail.teamSizeMin}–${detail.teamSizeMax}`} />
          <Fact icon={Briefcase} label="Mode" value={MODE_LABELS[detail.mode]} />
        </dl>

        <div className="mt-4">
          <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Eligibility</p>
          <p className="mt-1 text-sm text-slate-300">{detail.eligibility ?? 'Open to all KUET students'}</p>
        </div>

        <div className="mt-6 flex flex-wrap gap-2">
          <a
            href={detail.sourceUrl}
            target="_blank"
            rel="noopener noreferrer"
            className="inline-flex items-center gap-2 rounded-lg border border-slate-700 bg-slate-800 px-4 py-2 text-sm font-medium text-slate-100 hover:bg-slate-700"
          >
            <ExternalLink className="h-4 w-4 shrink-0" aria-hidden="true" />
            Official source
          </a>
          <button
            type="button"
            onClick={() => void startPlayground()}
            disabled={starting || detail.expired}
            title={detail.expired ? 'Registration has closed' : undefined}
            className="inline-flex min-w-[13rem] items-center justify-center gap-2 rounded-lg bg-emerald-600 px-4 py-2 text-sm font-medium text-white hover:bg-emerald-500 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {starting ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" /> : <Rocket className="h-4 w-4" aria-hidden="true" />}
            Start Project Playground
          </button>
        </div>
      </section>

      <section>
        <h3 className="text-sm font-semibold text-white">
          Teams formed <span className="font-normal text-slate-500 tabular-nums">({pluralize(teams.length, 'team', 'teams')})</span>
        </h3>
        {teams.length === 0 ? (
          <p className="mt-2 text-xs text-slate-500">No team yet. Start one and invite your classmates.</p>
        ) : (
          <ul className="mt-2 grid gap-2 sm:grid-cols-2">
            {teams.map((team) => (
              <li key={team.id}>
                <Link
                  to={`/playgrounds/${team.id}`}
                  className="flex items-center justify-between gap-3 rounded-lg border border-slate-800 bg-slate-900/60 px-3 py-2 hover:border-slate-700"
                >
                  <span className="truncate text-sm text-slate-200" title={team.title}>{team.title}</span>
                  <span className="shrink-0 text-[11px] text-slate-500 tabular-nums">{pluralize(team.memberCount, 'member', 'members')}</span>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}

function Fact({ icon: Icon, label, value }: { icon: typeof CalendarDays; label: string; value: string }) {
  return (
    <div className="min-w-0">
      <dt className="flex items-center gap-1.5 text-xs text-slate-500">
        <Icon className="h-3.5 w-3.5 shrink-0" aria-hidden="true" /> {label}
      </dt>
      <dd className="mt-0.5 text-slate-200 tabular-nums">{value}</dd>
    </div>
  );
}
