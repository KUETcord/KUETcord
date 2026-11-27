import React, { useCallback, useEffect, useMemo, useState } from 'react';
import * as Dialog from '@radix-ui/react-dialog';
import {
  BookOpen,
  GraduationCap,
  Loader2,
  Pencil,
  Plus,
  Search,
  ShieldAlert,
  ShieldCheck,
  Trash2,
  UserPlus,
  Users,
  X,
} from 'lucide-react';
import { toast } from 'sonner';
import { extractErrorMessage, useAuth } from '../context/AuthContext';
import { DEMO_ALL_USERS, DEMO_COURSES } from '../fixtures/ctHubFixtures';
import { adminApi } from '../lib/api';
import { cn, getInitials, pluralize } from '../lib/format';
import { KUET_DEPARTMENTS, resolveYearTerm, YEAR_TERM_OPTIONS } from '../lib/yearTerm';
import type { CourseOffering, GlobalRole, RoleName, UserStatus, UserSummary } from '../types/domain';

type AdminTab = 'courses' | 'users';

const ALL_ROLE_OPTIONS: ReadonlyArray<{ role: RoleName; label: string }> = [
  { role: 'ROLE_STUDENT', label: 'Student' },
  { role: 'ROLE_CR', label: 'CR' },
  { role: 'ROLE_TEACHER', label: 'Teacher' },
  { role: 'ROLE_MODERATOR', label: 'Moderator' },
  { role: 'ROLE_ADMIN', label: 'Admin' },
];

function deriveGlobalRole(roles: string[]): GlobalRole {
  if (roles.includes('ROLE_ADMIN')) return 'ADMIN';
  if (roles.includes('ROLE_MODERATOR')) return 'MODERATOR';
  if (roles.includes('ROLE_TEACHER')) return 'TEACHER';
  if (roles.includes('ROLE_CR')) return 'CR';
  return 'STUDENT';
}

