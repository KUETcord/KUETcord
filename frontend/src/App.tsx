import { Toaster, toast } from 'sonner';
import { Calendar, MessageSquare, Briefcase, GraduationCap, ShieldCheck } from 'lucide-react';

export default function App() {
  const handleTestToast = () => {
    toast.success('KUETcord platform initialized successfully!');
  };

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col">
      <Toaster position="top-right" richColors />
      
      {/* Top Header */}
      <header className="h-16 border-b border-slate-800 px-6 flex items-center justify-between bg-slate-900/50 backdrop-blur sticky top-0 z-50">
        <div className="flex items-center gap-3">
          <div className="h-9 w-9 rounded-lg bg-emerald-600 flex items-center justify-center font-bold text-white shadow-lg shadow-emerald-900/40">
            KC
          </div>
          <div>
            <h1 className="font-bold text-lg tracking-tight leading-tight">KUETcord</h1>
            <p className="text-xs text-slate-400">Digital Campus Platform</p>
          </div>
        </div>

        <div className="flex items-center gap-4">
          <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
            <span className="h-1.5 w-1.5 rounded-full bg-emerald-400 animate-pulse" />
            Backend Connected
          </span>
          <button
            onClick={handleTestToast}
            className="px-3.5 py-1.5 bg-emerald-600 hover:bg-emerald-500 text-white rounded-lg text-sm font-medium transition shadow-md shadow-emerald-900/30"
          >
            System Check
          </button>
        </div>
      </header>

      {/* Main Grid Hero */}
      <main className="flex-1 max-w-6xl w-full mx-auto px-6 py-12">
        <div className="text-center max-w-2xl mx-auto mb-12">
          <h2 className="text-3xl font-extrabold tracking-tight sm:text-4xl mb-4 bg-gradient-to-r from-white via-slate-200 to-slate-400 bg-clip-text text-transparent">
            Learn Together. Build Things. Graduate Stronger.
          </h2>
          <p className="text-slate-400 text-sm sm:text-base leading-relaxed">
            The dedicated academic collaboration and productivity ecosystem designed around the real workflows of KUET students.
          </p>
        </div>

        {/* Feature Cards Grid */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
          <div className="p-6 rounded-xl border border-slate-800 bg-slate-900/40 hover:border-slate-700 transition">
            <div className="h-10 w-10 rounded-lg bg-blue-500/10 text-blue-400 flex items-center justify-center mb-4">
              <Calendar className="h-5 w-5" />
            </div>
            <h3 className="font-semibold text-base mb-1 tracking-tight">CT Hub</h3>
            <p className="text-slate-400 text-xs leading-relaxed">
              Course-wise Class Test schedules, verified CR announcements, syllabus tracking, and conflict detection.
            </p>
          </div>

          <div className="p-6 rounded-xl border border-slate-800 bg-slate-900/40 hover:border-slate-700 transition">
            <div className="h-10 w-10 rounded-lg bg-emerald-500/10 text-emerald-400 flex items-center justify-center mb-4">
              <MessageSquare className="h-5 w-5" />
            </div>
            <h3 className="font-semibold text-base mb-1 tracking-tight">Core Messaging</h3>
            <p className="text-slate-400 text-xs leading-relaxed">
              Real-time channel chat, group DMs, STOMP heartbeats, and Discord-style server roles.
            </p>
          </div>

          <div className="p-6 rounded-xl border border-slate-800 bg-slate-900/40 hover:border-slate-700 transition">
            <div className="h-10 w-10 rounded-lg bg-purple-500/10 text-purple-400 flex items-center justify-center mb-4">
              <Briefcase className="h-5 w-5" />
            </div>
            <h3 className="font-semibold text-base mb-1 tracking-tight">Project Playgrounds</h3>
            <p className="text-slate-400 text-xs leading-relaxed">
              Collaborative workspaces, hackathon team formation, and interactive drag-and-drop Kanban boards.
            </p>
          </div>

          <div className="p-6 rounded-xl border border-slate-800 bg-slate-900/40 hover:border-slate-700 transition">
            <div className="h-10 w-10 rounded-lg bg-amber-500/10 text-amber-400 flex items-center justify-center mb-4">
              <GraduationCap className="h-5 w-5" />
            </div>
            <h3 className="font-semibold text-base mb-1 tracking-tight">Study Circles</h3>
            <p className="text-slate-400 text-xs leading-relaxed">
              Exam-focused peer study circles, Pomodoro sessions, and structured revision checklists.
            </p>
          </div>
        </div>

        {/* Security Gate Banner */}
        <div className="mt-12 p-6 rounded-2xl border border-emerald-900/40 bg-emerald-950/20 flex flex-col sm:flex-row items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <ShieldCheck className="h-8 w-8 text-emerald-400 flex-shrink-0" />
            <div>
              <h4 className="font-semibold text-sm text-emerald-200">Institutional Identity Protection</h4>
              <p className="text-xs text-slate-400">Strictly restricted to verified <span className="text-emerald-400 font-mono">@stud.kuet.ac.bd</span> institutional domains.</p>
            </div>
          </div>
          <div className="text-xs text-slate-400 font-mono tabular-nums">
            Sprint 1 • CSE 3220
          </div>
        </div>
      </main>

      <footer className="border-t border-slate-800 py-6 text-center text-xs text-slate-500">
        KUETcord © 2026 • Khulna University of Engineering & Technology
      </footer>
    </div>
  );
}
