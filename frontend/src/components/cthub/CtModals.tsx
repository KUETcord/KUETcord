import React, { useEffect, useState } from 'react';
import * as Dialog from '@radix-ui/react-dialog';
import { AlertTriangle, Loader2, RefreshCw, X } from 'lucide-react';
import type { CourseOffering, CtEventSummary } from '../../types/domain';

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
        <Dialog.Content className="kuet-dialog-content fixed left-1/2 top-1/2 z-50 w-[calc(100%-2rem)] max-w-lg -translate-x-1/2 -translate-y-1/2 rounded-2xl border border-slate-800 bg-slate-900 p-6 shadow-2xl focus:outline-none">
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
  onSubmit: (payload: {
    courseOfferingId: number;
    title: string;
    scheduledAt: string;
    durationMinutes: number;
    coveredTopics?: string;
  }) => Promise<void>;
}

export function ProposeCtModal({ open, onOpenChange, courses, onSubmit }: ProposeCtModalProps) {
  const [courseOfferingId, setCourseOfferingId] = useState<string>(courses[0] ? String(courses[0].id) : '');
  const [title, setTitle] = useState('');
  const [scheduledLocal, setScheduledLocal] = useState('2026-12-01T10:00');
  const [durationMinutes, setDurationMinutes] = useState('45');
  const [coveredTopics, setCoveredTopics] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (open && courses.length > 0 && !courseOfferingId) {
      setCourseOfferingId(String(courses[0].id));
    }
  }, [open, courses, courseOfferingId]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!courseOfferingId || !title.trim() || !scheduledLocal) {
      setError('Please complete all required fields.');
      return;
    }

    setSubmitting(true);
    setError(null);
    try {
      await onSubmit({
        courseOfferingId: Number(courseOfferingId),
        title: title.trim(),
        scheduledAt: new Date(scheduledLocal).toISOString(),
        durationMinutes: Number(durationMinutes) || 45,
        coveredTopics: coveredTopics.trim() || undefined,
      });
      setTitle('');
      setCoveredTopics('');
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
      description="Submitted CTs start as UNVERIFIED until confirmed by a Class Representative or Moderator."
    >
      <form onSubmit={handleSubmit} className="space-y-4">
        {error && (
          <div className="rounded-lg border border-rose-500/30 bg-rose-500/10 px-3 py-2 text-xs text-rose-200">
            {error}
          </div>
        )}

        <div>
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
            {courses.map((course) => (
              <option key={course.id} value={course.id}>
                {course.courseCode} — {course.courseTitle}
              </option>
            ))}
          </select>
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

        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <div>
            <label htmlFor="ct-date" className="block text-xs font-medium text-slate-300">
              Scheduled Date & Time
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
              Duration (minutes)
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
            disabled={submitting}
            className="inline-flex items-center gap-1.5 rounded-lg bg-emerald-600 px-4 py-2 text-xs font-medium text-white transition-colors duration-150 ease-out hover:bg-emerald-500 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {submitting && <Loader2 className="h-3.5 w-3.5 animate-spin shrink-0" />}
            <span>Submit Proposal</span>
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
    payload: { newScheduledAt: string; reason: string; expectedVersion: number },
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
  const [reason, setReason] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [reloading, setReloading] = useState(false);

  useEffect(() => {
    if (event) {
      const iso = new Date(event.scheduledAt);
      if (!Number.isNaN(iso.getTime())) {
        setNewScheduledLocal(iso.toISOString().slice(0, 16));
      }
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
          ? `Protected by JPA @Version optimistic locking (current version v${event.version}).`
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
              New Scheduled Date & Time
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
              className="inline-flex items-center gap-1.5 rounded-lg bg-purple-600 px-4 py-2 text-xs font-medium text-white transition-colors duration-150 ease-out hover:bg-purple-500 disabled:cursor-not-allowed disabled:opacity-50"
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
      description="Cancelling transitions this CT to the terminal CANCELLED state and alerts course followers."
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
              className="inline-flex items-center gap-1.5 rounded-lg bg-rose-600 px-4 py-2 text-xs font-medium text-white transition-colors duration-150 ease-out hover:bg-rose-500 disabled:cursor-not-allowed disabled:opacity-50"
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
