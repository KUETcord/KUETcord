import React, { useEffect, useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import {
  CheckCircle2,
  Eye,
  EyeOff,
  GraduationCap,
  KeyRound,
  Loader2,
  Mail,
  ShieldCheck,
  UserCheck,
} from 'lucide-react';
import { toast } from 'sonner';
import { extractErrorMessage, useAuth } from '../context/AuthContext';
import { ApiError } from '../lib/api';
import { cn } from '../lib/format';
import { KUET_DEPARTMENTS, YEAR_TERM_OPTIONS } from '../lib/yearTerm';

type AuthTab = 'login' | 'register' | 'verify';

const KUET_EMAIL_REGEX = /^[a-zA-Z0-9._%+-]+@([a-zA-Z0-9-]+\.)?kuet\.ac\.bd$/i;

function detectEmailKind(email: string): 'student' | 'faculty' | null {
  const trimmed = email.trim().toLowerCase();
  if (!trimmed.includes('@') || !KUET_EMAIL_REGEX.test(trimmed)) return null;
  return trimmed.endsWith('@stud.kuet.ac.bd') ? 'student' : 'faculty';
}

export function AuthPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { login, register, verifyEmail, resendVerification, enterDemoMode } = useAuth();

  const initialToken = searchParams.get('token') ?? '';
  const [activeTab, setActiveTab] = useState<AuthTab>(initialToken ? 'verify' : 'login');

  // Login state
  const [loginEmail, setLoginEmail] = useState('');
  const [loginPassword, setLoginPassword] = useState('');
  const [showLoginSecret, setShowLoginSecret] = useState(false);
  const [needsVerificationEmail, setNeedsVerificationEmail] = useState<string | null>(null);

  // Register state
  const [regEmail, setRegEmail] = useState('');
  const [regPassword, setRegPassword] = useState('');
  const [showRegSecret, setShowRegSecret] = useState(false);
  const [regDisplayName, setRegDisplayName] = useState('');
  const [regDepartment, setRegDepartment] = useState('CSE');
  const [regYearTerm, setRegYearTerm] = useState('3-2');
  const [regBatch, setRegBatch] = useState('2k20');
  const [regRollNumber, setRegRollNumber] = useState('');
  const [regBio, setRegBio] = useState('');

  // Verify state
  const [verifyToken, setVerifyToken] = useState(initialToken);
  const [verifiedDone, setVerifiedDone] = useState(false);

  const [submitting, setSubmitting] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  useEffect(() => {
    if (initialToken) {
      setVerifyToken(initialToken);
      setActiveTab('verify');
    }
  }, [initialToken]);

  const detectedRegRole = useMemo(() => detectEmailKind(regEmail), [regEmail]);

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMsg(null);
    setNeedsVerificationEmail(null);
    setSubmitting(true);
    try {
      const signedIn = await login(loginEmail.trim(), loginPassword);
      if (signedIn.globalRole === 'ADMIN' || signedIn.globalRole === 'MODERATOR') {
        navigate('/admin');
      } else {
        navigate('/ct-hub');
      }
    } catch (err) {
      if (err instanceof ApiError && err.status === 403) {
        setNeedsVerificationEmail(loginEmail.trim());
      }
      const message = extractErrorMessage(err, 'Unable to sign in');
      setErrorMsg(message);
      toast.error(message);
    } finally {
      setSubmitting(false);
    }
  };

  const handleRegister = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMsg(null);

    if (!KUET_EMAIL_REGEX.test(regEmail.trim())) {
      const msg = 'Email must belong to KUET (@stud.kuet.ac.bd, @kuet.ac.bd, or @<dept>.kuet.ac.bd)';
      setErrorMsg(msg);
      toast.error(msg);
      return;
    }

    const isFaculty = detectedRegRole === 'faculty';
    setSubmitting(true);
    try {
      await register({
        email: regEmail.trim(),
        password: regPassword,
        displayName: regDisplayName.trim(),
        department: regDepartment.trim() || undefined,
        batch: isFaculty ? 'Faculty' : regBatch.trim() || undefined,
        yearTerm: isFaculty ? undefined : regYearTerm || undefined,
        rollNumber: isFaculty ? undefined : regRollNumber.trim() || undefined,
        bio: regBio.trim() || undefined,
      });
      setActiveTab('verify');
    } catch (err) {
      const message = extractErrorMessage(err, 'Registration failed');
      setErrorMsg(message);
      toast.error(message);
    } finally {
      setSubmitting(false);
    }
  };

  const handleVerify = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMsg(null);
    setSubmitting(true);
    try {
      await verifyEmail(verifyToken.trim());
      setVerifiedDone(true);
    } catch (err) {
      const message = extractErrorMessage(err, 'Invalid or expired verification token');
      setErrorMsg(message);
      toast.error(message);
    } finally {
      setSubmitting(false);
    }
  };

  const handleResend = async () => {
    if (!needsVerificationEmail) return;
    setSubmitting(true);
    try {
      await resendVerification(needsVerificationEmail);
    } catch (err) {
      toast.error(extractErrorMessage(err, 'Unable to resend verification email'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="mx-auto max-w-lg py-6">
      <div className="mb-6 text-center">
        <div className="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-2xl bg-emerald-500/15 text-emerald-400 border border-emerald-500/30">
          <ShieldCheck className="h-6 w-6" aria-hidden="true" />
        </div>
        <h2 className="text-2xl font-bold tracking-tight text-white">KUETcord Campus Portal</h2>
        <p className="mt-1.5 text-xs leading-relaxed text-slate-400">
          Unified sign-in for KUET{' '}
          <span className="font-medium text-emerald-300">Students &amp; CRs</span>{' '}
          (<span className="font-mono">@stud.kuet.ac.bd</span>),{' '}
          <span className="font-medium text-sky-300">Teachers</span>{' '}
          (<span className="font-mono">@&lt;dept&gt;.kuet.ac.bd</span>), and{' '}
          <span className="font-medium text-amber-300">Moderators &amp; Admins</span>{' '}
          (<span className="font-mono">@kuet.ac.bd</span>).
        </p>

        {/* Friendly Role Guide Pills */}
        <div className="mt-3 flex flex-wrap items-center justify-center gap-1.5 text-[11px]">
          <span className="inline-flex items-center gap-1 rounded-full border border-emerald-500/25 bg-emerald-500/10 px-2.5 py-0.5 text-emerald-300">
            <UserCheck className="h-3 w-3 shrink-0" aria-hidden="true" /> Student / CR
          </span>
          <span className="inline-flex items-center gap-1 rounded-full border border-sky-500/25 bg-sky-500/10 px-2.5 py-0.5 text-sky-300">
            <GraduationCap className="h-3 w-3 shrink-0" aria-hidden="true" /> Course Teacher
          </span>
          <span className="inline-flex items-center gap-1 rounded-full border border-amber-500/25 bg-amber-500/10 px-2.5 py-0.5 text-amber-300">
            <ShieldCheck className="h-3 w-3 shrink-0" aria-hidden="true" /> Mod / Admin
          </span>
        </div>
      </div>

      <div className="rounded-2xl border border-slate-800 bg-slate-900/70 p-6 shadow-xl">
        {/* Segmented Control */}
        <div className="mb-6 grid grid-cols-3 rounded-xl bg-slate-950 p-1">
          {(['login', 'register', 'verify'] as AuthTab[]).map((tab) => (
            <button
              key={tab}
              type="button"
              onClick={() => {
                setErrorMsg(null);
                setActiveTab(tab);
              }}
              className={cn(
                'rounded-lg py-2 text-xs font-medium transition-colors duration-150 ease-out active:scale-[0.97]',
                activeTab === tab
                  ? 'bg-slate-800 text-white shadow-sm'
                  : 'text-slate-400 hover:text-slate-200',
              )}
            >
              {tab === 'login' ? 'Sign In' : tab === 'register' ? 'Register' : 'Verify Email'}
            </button>
          ))}
        </div>

        {errorMsg && (
          <div
            role="alert"
            className="mb-4 rounded-xl border border-rose-500/30 bg-rose-500/10 px-3.5 py-2.5 text-xs text-rose-200"
          >
            {errorMsg}
          </div>
        )}

        {activeTab === 'login' && (
          <form onSubmit={handleLogin} className="space-y-4">
            <div>
              <label htmlFor="login-email" className="block text-xs font-medium text-slate-300">
                KUET Email (Student, Teacher, Mod, or Admin)
              </label>
              <div className="relative mt-1.5">
                <Mail className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-500" />
                <input
                  id="login-email"
                  type="email"
                  autoComplete="email"
                  value={loginEmail}
                  onChange={(e) => setLoginEmail(e.target.value)}
                  placeholder="roll@stud.kuet.ac.bd · name@cse.kuet.ac.bd · admin@kuet.ac.bd"
                  className="w-full rounded-lg border border-slate-700 bg-slate-950 py-2.5 pl-9 pr-3 text-sm text-slate-100 placeholder:text-slate-500 focus:border-emerald-500 focus:outline-none"
                  required
                />
              </div>
            </div>

            <div>
              <label htmlFor="login-password" className="block text-xs font-medium text-slate-300">
                Password
              </label>
              <div className="relative mt-1.5">
                <KeyRound className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-500" />
                <input
                  id="login-password"
                  type={showLoginSecret ? 'text' : 'password'}
                  autoComplete="current-password"
                  value={loginPassword}
                  onChange={(e) => setLoginPassword(e.target.value)}
                  placeholder="••••••••••••"
                  className="w-full rounded-lg border border-slate-700 bg-slate-950 py-2.5 pl-9 pr-10 text-sm text-slate-100 placeholder:text-slate-500 focus:border-emerald-500 focus:outline-none"
                  required
                />
                <button
                  type="button"
                  aria-label={showLoginSecret ? 'Hide secret characters' : 'Reveal secret characters'}
                  onClick={() => setShowLoginSecret((v) => !v)}
                  className="absolute right-2.5 top-1/2 -translate-y-1/2 rounded p-1 text-slate-400 hover:text-slate-200"
                >
                  {showLoginSecret ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              </div>
            </div>

            {needsVerificationEmail && (
              <div className="rounded-xl border border-amber-500/30 bg-amber-500/10 p-3 text-xs text-amber-200">
                <p>Your KUET email has not been verified yet.</p>
                <button
                  type="button"
                  onClick={handleResend}
                  className="mt-2 font-semibold text-amber-300 underline hover:text-amber-100"
                >
                  Resend verification token
                </button>
              </div>
            )}

            <button
              type="submit"
              disabled={submitting}
              className="flex w-full items-center justify-center gap-2 rounded-xl bg-emerald-600 py-2.5 text-sm font-medium text-white transition-[background-color,transform] duration-100 ease-out hover:bg-emerald-500 active:scale-[0.97] disabled:cursor-not-allowed disabled:opacity-50"
            >
              {submitting && <Loader2 className="h-4 w-4 animate-spin shrink-0" />}
              <span>Sign In to KUETcord</span>
            </button>
          </form>
        )}

        {activeTab === 'register' && (
          <form onSubmit={handleRegister} className="space-y-4">
            <div>
              <div className="flex items-center justify-between gap-2">
                <label htmlFor="reg-email" className="block text-xs font-medium text-slate-300">
                  Institutional Email (Student or Faculty)
                </label>
                {detectedRegRole && (
                  <span
                    className={cn(
                      'rounded-full border px-2 py-0.5 text-[10px] font-semibold',
                      detectedRegRole === 'faculty'
                        ? 'border-sky-500/30 bg-sky-500/10 text-sky-300'
                        : 'border-emerald-500/30 bg-emerald-500/10 text-emerald-300',
                    )}
                  >
                    {detectedRegRole === 'faculty' ? 'Teacher / Staff Account' : 'Student Account'}
                  </span>
                )}
              </div>
              <input
                id="reg-email"
                type="email"
                value={regEmail}
                onChange={(e) => setRegEmail(e.target.value)}
                placeholder="2207001@stud.kuet.ac.bd or teacher@cse.kuet.ac.bd"
                className="mt-1.5 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2.5 text-sm text-slate-100 placeholder:text-slate-500 focus:border-emerald-500 focus:outline-none"
                required
              />
            </div>

            <div>
              <label htmlFor="reg-name" className="block text-xs font-medium text-slate-300">
                Full Name
              </label>
              <input
                id="reg-name"
                type="text"
                minLength={2}
                maxLength={100}
                value={regDisplayName}
                onChange={(e) => setRegDisplayName(e.target.value)}
                placeholder={
                  detectedRegRole === 'faculty' ? 'Prof. Dr. Hasan Mahmud' : 'Rahim Uddin'
                }
                className="mt-1.5 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2.5 text-sm text-slate-100 placeholder:text-slate-500 focus:border-emerald-500 focus:outline-none"
                required
              />
            </div>

            {detectedRegRole === 'faculty' ? (
              <div className="space-y-3">
                <div>
                  <label htmlFor="reg-dept" className="block text-xs font-medium text-slate-300">
                    Department
                  </label>
                  <select
                    id="reg-dept"
                    value={regDepartment}
                    onChange={(e) => setRegDepartment(e.target.value)}
                    className="mt-1.5 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2.5 text-sm text-slate-100 focus:border-emerald-500 focus:outline-none"
                  >
                    {KUET_DEPARTMENTS.map((dept) => (
                      <option key={dept} value={dept}>
                        {dept}
                      </option>
                    ))}
                  </select>
                </div>
                <div>
                  <label htmlFor="reg-bio" className="block text-xs font-medium text-slate-300">
                    Designation / Research Area (Optional)
                  </label>
                  <input
                    id="reg-bio"
                    type="text"
                    maxLength={280}
                    value={regBio}
                    onChange={(e) => setRegBio(e.target.value)}
                    placeholder="Associate Professor, Dept. of CSE"
                    className="mt-1.5 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2.5 text-sm text-slate-100 placeholder:text-slate-500 focus:border-emerald-500 focus:outline-none"
                  />
                </div>
                <p className="rounded-lg border border-sky-500/20 bg-sky-500/5 px-3 py-2 text-[11px] text-sky-200/90">
                  Faculty emails automatically receive <span className="font-mono">ROLE_TEACHER</span>. Platform Admins &amp; Moderators can also provision pre-verified Teacher accounts directly.
                </p>
              </div>
            ) : (
              <>
                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label htmlFor="reg-dept" className="block text-xs font-medium text-slate-300">
                      Department
                    </label>
                    <select
                      id="reg-dept"
                      value={regDepartment}
                      onChange={(e) => setRegDepartment(e.target.value)}
                      className="mt-1.5 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-slate-100 focus:border-emerald-500 focus:outline-none"
                    >
                      {KUET_DEPARTMENTS.map((dept) => (
                        <option key={dept} value={dept}>
                          {dept}
                        </option>
                      ))}
                    </select>
                  </div>
                  <div>
                    <label htmlFor="reg-year-term" className="block text-xs font-medium text-slate-300">
                      Year &amp; Term
                    </label>
                    <select
                      id="reg-year-term"
                      value={regYearTerm}
                      onChange={(e) => setRegYearTerm(e.target.value)}
                      className="mt-1.5 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-slate-100 focus:border-emerald-500 focus:outline-none"
                    >
                      {YEAR_TERM_OPTIONS.map((yt) => (
                        <option key={yt.code} value={yt.code}>
                          {yt.label}
                        </option>
                      ))}
                    </select>
                  </div>
                </div>

                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label htmlFor="reg-batch" className="block text-xs font-medium text-slate-300">
                      Batch
                    </label>
                    <input
                      id="reg-batch"
                      type="text"
                      maxLength={20}
                      value={regBatch}
                      onChange={(e) => setRegBatch(e.target.value)}
                      placeholder="2k21"
                      className="mt-1.5 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-slate-100 focus:border-emerald-500 focus:outline-none"
                    />
                  </div>
                  <div>
                    <label htmlFor="reg-roll" className="block text-xs font-medium text-slate-300">
                      Roll Number (Optional)
                    </label>
                    <input
                      id="reg-roll"
                      type="text"
                      maxLength={20}
                      value={regRollNumber}
                      onChange={(e) => setRegRollNumber(e.target.value)}
                      placeholder="2107001"
                      className="mt-1.5 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-slate-100 tabular-nums focus:border-emerald-500 focus:outline-none"
                    />
                  </div>
                </div>
              </>
            )}

            <div>
              <label htmlFor="reg-password" className="block text-xs font-medium text-slate-300">
                Password (min 8 chars)
              </label>
              <div className="relative mt-1.5">
                <input
                  id="reg-password"
                  type={showRegSecret ? 'text' : 'password'}
                  minLength={8}
                  maxLength={100}
                  value={regPassword}
                  onChange={(e) => setRegPassword(e.target.value)}
                  placeholder="Strong passphrase (BCrypt work factor 12)"
                  className="w-full rounded-lg border border-slate-700 bg-slate-950 py-2.5 pl-3 pr-10 text-sm text-slate-100 placeholder:text-slate-500 focus:border-emerald-500 focus:outline-none"
                  required
                />
                <button
                  type="button"
                  aria-label={showRegSecret ? 'Hide registration secret' : 'Reveal registration secret'}
                  onClick={() => setShowRegSecret((v) => !v)}
                  className="absolute right-2.5 top-1/2 -translate-y-1/2 rounded p-1 text-slate-400 hover:text-slate-200"
                >
                  {showRegSecret ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              </div>
            </div>

            <button
              type="submit"
              disabled={submitting}
              className="flex w-full items-center justify-center gap-2 rounded-xl bg-emerald-600 py-2.5 text-sm font-medium text-white transition-[background-color,transform] duration-100 ease-out hover:bg-emerald-500 active:scale-[0.97] disabled:cursor-not-allowed disabled:opacity-50"
            >
              {submitting && <Loader2 className="h-4 w-4 animate-spin shrink-0" />}
              <span>Create KUETcord Account</span>
            </button>
          </form>
        )}

        {activeTab === 'verify' && (
          <form onSubmit={handleVerify} className="space-y-4">
            {verifiedDone ? (
              <div className="space-y-3 rounded-xl border border-emerald-500/30 bg-emerald-500/10 p-4 text-center">
                <CheckCircle2 className="mx-auto h-8 w-8 text-emerald-400" />
                <p className="text-sm font-semibold text-white">Email Verified Successfully</p>
                <p className="text-xs text-slate-300">
                  Your institutional identity is active. Proceed to sign in.
                </p>
                <button
                  type="button"
                  onClick={() => setActiveTab('login')}
                  className="rounded-lg bg-emerald-600 px-4 py-2 text-xs font-medium text-white hover:bg-emerald-500"
                >
                  Continue to Sign In
                </button>
              </div>
            ) : (
              <>
                <div>
                  <label htmlFor="verify-token" className="block text-xs font-medium text-slate-300">
                    64-Character Verification Token (from Mailpit / KUET Inbox)
                  </label>
                  <input
                    id="verify-token"
                    type="text"
                    value={verifyToken}
                    onChange={(e) => setVerifyToken(e.target.value)}
                    placeholder="Paste verification token..."
                    className="mt-1.5 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2.5 font-mono text-xs text-slate-100 placeholder:text-slate-500 focus:border-emerald-500 focus:outline-none"
                    required
                  />
                </div>
                <button
                  type="submit"
                  disabled={submitting}
                  className="flex w-full items-center justify-center gap-2 rounded-xl bg-emerald-600 py-2.5 text-sm font-medium text-white transition-[background-color,transform] duration-100 ease-out hover:bg-emerald-500 active:scale-[0.97] disabled:cursor-not-allowed disabled:opacity-50"
                >
                  {submitting && <Loader2 className="h-4 w-4 animate-spin shrink-0" />}
                  <span>Verify Institutional Email</span>
                </button>
              </>
            )}
          </form>
        )}

        {/* Quick Demo Preview Switcher */}
        <div className="mt-6 border-t border-slate-800 pt-4 text-center">
          <p className="text-[11px] text-slate-500">Previewing without a running backend? Try any role:</p>
          <div className="mt-2 grid grid-cols-2 gap-2 sm:grid-cols-4">
            {(
              [
                { role: 'STUDENT', label: 'Student', target: '/ct-hub' },
                { role: 'CR', label: 'CR', target: '/ct-hub' },
                { role: 'TEACHER', label: 'Teacher', target: '/ct-hub' },
                { role: 'ADMIN', label: 'Admin', target: '/admin' },
              ] as const
            ).map((item) => (
              <button
                key={item.role}
                type="button"
                onClick={() => {
                  enterDemoMode(item.role);
                  navigate(item.target);
                }}
                className="rounded-lg border border-slate-700 bg-slate-950 px-2.5 py-1.5 text-xs font-medium text-slate-300 transition-[background-color,transform] duration-100 ease-out hover:bg-slate-800 hover:text-white active:scale-[0.97]"
              >
                {item.label}
              </button>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
