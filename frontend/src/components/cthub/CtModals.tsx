import React, { useEffect, useMemo, useState } from 'react';
import * as Dialog from '@radix-ui/react-dialog';
import { AlertTriangle, Check, Loader2, MapPin, RefreshCw, Search, X } from 'lucide-react';
import { ctHubApi } from '../../lib/api';
import { getInitials } from '../../lib/format';
import { resolveYearTerm, YEAR_TERM_OPTIONS } from '../../lib/yearTerm';
import type { CourseOffering, CtEventSummary, TeacherSummary } from '../../types/domain';

function ModalFrame({
  open,
  onOpenChange,
  title,
  description,
  children,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  description: string;
  children: React.ReactNode;
}) {
  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Portal>
        <Dialog.Overlay className="kuet-dialog-overlay fixed inset-0 z-50 bg-slate-950/75 backdrop-blur-sm" />
        <Dialog.Content className="kuet-dialog-content fixed left-1/2 top-1/2 z-50 max-h-[90vh] w-[calc(100%-2rem)] max-w-lg -translate-x-1/2 -translate-y-1/2 overflow-y-auto rounded-2xl border border-slate-800 bg-slate-900 p-6 shadow-2xl focus:outline-none">
          <div className="flex items-start justify-between gap-4">
            <div className="min-w-0">
              <Dialog.Title className="text-lg font-semibold tracking-tight text-white">
                {title}
              </Dialog.Title>
              <Dialog.Description className="mt-1 text-xs leading-relaxed text-slate-400">
                {description}
              </Dialog.Description>
            </div>
            <Dialog.Close
              aria-label="Close modal"
              className="rounded-lg p-1.5 text-slate-400 transition-colors duration-150 ease-out hover:bg-slate-800 hover:text-white shrink-0"
            >
              <X className="h-4 w-4" />
            </Dialog.Close>
          </div>
          <div className="mt-5">{children}</div>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}

interface ProposeCtModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  courses: CourseOffering[];
  defaultYearTerm?: string | null;
  demoTeachers: TeacherSummary[];
  isDemoMode: boolean;
  onSubmit: (payload: {
    courseOfferingId: number;
    teacherId: number;
    teacherName: string;
    title: string;
    scheduledAt: string;
    durationMinutes: number;
    examRoom?: string;
    coveredTopics?: string;
  }) => Promise<void>;
}

