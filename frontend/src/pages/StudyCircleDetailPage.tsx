import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { Link, useParams } from 'react-router-dom';
import { ArrowLeft, CalendarPlus, Loader2, Lock, UserPlus } from 'lucide-react';
import { toast } from 'sonner';
import { Avatar } from '../components/collab/Avatar';
import { PomodoroTimer } from '../components/study/PomodoroTimer';
import { ResourceList } from '../components/study/ResourceList';
import { RevisionChecklist } from '../components/study/RevisionChecklist';
import { SessionCard } from '../components/study/SessionCard';
import { errorMessage } from '../hooks/useCollabSource';
import { useStudySource } from '../hooks/useStudySource';
import { pluralize } from '../lib/format';
import type { StudySource } from '../lib/study/studySource';
import type { StudyCircleDetail, StudySession } from '../types/study';

const DURATIONS = [30, 45, 60, 90, 120];

/** Local "YYYY-MM-DDTHH:mm" for a datetime-local input, defaulting to tomorrow 18:00. */
function defaultStart(): string {
  const d = new Date(Date.now() + 24 * 60 * 60 * 1000);
  d.setHours(18, 0, 0, 0);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function Sessions({ circleId, source }: { circleId: number; source: StudySource }) {
  const [sessions, setSessions] = useState<StudySession[] | null>(null);
  const [title, setTitle] = useState('');
  const [start, setStart] = useState(defaultStart);
  const [duration, setDuration] = useState(60);
  const [where, setWhere] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    source.listSessions(circleId)
      .then(setSessions)
      .catch((err) => {
        setSessions([]);
        toast.error(errorMessage(err, 'Could not load sessions'));
      });
  }, [circleId, source]);

  const schedule = async (event: FormEvent) => {
    event.preventDefault();
    if (!title.trim() || !start) return;
    setSaving(true);
    try {
      const created = await source.scheduleSession(circleId, {
        title: title.trim(),
        // datetime-local is wall-clock time in the browser's zone; the API stores UTC.
        scheduledAt: new Date(start).toISOString(),
        durationMinutes: duration,
        locationOrLink: where.trim() || undefined,
      });
      setSessions((prev) => [...(prev ?? []), created].sort((a, b) => a.scheduledAt.localeCompare(b.scheduledAt)));
      setTitle('');
      setWhere('');
      toast.success('Session scheduled');
    } catch (err) {
      toast.error(errorMessage(err, 'Could not schedule the session'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <section aria-labelledby="sessions-heading" className="space-y-3">
      <h3 id="sessions-heading" className="text-sm font-semibold text-white">Upcoming sessions</h3>
      {sessions === null ? (
        <Loader2 className="h-4 w-4 animate-spin text-slate-400" aria-label="Loading sessions" />
      ) : sessions.length === 0 ? (
        <p className="rounded-xl border border-dashed border-slate-800 p-4 text-center text-xs text-slate-400">No sessions scheduled yet.</p>
      ) : (
        <div className="space-y-2">{sessions.map((s) => <SessionCard key={s.id} session={s} />)}</div>
      )}

      <form onSubmit={schedule} className="grid gap-2 rounded-xl border border-slate-800 bg-slate-900/60 p-3 sm:grid-cols-2">
        <input
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          maxLength={150}
          placeholder="Session title"
          aria-label="Session title"
          className="min-w-0 rounded-lg border border-slate-800 bg-slate-950 px-3 py-2 text-sm text-slate-100 placeholder:text-slate-500 focus:border-emerald-500/50 focus:outline-none sm:col-span-2"
        />
        <input
          type="datetime-local"
          value={start}
          onChange={(e) => setStart(e.target.value)}
          aria-label="Starts at"
          className="min-w-0 rounded-lg border border-slate-800 bg-slate-950 px-3 py-2 text-sm text-slate-100 [color-scheme:dark] tabular-nums"
        />
        <select
          aria-label="Duration"
          value={duration}
          onChange={(e) => setDuration(Number(e.target.value))}
          className="rounded-lg border border-slate-800 bg-slate-950 px-2 py-2 text-sm text-slate-200"
        >
          {DURATIONS.map((m) => <option key={m} value={m}>{m} minutes</option>)}
        </select>
        <input
          value={where}
          onChange={(e) => setWhere(e.target.value)}
          maxLength={255}
          placeholder="Room or meeting link (optional)"
          aria-label="Location or link"
          className="min-w-0 rounded-lg border border-slate-800 bg-slate-950 px-3 py-2 text-sm text-slate-100 placeholder:text-slate-500 focus:border-emerald-500/50 focus:outline-none"
        />
        <button
          type="submit"
          disabled={saving || !title.trim()}
          className="inline-flex min-w-[9rem] items-center justify-center gap-1.5 rounded-lg bg-emerald-600 px-3 py-2 text-xs font-medium text-white hover:bg-emerald-500 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {saving ? <Loader2 className="h-3.5 w-3.5 animate-spin" aria-hidden="true" /> : <CalendarPlus className="h-3.5 w-3.5" aria-hidden="true" />}
          Schedule session
        </button>
      </form>
    </section>
  );
}

/** One circle: who is in it, and for members the sessions, revision checklist, resources and a Pomodoro. */
export function StudyCircleDetailPage() {
  const { id } = useParams();
  const circleId = Number(id);
  const { source } = useStudySource();
  const [circle, setCircle] = useState<StudyCircleDetail | null>(null);
  const [missing, setMissing] = useState(false);
  const [joining, setJoining] = useState(false);

  const load = useCallback(() => {
    source.getCircle(circleId)
      .then((detail) => { setCircle(detail); setMissing(false); })
      .catch((err) => {
        setMissing(true);
        toast.error(errorMessage(err, 'Could not load the circle'));
      });
  }, [circleId, source]);

  useEffect(load, [load]);

  const join = async () => {
    setJoining(true);
    try {
      await source.joinCircle(circleId);
      toast.success('Joined the circle');
      load();
    } catch (err) {
      toast.error(errorMessage(err, 'Could not join the circle'));
    } finally {
      setJoining(false);
    }
  };

  if (missing) {
    return (
      <div className="rounded-2xl border border-dashed border-slate-800 p-10 text-center">
        <p className="text-sm text-slate-300">This study circle does not exist.</p>
        <Link to="/study-circles" className="mt-2 inline-block text-sm text-emerald-300 hover:underline">Back to Study Circles</Link>
      </div>
    );
  }
  if (!circle) return <Loader2 className="h-4 w-4 animate-spin text-slate-400" aria-label="Loading circle" />;

  const isMember = circle.myRole !== null;

  return (
    <div className="space-y-5">
      <Link to="/study-circles" className="inline-flex items-center gap-1 text-xs text-slate-400 hover:text-white">
        <ArrowLeft className="h-3.5 w-3.5" aria-hidden="true" /> Study Circles
      </Link>

      <header className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <h2 className="break-words text-2xl font-bold tracking-tight text-white">{circle.name}</h2>
          {circle.course && (
            <p className="mt-0.5 font-mono text-xs text-slate-400">{circle.course.courseCode} · {circle.course.courseTitle}</p>
          )}
          {circle.description && <p className="mt-2 max-w-[65ch] text-sm text-slate-300">{circle.description}</p>}
        </div>
        {isMember ? (
          <span className="shrink-0 rounded bg-slate-800 px-2 py-1 font-mono text-[11px] text-emerald-300">{circle.myRole}</span>
        ) : (
          <button
            type="button"
            onClick={join}
            disabled={joining}
            className="inline-flex min-w-[8rem] shrink-0 items-center justify-center gap-1.5 rounded-lg bg-emerald-600 px-3 py-2 text-xs font-medium text-white hover:bg-emerald-500 disabled:opacity-50"
          >
            {joining ? <Loader2 className="h-3.5 w-3.5 animate-spin" aria-hidden="true" /> : <UserPlus className="h-3.5 w-3.5" aria-hidden="true" />}
            Join circle
          </button>
        )}
      </header>

      <section aria-label="Members" className="flex flex-wrap items-center gap-2">
        <span className="text-xs text-slate-400 tabular-nums">{pluralize(circle.members.length, 'member', 'members')}</span>
        <ul className="flex flex-wrap gap-1.5">
          {circle.members.map((m) => (
            <li key={m.userId} className="flex items-center gap-1.5 rounded-full border border-slate-800 bg-slate-900/60 py-0.5 pl-0.5 pr-2.5">
              <Avatar name={m.displayName} size="xs" />
              <span className="text-xs text-slate-200">{m.displayName}</span>
              {m.role === 'HOST' && <span className="text-[10px] text-emerald-300">host</span>}
            </li>
          ))}
        </ul>
      </section>

      {isMember ? (
        <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_18rem]">
          <div className="min-w-0 space-y-4">
            <Sessions circleId={circle.id} source={source} />
            <RevisionChecklist circleId={circle.id} source={source} />
            <ResourceList circleId={circle.id} source={source} />
          </div>
          <aside className="lg:sticky lg:top-4 lg:self-start">
            <PomodoroTimer />
          </aside>
        </div>
      ) : (
        <div className="rounded-2xl border border-dashed border-slate-800 p-8 text-center">
          <Lock className="mx-auto mb-2 h-6 w-6 text-slate-500" aria-hidden="true" />
          <p className="text-sm text-slate-300">Join this circle to see its sessions, revision checklist and resources.</p>
          <p className="mt-1 text-xs text-slate-500 tabular-nums">{pluralize(circle.resourceCount, 'resource', 'resources')} shared so far</p>
        </div>
      )}
    </div>
  );
}
