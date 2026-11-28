import React, { createContext, useCallback, useContext, useMemo, useState } from 'react';
import { toast } from 'sonner';
import { ApiError, authApi, setAccessToken, type UpdateProfilePayload } from '../lib/api';
import {
  DEMO_ADMIN_USER,
  DEMO_CR_USER,
  DEMO_STUDENT_USER,
  DEMO_TEACHER_USER,
} from '../fixtures/ctHubFixtures';
import type { UserSummary } from '../types/domain';

export type DemoPreviewRole = 'ADMIN' | 'TEACHER' | 'CR' | 'STUDENT';

interface AuthContextValue {
  user: UserSummary | null;
  isDemoMode: boolean;
  canProposeCt: boolean;
  canApproveCt: boolean;
  canRescheduleOrCancelCt: boolean;
  canAccessAdminConsole: boolean;
  login: (email: string, password: string) => Promise<UserSummary>;
  register: (payload: {
    email: string;
    password: string;
    displayName: string;
    department?: string;
    batch?: string;
    yearTerm?: string;
    rollNumber?: string;
    bio?: string;
  }) => Promise<UserSummary>;
  verifyEmail: (token: string) => Promise<UserSummary>;
  resendVerification: (email: string) => Promise<void>;
  updateProfile: (payload: UpdateProfilePayload) => Promise<UserSummary>;
  changePassword: (payload: { currentPassword?: string; newPassword: string }) => Promise<void>;
  enterDemoMode: (role: DemoPreviewRole) => void;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<UserSummary | null>(DEMO_CR_USER);
  const [isDemoMode, setIsDemoMode] = useState<boolean>(true);

  const login = useCallback(async (email: string, password: string) => {
    const response = await authApi.login({ email, password });
    setAccessToken(response.accessToken);
    setUser(response.user);
    setIsDemoMode(false);
    toast.success(`Signed in as ${response.user.displayName}`);
    return response.user;
  }, []);

  const register = useCallback(
    async (payload: {
      email: string;
      password: string;
      displayName: string;
      department?: string;
      batch?: string;
      yearTerm?: string;
      rollNumber?: string;
      bio?: string;
    }) => {
      const created = await authApi.register(payload);
      toast.success('Account created. Check your KUET institutional inbox for the verification token.');
      return created;
    },
    [],
  );

  const verifyEmail = useCallback(async (token: string) => {
    const verified = await authApi.verifyEmail(token);
    toast.success('Institutional email verified! You can now sign in.');
    return verified;
  }, []);

  const resendVerification = useCallback(async (email: string) => {
    await authApi.resendVerification(email);
    toast.success('Verification email dispatched.');
  }, []);

  const updateProfile = useCallback(
    async (payload: UpdateProfilePayload): Promise<UserSummary> => {
      if (isDemoMode) {
        if (!user) throw new Error('Not signed in');
        const next: UserSummary = {
          ...user,
          displayName: payload.displayName?.trim() || user.displayName,
          username: payload.username?.trim().toLowerCase() || user.username,
          department:
            payload.department !== undefined
              ? payload.department.trim().toUpperCase() || null
              : user.department,
          batch: payload.batch !== undefined ? payload.batch.trim() || null : user.batch,
          yearTerm:
            payload.yearTerm !== undefined ? payload.yearTerm.trim() || null : user.yearTerm,
          rollNumber:
            payload.rollNumber !== undefined ? payload.rollNumber.trim() || null : user.rollNumber,
          bio: payload.bio !== undefined ? payload.bio.trim() || null : user.bio,
          avatarUrl: payload.clearAvatar
            ? null
            : payload.avatarUrl !== undefined
              ? payload.avatarUrl || null
              : user.avatarUrl,
        };
        setUser(next);
        toast.success('Account profile updated');
        return next;
      }

      const updated = await authApi.updateMe(payload);
      setUser(updated);
      toast.success('Account profile updated');
      return updated;
    },
    [isDemoMode, user],
  );

  const changePassword = useCallback(
    async (payload: { currentPassword?: string; newPassword: string }): Promise<void> => {
      if (isDemoMode) {
        if (payload.newPassword.length < 8) {
          throw new Error('New password must be at least 8 characters');
        }
        toast.success('Password updated in interactive preview');
        return;
      }
      await authApi.changePassword(payload);
      toast.success('Password updated successfully');
    },
    [isDemoMode],
  );

  const enterDemoMode = useCallback((role: DemoPreviewRole) => {
    setAccessToken(null);
    setIsDemoMode(true);
    const nextUser =
      role === 'ADMIN'
        ? DEMO_ADMIN_USER
        : role === 'TEACHER'
          ? DEMO_TEACHER_USER
          : role === 'CR'
            ? DEMO_CR_USER
            : DEMO_STUDENT_USER;
    setUser(nextUser);
    toast.info(`Switched preview role to ${nextUser.displayName} (${role})`);
  }, []);

  const logout = useCallback(() => {
    setAccessToken(null);
    setUser(null);
    setIsDemoMode(false);
    toast('Signed out of KUETcord');
  }, []);

  const canProposeCt = useMemo(() => {
    if (!user) return false;
    return ['CR', 'TEACHER', 'MODERATOR', 'ADMIN'].includes(user.globalRole);
  }, [user]);

  const canApproveCt = useMemo(() => {
    if (!user) return false;
    return ['TEACHER', 'MODERATOR', 'ADMIN'].includes(user.globalRole);
  }, [user]);

  const canRescheduleOrCancelCt = useMemo(() => {
    if (!user) return false;
    return ['CR', 'TEACHER', 'MODERATOR', 'ADMIN'].includes(user.globalRole);
  }, [user]);

  const canAccessAdminConsole = useMemo(() => {
    if (!user) return false;
    return ['MODERATOR', 'ADMIN'].includes(user.globalRole);
  }, [user]);

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      isDemoMode,
      canProposeCt,
      canApproveCt,
      canRescheduleOrCancelCt,
      canAccessAdminConsole,
      login,
      register,
      verifyEmail,
      resendVerification,
      updateProfile,
      changePassword,
      enterDemoMode,
      logout,
    }),
    [
      user,
      isDemoMode,
      canProposeCt,
      canApproveCt,
      canRescheduleOrCancelCt,
      canAccessAdminConsole,
      login,
      register,
      verifyEmail,
      resendVerification,
      updateProfile,
      changePassword,
      enterDemoMode,
      logout,
    ],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth must be used inside <AuthProvider>');
  }
  return ctx;
}

export function extractErrorMessage(err: unknown, fallback = 'Something went wrong'): string {
  if (err instanceof ApiError) {
    if (err.validationErrors) {
      const firstFieldErr = Object.values(err.validationErrors)[0];
      if (firstFieldErr) return firstFieldErr;
    }
    return err.message;
  }
  if (err instanceof Error) {
    return err.message;
  }
  return fallback;
}
