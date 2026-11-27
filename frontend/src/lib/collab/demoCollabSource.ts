import { ApiError } from '../api';
import { buildDemoSeed, DEMO_PEOPLE, type DemoPlayground, type DemoSeed } from '../../fixtures/collabFixtures';
import type {
  KanbanBoard,
  OpportunityDetail,
  OpportunitySummary,
  PlaygroundDetail,
  PlaygroundRole,
  TaskCard,
  TaskStatus,
  UserRef,
} from '../../types/collab';
import { TASK_STATUSES } from '../../types/collab';
import type { CollabSource } from './collabSource';

/** Same message the Spring GlobalExceptionHandler sends with a 409. */
export const CONFLICT_MESSAGE = 'Resource was modified by another concurrent action. Please reload.';

export interface DemoCollabSource extends CollabSource {
  /**
   * Preview-only: a teammate (Arif) moves or re-assigns a card behind the viewer's back, bumping its
   * version, so the viewer's next edit of that card hits the real 409 path. Returns what changed.
   */
  simulateTeammateEdit(playgroundId: number): Promise<string>;
}

let sharedStore: DemoSeed | null = null;

function store(): DemoSeed {
  if (!sharedStore) sharedStore = buildDemoSeed();
  return sharedStore;
}

/** Test hook: start every test from the same seed. */
export function resetDemoCollabStore(nowMs?: number) {
  sharedStore = buildDemoSeed(nowMs);
}

/** Callers get copies, so UI state can never mutate the store behind its back (data is plain JSON). */
const clone = <T,>(value: T): T => JSON.parse(JSON.stringify(value)) as T;

function summaryOf(detail: OpportunityDetail, bookmarked: boolean, now: number): OpportunitySummary {
  return {
    id: detail.id,
    title: detail.title,
    category: detail.category,
    organizer: detail.organizer,
    mode: detail.mode,
    status: detail.status,
    registrationDeadline: detail.registrationDeadline,
    startDate: detail.startDate,
    endDate: detail.endDate,
    teamSizeMin: detail.teamSizeMin,
    teamSizeMax: detail.teamSizeMax,
    createdAt: detail.createdAt,
    expired: new Date(detail.registrationDeadline).getTime() <= now,
    bookmarked,
  };
}

/**
 * In-memory implementation of {@link CollabSource} for preview mode. It enforces the same rules as the
 * server (roles, archived freeze, mandatory expectedVersion with 409 on mismatch, end-of-column moves) so the
 * UI is exercised exactly as it will be against Spring.
 */