export function ProposeCtModal({
  open,
  onOpenChange,
  courses,
  defaultYearTerm,
  demoTeachers,
  isDemoMode,
  onSubmit,
}: ProposeCtModalProps) {
  const [postingYearTerm, setPostingYearTerm] = useState<string>(defaultYearTerm ?? '');
  const [courseOfferingId, setCourseOfferingId] = useState<string>(
    courses[0] ? String(courses[0].id) : '',
  );
  const [teacherQuery, setTeacherQuery] = useState('');
  const [teacherResults, setTeacherResults] = useState<TeacherSummary[]>(demoTeachers);
  const [selectedTeacher, setSelectedTeacher] = useState<TeacherSummary | null>(null);
  const [searchingTeachers, setSearchingTeachers] = useState(false);

  const [title, setTitle] = useState('');
  const [scheduledLocal, setScheduledLocal] = useState('2026-12-01T10:00');
  const [durationMinutes, setDurationMinutes] = useState('45');
  const [examRoom, setExamRoom] = useState('');
  const [coveredTopics, setCoveredTopics] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const selectableCourses = useMemo(() => {
    if (!postingYearTerm) return courses;
    const matches = courses.filter(
      (c) => resolveYearTerm(c.yearTerm, c.semester) === postingYearTerm,
    );
    return matches.length > 0 ? matches : courses;
  }, [courses, postingYearTerm]);

  useEffect(() => {
    if (!open) return;
    if (selectableCourses.length > 0) {
      const stillPresent = selectableCourses.some((c) => String(c.id) === courseOfferingId);
      if (!stillPresent) {
        setCourseOfferingId(String(selectableCourses[0].id));
      }
    }
  }, [open, selectableCourses, courseOfferingId]);

  useEffect(() => {
    if (!open) return;

    const q = teacherQuery.trim().toLowerCase();
    if (isDemoMode) {
      const filtered = demoTeachers.filter(
        (t) =>
          !q ||
          t.displayName.toLowerCase().includes(q) ||
          t.email.toLowerCase().includes(q) ||
          (t.department && t.department.toLowerCase().includes(q)),
      );
      setTeacherResults(filtered);
      return;
    }

    const timer = window.setTimeout(async () => {
      setSearchingTeachers(true);
      try {
        const fetched = await ctHubApi.searchTeachers(q || undefined);
        setTeacherResults(fetched);
      } catch {
        setTeacherResults([]);
      } finally {
        setSearchingTeachers(false);
      }
    }, 200);

    return () => window.clearTimeout(timer);
  }, [open, teacherQuery, isDemoMode, demoTeachers]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!courseOfferingId || !selectedTeacher || !title.trim() || !scheduledLocal) {
      setError('Please select a course, assign a course teacher, and complete required fields.');
      return;
    }

    setSubmitting(true);
    setError(null);
    try {
      await onSubmit({
        courseOfferingId: Number(courseOfferingId),
        teacherId: selectedTeacher.id,
        teacherName: selectedTeacher.displayName,
        title: title.trim(),
        scheduledAt: new Date(scheduledLocal).toISOString(),
        durationMinutes: Number(durationMinutes) || 45,
        examRoom: examRoom.trim() || undefined,
        coveredTopics: coveredTopics.trim() || undefined,
      });
      setTitle('');
      setExamRoom('');
      setCoveredTopics('');
      setTeacherQuery('');
      setSelectedTeacher(null);
      onOpenChange(false);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to propose CT schedule');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <ModalFrame
      open={open}
      onOpenChange={onOpenChange}
      title="Propose Class Test Schedule"
      description="Filter by Year–Term, assign the course teacher, and set the exam room so everyone receives accurate notifications."
    >
      <form onSubmit={handleSubmit} className="space-y-4">
        {error && (
          <div className="rounded-lg border border-rose-500/30 bg-rose-500/10 px-3 py-2 text-xs text-rose-200">
            {error}
          </div>
        )}

        <div className="grid grid-cols-1 gap-3 sm:grid-cols-5">
          <div className="sm:col-span-2">
            <label htmlFor="ct-post-year-term" className="block text-xs font-medium text-slate-300">
              Year &amp; Term Filter
            </label>
            <select
              id="ct-post-year-term"
              value={postingYearTerm}
              onChange={(e) => setPostingYearTerm(e.target.value)}
              className="mt-1.5 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-slate-100 focus:border-emerald-500 focus:outline-none"
            >
              <option value="">All Terms</option>
              {YEAR_TERM_OPTIONS.map((yt) => (
                <option key={yt.code} value={yt.code}>
                  {yt.code} ({yt.label.split(' (')[0]})
                </option>
              ))}
            </select>
          </div>

          <div className="sm:col-span-3">
            <label htmlFor="ct-course" className="block text-xs font-medium text-slate-300">
              Course Offering
            </label>
            <select
              id="ct-course"
              value={courseOfferingId}
              onChange={(e) => setCourseOfferingId(e.target.value)}
              className="mt-1.5 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-slate-100 focus:border-emerald-500 focus:outline-none"
              required
            >
              {selectableCourses.map((course) => (
                <option key={course.id} value={course.id}>
                  {course.courseCode} ({resolveYearTerm(course.yearTerm, course.semester) ?? course.semester}) — {course.courseTitle}
                </option>
              ))}
            </select>
          </div>
        </div>

        {/* Searchable Teacher Picker */}
        <div>
          <label htmlFor="ct-teacher-search" className="block text-xs font-medium text-slate-300">
            Course Teacher (Notified for Approval)
          </label>
          <div className="relative mt-1.5">
            <Search
              className="pointer-events-none absolute left-3 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-slate-500"
              aria-hidden="true"
            />
            <input
              id="ct-teacher-search"
              type="search"
              value={teacherQuery}
              onChange={(e) => setTeacherQuery(e.target.value)}
              placeholder="Search teacher by name or email..."
              className="w-full rounded-lg border border-slate-700 bg-slate-950 py-2 pl-9 pr-3 text-sm text-slate-100 placeholder:text-slate-500 focus:border-emerald-500 focus:outline-none"
            />
          </div>

          {selectedTeacher && (
            <div className="mt-2 flex items-center justify-between gap-2 rounded-lg border border-emerald-500/30 bg-emerald-500/10 px-3 py-2 text-xs text-emerald-200">
              <div className="min-w-0 truncate">
                <span className="font-semibold">{selectedTeacher.displayName}</span>
                <span className="ml-2 text-emerald-300/80">{selectedTeacher.email}</span>
              </div>
              <button
                type="button"
                onClick={() => setSelectedTeacher(null)}
                className="text-emerald-300 hover:text-white shrink-0"
                aria-label="Clear selected teacher"
              >
                <X className="h-3.5 w-3.5" />
              </button>
            </div>
          )}

          <div
            role="listbox"
            aria-label="Matching course teachers"
            className="mt-1.5 max-h-36 overflow-y-auto rounded-lg border border-slate-800 bg-slate-950/90 divide-y divide-slate-800/70"
          >
            {searchingTeachers ? (
              <div className="flex items-center gap-2 px-3 py-2.5 text-xs text-slate-400">
                <Loader2 className="h-3.5 w-3.5 animate-spin shrink-0" />
                <span>Searching KUET faculty...</span>
              </div>
            ) : teacherResults.length === 0 ? (
              <p className="px-3 py-2.5 text-xs text-slate-500">No matching teachers found.</p>
            ) : (
              teacherResults.map((teacher) => {
                const isSelected = selectedTeacher?.id === teacher.id;
                return (
                  <button
                    key={teacher.id}
                    type="button"
                    role="option"
                    aria-selected={isSelected}
                    onClick={() => setSelectedTeacher(teacher)}
                    className="flex w-full items-center gap-2.5 px-3 py-2 text-left transition-colors duration-150 ease-out hover:bg-slate-800/80"
                  >
                    <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-sky-500/15 text-[11px] font-semibold text-sky-300">
                      {getInitials(teacher.displayName)}
                    </span>
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-xs font-medium text-slate-100" title={teacher.displayName}>
                        {teacher.displayName}
                      </p>
                      <p className="truncate text-[11px] text-slate-400 [overflow-wrap:anywhere]">
                        {teacher.email}
                      </p>
                    </div>
                    {teacher.department && (
                      <span className="rounded bg-slate-800 px-1.5 py-0.5 font-mono text-[10px] text-slate-300 shrink-0">
                        {teacher.department}
                      </span>
                    )}
                    {isSelected && <Check className="h-3.5 w-3.5 text-emerald-400 shrink-0" />}
                  </button>
                );
              })
            )}
          </div>
        </div>

        <div>
          <label htmlFor="ct-title" className="block text-xs font-medium text-slate-300">
            CT Title
          </label>
          <input
            id="ct-title"
            type="text"
            maxLength={150}
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="e.g., CT 2: Dynamic Programming & Shortest Paths"
            className="mt-1.5 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-slate-100 placeholder:text-slate-500 focus:border-emerald-500 focus:outline-none"
            required
          />
        </div>

        <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
          <div className="sm:col-span-2">
            <label htmlFor="ct-date" className="block text-xs font-medium text-slate-300">
              Scheduled Date &amp; Time
            </label>
            <input
              id="ct-date"
              type="datetime-local"
              value={scheduledLocal}
              onChange={(e) => setScheduledLocal(e.target.value)}
              className="mt-1.5 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-slate-100 tabular-nums focus:border-emerald-500 focus:outline-none"
              required
            />
          </div>
          <div>
            <label htmlFor="ct-duration" className="block text-xs font-medium text-slate-300">
              Duration (min)
            </label>
            <input
              id="ct-duration"
              type="number"
              min={10}
              max={240}
              value={durationMinutes}
              onChange={(e) => setDurationMinutes(e.target.value)}
              className="mt-1.5 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-slate-100 tabular-nums focus:border-emerald-500 focus:outline-none"
              required
            />
          </div>
        </div>

        <div>
          <label htmlFor="ct-room" className="block text-xs font-medium text-slate-300">
            Exam Room (Optional)
          </label>
          <div className="relative mt-1.5">
            <MapPin className="pointer-events-none absolute left-3 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-slate-500" />
            <input
              id="ct-room"
              type="text"
              maxLength={100}
              value={examRoom}
              onChange={(e) => setExamRoom(e.target.value)}
              placeholder="e.g., CSE-302, Gallery-1, Lab-2"
              className="w-full rounded-lg border border-slate-700 bg-slate-950 py-2 pl-9 pr-3 text-sm text-slate-100 placeholder:text-slate-500 focus:border-emerald-500 focus:outline-none"
            />
          </div>
        </div>

        <div>
          <label htmlFor="ct-topics" className="block text-xs font-medium text-slate-300">
            Covered Topics / Syllabus (Optional)
          </label>
          <textarea
            id="ct-topics"
            rows={3}
            value={coveredTopics}
            onChange={(e) => setCoveredTopics(e.target.value)}
            placeholder="Chapters, lecture notes, or reference links..."
            className="mt-1.5 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-slate-100 placeholder:text-slate-500 focus:border-emerald-500 focus:outline-none"
          />
        </div>

        <div className="flex justify-end gap-2 pt-2">
          <Dialog.Close asChild>
            <button
              type="button"
              className="rounded-lg border border-slate-700 px-3.5 py-2 text-xs font-medium text-slate-300 transition-colors duration-150 ease-out hover:bg-slate-800"
            >
              Cancel
            </button>
          </Dialog.Close>
          <button
            type="submit"
            disabled={submitting || !selectedTeacher}
            className="inline-flex items-center gap-1.5 rounded-lg bg-emerald-600 px-4 py-2 text-xs font-medium text-white transition-[background-color,transform] duration-100 ease-out hover:bg-emerald-500 active:scale-[0.97] disabled:cursor-not-allowed disabled:opacity-50"
          >
            {submitting && <Loader2 className="h-3.5 w-3.5 animate-spin shrink-0" />}
            <span>Submit &amp; Notify Teacher</span>
          </button>
        </div>
      </form>
    </ModalFrame>
  );
}

