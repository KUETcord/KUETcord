import { useState } from 'react';
import { Link } from 'react-router-dom';
import {
  BookOpen,
  CalendarClock,
  Check,
  ChevronDown,
  ChevronUp,
  Clock,
  GraduationCap,
  Loader2,
  MapPin,
  RefreshCw,
  UserCheck,
  XCircle,
} from 'lucide-react';
import { cn, formatExactDateTime, formatSmartTimestamp, getInitials } from '../../lib/format';
import type { CtEventSummary, GlobalRole, RoomAllocation } from '../../types/domain';
import { CtStatusBadge } from './CtStatusBadge';

/**
 * Extracts a student's numeric class roll (e.g. "2207053" -> 53, or "53" -> 53)
 * to highlight which roll-range room applies to the signed-in student.
 */
function parseClassRoll(rollNumber?: string | null): number | null {
  if (!rollNumber) return null;
  const digits = rollNumber.replace(/\D/g, '');
  if (!digits) return null;
  if (digits.length >= 6) {
    const tail = Number.parseInt(digits.slice(-3), 10);
    return Number.isFinite(tail) && tail > 0 ? tail : null;
  }
  const parsed = Number.parseInt(digits, 10);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null;
}

function matchesStudentRoll(allocation: RoomAllocation, studentRoll: number | null): boolean {
  if (studentRoll === null) return false;
  if (allocation.rollStart !== null && studentRoll < allocation.rollStart) return false;
  if (allocation.rollEnd !== null && studentRoll > allocation.rollEnd) return false;
  return allocation.rollStart !== null || allocation.rollEnd !== null;
}

interface CtEventCardProps {
  event: CtEventSummary;
  currentUserId: number | null;
  currentUserRole: GlobalRole | null;
  currentUserRollNumber?: string | null;
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
  currentUserRollNumber,
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
    (event.assignedTeacherId === currentUserId || event.coTeacherId === currentUserId);

  const canApproveThisEvent =
    canApprove &&
    (isElevated || isAssignedTeacher) &&
    (event.status === 'UNVERIFIED' || event.status === 'RESCHEDULED');

  const canMutateThisEvent =
    canRescheduleOrCancel &&
    !isTerminal &&
    (isElevated || currentUserRole === 'CR' || isAssignedTeacher);

  const isVerifying = verifyingId === event.id;
  const studentClassRoll = parseClassRoll(currentUserRollNumber);
  const allocations = event.roomAllocations ?? [];

  const combinedTeacherLabel = event.coTeacherName
    ? `${event.assignedTeacherName ?? ''} & ${event.coTeacherName}`
    : event.assignedTeacherName;

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
            {(event.targetYearTerm ?? event.courseYearTerm) && (
              <span className="rounded-md border border-slate-800 bg-slate-950/60 px-2 py-0.5 font-mono text-[11px] text-sky-300 tabular-nums">
                {event.targetYearTerm ?? event.courseYearTerm}
              </span>
            )}
            {event.courseDepartment &&
              event.targetDepartment &&
              event.courseDepartment.toUpperCase() !== event.targetDepartment.toUpperCase() && (
                <span
                  className="rounded-md border border-amber-500/35 bg-amber-500/10 px-2 py-0.5 font-mono text-[11px] font-semibold text-amber-200"
                  title={`Cross-department course offered by ${event.courseDepartment} for ${event.targetDepartment} students`}
                >
                  Cross-Dept · {event.courseDepartment} → {event.targetDepartment}
                </span>
              )}
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
            {combinedTeacherLabel && (
              <span
                className="inline-flex min-w-0 items-center gap-1.5 text-sky-300/90"
                title={`Assigned teacher(s): ${combinedTeacherLabel}`}
              >
                <GraduationCap className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                <span className="truncate max-w-[40ch]">{combinedTeacherLabel}</span>
              </span>
            )}
          </div>

