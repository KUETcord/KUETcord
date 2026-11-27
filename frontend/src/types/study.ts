import type { UserRef } from './collab';

/** Mirrors com.kuetcord.studycircles and com.kuetcord.pulse DTOs (Member C, Weeks 3 and 5). */

export type StudyCircleRole = 'HOST' | 'ATTENDEE';
export type ResourceCategory = 'NOTES' | 'QUESTION_BANK' | 'SLIDES' | 'RECORDING';
export type AnnouncementCategory = 'SEMINAR' | 'CLUB' | 'ACADEMIC' | 'CAREER';

export const RESOURCE_CATEGORIES: ResourceCategory[] = ['NOTES', 'QUESTION_BANK', 'SLIDES', 'RECORDING'];
export const ANNOUNCEMENT_CATEGORIES: AnnouncementCategory[] = ['CLUB', 'SEMINAR', 'ACADEMIC', 'CAREER'];

export interface CourseRef {
  id: number;
  courseCode: string;
  courseTitle: string;
  semester: string;
  academicYear: string;
}

export interface StudyCircleSummary {
  id: number;
  name: string;
  description: string | null;
  course: CourseRef | null;
  memberCount: number;
  myRole: StudyCircleRole | null;
  nextSessionAt: string | null;
  createdAt: string;
}

export interface StudyCirclePage {
  items: StudyCircleSummary[];
  page: number;
  size: number;
  totalItems: number;
}

export interface CircleMember {
  userId: number;
  displayName: string;
  role: StudyCircleRole;
  joinedAt: string;
}

export interface StudySession {
  id: number;
  title: string;
  scheduledAt: string;
  durationMinutes: number;
  endsAt: string;
  agenda: string | null;
  locationOrLink: string | null;
  scheduledBy: UserRef | null;
  createdAt: string;
}

export interface StudyCircleDetail {
  id: number;
  name: string;
  description: string | null;
  course: CourseRef | null;
  creator: UserRef | null;
  /** null when the viewer is not a member; sessions, resources and the checklist are then hidden. */
  myRole: StudyCircleRole | null;
  members: CircleMember[];
  nextSession: StudySession | null;
  resourceCount: number;
  createdAt: string;
}

export interface Membership {
  studyCircleId: number;
  role: StudyCircleRole;
  memberCount: number;
  newlyJoined: boolean;
}

export interface StudyResource {
  id: number;
  title: string;
  url: string;
  category: ResourceCategory;
  contributor: UserRef | null;
  createdAt: string;
}

export interface RevisionItem {
  id: number;
  title: string;
  done: boolean;
  createdBy: UserRef | null;
  completedBy: UserRef | null;
  completedAt: string | null;
  createdAt: string;
}

export interface Announcement {
  id: number;
  organization: string;
  category: AnnouncementCategory;
  title: string;
  body: string;
  sourceUrl: string | null;
  publishedAt: string;
  author: UserRef | null;
}

export interface AnnouncementPage {
  items: Announcement[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export interface CircleQuery {
  q?: string;
  mine?: boolean;
  /** Set by the CT Hub's "Study Circles" link on a CT card (Member A, ?courseId=). */
  courseOfferingId?: number;
}

export interface CreateCirclePayload {
  name: string;
  description?: string;
}

export interface ScheduleSessionPayload {
  title: string;
  scheduledAt: string;
  durationMinutes: number;
  agenda?: string;
  locationOrLink?: string;
}

export interface ShareResourcePayload {
  title: string;
  url: string;
  category: ResourceCategory;
}

export interface PublishAnnouncementPayload {
  organization: string;
  category: AnnouncementCategory;
  title: string;
  body: string;
  sourceUrl?: string;
}
