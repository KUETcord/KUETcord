import { useState } from 'react';
import {
  CalendarClock,
  Check,
  ChevronDown,
  ChevronUp,
  Clock,
  GraduationCap,
  Loader2,
  RefreshCw,
  UserCheck,
  XCircle,
} from 'lucide-react';
import { cn, formatExactDateTime, formatSmartTimestamp, getInitials } from '../../lib/format';
import type { CtEventSummary, GlobalRole } from '../../types/domain';
import { CtStatusBadge } from './CtStatusBadge';

interface CtEventCardProps {
  event: CtEventSummary;
  currentUserId: number | null;
  currentUserRole: GlobalRole | null;
  canApprove: boolean;
  canRescheduleOrCancel: boolean;
  verifyingId: number | null;
  onVerify: (event: CtEventSummary) => void;
  onOpenReschedule: (event: CtEventSummary) => void;
  onOpenCancel: (event: CtEventSummary) => void;
}

export function CtEventCard({
  event,
  currentUserId,
  currentUserRole,
  canApprove,
  canRescheduleOrCancel,
  verifyingId,
  onVerify,
  onOpenReschedule,
  onOpenCancel,
}: CtEventCardProps) {
  const [topicsOpen, setTopicsOpen] = useState(false);

  const isTerminal = event.status === 'CANCELLED';
  const isElevated = currentUserRole === 'ADMIN' || currentUserRole === 'MODERATOR';
  const isAssignedTeacher =
    currentUserRole === 'TEACHER' &&
    currentUserId !== null &&
    event.assignedTeacherId === currentUserId;

  const canApproveThisEvent =
    canApprove &&
    (isElevated || isAssignedTeacher) &&
    (event.status === 'UNVERIFIED' || event.status === 'RESCHEDULED');

  const canMutateThisEvent =
    canRescheduleOrCancel &&
    !isTerminal &&
    (isElevated || currentUserRole === 'CR' || isAssignedTeacher);

  const isVerifying = verifyingId === event.id;

  return (
    <article
      className={cn(
        'rounded-xl border border-slate-800 bg-slate-900/60 p-5 transition-colors duration-150 ease-out hover:border-slate-700',
        isTerminal && 'opacity-75',
      )}
    >
      <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0 flex-1 space-y-2">
          <div className="flex flex-wrap items-center gap-2">
            <span className="rounded-md bg-slate-800 px-2 py-0.5 font-mono text-xs font-semibold tracking-tight text-emerald-300">
              {event.courseCode}
            </span>
            <CtStatusBadge status={event.status} />
            <span
              className="rounded-md border border-slate-800 bg-slate-950/60 px-2 py-0.5 font-mono text-[11px] text-slate-400 tabular-nums"
              title="Optimistic locking version"
            >
              v{event.version}
            </span>
          </div>

          <h3 className="text-base font-semibold leading-snug tracking-tight text-slate-100 break-words">
            {event.title}
          </h3>

          <p className="text-xs text-slate-400 break-words" title={event.courseTitle}>
            {event.courseTitle}
          </p>

          <div className="flex flex-wrap items-center gap-x-4 gap-y-1.5 pt-1 text-xs text-slate-300">
            <span className="inline-flex items-center gap-1.5 tabular-nums">
              <CalendarClock className="h-3.5 w-3.5 text-emerald-400 shrink-0" aria-hidden="true" />
              <span title={formatExactDateTime(event.scheduledAt)}>
                {formatExactDateTime(event.scheduledAt)} ({formatSmartTimestamp(event.scheduledAt)})
              </span>
            </span>
            <span className="inline-flex items-center gap-1.5 text-slate-400 tabular-nums">
              <Clock className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
              <span>{event.durationMinutes} min</span>
            </span>
            {event.assignedTeacherName && (
              <span
                className="inline-flex min-w-0 items-center gap-1.5 text-sky-300/90"
                title={`Assigned teacher: ${event.assignedTeacherName}`}
              >
                <GraduationCap className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                <span className="truncate max-w-[26ch]">{event.assignedTeacherName}</span>
              </span>
            )}
          </div>
        </div>

        {(canApproveThisEvent || canMutateThisEvent) && (
          <div className="flex flex-wrap items-center gap-2 shrink-0">
            {canApproveThisEvent && (
              <button
                type="button"
                disabled={isVerifying}
                onClick={() => onVerify(event)}
                className="inline-flex items-center justify-center gap-1.5 rounded-lg bg-emerald-600 px-3 py-1.5 text-xs font-medium text-white transition-colors duration-150 ease-out hover:bg-emerald-500 disabled:cursor-not-allowed disabled:opacity-50"
              >
                {isVerifying ? (
                  <Loader2 className="h-3.5 w-3.5 animate-spin shrink-0" aria-hidden="true" />
                ) : (
                  <Check className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                )}
                <span>Approve</span>
              </button>
            )}
            {canMutateThisEvent && (
              <>
                <button
                  type="button"
                  onClick={() => onOpenReschedule(event)}
                  className="inline-flex items-center justify-center gap-1.5 rounded-lg border border-slate-700 bg-slate-800/80 px-3 py-1.5 text-xs font-medium text-slate-200 transition-colors duration-150 ease-out hover:bg-slate-700"
                >
                  <RefreshCw className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                  <span>Reschedule</span>
                </button>
                <button
                  type="button"
                  onClick={() => onOpenCancel(event)}
                  className="inline-flex items-center justify-center gap-1.5 rounded-lg border border-rose-500/30 bg-rose-500/10 px-3 py-1.5 text-xs font-medium text-rose-300 transition-colors duration-150 ease-out hover:bg-rose-500/20"
                >
                  <XCircle className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                  <span>Cancel</span>
                </button>
              </>
            )}
          </div>
        )}
      </div>

      {/* Syllabus / Topics Disclosure */}
      <div className="mt-4 border-t border-slate-800/80 pt-3">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div className="flex min-w-0 items-center gap-3 text-xs text-slate-400">
            <div className="flex min-w-0 items-center gap-2">
              <span
                className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-slate-800 text-[10px] font-semibold text-slate-200"
                aria-hidden="true"
              >
                {getInitials(event.submittedByName)}
              </span>
              <span className="truncate" title={event.submittedByName}>
                Proposed by <strong className="font-medium text-slate-200">{event.submittedByName}</strong>
              </span>
            </div>

            {event.verifiedByName && (
              <span
                className="inline-flex min-w-0 items-center gap-1 truncate text-emerald-300/90"
                title={`Approved by ${event.verifiedByName}`}
              >
                <UserCheck className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                <span className="truncate">{event.verifiedByName}</span>
              </span>
            )}
          </div>

          {event.coveredTopics && (
            <button
              type="button"
              onClick={() => setTopicsOpen((prev) => !prev)}
              className="inline-flex items-center gap-1 text-xs font-medium text-slate-300 transition-colors duration-150 ease-out hover:text-white shrink-0"
              aria-expanded={topicsOpen}
            >
              <span>{topicsOpen ? 'Hide syllabus' : 'View syllabus'}</span>
              {topicsOpen ? (
                <ChevronUp className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
              ) : (
                <ChevronDown className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
              )}
            </button>
          )}
        </div>

        {topicsOpen && event.coveredTopics && (
          <p className="mt-2.5 max-w-[65ch] rounded-lg bg-slate-950/70 p-3 text-xs leading-[1.6] text-slate-300 [overflow-wrap:anywhere]">
            {event.coveredTopics}
          </p>
        )}
      </div>
    </article>
  );
}
