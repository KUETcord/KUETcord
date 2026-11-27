/**
 * Contracts for Member C's collaboration APIs (Contest Board, Playgrounds, Kanban).
 * Field names mirror the Spring DTOs in com.kuetcord.opportunities / com.kuetcord.playgrounds.
 * All timestamps are ISO-8601 UTC strings.
 */

export interface UserRef {
  id: number;
  displayName: string;
}

// ---------- Contest & Challenge Board ----------

export const OPPORTUNITY_CATEGORIES = [
  'PROGRAMMING_CONTEST',
  'HACKATHON',
  'ROBOTICS',
  'RESEARCH',
  'CASE_COMPETITION',
] as const;
export type OpportunityCategory = (typeof OPPORTUNITY_CATEGORIES)[number];

export type OpportunityMode = 'ONLINE' | 'ON_SITE' | 'HYBRID';
export type VerificationStatus = 'UNVERIFIED' | 'VERIFIED';
export type RankingStrategy = 'deadline' | 'recent';

export interface OpportunitySummary {
  id: number;
  title: string;
  category: OpportunityCategory;
  organizer: string;
  mode: OpportunityMode;
  status: VerificationStatus;
  registrationDeadline: string;
  startDate: string;
  endDate: string;
  teamSizeMin: number;
  teamSizeMax: number;
  createdAt: string;
  expired: boolean;
  bookmarked: boolean;
}

export interface OpportunityDetail extends OpportunitySummary {
  sourceUrl: string;
  eligibility: string | null;
  description: string | null;
  submittedBy: UserRef | null;
  /** Moderator user id (the backend stores the id only). */
  verifiedBy: number | null;
  verifiedAt: string | null;
  updatedAt: string;
  bookmarkCount: number;
}

export interface OpportunityPage {
  items: OpportunitySummary[];
  page: number;
  size: number;
  totalItems: number;
  strategy: RankingStrategy;
  truncated: boolean;
}

export interface OpportunityQuery {
  category?: OpportunityCategory;
  q?: string;
  bookmarkedOnly?: boolean;
  strategy?: RankingStrategy;
}

export interface BookmarkStatus {
  opportunityId: number;
  bookmarked: boolean;
  bookmarkCount: number;
}

// ---------- Project Playgrounds ----------

export type PlaygroundRole = 'LEAD' | 'CONTRIBUTOR' | 'VIEWER';
export type PlaygroundVisibility = 'PUBLIC' | 'PRIVATE';
export type PlaygroundStatus = 'PLANNING' | 'IN_PROGRESS' | 'COMPLETED' | 'ARCHIVED';

export const TASK_STATUSES = ['BACKLOG', 'TODO', 'IN_PROGRESS', 'REVIEW', 'DONE'] as const;
export type TaskStatus = (typeof TASK_STATUSES)[number];

export const TASK_PRIORITIES = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'] as const;
export type TaskPriority = (typeof TASK_PRIORITIES)[number];

export interface PlaygroundSummary {
  id: number;
  title: string;
  status: PlaygroundStatus;
  visibility: PlaygroundVisibility;
  myRole: PlaygroundRole;
  memberCount: number;
  updatedAt: string;
}

export interface PlaygroundMember {
  userId: number;
  displayName: string;
  username: string;
  role: PlaygroundRole;
  joinedAt: string;
}

export interface LinkedOpportunity {
  id: number;
  title: string;
  category: OpportunityCategory;
  registrationDeadline: string;
}

export interface PlaygroundDetail {
  id: number;
  title: string;
  problemStatement: string | null;
  tags: string[];
  visibility: PlaygroundVisibility;
  status: PlaygroundStatus;
  repoUrl: string | null;
  demoUrl: string | null;
  ownerId: number;
  myRole: PlaygroundRole | null;
  version: number;
  members: PlaygroundMember[];
  taskCounts: Record<TaskStatus, number>;
  linkedOpportunities: LinkedOpportunity[];
  createdAt: string;
  updatedAt: string;
}

export interface CreatePlaygroundPayload {
  title: string;
  problemStatement?: string;
  visibility?: PlaygroundVisibility;
}

export interface TaskCard {
  id: number;
  title: string;
  status: TaskStatus;
  priority: TaskPriority;
  position: number;
  dueDate: string | null;
  assignee: UserRef | null;
  version: number;
}

export interface KanbanColumn {
  status: TaskStatus;
  tasks: TaskCard[];
}

export interface KanbanBoard {
  playgroundId: number;
  columns: KanbanColumn[];
  totalTasks: number;
}

export interface CreateTaskPayload {
  title: string;
  status?: TaskStatus;
  priority?: TaskPriority;
  assigneeId?: number | null;
  dueDate?: string | null;
}

/** PATCH body; `expectedVersion` is mandatory (stale -> HTTP 409). */
export interface UpdateTaskPayload {
  status?: TaskStatus;
  priority?: TaskPriority;
  assigneeId?: number;
  clearAssignee?: boolean;
  expectedVersion: number;
}

export type MilestoneStatus = 'PENDING' | 'COMPLETED';

export interface Milestone {
  id: number;
  title: string;
  description: string | null;
  dueDate: string;
  status: MilestoneStatus;
  overdue: boolean;
  createdBy: number | null;
  createdAt: string;
  completedAt: string | null;
  completedBy: number | null;
}

export interface Decision {
  id: number;
  decision: string;
  rationale: string | null;
  author: UserRef | null;
  createdAt: string;
}

export interface OpportunityPlaygroundLink {
  opportunityId: number;
  opportunityTitle: string;
  playground: PlaygroundDetail;
  playgroundCreated: boolean;
  newlyLinked: boolean;
}

export interface LinkedPlayground {
  id: number;
  title: string;
  status: PlaygroundStatus;
  visibility: PlaygroundVisibility;
  memberCount: number;
  linkedAt: string;
}
