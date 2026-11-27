import { request } from '../api';
import type {
  Announcement,
  AnnouncementCategory,
  AnnouncementPage,
  CircleQuery,
  CreateCirclePayload,
  Membership,
  PublishAnnouncementPayload,
  RevisionItem,
  ScheduleSessionPayload,
  ShareResourcePayload,
  StudyCircleDetail,
  StudyCirclePage,
  StudyResource,
  StudySession,
} from '../../types/study';

/**
 * Everything the Study Circles and Campus Pulse screens need. Like CollabSource, pages depend only on
 * this interface, so the same components run against Spring (signed in) or the preview store.
 */
export interface StudySource {
  browseCircles(query: CircleQuery): Promise<StudyCirclePage>;
  createCircle(payload: CreateCirclePayload): Promise<StudyCircleDetail>;
  getCircle(id: number): Promise<StudyCircleDetail>;
  joinCircle(id: number): Promise<Membership>;

  listSessions(circleId: number): Promise<StudySession[]>;
  scheduleSession(circleId: number, payload: ScheduleSessionPayload): Promise<StudySession>;

  listResources(circleId: number): Promise<StudyResource[]>;
  shareResource(circleId: number, payload: ShareResourcePayload): Promise<StudyResource>;

  listRevisionItems(circleId: number): Promise<RevisionItem[]>;
  addRevisionItem(circleId: number, title: string): Promise<RevisionItem>;
  setRevisionItemDone(circleId: number, itemId: number, done: boolean): Promise<RevisionItem>;

  listAnnouncements(query: { category?: AnnouncementCategory; page?: number }): Promise<AnnouncementPage>;
  publishAnnouncement(payload: PublishAnnouncementPayload): Promise<Announcement>;
}

function json(body: unknown): RequestInit {
  return { body: JSON.stringify(body) };
}

export const liveStudySource: StudySource = {
  browseCircles(query) {
    const qs = new URLSearchParams();
    if (query.q?.trim()) qs.set('q', query.q.trim());
    if (query.mine) qs.set('mine', 'true');
    if (query.courseOfferingId) qs.set('courseOfferingId', String(query.courseOfferingId));
    qs.set('size', '50');
    return request<StudyCirclePage>(`/study-circles?${qs.toString()}`);
  },
  createCircle: (payload) => request<StudyCircleDetail>('/study-circles', { method: 'POST', ...json(payload) }),
  getCircle: (id) => request<StudyCircleDetail>(`/study-circles/${id}`),
  joinCircle: (id) => request<Membership>(`/study-circles/${id}/join`, { method: 'POST' }),

  listSessions: (circleId) => request<StudySession[]>(`/study-circles/${circleId}/sessions`),
  scheduleSession: (circleId, payload) =>
    request<StudySession>(`/study-circles/${circleId}/sessions`, { method: 'POST', ...json(payload) }),

  listResources: (circleId) => request<StudyResource[]>(`/study-circles/${circleId}/resources`),
  shareResource: (circleId, payload) =>
    request<StudyResource>(`/study-circles/${circleId}/resources`, { method: 'POST', ...json(payload) }),

  listRevisionItems: (circleId) => request<RevisionItem[]>(`/study-circles/${circleId}/revision-items`),
  addRevisionItem: (circleId, title) =>
    request<RevisionItem>(`/study-circles/${circleId}/revision-items`, { method: 'POST', ...json({ title }) }),
  setRevisionItemDone: (circleId, itemId, done) =>
    request<RevisionItem>(`/study-circles/${circleId}/revision-items/${itemId}`, { method: 'PATCH', ...json({ done }) }),

  listAnnouncements(query) {
    const qs = new URLSearchParams();
    if (query.category) qs.set('category', query.category);
    qs.set('page', String(query.page ?? 0));
    qs.set('size', '10');
    return request<AnnouncementPage>(`/announcements?${qs.toString()}`);
  },
  publishAnnouncement: (payload) => request<Announcement>('/announcements', { method: 'POST', ...json(payload) }),
};