export function createDemoCollabSource(viewer: UserRef): DemoCollabSource {
  const db = store();

  const bookmarksOf = (userId: number) => {
    if (!db.bookmarks.has(userId)) db.bookmarks.set(userId, new Set());
    return db.bookmarks.get(userId)!;
  };

  const requireOpportunity = (id: number) => {
    const found = db.opportunities.find((opp) => opp.id === id);
    if (!found) throw new ApiError(404, 'Opportunity not found');
    return found;
  };

  const roleOf = (playground: DemoPlayground, userId: number): PlaygroundRole | null =>
    playground.members.find((m) => m.userId === userId)?.role ?? null;

  const requirePlayground = (id: number, permission: 'VIEW' | 'EDIT' | 'MANAGE' = 'VIEW') => {
    const playground = db.playgrounds.find((p) => p.id === id);
    if (!playground) throw new ApiError(404, 'Playground not found');
    const role = roleOf(playground, viewer.id);
    if (!role && playground.visibility === 'PRIVATE') throw new ApiError(404, 'Playground not found');
    if (permission !== 'VIEW') {
      if (!role) throw new ApiError(403, 'Only members of this playground can do that');
      if (permission === 'EDIT' && role === 'VIEWER') throw new ApiError(403, 'VIEWER members have read-only access');
      if (permission === 'MANAGE' && role !== 'LEAD') throw new ApiError(403, 'Only a playground LEAD can do that');
      if (playground.status === 'ARCHIVED') throw new ApiError(409, 'Playground is archived');
    }
    return playground;
  };

  const tasksOf = (playgroundId: number) => {
    if (!db.tasks.has(playgroundId)) db.tasks.set(playgroundId, []);
    return db.tasks.get(playgroundId)!;
  };

  const nextPosition = (playgroundId: number, status: TaskStatus) => {
    const inColumn = tasksOf(playgroundId).filter((task) => task.status === status);
    return inColumn.length === 0 ? 0 : Math.max(...inColumn.map((task) => task.position)) + 1;
  };

  const detailOf = (playground: DemoPlayground): PlaygroundDetail => {
    const counts = Object.fromEntries(TASK_STATUSES.map((status) => [status, 0])) as Record<TaskStatus, number>;
    for (const task of tasksOf(playground.id)) counts[task.status] += 1;
    const linkedOpportunities = db.opportunities
      .filter((opp) => db.links.get(opp.id)?.has(playground.id))
      .map((opp) => ({ id: opp.id, title: opp.title, category: opp.category, registrationDeadline: opp.registrationDeadline }));
    return clone({
      ...playground,
      myRole: roleOf(playground, viewer.id),
      taskCounts: counts,
      linkedOpportunities,
    });
  };

  const nextId = (ids: number[]) => (ids.length === 0 ? 1 : Math.max(...ids) + 1);

  const createPlaygroundInternal = (title: string, problemStatement: string | null, visibility: 'PUBLIC' | 'PRIVATE', tags: string[]) => {
    const now = new Date().toISOString();
    const person = DEMO_PEOPLE[viewer.id];
    const playground: DemoPlayground = {
      id: nextId(db.playgrounds.map((p) => p.id)),
      title,
      problemStatement,
      tags,
      visibility,
      status: 'PLANNING',
      repoUrl: null,
      demoUrl: null,
      ownerId: viewer.id,
      version: 0,
      members: [{
        userId: viewer.id,
        displayName: viewer.displayName,
        username: person?.username ?? `user${viewer.id}`,
        role: 'LEAD',
        joinedAt: now,
      }],
      createdAt: now,
      updatedAt: now,
    };
    db.playgrounds.push(playground);
    return playground;
  };

  const source: DemoCollabSource = {
    async browseOpportunities(query) {
      const now = Date.now();
      const mine = bookmarksOf(viewer.id);
      const needle = query.q?.trim().toLowerCase();
      let items = db.opportunities
        .filter((opp) => !query.category || opp.category === query.category)
        .filter((opp) => !needle || opp.title.toLowerCase().includes(needle) || opp.organizer.toLowerCase().includes(needle))
        .filter((opp) => !query.bookmarkedOnly || mine.has(opp.id))
        .map((opp) => summaryOf(opp, mine.has(opp.id), now));

      const strategy = query.strategy ?? 'deadline';
      items = strategy === 'recent'
        ? items.sort((a, b) => b.createdAt.localeCompare(a.createdAt) || b.id - a.id)
        : items.sort((a, b) => {
          // Same rule as DeadlineFirstStrategy: open by nearest deadline, then closed by most recently closed.
          if (a.expired !== b.expired) return a.expired ? 1 : -1;
          const byDeadline = a.registrationDeadline.localeCompare(b.registrationDeadline);
          return (a.expired ? -byDeadline : byDeadline) || a.id - b.id;
        });
      return clone({ items, page: 0, size: 50, totalItems: items.length, strategy, truncated: false });
    },

    async getOpportunity(id) {
      const opp = requireOpportunity(id);
      return clone({
        ...opp,
        expired: new Date(opp.registrationDeadline).getTime() <= Date.now(),
        bookmarked: bookmarksOf(viewer.id).has(id),
      });
    },

    async setBookmark(id, bookmarked) {
      const opp = requireOpportunity(id);
      const mine = bookmarksOf(viewer.id);
      const had = mine.has(id);
      if (bookmarked && !had) {
        mine.add(id);
        opp.bookmarkCount += 1;
      } else if (!bookmarked && had) {
        mine.delete(id);
        opp.bookmarkCount -= 1;
      }
      return { opportunityId: id, bookmarked, bookmarkCount: opp.bookmarkCount };
    },

    async startPlayground(opportunityId) {
      const opp = requireOpportunity(opportunityId);
      const title = `${opp.title.slice(0, 145).trim()} Team`;
      const playground = createPlaygroundInternal(title, opp.description, 'PUBLIC', [opp.category]);
      if (!db.links.has(opportunityId)) db.links.set(opportunityId, new Set());
      db.links.get(opportunityId)!.add(playground.id);
      return {
        opportunityId,
        opportunityTitle: opp.title,
        playground: detailOf(playground),
        playgroundCreated: true,
        newlyLinked: true,
      };
    },

    async listTeams(opportunityId) {
      requireOpportunity(opportunityId);
      const linked = db.links.get(opportunityId) ?? new Set<number>();
      return clone(db.playgrounds
        .filter((p) => linked.has(p.id))
        .filter((p) => p.visibility === 'PUBLIC' || roleOf(p, viewer.id) !== null)
        .map((p) => ({ id: p.id, title: p.title, status: p.status, visibility: p.visibility, memberCount: p.members.length, linkedAt: p.createdAt })));
    },

    async listPlaygrounds() {
      return clone(db.playgrounds
        .filter((p) => roleOf(p, viewer.id) !== null)
        .sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))
        .map((p) => ({
          id: p.id,
          title: p.title,
          status: p.status,
          visibility: p.visibility,
          myRole: roleOf(p, viewer.id)!,
          memberCount: p.members.length,
          updatedAt: p.updatedAt,
        })));
    },

    async createPlayground(payload) {
      if (!payload.title.trim()) throw new ApiError(400, 'Validation failed', { title: 'Title is required' });
      const playground = createPlaygroundInternal(
        payload.title.trim(), payload.problemStatement?.trim() || null, payload.visibility ?? 'PUBLIC', []);
      return detailOf(playground);
    },

    async getPlayground(id) {
      return detailOf(requirePlayground(id));
    },

    async getBoard(playgroundId) {
      requirePlayground(playgroundId);
      const tasks = tasksOf(playgroundId);
      const board: KanbanBoard = {
        playgroundId,
        columns: TASK_STATUSES.map((status) => ({
          status,
          tasks: tasks.filter((t) => t.status === status).sort((a, b) => a.position - b.position || a.id - b.id),
        })),
        totalTasks: tasks.length,
      };
      return clone(board);
    },

    async createTask(playgroundId, payload) {
      const playground = requirePlayground(playgroundId, 'EDIT');
      if (!payload.title.trim()) throw new ApiError(400, 'Validation failed', { title: 'Title is required' });
      const assignee = payload.assigneeId ? playground.members.find((m) => m.userId === payload.assigneeId) : undefined;
      if (payload.assigneeId && (!assignee || assignee.role === 'VIEWER')) {
        throw new ApiError(400, 'Assignee must be a LEAD or CONTRIBUTOR of this playground');
      }
      const status = payload.status ?? 'BACKLOG';
      const card: TaskCard = {
        id: nextId([...db.tasks.values()].flat().map((t) => t.id)),
        title: payload.title.trim(),
        status,
        priority: payload.priority ?? 'MEDIUM',
        position: nextPosition(playgroundId, status),
        dueDate: payload.dueDate ?? null,
        assignee: assignee ? { id: assignee.userId, displayName: assignee.displayName } : null,
        version: 0,
      };
      tasksOf(playgroundId).push(card);
      return clone(card);
    },

    async updateTask(playgroundId, taskId, payload) {
      const playground = requirePlayground(playgroundId, 'EDIT');
      const task = tasksOf(playgroundId).find((t) => t.id === taskId);
      if (!task) throw new ApiError(404, 'Task not found');
      if (task.version !== payload.expectedVersion) throw new ApiError(409, CONFLICT_MESSAGE);

      if (payload.assigneeId !== undefined) {
        const assignee = playground.members.find((m) => m.userId === payload.assigneeId);
        if (!assignee || assignee.role === 'VIEWER') {
          throw new ApiError(400, 'Assignee must be a LEAD or CONTRIBUTOR of this playground');
        }
        task.assignee = { id: assignee.userId, displayName: assignee.displayName };
      }
      if (payload.clearAssignee) task.assignee = null;
      if (payload.priority) task.priority = payload.priority;
      if (payload.status && payload.status !== task.status) {
        task.position = nextPosition(playgroundId, payload.status);
        task.status = payload.status;
      }
      task.version += 1;
      playground.updatedAt = new Date().toISOString();
      return clone(task);
    },

    async listMilestones(playgroundId) {
      requirePlayground(playgroundId);
      const now = Date.now();
      return clone((db.milestones.get(playgroundId) ?? [])
        .map((m) => ({ ...m, overdue: m.status === 'PENDING' && new Date(m.dueDate).getTime() < now }))
        .sort((a, b) => a.dueDate.localeCompare(b.dueDate) || a.id - b.id));
    },

    async createMilestone(playgroundId, payload) {
      requirePlayground(playgroundId, 'EDIT');
      if (!db.milestones.has(playgroundId)) db.milestones.set(playgroundId, []);
      const list = db.milestones.get(playgroundId)!;
      const milestone = {
        id: nextId([...db.milestones.values()].flat().map((m) => m.id)),
        title: payload.title.trim(),
        description: payload.description?.trim() || null,
        dueDate: payload.dueDate,
        status: 'PENDING' as const,
        overdue: new Date(payload.dueDate).getTime() < Date.now(),
        createdBy: viewer.id,
        createdAt: new Date().toISOString(),
        completedAt: null,
        completedBy: null,
      };
      list.push(milestone);
      return clone(milestone);
    },

    async setMilestoneStatus(playgroundId, milestoneId, status) {
      requirePlayground(playgroundId, 'EDIT');
      const milestone = (db.milestones.get(playgroundId) ?? []).find((m) => m.id === milestoneId);
      if (!milestone) throw new ApiError(404, 'Milestone not found');
      if (status === 'COMPLETED' && milestone.status === 'PENDING') {
        milestone.status = 'COMPLETED';
        milestone.completedAt = new Date().toISOString();
        milestone.completedBy = viewer.id;
      } else if (status === 'PENDING' && milestone.status === 'COMPLETED') {
        milestone.status = 'PENDING';
        milestone.completedAt = null;
        milestone.completedBy = null;
      }
      milestone.overdue = milestone.status === 'PENDING' && new Date(milestone.dueDate).getTime() < Date.now();
      return clone(milestone);
    },

    async listDecisions(playgroundId) {
      requirePlayground(playgroundId);
      return clone([...(db.decisions.get(playgroundId) ?? [])]
        .sort((a, b) => b.createdAt.localeCompare(a.createdAt) || b.id - a.id));
    },

    async recordDecision(playgroundId, payload) {
      requirePlayground(playgroundId, 'EDIT');
      if (!payload.decision.trim()) throw new ApiError(400, 'Validation failed', { decision: 'Decision is required' });
      if (!db.decisions.has(playgroundId)) db.decisions.set(playgroundId, []);
      const decision = {
        id: nextId([...db.decisions.values()].flat().map((d) => d.id)),
        decision: payload.decision.trim(),
        rationale: payload.rationale?.trim() || null,
        author: { id: viewer.id, displayName: viewer.displayName },
        createdAt: new Date().toISOString(),
      };
      db.decisions.get(playgroundId)!.push(decision);
      return clone(decision);
    },

    async simulateTeammateEdit(playgroundId) {
      const tasks = tasksOf(playgroundId);
      const target = tasks.find((t) => t.status !== 'DONE') ?? tasks[0];
      if (!target) return 'There are no cards for a teammate to edit yet.';
      const teammate = DEMO_PEOPLE[21];
      const order: TaskStatus[] = [...TASK_STATUSES];
      const next = order[Math.min(order.indexOf(target.status) + 1, order.length - 1)];
      target.position = nextPosition(playgroundId, next);
      target.status = next;
      target.assignee = { id: teammate.id, displayName: teammate.displayName };
      target.version += 1;
      return `${teammate.displayName} moved "${target.title}" without you seeing it. Your next edit of that card will conflict.`;
    },
  };

  return source;
}
