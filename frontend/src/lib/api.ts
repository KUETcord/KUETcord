import type {
  AuthResponse,
  CourseFollowStatus,
  CourseOffering,
  CtEventDetail,
  CtEventSummary,
  CtStatus,
  TeacherSummary,
  UserSummary,
} from '../types/domain';

interface ApiEnvelope<T> {
  success: boolean;
  message?: string;
  data: T;
  timestamp: string;
}

interface ErrorEnvelope {
  status: number;
  error: string;
  message: string;
  path?: string;
  validationErrors?: Record<string, string>;
}

export class ApiError extends Error {
  readonly status: number;
  readonly validationErrors?: Record<string, string>;

  constructor(status: number, message: string, validationErrors?: Record<string, string>) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.validationErrors = validationErrors;
  }
}

const API_BASE = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? '/api/v1';

let inMemoryToken: string | null = null;

export function setAccessToken(token: string | null) {
  inMemoryToken = token;
}

export function getAccessToken(): string | null {
  return inMemoryToken;
}

export async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  if (!headers.has('Content-Type') && init.body) {
    headers.set('Content-Type', 'application/json');
  }
  if (inMemoryToken && !headers.has('Authorization')) {
    headers.set('Authorization', `Bearer ${inMemoryToken}`);
  }

  const response = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers,
  });

  const text = await response.text();
  const parsed = text ? JSON.parse(text) : null;

  if (!response.ok) {
    const err = parsed as ErrorEnvelope | null;
    throw new ApiError(
      response.status,
      err?.message ?? `Request failed with status ${response.status}`,
      err?.validationErrors,
    );
  }

  const envelope = parsed as ApiEnvelope<T> | null;
  return envelope ? envelope.data : (undefined as T);
}

export const authApi = {
  register(payload: {
    email: string;
    password: string;
    displayName: string;
    department?: string;
    batch?: string;
  }): Promise<UserSummary> {
    return request<UserSummary>('/auth/register', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  },

  login(payload: { email: string; password: string }): Promise<AuthResponse> {
    return request<AuthResponse>('/auth/login', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  },

  verifyEmail(token: string): Promise<UserSummary> {
    return request<UserSummary>('/auth/verify-email', {
      method: 'POST',
      body: JSON.stringify({ token }),
    });
  },

  resendVerification(email: string): Promise<void> {
    return request<void>('/auth/resend-verification', {
      method: 'POST',
      body: JSON.stringify({ email }),
    });
  },

  googleSignIn(idToken: string): Promise<AuthResponse> {
    return request<AuthResponse>('/auth/google', {
      method: 'POST',
      body: JSON.stringify({ idToken }),
    });
  },

  me(): Promise<UserSummary> {
    return request<UserSummary>('/me');
  },

  updateMe(payload: { displayName?: string; department?: string; batch?: string }): Promise<UserSummary> {
    return request<UserSummary>('/me', {
      method: 'PATCH',
      body: JSON.stringify(payload),
    });
  },
};

export const ctHubApi = {
  listCourses(params?: {
    department?: string;
    semester?: string;
    academicYear?: string;
    q?: string;
  }): Promise<CourseOffering[]> {
    const qs = new URLSearchParams();
    if (params?.department) qs.set('department', params.department);
    if (params?.semester) qs.set('semester', params.semester);
    if (params?.academicYear) qs.set('academicYear', params.academicYear);
    if (params?.q) qs.set('q', params.q);
    const suffix = qs.toString() ? `?${qs.toString()}` : '';
    return request<CourseOffering[]>(`/ct/courses${suffix}`);
  },

  followCourse(courseId: number): Promise<CourseFollowStatus> {
    return request<CourseFollowStatus>(`/ct/courses/${courseId}/follow`, {
      method: 'POST',
    });
  },

  unfollowCourse(courseId: number): Promise<CourseFollowStatus> {
    return request<CourseFollowStatus>(`/ct/courses/${courseId}/follow`, {
      method: 'DELETE',
    });
  },

  listEvents(params?: {
    courseId?: number;
    status?: CtStatus | '';
    from?: string;
    to?: string;
  }): Promise<CtEventSummary[]> {
    const qs = new URLSearchParams();
    if (params?.courseId) qs.set('courseId', String(params.courseId));
    if (params?.status) qs.set('status', params.status);
    if (params?.from) qs.set('from', params.from);
    if (params?.to) qs.set('to', params.to);
    const suffix = qs.toString() ? `?${qs.toString()}` : '';
    return request<CtEventSummary[]>(`/ct/events${suffix}`);
  },

  getEventDetail(eventId: number): Promise<CtEventDetail> {
    return request<CtEventDetail>(`/ct/events/${eventId}`);
  },

  searchTeachers(query?: string, department?: string): Promise<TeacherSummary[]> {
    const qs = new URLSearchParams();
    if (query) qs.set('q', query);
    if (department) qs.set('department', department);
    const suffix = qs.toString() ? `?${qs.toString()}` : '';
    return request<TeacherSummary[]>(`/ct/teachers${suffix}`);
  },

  createEvent(payload: {
    courseOfferingId: number;
    teacherId: number;
    title: string;
    scheduledAt: string;
    durationMinutes: number;
    coveredTopics?: string;
  }): Promise<CtEventSummary> {
    return request<CtEventSummary>('/ct/events', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  },

  verifyEvent(eventId: number, expectedVersion: number, note?: string): Promise<CtEventSummary> {
    return request<CtEventSummary>(`/ct/events/${eventId}/verify`, {
      method: 'POST',
      body: JSON.stringify({ expectedVersion, note }),
    });
  },

  rescheduleEvent(
    eventId: number,
    payload: { newScheduledAt: string; reason: string; expectedVersion: number },
  ): Promise<CtEventSummary> {
    return request<CtEventSummary>(`/ct/events/${eventId}/reschedule`, {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  },

  cancelEvent(
    eventId: number,
    payload: { reason: string; expectedVersion: number },
  ): Promise<CtEventSummary> {
    return request<CtEventSummary>(`/ct/events/${eventId}/cancel`, {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  },
};