export function AdminConsolePage() {
  const { user, isDemoMode, canAccessAdminConsole, enterDemoMode } = useAuth();
  const [tab, setTab] = useState<AdminTab>('courses');

  // Courses CRUD state
  const [courses, setCourses] = useState<CourseOffering[]>(DEMO_COURSES);
  const [courseSearch, setCourseSearch] = useState('');
  const [courseDept, setCourseDept] = useState('');
  const [courseYearTerm, setCourseYearTerm] = useState('');
  const [courseModalOpen, setCourseModalOpen] = useState(false);
  const [editingCourse, setEditingCourse] = useState<CourseOffering | null>(null);

  // Course form fields
  const [cCode, setCCode] = useState('');
  const [cTitle, setCTitle] = useState('');
  const [cDept, setCDept] = useState('CSE');
  const [cYearTerm, setCYearTerm] = useState('3-2');
  const [cAcademicYear, setCAcademicYear] = useState('2026');
  const [cTargetBatch, setCTargetBatch] = useState('');
  const [savingCourse, setSavingCourse] = useState(false);

  // Users CRUD state
  const [users, setUsers] = useState<UserSummary[]>(DEMO_ALL_USERS);
  const [userSearch, setUserSearch] = useState('');
  const [userRoleFilter, setUserRoleFilter] = useState<RoleName | ''>('');
  const [userDeptFilter, setUserDeptFilter] = useState('');

  // Add Teacher Directly modal
  const [teacherModalOpen, setTeacherModalOpen] = useState(false);
  const [tEmail, setTEmail] = useState('');
  const [tName, setTName] = useState('');
  const [tDept, setTDept] = useState('CSE');
  const [tBio, setTBio] = useState('');
  const [tPassword, setTPassword] = useState('');
  const [savingTeacher, setSavingTeacher] = useState(false);

  // Edit User modal
  const [editingUser, setEditingUser] = useState<UserSummary | null>(null);
  const [uName, setUName] = useState('');
  const [uDept, setUDept] = useState('CSE');
  const [uBatch, setUBatch] = useState('');
  const [uYearTerm, setUYearTerm] = useState('');
  const [uStatus, setUStatus] = useState<UserStatus>('ACTIVE');
  const [uRoles, setURoles] = useState<RoleName[]>(['ROLE_STUDENT']);
  const [uResetPassword, setUResetPassword] = useState('');
  const [savingUser, setSavingUser] = useState(false);

  const isAdmin = user?.globalRole === 'ADMIN';

  const loadGovernanceData = useCallback(async () => {
    if (!canAccessAdminConsole) return;
    if (isDemoMode) return;

    try {
      const [fetchedCourses, fetchedUsers] = await Promise.all([
        adminApi.listCourses(),
        adminApi.listUsers(),
      ]);
      setCourses(fetchedCourses);
      setUsers(fetchedUsers);
    } catch (err) {
      toast.error(extractErrorMessage(err, 'Failed to load governance console data'));
    }
  }, [canAccessAdminConsole, isDemoMode]);

  useEffect(() => {
    void loadGovernanceData();
  }, [loadGovernanceData]);

  const filteredCourses = useMemo(() => {
    const q = courseSearch.trim().toLowerCase();
    return courses.filter((c) => {
      const resolvedYt = resolveYearTerm(c.yearTerm, c.semester);
      if (courseDept && c.department !== courseDept) return false;
      if (courseYearTerm && resolvedYt !== courseYearTerm) return false;
      if (q && !c.courseCode.toLowerCase().includes(q) && !c.courseTitle.toLowerCase().includes(q)) {
        return false;
      }
      return true;
    });
  }, [courses, courseDept, courseYearTerm, courseSearch]);

  const filteredUsers = useMemo(() => {
    const q = userSearch.trim().toLowerCase();
    return users.filter((u) => {
      if (userRoleFilter && !u.roles.includes(userRoleFilter)) return false;
      if (userDeptFilter && u.department !== userDeptFilter) return false;
      if (
        q &&
        !u.displayName.toLowerCase().includes(q) &&
        !u.email.toLowerCase().includes(q) &&
        !u.username.toLowerCase().includes(q)
      ) {
        return false;
      }
      return true;
    });
  }, [users, userRoleFilter, userDeptFilter, userSearch]);

  if (!canAccessAdminConsole) {
    return (
      <div className="mx-auto max-w-lg rounded-2xl border border-slate-800 bg-slate-900/60 p-8 text-center">
        <ShieldAlert className="mx-auto mb-3 h-10 w-10 text-amber-400" aria-hidden="true" />
        <h2 className="text-xl font-bold tracking-tight text-white">Moderator or Admin Authority Required</h2>
        <p className="mt-1.5 text-xs text-slate-400">
          Only <span className="font-mono text-slate-200">ROLE_MODERATOR</span> and{' '}
          <span className="font-mono text-slate-200">ROLE_ADMIN</span> can manage courses and provision faculty accounts.
        </p>
        {isDemoMode && (
          <button
            type="button"
            onClick={() => enterDemoMode('ADMIN')}
            className="mt-4 inline-flex items-center gap-2 rounded-xl bg-emerald-600 px-4 py-2 text-xs font-medium text-white hover:bg-emerald-500 active:scale-[0.97]"
          >
            Switch Preview to Admin
          </button>
        )}
      </div>
    );
  }

  const openCreateCourse = () => {
    setEditingCourse(null);
    setCCode('');
    setCTitle('');
    setCDept('CSE');
    setCYearTerm('3-2');
    setCAcademicYear('2026');
    setCTargetBatch('');
    setCourseModalOpen(true);
  };

  const openEditCourse = (course: CourseOffering) => {
    setEditingCourse(course);
    setCCode(course.courseCode);
    setCTitle(course.courseTitle);
    setCDept(course.department);
    setCYearTerm(resolveYearTerm(course.yearTerm, course.semester) ?? '3-2');
    setCAcademicYear(course.academicYear);
    setCTargetBatch(course.targetBatch ?? '');
    setCourseModalOpen(true);
  };

  const handleSaveCourse = async (e: React.FormEvent) => {
    e.preventDefault();
    setSavingCourse(true);
    try {
      const payload = {
        courseCode: cCode.trim().toUpperCase(),
        courseTitle: cTitle.trim(),
        department: cDept,
        yearTerm: cYearTerm,
        academicYear: cAcademicYear.trim(),
        targetBatch: cTargetBatch.trim() || undefined,
      };

      if (isDemoMode) {
        if (editingCourse) {
          setCourses((prev) =>
            prev.map((c) =>
              c.id === editingCourse.id
                ? { ...c, ...payload, semester: cYearTerm }
                : c,
            ),
          );
          toast.success(`Updated ${payload.courseCode}`);
        } else {
          const created: CourseOffering = {
            id: Date.now(),
            courseCode: payload.courseCode,
            courseTitle: payload.courseTitle,
            department: payload.department,
            semester: payload.yearTerm,
            yearTerm: payload.yearTerm,
            targetBatch: payload.targetBatch ?? null,
            academicYear: payload.academicYear,
            followed: false,
          };
          setCourses((prev) => [created, ...prev]);
          toast.success(`Created ${payload.courseCode}`);
        }
        setCourseModalOpen(false);
        return;
      }

      if (editingCourse) {
        const updated = await adminApi.updateCourse(editingCourse.id, payload);
        setCourses((prev) => prev.map((c) => (c.id === updated.id ? updated : c)));
        toast.success(`Updated ${updated.courseCode}`);
      } else {
        const created = await adminApi.createCourse(payload);
        setCourses((prev) => [created, ...prev]);
        toast.success(`Created ${created.courseCode}`);
      }
      setCourseModalOpen(false);
    } catch (err) {
      toast.error(extractErrorMessage(err, 'Failed to save course offering'));
    } finally {
      setSavingCourse(false);
    }
  };

  const handleDeleteCourse = async (course: CourseOffering) => {
    try {
      if (!isDemoMode) {
        await adminApi.deleteCourse(course.id);
      }
      setCourses((prev) => prev.filter((c) => c.id !== course.id));
      toast.success(`Deleted ${course.courseCode}`);
    } catch (err) {
      toast.error(extractErrorMessage(err, 'Could not delete course offering'));
    }
  };

  const handleCreateTeacher = async (e: React.FormEvent) => {
    e.preventDefault();
    setSavingTeacher(true);
    try {
      if (isDemoMode) {
        const created: UserSummary = {
          id: Date.now(),
          email: tEmail.trim().toLowerCase(),
          displayName: tName.trim(),
          username: tEmail.trim().split('@')[0] ?? 'teacher',
          globalRole: 'TEACHER',
          roles: ['ROLE_TEACHER'],
          department: tDept,
          batch: 'Faculty',
          yearTerm: null,
          bio: tBio.trim() || null,
          emailVerified: true,
          status: 'ACTIVE',
        };
        setUsers((prev) => [created, ...prev]);
        toast.success(`Provisioned verified teacher account for ${created.displayName}`);
      } else {
        const created = await adminApi.createTeacher({
          email: tEmail.trim(),
          displayName: tName.trim(),
          department: tDept,
          bio: tBio.trim() || undefined,
          initialPassword: tPassword,
        });
        setUsers((prev) => [created, ...prev]);
        toast.success(`Provisioned verified teacher account for ${created.displayName}`);
      }
      setTEmail('');
      setTName('');
      setTBio('');
      setTPassword('');
      setTeacherModalOpen(false);
    } catch (err) {
      toast.error(extractErrorMessage(err, 'Could not provision teacher account'));
    } finally {
      setSavingTeacher(false);
    }
  };

  const openEditUser = (target: UserSummary) => {
    setEditingUser(target);
    setUName(target.displayName);
    setUDept(target.department ?? 'CSE');
    setUBatch(target.batch ?? '');
    setUYearTerm(target.yearTerm ?? '');
    setUStatus(target.status);
    setURoles((target.roles as RoleName[]) ?? ['ROLE_STUDENT']);
    setUResetPassword('');
  };

  const toggleRoleInSelection = (role: RoleName) => {
    setURoles((prev) => {
      if (prev.includes(role)) {
        return prev.length > 1 ? prev.filter((r) => r !== role) : prev;
      }
      return [...prev, role];
    });
  };

  const handleSaveUser = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!editingUser) return;
    setSavingUser(true);
    try {
      if (isDemoMode) {
        const updated: UserSummary = {
          ...editingUser,
          displayName: uName.trim(),
          department: uDept || null,
          batch: uBatch.trim() || null,
          yearTerm: uYearTerm || null,
          status: uStatus,
          roles: uRoles,
          globalRole: deriveGlobalRole(uRoles),
        };
        setUsers((prev) => prev.map((u) => (u.id === updated.id ? updated : u)));
        toast.success(`Updated ${updated.displayName}`);
      } else {
        const updated = await adminApi.updateUser(editingUser.id, {
          displayName: uName.trim(),
          department: uDept,
          batch: uBatch.trim(),
          yearTerm: uYearTerm,
          roles: uRoles,
          status: uStatus,
          resetPassword: uResetPassword.trim() || undefined,
        });
        setUsers((prev) => prev.map((u) => (u.id === updated.id ? updated : u)));
        toast.success(`Updated ${updated.displayName}`);
      }
      setEditingUser(null);
    } catch (err) {
      toast.error(extractErrorMessage(err, 'Failed to update user account'));
    } finally {
      setSavingUser(false);
    }
  };

  const handleDeleteUser = async (target: UserSummary) => {
    if (target.id === user?.id) {
      toast.error('You cannot delete your own account');
      return;
    }
    try {
      if (!isDemoMode) {
        await adminApi.deleteUser(target.id);
      }
      setUsers((prev) => prev.filter((u) => u.id !== target.id));
      toast.success(`Deleted ${target.email}`);
    } catch (err) {
      toast.error(extractErrorMessage(err, 'Could not delete user'));
    }
  };

  return (
    <div className="space-y-6 pb-16">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h2 className="flex items-center gap-2 text-2xl font-bold tracking-tight text-white sm:text-3xl">
            <ShieldCheck className="h-6 w-6 text-emerald-400 shrink-0" aria-hidden="true" />
            <span>Governance Console</span>
          </h2>
          <p className="mt-1 max-w-[65ch] text-sm text-slate-400">
            Admin &amp; Moderator CRUD for KUET course offerings, direct Teacher account provisioning, and role management.
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <button
            type="button"
            onClick={() => setTeacherModalOpen(true)}
            className="inline-flex items-center gap-1.5 rounded-xl bg-sky-600 px-3.5 py-2 text-xs font-medium text-white transition-[background-color,transform] duration-100 ease-out hover:bg-sky-500 active:scale-[0.97]"
          >
            <UserPlus className="h-4 w-4 shrink-0" aria-hidden="true" />
            <span>Add Teacher Directly</span>
          </button>
          <button
            type="button"
            onClick={openCreateCourse}
            className="inline-flex items-center gap-1.5 rounded-xl bg-emerald-600 px-3.5 py-2 text-xs font-medium text-white transition-[background-color,transform] duration-100 ease-out hover:bg-emerald-500 active:scale-[0.97]"
          >
            <Plus className="h-4 w-4 shrink-0" aria-hidden="true" />
            <span>Add Course</span>
          </button>
        </div>
      </div>

      {/* Tabs */}
      <div className="inline-flex rounded-xl border border-slate-800 bg-slate-900 p-1">
        <button
          type="button"
          onClick={() => setTab('courses')}
          className={cn(
            'inline-flex items-center gap-2 rounded-lg px-4 py-2 text-xs font-medium transition-colors duration-150 ease-out',
            tab === 'courses' ? 'bg-slate-800 text-white' : 'text-slate-400 hover:text-slate-200',
          )}
        >
          <BookOpen className="h-3.5 w-3.5 shrink-0" />
          <span className="tabular-nums">Courses ({courses.length})</span>
        </button>
        <button
          type="button"
          onClick={() => setTab('users')}
          className={cn(
            'inline-flex items-center gap-2 rounded-lg px-4 py-2 text-xs font-medium transition-colors duration-150 ease-out',
            tab === 'users' ? 'bg-slate-800 text-white' : 'text-slate-400 hover:text-slate-200',
          )}
        >
          <Users className="h-3.5 w-3.5 shrink-0" />
          <span className="tabular-nums">Teachers &amp; Users ({users.length})</span>
        </button>
      </div>

      {tab === 'courses' && (
        <section className="space-y-4">
          <div className="grid grid-cols-1 gap-3 rounded-xl border border-slate-800 bg-slate-900/50 p-4 sm:grid-cols-12">
            <div className="relative sm:col-span-6">
              <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-500" />
              <input
                type="search"
                value={courseSearch}
                onChange={(e) => setCourseSearch(e.target.value)}
                placeholder="Search course code or title..."
                className="w-full rounded-lg border border-slate-700 bg-slate-950 py-2 pl-9 pr-3 text-sm text-slate-100 placeholder:text-slate-500 focus:border-emerald-500 focus:outline-none"
              />
            </div>
            <div className="sm:col-span-3">
              <select
                aria-label="Filter courses by department"
                value={courseDept}
                onChange={(e) => setCourseDept(e.target.value)}
                className="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-slate-200 focus:border-emerald-500 focus:outline-none"
              >
                <option value="">All Departments</option>
                {KUET_DEPARTMENTS.map((dept) => (
                  <option key={dept} value={dept}>
                    {dept}
                  </option>
                ))}
              </select>
            </div>
            <div className="sm:col-span-3">
              <select
                aria-label="Filter courses by Year-Term"
                value={courseYearTerm}
                onChange={(e) => setCourseYearTerm(e.target.value)}
                className="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-slate-200 focus:border-emerald-500 focus:outline-none"
              >
                <option value="">All Year–Terms</option>
                {YEAR_TERM_OPTIONS.map((yt) => (
                  <option key={yt.code} value={yt.code}>
                    {yt.label}
                  </option>
                ))}
              </select>
            </div>
          </div>

          <p className="text-xs text-slate-500 tabular-nums">
            Showing {pluralize(filteredCourses.length, 'course offering', 'course offerings')}
          </p>

          <div className="overflow-x-auto rounded-xl border border-slate-800 bg-slate-900/60">
            <table className="w-full text-left text-xs">
              <thead className="border-b border-slate-800 bg-slate-950/60 text-slate-400">
                <tr>
                  <th className="px-4 py-3 font-medium">Code</th>
                  <th className="px-4 py-3 font-medium">Title</th>
                  <th className="px-4 py-3 font-medium">Dept</th>
                  <th className="px-4 py-3 font-medium">Year–Term</th>
                  <th className="px-4 py-3 font-medium">Session</th>
                  <th className="px-4 py-3 text-right font-medium">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800/70">
                {filteredCourses.map((course) => (
                  <tr key={course.id} className="hover:bg-slate-800/30">
                    <td className="px-4 py-3 font-mono font-semibold text-emerald-300">
                      {course.courseCode}
                    </td>
                    <td className="px-4 py-3 text-slate-100">{course.courseTitle}</td>
                    <td className="px-4 py-3 text-slate-300">{course.department}</td>
                    <td className="px-4 py-3 font-mono text-sky-300 tabular-nums">
                      {resolveYearTerm(course.yearTerm, course.semester) ?? course.semester}
                    </td>
                    <td className="px-4 py-3 text-slate-400 tabular-nums">{course.academicYear}</td>
                    <td className="px-4 py-3 text-right">
                      <div className="inline-flex items-center gap-1.5">
                        <button
                          type="button"
                          onClick={() => openEditCourse(course)}
                          aria-label={`Edit ${course.courseCode}`}
                          className="rounded-md border border-slate-700 px-2 py-1 text-slate-300 hover:bg-slate-800 hover:text-white active:scale-[0.97]"
                        >
                          <Pencil className="h-3.5 w-3.5" />
                        </button>
                        <button
                          type="button"
                          onClick={() => handleDeleteCourse(course)}
                          aria-label={`Delete ${course.courseCode}`}
                          className="rounded-md border border-rose-500/30 bg-rose-500/10 px-2 py-1 text-rose-300 hover:bg-rose-500/20 active:scale-[0.97]"
                        >
                          <Trash2 className="h-3.5 w-3.5" />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}

      {tab === 'users' && (
        <section className="space-y-4">
          <div className="grid grid-cols-1 gap-3 rounded-xl border border-slate-800 bg-slate-900/50 p-4 sm:grid-cols-12">
            <div className="relative sm:col-span-6">
              <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-500" />
              <input
                type="search"
                value={userSearch}
                onChange={(e) => setUserSearch(e.target.value)}
                placeholder="Search teacher or student by name, email, username..."
                className="w-full rounded-lg border border-slate-700 bg-slate-950 py-2 pl-9 pr-3 text-sm text-slate-100 placeholder:text-slate-500 focus:border-emerald-500 focus:outline-none"
              />
            </div>
            <div className="sm:col-span-3">
              <select
                aria-label="Filter users by role"
                value={userRoleFilter}
                onChange={(e) => setUserRoleFilter(e.target.value as RoleName | '')}
                className="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-slate-200 focus:border-emerald-500 focus:outline-none"
              >
                <option value="">All Roles</option>
                {ALL_ROLE_OPTIONS.map((opt) => (
                  <option key={opt.role} value={opt.role}>
                    {opt.label}
                  </option>
                ))}
              </select>
            </div>
            <div className="sm:col-span-3">
              <select
                aria-label="Filter users by department"
                value={userDeptFilter}
                onChange={(e) => setUserDeptFilter(e.target.value)}
                className="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-slate-200 focus:border-emerald-500 focus:outline-none"
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

          <div className="overflow-x-auto rounded-xl border border-slate-800 bg-slate-900/60">
            <table className="w-full text-left text-xs">
              <thead className="border-b border-slate-800 bg-slate-950/60 text-slate-400">
                <tr>
                  <th className="px-4 py-3 font-medium">Account</th>
                  <th className="px-4 py-3 font-medium">Dept &amp; Term</th>
                  <th className="px-4 py-3 font-medium">Roles</th>
                  <th className="px-4 py-3 font-medium">Status</th>
                  <th className="px-4 py-3 text-right font-medium">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800/70">
                {filteredUsers.map((u) => (
                  <tr key={u.id} className="hover:bg-slate-800/30">
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-2.5">
                        <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-slate-800 text-[11px] font-semibold text-emerald-300">
                          {getInitials(u.displayName)}
                        </span>
                        <div className="min-w-0">
                          <p className="truncate font-semibold text-slate-100">{u.displayName}</p>
                          <p className="truncate text-[11px] text-slate-400">{u.email}</p>
                        </div>
                      </div>
                    </td>
                    <td className="px-4 py-3 text-slate-300 tabular-nums">
                      {u.department ?? '—'}
                      {u.yearTerm ? ` · ${u.yearTerm}` : u.batch ? ` · ${u.batch}` : ''}
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex flex-wrap gap-1">
                        {u.roles.map((r) => (
                          <span
                            key={r}
                            className="rounded bg-slate-800 px-1.5 py-0.5 font-mono text-[10px] text-emerald-300"
                          >
                            {r.replace('ROLE_', '')}
                          </span>
                        ))}
                      </div>
                    </td>
                    <td className="px-4 py-3">
                      <span
                        className={cn(
                          'rounded-full px-2 py-0.5 text-[10px] font-semibold',
                          u.status === 'ACTIVE'
                            ? 'bg-emerald-500/15 text-emerald-300'
                            : 'bg-rose-500/15 text-rose-300',
                        )}
                      >
                        {u.status}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-right">
                      <div className="inline-flex items-center gap-1.5">
                        <button
                          type="button"
                          onClick={() => openEditUser(u)}
                          aria-label={`Edit ${u.displayName}`}
                          className="rounded-md border border-slate-700 px-2 py-1 text-slate-300 hover:bg-slate-800 hover:text-white active:scale-[0.97]"
                        >
                          <Pencil className="h-3.5 w-3.5" />
                        </button>
                        {u.id !== user?.id && (
                          <button
                            type="button"
                            onClick={() => handleDeleteUser(u)}
                            aria-label={`Delete ${u.displayName}`}
                            className="rounded-md border border-rose-500/30 bg-rose-500/10 px-2 py-1 text-rose-300 hover:bg-rose-500/20 active:scale-[0.97]"
                          >
                            <Trash2 className="h-3.5 w-3.5" />
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}

      {/* Course Create/Edit Modal */}
      <Dialog.Root open={courseModalOpen} onOpenChange={setCourseModalOpen}>
        <Dialog.Portal>
          <Dialog.Overlay className="kuet-dialog-overlay fixed inset-0 z-50 bg-slate-950/75 backdrop-blur-sm" />
          <Dialog.Content className="kuet-dialog-content fixed left-1/2 top-1/2 z-50 w-[calc(100%-2rem)] max-w-md -translate-x-1/2 -translate-y-1/2 rounded-2xl border border-slate-800 bg-slate-900 p-6 shadow-2xl">
            <div className="flex items-start justify-between">
              <Dialog.Title className="text-lg font-semibold text-white">
                {editingCourse ? 'Edit Course Offering' : 'Add Course Offering'}
              </Dialog.Title>
              <Dialog.Close aria-label="Close modal" className="rounded-lg p-1 text-slate-400 hover:text-white">
                <X className="h-4 w-4" />
              </Dialog.Close>
            </div>
            <Dialog.Description className="mt-1 text-xs text-slate-400">
              Courses appear immediately across CT Hub, Assignment Hub, Presentation Hub, and Project Hub.
            </Dialog.Description>
            <form onSubmit={handleSaveCourse} className="mt-4 space-y-3.5">
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label htmlFor="admin-c-code" className="block text-xs font-medium text-slate-300">
                    Course Code
                  </label>
                  <input
                    id="admin-c-code"
                    value={cCode}
                    onChange={(e) => setCCode(e.target.value)}
                    placeholder="CSE 3201"
                    className="mt-1 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-slate-100"
                    required
                  />
                </div>
                <div>
                  <label htmlFor="admin-c-dept" className="block text-xs font-medium text-slate-300">
                    Department
                  </label>
                  <select
                    id="admin-c-dept"
                    value={cDept}
                    onChange={(e) => setCDept(e.target.value)}
                    className="mt-1 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-slate-100"
                  >
                    {KUET_DEPARTMENTS.map((d) => (
                      <option key={d} value={d}>
                        {d}
                      </option>
                    ))}
                  </select>
                </div>
              </div>
              <div>
                <label htmlFor="admin-c-title" className="block text-xs font-medium text-slate-300">
                  Course Title
                </label>
                <input
                  id="admin-c-title"
                  value={cTitle}
                  onChange={(e) => setCTitle(e.target.value)}
                  placeholder="Software Engineering"
                  className="mt-1 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-slate-100"
                  required
                />
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label htmlFor="admin-c-yt" className="block text-xs font-medium text-slate-300">
                    Year &amp; Term
                  </label>
                  <select
                    id="admin-c-yt"
                    value={cYearTerm}
                    onChange={(e) => setCYearTerm(e.target.value)}
                    className="mt-1 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-slate-100"
                  >
                    {YEAR_TERM_OPTIONS.map((yt) => (
                      <option key={yt.code} value={yt.code}>
                        {yt.label}
                      </option>
                    ))}
                  </select>
                </div>
                <div>
                  <label htmlFor="admin-c-year" className="block text-xs font-medium text-slate-300">
                    Academic Year
                  </label>
                  <input
                    id="admin-c-year"
                    value={cAcademicYear}
                    onChange={(e) => setCAcademicYear(e.target.value)}
                    placeholder="2026"
                    className="mt-1 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-slate-100 tabular-nums"
                    required
                  />
                </div>
              </div>
              <div>
                <label htmlFor="admin-c-batch" className="block text-xs font-medium text-slate-300">
                  Target Batch (Optional)
                </label>
                <input
                  id="admin-c-batch"
                  value={cTargetBatch}
                  onChange={(e) => setCTargetBatch(e.target.value)}
                  placeholder="2k20"
                  className="mt-1 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-slate-100"
                />
              </div>
              <div className="flex justify-end gap-2 pt-2">
                <Dialog.Close asChild>
                  <button
                    type="button"
                    className="rounded-lg border border-slate-700 px-3 py-2 text-xs text-slate-300 hover:bg-slate-800"
                  >
                    Cancel
                  </button>
                </Dialog.Close>
                <button
                  type="submit"
                  disabled={savingCourse}
                  className="inline-flex items-center gap-1.5 rounded-lg bg-emerald-600 px-4 py-2 text-xs font-medium text-white hover:bg-emerald-500 active:scale-[0.97]"
                >
                  {savingCourse && <Loader2 className="h-3.5 w-3.5 animate-spin" />}
                  <span>{editingCourse ? 'Save Course' : 'Create Course'}</span>
                </button>
              </div>
            </form>
          </Dialog.Content>
        </Dialog.Portal>
      </Dialog.Root>

      {/* Direct Teacher Provisioning Modal */}
      <Dialog.Root open={teacherModalOpen} onOpenChange={setTeacherModalOpen}>
        <Dialog.Portal>
          <Dialog.Overlay className="kuet-dialog-overlay fixed inset-0 z-50 bg-slate-950/75 backdrop-blur-sm" />
          <Dialog.Content className="kuet-dialog-content fixed left-1/2 top-1/2 z-50 w-[calc(100%-2rem)] max-w-md -translate-x-1/2 -translate-y-1/2 rounded-2xl border border-slate-800 bg-slate-900 p-6 shadow-2xl">
            <div className="flex items-start justify-between">
              <Dialog.Title className="flex items-center gap-2 text-lg font-semibold text-white">
                <GraduationCap className="h-5 w-5 text-sky-400" />
                <span>Add Teacher Account Directly</span>
              </Dialog.Title>
              <Dialog.Close aria-label="Close modal" className="rounded-lg p-1 text-slate-400 hover:text-white">
                <X className="h-4 w-4" />
              </Dialog.Close>
            </div>
            <Dialog.Description className="mt-1 text-xs text-slate-400">
              Creates a pre-verified <span className="font-mono text-sky-300">ROLE_TEACHER</span> account immediately ready to sign in and approve CTs, Assignments, Presentations, and Projects.
            </Dialog.Description>
            <form onSubmit={handleCreateTeacher} className="mt-4 space-y-3.5">
              <div>
                <label htmlFor="admin-t-name" className="block text-xs font-medium text-slate-300">
                  Faculty Full Name
                </label>
                <input
                  id="admin-t-name"
                  value={tName}
                  onChange={(e) => setTName(e.target.value)}
                  placeholder="Prof. Dr. M. A. Hashem"
                  className="mt-1 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-slate-100"
                  required
                />
              </div>
              <div className="grid grid-cols-3 gap-3">
                <div className="col-span-2">
                  <label htmlFor="admin-t-email" className="block text-xs font-medium text-slate-300">
                    KUET Faculty Email
                  </label>
                  <input
                    id="admin-t-email"
                    type="email"
                    value={tEmail}
                    onChange={(e) => setTEmail(e.target.value)}
                    placeholder="hashem@cse.kuet.ac.bd"
                    className="mt-1 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-slate-100"
                    required
                  />
                </div>
                <div>
                  <label htmlFor="admin-t-dept" className="block text-xs font-medium text-slate-300">
                    Department
                  </label>
                  <select
                    id="admin-t-dept"
                    value={tDept}
                    onChange={(e) => setTDept(e.target.value)}
                    className="mt-1 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-slate-100"
                  >
                    {KUET_DEPARTMENTS.map((d) => (
                      <option key={d} value={d}>
                        {d}
                      </option>
                    ))}
                  </select>
                </div>
              </div>
              <div>
                <label htmlFor="admin-t-bio" className="block text-xs font-medium text-slate-300">
                  Designation / Note (Optional)
                </label>
                <input
                  id="admin-t-bio"
                  value={tBio}
                  onChange={(e) => setTBio(e.target.value)}
                  placeholder="Professor, Dept. of CSE"
                  className="mt-1 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-slate-100"
                />
              </div>
              <div>
                <label htmlFor="admin-t-pw" className="block text-xs font-medium text-slate-300">
                  Initial Password (min 8 chars)
                </label>
                <input
                  id="admin-t-pw"
                  type="text"
                  minLength={8}
                  value={tPassword}
                  onChange={(e) => setTPassword(e.target.value)}
                  placeholder="TempPass2026!"
                  className="mt-1 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 font-mono text-sm text-slate-100"
                  required
                />
              </div>
              <div className="flex justify-end gap-2 pt-2">
                <Dialog.Close asChild>
                  <button
                    type="button"
                    className="rounded-lg border border-slate-700 px-3 py-2 text-xs text-slate-300 hover:bg-slate-800"
                  >
                    Cancel
                  </button>
                </Dialog.Close>
                <button
                  type="submit"
                  disabled={savingTeacher}
                  className="inline-flex items-center gap-1.5 rounded-lg bg-sky-600 px-4 py-2 text-xs font-medium text-white hover:bg-sky-500 active:scale-[0.97]"
                >
                  {savingTeacher && <Loader2 className="h-3.5 w-3.5 animate-spin" />}
                  <span>Provision Teacher</span>
                </button>
              </div>
            </form>
          </Dialog.Content>
        </Dialog.Portal>
      </Dialog.Root>

      {/* Edit User / Roles Modal */}
      <Dialog.Root open={Boolean(editingUser)} onOpenChange={(open) => !open && setEditingUser(null)}>
        <Dialog.Portal>
          <Dialog.Overlay className="kuet-dialog-overlay fixed inset-0 z-50 bg-slate-950/75 backdrop-blur-sm" />
          <Dialog.Content className="kuet-dialog-content fixed left-1/2 top-1/2 z-50 w-[calc(100%-2rem)] max-w-md -translate-x-1/2 -translate-y-1/2 rounded-2xl border border-slate-800 bg-slate-900 p-6 shadow-2xl">
            <div className="flex items-start justify-between">
              <Dialog.Title className="text-lg font-semibold text-white">
                Edit User &amp; Roles
              </Dialog.Title>
              <Dialog.Close aria-label="Close modal" className="rounded-lg p-1 text-slate-400 hover:text-white">
                <X className="h-4 w-4" />
              </Dialog.Close>
            </div>
            <Dialog.Description className="mt-1 text-xs text-slate-400">
              {editingUser?.email}
            </Dialog.Description>
            <form onSubmit={handleSaveUser} className="mt-4 space-y-3.5">
              <div>
                <label htmlFor="admin-u-name" className="block text-xs font-medium text-slate-300">
                  Display Name
                </label>
                <input
                  id="admin-u-name"
                  value={uName}
                  onChange={(e) => setUName(e.target.value)}
                  className="mt-1 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-slate-100"
                  required
                />
              </div>
              <div className="grid grid-cols-3 gap-2">
                <div>
                  <label htmlFor="admin-u-dept" className="block text-xs font-medium text-slate-300">
                    Dept
                  </label>
                  <select
                    id="admin-u-dept"
                    value={uDept}
                    onChange={(e) => setUDept(e.target.value)}
                    className="mt-1 w-full rounded-lg border border-slate-700 bg-slate-950 px-2 py-2 text-sm text-slate-100"
                  >
                    {KUET_DEPARTMENTS.map((d) => (
                      <option key={d} value={d}>
                        {d}
                      </option>
                    ))}
                  </select>
                </div>
                <div>
                  <label htmlFor="admin-u-yt" className="block text-xs font-medium text-slate-300">
                    Year–Term
                  </label>
                  <select
                    id="admin-u-yt"
                    value={uYearTerm}
                    onChange={(e) => setUYearTerm(e.target.value)}
                    className="mt-1 w-full rounded-lg border border-slate-700 bg-slate-950 px-2 py-2 text-sm text-slate-100"
                  >
                    <option value="">None</option>
                    {YEAR_TERM_OPTIONS.map((yt) => (
                      <option key={yt.code} value={yt.code}>
                        {yt.code}
                      </option>
                    ))}
                  </select>
                </div>
                <div>
                  <label htmlFor="admin-u-status" className="block text-xs font-medium text-slate-300">
                    Status
                  </label>
                  <select
                    id="admin-u-status"
                    value={uStatus}
                    onChange={(e) => setUStatus(e.target.value as UserStatus)}
                    className="mt-1 w-full rounded-lg border border-slate-700 bg-slate-950 px-2 py-2 text-sm text-slate-100"
                  >
                    <option value="ACTIVE">ACTIVE</option>
                    <option value="SUSPENDED">SUSPENDED</option>
                  </select>
                </div>
              </div>

              <div>
                <span className="block text-xs font-medium text-slate-300">Assigned Roles</span>
                <div className="mt-1.5 flex flex-wrap gap-1.5">
                  {ALL_ROLE_OPTIONS.filter((opt) => isAdmin || opt.role !== 'ROLE_ADMIN').map(
                    (opt) => {
                      const active = uRoles.includes(opt.role);
                      return (
                        <button
                          key={opt.role}
                          type="button"
                          onClick={() => toggleRoleInSelection(opt.role)}
                          className={cn(
                            'rounded-lg border px-2.5 py-1 text-xs font-medium',
                            active
                              ? 'border-emerald-500/50 bg-emerald-500/20 text-emerald-200'
                              : 'border-slate-700 bg-slate-950 text-slate-400',
                          )}
                        >
                          {opt.label}
                        </button>
                      );
                    },
                  )}
                </div>
              </div>

              <div>
                <label htmlFor="admin-u-reset" className="block text-xs font-medium text-slate-300">
                  Reset Password (Optional, min 8 chars)
                </label>
                <input
                  id="admin-u-reset"
                  type="text"
                  minLength={8}
                  value={uResetPassword}
                  onChange={(e) => setUResetPassword(e.target.value)}
                  placeholder="Leave blank to keep existing password"
                  className="mt-1 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 font-mono text-xs text-slate-100"
                />
              </div>

              <div className="flex justify-end gap-2 pt-2">
                <Dialog.Close asChild>
                  <button
                    type="button"
                    className="rounded-lg border border-slate-700 px-3 py-2 text-xs text-slate-300 hover:bg-slate-800"
                  >
                    Cancel
                  </button>
                </Dialog.Close>
                <button
                  type="submit"
                  disabled={savingUser}
                  className="inline-flex items-center gap-1.5 rounded-lg bg-emerald-600 px-4 py-2 text-xs font-medium text-white hover:bg-emerald-500 active:scale-[0.97]"
                >
                  {savingUser && <Loader2 className="h-3.5 w-3.5 animate-spin" />}
                  <span>Save Changes</span>
                </button>
              </div>
            </form>
          </Dialog.Content>
        </Dialog.Portal>
      </Dialog.Root>
    </div>
  );
}
