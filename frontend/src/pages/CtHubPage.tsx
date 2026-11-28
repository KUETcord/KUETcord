import { useCallback, useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
  Bell,
  BellOff,
  CalendarPlus,
  Filter,
  GraduationCap,
  Search,
  Sparkles,
} from 'lucide-react';
import { toast } from 'sonner';
import { CtEventCard } from '../components/cthub/CtEventCard';
import { CancelCtModal, ProposeCtModal, RescheduleCtModal } from '../components/cthub/CtModals';
import { extractErrorMessage, useAuth } from '../context/AuthContext';
import {
  DEMO_COURSES,
  DEMO_CT_EVENTS,
  DEMO_TEACHERS,
  WORST_CASE_COURSES,
  WORST_CASE_CT_EVENTS,
  WORST_CASE_TEACHERS,
  type DataFixtureMode,
} from '../fixtures/ctHubFixtures';
import { ApiError, ctHubApi } from '../lib/api';
import { cn, pluralize } from '../lib/format';
import { KUET_DEPARTMENTS, resolveYearTerm, YEAR_TERM_OPTIONS } from '../lib/yearTerm';
import type { CourseOffering, CtEventSummary, CtStatus, RoomAllocation } from '../types/domain';

const STATUS_OPTIONS: Array<{ value: '' | CtStatus; label: string }> = [
  { value: '', label: 'All statuses' },
  { value: 'VERIFIED', label: 'Verified' },
  { value: 'UNVERIFIED', label: 'Unverified' },
  { value: 'RESCHEDULED', label: 'Rescheduled' },
  { value: 'CANCELLED', label: 'Cancelled' },
];

