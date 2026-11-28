export type GlobalRole = 'STUDENT' | 'CR' | 'TEACHER' | 'MODERATOR' | 'ADMIN';

export type RoleName =
  | 'ROLE_STUDENT'
  | 'ROLE_CR'
  | 'ROLE_TEACHER'
  | 'ROLE_MODERATOR'
  | 'ROLE_ADMIN';

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
  yearTerm?: string | null;
  rollNumber?: string | null;
  bio?: string | null;
  avatarUrl?: string | null;
  hasLocalPassword?: boolean;
  emailVerified: boolean;
  status: UserStatus;
}

export interface TeacherSummary {
  id: number;
  displayName: string;
  email: string;
  department: string | null;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: UserSummary;
}

export type CtStatus = 'UNVERIFIED' | 'VERIFIED' | 'RESCHEDULED' | 'CANCELLED';

export type AssessmentType = 'CT' | 'LABTEST' | 'QUIZ';

export interface CourseOffering {
  id: number;
  courseCode: string;
  courseTitle: string;
  department: string;
  semester: string;
  yearTerm?: string;
  targetBatch?: string | null;
  academicYear: string;
  followed: boolean;
}

export interface CourseFollowStatus {
  courseOfferingId: number;
  followed: boolean;
  followerCount: number;
}

export interface RoomAllocation {
  rollStart: number | null;
  rollEnd: number | null;
  room: string;
}

export interface CtEventSummary {
  id: number;
  courseOfferingId: number;
  courseCode: string;
  courseTitle: string;
  courseDepartment?: string | null;
  courseYearTerm?: string | null;
  targetDepartment?: string | null;
  targetYearTerm?: string | null;
  assessmentType?: AssessmentType;
  title: string;
  scheduledAt: string;
  durationMinutes: number;
  examRoom?: string | null;
  roomAllocations?: RoomAllocation[];
  targetBatch?: string | null;
  coveredTopics: string | null;
  status: CtStatus;
  submittedById: number;
  submittedByName: string;
  assignedTeacherId: number | null;
  assignedTeacherName: string | null;
  coTeacherId?: number | null;
  coTeacherName?: string | null;
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

export type CalendarEventType =
  | 'CT'
  | 'LABTEST'
  | 'QUIZ'
  | 'ASSIGNMENT'
  | 'PRESENTATION'
  | 'PROJECT'
  | 'TERM_FINAL';

export interface TermFinalEntry {
  id: number;
  examDate: string;
  dayOfWeek: string;
  courseOfferingId: number | null;
  courseCode: string;
  courseTitle: string;
  courseDepartment: string | null;
  examRoom: string | null;
}

export interface TermFinalSchedule {
  id: number;
  title: string;
  targetDepartment: string;
  yearTerm: string;
  batch: string;
  session: string;
  examStartTime: string;
  examEndTime: string;
  examRoom?: string | null;
  roomAllocations: RoomAllocation[];
  createdById: number;
  createdByName: string;
  entries: TermFinalEntry[];
  createdAt: string;
  updatedAt: string;
}

export interface UpsertTermFinalEntryPayload {
  examDate: string;
  courseOfferingId?: number | null;
  courseCode?: string;
  courseTitle?: string;
  examRoom?: string;
}

export interface UpsertTermFinalSchedulePayload {
  title: string;
  targetDepartment: string;
  yearTerm: string;
  batch: string;
  session: string;
  examStartTime: string;
  examEndTime: string;
  examRoom?: string | null;
  roomAllocations?: RoomAllocation[];
  entries: UpsertTermFinalEntryPayload[];
}

export interface CalendarEvent {
  id: string;
  sourceId: number;
  type: CalendarEventType;
  title: string;
  courseOfferingId: number;
  courseCode: string;
  courseTitle: string;
  department: string | null;
  yearTerm: string | null;
  targetBatch: string | null;
  startsAt: string;
  endsAt: string | null;
  durationMinutes: number | null;
  room: string | null;
  status: string;
  assignedTeacherName: string | null;
  submissionLink: string | null;
  submittedListLink: string | null;
  hubPath: string;
}
