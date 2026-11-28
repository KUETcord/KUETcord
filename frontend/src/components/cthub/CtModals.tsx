import React, { useEffect, useMemo, useState } from 'react';
import * as Dialog from '@radix-ui/react-dialog';
import {
  AlertTriangle,
  Check,
  Loader2,
  MapPin,
  Plus,
  RefreshCw,
  Search,
  Trash2,
  X,
} from 'lucide-react';
import { ctHubApi } from '../../lib/api';
import { getInitials } from '../../lib/format';
import { KUET_DEPARTMENTS, resolveYearTerm, YEAR_TERM_OPTIONS } from '../../lib/yearTerm';
import type {
  CourseOffering,
  CtEventSummary,
  RoomAllocation,
  TeacherSummary,
} from '../../types/domain';

interface RoomRowDraft {
  rollStart: string;
  rollEnd: string;
  room: string;
}

function toRoomAllocations(rows: RoomRowDraft[]): {
  allocations: RoomAllocation[];
  error: string | null;
} {
  const allocations: RoomAllocation[] = [];
  for (const row of rows) {
    const room = row.room.trim();
    if (!room) continue;
    const start = row.rollStart.trim() ? Number.parseInt(row.rollStart.trim(), 10) : null;
    const end = row.rollEnd.trim() ? Number.parseInt(row.rollEnd.trim(), 10) : null;
    if (start !== null && end !== null && start > end) {
      return {
        allocations: [],
        error: `Invalid roll range for ${room}: From Roll (${start}) cannot exceed To Roll (${end}).`,
      };
    }
    allocations.push({ rollStart: start, rollEnd: end, room });
  }
  return { allocations, error: null };
}

