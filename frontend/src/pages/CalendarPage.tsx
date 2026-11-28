import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import * as Dialog from '@radix-ui/react-dialog';
import {
  Calendar as CalendarIcon,
  ChevronLeft,
  ChevronRight,
  Clock,
  ExternalLink,
  FileSpreadsheet,
  GraduationCap,
  MapPin,
  Upload,
  X,
} from 'lucide-react';
import { toast } from 'sonner';
import { extractErrorMessage, useAuth } from '../context/AuthContext';
import { buildDemoCalendarEvents } from '../fixtures/calendarFixtures';
import { calendarApi } from '../lib/api';
import { cn, formatExactDateTime, pluralize } from '../lib/format';
import { KUET_DEPARTMENTS, YEAR_TERM_OPTIONS } from '../lib/yearTerm';
import type { CalendarEvent, CalendarEventType } from '../types/domain';

type CalendarViewMode = 'month' | 'week' | 'schedule';

const EVENT_TYPE_META: Record<
  CalendarEventType,
  { label: string; dot: string; pill: string; badge: string }
> = {
  CT: {
    label: 'Class Tests (CT)',
    dot: 'bg-emerald-400',
    pill: 'border-emerald-500/40 bg-emerald-500/15 text-emerald-200 hover:bg-emerald-500/25',
    badge: 'border-emerald-500/40 bg-emerald-500/15 text-emerald-300',
  },
  LABTEST: {
    label: 'Lab Tests',
    dot: 'bg-cyan-400',
    pill: 'border-cyan-500/40 bg-cyan-500/15 text-cyan-200 hover:bg-cyan-500/25',
    badge: 'border-cyan-500/40 bg-cyan-500/15 text-cyan-300',
  },
  QUIZ: {
    label: 'Quizzes',
    dot: 'bg-amber-400',
    pill: 'border-amber-500/40 bg-amber-500/15 text-amber-200 hover:bg-amber-500/25',
    badge: 'border-amber-500/40 bg-amber-500/15 text-amber-300',
  },
  ASSIGNMENT: {
    label: 'Assignments',
    dot: 'bg-sky-400',
    pill: 'border-sky-500/40 bg-sky-500/15 text-sky-200 hover:bg-sky-500/25',
    badge: 'border-sky-500/40 bg-sky-500/15 text-sky-300',
  },
  PRESENTATION: {
    label: 'Presentations',
    dot: 'bg-violet-400',
    pill: 'border-violet-500/40 bg-violet-500/15 text-violet-200 hover:bg-violet-500/25',
    badge: 'border-violet-500/40 bg-violet-500/15 text-violet-300',
  },
  PROJECT: {
    label: 'Project Showcase',
    dot: 'bg-orange-400',
    pill: 'border-orange-500/40 bg-orange-500/15 text-orange-200 hover:bg-orange-500/25',
    badge: 'border-orange-500/40 bg-orange-500/15 text-orange-300',
  },
  TERM_FINAL: {
    label: 'Term Finals',
    dot: 'bg-rose-400',
    pill: 'border-rose-500/40 bg-rose-500/15 text-rose-200 hover:bg-rose-500/25',
    badge: 'border-rose-500/40 bg-rose-500/15 text-rose-300',
  },
};

const ALL_TYPES: CalendarEventType[] = [
  'CT',
  'LABTEST',
  'QUIZ',
  'ASSIGNMENT',
  'PRESENTATION',
  'PROJECT',
  'TERM_FINAL',
];
const WEEKDAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];

function dateKey(date: Date): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

function buildMonthGrid(anchor: Date): Date[] {
  const firstOfMonth = new Date(anchor.getFullYear(), anchor.getMonth(), 1);
  const start = new Date(firstOfMonth);
  start.setDate(firstOfMonth.getDate() - firstOfMonth.getDay());
  const days: Date[] = [];
  for (let i = 0; i < 42; i++) {
    const cell = new Date(start);
    cell.setDate(start.getDate() + i);
    days.push(cell);
  }
  return days;
}

