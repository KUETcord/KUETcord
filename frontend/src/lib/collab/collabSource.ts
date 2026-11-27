import { request } from '../api';
import type {
  BookmarkStatus,
  CreatePlaygroundPayload,
  CreateTaskPayload,
  Decision,
  KanbanBoard,
  LinkedPlayground,
  Milestone,
  MilestoneStatus,
  OpportunityDetail,
  OpportunityPage,
  OpportunityPlaygroundLink,
  OpportunityQuery,
  PlaygroundDetail,
  PlaygroundSummary,
  TaskCard,
  UpdateTaskPayload,
} from '../../types/collab';

/**
 * Everything the Contest Board and Playground screens need. Pages depend on this interface only, so
 * the same components run against the Spring API (signed in) or the in-memory demo store (preview mode).
 * Failures surface as {@link ApiError} in both implementations, including HTTP 409 on a stale version.
 */
export interface CollabSource {
  browseOpportunities(query: OpportunityQuery): Promise<OpportunityPage>;
  getOpportunity(id: number): Promise<OpportunityDetail>;
  setBookmark(id: number, bookmarked: boolean): Promise<BookmarkStatus>;
  startPlayground(opportunityId: number): Promise<OpportunityPlaygroundLink>;
  listTeams(opportunityId: number): Promise<LinkedPlayground[]>;

  listPlaygrounds(): Promise<PlaygroundSummary[]>;
  createPlayground(payload: CreatePlaygroundPayload): Promise<PlaygroundDetail>;
  getPlayground(id: number): Promise<PlaygroundDetail>;

  getBoard(playgroundId: number): Promise<KanbanBoard>;
  createTask(playgroundId: number, payload: CreateTaskPayload): Promise<TaskCard>;
  updateTask(playgroundId: number, taskId: number, payload: UpdateTaskPayload): Promise<TaskCard>;

  listMilestones(playgroundId: number): Promise<Milestone[]>;
  createMilestone(playgroundId: number, payload: { title: string; dueDate: string; description?: string }): Promise<Milestone>;
  setMilestoneStatus(playgroundId: number, milestoneId: number, status: MilestoneStatus): Promise<Milestone>;

  listDecisions(playgroundId: number): Promise<Decision[]>;
  recordDecision(playgroundId: number, payload: { decision: string; rationale?: string }): Promise<Decision>;
}

function json(body: unknown): RequestInit {
  return { body: JSON.stringify(body) };
}

/** The task endpoints return the full TaskDto; the board only needs the card fields. */
function toCard(task: TaskCard): TaskCard {
  return {
    id: task.id,
    title: task.title,
    status: task.status,
    priority: task.priority,
    position: task.position,
    dueDate: task.dueDate,
    assignee: task.assignee,
    version: task.version,
  };
}

export const liveCollabSource: CollabSource = {
  browseOpportunities(query) {
    const qs = new URLSearchParams();
    if (query.category) qs.set('category', query.category);
    if (query.q?.trim()) qs.set('q', query.q.trim());
    if (query.bookmarkedOnly) qs.set('bookmarkedOnly', 'true');
    qs.set('strategy', query.strategy ?? 'deadline');
    qs.set('size', '50');
    return request<OpportunityPage>(`/opportunities?${qs.toString()}`);
  },
  getOpportunity: (id) => request<OpportunityDetail>(`/opportunities/${id}`),
  setBookmark: (id, bookmarked) =>
    request<BookmarkStatus>(`/opportunities/${id}/bookmark`, { method: bookmarked ? 'POST' : 'DELETE' }),
  startPlayground: (opportunityId) =>
    request<OpportunityPlaygroundLink>(`/opportunities/${opportunityId}/playgrounds`, { method: 'POST', ...json({}) }),
  listTeams: (opportunityId) => request<LinkedPlayground[]>(`/opportunities/${opportunityId}/playgrounds`),

  listPlaygrounds: () => request<PlaygroundSummary[]>('/playgrounds'),
  createPlayground: (payload) => request<PlaygroundDetail>('/playgrounds', { method: 'POST', ...json(payload) }),
  getPlayground: (id) => request<PlaygroundDetail>(`/playgrounds/${id}`),

  getBoard: (playgroundId) => request<KanbanBoard>(`/playgrounds/${playgroundId}/tasks`),
  async createTask(playgroundId, payload) {
    return toCard(await request<TaskCard>(`/playgrounds/${playgroundId}/tasks`, { method: 'POST', ...json(payload) }));
  },
  async updateTask(playgroundId, taskId, payload) {
    return toCard(
      await request<TaskCard>(`/playgrounds/${playgroundId}/tasks/${taskId}`, { method: 'PATCH', ...json(payload) }),
    );
  },

  listMilestones: (playgroundId) => request<Milestone[]>(`/playgrounds/${playgroundId}/milestones`),
  createMilestone: (playgroundId, payload) =>
    request<Milestone>(`/playgrounds/${playgroundId}/milestones`, { method: 'POST', ...json(payload) }),
  setMilestoneStatus: (playgroundId, milestoneId, status) =>
    request<Milestone>(`/playgrounds/${playgroundId}/milestones/${milestoneId}`, { method: 'PATCH', ...json({ status }) }),

  listDecisions: (playgroundId) => request<Decision[]>(`/playgrounds/${playgroundId}/decisions`),
  recordDecision: (playgroundId, payload) =>
    request<Decision>(`/playgrounds/${playgroundId}/decisions`, { method: 'POST', ...json(payload) }),
};
