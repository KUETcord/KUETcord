import { ApiError } from '../api';
import { buildStudySeed, type DemoCircle, type DemoStudySeed } from '../../fixtures/studyFixtures';
import type { UserRef } from '../../types/collab';
import type { StudyCircleDetail, StudyCircleSummary, StudySession } from '../../types/study';
import type { StudySource } from './studySource';

/** Same message the Spring StudyCircleAccessPolicy sends with a 403. */
export const MEMBERS_ONLY_MESSAGE = 'Join this study circle to see and share its sessions and resources';

const URL_PATTERN = /^https?:\/\/[^\s/$.?#][^\s]*$/;

let sharedStore: DemoStudySeed | null = null;

function store(): DemoStudySeed {
  if (!sharedStore) sharedStore = buildStudySeed();
  return sharedStore;
}

/** Test hook: start every test from the same seed. */
export function resetDemoStudyStore(nowMs?: number) {
  sharedStore = buildStudySeed(nowMs);
}

const clone = <T,>(value: T): T => JSON.parse(JSON.stringify(value)) as T;

const nextId = (rows: { id: number }[]) => rows.reduce((max, row) => Math.max(max, row.id), 0) + 1;

/**
 * In-memory implementation of {@link StudySource} for preview mode, enforcing the same rules as the server:
 * circles are open to browse, sessions, resources and the checklist are members-only (403), joining is
 * idempotent, sessions must be in the future and 15 to 480 minutes long, and resource links must be http(s).
 */
export function createDemoStudySource(viewer: UserRef, roles: string[] = []): StudySource {
  const db = store();

  const requireCircle = (id: number): DemoCircle => {
    const found = db.circles.find((c) => c.id === id);
    if (!found) throw new ApiError(404, 'Study circle not found');
    return found;
  };
  const roleOf = (circle: DemoCircle) => circle.members.find((m) => m.userId === viewer.id)?.role ?? null;
  const requireMember = (id: number) => {
    const circle = requireCircle(id);
    if (!roleOf(circle)) throw new ApiError(403, MEMBERS_ONLY_MESSAGE);
    return circle;
  };
  const listOf = <T,>(map: Map<number, T[]>, id: number): T[] => {
    if (!map.has(id)) map.set(id, []);
    return map.get(id)!;
  };
  const upcoming = (id: number): StudySession[] =>
    listOf(db.sessions, id)
      .filter((s) => new Date(s.endsAt).getTime() > Date.now())
      .sort((a, b) => a.scheduledAt.localeCompare(b.scheduledAt) || a.id - b.id);

  const summaryOf = (circle: DemoCircle): StudyCircleSummary => ({
    id: circle.id,
    name: circle.name,
    description: circle.description,
    course: circle.course,
    memberCount: circle.members.length,
    myRole: roleOf(circle),
    nextSessionAt: upcoming(circle.id)[0]?.scheduledAt ?? null,
    createdAt: circle.createdAt,
  });

  const detailOf = (circle: DemoCircle): StudyCircleDetail => {
    const myRole = roleOf(circle);
    const creator = circle.members.find((m) => m.userId === circle.creatorId);
    return {
      id: circle.id,
      name: circle.name,
      description: circle.description,
      course: circle.course,
      creator: creator ? { id: creator.userId, displayName: creator.displayName } : null,
      myRole,
      members: circle.members,
      nextSession: myRole ? upcoming(circle.id)[0] ?? null : null,
      resourceCount: listOf(db.resources, circle.id).length,
      createdAt: circle.createdAt,
    };
  };

  return {
    async browseCircles(query) {
      const q = query.q?.trim().toLowerCase();
      const items = db.circles
        .filter((c) => !query.mine || roleOf(c))
        .filter((c) => !query.courseOfferingId || c.course?.id === query.courseOfferingId)
        .filter((c) => !q || c.name.toLowerCase().includes(q) || (c.description ?? '').toLowerCase().includes(q)
          || (c.course?.courseCode.toLowerCase().includes(q) ?? false))
        .sort((a, b) => b.createdAt.localeCompare(a.createdAt) || b.id - a.id)
        .map(summaryOf);
      return clone({ items, page: 0, size: 50, totalItems: items.length });
    },

    async createCircle(payload) {
      const name = payload.name.trim();
      if (!name) throw new ApiError(400, 'Name is required');
      const now = new Date().toISOString();
      const circle: DemoCircle = {
        id: nextId(db.circles),
        name,
        description: payload.description?.trim() || null,
        course: null,
        creatorId: viewer.id,
        members: [{ userId: viewer.id, displayName: viewer.displayName, role: 'HOST', joinedAt: now }],
        createdAt: now,
      };
      db.circles.push(circle);
      return clone(detailOf(circle));
    },

    async getCircle(id) {
      return clone(detailOf(requireCircle(id)));
    },

    async joinCircle(id) {
      const circle = requireCircle(id);
      const existing = roleOf(circle);
      if (!existing) {
        circle.members.push({ userId: viewer.id, displayName: viewer.displayName, role: 'ATTENDEE', joinedAt: new Date().toISOString() });
      }
      return { studyCircleId: id, role: roleOf(circle)!, memberCount: circle.members.length, newlyJoined: !existing };
    },

    async listSessions(circleId) {
      requireMember(circleId);
      return clone(upcoming(circleId));
    },

    async scheduleSession(circleId, payload) {
      requireMember(circleId);
      const start = new Date(payload.scheduledAt).getTime();
      if (!payload.title.trim()) throw new ApiError(400, 'Title is required');
      if (!(start > Date.now())) throw new ApiError(400, 'Sessions must be scheduled in the future');
      if (payload.durationMinutes < 15 || payload.durationMinutes > 480) {
        throw new ApiError(400, 'A session lasts between 15 minutes and 8 hours');
      }
      const all = [...db.sessions.values()].flat();
      const created: StudySession = {
        id: nextId(all),
        title: payload.title.trim(),
        scheduledAt: new Date(start).toISOString(),
        durationMinutes: payload.durationMinutes,
        endsAt: new Date(start + payload.durationMinutes * 60_000).toISOString(),
        agenda: payload.agenda?.trim() || null,
        locationOrLink: payload.locationOrLink?.trim() || null,
        scheduledBy: viewer,
        createdAt: new Date().toISOString(),
      };
      listOf(db.sessions, circleId).push(created);
      return clone(created);
    },

    async listResources(circleId) {
      requireMember(circleId);
      return clone(listOf(db.resources, circleId));
    },

    async shareResource(circleId, payload) {
      requireMember(circleId);
      if (!payload.title.trim()) throw new ApiError(400, 'Title is required');
      if (!URL_PATTERN.test(payload.url.trim())) throw new ApiError(400, 'URL must be a valid http(s) link');
      const all = [...db.resources.values()].flat();
      const created = {
        id: nextId(all),
        title: payload.title.trim(),
        url: payload.url.trim(),
        category: payload.category,
        contributor: viewer,
        createdAt: new Date().toISOString(),
      };
      listOf(db.resources, circleId).unshift(created);
      return clone(created);
    },

    async listRevisionItems(circleId) {
      requireMember(circleId);
      return clone(listOf(db.revisionItems, circleId));
    },

    async addRevisionItem(circleId, title) {
      requireMember(circleId);
      const trimmed = title.trim();
      if (!trimmed) throw new ApiError(400, 'Title is required');
      if (trimmed.length > 200) throw new ApiError(400, 'Title must be at most 200 characters');
      const all = [...db.revisionItems.values()].flat();
      const created = {
        id: nextId(all),
        title: trimmed,
        done: false,
        createdBy: viewer,
        completedBy: null,
        completedAt: null,
        createdAt: new Date().toISOString(),
      };
      listOf(db.revisionItems, circleId).push(created);
      return clone(created);
    },

    async setRevisionItemDone(circleId, itemId, done) {
      requireMember(circleId);
      const item = listOf(db.revisionItems, circleId).find((i) => i.id === itemId);
      if (!item) throw new ApiError(404, 'Revision item not found');
      // Idempotent like the server: re-ticking keeps the first "ticked by".
      if (item.done !== done) {
        item.done = done;
        item.completedBy = done ? viewer : null;
        item.completedAt = done ? new Date().toISOString() : null;
      }
      return clone(item);
    },

    async listAnnouncements(query) {
      const size = 10;
      const page = query.page ?? 0;
      const matching = db.announcements
        .filter((a) => !query.category || a.category === query.category)
        .sort((a, b) => b.publishedAt.localeCompare(a.publishedAt) || b.id - a.id);
      return clone({
        items: matching.slice(page * size, page * size + size),
        page,
        size,
        totalItems: matching.length,
        totalPages: Math.ceil(matching.length / size),
      });
    },

    async publishAnnouncement(payload) {
      if (!roles.some((r) => ['ROLE_TEACHER', 'ROLE_MODERATOR', 'ROLE_ADMIN'].includes(r))) {
        throw new ApiError(403, 'Access denied');
      }
      if (!payload.title.trim() || !payload.body.trim() || !payload.organization.trim()) {
        throw new ApiError(400, 'Organization, title and body are required');
      }
      if (payload.sourceUrl?.trim() && !URL_PATTERN.test(payload.sourceUrl.trim())) {
        throw new ApiError(400, 'Source URL must be a valid http(s) link');
      }
      const created = {
        id: nextId(db.announcements),
        organization: payload.organization.trim(),
        category: payload.category,
        title: payload.title.trim(),
        body: payload.body.trim(),
        sourceUrl: payload.sourceUrl?.trim() || null,
        publishedAt: new Date().toISOString(),
        author: viewer,
      };
      db.announcements.unshift(created);
      return clone(created);
    },
  };
}