interface RescheduleCtModalProps {
  event: CtEventSummary | null;
  onOpenChange: (open: boolean) => void;
  conflictMessage: string | null;
  onReloadLatest: () => Promise<void>;
  onSubmit: (
    event: CtEventSummary,
    payload: { newScheduledAt: string; reason: string; expectedVersion: number; examRoom?: string },
  ) => Promise<void>;
}

export function RescheduleCtModal({
  event,
  onOpenChange,
  conflictMessage,
  onReloadLatest,
  onSubmit,
}: RescheduleCtModalProps) {
  const [newScheduledLocal, setNewScheduledLocal] = useState('2026-12-03T10:30');
  const [examRoom, setExamRoom] = useState('');
  const [reason, setReason] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [reloading, setReloading] = useState(false);

  useEffect(() => {
    if (event) {
      const iso = new Date(event.scheduledAt);
      if (!Number.isNaN(iso.getTime())) {
        setNewScheduledLocal(iso.toISOString().slice(0, 16));
      }
      setExamRoom(event.examRoom ?? '');
      setReason('');
    }
  }, [event]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!event || !reason.trim()) return;
    setSubmitting(true);
    try {
      await onSubmit(event, {
        newScheduledAt: new Date(newScheduledLocal).toISOString(),
        reason: reason.trim(),
        expectedVersion: event.version,
        examRoom: examRoom.trim(),
      });
    } finally {
      setSubmitting(false);
    }
  };

  const handleReload = async () => {
    setReloading(true);
    try {
      await onReloadLatest();
    } finally {
      setReloading(false);
    }
  };

  return (
    <ModalFrame
      open={Boolean(event)}
      onOpenChange={onOpenChange}
      title="Reschedule Class Test"
      description={
        event
          ? `Protected by JPA @Version optimistic locking (v${event.version}). The assigned teacher and course followers will be notified.`
          : ''
      }
    >
      {event && (
        <form onSubmit={handleSubmit} className="space-y-4">
          {conflictMessage && (
            <div
              role="alert"
              className="flex flex-col gap-2.5 rounded-xl border border-amber-500/40 bg-amber-500/10 p-3.5 text-xs text-amber-100"
            >
              <div className="flex items-start gap-2">
                <AlertTriangle className="mt-0.5 h-4 w-4 text-amber-400 shrink-0" aria-hidden="true" />
                <p className="leading-relaxed">{conflictMessage}</p>
              </div>
              <button
                type="button"
                disabled={reloading}
                onClick={handleReload}
                className="inline-flex w-fit items-center gap-1.5 rounded-lg bg-amber-500/20 px-3 py-1.5 text-xs font-medium text-amber-200 transition-colors duration-150 ease-out hover:bg-amber-500/30"
              >
                <RefreshCw className={`h-3.5 w-3.5 shrink-0 ${reloading ? 'animate-spin' : ''}`} />
                <span>Reload Latest Schedule</span>
              </button>
            </div>
          )}

          <div>
            <label htmlFor="reschedule-date" className="block text-xs font-medium text-slate-300">
              New Scheduled Date &amp; Time
            </label>
            <input
              id="reschedule-date"
              type="datetime-local"
              value={newScheduledLocal}
              onChange={(e) => setNewScheduledLocal(e.target.value)}
              className="mt-1.5 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-slate-100 tabular-nums focus:border-emerald-500 focus:outline-none"
              required
            />
          </div>

          <div>
            <label htmlFor="reschedule-room" className="block text-xs font-medium text-slate-300">
              Exam Room (Optional)
            </label>
            <input
              id="reschedule-room"
              type="text"
              maxLength={100}
              value={examRoom}
              onChange={(e) => setExamRoom(e.target.value)}
              placeholder="e.g., CSE-302 or Gallery-2"
              className="mt-1.5 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-slate-100 placeholder:text-slate-500 focus:border-emerald-500 focus:outline-none"
            />
          </div>

          <div>
            <label htmlFor="reschedule-reason" className="block text-xs font-medium text-slate-300">
              Reschedule Reason (Recorded in Audit History)
            </label>
            <textarea
              id="reschedule-reason"
              rows={3}
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder="e.g., Clashes with CSE 3200 lab viva"
              className="mt-1.5 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-slate-100 placeholder:text-slate-500 focus:border-emerald-500 focus:outline-none"
              required
            />
          </div>

          <div className="flex justify-end gap-2 pt-2">
            <Dialog.Close asChild>
              <button
                type="button"
                className="rounded-lg border border-slate-700 px-3.5 py-2 text-xs font-medium text-slate-300 transition-colors duration-150 ease-out hover:bg-slate-800"
              >
                Close
              </button>
            </Dialog.Close>
            <button
              type="submit"
              disabled={submitting}
              className="inline-flex items-center gap-1.5 rounded-lg bg-purple-600 px-4 py-2 text-xs font-medium text-white transition-[background-color,transform] duration-100 ease-out hover:bg-purple-500 active:scale-[0.97] disabled:cursor-not-allowed disabled:opacity-50"
            >
              {submitting && <Loader2 className="h-3.5 w-3.5 animate-spin shrink-0" />}
              <span>Confirm Reschedule</span>
            </button>
          </div>
        </form>
      )}
    </ModalFrame>
  );
}

