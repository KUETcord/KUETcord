import { useState } from 'react';
import { NavLink, Outlet, useLocation } from 'react-router-dom';
import {
  Briefcase,
  Calendar,
  GraduationCap,
  LayoutDashboard,
  LogIn,
  LogOut,
  Menu,
  MessageSquare,
  ShieldCheck,
  Trophy,
  X,
} from 'lucide-react';
import { useAuth } from '../../context/AuthContext';
import { cn, getInitials } from '../../lib/format';

interface NavItem {
  to: string;
  label: string;
  icon: typeof Calendar;
  badge?: string;
}

const NAV_ITEMS: NavItem[] = [
  { to: '/', label: 'Overview', icon: LayoutDashboard },
  { to: '/ct-hub', label: 'CT Hub', icon: Calendar },
  { to: '/messaging', label: 'Core Messaging', icon: MessageSquare },
  { to: '/opportunities', label: 'Contest Board', icon: Trophy },
  { to: '/playgrounds', label: 'Project Playgrounds', icon: Briefcase },
  { to: '/study-circles', label: 'Study Circles', icon: GraduationCap },
];

const ROUTE_TITLES: Record<string, string> = {
  '/': 'Overview',
  '/ct-hub': 'CT Hub',
  '/messaging': 'Core Messaging',
  '/opportunities': 'Contest & Challenge Board',
  '/playgrounds': 'Project Playgrounds',
  '/study-circles': 'Study Circles',
  '/auth': 'Institutional Authentication',
};

