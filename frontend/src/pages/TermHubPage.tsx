import React, { useCallback, useEffect, useMemo, useState } from 'react';
import * as Dialog from '@radix-ui/react-dialog';
import {
  CalendarCheck2,
  Clock,
  GraduationCap,
  MapPin,
  Pencil,
  Plus,
  Trash2,
  X,
} from 'lucide-react';
import { toast } from 'sonner';
import { extractErrorMessage, useAuth } from '../context/AuthContext';
import { DEMO_COURSES, DEMO_TERM_FINAL_SCHEDULES } from '../fixtures/ctHubFixtures';
import { ctHubApi, termFinalApi } from '../lib/api';
import { cn, pluralize } from '../lib/format';
import { KUET_DEPARTMENTS, YEAR_TERM_OPTIONS } from '../lib/yearTerm';
import type {
  CourseOffering,
  RoomAllocation,
  TermFinalSchedule,
  UpsertTermFinalSchedulePayload,
} from '../types/domain';

interface ExamRowDraft {
  examDate: string;
  courseOfferingId: string;
  courseCode: string;
  courseTitle: string;
}

interface RoomRowDraft {
  rollStart: string;
  rollEnd: string;
  room: string;
}

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

function summarizeRoomAllocations(allocations: RoomAllocation[]): string | null {
  if (allocations.length === 0) return null;
  return allocations
    .map((a) =>
      a.rollStart !== null && a.rollEnd !== null
        ? `${a.room} (${a.rollStart}-${a.rollEnd})`
        : a.rollStart !== null
          ? `${a.room} (${a.rollStart}+)`
          : a.room,
    )
    .join(', ');
}

function formatExamCountdown(isoDate: string): { label: string; tone: 'upcoming' | 'today' | 'past' } {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(isoDate)) {
    return { label: '—', tone: 'past' };
  }
  const [y, m, d] = isoDate.split('-').map(Number);
  const examUtc = Date.UTC(y, m - 1, d);
  const now = new Date();
  const todayUtc = Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate());
  const diffDays = Math.round((examUtc - todayUtc) / 86_400_000);
  if (diffDays === 0) return { label: 'Today', tone: 'today' };
  if (diffDays === 1) return { label: 'Tomorrow', tone: 'upcoming' };
  if (diffDays > 1) return { label: `In ${diffDays}d`, tone: 'upcoming' };
  return { label: `${Math.abs(diffDays)}d ago`, tone: 'past' };
}

export function computeDayOfWeek(isoDate: string): string {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(isoDate)) return '—';
  const [y, m, d] = isoDate.split('-').map(Number);
  const utcDate = new Date(Date.UTC(y, m - 1, d));
  if (Number.isNaN(utcDate.getTime())) return '—';
  return new Intl.DateTimeFormat('en-US', { weekday: 'long', timeZone: 'UTC' }).format(utcDate);
}

function formatClock(timeStr: string): string {
  if (!timeStr) return '';
  const [hStr, mStr] = timeStr.split(':');
  const h = Number.parseInt(hStr, 10);
  const m = Number.parseInt(mStr ?? '0', 10);
  if (Number.isNaN(h) || Number.isNaN(m)) return timeStr;
  const suffix = h >= 12 ? 'PM' : 'AM';
  const hour12 = h % 12 === 0 ? 12 : h % 12;
  return `${hour12}:${String(m).padStart(2, '0')} ${suffix}`;
}

function toInputTime(timeStr: string): string {
  if (!timeStr) return '09:30';
  return timeStr.slice(0, 5);
}

const EMPTY_ROW = (): ExamRowDraft => ({
  examDate: '',
  courseOfferingId: '',
  courseCode: '',
  courseTitle: '',
});

const DEFAULT_ROOM_ROWS = (): RoomRowDraft[] => [
  { rollStart: '1', rollEnd: '40', room: '' },
];