interface CancelCtModalProps {
  event: CtEventSummary | null;
  onOpenChange: (open: boolean) => void;
  onSubmit: (
    event: CtEventSummary,
    payload: { reason: string; expectedVersion: number },
  ) => Promise<void>;
}

export function CancelCtModal({ event, onOpenChange, onSubmit }: CancelCtModalProps) {
  const [reason, setReason] = useState('');
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (event) setReason('');
  }, [event]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!event || !reason.trim()) return;
    setSubmitting(true);
    try {
      await onSubmit(event, { reason: reason.trim(), expectedVersion: event.version });
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <ModalFrame
      open={Boolean(event)}
      onOpenChange={onOpenChange}
      title="Cancel Class Test"
      description="Cancelling transitions this CT to the terminal CANCELLED state and notifies the assigned teacher and course followers."
    >
      {event && (
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label htmlFor="cancel-reason" className="block text-xs font-medium text-slate-300">
              Cancellation Reason
            </label>
            <textarea
              id="cancel-reason"
              rows={3}
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder="e.g., Course teacher merged CT 2 into final assignment"
              className="mt-1.5 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-slate-100 placeholder:text-slate-500 focus:border-rose-500 focus:outline-none"
              required
            />
          </div>

          <div className="flex justify-end gap-2 pt-2">
            <Dialog.Close asChild>
              <button
                type="button"
                className="rounded-lg border border-slate-700 px-3.5 py-2 text-xs font-medium text-slate-300 transition-colors duration-150 ease-out hover:bg-slate-800"
              >
                Keep CT
              </button>
            </Dialog.Close>
            <button
              type="submit"
              disabled={submitting}
              className="inline-flex items-center gap-1.5 rounded-lg bg-rose-600 px-4 py-2 text-xs font-medium text-white transition-[background-color,transform] duration-100 ease-out hover:bg-rose-500 active:scale-[0.97] disabled:cursor-not-allowed disabled:opacity-50"
            >
              {submitting && <Loader2 className="h-3.5 w-3.5 animate-spin shrink-0" />}
              <span>Cancel CT</span>
            </button>
          </div>
        </form>
      )}
    </ModalFrame>
  );
}