export function CtHubPage() {
  const { user, isDemoMode, canProposeCt, canApproveCt, canRescheduleOrCancelCt } = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();

  const urlFixture = (searchParams.get('data') as DataFixtureMode | null) ?? 'demo';
  const [fixtureMode, setFixtureMode] = useState<DataFixtureMode>(
    ['demo', 'worst', 'empty', 'one'].includes(urlFixture) ? urlFixture : 'demo',
  );

  const [courses, setCourses] = useState<CourseOffering[]>(DEMO_COURSES);
  const [events, setEvents] = useState<CtEventSummary[]>(DEMO_CT_EVENTS);

  // Filters — "My Batch (e.g. 3-2 · CSE)" is active by default when student/CR has yearTerm set
  const hasStudentBatchScope = Boolean(user?.yearTerm);
  const [myBatchOnly, setMyBatchOnly] = useState<boolean>(hasStudentBatchScope);
  const [departmentFilter, setDepartmentFilter] = useState<string>(user?.department ?? '');
  const [yearTermFilter, setYearTermFilter] = useState<string>('');
  const [statusFilter, setStatusFilter] = useState<'' | CtStatus>('');
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [selectedCourseId, setSelectedCourseId] = useState<number | null>(null);

  useEffect(() => {
    setMyBatchOnly(Boolean(user?.yearTerm));
    if (user?.department) {
      setDepartmentFilter(user.department);
    }
  }, [user?.id, user?.yearTerm, user?.department]);

  // Modals & action state
  const [proposeOpen, setProposeOpen] = useState(false);
  const [rescheduleTarget, setRescheduleTarget] = useState<CtEventSummary | null>(null);
  const [cancelTarget, setCancelTarget] = useState<CtEventSummary | null>(null);
  const [verifyingId, setVerifyingId] = useState<number | null>(null);
  const [conflictBanner, setConflictBanner] = useState<string | null>(null);

  const demoTeachers = fixtureMode === 'worst' ? WORST_CASE_TEACHERS : DEMO_TEACHERS;

  const loadData = useCallback(async () => {
    if (isDemoMode) {
      if (fixtureMode === 'worst') {
        setCourses(WORST_CASE_COURSES);
        setEvents(WORST_CASE_CT_EVENTS);
      } else if (fixtureMode === 'empty') {
        setCourses(DEMO_COURSES);
        setEvents([]);
      } else if (fixtureMode === 'one') {
        setCourses([DEMO_COURSES[0]]);
        setEvents([DEMO_CT_EVENTS[0]]);
      } else {
        setCourses(DEMO_COURSES);
        setEvents(DEMO_CT_EVENTS);
      }
      return;
    }

    try {
      const [fetchedCourses, fetchedEvents] = await Promise.all([
        ctHubApi.listCourses(),
        ctHubApi.listEvents(),
      ]);
      setCourses(fetchedCourses);
      setEvents(fetchedEvents);
    } catch (err) {
      toast.error(extractErrorMessage(err, 'Could not load CT Hub data from server'));
    }
  }, [isDemoMode, fixtureMode]);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  const departments = useMemo(() => {
    return Array.from(new Set([...KUET_DEPARTMENTS, ...courses.map((c) => c.department)])).sort();
  }, [courses]);

  const effectiveDepartment = myBatchOnly && user?.department ? user.department : departmentFilter;
  const effectiveYearTerm = myBatchOnly && user?.yearTerm ? user.yearTerm : yearTermFilter;

  const filteredCourses = useMemo(() => {
    return courses.filter((course) => {
      const cYt = resolveYearTerm(course.yearTerm, course.semester);
      if (effectiveDepartment && course.department !== effectiveDepartment) return false;
      if (effectiveYearTerm && cYt !== effectiveYearTerm) return false;
      if (searchQuery.trim()) {
        const q = searchQuery.trim().toLowerCase();
        return (
          course.courseCode.toLowerCase().includes(q) ||
          course.courseTitle.toLowerCase().includes(q)
        );
      }
      return true;
    });
  }, [courses, effectiveDepartment, effectiveYearTerm, searchQuery]);

  const filteredEvents = useMemo(() => {
    return events.filter((ev) => {
      if (selectedCourseId && ev.courseOfferingId !== selectedCourseId) return false;
      if (statusFilter && ev.status !== statusFilter) return false;

      const matchCourse = courses.find((c) => c.id === ev.courseOfferingId);
      const evDept = ev.targetDepartment ?? ev.courseDepartment ?? matchCourse?.department ?? null;
      const evYt = resolveYearTerm(
        ev.targetYearTerm ?? ev.courseYearTerm ?? matchCourse?.yearTerm,
        matchCourse?.semester,
      );

      if (effectiveDepartment && evDept && evDept !== effectiveDepartment) return false;
      if (effectiveYearTerm && evYt && evYt !== effectiveYearTerm) return false;

      if (searchQuery.trim()) {
        const q = searchQuery.trim().toLowerCase();
        return (
          ev.title.toLowerCase().includes(q) ||
          ev.courseCode.toLowerCase().includes(q) ||
          ev.courseTitle.toLowerCase().includes(q) ||
          (ev.examRoom?.toLowerCase().includes(q) ?? false) ||
          (ev.assignedTeacherName?.toLowerCase().includes(q) ?? false) ||
          (ev.coTeacherName?.toLowerCase().includes(q) ?? false)
        );
      }
      return true;
    });
  }, [
    events,
    selectedCourseId,
    statusFilter,
    effectiveDepartment,
    effectiveYearTerm,
    searchQuery,
    courses,
  ]);

  const handleToggleFollow = async (course: CourseOffering) => {
    const nextFollowed = !course.followed;
    setCourses((prev) =>
      prev.map((c) => (c.id === course.id ? { ...c, followed: nextFollowed } : c)),
    );

    if (isDemoMode) {
      toast.success(
        nextFollowed
          ? `Following ${course.courseCode} for CT alerts`
          : `Unfollowed ${course.courseCode}`,
      );
      return;
    }

    try {
      if (nextFollowed) {
        await ctHubApi.followCourse(course.id);
        toast.success(`Following ${course.courseCode} for CT alerts`);
      } else {
        await ctHubApi.unfollowCourse(course.id);
        toast(`Unfollowed ${course.courseCode}`);
      }
    } catch (err) {
      setCourses((prev) =>
        prev.map((c) => (c.id === course.id ? { ...c, followed: !nextFollowed } : c)),
      );
      toast.error(extractErrorMessage(err, 'Failed to update course follow status'));
    }
  };

  const handleCreateEvent = async (payload: {
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
  }) => {
    const teacherDisplay = payload.coTeacherName
      ? `${payload.teacherName} & ${payload.coTeacherName}`
      : payload.teacherName;
    const summaryRoom = payload.roomAllocations?.length
      ? payload.roomAllocations
          .map((a) =>
            a.rollStart !== null && a.rollEnd !== null
              ? `${a.room} (${a.rollStart}-${a.rollEnd})`
              : a.room,
          )
          .join(', ')
      : null;

    if (isDemoMode) {
      const course = courses.find((c) => c.id === payload.courseOfferingId) ?? courses[0];
      const newEvent: CtEventSummary = {
        id: Date.now(),
        courseOfferingId: course.id,
        courseCode: course.courseCode,
        courseTitle: course.courseTitle,
        courseDepartment: course.department,
        courseYearTerm: resolveYearTerm(course.yearTerm, course.semester),
        targetDepartment: payload.targetDepartment ?? user?.department ?? course.department,
        targetYearTerm: payload.targetYearTerm ?? resolveYearTerm(course.yearTerm, course.semester),
        title: payload.title,
        scheduledAt: payload.scheduledAt,
        durationMinutes: payload.durationMinutes,
        examRoom: summaryRoom,
        roomAllocations: payload.roomAllocations ?? [],
        coveredTopics: payload.coveredTopics ?? null,
        status: 'UNVERIFIED',
        submittedById: user?.id ?? 2,
        submittedByName: user?.displayName ?? 'Karim Rahman',
        assignedTeacherId: payload.teacherId,
        assignedTeacherName: payload.teacherName,
        coTeacherId: payload.coTeacherId ?? null,
        coTeacherName: payload.coTeacherName ?? null,
        verifiedById: null,
        verifiedByName: null,
        version: 0,
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      };
      setEvents((prev) => [newEvent, ...prev]);
      toast.success(`CT proposed and ${teacherDisplay} notified for approval`);
      return;
    }

    const created = await ctHubApi.createEvent({
      courseOfferingId: payload.courseOfferingId,
      teacherId: payload.teacherId,
      coTeacherId: payload.coTeacherId,
      title: payload.title,
      scheduledAt: payload.scheduledAt,
      durationMinutes: payload.durationMinutes,
      roomAllocations: payload.roomAllocations,
      coveredTopics: payload.coveredTopics,
      targetDepartment: payload.targetDepartment,
      targetYearTerm: payload.targetYearTerm,
    });
    setEvents((prev) => [created, ...prev]);
    toast.success(`CT proposed and ${teacherDisplay} notified for approval`);
  };

  const handleVerify = async (event: CtEventSummary) => {
    setVerifyingId(event.id);
    try {
      if (isDemoMode) {
        setEvents((prev) =>
          prev.map((e) =>
            e.id === event.id
              ? {
                  ...e,
                  status: 'VERIFIED',
                  verifiedById: user?.id ?? 10,
                  verifiedByName: user?.displayName ?? 'Prof. Dr. Hasan Mahmud',
                  version: e.version + 1,
                }
              : e,
          ),
        );
        toast.success(`Approved ${event.courseCode}: ${event.title}`);
        return;
      }

      const updated = await ctHubApi.verifyEvent(event.id, event.version);
      setEvents((prev) => prev.map((e) => (e.id === updated.id ? updated : e)));
      toast.success('CT schedule approved by course teacher');
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        toast.error('Schedule was updated concurrently. Reloading latest data.');
        await loadData();
      } else {
        toast.error(extractErrorMessage(err, 'Failed to approve CT schedule'));
      }
    } finally {
      setVerifyingId(null);
    }
  };

  const handleReschedule = async (
    event: CtEventSummary,
    payload: {
      newScheduledAt: string;
      reason: string;
      expectedVersion: number;
      roomAllocations?: RoomAllocation[];
    },
  ) => {
    setConflictBanner(null);
    const notifyNote =
      user?.globalRole === 'CR' && event.assignedTeacherName
        ? ` — ${event.coTeacherName ? `${event.assignedTeacherName} & ${event.coTeacherName}` : event.assignedTeacherName} notified`
        : '';

    try {
      if (isDemoMode) {
        const nextRoomSummary = payload.roomAllocations?.length
          ? payload.roomAllocations
              .map((a) =>
                a.rollStart !== null && a.rollEnd !== null
                  ? `${a.room} (${a.rollStart}-${a.rollEnd})`
                  : a.room,
              )
              .join(', ')
          : null;
        setEvents((prev) =>
          prev.map((e) =>
            e.id === event.id
              ? {
                  ...e,
                  scheduledAt: payload.newScheduledAt,
                  roomAllocations: payload.roomAllocations ?? e.roomAllocations,
                  examRoom:
                    payload.roomAllocations !== undefined ? nextRoomSummary : e.examRoom,
                  status: 'RESCHEDULED',
                  version: e.version + 1,
                }
              : e,
          ),
        );
        toast.success(`Rescheduled ${event.courseCode}${notifyNote}`);
        setRescheduleTarget(null);
        return;
      }

      const updated = await ctHubApi.rescheduleEvent(event.id, payload);
      setEvents((prev) => prev.map((e) => (e.id === updated.id ? updated : e)));
      toast.success(`CT schedule rescheduled${notifyNote}`);
      setRescheduleTarget(null);
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        setConflictBanner(
          'Schedule was modified concurrently (HTTP 409 Conflict). Reload the latest version before applying changes.',
        );
        toast.error('Version conflict (409): reload required');
      } else {
        toast.error(extractErrorMessage(err, 'Failed to reschedule CT'));
      }
    }
  };

  const handleCancel = async (
    event: CtEventSummary,
    payload: { reason: string; expectedVersion: number },
  ) => {
    const notifyNote =
      user?.globalRole === 'CR' && event.assignedTeacherName
        ? ` — ${event.assignedTeacherName} notified`
        : '';

    try {
      if (isDemoMode) {
        setEvents((prev) =>
          prev.map((e) =>
            e.id === event.id ? { ...e, status: 'CANCELLED', version: e.version + 1 } : e,
          ),
        );
        toast(`CT event cancelled${notifyNote}`);
        setCancelTarget(null);
        return;
      }

      const updated = await ctHubApi.cancelEvent(event.id, payload);
      setEvents((prev) => prev.map((e) => (e.id === updated.id ? updated : e)));
      toast(`CT event cancelled${notifyNote}`);
      setCancelTarget(null);
    } catch (err) {
      toast.error(extractErrorMessage(err, 'Failed to cancel CT'));
    }
  };

  const switchFixture = (mode: DataFixtureMode) => {
    setFixtureMode(mode);
    const next = new URLSearchParams(searchParams);
    next.set('data', mode);
    setSearchParams(next, { replace: true });
  };

  return (
    <div className="space-y-8 pb-16">
      {/* Page Heading */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div className="min-w-0">
          <h2 className="text-2xl font-bold tracking-tight text-white sm:text-3xl">
            Class Test Hub
          </h2>
          <p className="mt-1 max-w-[65ch] text-sm leading-relaxed text-slate-400">
            CRs propose Class Tests with exam rooms and assign course teachers; teachers approve schedules and students filter directly by their Year–Term batch.
          </p>
        </div>

        {canProposeCt && (
          <button
            type="button"
            onClick={() => setProposeOpen(true)}
            disabled={courses.length === 0}
            className="inline-flex items-center justify-center gap-2 rounded-xl bg-emerald-600 px-4 py-2.5 text-sm font-medium text-white shadow-lg shadow-emerald-950/50 transition-[background-color,transform] duration-100 ease-out hover:bg-emerald-500 active:scale-[0.97] disabled:cursor-not-allowed disabled:opacity-50 shrink-0"
          >
            <CalendarPlus className="h-4 w-4 shrink-0" aria-hidden="true" />
            <span>Propose CT</span>
          </button>
        )}
      </div>

      {/* Smart "My Batch (Year-Term · Dept)" Default Toggle */}
      {user?.yearTerm && (
        <div className="flex flex-wrap items-center gap-2">
          <button
            type="button"
            aria-pressed={myBatchOnly}
            onClick={() => setMyBatchOnly(true)}
            className={cn(
              'inline-flex items-center gap-1.5 rounded-full border px-3 py-1 text-xs font-medium transition-[background-color,border-color,transform] duration-100 ease-out active:scale-[0.97]',
              myBatchOnly
                ? 'border-emerald-500/50 bg-emerald-500/15 text-emerald-200'
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
        aria-label="Filter CT Hub"
        className="grid grid-cols-1 gap-3 rounded-xl border border-slate-800 bg-slate-900/50 p-4 md:grid-cols-12"
      >
        <div className="relative md:col-span-4">
          <Search
            className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-500"
            aria-hidden="true"
          />
          <input
            type="search"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search course, room, or teacher..."
            className="w-full rounded-lg border border-slate-700 bg-slate-950 py-2 pl-9 pr-3 text-sm text-slate-100 placeholder:text-slate-500 focus:border-emerald-500 focus:outline-none"
          />
        </div>

        <div className="md:col-span-2">
          <select
            aria-label="Filter by department"
            value={effectiveDepartment}
            onChange={(e) => {
              setMyBatchOnly(false);
              setDepartmentFilter(e.target.value);
            }}
            className="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-slate-100 focus:border-emerald-500 focus:outline-none"
          >
            <option value="">All departments</option>
            {departments.map((dept) => (
              <option key={dept} value={dept}>
                {dept}
              </option>
            ))}
          </select>
        </div>

        <div className="md:col-span-3">
          <select
            aria-label="Filter by Year-Term"
            value={effectiveYearTerm}
            onChange={(e) => {
              setMyBatchOnly(false);
              setYearTermFilter(e.target.value);
            }}
            className="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-slate-100 focus:border-emerald-500 focus:outline-none"
          >
            <option value="">All Year–Terms</option>
            {YEAR_TERM_OPTIONS.map((yt) => (
              <option key={yt.code} value={yt.code}>
                {yt.label}
              </option>
            ))}
          </select>
        </div>

        <div className="md:col-span-3">
          <div className="flex items-center gap-2">
            <Filter className="h-4 w-4 text-slate-500 shrink-0" aria-hidden="true" />
            <select
              aria-label="Filter by status"
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value as '' | CtStatus)}
              className="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-slate-100 focus:border-emerald-500 focus:outline-none"
            >
              {STATUS_OPTIONS.map((opt) => (
                <option key={opt.value || 'all'} value={opt.value}>
                  {opt.label}
                </option>
              ))}
            </select>
          </div>
        </div>
      </section>

      {/* Course Offerings Shelf */}
      <section aria-label="Course offerings" className="space-y-3">
        <div className="flex items-center justify-between gap-2">
          <h3 className="text-sm font-semibold uppercase tracking-wider text-slate-400">
            Course Offerings ({pluralize(filteredCourses.length, 'course', 'courses')})
          </h3>
          {selectedCourseId && (
            <button
              type="button"
              onClick={() => setSelectedCourseId(null)}
              className="text-xs font-medium text-emerald-400 hover:text-emerald-300"
            >
              Clear course filter
            </button>
          )}
        </div>

        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {filteredCourses.map((course) => {
            const isSelected = selectedCourseId === course.id;
            const cYt = resolveYearTerm(course.yearTerm, course.semester) ?? course.semester;
            return (
              <div
                key={course.id}
                className={cn(
                  'flex items-start justify-between gap-3 rounded-xl border p-4 transition-colors duration-150 ease-out',
                  isSelected
                    ? 'border-emerald-500/50 bg-emerald-950/20'
                    : 'border-slate-800 bg-slate-900/40 hover:border-slate-700',
                )}
              >
                <button
                  type="button"
                  onClick={() => setSelectedCourseId(isSelected ? null : course.id)}
                  className="min-w-0 flex-1 text-left"
                >
                  <div className="flex items-center gap-2">
                    <span className="font-mono text-xs font-bold tracking-tight text-emerald-300">
                      {course.courseCode}
                    </span>
                    <span className="text-[11px] text-slate-500 tabular-nums">
                      {course.department} • {cYt}
                    </span>
                  </div>
                  <p
                    className="mt-1 truncate text-sm font-medium text-slate-100"
                    title={course.courseTitle}
                  >
                    {course.courseTitle}
                  </p>
                </button>

                <button
                  type="button"
                  aria-label={course.followed ? `Unfollow ${course.courseCode}` : `Follow ${course.courseCode}`}
                  onClick={() => handleToggleFollow(course)}
                  className={cn(
                    'inline-flex items-center gap-1 rounded-lg border px-2.5 py-1.5 text-xs font-medium transition-[background-color,transform] duration-100 ease-out active:scale-[0.97] shrink-0',
                    course.followed
                      ? 'border-emerald-500/30 bg-emerald-500/15 text-emerald-300 hover:bg-emerald-500/25'
                      : 'border-slate-700 bg-slate-800 text-slate-300 hover:bg-slate-700',
                  )}
                >
                  {course.followed ? (
                    <>
                      <Bell className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                      <span>Following</span>
                    </>
                  ) : (
                    <>
                      <BellOff className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                      <span>Follow</span>
                    </>
                  )}
                </button>
              </div>
            );
          })}
        </div>
      </section>

      {/* CT Timeline */}
      <section aria-label="Upcoming Class Tests" className="space-y-4">
        <div className="flex items-center justify-between gap-2">
          <h3 className="text-sm font-semibold uppercase tracking-wider text-slate-400 tabular-nums">
            Class Test Schedule ({pluralize(filteredEvents.length, 'CT', 'CTs')})
          </h3>
        </div>

        {filteredEvents.length === 0 ? (
          <div className="rounded-2xl border border-dashed border-slate-800 bg-slate-900/30 px-6 py-12 text-center">
            <Sparkles className="mx-auto mb-3 h-8 w-8 text-slate-500" aria-hidden="true" />
            <h4 className="text-base font-semibold text-slate-200">
              No upcoming Class Tests scheduled
            </h4>
            <p className="mx-auto mt-1.5 max-w-[48ch] text-xs leading-relaxed text-slate-400">
              Follow a course above to receive instant alerts when course teachers approve new schedules.
            </p>
            {canProposeCt && (
              <button
                type="button"
                onClick={() => setProposeOpen(true)}
                className="mt-4 inline-flex items-center gap-1.5 rounded-lg bg-emerald-600 px-4 py-2 text-xs font-medium text-white hover:bg-emerald-500"
              >
                <CalendarPlus className="h-3.5 w-3.5 shrink-0" />
                <span>Propose First CT</span>
              </button>
            )}
          </div>
        ) : (
          <div className="space-y-3">
            {filteredEvents.map((ev) => (
              <CtEventCard
                key={ev.id}
                event={ev}
                currentUserId={user?.id ?? null}
                currentUserRole={user?.globalRole ?? null}
                currentUserRollNumber={user?.rollNumber ?? null}
                canApprove={canApproveCt}
                canRescheduleOrCancel={canRescheduleOrCancelCt}
                verifyingId={verifyingId}
                onVerify={handleVerify}
                onOpenReschedule={(target) => {
                  setConflictBanner(null);
                  setRescheduleTarget(target);
                }}
                onOpenCancel={(target) => setCancelTarget(target)}
              />
            ))}
          </div>
        )}
      </section>

      {/* Modals */}
      <ProposeCtModal
        open={proposeOpen}
        onOpenChange={setProposeOpen}
        courses={courses}
        defaultYearTerm={user?.yearTerm ?? null}
        defaultDepartment={user?.department ?? null}
        isCrOnly={
          user?.globalRole === 'CR' &&
          !user.roles.includes('ROLE_ADMIN') &&
          !user.roles.includes('ROLE_MODERATOR')
        }
        demoTeachers={demoTeachers}
        isDemoMode={isDemoMode}
        onSubmit={handleCreateEvent}
      />

      <RescheduleCtModal
        event={rescheduleTarget}
        onOpenChange={(open) => {
          if (!open) {
            setRescheduleTarget(null);
            setConflictBanner(null);
          }
        }}
        conflictMessage={conflictBanner}
        onReloadLatest={async () => {
          await loadData();
          setConflictBanner(null);
          setRescheduleTarget(null);
          toast.info('Reloaded latest CT schedule');
        }}
        onSubmit={handleReschedule}
      />

      <CancelCtModal
        event={cancelTarget}
        onOpenChange={(open) => {
          if (!open) setCancelTarget(null);
        }}
        onSubmit={handleCancel}
      />

      {/* Dev-Only break-ui Fixture Switcher */}
      {import.meta.env.DEV && isDemoMode && (
        <div
          role="group"
          aria-label="Break UI data fixture switcher"
          className="fixed bottom-4 left-1/2 z-40 flex -translate-x-1/2 items-center gap-1 rounded-full border border-slate-700 bg-slate-900/95 p-1 text-xs shadow-lg"
        >
          {(['demo', 'worst', 'empty', 'one'] as const).map((mode) => (
            <button
              key={mode}
              type="button"
              onClick={() => switchFixture(mode)}
              className={cn(
                'rounded-full px-3 py-1 font-medium capitalize',
                fixtureMode === mode
                  ? 'bg-slate-100 text-slate-950'
                  : 'text-slate-300 hover:text-white',
              )}
            >
              {mode === 'worst' ? 'Worst case' : mode === 'one' ? 'One' : mode}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