function RoomAllocationEditor({
  rows,
  onChange,
}: {
  rows: RoomRowDraft[];
  onChange: (next: RoomRowDraft[]) => void;
}) {
  const addRow = () => {
    const last = rows[rows.length - 1];
    const nextStart = last?.rollEnd ? String(Number.parseInt(last.rollEnd, 10) + 1) : '1';
    const nextEnd = last?.rollEnd ? String(Number.parseInt(last.rollEnd, 10) + 40) : '40';
    onChange([...rows, { rollStart: nextStart, rollEnd: nextEnd, room: '' }]);
  };

  const updateRow = (index: number, patch: Partial<RoomRowDraft>) => {
    onChange(rows.map((r, i) => (i === index ? { ...r, ...patch } : r)));
  };

  const removeRow = (index: number) => {
    onChange(rows.filter((_, i) => i !== index));
  };

  return (
    <div className="space-y-2">
      <div className="flex items-center justify-between gap-2">
        <span className="flex items-center gap-1.5 text-xs font-medium text-slate-300">
          <MapPin className="h-3.5 w-3.5 text-amber-300 shrink-0" aria-hidden="true" />
          <span>Exam Rooms &amp; Roll Ranges (Optional)</span>
        </span>
        <button
          type="button"
          onClick={addRow}
          className="inline-flex items-center gap-1 rounded-md border border-slate-700 bg-slate-950 px-2 py-1 text-[11px] font-medium text-emerald-300 hover:bg-slate-800 active:scale-[0.97]"
        >
          <Plus className="h-3 w-3 shrink-0" />
          <span>Add Room</span>
        </button>
      </div>

      {rows.length === 0 ? (
        <p className="rounded-lg border border-dashed border-slate-800 px-3 py-2 text-[11px] text-slate-500">
          No rooms assigned yet. Click &ldquo;Add Room&rdquo; for roll ranges (e.g., Roll 1–40: CSE-101, 41–80: CSE-105).
        </p>
      ) : (
        <div className="space-y-2">
          {rows.map((row, idx) => (
            <div key={idx} className="grid grid-cols-12 items-center gap-2">
              <input
                type="number"
                min={1}
                aria-label={`Room ${idx + 1} from roll`}
                value={row.rollStart}
                onChange={(e) => updateRow(idx, { rollStart: e.target.value })}
                placeholder="From (1)"
                className="col-span-3 rounded-lg border border-slate-700 bg-slate-950 px-2.5 py-1.5 text-xs text-slate-100 tabular-nums placeholder:text-slate-500 focus:border-emerald-500 focus:outline-none"
              />
              <input
                type="number"
                min={1}
                aria-label={`Room ${idx + 1} to roll`}
                value={row.rollEnd}
                onChange={(e) => updateRow(idx, { rollEnd: e.target.value })}
                placeholder="To (40)"
                className="col-span-3 rounded-lg border border-slate-700 bg-slate-950 px-2.5 py-1.5 text-xs text-slate-100 tabular-nums placeholder:text-slate-500 focus:border-emerald-500 focus:outline-none"
              />
              <input
                type="text"
                maxLength={100}
                aria-label={`Room ${idx + 1} name`}
                value={row.room}
                onChange={(e) => updateRow(idx, { room: e.target.value })}
                placeholder="Room (e.g., CSE-101)"
                className="col-span-5 rounded-lg border border-slate-700 bg-slate-950 px-2.5 py-1.5 text-xs text-slate-100 placeholder:text-slate-500 focus:border-emerald-500 focus:outline-none"
              />
              <button
                type="button"
                aria-label={`Remove room row ${idx + 1}`}
                onClick={() => removeRow(idx)}
                className="col-span-1 flex justify-center rounded-md p-1.5 text-slate-400 hover:bg-rose-500/15 hover:text-rose-300"
              >
                <Trash2 className="h-3.5 w-3.5" />
              </button>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

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
  defaultDepartment?: string | null;
  isCrOnly?: boolean;
  demoTeachers: TeacherSummary[];
  isDemoMode: boolean;
  onSubmit: (payload: {
    courseOfferingId: number;
    teacherId: number;
    teacherName: string;
    coTeacherId?: number;
    coTeacherName?: string;
    title: string;
    scheduledAt: string;
    durationMinutes: number;
    roomAllocations?: RoomAllocation[];
    coveredTopics?: string;
    targetDepartment?: string;
    targetYearTerm?: string;
  }) => Promise<void>;
}

export function ProposeCtModal({
  open,
  onOpenChange,
  courses,
  defaultYearTerm,
  defaultDepartment,
  isCrOnly = false,
  demoTeachers,
  isDemoMode,
  onSubmit,
}: ProposeCtModalProps) {
  const [targetDepartment, setTargetDepartment] = useState<string>(defaultDepartment ?? 'CSE');
  const [postingYearTerm, setPostingYearTerm] = useState<string>(defaultYearTerm ?? '');
  const [courseOfferingId, setCourseOfferingId] = useState<string>(
    courses[0] ? String(courses[0].id) : '',
  );
  const [teacherQuery, setTeacherQuery] = useState('');
  const [teacherResults, setTeacherResults] = useState<TeacherSummary[]>(demoTeachers);
  const [selectedTeachers, setSelectedTeachers] = useState<TeacherSummary[]>([]);
  const [searchingTeachers, setSearchingTeachers] = useState(false);

  const [title, setTitle] = useState('');
  const [scheduledLocal, setScheduledLocal] = useState('2026-12-01T10:00');
  const [durationMinutes, setDurationMinutes] = useState('45');
  const [roomRows, setRoomRows] = useState<RoomRowDraft[]>([
    { rollStart: '1', rollEnd: '40', room: '' },
  ]);
  const [coveredTopics, setCoveredTopics] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    if (defaultDepartment) {
      setTargetDepartment(defaultDepartment);
    }
  }, [open, defaultDepartment]);

  const selectedCourse = useMemo(
    () => courses.find((c) => String(c.id) === courseOfferingId) ?? null,
    [courses, courseOfferingId],
  );

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

  const toggleTeacher = (teacher: TeacherSummary) => {
    setSelectedTeachers((prev) => {
      if (prev.some((t) => t.id === teacher.id)) {
        return prev.filter((t) => t.id !== teacher.id);
      }
      if (prev.length >= 2) {
        return [prev[0], teacher];
      }
      return [...prev, teacher];
    });
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!courseOfferingId || selectedTeachers.length === 0 || !title.trim() || !scheduledLocal) {
      setError('Please select a course, assign 1 or 2 course teachers, and complete required fields.');
      return;
    }

    const { allocations, error: roomErr } = toRoomAllocations(roomRows);
    if (roomErr) {
      setError(roomErr);
      return;
    }

    const primary = selectedTeachers[0];
    const coTeacher = selectedTeachers[1];

    setSubmitting(true);
    setError(null);
    try {
      await onSubmit({
        courseOfferingId: Number(courseOfferingId),
        teacherId: primary.id,
        teacherName: primary.displayName,
        coTeacherId: coTeacher?.id,
        coTeacherName: coTeacher?.displayName,
        title: title.trim(),
        scheduledAt: new Date(scheduledLocal).toISOString(),
        durationMinutes: Number(durationMinutes) || 45,
        roomAllocations: allocations.length > 0 ? allocations : undefined,
        coveredTopics: coveredTopics.trim() || undefined,
        targetDepartment: targetDepartment || undefined,
        targetYearTerm: postingYearTerm || undefined,
      });
      setTitle('');
      setRoomRows([{ rollStart: '1', rollEnd: '40', room: '' }]);
      setCoveredTopics('');
      setTeacherQuery('');
      setSelectedTeachers([]);
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
      description="Assign 1 or 2 combined course teachers and optional roll-range exam rooms. Both teachers are notified for approval."
    >
      <form onSubmit={handleSubmit} className="space-y-4">
        {error && (
          <div className="rounded-lg border border-rose-500/30 bg-rose-500/10 px-3 py-2 text-xs text-rose-200">
            {error}
          </div>
        )}

        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <div>
            <label htmlFor="ct-target-dept" className="block text-xs font-medium text-slate-300">
              Target Student Department
            </label>
            <select
              id="ct-target-dept"
              value={targetDepartment}
              disabled={isCrOnly}
              onChange={(e) => setTargetDepartment(e.target.value)}
              className="mt-1.5 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-slate-100 disabled:opacity-60 focus:border-emerald-500 focus:outline-none"
            >
              {KUET_DEPARTMENTS.map((dept) => (
                <option key={dept} value={dept}>
                  {dept}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label htmlFor="ct-post-year-term" className="block text-xs font-medium text-slate-300">
              Target Year &amp; Term
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
        </div>

        <div>
          <label htmlFor="ct-course" className="block text-xs font-medium text-slate-300">
            Course Offering (All KUET Departments)
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
                [{course.department}] {course.courseCode} ({resolveYearTerm(course.yearTerm, course.semester) ?? course.semester}) — {course.courseTitle}
              </option>
            ))}
          </select>
          {selectedCourse &&
            targetDepartment &&
            selectedCourse.department.toUpperCase() !== targetDepartment.toUpperCase() && (
              <p className="mt-1 text-[11px] text-amber-300">
                Cross-department course ({selectedCourse.department}) — will appear in {targetDepartment} CT Hub &amp; Calendar.
              </p>
            )}
        </div>

        {/* Searchable Combined Teacher Picker (1 or 2 teachers) */}
        <div>
          <div className="flex items-center justify-between gap-2">
            <label htmlFor="ct-teacher-search" className="block text-xs font-medium text-slate-300">
              Course Teacher(s) — Pick 1 or 2 Combined
            </label>
            <span className="text-[11px] text-slate-400 tabular-nums">
              {selectedTeachers.length}/2 selected
            </span>
          </div>
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

          {selectedTeachers.length > 0 && (
            <div className="mt-2 flex flex-wrap gap-1.5">
              {selectedTeachers.map((teacher, idx) => (
                <div
                  key={teacher.id}
                  className="inline-flex items-center gap-2 rounded-lg border border-emerald-500/30 bg-emerald-500/10 px-2.5 py-1.5 text-xs text-emerald-200"
                >
                  <span className="rounded bg-emerald-500/20 px-1 text-[10px] font-semibold">
                    {idx === 0 ? 'Primary' : 'Combined'}
                  </span>
                  <span className="font-medium truncate max-w-[20ch]">{teacher.displayName}</span>
                  <button
                    type="button"
                    onClick={() => toggleTeacher(teacher)}
                    className="text-emerald-300 hover:text-white shrink-0"
                    aria-label={`Remove ${teacher.displayName}`}
                  >
                    <X className="h-3.5 w-3.5" />
                  </button>
                </div>
              ))}
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
                const isSelected = selectedTeachers.some((t) => t.id === teacher.id);
                return (
                  <button
                    key={teacher.id}
                    type="button"
                    role="option"
                    aria-selected={isSelected}
                    onClick={() => toggleTeacher(teacher)}
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

        <RoomAllocationEditor rows={roomRows} onChange={setRoomRows} />

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
            disabled={submitting || selectedTeachers.length === 0}
            className="inline-flex items-center gap-1.5 rounded-lg bg-emerald-600 px-4 py-2 text-xs font-medium text-white transition-[background-color,transform] duration-100 ease-out hover:bg-emerald-500 active:scale-[0.97] disabled:cursor-not-allowed disabled:opacity-50"
          >
            {submitting && <Loader2 className="h-3.5 w-3.5 animate-spin shrink-0" />}
            <span>
              Submit &amp; Notify {selectedTeachers.length > 1 ? 'Both Teachers' : 'Teacher'}
            </span>
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
    payload: {
      newScheduledAt: string;
      reason: string;
      expectedVersion: number;
      roomAllocations?: RoomAllocation[];
    },
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
  const [roomRows, setRoomRows] = useState<RoomRowDraft[]>([]);
  const [reason, setReason] = useState('');
  const [roomError, setRoomError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [reloading, setReloading] = useState(false);

  useEffect(() => {
    if (event) {
      const iso = new Date(event.scheduledAt);
      if (!Number.isNaN(iso.getTime())) {
        setNewScheduledLocal(iso.toISOString().slice(0, 16));
      }
      if (event.roomAllocations && event.roomAllocations.length > 0) {
        setRoomRows(
          event.roomAllocations.map((a) => ({
            rollStart: a.rollStart !== null ? String(a.rollStart) : '',
            rollEnd: a.rollEnd !== null ? String(a.rollEnd) : '',
            room: a.room,
          })),
        );
      } else if (event.examRoom) {
        setRoomRows([{ rollStart: '', rollEnd: '', room: event.examRoom }]);
      } else {
        setRoomRows([]);
      }
      setRoomError(null);
      setReason('');
    }
  }, [event]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!event || !reason.trim()) return;
    const { allocations, error } = toRoomAllocations(roomRows);
    if (error) {
      setRoomError(error);
      return;
    }
    setRoomError(null);
    setSubmitting(true);
    try {
      await onSubmit(event, {
        newScheduledAt: new Date(newScheduledLocal).toISOString(),
        reason: reason.trim(),
        expectedVersion: event.version,
        roomAllocations: allocations,
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
          ? `Protected by JPA @Version optimistic locking (v${event.version}). Assigned teacher(s) and course followers will be notified.`
          : ''
      }
    >
      {event && (
        <form onSubmit={handleSubmit} className="space-y-4">
          {(conflictMessage || roomError) && (
            <div
              role="alert"
              className="flex flex-col gap-2.5 rounded-xl border border-amber-500/40 bg-amber-500/10 p-3.5 text-xs text-amber-100"
            >
              <div className="flex items-start gap-2">
                <AlertTriangle className="mt-0.5 h-4 w-4 text-amber-400 shrink-0" aria-hidden="true" />
                <p className="leading-relaxed">{roomError ?? conflictMessage}</p>
              </div>
              {conflictMessage && (
                <button
                  type="button"
                  disabled={reloading}
                  onClick={handleReload}
                  className="inline-flex w-fit items-center gap-1.5 rounded-lg bg-amber-500/20 px-3 py-1.5 text-xs font-medium text-amber-200 transition-colors duration-150 ease-out hover:bg-amber-500/30"
                >
                  <RefreshCw className={`h-3.5 w-3.5 shrink-0 ${reloading ? 'animate-spin' : ''}`} />
                  <span>Reload Latest Schedule</span>
                </button>
              )}
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

          <RoomAllocationEditor rows={roomRows} onChange={setRoomRows} />

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
      description="Cancelling transitions this CT to the terminal CANCELLED state and notifies the assigned teacher(s) and course followers."
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
