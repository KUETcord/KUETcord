import React, { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { CheckCircle2, KeyRound, Loader2, Mail, ShieldCheck } from 'lucide-react';
import { toast } from 'sonner';
import { extractErrorMessage, useAuth } from '../context/AuthContext';
import { ApiError } from '../lib/api';
import { cn } from '../lib/format';

type AuthTab = 'login' | 'register' | 'verify';

const KUET_EMAIL_REGEX = /^[a-zA-Z0-9._%+-]+@([a-zA-Z0-9-]+\.)?kuet\.ac\.bd$/i;

export function AuthPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { login, register, verifyEmail, resendVerification, enterDemoMode } = useAuth();

  const initialToken = searchParams.get('token') ?? '';
  const [activeTab, setActiveTab] = useState<AuthTab>(initialToken ? 'verify' : 'login');

  // Login state
  const [loginEmail, setLoginEmail] = useState('');
  const [loginPassword, setLoginPassword] = useState('');
  const [needsVerificationEmail, setNeedsVerificationEmail] = useState<string | null>(null);

  // Register state
  const [regEmail, setRegEmail] = useState('');
  const [regPassword, setRegPassword] = useState('');
  const [regDisplayName, setRegDisplayName] = useState('');
  const [regDepartment, setRegDepartment] = useState('CSE');
  const [regBatch, setRegBatch] = useState('2k20');

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

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMsg(null);
    setNeedsVerificationEmail(null);
    setSubmitting(true);
    try {
      await login(loginEmail.trim(), loginPassword);
      navigate('/ct-hub');
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

    setSubmitting(true);
    try {
      await register({
        email: regEmail.trim(),
        password: regPassword,
        displayName: regDisplayName.trim(),
        department: regDepartment.trim(),
        batch: regBatch.trim(),
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
      const message = extractErrorMessage(err, 'Verification failed');
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
      toast.error(extractErrorMessage(err, 'Could not resend verification email'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="mx-auto max-w-md py-4">
      <div className="mb-6 text-center">
        <div className="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-xl bg-emerald-600/20 text-emerald-400 border border-emerald-500/30">
          <ShieldCheck className="h-6 w-6" />
        </div>
        <h2 className="text-2xl font-bold tracking-tight text-white">KUET Institutional Access</h2>
        <p className="mt-1.5 text-xs text-slate-400">
          Restricted to verified KUET student (<span className="font-mono text-emerald-400">@stud.kuet.ac.bd</span>) and faculty (<span className="font-mono text-emerald-400">@&lt;dept&gt;.kuet.ac.bd</span>) accounts.
        </p>
      </div>

      <div className="rounded-2xl border border-slate-800 bg-slate-900/70 p-6 shadow-xl">
        {/* Tabs */}
        <div className="mb-5 grid grid-cols-3 gap-1 rounded-xl bg-slate-950 p-1">
          {(['login', 'register', 'verify'] as const).map((tab) => (
            <button
              key={tab}
              type="button"
              onClick={() => {
                setActiveTab(tab);
                setErrorMsg(null);
              }}
              className={cn(
                'rounded-lg py-1.5 text-xs font-medium capitalize transition-colors duration-150 ease-out',
                activeTab === tab
                  ? 'bg-slate-800 text-white'
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
            className="mb-4 rounded-xl border border-rose-500/30 bg-rose-500/10 p-3 text-xs text-rose-200"
          >
            <p>{errorMsg}</p>
            {needsVerificationEmail && (
              <button
                type="button"
                disabled={submitting}
                onClick={handleResend}
                className="mt-2 inline-flex items-center gap-1 text-xs font-medium text-emerald-300 underline hover:text-emerald-200"
              >
                <Mail className="h-3.5 w-3.5 shrink-0" />
                <span>Resend verification email</span>
              </button>
            )}
          </div>
        )}

        {activeTab === 'login' && (
          <form onSubmit={handleLogin} className="space-y-4">
            <div>
              <label htmlFor="login-email" className="block text-xs font-medium text-slate-300">
                KUET Student Email
              </label>
              <input
                id="login-email"
                type="email"
                value={loginEmail}
                onChange={(e) => setLoginEmail(e.target.value)}
                placeholder="2007001@stud.kuet.ac.bd"
                className="mt-1.5 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-slate-100 placeholder:text-slate-500 focus:border-emerald-500 focus:outline-none"
                required
              />
            </div>

            <div>
              <label htmlFor="login-password" className="block text-xs font-medium text-slate-300">
                Password
              </label>
              <input
                id="login-password"
                type="password"
                value={loginPassword}
                onChange={(e) => setLoginPassword(e.target.value)}
                placeholder="••••••••"
                className="mt-1.5 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-slate-100 placeholder:text-slate-500 focus:border-emerald-500 focus:outline-none"
                required
              />
            </div>

            <button
              type="submit"
              disabled={submitting}
              className="flex w-full items-center justify-center gap-2 rounded-lg bg-emerald-600 px-4 py-2.5 text-sm font-medium text-white transition-colors duration-150 ease-out hover:bg-emerald-500 disabled:cursor-not-allowed disabled:opacity-50"
            >
              {submitting && <Loader2 className="h-4 w-4 animate-spin shrink-0" />}
              <span>Sign In</span>
            </button>
          </form>
        )}

        {activeTab === 'register' && (
          <form onSubmit={handleRegister} className="space-y-3.5">
            <div>
              <label htmlFor="reg-email" className="block text-xs font-medium text-slate-300">
                Institutional Email (@stud.kuet.ac.bd)
              </label>
              <input
                id="reg-email"
                type="email"
                value={regEmail}
                onChange={(e) => setRegEmail(e.target.value)}
                placeholder="2007001@stud.kuet.ac.bd"
                className="mt-1 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-slate-100 placeholder:text-slate-500 focus:border-emerald-500 focus:outline-none"
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
                value={regDisplayName}
                onChange={(e) => setRegDisplayName(e.target.value)}
                placeholder="Rahim Ahmed"
                className="mt-1 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-slate-100 placeholder:text-slate-500 focus:border-emerald-500 focus:outline-none"
                required
              />
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label htmlFor="reg-dept" className="block text-xs font-medium text-slate-300">
                  Department
                </label>
                <input
                  id="reg-dept"
                  type="text"
                  value={regDepartment}
                  onChange={(e) => setRegDepartment(e.target.value)}
                  placeholder="CSE"
                  className="mt-1 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-slate-100 focus:border-emerald-500 focus:outline-none"
                />
              </div>
              <div>
                <label htmlFor="reg-batch" className="block text-xs font-medium text-slate-300">
                  Batch
                </label>
                <input
                  id="reg-batch"
                  type="text"
                  value={regBatch}
                  onChange={(e) => setRegBatch(e.target.value)}
                  placeholder="2k20"
                  className="mt-1 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-slate-100 focus:border-emerald-500 focus:outline-none"
                />
              </div>
            </div>

            <div>
              <label htmlFor="reg-password" className="block text-xs font-medium text-slate-300">
                Password (min 8 chars)
              </label>
              <input
                id="reg-password"
                type="password"
                minLength={8}
                value={regPassword}
                onChange={(e) => setRegPassword(e.target.value)}
                placeholder="StrongPassword!2026"
                className="mt-1 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-slate-100 placeholder:text-slate-500 focus:border-emerald-500 focus:outline-none"
                required
              />
            </div>

            <button
              type="submit"
              disabled={submitting}
              className="flex w-full items-center justify-center gap-2 rounded-lg bg-emerald-600 px-4 py-2.5 text-sm font-medium text-white transition-colors duration-150 ease-out hover:bg-emerald-500 disabled:cursor-not-allowed disabled:opacity-50"
            >
              {submitting && <Loader2 className="h-4 w-4 animate-spin shrink-0" />}
              <span>Create KUETcord Account</span>
            </button>
          </form>
        )}

        {activeTab === 'verify' && (
          <form onSubmit={handleVerify} className="space-y-4">
            {verifiedDone ? (
              <div className="rounded-xl border border-emerald-500/30 bg-emerald-500/10 p-4 text-center">
                <CheckCircle2 className="mx-auto mb-2 h-6 w-6 text-emerald-400" />
                <p className="text-sm font-medium text-emerald-200">Email Verified!</p>
                <button
                  type="button"
                  onClick={() => setActiveTab('login')}
                  className="mt-3 rounded-lg bg-emerald-600 px-4 py-1.5 text-xs font-medium text-white hover:bg-emerald-500"
                >
                  Proceed to Sign In
                </button>
              </div>
            ) : (
              <>
                <div>
                  <label htmlFor="verify-token" className="block text-xs font-medium text-slate-300">
                    64-Character Verification Token
                  </label>
                  <input
                    id="verify-token"
                    type="text"
                    value={verifyToken}
                    onChange={(e) => setVerifyToken(e.target.value)}
                    placeholder="Paste token from Mailpit / KUET inbox"
                    className="mt-1.5 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 font-mono text-xs text-slate-100 placeholder:text-slate-500 focus:border-emerald-500 focus:outline-none"
                    required
                  />
                </div>
                <button
                  type="submit"
                  disabled={submitting}
                  className="flex w-full items-center justify-center gap-2 rounded-lg bg-emerald-600 px-4 py-2.5 text-sm font-medium text-white transition-colors duration-150 ease-out hover:bg-emerald-500 disabled:cursor-not-allowed disabled:opacity-50"
                >
                  {submitting ? (
                    <Loader2 className="h-4 w-4 animate-spin shrink-0" />
                  ) : (
                    <KeyRound className="h-4 w-4 shrink-0" />
                  )}
                  <span>Verify Institutional Email</span>
                </button>
              </>
            )}
          </form>
        )}

        {/* Offline / Viva Demo Switcher */}
        <div className="mt-6 border-t border-slate-800 pt-4 text-center">
          <p className="mb-2.5 text-[11px] text-slate-400">
            Previewing without a running backend?
          </p>
          <div className="flex gap-2">
            <button
              type="button"
              onClick={() => {
                enterDemoMode('CR');
                navigate('/ct-hub');
              }}
              className="flex-1 rounded-lg border border-slate-700 bg-slate-800/70 px-3 py-1.5 text-xs font-medium text-slate-200 hover:bg-slate-800"
            >
              Preview as CR
            </button>
            <button
              type="button"
              onClick={() => {
                enterDemoMode('STUDENT');
                navigate('/ct-hub');
              }}
              className="flex-1 rounded-lg border border-slate-700 bg-slate-800/70 px-3 py-1.5 text-xs font-medium text-slate-200 hover:bg-slate-800"
            >
              Preview as Student
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
