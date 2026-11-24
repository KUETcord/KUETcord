import { Link } from 'react-router-dom';
import {
  ArrowRight,
  Briefcase,
  Calendar,
  GraduationCap,
  MessageSquare,
  ShieldCheck,
} from 'lucide-react';
import { toast } from 'sonner';

export function OverviewPage() {
  return (
    <div className="space-y-10">
      <div className="mx-auto max-w-2xl text-center">
        <h2 className="bg-gradient-to-r from-white via-slate-200 to-slate-400 bg-clip-text text-3xl font-extrabold tracking-tight text-transparent sm:text-4xl">
          Learn Together. Build Things. Graduate Stronger.
        </h2>
        <p className="mx-auto mt-4 max-w-[65ch] text-sm leading-relaxed text-slate-400 sm:text-base">
          The dedicated academic collaboration and productivity ecosystem designed around the real workflows of KUET students.
        </p>
        <div className="mt-6 flex flex-wrap items-center justify-center gap-3">
          <Link
            to="/ct-hub"
            className="inline-flex items-center gap-2 rounded-xl bg-emerald-600 px-5 py-2.5 text-sm font-medium text-white shadow-lg shadow-emerald-950/40 transition-colors duration-150 ease-out hover:bg-emerald-500"
          >
            <span>Open CT Hub</span>
            <ArrowRight className="h-4 w-4 shrink-0" />
          </Link>
          <button
            type="button"
            onClick={() => toast.success('KUETcord platform initialized successfully!')}
            className="rounded-xl border border-slate-700 bg-slate-900 px-4 py-2.5 text-sm font-medium text-slate-200 transition-colors duration-150 ease-out hover:bg-slate-800"
          >
            System Check
          </button>
        </div>
      </div>

      {/* 4 Core Module Cards */}
      <div className="grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-4">
        <Link
          to="/ct-hub"
          className="group rounded-xl border border-slate-800 bg-slate-900/40 p-6 transition-colors duration-150 ease-out hover:border-blue-500/40"
        >
          <div className="mb-4 flex h-10 w-10 items-center justify-center rounded-lg bg-blue-500/10 text-blue-400">
            <Calendar className="h-5 w-5" />
          </div>
          <h3 className="mb-1 text-base font-semibold tracking-tight text-white group-hover:text-blue-300">
            CT Hub
          </h3>
          <p className="text-xs leading-relaxed text-slate-400">
            Course-wise Class Test schedules, verified CR announcements, syllabus tracking, and conflict detection.
          </p>
        </Link>

        <Link
          to="/messaging"
          className="group rounded-xl border border-slate-800 bg-slate-900/40 p-6 transition-colors duration-150 ease-out hover:border-emerald-500/40"
        >
          <div className="mb-4 flex h-10 w-10 items-center justify-center rounded-lg bg-emerald-500/10 text-emerald-400">
            <MessageSquare className="h-5 w-5" />
          </div>
          <h3 className="mb-1 text-base font-semibold tracking-tight text-white group-hover:text-emerald-300">
            Core Messaging
          </h3>
          <p className="text-xs leading-relaxed text-slate-400">
            Real-time channel chat, group DMs, STOMP heartbeats, and Discord-style server roles.
          </p>
        </Link>

        <Link
          to="/playgrounds"
          className="group rounded-xl border border-slate-800 bg-slate-900/40 p-6 transition-colors duration-150 ease-out hover:border-purple-500/40"
        >
          <div className="mb-4 flex h-10 w-10 items-center justify-center rounded-lg bg-purple-500/10 text-purple-400">
            <Briefcase className="h-5 w-5" />
          </div>
          <h3 className="mb-1 text-base font-semibold tracking-tight text-white group-hover:text-purple-300">
            Project Playgrounds
          </h3>
          <p className="text-xs leading-relaxed text-slate-400">
            Collaborative workspaces, hackathon team formation, and interactive drag-and-drop Kanban boards.
          </p>
        </Link>

        <Link
          to="/study-circles"
          className="group rounded-xl border border-slate-800 bg-slate-900/40 p-6 transition-colors duration-150 ease-out hover:border-amber-500/40"
        >
          <div className="mb-4 flex h-10 w-10 items-center justify-center rounded-lg bg-amber-500/10 text-amber-400">
            <GraduationCap className="h-5 w-5" />
          </div>
          <h3 className="mb-1 text-base font-semibold tracking-tight text-white group-hover:text-amber-300">
            Study Circles
          </h3>
          <p className="text-xs leading-relaxed text-slate-400">
            Exam-focused peer study circles, Pomodoro sessions, and structured revision checklists.
          </p>
        </Link>
      </div>

      {/* Institutional Security Banner */}
      <div className="flex flex-col items-center justify-between gap-4 rounded-2xl border border-emerald-900/40 bg-emerald-950/20 p-6 sm:flex-row">
        <div className="flex items-center gap-3">
          <ShieldCheck className="h-8 w-8 text-emerald-400 shrink-0" />
          <div className="min-w-0">
            <h4 className="text-sm font-semibold text-emerald-200">Institutional Identity Protection</h4>
            <p className="text-xs text-slate-400">
              Strictly restricted to verified{' '}
              <span className="font-mono text-emerald-400">@stud.kuet.ac.bd</span> institutional domains.
            </p>
          </div>
        </div>
        <div className="font-mono text-xs text-slate-400 tabular-nums shrink-0">
          Phase 4 • CSE 3220
        </div>
      </div>
    </div>
  );
}