          {/* Multi-Room Roll-Range Allocations (or Single Exam Room Fallback) */}
          {allocations.length > 0 ? (
            <div className="flex flex-wrap items-center gap-1.5 pt-1" aria-label="Exam room allocations">
              {allocations.map((alloc, idx) => {
                const isMyRoom = matchesStudentRoll(alloc, studentClassRoll);
                const rangeLabel =
                  alloc.rollStart !== null && alloc.rollEnd !== null
                    ? `Roll ${alloc.rollStart}–${alloc.rollEnd}`
                    : alloc.rollStart !== null
                      ? `Roll ${alloc.rollStart}+`
                      : null;

                return (
                  <span
                    key={`${alloc.room}-${idx}`}
                    className={cn(
                      'inline-flex items-center gap-1 rounded-md border px-2 py-0.5 text-[11px] tabular-nums',
                      isMyRoom
                        ? 'border-emerald-500/50 bg-emerald-500/15 font-semibold text-emerald-200'
                        : 'border-amber-500/25 bg-amber-500/10 text-amber-200',
                    )}
                    title={isMyRoom ? `Your assigned room: ${alloc.room}` : undefined}
                  >
                    <MapPin
                      className={cn(
                        'h-3 w-3 shrink-0',
                        isMyRoom ? 'text-emerald-300' : 'text-amber-300',
                      )}
                      aria-hidden="true"
                    />
                    {rangeLabel && <span className="opacity-85">{rangeLabel}:</span>}
                    <span className="font-mono font-semibold">{alloc.room}</span>
                    {isMyRoom && (
                      <span className="ml-0.5 rounded bg-emerald-500/25 px-1 text-[10px] text-emerald-200">
                        Your Room
                      </span>
                    )}
                  </span>
                );
              })}
            </div>
          ) : (
            event.examRoom && (
              <div className="pt-1">
                <span
                  className="inline-flex min-w-0 items-center gap-1 rounded-md border border-amber-500/25 bg-amber-500/10 px-2 py-0.5 text-xs text-amber-200"
                  title={`Exam room: ${event.examRoom}`}
                >
                  <MapPin className="h-3.5 w-3.5 shrink-0 text-amber-300" aria-hidden="true" />
                  <span className="truncate max-w-[36ch]">{event.examRoom}</span>
                </span>
              </div>
            )
          )}
        </div>

        {(canApproveThisEvent || canMutateThisEvent) && (
          <div className="flex flex-wrap items-center gap-2 shrink-0">
            {canApproveThisEvent && (
              <button
                type="button"
                disabled={isVerifying}
                onClick={() => onVerify(event)}
                className="inline-flex items-center gap-1.5 rounded-lg bg-emerald-600 px-3 py-1.5 text-xs font-medium text-white transition-[background-color,transform] duration-100 ease-out hover:bg-emerald-500 active:scale-[0.97] disabled:cursor-not-allowed disabled:opacity-50"
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
                  className="inline-flex items-center gap-1.5 rounded-lg border border-slate-700 bg-slate-800/80 px-3 py-1.5 text-xs font-medium text-slate-200 transition-[background-color,transform] duration-100 ease-out hover:bg-slate-700 active:scale-[0.97]"
                >
                  <RefreshCw className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                  <span>Reschedule</span>
                </button>
                <button
                  type="button"
                  onClick={() => onOpenCancel(event)}
                  className="inline-flex items-center gap-1.5 rounded-lg border border-rose-500/30 bg-rose-500/10 px-3 py-1.5 text-xs font-medium text-rose-200 transition-[background-color,transform] duration-100 ease-out hover:bg-rose-500/20 active:scale-[0.97]"
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

          <div className="flex items-center gap-3 shrink-0">
            <Link
              to={`/study-circles?courseId=${event.courseOfferingId}&courseCode=${encodeURIComponent(event.courseCode)}`}
              className="inline-flex items-center gap-1 rounded-md border border-slate-800 bg-slate-950/60 px-2 py-1 text-xs font-medium text-sky-300 transition-[color,border-color,transform] duration-100 ease-out hover:border-sky-500/50 hover:text-sky-200 active:scale-[0.97]"
              title={`Open Study Circles for ${event.courseCode}`}
            >
              <BookOpen className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
              <span>Study Circles</span>
            </Link>

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
