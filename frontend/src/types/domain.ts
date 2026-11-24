export type GlobalRole = 'STUDENT' | 'CR' | 'MODERATOR' | 'ADMIN';

export type UserStatus = 'ACTIVE' | 'SUSPENDED' | 'DELETED';

export interface UserSummary {
  id: number;
  email: string;
  displayName: string;
  username: string;
  globalRole: GlobalRole;
  roles: string[];
  department: string | null;
  batch: string | null;
  emailVerified: boolean;
  status: UserStatus;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: UserSummary;
}

export type CtStatus = 'UNVERIFIED' | 'VERIFIED' | 'RESCHEDULED' | 'CANCELLED';

export interface CourseOffering {
  id: number;
  courseCode: string;
  courseTitle: string;
  department: string;
  semester: string;
  academicYear: string;
  followed: boolean;
}

export interface CourseFollowStatus {
  courseOfferingId: number;
  followed: boolean;
  followerCount: number;
}

export interface CtEventSummary {
  id: number;
  courseOfferingId: number;
  courseCode: string;
  courseTitle: string;
  title: string;
  scheduledAt: string;
  durationMinutes: number;
  coveredTopics: string | null;
  status: CtStatus;
  submittedById: number;
  submittedByName: string;
  verifiedById: number | null;
  verifiedByName: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface CtEventHistory {
  id: number;
  previousStatus: CtStatus | null;
  newStatus: CtStatus;
  previousScheduledAt: string | null;
  newScheduledAt: string | null;
  reason: string | null;
  changedById: number;
  changedByName: string;
  changedAt: string;
}

export interface CtEventDetail {
  event: CtEventSummary;
  history: CtEventHistory[];
}