export function TermHubPage() {
  const { user, isDemoMode } = useAuth();

  const isStaff =
    user?.globalRole === 'ADMIN' ||
    user?.globalRole === 'MODERATOR' ||
    Boolean(user?.roles?.includes('ROLE_ADMIN') || user?.roles?.includes('ROLE_MODERATOR'));
  const isCr =
    user?.globalRole === 'CR' || Boolean(user?.roles?.includes('ROLE_CR'));
  const canManage = isStaff || isCr;

  const canMutateSchedule = (schedule: TermFinalSchedule): boolean => {
    if (isStaff) return true;
    if (!isCr || !user?.department) return false;
    return user.department.trim().toUpperCase() === schedule.targetDepartment.trim().toUpperCase();
  };

  const [schedules, setSchedules] = useState<TermFinalSchedule[]>(DEMO_TERM_FINAL_SCHEDULES);
  const [courses, setCourses] = useState<CourseOffering[]>(DEMO_COURSES);
  const [loading, setLoading] = useState(false);

  // Filters: default to My Batch / user's own department
  const [myBatchOnly, setMyBatchOnly] = useState<boolean>(Boolean(user?.yearTerm));
  const [departmentFilter, setDepartmentFilter] = useState<string>(user?.department ?? '');
  const [yearTermFilter, setYearTermFilter] = useState<string>('');
  const [batchFilter, setBatchFilter] = useState<string>('');
  const [sessionFilter, setSessionFilter] = useState<string>('');

  // Modal state
  const [modalOpen, setModalOpen] = useState(false);
  const [editingSchedule, setEditingSchedule] = useState<TermFinalSchedule | null>(null);
  const [title, setTitle] = useState('');
  const [targetDepartment, setTargetDepartment] = useState(user?.department ?? 'CSE');
  const [yearTerm, setYearTerm] = useState(user?.yearTerm ?? '3-2');
  const [batch, setBatch] = useState(user?.batch ?? '2k20');
  const [session, setSession] = useState('2025-2026');
  const [examStartTime, setExamStartTime] = useState('09:30');
  const [examEndTime, setExamEndTime] = useState('12:30');
  const [roomRows, setRoomRows] = useState<RoomRowDraft[]>(DEFAULT_ROOM_ROWS());
  const [rows, setRows] = useState<ExamRowDraft[]>([EMPTY_ROW()]);
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const studentClassRoll = useMemo(() => parseClassRoll(user?.rollNumber), [user?.rollNumber]);

  useEffect(() => {
    setMyBatchOnly(Boolean(user?.yearTerm));
    if (user?.department) {
      setDepartmentFilter(user.department);
    }
  }, [user?.id, user?.yearTerm, user?.department]);

  const effectiveDepartment = myBatchOnly && user?.department ? user.department : departmentFilter;
  const effectiveYearTerm = myBatchOnly && user?.yearTerm ? user.yearTerm : yearTermFilter;

  const loadData = useCallback(async () => {
    if (isDemoMode) {
      setSchedules(DEMO_TERM_FINAL_SCHEDULES);
      setCourses(DEMO_COURSES);
      return;
    }

    setLoading(true);
    try {
      const [fetchedSchedules, fetchedCourses] = await Promise.all([
        termFinalApi.list(),
        ctHubApi.listCourses(),
      ]);
      setSchedules(fetchedSchedules);
      setCourses(fetchedCourses);
    } catch (err) {
      toast.error(extractErrorMessage(err, 'Could not load Term Hub schedules'));
    } finally {
      setLoading(false);
    }
  }, [isDemoMode]);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  const filteredSchedules = useMemo(() => {
    return schedules.filter((s) => {
      if (effectiveDepartment && s.targetDepartment.toUpperCase() !== effectiveDepartment.toUpperCase()) {
        return false;
      }
      if (effectiveYearTerm && s.yearTerm !== effectiveYearTerm) {
        return false;
      }
      if (batchFilter.trim() && !s.batch.toLowerCase().includes(batchFilter.trim().toLowerCase())) {
        return false;
      }
      if (sessionFilter.trim() && !s.session.toLowerCase().includes(sessionFilter.trim().toLowerCase())) {
        return false;
      }
      return true;
    });
  }, [schedules, effectiveDepartment, effectiveYearTerm, batchFilter, sessionFilter]);

  const openCreateModal = () => {
    setEditingSchedule(null);
    setTitle('');
    setTargetDepartment(user?.department ?? 'CSE');
    setYearTerm(user?.yearTerm ?? '3-2');
    setBatch(user?.batch && user.batch !== 'Administration' && user.batch !== 'Faculty' ? user.batch : '2k22');
    setSession('2025-2026');
    setExamStartTime('09:30');
    setExamEndTime('12:30');
    setRoomRows(DEFAULT_ROOM_ROWS());
    setRows([EMPTY_ROW()]);
    setFormError(null);
    setModalOpen(true);
  };

  const openEditModal = (schedule: TermFinalSchedule) => {
    setEditingSchedule(schedule);
    setTitle(schedule.title);
    setTargetDepartment(schedule.targetDepartment);
    setYearTerm(schedule.yearTerm);
    setBatch(schedule.batch);
    setSession(schedule.session);
    setExamStartTime(toInputTime(schedule.examStartTime));
    setExamEndTime(toInputTime(schedule.examEndTime));
    if (schedule.roomAllocations && schedule.roomAllocations.length > 0) {
      setRoomRows(
        schedule.roomAllocations.map((a) => ({
          rollStart: a.rollStart !== null ? String(a.rollStart) : '',
          rollEnd: a.rollEnd !== null ? String(a.rollEnd) : '',
          room: a.room,
        })),
      );
    } else if (schedule.examRoom) {
      setRoomRows([{ rollStart: '', rollEnd: '', room: schedule.examRoom }]);
    } else {
      setRoomRows(DEFAULT_ROOM_ROWS());
    }
    setRows(
      schedule.entries.map((entry) => ({
        examDate: entry.examDate,
        courseOfferingId: entry.courseOfferingId ? String(entry.courseOfferingId) : '',
        courseCode: entry.courseCode,
        courseTitle: entry.courseTitle,
      })),
    );
    setFormError(null);
    setModalOpen(true);
  };

  const addRoomRow = () => {
    setRoomRows((prev) => {
      const last = prev[prev.length - 1];
      const parsedEnd = last?.rollEnd ? Number.parseInt(last.rollEnd, 10) : Number.NaN;
      const nextStart = Number.isFinite(parsedEnd) ? String(parsedEnd + 1) : '1';
      const nextEnd = Number.isFinite(parsedEnd) ? String(parsedEnd + 40) : '40';
      return [...prev, { rollStart: nextStart, rollEnd: nextEnd, room: '' }];
    });
  };

  const updateRoomRow = (index: number, patch: Partial<RoomRowDraft>) => {
    setRoomRows((prev) => prev.map((r, i) => (i === index ? { ...r, ...patch } : r)));
  };

  const removeRoomRow = (index: number) => {
    setRoomRows((prev) => prev.filter((_, i) => i !== index));
  };

  const updateRow = (index: number, patch: Partial<ExamRowDraft>) => {
    setRows((prev) => prev.map((r, i) => (i === index ? { ...r, ...patch } : r)));
  };

  const handleCourseSelect = (index: number, offeringIdStr: string) => {
    const matched = courses.find((c) => String(c.id) === offeringIdStr);
    if (matched) {
      updateRow(index, {
        courseOfferingId: offeringIdStr,
        courseCode: matched.courseCode,
        courseTitle: matched.courseTitle,
      });
    } else {
      updateRow(index, { courseOfferingId: '' });
    }
  };

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    setFormError(null);

    if (!title.trim() || !targetDepartment.trim() || !yearTerm.trim() || !batch.trim() || !session.trim()) {
      setFormError('Please fill in Title, Department, Year-Term, Batch, and Session.');
      return;
    }
    if (examEndTime <= examStartTime) {
      setFormError('Exam end time must be after exam start time.');
      return;
    }

    const { allocations, error: roomErr } = toRoomAllocations(roomRows);
    if (roomErr) {
      setFormError(roomErr);
      return;
    }

    const validRows = rows.filter((r) => r.examDate.trim() !== '');
    if (validRows.length === 0) {
      setFormError('Add at least one exam row with an exam date.');
      return;
    }
    for (const r of validRows) {
      if (!r.courseOfferingId && (!r.courseCode.trim() || !r.courseTitle.trim())) {
        setFormError('Each exam row must pick a course offering or specify both course code and title.');
        return;
      }
    }

    const summaryRoom = summarizeRoomAllocations(allocations);
    const payload: UpsertTermFinalSchedulePayload = {
      title: title.trim(),
      targetDepartment: targetDepartment.trim().toUpperCase(),
      yearTerm: yearTerm.trim(),
      batch: batch.trim(),
      session: session.trim(),
      examStartTime,
      examEndTime,
      examRoom: summaryRoom,
      roomAllocations: allocations,
      entries: validRows.map((r) => ({
        examDate: r.examDate,
        courseOfferingId: r.courseOfferingId ? Number(r.courseOfferingId) : null,
        courseCode: r.courseCode.trim() || undefined,
        courseTitle: r.courseTitle.trim() || undefined,
      })),
    };

    setSubmitting(true);
    try {
      if (isDemoMode) {
        const synthesized: TermFinalSchedule = {
          id: editingSchedule?.id ?? Date.now(),
          title: payload.title,
          targetDepartment: payload.targetDepartment,
          yearTerm: payload.yearTerm,
          batch: payload.batch,
          session: payload.session,
          examStartTime: `${payload.examStartTime}:00`,
          examEndTime: `${payload.examEndTime}:00`,
          examRoom: summaryRoom,
          roomAllocations: allocations,
          createdById: user?.id ?? 2,
          createdByName: user?.displayName ?? 'Karim Rahman',
          entries: payload.entries.map((entry, idx) => {
            const matchedCourse = entry.courseOfferingId
              ? courses.find((c) => c.id === entry.courseOfferingId)
              : undefined;
            return {
              id: idx + 1,
              examDate: entry.examDate,
              dayOfWeek: computeDayOfWeek(entry.examDate),
              courseOfferingId: entry.courseOfferingId ?? null,
              courseCode: entry.courseCode ?? matchedCourse?.courseCode ?? '',
              courseTitle: entry.courseTitle ?? matchedCourse?.courseTitle ?? '',
              courseDepartment: matchedCourse?.department ?? null,
              examRoom: null,
            };
          }),
          createdAt: editingSchedule?.createdAt ?? new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        };
        if (editingSchedule) {
          setSchedules((prev) => prev.map((s) => (s.id === synthesized.id ? synthesized : s)));
          toast.success('Term final schedule updated');
        } else {
          setSchedules((prev) => [synthesized, ...prev]);
          toast.success('Term final schedule uploaded');
        }
        setModalOpen(false);
        return;
      }

      if (editingSchedule) {
        const updated = await termFinalApi.update(editingSchedule.id, payload);
        setSchedules((prev) => prev.map((s) => (s.id === updated.id ? updated : s)));
        toast.success('Term final schedule updated');
      } else {
        const created = await termFinalApi.create(payload);
        setSchedules((prev) => [created, ...prev]);
        toast.success('Term final schedule uploaded and synced to Calendar');
      }
      setModalOpen(false);
    } catch (err) {
      setFormError(extractErrorMessage(err, 'Failed to save Term Final schedule'));
    } finally {
      setSubmitting(false);
    }
  };

  const handleDelete = async (schedule: TermFinalSchedule) => {
    try {
      if (isDemoMode) {
        setSchedules((prev) => prev.filter((s) => s.id !== schedule.id));
        toast('Term final schedule removed');
        return;
      }
      await termFinalApi.remove(schedule.id);
      setSchedules((prev) => prev.filter((s) => s.id !== schedule.id));
      toast('Term final schedule removed');
    } catch (err) {
      toast.error(extractErrorMessage(err, 'Could not delete schedule'));
    }
  };

  return (
    <div className="space-y-6 pb-16">
      {/* Header */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div className="min-w-0">
          <div className="flex items-center gap-2.5">
            <CalendarCheck2 className="h-6 w-6 text-rose-400 shrink-0" aria-hidden="true" />
            <h2 className="text-2xl font-bold tracking-[-0.02em] text-white sm:text-3xl">
              Term Hub
            </h2>
          </div>
          <p className="mt-1 max-w-[65ch] text-sm leading-relaxed text-slate-400">
            Official Term Final Examination routines by department, Year–Term, batch, and session. Every exam date automatically calculates its day of week and syncs to the Academic Calendar.
          </p>
        </div>

        {canManage && (
          <button
            type="button"
            onClick={openCreateModal}
            className="inline-flex items-center justify-center gap-2 rounded-xl bg-rose-600 px-4 py-2.5 text-sm font-medium text-white shadow-lg shadow-rose-950/50 transition-[background-color,transform] duration-100 ease-out hover:bg-rose-500 active:scale-[0.97] shrink-0"
          >
            <Plus className="h-4 w-4 shrink-0" aria-hidden="true" />
            <span>Upload Schedule</span>
          </button>
        )}
      </div>

      {/* My Batch / All Terms Quick Toggle */}
      {user?.yearTerm && (
        <div className="flex flex-wrap items-center gap-2">
          <button
            type="button"
            aria-pressed={myBatchOnly}
            onClick={() => setMyBatchOnly(true)}
            className={cn(
              'inline-flex items-center gap-1.5 rounded-full border px-3 py-1 text-xs font-medium transition-[background-color,border-color,transform] duration-100 ease-out active:scale-[0.97]',
              myBatchOnly
                ? 'border-rose-500/50 bg-rose-500/15 text-rose-200'
                : 'border-slate-800 bg-slate-900/50 text-slate-400 hover:text-slate-200',
            )}
          >
            <GraduationCap className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
            <span className="tabular-nums">
              My Batch ({user.yearTerm}
              {user.department ? ` · ${user.department}` : ''})
            </span>
          </button>
          <button
            type="button"
            aria-pressed={!myBatchOnly}
            onClick={() => {
              setMyBatchOnly(false);
              setDepartmentFilter('');
              setYearTermFilter('');
            }}
            className={cn(
              'rounded-full border px-3 py-1 text-xs font-medium transition-[background-color,border-color,transform] duration-100 ease-out active:scale-[0.97]',
              !myBatchOnly
                ? 'border-sky-500/50 bg-sky-500/15 text-sky-200'
                : 'border-slate-800 bg-slate-900/50 text-slate-400 hover:text-slate-200',
            )}
          >
            All Terms
          </button>
        </div>
      )}

      {/* Filter Bar */}
      <section
        aria-label="Filter Term Hub schedules"
        className="grid grid-cols-1 gap-3 rounded-xl border border-slate-800 bg-slate-900/50 p-4 sm:grid-cols-2 lg:grid-cols-4"
      >
        <select
          aria-label="Filter schedules by department"
          value={effectiveDepartment}
          onChange={(e) => {
            setMyBatchOnly(false);
            setDepartmentFilter(e.target.value);
          }}
          className="rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-slate-100 focus:border-rose-500 focus:outline-none"
        >
          <option value="">All Departments</option>
          {KUET_DEPARTMENTS.map((dept) => (
            <option key={dept} value={dept}>
              {dept}
            </option>
          ))}
        </select>

        <select
          aria-label="Filter schedules by Year-Term"
          value={effectiveYearTerm}
          onChange={(e) => {
            setMyBatchOnly(false);
            setYearTermFilter(e.target.value);
          }}
          className="rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-slate-100 focus:border-rose-500 focus:outline-none"
        >
          <option value="">All Year–Terms</option>
          {YEAR_TERM_OPTIONS.map((yt) => (
            <option key={yt.code} value={yt.code}>
              {yt.label}
            </option>
          ))}
        </select>

        <input
          type="text"
          aria-label="Filter by batch"
          value={batchFilter}
          onChange={(e) => setBatchFilter(e.target.value)}
          placeholder="Batch (e.g., 2k20, 2k22)"
          className="rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-slate-100 placeholder:text-slate-500 focus:border-rose-500 focus:outline-none"
        />

        <input
          type="text"
          aria-label="Filter by session"
          value={sessionFilter}
          onChange={(e) => setSessionFilter(e.target.value)}
          placeholder="Session (e.g., 2025-2026)"
          className="rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-slate-100 placeholder:text-slate-500 focus:border-rose-500 focus:outline-none"
        />
      </section>

      {/* Schedule List */}
      <div className="flex items-center justify-between">
        <h3 className="text-xs font-semibold uppercase tracking-wider text-slate-400 tabular-nums">
          Published Routines ({pluralize(filteredSchedules.length, 'schedule', 'schedules')})
        </h3>
      </div>

      {loading ? (
        <div className="rounded-2xl border border-slate-800 bg-slate-900/50 p-10 text-center text-sm text-slate-400">
          Loading Term Final schedules…
        </div>
      ) : filteredSchedules.length === 0 ? (
        <div className="rounded-2xl border border-dashed border-slate-800 p-10 text-center text-sm text-slate-400">
          No Term Final schedules match your current filter.
        </div>
      ) : (
        <div className="space-y-5">
          {filteredSchedules.map((schedule) => (
            <article
              key={schedule.id}
              className="overflow-hidden rounded-2xl border border-slate-800 bg-slate-900/60"
            >
              <div className="flex flex-col gap-3 border-b border-slate-800 bg-slate-950/50 p-5 sm:flex-row sm:items-start sm:justify-between">
                <div className="min-w-0 space-y-2">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="rounded-md border border-rose-500/40 bg-rose-500/15 px-2 py-0.5 font-mono text-xs font-semibold text-rose-200">
                      {schedule.targetDepartment}
                    </span>
                    <span className="rounded-md border border-sky-500/30 bg-sky-500/10 px-2 py-0.5 font-mono text-xs text-sky-200 tabular-nums">
                      Term {schedule.yearTerm}
                    </span>
                    <span className="rounded-md border border-slate-700 bg-slate-900 px-2 py-0.5 font-mono text-xs text-slate-300 tabular-nums">
                      Batch {schedule.batch}
                    </span>
                    <span className="rounded-md border border-slate-800 bg-slate-950 px-2 py-0.5 font-mono text-xs text-slate-400 tabular-nums">
                      Session {schedule.session}
                    </span>
                  </div>
                  <h3 className="text-lg font-bold tracking-tight text-white">
                    {schedule.title}
                  </h3>
                  <div className="flex flex-wrap items-center gap-4 text-xs text-slate-400">
                    <span className="inline-flex items-center gap-1.5 text-rose-200 tabular-nums">
                      <Clock className="h-3.5 w-3.5 text-rose-400 shrink-0" aria-hidden="true" />
                      <span>
                        Exam Time: {formatClock(schedule.examStartTime)} – {formatClock(schedule.examEndTime)}
                      </span>
                    </span>
                    <span>Published by {schedule.createdByName}</span>
                  </div>

                  {/* Central Room Arrangement (Roll Ranges) */}
                  {schedule.roomAllocations && schedule.roomAllocations.length > 0 ? (
                    <div
                      className="flex flex-wrap items-center gap-1.5 pt-1"
                      aria-label="Central exam room arrangement"
                    >
                      {schedule.roomAllocations.map((alloc, idx) => {
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
                            title={isMyRoom ? `Your assigned exam room: ${alloc.room}` : undefined}
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
                    schedule.examRoom && (
                      <div className="pt-1">
                        <span className="inline-flex items-center gap-1 rounded-md border border-amber-500/25 bg-amber-500/10 px-2 py-0.5 text-xs text-amber-200">
                          <MapPin className="h-3.5 w-3.5 shrink-0 text-amber-300" aria-hidden="true" />
                          <span>{schedule.examRoom}</span>
                        </span>
                      </div>
                    )
                  )}
                </div>

                {canMutateSchedule(schedule) && (
                  <div className="flex items-center gap-2 shrink-0">
                    <button
                      type="button"
                      onClick={() => openEditModal(schedule)}
                      className="inline-flex items-center gap-1.5 rounded-lg border border-slate-700 bg-slate-800/80 px-3 py-1.5 text-xs font-medium text-slate-200 transition-[background-color,transform] duration-100 ease-out hover:bg-slate-700 active:scale-[0.97]"
                    >
                      <Pencil className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                      <span>Edit</span>
                    </button>
                    <button
                      type="button"
                      onClick={() => handleDelete(schedule)}
                      className="inline-flex items-center gap-1.5 rounded-lg border border-rose-500/30 bg-rose-500/10 px-3 py-1.5 text-xs font-medium text-rose-200 transition-[background-color,transform] duration-100 ease-out hover:bg-rose-500/20 active:scale-[0.97]"
                    >
                      <Trash2 className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                      <span>Delete</span>
                    </button>
                  </div>
                )}
              </div>

              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs">
                  <thead className="border-b border-slate-800/80 bg-slate-950/30 text-[11px] uppercase tracking-wider text-slate-400">
                    <tr>
                      <th className="px-5 py-3 font-semibold">Date</th>
                      <th className="px-5 py-3 font-semibold">Day</th>
                      <th className="px-5 py-3 font-semibold">Course</th>
                      <th className="px-5 py-3 text-right font-semibold">Countdown</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-800/70">
                    {schedule.entries.map((entry) => {
                      const isCrossDept =
                        Boolean(entry.courseDepartment) &&
                        entry.courseDepartment?.toUpperCase() !==
                          schedule.targetDepartment.toUpperCase();
                      const countdown = formatExamCountdown(entry.examDate);
                      return (
                        <tr key={entry.id} className="hover:bg-slate-800/30">
                          <td className="whitespace-nowrap px-5 py-3 font-mono font-semibold text-slate-100 tabular-nums">
                            {entry.examDate}
                          </td>
                          <td className="whitespace-nowrap px-5 py-3 text-slate-300">
                            {entry.dayOfWeek}
                          </td>
                          <td className="px-5 py-3">
                            <div className="flex flex-wrap items-center gap-2">
                              <span className="font-mono font-semibold text-emerald-300">
                                {entry.courseCode}
                              </span>
                              {isCrossDept && (
                                <span
                                  className="rounded-md border border-amber-500/35 bg-amber-500/10 px-1.5 py-0.5 font-mono text-[10px] font-semibold text-amber-200"
                                  title={`Cross-department course offered by ${entry.courseDepartment}`}
                                >
                                  Cross-Dept ({entry.courseDepartment})
                                </span>
                              )}
                              <span className="text-slate-300">{entry.courseTitle}</span>
                            </div>
                          </td>
                          <td className="whitespace-nowrap px-5 py-3 text-right tabular-nums">
                            <span
                              className={cn(
                                'inline-flex rounded-md border px-2 py-0.5 font-mono text-[11px] font-medium',
                                countdown.tone === 'today'
                                  ? 'border-rose-500/40 bg-rose-500/15 text-rose-200'
                                  : countdown.tone === 'upcoming'
                                    ? 'border-sky-500/30 bg-sky-500/10 text-sky-200'
                                    : 'border-slate-800 bg-slate-900 text-slate-500',
                              )}
                            >
                              {countdown.label}
                            </span>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            </article>
          ))}
        </div>
      )}

      {/* Upload / Edit Schedule Dialog */}
      <Dialog.Root open={modalOpen} onOpenChange={setModalOpen}>
        <Dialog.Portal>
          <Dialog.Overlay className="kuet-dialog-overlay fixed inset-0 z-50 bg-slate-950/75 backdrop-blur-sm" />
          <Dialog.Content className="kuet-dialog-content fixed left-1/2 top-1/2 z-50 max-h-[90vh] w-[calc(100%-2rem)] max-w-3xl -translate-x-1/2 -translate-y-1/2 overflow-y-auto rounded-2xl border border-slate-800 bg-slate-900 p-6 shadow-2xl focus:outline-none">
            <div className="flex items-start justify-between gap-4">
              <div>
                <Dialog.Title className="text-lg font-bold tracking-tight text-white">
                  {editingSchedule ? 'Edit Term Final Schedule' : 'Upload Term Final Schedule'}
                </Dialog.Title>
                <Dialog.Description className="mt-1 text-xs text-slate-400">
                  Set shared exam hours once and list each exam date. Courses from any KUET department (e.g., EEE 1107 for CSE 1-1) can be picked.
                </Dialog.Description>
              </div>
              <Dialog.Close
                aria-label="Close Term Final modal"
                className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-800 hover:text-white"
              >
                <X className="h-4 w-4" />
              </Dialog.Close>
            </div>

            <form onSubmit={handleSave} className="mt-5 space-y-4">
              {formError && (
                <div className="rounded-lg border border-rose-500/30 bg-rose-500/10 px-3 py-2 text-xs text-rose-200">
                  {formError}
                </div>
              )}

              <div>
                <label htmlFor="tf-title" className="block text-xs font-medium text-slate-300">
                  Schedule Title
                </label>
                <input
                  id="tf-title"
                  type="text"
                  required
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                  placeholder="e.g., B.Sc. Engineering 3rd Year 2nd Term Final 2026"
                  className="mt-1.5 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-slate-100 placeholder:text-slate-500 focus:border-rose-500 focus:outline-none"
                />
              </div>

              <div className="grid grid-cols-1 gap-3 sm:grid-cols-4">
                <div>
                  <label htmlFor="tf-dept" className="block text-xs font-medium text-slate-300">
                    Target Department
                  </label>
                  <select
                    id="tf-dept"
                    value={targetDepartment}
                    disabled={!isStaff && isCr}
                    onChange={(e) => setTargetDepartment(e.target.value)}
                    className="mt-1.5 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-slate-100 disabled:opacity-60 focus:border-rose-500 focus:outline-none"
                  >
                    {KUET_DEPARTMENTS.map((dept) => (
                      <option key={dept} value={dept}>
                        {dept}
                      </option>
                    ))}
                  </select>
                </div>

                <div>
                  <label htmlFor="tf-yt" className="block text-xs font-medium text-slate-300">
                    Year–Term
                  </label>
                  <select
                    id="tf-yt"
                    value={yearTerm}
                    onChange={(e) => setYearTerm(e.target.value)}
                    className="mt-1.5 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-slate-100 focus:border-rose-500 focus:outline-none"
                  >
                    {YEAR_TERM_OPTIONS.map((yt) => (
                      <option key={yt.code} value={yt.code}>
                        {yt.code}
                      </option>
                    ))}
                  </select>
                </div>

                <div>
                  <label htmlFor="tf-batch" className="block text-xs font-medium text-slate-300">
                    Batch
                  </label>
                  <input
                    id="tf-batch"
                    type="text"
                    required
                    value={batch}
                    onChange={(e) => setBatch(e.target.value)}
                    placeholder="2k22"
                    className="mt-1.5 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-slate-100 focus:border-rose-500 focus:outline-none"
                  />
                </div>

                <div>
                  <label htmlFor="tf-session" className="block text-xs font-medium text-slate-300">
                    Session
                  </label>
                  <input
                    id="tf-session"
                    type="text"
                    required
                    value={session}
                    onChange={(e) => setSession(e.target.value)}
                    placeholder="2025-2026"
                    className="mt-1.5 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-slate-100 focus:border-rose-500 focus:outline-none"
                  />
                </div>
              </div>

              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                <div>
                  <label htmlFor="tf-start" className="block text-xs font-medium text-slate-300">
                    Shared Exam Start Time
                  </label>
                  <input
                    id="tf-start"
                    type="time"
                    required
                    value={examStartTime}
                    onChange={(e) => setExamStartTime(e.target.value)}
                    className="mt-1.5 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-slate-100 tabular-nums focus:border-rose-500 focus:outline-none"
                  />
                </div>
                <div>
                  <label htmlFor="tf-end" className="block text-xs font-medium text-slate-300">
                    Shared Exam End Time
                  </label>
                  <input
                    id="tf-end"
                    type="time"
                    required
                    value={examEndTime}
                    onChange={(e) => setExamEndTime(e.target.value)}
                    className="mt-1.5 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-slate-100 tabular-nums focus:border-rose-500 focus:outline-none"
                  />
                </div>
              </div>

              {/* Central Room Arrangement (Roll Ranges) */}
              <div className="space-y-2.5 pt-2">
                <div className="flex items-center justify-between gap-2">
                  <span className="flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wider text-slate-400">
                    <MapPin className="h-3.5 w-3.5 text-amber-300 shrink-0" aria-hidden="true" />
                    <span>Central Room Arrangement (Roll Ranges)</span>
                  </span>
                  <button
                    type="button"
                    onClick={addRoomRow}
                    className="inline-flex items-center gap-1 rounded-md border border-slate-700 bg-slate-950 px-2.5 py-1 text-xs font-medium text-emerald-300 hover:bg-slate-800 active:scale-[0.97]"
                  >
                    <Plus className="h-3.5 w-3.5 shrink-0" />
                    <span>Add Room</span>
                  </button>
                </div>

                {roomRows.length === 0 ? (
                  <p className="rounded-lg border border-dashed border-slate-800 px-3 py-2 text-[11px] text-slate-500">
                    No central rooms configured yet. Click &ldquo;Add Room&rdquo; for roll ranges (e.g., 1–40: CSE-101, 41–80: CSE-105, 81–121: CSE-102).
                  </p>
                ) : (
                  <div className="space-y-2">
                    {roomRows.map((roomRow, idx) => (
                      <div key={idx} className="grid grid-cols-12 items-center gap-2">
                        <input
                          type="number"
                          min={1}
                          aria-label={`Central room ${idx + 1} from roll`}
                          value={roomRow.rollStart}
                          onChange={(e) => updateRoomRow(idx, { rollStart: e.target.value })}
                          placeholder="From Roll (1)"
                          className="col-span-3 rounded-lg border border-slate-700 bg-slate-950 px-2.5 py-1.5 text-xs text-slate-100 tabular-nums placeholder:text-slate-500 focus:border-emerald-500 focus:outline-none"
                        />
                        <input
                          type="number"
                          min={1}
                          aria-label={`Central room ${idx + 1} to roll`}
                          value={roomRow.rollEnd}
                          onChange={(e) => updateRoomRow(idx, { rollEnd: e.target.value })}
                          placeholder="To Roll (40)"
                          className="col-span-3 rounded-lg border border-slate-700 bg-slate-950 px-2.5 py-1.5 text-xs text-slate-100 tabular-nums placeholder:text-slate-500 focus:border-emerald-500 focus:outline-none"
                        />
                        <input
                          type="text"
                          maxLength={100}
                          aria-label={`Central room ${idx + 1} name`}
                          value={roomRow.room}
                          onChange={(e) => updateRoomRow(idx, { room: e.target.value })}
                          placeholder="Room (e.g., CSE-101)"
                          className="col-span-5 rounded-lg border border-slate-700 bg-slate-950 px-2.5 py-1.5 text-xs text-slate-100 placeholder:text-slate-500 focus:border-emerald-500 focus:outline-none"
                        />
                        <button
                          type="button"
                          aria-label={`Remove central room ${idx + 1}`}
                          onClick={() => removeRoomRow(idx)}
                          className="col-span-1 flex justify-center rounded-md p-1.5 text-slate-400 hover:bg-rose-500/15 hover:text-rose-300"
                        >
                          <Trash2 className="h-3.5 w-3.5" />
                        </button>
                      </div>
                    ))}
                  </div>
                )}
              </div>

              {/* Exam Rows */}
              <div className="space-y-2.5 pt-2">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-semibold uppercase tracking-wider text-slate-400">
                    Exam Rows ({rows.length})
                  </span>
                  <button
                    type="button"
                    onClick={() => setRows((prev) => [...prev, EMPTY_ROW()])}
                    className="inline-flex items-center gap-1 rounded-md border border-slate-700 bg-slate-950 px-2.5 py-1 text-xs font-medium text-rose-300 hover:bg-slate-800 active:scale-[0.97]"
                  >
                    <Plus className="h-3.5 w-3.5" />
                    <span>Add Exam Row</span>
                  </button>
                </div>

                <div className="space-y-3">
                  {rows.map((row, idx) => (
                    <div
                      key={idx}
                      className="rounded-xl border border-slate-800 bg-slate-950/60 p-3 space-y-2.5"
                    >
                      <div className="grid grid-cols-1 gap-2.5 sm:grid-cols-12 sm:items-center">
                        <div className="sm:col-span-4">
                          <label className="block text-[11px] text-slate-400">
                            Exam Date
                          </label>
                          <input
                            type="date"
                            aria-label={`Exam row ${idx + 1} date`}
                            value={row.examDate}
                            onChange={(e) => updateRow(idx, { examDate: e.target.value })}
                            required
                            className="mt-1 w-full rounded-lg border border-slate-700 bg-slate-900 px-2.5 py-1.5 text-xs text-slate-100 tabular-nums focus:border-rose-500 focus:outline-none"
                          />
                        </div>

                        <div className="sm:col-span-3">
                          <span className="block text-[11px] text-slate-400">Auto Day</span>
                          <div
                            data-testid={`exam-row-day-${idx}`}
                            className="mt-1 rounded-lg border border-slate-800 bg-slate-900/70 px-2.5 py-1.5 text-xs font-medium text-emerald-300"
                          >
                            {computeDayOfWeek(row.examDate)}
                          </div>
                        </div>

                        <div className="sm:col-span-4">
                          <label className="block text-[11px] text-slate-400">
                            Pick Course (All Depts)
                          </label>
                          <select
                            aria-label={`Exam row ${idx + 1} course picker`}
                            value={row.courseOfferingId}
                            onChange={(e) => handleCourseSelect(idx, e.target.value)}
                            className="mt-1 w-full rounded-lg border border-slate-700 bg-slate-900 px-2.5 py-1.5 text-xs text-slate-100 focus:border-rose-500 focus:outline-none"
                          >
                            <option value="">Manual code / title</option>
                            {courses.map((c) => (
                              <option key={c.id} value={c.id}>
                                [{c.department}] {c.courseCode} — {c.courseTitle}
                              </option>
                            ))}
                          </select>
                        </div>

                        <div className="sm:col-span-1 flex justify-end pt-4">
                          <button
                            type="button"
                            disabled={rows.length === 1}
                            aria-label={`Remove exam row ${idx + 1}`}
                            onClick={() => setRows((prev) => prev.filter((_, i) => i !== idx))}
                            className="rounded-md p-1.5 text-slate-400 hover:bg-rose-500/15 hover:text-rose-300 disabled:opacity-40"
                          >
                            <Trash2 className="h-3.5 w-3.5" />
                          </button>
                        </div>
                      </div>

                      <div className="grid grid-cols-1 gap-2.5 sm:grid-cols-12">
                        <input
                          type="text"
                          aria-label={`Exam row ${idx + 1} course code`}
                          value={row.courseCode}
                          onChange={(e) => updateRow(idx, { courseCode: e.target.value })}
                          placeholder="Course Code (e.g., EEE 1107)"
                          className="sm:col-span-4 rounded-lg border border-slate-700 bg-slate-900 px-2.5 py-1.5 text-xs text-slate-100 placeholder:text-slate-500 focus:border-rose-500 focus:outline-none"
                        />
                        <input
                          type="text"
                          aria-label={`Exam row ${idx + 1} course title`}
                          value={row.courseTitle}
                          onChange={(e) => updateRow(idx, { courseTitle: e.target.value })}
                          placeholder="Course Title"
                          className="sm:col-span-8 rounded-lg border border-slate-700 bg-slate-900 px-2.5 py-1.5 text-xs text-slate-100 placeholder:text-slate-500 focus:border-rose-500 focus:outline-none"
                        />
                      </div>
                    </div>
                  ))}
                </div>
              </div>

              <div className="flex items-center justify-end gap-2 border-t border-slate-800 pt-4">
                <Dialog.Close asChild>
                  <button
                    type="button"
                    className="rounded-lg border border-slate-700 px-4 py-2 text-xs font-medium text-slate-300 hover:bg-slate-800"
                  >
                    Cancel
                  </button>
                </Dialog.Close>
                <button
                  type="submit"
                  disabled={submitting}
                  className="rounded-lg bg-rose-600 px-4 py-2 text-xs font-semibold text-white transition-[background-color,transform] duration-100 ease-out hover:bg-rose-500 active:scale-[0.97] disabled:opacity-50"
                >
                  {submitting
                    ? 'Saving…'
                    : editingSchedule
                      ? 'Save Changes'
                      : 'Publish Term Schedule'}
                </button>
              </div>
            </form>
          </Dialog.Content>
        </Dialog.Portal>
      </Dialog.Root>
    </div>
  );
}