export function AppShell() {
  const { user, isDemoMode, enterDemoMode, logout } = useAuth();
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const location = useLocation();
  const currentTitle = ROUTE_TITLES[location.pathname] ?? 'KUETcord';

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex">
      {/* Desktop & Mobile Navigation Sidebar */}
      <aside
        className={cn(
          'fixed inset-y-0 left-0 z-40 flex w-64 flex-col border-r border-slate-800 bg-slate-900/95 backdrop-blur lg:static lg:translate-x-0',
          sidebarOpen ? 'translate-x-0' : '-translate-x-full',
          'transition-transform duration-150 ease-out',
        )}
      >
        <div className="flex h-16 items-center justify-between border-b border-slate-800 px-5">
          <div className="flex min-w-0 items-center gap-3">
            <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-emerald-600 font-bold text-white shadow-lg shadow-emerald-900/40">
              KC
            </div>
            <div className="min-w-0">
              <h1 className="truncate text-base font-bold tracking-tight text-white">KUETcord</h1>
              <p className="truncate text-[11px] text-slate-400">Digital Campus</p>
            </div>
          </div>
          <button
            type="button"
            aria-label="Close sidebar"
            onClick={() => setSidebarOpen(false)}
            className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-800 hover:text-white lg:hidden shrink-0"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        <nav className="flex-1 space-y-1 overflow-y-auto px-3 py-4" aria-label="Main navigation">
          {NAV_ITEMS.map((item) => {
            const Icon = item.icon;
            return (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.to === '/'}
                onClick={() => setSidebarOpen(false)}
                className={({ isActive }) =>
                  cn(
                    'flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors duration-150 ease-out',
                    isActive
                      ? 'bg-emerald-500/15 text-emerald-300 border border-emerald-500/20'
                      : 'text-slate-300 hover:bg-slate-800/70 hover:text-white border border-transparent',
                  )
                }
              >
                <Icon className="h-4 w-4 shrink-0" aria-hidden="true" />
                <span className="truncate">{item.label}</span>
              </NavLink>
            );
          })}
        </nav>

        {/* User Footer Card */}
        <div className="border-t border-slate-800 p-3">
          {user ? (
            <div className="space-y-2.5">
              <div className="flex items-center gap-2.5 rounded-lg bg-slate-950/60 p-2.5">
                <div
                  className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-emerald-600/25 text-xs font-semibold text-emerald-300 border border-emerald-500/30"
                  aria-hidden="true"
                >
                  {getInitials(user.displayName)}
                </div>
                <div className="min-w-0 flex-1">
                  <p className="truncate text-xs font-semibold text-slate-100" title={user.displayName}>
                    {user.displayName}
                  </p>
                  <p
                    className="truncate text-[11px] text-slate-400 [overflow-wrap:anywhere]"
                    title={user.email}
                  >
                    {user.email}
                  </p>
                </div>
                <span className="rounded bg-slate-800 px-1.5 py-0.5 font-mono text-[10px] font-semibold text-emerald-300 shrink-0">
                  {user.globalRole}
                </span>
              </div>

              {isDemoMode && (
                <div className="grid grid-cols-3 gap-1">
                  {(['TEACHER', 'CR', 'STUDENT'] as const).map((role) => (
                    <button
                      key={role}
                      type="button"
                      onClick={() => enterDemoMode(role)}
                      className={cn(
                        'rounded-md px-1.5 py-1 text-[11px] font-medium transition-colors duration-150 ease-out',
                        user.globalRole === role
                          ? 'bg-emerald-600 text-white'
                          : 'bg-slate-800 text-slate-300 hover:bg-slate-700',
                      )}
                    >
                      {role === 'TEACHER' ? 'Teacher' : role === 'CR' ? 'CR' : 'Student'}
                    </button>
                  ))}
                </div>
              )}

              <div className="flex items-center justify-between gap-2">
                <NavLink
                  to="/auth"
                  className="inline-flex items-center gap-1.5 text-xs text-slate-400 hover:text-slate-200"
                >
                  <ShieldCheck className="h-3.5 w-3.5 shrink-0" />
                  <span>Auth Portal</span>
                </NavLink>
                <button
                  type="button"
                  onClick={logout}
                  className="inline-flex items-center gap-1 text-xs text-slate-400 hover:text-rose-300"
                >
                  <LogOut className="h-3.5 w-3.5 shrink-0" />
                  <span>Sign out</span>
                </button>
              </div>
            </div>
          ) : (
            <NavLink
              to="/auth"
              className="flex w-full items-center justify-center gap-2 rounded-lg bg-emerald-600 px-3 py-2 text-xs font-medium text-white hover:bg-emerald-500"
            >
              <LogIn className="h-3.5 w-3.5 shrink-0" />
              <span>Sign in with KUET Email</span>
            </NavLink>
          )}
        </div>
      </aside>

      {/* Mobile backdrop */}
      {sidebarOpen && (
        <div
          className="fixed inset-0 z-30 bg-slate-950/60 lg:hidden"
          onClick={() => setSidebarOpen(false)}
          aria-hidden="true"
        />
      )}

      {/* Main Content Area */}
      <div className="flex min-w-0 flex-1 flex-col">
        <header className="sticky top-0 z-20 flex h-16 items-center justify-between gap-4 border-b border-slate-800 bg-slate-900/60 px-4 backdrop-blur sm:px-6">
          <div className="flex min-w-0 items-center gap-3">
            <button
              type="button"
              aria-label="Open navigation menu"
              onClick={() => setSidebarOpen(true)}
              className="rounded-lg p-2 text-slate-300 hover:bg-slate-800 hover:text-white lg:hidden shrink-0"
            >
              <Menu className="h-5 w-5" />
            </button>
            <div className="flex min-w-0 items-center gap-2 text-sm">
              <span className="hidden text-slate-500 sm:inline">KUETcord</span>
              <span className="hidden text-slate-600 sm:inline">/</span>
              <span className="truncate font-semibold text-slate-100">{currentTitle}</span>
            </div>
          </div>

          <div className="flex items-center gap-3 shrink-0">
            <span className="inline-flex items-center gap-1.5 rounded-full border border-emerald-500/20 bg-emerald-500/10 px-2.5 py-1 text-xs font-medium text-emerald-400">
              <span className="h-1.5 w-1.5 rounded-full bg-emerald-400 shrink-0" />
              <span>{isDemoMode ? 'Interactive Preview' : 'API Connected'}</span>
            </span>
          </div>
        </header>

        <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-8 sm:px-6">
          <Outlet />
        </main>

        <footer className="border-t border-slate-800/80 py-5 text-center text-xs text-slate-500">
          KUETcord © 2026 • Khulna University of Engineering &amp; Technology
        </footer>
      </div>
    </div>
  );
}
