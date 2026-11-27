import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { CalendarDays, GraduationCap, Loader2, Plus, Search, UserPlus, Users } from 'lucide-react';
import { toast } from 'sonner';
import { DateTile } from '../components/study/SessionCard';
import { errorMessage } from '../hooks/useCollabSource';
import { useStudySource } from '../hooks/useStudySource';
import { cn, pluralize } from '../lib/format';
import type { StudyCircleSummary } from '../types/study';

const timeFmt = new Intl.DateTimeFormat('en-US', { hour: 'numeric', minute: '2-digit' });

/** Study Circles hub: upcoming sessions from my circles, the circle directory, join and create. */
export function StudyCirclesPage() {
  const { source } = useStudySource();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const courseId = Number(searchParams.get('courseId')) || undefined;
  const courseCode = searchParams.get('courseCode');
  const [circles, setCircles] = useState<StudyCircleSummary[] | null>(null);
  const [mine, setMine] = useState<StudyCircleSummary[]>([]);
  const [onlyMine, setOnlyMine] = useState(false);
  const [search, setSearch] = useState('');
  const [query, setQuery] = useState('');
  const [joining, setJoining] = useState<number | null>(null);
  const [name, setName] = useState('');
  const [creating, setCreating] = useState(false);

  useEffect(() => {
    const id = window.setTimeout(() => setQuery(search), 250);
    return () => window.clearTimeout(id);
  }, [search]);

  const loadMine = useCallback(() => {
    source.browseCircles({ mine: true }).then((page) => setMine(page.items)).catch(() => setMine([]));
  }, [source]);

  useEffect(() => {
    let cancelled = false;
    source.browseCircles({ q: query, mine: onlyMine, courseOfferingId: courseId })
      .then((page) => { if (!cancelled) setCircles(page.items); })
      .catch((err) => {
        if (cancelled) return;
        setCircles([]);
        toast.error(errorMessage(err, 'Could not load study circles'));
      });
    return () => { cancelled = true; };
  }, [source, query, onlyMine, courseId]);

  useEffect(loadMine, [loadMine]);

  const join = async (circle: StudyCircleSummary) => {
    setJoining(circle.id);
    try {
      const membership = await source.joinCircle(circle.id);
      setCircles((prev) => prev?.map((c) => (c.id === circle.id
        ? { ...c, myRole: membership.role, memberCount: membership.memberCount } : c)) ?? prev);
      toast.success(`Joined ${circle.name}`);
      loadMine();
    } catch (err) {
      toast.error(errorMessage(err, 'Could not join the circle'));
    } finally {
      setJoining(null);
    }
  };

  const create = async (event: FormEvent) => {
    event.preventDefault();
    if (!name.trim()) return;
    setCreating(true);
    try {
      const created = await source.createCircle({ name: name.trim() });
      toast.success(`Created "${created.name}"`);
      navigate(`/study-circles/${created.id}`);
    } catch (err) {
      toast.error(errorMessage(err, 'Could not create the circle'));
      setCreating(false);
    }
  };

  const upcoming = mine
    .filter((c): c is StudyCircleSummary & { nextSessionAt: string } => c.nextSessionAt !== null)
    .sort((a, b) => a.nextSessionAt.localeCompare(b.nextSessionAt))
    .slice(0, 4);

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-2xl font-bold tracking-tight text-white">Study Circles</h2>
        <p className="mt-1 max-w-[65ch] text-sm text-slate-400">
          Small peer groups that revise together: shared sessions, a revision checklist and vetted resources.
        </p>
      </div>

      <section aria-labelledby="upcoming-heading">
        <h3 id="upcoming-heading" className="mb-2 flex items-center gap-1.5 text-sm font-semibold text-white">
          <CalendarDays className="h-4 w-4 text-emerald-300" aria-hidden="true" /> Your upcoming sessions
        </h3>
        {upcoming.length === 0 ? (
          <p className="rounded-xl border border-dashed border-slate-800 p-4 text-xs text-slate-400">
            No sessions scheduled in your circles. Join a circle or schedule one from its page.
          </p>
        ) : (
          <ul className="grid gap-2 sm:grid-cols-2 xl:grid-cols-4" aria-label="Upcoming sessions">
            {upcoming.map((c) => (
              <li key={c.id}>
                <Link to={`/study-circles/${c.id}`} className="flex gap-3 rounded-xl border border-slate-800 bg-slate-900/60 p-3 hover:border-slate-700">
                  <DateTile iso={c.nextSessionAt} />
                  <div className="min-w-0">
                    <p className="truncate text-sm font-medium text-white" title={c.name}>{c.name}</p>
                    <p className="mt-0.5 text-xs text-slate-400 tabular-nums">
                      <time dateTime={c.nextSessionAt}>{timeFmt.format(new Date(c.nextSessionAt))}</time>
                    </p>
                    {c.course && <p className="mt-0.5 truncate font-mono text-[10px] text-slate-500">{c.course.courseCode}</p>}
                  </div>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </section>

      <div className="flex flex-wrap items-center gap-2">
        <div className="relative min-w-[14rem] flex-1">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-slate-500" aria-hidden="true" />
          <input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search circles or course codes"
            aria-label="Search study circles"
            className="w-full rounded-lg border border-slate-800 bg-slate-950 py-2 pl-8 pr-3 text-sm text-slate-100 placeholder:text-slate-500 focus:border-emerald-500/50 focus:outline-none"
          />
        </div>
        <div className="flex rounded-lg border border-slate-800 bg-slate-950 p-0.5" role="group" aria-label="Which circles">
          {[{ label: 'All circles', value: false }, { label: 'My circles', value: true }].map((opt) => (
            <button
              key={opt.label}
              type="button"
              aria-pressed={onlyMine === opt.value}
              onClick={() => setOnlyMine(opt.value)}
              className={cn('rounded-md px-3 py-1.5 text-xs font-medium',
                onlyMine === opt.value ? 'bg-slate-800 text-white' : 'text-slate-400 hover:text-slate-200')}
            >
              {opt.label}
            </button>
          ))}
        </div>
      </div>

      {courseId && (
        <p className="flex flex-wrap items-center gap-2 text-xs text-slate-400">
          Showing circles for
          <span className="rounded-full border border-emerald-500/40 bg-emerald-500/10 px-2 py-0.5 font-mono text-emerald-200">
            {courseCode ?? `course ${courseId}`}
          </span>
          <button type="button" onClick={() => setSearchParams({})} className="text-slate-400 underline-offset-2 hover:text-white hover:underline">
            Show all courses
          </button>
        </p>
      )}

      <form onSubmit={create} className="flex flex-wrap items-center gap-2 rounded-xl border border-slate-800 bg-slate-900/60 p-3">
        <input
          value={name}
          onChange={(e) => setName(e.target.value)}
          maxLength={100}
          placeholder="Start a circle, e.g. CT-3 Compiler Design Sprint"
          aria-label="New circle name"
          className="min-w-[14rem] flex-1 rounded-lg border border-slate-800 bg-slate-950 px-3 py-2 text-sm text-slate-100 placeholder:text-slate-500 focus:border-emerald-500/50 focus:outline-none"
        />
        <button
          type="submit"
          disabled={creating || !name.trim()}
          className="inline-flex min-w-[8rem] items-center justify-center gap-1.5 rounded-lg bg-emerald-600 px-3 py-2 text-xs font-medium text-white hover:bg-emerald-500 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {creating ? <Loader2 className="h-3.5 w-3.5 animate-spin" aria-hidden="true" /> : <Plus className="h-3.5 w-3.5" aria-hidden="true" />}
          Create circle
        </button>
      </form>

      {circles === null ? (
        <Loader2 className="h-4 w-4 animate-spin text-slate-400" aria-label="Loading study circles" />
      ) : circles.length === 0 ? (
        <div className="rounded-2xl border border-dashed border-slate-800 p-10 text-center">
          <GraduationCap className="mx-auto mb-3 h-8 w-8 text-slate-500" aria-hidden="true" />
          <p className="text-sm text-slate-300">{onlyMine ? 'You have not joined any circle yet.' : 'No circles match this search.'}</p>
        </div>
      ) : (
        <>
          <p className="text-xs text-slate-500 tabular-nums">{pluralize(circles.length, 'circle', 'circles')}</p>
          <ul className="grid gap-3 md:grid-cols-2" aria-label="Study circles">
            {circles.map((c) => (
              <li key={c.id} className="flex flex-col rounded-xl border border-slate-800 bg-slate-900/60 p-4">
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <Link to={`/study-circles/${c.id}`} className="block truncate text-sm font-semibold text-white hover:text-emerald-300" title={c.name}>
                      {c.name}
                    </Link>
                    {c.course && (
                      <p className="mt-0.5 truncate font-mono text-[11px] text-slate-400">{c.course.courseCode} · {c.course.courseTitle}</p>
                    )}
                  </div>
                  {c.myRole ? (
                    <span className="shrink-0 rounded bg-slate-800 px-1.5 py-0.5 font-mono text-[10px] text-emerald-300">{c.myRole}</span>
                  ) : (
                    <button
                      type="button"
                      onClick={() => join(c)}
                      disabled={joining === c.id}
                      aria-label={`Join ${c.name}`}
                      className="inline-flex min-w-[4.5rem] shrink-0 items-center justify-center gap-1 rounded-lg border border-emerald-500/40 px-2.5 py-1 text-xs font-medium text-emerald-300 hover:bg-emerald-500/10 disabled:opacity-50"
                    >
                      {joining === c.id ? <Loader2 className="h-3 w-3 animate-spin" aria-hidden="true" /> : <UserPlus className="h-3 w-3" aria-hidden="true" />}
                      Join
                    </button>
                  )}
                </div>
                {c.description && <p className="mt-2 line-clamp-2 text-xs text-slate-300">{c.description}</p>}
                <p className="mt-auto flex items-center gap-2 pt-3 text-[11px] text-slate-400 tabular-nums">
                  <Users className="h-3 w-3 shrink-0" aria-hidden="true" />
                  <span>{pluralize(c.memberCount, 'member', 'members')}</span>
                  {c.nextSessionAt && (
                    <>
                      <span>·</span>
                      <span>next session <time dateTime={c.nextSessionAt}>{new Date(c.nextSessionAt).toLocaleDateString('en-GB', { day: '2-digit', month: 'short' })}</time></span>
                    </>
                  )}
                </p>
              </li>
            ))}
          </ul>
        </>
      )}
    </div>
  );
}
