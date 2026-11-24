import React, { createContext, useCallback, useContext, useMemo, useState } from 'react';
import { toast } from 'sonner';
import { ApiError, authApi, setAccessToken } from '../lib/api';
import { DEMO_CR_USER, DEMO_STUDENT_USER, DEMO_TEACHER_USER } from '../fixtures/ctHubFixtures';
import type { UserSummary } from '../types/domain';

interface AuthContextValue {
  user: UserSummary | null;
  isDemoMode: boolean;
  canProposeCt: boolean;
  canApproveCt: boolean;
  canRescheduleOrCancelCt: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (payload: {
    email: string;
    password: string;
    displayName: string;
    department?: string;
    batch?: string;
  }) => Promise<UserSummary>;
  verifyEmail: (token: string) => Promise<UserSummary>;
  resendVerification: (email: string) => Promise<void>;
  enterDemoMode: (role: 'TEACHER' | 'CR' | 'STUDENT') => void;
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
  }, []);

  const register = useCallback(
    async (payload: {
      email: string;
      password: string;
      displayName: string;
      department?: string;
      batch?: string;
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

  const enterDemoMode = useCallback((role: 'TEACHER' | 'CR' | 'STUDENT') => {
    setAccessToken(null);
    setIsDemoMode(true);
    const nextUser =
      role === 'TEACHER'
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

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      isDemoMode,
      canProposeCt,
      canApproveCt,
      canRescheduleOrCancelCt,
      login,
      register,
      verifyEmail,
      resendVerification,
      enterDemoMode,
      logout,
    }),
    [
      user,
      isDemoMode,
      canProposeCt,
      canApproveCt,
      canRescheduleOrCancelCt,
      login,
      register,
      verifyEmail,
      resendVerification,
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
