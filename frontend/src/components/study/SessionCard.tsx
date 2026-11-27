import { Clock, MapPin, Video } from 'lucide-react';
import type { StudySession } from '../../types/study';

const dayFmt = new Intl.DateTimeFormat('en-GB', { weekday: 'short' });
const dateFmt = new Intl.DateTimeFormat('en-GB', { day: '2-digit' });
const monthFmt = new Intl.DateTimeFormat('en-GB', { month: 'short' });
const timeFmt = new Intl.DateTimeFormat('en-US', { hour: 'numeric', minute: '2-digit' });

export function isLink(value: string | null): value is string {
  return !!value && /^https?:\/\//i.test(value);
}

/** Calendar tile: weekday, day of month and month, in tabular figures. */
export function DateTile({ iso }: { iso: string }) {
  const date = new Date(iso);
  return (
    <div className="flex w-14 shrink-0 flex-col items-center rounded-lg border border-slate-700 bg-slate-950 py-1.5 tabular-nums" aria-hidden="true">
      <span className="text-[10px] font-medium uppercase tracking-wide text-emerald-300">{dayFmt.format(date)}</span>
      <span className="text-lg font-bold leading-tight text-white">{dateFmt.format(date)}</span>
      <span className="text-[10px] uppercase text-slate-400">{monthFmt.format(date)}</span>
    </div>
  );
}

export function timeRange(session: Pick<StudySession, 'scheduledAt' | 'endsAt'>): string {
  return `${timeFmt.format(new Date(session.scheduledAt))} – ${timeFmt.format(new Date(session.endsAt))}`;
}

export function SessionCard({ session }: { session: StudySession }) {
  const where = session.locationOrLink;
  return (
    <article className="flex gap-3 rounded-xl border border-slate-800 bg-slate-900/60 p-3" aria-label={session.title}>
      <DateTile iso={session.scheduledAt} />
      <div className="min-w-0 flex-1">
        <h4 className="break-words text-sm font-semibold text-white">{session.title}</h4>
        <p className="mt-1 flex items-center gap-1.5 text-xs text-slate-400 tabular-nums">
          <Clock className="h-3 w-3 shrink-0" aria-hidden="true" />
          <time dateTime={session.scheduledAt}>{timeRange(session)}</time>
          <span>· {session.durationMinutes} min</span>
        </p>
        {where && (
          <p className="mt-1 flex min-w-0 items-center gap-1.5 text-xs text-slate-400">
            {isLink(where) ? (
              <>
                <Video className="h-3 w-3 shrink-0" aria-hidden="true" />
                <a href={where} target="_blank" rel="noopener noreferrer" className="truncate text-emerald-300 hover:underline">Join online</a>
              </>
            ) : (
              <>
                <MapPin className="h-3 w-3 shrink-0" aria-hidden="true" />
                <span className="truncate">{where}</span>
              </>
            )}
          </p>
        )}
        {session.agenda && <p className="mt-1.5 line-clamp-2 text-xs text-slate-300">{session.agenda}</p>}
      </div>
    </article>
  );
}
