import React, { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Camera,
  Eye,
  EyeOff,
  KeyRound,
  Loader2,
  Save,
  ShieldCheck,
  Trash2,
  UserCircle2,
} from 'lucide-react';
import { toast } from 'sonner';
import { extractErrorMessage, useAuth } from '../context/AuthContext';
import { getInitials } from '../lib/format';
import {
  compressAvatarToDataUrl,
  formatYearTermLabel,
  KUET_DEPARTMENTS,
  YEAR_TERM_OPTIONS,
} from '../lib/yearTerm';

export function AccountPage() {
  const { user, updateProfile, changePassword } = useAuth();
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  const [displayName, setDisplayName] = useState(user?.displayName ?? '');
  const [username, setUsername] = useState(user?.username ?? '');
  const [department, setDepartment] = useState(user?.department ?? 'CSE');
  const [yearTerm, setYearTerm] = useState(user?.yearTerm ?? '');
  const [batch, setBatch] = useState(user?.batch ?? '');
  const [rollNumber, setRollNumber] = useState(user?.rollNumber ?? '');
  const [bio, setBio] = useState(user?.bio ?? '');
  const [avatarUrl, setAvatarUrl] = useState<string>(user?.avatarUrl ?? '');
  const [avatarDirtyClear, setAvatarDirtyClear] = useState(false);
  const [avatarByteSize, setAvatarByteSize] = useState<number | null>(null);

  const [savingProfile, setSavingProfile] = useState(false);

  // Password form
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPasswords, setShowPasswords] = useState(false);
  const [savingPassword, setSavingPassword] = useState(false);

  useEffect(() => {
    if (!user) return;
    setDisplayName(user.displayName ?? '');
    setUsername(user.username ?? '');
    setDepartment(user.department ?? 'CSE');
    setYearTerm(user.yearTerm ?? '');
    setBatch(user.batch ?? '');
    setRollNumber(user.rollNumber ?? '');
    setBio(user.bio ?? '');
    setAvatarUrl(user.avatarUrl ?? '');
    setAvatarDirtyClear(false);
    setAvatarByteSize(null);
  }, [user]);

  if (!user) {
    return (
      <div className="mx-auto max-w-lg rounded-2xl border border-slate-800 bg-slate-900/60 p-8 text-center">
        <UserCircle2 className="mx-auto mb-3 h-10 w-10 text-slate-500" aria-hidden="true" />
        <h2 className="text-xl font-bold tracking-tight text-white">Sign in to manage your account</h2>
        <p className="mt-1.5 text-xs text-slate-400">
          Update your profile picture, Year–Term batch filter, and password once signed in.
        </p>
        <Link
          to="/auth"
          className="mt-4 inline-flex items-center gap-2 rounded-xl bg-emerald-600 px-4 py-2 text-xs font-medium text-white hover:bg-emerald-500 active:scale-[0.97]"
        >
          Go to Sign In
        </Link>
      </div>
    );
  }

  const isStudentLike = user.globalRole === 'STUDENT' || user.globalRole === 'CR';

  const handleAvatarFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;

    try {
      const { dataUrl, byteSize } = await compressAvatarToDataUrl(file);
      setAvatarUrl(dataUrl);
      setAvatarDirtyClear(false);
      setAvatarByteSize(byteSize);
      toast.success(`Compressed profile photo to ${(byteSize / 1024).toFixed(1)} KB (WebP)`);
    } catch (err) {
      toast.error(extractErrorMessage(err, 'Could not process image'));
    }
  };

  const handleSaveProfile = async (e: React.FormEvent) => {
    e.preventDefault();
    setSavingProfile(true);
    try {
      await updateProfile({
        displayName: displayName.trim(),
        username: username.trim(),
        department: department.trim(),
        batch: batch.trim(),
        yearTerm: isStudentLike ? yearTerm : '',
        rollNumber: isStudentLike ? rollNumber.trim() : '',
        bio: bio.trim(),
        avatarUrl: avatarDirtyClear ? undefined : avatarUrl.trim() || undefined,
        clearAvatar: avatarDirtyClear,
      });
      setAvatarDirtyClear(false);
    } catch (err) {
      toast.error(extractErrorMessage(err, 'Failed to update profile'));
    } finally {
      setSavingProfile(false);
    }
  };

  const handleChangePassword = async (e: React.FormEvent) => {
    e.preventDefault();
    if (newPassword.length < 8) {
      toast.error('New password must be at least 8 characters');
      return;
    }
    if (newPassword !== confirmPassword) {
      toast.error('New password and confirmation do not match');
      return;
    }

    setSavingPassword(true);
    try {
      await changePassword({
        currentPassword: currentPassword || undefined,
        newPassword,
      });
      setCurrentPassword('');
      setNewPassword('');
      setConfirmPassword('');
    } catch (err) {
      toast.error(extractErrorMessage(err, 'Could not change password'));
    } finally {
      setSavingPassword(false);
    }
  };

  return (
    <div className="mx-auto max-w-3xl space-y-6 pb-16">
      <div>
        <h2 className="text-2xl font-bold tracking-tight text-white sm:text-3xl">My Account</h2>
        <p className="mt-1 max-w-[65ch] text-sm text-slate-400">
          Manage your KUETcord identity, profile photo, default Year–Term batch filter, and account security.
        </p>
      </div>

      {/* Identity & Avatar Card */}
      <form
        onSubmit={handleSaveProfile}
        className="space-y-6 rounded-2xl border border-slate-800 bg-slate-900/60 p-6"
      >
        <div className="flex flex-col gap-4 border-b border-slate-800 pb-6 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex min-w-0 items-center gap-4">
            {avatarUrl && !avatarDirtyClear ? (
              <img
                src={avatarUrl}
                alt={`${user.displayName} avatar`}
                className="h-16 w-16 shrink-0 rounded-full border border-emerald-500/40 object-cover"
              />
            ) : (
              <div className="flex h-16 w-16 shrink-0 items-center justify-center rounded-full border border-emerald-500/30 bg-emerald-600/20 text-lg font-bold text-emerald-300">
                {getInitials(displayName || user.displayName)}
              </div>
            )}
            <div className="min-w-0">
              <div className="flex flex-wrap items-center gap-2">
                <h3 className="truncate text-base font-semibold text-white">{user.displayName}</h3>
                <span className="rounded-md bg-slate-800 px-2 py-0.5 font-mono text-[11px] font-semibold text-emerald-300">
                  {user.globalRole}
                </span>
                {user.yearTerm && (
                  <span className="rounded-md border border-sky-500/30 bg-sky-500/10 px-2 py-0.5 text-[11px] font-medium text-sky-300 tabular-nums">
                    {formatYearTermLabel(user.yearTerm)}
                  </span>
                )}
              </div>
              <p className="mt-0.5 truncate text-xs text-slate-400">{user.email}</p>
              {avatarByteSize !== null && (
                <p className="mt-1 text-[11px] text-emerald-400 tabular-nums">
                  Ready to save · {(avatarByteSize / 1024).toFixed(1)} KB compressed WebP (Free-Tier persistent)
                </p>
              )}
            </div>
          </div>

          <div className="flex flex-wrap items-center gap-2 shrink-0">
            <input
              ref={fileInputRef}
              type="file"
              accept="image/png,image/jpeg,image/webp"
              onChange={handleAvatarFileChange}
              className="hidden"
              aria-label="Upload profile photo"
            />
            <button
              type="button"
              onClick={() => fileInputRef.current?.click()}
              className="inline-flex items-center gap-1.5 rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-xs font-medium text-slate-200 transition-[background-color,transform] duration-100 ease-out hover:bg-slate-800 active:scale-[0.97]"
            >
              <Camera className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
              <span>Upload Photo</span>
            </button>
            {(avatarUrl || user.avatarUrl) && !avatarDirtyClear && (
              <button
                type="button"
                onClick={() => {
                  setAvatarUrl('');
                  setAvatarDirtyClear(true);
                  setAvatarByteSize(null);
                }}
                className="inline-flex items-center gap-1 rounded-lg border border-rose-500/30 bg-rose-500/10 px-3 py-2 text-xs font-medium text-rose-200 transition-[background-color,transform] duration-100 ease-out hover:bg-rose-500/20 active:scale-[0.97]"
              >
                <Trash2 className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                <span>Remove</span>
              </button>
            )}
          </div>
        </div>

        {/* Profile Fields */}
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div>
            <label htmlFor="acc-display-name" className="block text-xs font-medium text-slate-300">
              Display Name
            </label>
            <input
              id="acc-display-name"
              type="text"
              minLength={2}
              maxLength={100}
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
              className="mt-1.5 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-slate-100 focus:border-emerald-500 focus:outline-none"
              required
            />
          </div>

          <div>
            <label htmlFor="acc-username" className="block text-xs font-medium text-slate-300">
              Username Handle
            </label>
            <input
              id="acc-username"
              type="text"
              minLength={3}
              maxLength={50}
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              className="mt-1.5 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 font-mono text-sm text-slate-100 focus:border-emerald-500 focus:outline-none"
              required
            />
          </div>

          <div>
            <label htmlFor="acc-dept" className="block text-xs font-medium text-slate-300">
              Department
            </label>
            <select
              id="acc-dept"
              value={department}
              onChange={(e) => setDepartment(e.target.value)}
              className="mt-1.5 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-slate-100 focus:border-emerald-500 focus:outline-none"
            >
              {KUET_DEPARTMENTS.map((dept) => (
                <option key={dept} value={dept}>
                  {dept}
                </option>
              ))}
            </select>
          </div>

          {isStudentLike ? (
            <div>
              <label htmlFor="acc-year-term" className="block text-xs font-medium text-slate-300">
                Current Year &amp; Term (Default Hub Filter)
              </label>
              <select
                id="acc-year-term"
                value={yearTerm}
                onChange={(e) => setYearTerm(e.target.value)}
                className="mt-1.5 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-slate-100 focus:border-emerald-500 focus:outline-none"
              >
                <option value="">Not set (show all terms)</option>
                {YEAR_TERM_OPTIONS.map((yt) => (
                  <option key={yt.code} value={yt.code}>
                    {yt.label}
                  </option>
                ))}
              </select>
            </div>
          ) : (
            <div>
              <label htmlFor="acc-batch" className="block text-xs font-medium text-slate-300">
                Affiliation / Unit
              </label>
              <input
                id="acc-batch"
                type="text"
                maxLength={20}
                value={batch}
                onChange={(e) => setBatch(e.target.value)}
                placeholder="Faculty"
                className="mt-1.5 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-slate-100 focus:border-emerald-500 focus:outline-none"
              />
            </div>
          )}

          {isStudentLike && (
            <>
              <div>
                <label htmlFor="acc-batch" className="block text-xs font-medium text-slate-300">
                  Batch
                </label>
                <input
                  id="acc-batch"
                  type="text"
                  maxLength={20}
                  value={batch}
                  onChange={(e) => setBatch(e.target.value)}
                  placeholder="2k20"
                  className="mt-1.5 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-slate-100 focus:border-emerald-500 focus:outline-none"
                />
              </div>

              <div>
                <label htmlFor="acc-roll" className="block text-xs font-medium text-slate-300">
                  Roll Number
                </label>
                <input
                  id="acc-roll"
                  type="text"
                  maxLength={20}
                  value={rollNumber}
                  onChange={(e) => setRollNumber(e.target.value)}
                  placeholder="2007001"
                  className="mt-1.5 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-slate-100 tabular-nums focus:border-emerald-500 focus:outline-none"
                />
              </div>
            </>
          )}
        </div>

        <div>
          <label htmlFor="acc-bio" className="block text-xs font-medium text-slate-300">
            Bio / Academic Note (Max 280 chars)
          </label>
          <textarea
            id="acc-bio"
            rows={2}
            maxLength={280}
            value={bio}
            onChange={(e) => setBio(e.target.value)}
            placeholder="Interests, designation, lab group, or office hours..."
            className="mt-1.5 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-slate-100 placeholder:text-slate-500 focus:border-emerald-500 focus:outline-none"
          />
        </div>

        <div className="flex justify-end">
          <button
            type="submit"
            disabled={savingProfile}
            className="inline-flex items-center gap-2 rounded-xl bg-emerald-600 px-4 py-2.5 text-xs font-medium text-white transition-[background-color,transform] duration-100 ease-out hover:bg-emerald-500 active:scale-[0.97] disabled:cursor-not-allowed disabled:opacity-50"
          >
            {savingProfile ? (
              <Loader2 className="h-4 w-4 animate-spin shrink-0" />
            ) : (
              <Save className="h-4 w-4 shrink-0" />
            )}
            <span>Save Account Changes</span>
          </button>
        </div>
      </form>

      {/* Security / Password Change Card */}
      <form
        onSubmit={handleChangePassword}
        className="space-y-4 rounded-2xl border border-slate-800 bg-slate-900/60 p-6"
      >
        <div className="flex items-center justify-between gap-2">
          <div className="flex items-center gap-2.5">
            <ShieldCheck className="h-5 w-5 text-emerald-400 shrink-0" aria-hidden="true" />
            <div>
              <h3 className="text-base font-semibold text-white">Change Password</h3>
              <p className="text-xs text-slate-400">
                Hashed at rest with BCrypt (work factor 12). Minimum 8 characters.
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={() => setShowPasswords((v) => !v)}
            className="inline-flex items-center gap-1 rounded-lg border border-slate-800 px-2.5 py-1 text-xs text-slate-400 hover:text-slate-200"
          >
            {showPasswords ? <EyeOff className="h-3.5 w-3.5" /> : <Eye className="h-3.5 w-3.5" />}
            <span>{showPasswords ? 'Hide' : 'Show'}</span>
          </button>
        </div>

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
          {user.hasLocalPassword !== false && (
            <div>
              <label htmlFor="acc-cur-pw" className="block text-xs font-medium text-slate-300">
                Current Password
              </label>
              <input
                id="acc-cur-pw"
                type={showPasswords ? 'text' : 'password'}
                value={currentPassword}
                onChange={(e) => setCurrentPassword(e.target.value)}
                className="mt-1.5 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-slate-100 focus:border-emerald-500 focus:outline-none"
                required
              />
            </div>
          )}

          <div>
            <label htmlFor="acc-new-pw" className="block text-xs font-medium text-slate-300">
              New Password
            </label>
            <input
              id="acc-new-pw"
              type={showPasswords ? 'text' : 'password'}
              minLength={8}
              maxLength={100}
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              className="mt-1.5 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-slate-100 focus:border-emerald-500 focus:outline-none"
              required
            />
          </div>

          <div>
            <label htmlFor="acc-confirm-pw" className="block text-xs font-medium text-slate-300">
              Confirm New Password
            </label>
            <input
              id="acc-confirm-pw"
              type={showPasswords ? 'text' : 'password'}
              minLength={8}
              maxLength={100}
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              className="mt-1.5 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-slate-100 focus:border-emerald-500 focus:outline-none"
              required
            />
          </div>
        </div>

        <div className="flex justify-end">
          <button
            type="submit"
            disabled={savingPassword}
            className="inline-flex items-center gap-2 rounded-xl bg-slate-800 px-4 py-2.5 text-xs font-medium text-white transition-[background-color,transform] duration-100 ease-out hover:bg-slate-700 active:scale-[0.97] disabled:cursor-not-allowed disabled:opacity-50"
          >
            {savingPassword ? (
              <Loader2 className="h-4 w-4 animate-spin shrink-0" />
            ) : (
              <KeyRound className="h-4 w-4 shrink-0" />
            )}
            <span>Update Password</span>
          </button>
        </div>
      </form>
    </div>
  );
}