function buildWeekGrid(anchor: Date): Date[] {
  const start = new Date(anchor);
  start.setDate(anchor.getDate() - anchor.getDay());
  const days: Date[] = [];
  for (let i = 0; i < 7; i++) {
    const cell = new Date(start);
    cell.setDate(start.getDate() + i);
    days.push(cell);
  }
  return days;
}

function formatShortTime(iso: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '';
  return d.toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' });
}

export function CalendarPage() {
  const { user, isDemoMode } = useAuth();

  const [viewMode, setViewMode] = useState<CalendarViewMode>('month');
  const [anchorDate, setAnchorDate] = useState<Date>(() => new Date());
  const [events, setEvents] = useState<CalendarEvent[]>(() => buildDemoCalendarEvents());

  // Category checkboxes (Google Calendar "My Calendars" style)
  const [enabledTypes, setEnabledTypes] = useState<Record<CalendarEventType, boolean>>({
    CT: true,
    LABTEST: true,
    QUIZ: true,
    ASSIGNMENT: true,
    PRESENTATION: true,
    PROJECT: true,
    TERM_FINAL: true,
  });

  // Batch (Year-Term) filter — active by default when student/CR has yearTerm set
  const [myBatchOnly, setMyBatchOnly] = useState<boolean>(Boolean(user?.yearTerm));
  const [departmentFilter, setDepartmentFilter] = useState<string>(user?.department ?? '');
  const [yearTermFilter, setYearTermFilter] = useState<string>('');

  const [selectedEvent, setSelectedEvent] = useState<CalendarEvent | null>(null);
  const [expandedDayKey, setExpandedDayKey] = useState<string | null>(null);

  useEffect(() => {
    setMyBatchOnly(Boolean(user?.yearTerm));
    if (user?.department) {
      setDepartmentFilter(user.department);
    }
  }, [user?.id, user?.yearTerm, user?.department]);

  const effectiveDepartment = myBatchOnly && user?.department ? user.department : departmentFilter;
  const effectiveYearTerm = myBatchOnly && user?.yearTerm ? user.yearTerm : yearTermFilter;

  const loadCalendar = useCallback(async () => {
    if (isDemoMode) {
      setEvents(buildDemoCalendarEvents());
      return;
    }

    try {
      const fetched = await calendarApi.listEvents({
        department: effectiveDepartment || undefined,
        yearTerm: effectiveYearTerm || undefined,
      });
      setEvents(fetched);
    } catch (err) {
      toast.error(extractErrorMessage(err, 'Could not load calendar events'));
    }
  }, [isDemoMode, effectiveDepartment, effectiveYearTerm]);

  useEffect(() => {
    void loadCalendar();
  }, [loadCalendar]);

  const filteredEvents = useMemo(() => {
    return events
      .filter((ev) => {
        if (!enabledTypes[ev.type]) return false;
        if (effectiveDepartment && ev.department && ev.department !== effectiveDepartment) {
          return false;
        }
        if (effectiveYearTerm && ev.yearTerm && ev.yearTerm !== effectiveYearTerm) {
          return false;
        }
        return true;
      })
      .sort((a, b) => new Date(a.startsAt).getTime() - new Date(b.startsAt).getTime());
  }, [events, enabledTypes, effectiveDepartment, effectiveYearTerm]);

  const eventsByDay = useMemo(() => {
    const map = new Map<string, CalendarEvent[]>();
    for (const ev of filteredEvents) {
      const d = new Date(ev.startsAt);
      if (Number.isNaN(d.getTime())) continue;
      const key = dateKey(d);
      const bucket = map.get(key);
      if (bucket) {
        bucket.push(ev);
      } else {
        map.set(key, [ev]);
      }
    }
    return map;
  }, [filteredEvents]);

  const typeCounts = useMemo(() => {
    const counts: Record<CalendarEventType, number> = {
      CT: 0,
      LABTEST: 0,
      QUIZ: 0,
      ASSIGNMENT: 0,
      PRESENTATION: 0,
      PROJECT: 0,
      TERM_FINAL: 0,
    };
    for (const ev of events) {
      if (effectiveDepartment && ev.department && ev.department !== effectiveDepartment) continue;
      if (effectiveYearTerm && ev.yearTerm && ev.yearTerm !== effectiveYearTerm) continue;
      counts[ev.type] += 1;
    }
    return counts;
  }, [events, effectiveDepartment, effectiveYearTerm]);

  const todayKey = dateKey(new Date());
  const monthGrid = useMemo(() => buildMonthGrid(anchorDate), [anchorDate]);
  const weekGrid = useMemo(() => buildWeekGrid(anchorDate), [anchorDate]);

  const headerPeriodLabel = useMemo(() => {
    if (viewMode === 'week') {
      const first = weekGrid[0];
      const last = weekGrid[6];
      return `${first.toLocaleDateString(undefined, { month: 'short', day: 'numeric' })} – ${last.toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' })}`;
    }
    return anchorDate.toLocaleDateString(undefined, { month: 'long', year: 'numeric' });
  }, [viewMode, anchorDate, weekGrid]);

  const shiftPeriod = (delta: number) => {
    const next = new Date(anchorDate);
    if (viewMode === 'week') {
      next.setDate(next.getDate() + delta * 7);
    } else {
      next.setMonth(next.getMonth() + delta);
    }
    setAnchorDate(next);
  };

  const toggleType = (type: CalendarEventType) => {
    setEnabledTypes((prev) => ({ ...prev, [type]: !prev[type] }));
  };

  return (
    <div className="space-y-5 pb-16">
      {/* Google Calendar Top Toolbar */}
      <div className="flex flex-col gap-3 rounded-2xl border border-slate-800 bg-slate-900/60 p-4 lg:flex-row lg:items-center lg:justify-between">
        <div className="flex flex-wrap items-center gap-3">
          <div className="flex items-center gap-2">
            <CalendarIcon className="h-5 w-5 text-emerald-400 shrink-0" aria-hidden="true" />
            <h2 className="text-xl font-bold tracking-tight text-white">Academic Calendar</h2>
          </div>

          <div className="flex items-center gap-1.5">
            <button
              type="button"
              onClick={() => setAnchorDate(new Date())}
              className="rounded-lg border border-slate-700 bg-slate-950 px-3 py-1.5 text-xs font-medium text-slate-200 transition-[background-color,transform] duration-100 ease-out hover:bg-slate-800 active:scale-[0.97]"
            >
              Today
            </button>
            <button
              type="button"
              aria-label="Previous period"
              onClick={() => shiftPeriod(-1)}
              className="rounded-lg border border-slate-800 p-1.5 text-slate-300 hover:bg-slate-800 hover:text-white active:scale-[0.97]"
            >
              <ChevronLeft className="h-4 w-4" />
            </button>
            <button
              type="button"
              aria-label="Next period"
              onClick={() => shiftPeriod(1)}
              className="rounded-lg border border-slate-800 p-1.5 text-slate-300 hover:bg-slate-800 hover:text-white active:scale-[0.97]"
            >
              <ChevronRight className="h-4 w-4" />
            </button>
          </div>

          <span className="text-base font-semibold tracking-tight text-slate-100 tabular-nums">
            {headerPeriodLabel}
          </span>
        </div>

        {/* View Switcher (Month / Week / Schedule) */}
        <div
          role="group"
          aria-label="Calendar view mode"
          className="inline-flex rounded-xl border border-slate-800 bg-slate-950 p-1"
        >
          {(
            [
              { id: 'month', label: 'Month' },
              { id: 'week', label: 'Week' },
              { id: 'schedule', label: 'Schedule' },
            ] as const
          ).map((mode) => (
            <button
              key={mode.id}
              type="button"
              aria-pressed={viewMode === mode.id}
              onClick={() => setViewMode(mode.id)}
              className={cn(
                'rounded-lg px-3 py-1.5 text-xs font-medium transition-colors duration-150 ease-out',
                viewMode === mode.id
                  ? 'bg-emerald-600 text-white'
                  : 'text-slate-400 hover:text-slate-200',
              )}
            >
              {mode.label}
            </button>
          ))}
        </div>
      </div>

      {/* Main Google Calendar Split: Left Filter Sidebar + Calendar Grid */}
      <div className="grid grid-cols-1 gap-5 lg:grid-cols-12">
        {/* Left Sidebar: My Calendars + Batch (Year-Term) Filter */}
        <aside className="space-y-4 lg:col-span-3">
          {/* Batch / Year-Term Scope */}
          <div className="rounded-2xl border border-slate-800 bg-slate-900/60 p-4 space-y-3">
            <h3 className="text-xs font-semibold uppercase tracking-wider text-slate-400">
              Batch &amp; Year–Term
            </h3>

            {user?.yearTerm && (
              <div className="flex flex-wrap gap-1.5">
                <button
                  type="button"
                  aria-pressed={myBatchOnly}
                  onClick={() => setMyBatchOnly(true)}
                  className={cn(
                    'inline-flex items-center gap-1 rounded-full border px-2.5 py-1 text-xs font-medium active:scale-[0.97]',
                    myBatchOnly
                      ? 'border-emerald-500/50 bg-emerald-500/15 text-emerald-200'
                      : 'border-slate-800 bg-slate-950 text-slate-400 hover:text-slate-200',
                  )}
                >
                  <GraduationCap className="h-3.5 w-3.5 shrink-0" />
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
                    'rounded-full border px-2.5 py-1 text-xs font-medium active:scale-[0.97]',
                    !myBatchOnly
                      ? 'border-sky-500/50 bg-sky-500/15 text-sky-200'
                      : 'border-slate-800 bg-slate-950 text-slate-400 hover:text-slate-200',
                  )}
                >
                  All Terms
                </button>
              </div>
            )}

            <div className="space-y-2">
              <select
                aria-label="Filter calendar by Year-Term"
                value={effectiveYearTerm}
                onChange={(e) => {
                  setMyBatchOnly(false);
                  setYearTermFilter(e.target.value);
                }}
                className="w-full rounded-lg border border-slate-700 bg-slate-950 px-2.5 py-2 text-xs text-slate-100 focus:border-emerald-500 focus:outline-none"
              >
                <option value="">All Year–Terms</option>
                {YEAR_TERM_OPTIONS.map((yt) => (
                  <option key={yt.code} value={yt.code}>
                    {yt.label}
                  </option>
                ))}
              </select>

              <select
                aria-label="Filter calendar by Department"
                value={effectiveDepartment}
                onChange={(e) => {
                  setMyBatchOnly(false);
                  setDepartmentFilter(e.target.value);
                }}
                className="w-full rounded-lg border border-slate-700 bg-slate-950 px-2.5 py-2 text-xs text-slate-100 focus:border-emerald-500 focus:outline-none"
              >
                <option value="">All Departments</option>
                {KUET_DEPARTMENTS.map((dept) => (
                  <option key={dept} value={dept}>
                    {dept}
                  </option>
                ))}
              </select>
            </div>
          </div>

          {/* Google Calendar Style Category Checkboxes */}
          <div className="rounded-2xl border border-slate-800 bg-slate-900/60 p-4 space-y-2.5">
            <div className="flex items-center justify-between">
              <h3 className="text-xs font-semibold uppercase tracking-wider text-slate-400">
                Synced Hubs
              </h3>
              <span className="text-[11px] text-slate-500 tabular-nums">
                {pluralize(filteredEvents.length, 'event', 'events')}
              </span>
            </div>

            <div className="space-y-1.5">
              {ALL_TYPES.map((type) => {
                const meta = EVENT_TYPE_META[type];
                return (
                  <label
                    key={type}
                    className="flex cursor-pointer items-center justify-between gap-2 rounded-lg px-2 py-1.5 text-xs text-slate-200 hover:bg-slate-800/60"
                  >
                    <span className="flex items-center gap-2.5 min-w-0">
                      <input
                        type="checkbox"
                        checked={enabledTypes[type]}
                        onChange={() => toggleType(type)}
                        className="h-3.5 w-3.5 rounded border-slate-600 bg-slate-950 accent-emerald-500"
                      />
                      <span className={cn('h-2.5 w-2.5 rounded-full shrink-0', meta.dot)} />
                      <span className="truncate">{meta.label}</span>
                    </span>
                    <span className="font-mono text-[11px] text-slate-500 tabular-nums">
                      {typeCounts[type]}
                    </span>
                  </label>
                );
              })}
            </div>
          </div>
        </aside>

        {/* Right Canvas: Month / Week / Schedule */}
        <section className="lg:col-span-9">
          {viewMode === 'month' && (
            <div className="overflow-hidden rounded-2xl border border-slate-800 bg-slate-900/50">
              <div className="grid grid-cols-7 border-b border-slate-800 bg-slate-950/70">
                {WEEKDAYS.map((day) => (
                  <div
                    key={day}
                    className="py-2 text-center text-[11px] font-semibold uppercase tracking-wider text-slate-400"
                  >
                    {day}
                  </div>
                ))}
              </div>
              <div className="grid grid-cols-7 divide-x divide-y divide-slate-800/80">
                {monthGrid.map((cellDate) => {
                  const key = dateKey(cellDate);
                  const isCurrentMonth = cellDate.getMonth() === anchorDate.getMonth();
                  const isToday = key === todayKey;
                  const dayEvents = eventsByDay.get(key) ?? [];
                  const expanded = expandedDayKey === key;
                  const visibleEvents = expanded ? dayEvents : dayEvents.slice(0, 3);

                  return (
                    <div
                      key={key}
                      className={cn(
                        'min-h-[108px] p-1.5 transition-colors',
                        !isCurrentMonth && 'bg-slate-950/50 text-slate-600',
                      )}
                    >
                      <div className="mb-1 flex items-center justify-between">
                        <span
                          className={cn(
                            'flex h-6 w-6 items-center justify-center rounded-full text-xs font-medium tabular-nums',
                            isToday
                              ? 'bg-emerald-500 text-slate-950 font-bold'
                              : isCurrentMonth
                                ? 'text-slate-200'
                                : 'text-slate-600',
                          )}
                        >
                          {cellDate.getDate()}
                        </span>
                      </div>

                      <div className="space-y-1">
                        {visibleEvents.map((ev) => (
                          <button
                            key={ev.id}
                            type="button"
                            onClick={() => setSelectedEvent(ev)}
                            className={cn(
                              'block w-full truncate rounded-md border px-1.5 py-0.5 text-left text-[11px] font-medium transition-colors',
                              EVENT_TYPE_META[ev.type].pill,
                            )}
                            title={`${ev.courseCode}: ${ev.title}`}
                          >
                            <span className="mr-1 opacity-75 tabular-nums">
                              {formatShortTime(ev.startsAt)}
                            </span>
                            <span>{ev.courseCode}</span> · {ev.title}
                          </button>
                        ))}
                        {!expanded && dayEvents.length > 3 && (
                          <button
                            type="button"
                            onClick={() => setExpandedDayKey(key)}
                            className="w-full rounded px-1 text-left text-[10px] font-semibold text-slate-400 hover:text-white tabular-nums"
                          >
                            +{dayEvents.length - 3} more
                          </button>
                        )}
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          )}

          {viewMode === 'week' && (
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-7">
              {weekGrid.map((cellDate) => {
                const key = dateKey(cellDate);
                const isToday = key === todayKey;
                const dayEvents = eventsByDay.get(key) ?? [];

                return (
                  <div
                    key={key}
                    className={cn(
                      'min-h-[260px] rounded-xl border p-3',
                      isToday
                        ? 'border-emerald-500/50 bg-emerald-950/15'
                        : 'border-slate-800 bg-slate-900/50',
                    )}
                  >
                    <div className="mb-3 border-b border-slate-800 pb-2 text-center">
                      <p className="text-[11px] font-semibold uppercase text-slate-400">
                        {WEEKDAYS[cellDate.getDay()]}
                      </p>
                      <p
                        className={cn(
                          'mt-0.5 text-lg font-bold tabular-nums',
                          isToday ? 'text-emerald-300' : 'text-white',
                        )}
                      >
                        {cellDate.getDate()}
                      </p>
                    </div>
                    <div className="space-y-2">
                      {dayEvents.length === 0 ? (
                        <p className="text-center text-[11px] text-slate-600">—</p>
                      ) : (
                        dayEvents.map((ev) => (
                          <button
                            key={ev.id}
                            type="button"
                            onClick={() => setSelectedEvent(ev)}
                            className={cn(
                              'w-full rounded-lg border p-2 text-left text-xs transition-colors',
                              EVENT_TYPE_META[ev.type].pill,
                            )}
                          >
                            <p className="font-mono text-[10px] opacity-80 tabular-nums">
                              {formatShortTime(ev.startsAt)} · {ev.courseCode}
                            </p>
                            <p className="mt-0.5 line-clamp-2 font-semibold">{ev.title}</p>
                            {ev.room && (
                              <p className="mt-1 truncate text-[10px] opacity-80">{ev.room}</p>
                            )}
                          </button>
                        ))
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          )}

          {viewMode === 'schedule' && (
            <div className="space-y-3">
              {filteredEvents.length === 0 ? (
                <div className="rounded-2xl border border-dashed border-slate-800 p-10 text-center text-sm text-slate-400">
                  No matching academic deadlines in this filter.
                </div>
              ) : (
                filteredEvents.map((ev) => (
                  <button
                    key={ev.id}
                    type="button"
                    onClick={() => setSelectedEvent(ev)}
                    className="flex w-full flex-col gap-2 rounded-xl border border-slate-800 bg-slate-900/60 p-4 text-left transition-colors hover:border-slate-700 sm:flex-row sm:items-center sm:justify-between"
                  >
                    <div className="min-w-0 space-y-1">
                      <div className="flex flex-wrap items-center gap-2">
                        <span
                          className={cn(
                            'rounded-full border px-2 py-0.5 text-[10px] font-semibold',
                            EVENT_TYPE_META[ev.type].badge,
                          )}
                        >
                          {EVENT_TYPE_META[ev.type].label}
                        </span>
                        <span className="font-mono text-xs font-semibold text-emerald-300">
                          {ev.courseCode}
                        </span>
                        {ev.yearTerm && (
                          <span className="font-mono text-[11px] text-slate-400 tabular-nums">
                            {ev.yearTerm}
                          </span>
                        )}
                      </div>
                      <p className="truncate text-sm font-semibold text-white">{ev.title}</p>
                    </div>

                    <div className="flex flex-wrap items-center gap-3 text-xs text-slate-400 shrink-0 tabular-nums">
                      <span className="inline-flex items-center gap-1">
                        <Clock className="h-3.5 w-3.5 text-slate-500" />
                        {formatExactDateTime(ev.startsAt)}
                      </span>
                      {ev.room && (
                        <span className="inline-flex items-center gap-1 text-amber-300">
                          <MapPin className="h-3.5 w-3.5" />
                          {ev.room}
                        </span>
                      )}
                    </div>
                  </button>
                ))
              )}
            </div>
          )}
        </section>
      </div>

      {/* Event Detail Modal (Google Calendar Popover Style) */}
      <Dialog.Root
        open={Boolean(selectedEvent)}
        onOpenChange={(open) => !open && setSelectedEvent(null)}
      >
        <Dialog.Portal>
          <Dialog.Overlay className="kuet-dialog-overlay fixed inset-0 z-50 bg-slate-950/75 backdrop-blur-sm" />
          <Dialog.Content className="kuet-dialog-content fixed left-1/2 top-1/2 z-50 w-[calc(100%-2rem)] max-w-md -translate-x-1/2 -translate-y-1/2 rounded-2xl border border-slate-800 bg-slate-900 p-6 shadow-2xl">
            {selectedEvent && (
              <div className="space-y-4">
                <div className="flex items-start justify-between gap-3">
                  <div className="space-y-1.5">
                    <span
                      className={cn(
                        'inline-flex rounded-full border px-2.5 py-0.5 text-[11px] font-semibold',
                        EVENT_TYPE_META[selectedEvent.type].badge,
                      )}
                    >
                      {EVENT_TYPE_META[selectedEvent.type].label}
                    </span>
                    <Dialog.Title className="text-lg font-bold tracking-tight text-white">
                      {selectedEvent.title}
                    </Dialog.Title>
                    <Dialog.Description className="text-xs text-slate-400">
                      {selectedEvent.courseCode} · {selectedEvent.courseTitle}
                      {selectedEvent.yearTerm ? ` (${selectedEvent.yearTerm})` : ''}
                    </Dialog.Description>
                  </div>
                  <Dialog.Close
                    aria-label="Close event details"
                    className="rounded-lg p-1 text-slate-400 hover:bg-slate-800 hover:text-white"
                  >
                    <X className="h-4 w-4" />
                  </Dialog.Close>
                </div>

                <div className="space-y-2 rounded-xl border border-slate-800 bg-slate-950/70 p-3.5 text-xs text-slate-300">
                  <div className="flex items-center gap-2 tabular-nums">
                    <Clock className="h-4 w-4 text-emerald-400 shrink-0" />
                    <span>{formatExactDateTime(selectedEvent.startsAt)}</span>
                  </div>
                  {selectedEvent.room && (
                    <div className="flex items-center gap-2 text-amber-200">
                      <MapPin className="h-4 w-4 text-amber-400 shrink-0" />
                      <span>Room: {selectedEvent.room}</span>
                    </div>
                  )}
                  {selectedEvent.assignedTeacherName && (
                    <div className="flex items-center gap-2 text-sky-200">
                      <GraduationCap className="h-4 w-4 text-sky-400 shrink-0" />
                      <span>{selectedEvent.assignedTeacherName}</span>
                    </div>
                  )}
                </div>

                {/* Submission & Submitted List Links when present */}
                {(selectedEvent.submissionLink || selectedEvent.submittedListLink) && (
                  <div className="flex flex-wrap gap-2">
                    {selectedEvent.submissionLink && (
                      <a
                        href={selectedEvent.submissionLink}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="inline-flex items-center gap-1.5 rounded-lg bg-emerald-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-emerald-500 active:scale-[0.97]"
                      >
                        <Upload className="h-3.5 w-3.5 shrink-0" />
                        <span>Submission Link</span>
                        <ExternalLink className="h-3 w-3 shrink-0" />
                      </a>
                    )}
                    {selectedEvent.submittedListLink && (
                      <a
                        href={selectedEvent.submittedListLink}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="inline-flex items-center gap-1.5 rounded-lg border border-sky-500/40 bg-sky-500/15 px-3 py-1.5 text-xs font-medium text-sky-200 hover:bg-sky-500/25 active:scale-[0.97]"
                      >
                        <FileSpreadsheet className="h-3.5 w-3.5 shrink-0" />
                        <span>Submitted List</span>
                        <ExternalLink className="h-3 w-3 shrink-0" />
                      </a>
                    )}
                  </div>
                )}

                <div className="flex items-center justify-between border-t border-slate-800 pt-3">
                  <Link
                    to={selectedEvent.hubPath}
                    onClick={() => setSelectedEvent(null)}
                    className="inline-flex items-center gap-1.5 text-xs font-medium text-emerald-300 hover:text-emerald-200"
                  >
                    <span>Open in Hub</span>
                    <ExternalLink className="h-3.5 w-3.5" />
                  </Link>
                  <Dialog.Close asChild>
                    <button
                      type="button"
                      className="rounded-lg border border-slate-700 px-3 py-1.5 text-xs text-slate-300 hover:bg-slate-800"
                    >
                      Close
                    </button>
                  </Dialog.Close>
                </div>
              </div>
            )}
          </Dialog.Content>
        </Dialog.Portal>
      </Dialog.Root>
    </div>
  );
}
