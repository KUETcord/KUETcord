import type {
  Decision,
  Milestone,
  OpportunityDetail,
  PlaygroundMember,
  PlaygroundRole,
  PlaygroundStatus,
  PlaygroundVisibility,
  TaskCard,
  UserRef,
} from '../types/collab';

/** Demo people. Ids 2, 3 and 10 match the AppShell preview roles (CR, Student, Teacher). */
export const DEMO_PEOPLE: Record<number, UserRef & { username: string }> = {
  2: { id: 2, displayName: 'Karim Rahman', username: 'karim.cr' },
  3: { id: 3, displayName: 'Sadia Afrin', username: 'sadia.student' },
  10: { id: 10, displayName: 'Prof. Dr. Hasan Mahmud', username: 'prof.hasan' },
  21: { id: 21, displayName: 'Arif Hossain', username: 'arif.h' },
  22: { id: 22, displayName: 'Nusrat Jahan', username: 'nusrat.j' },
};

export interface DemoPlayground {
  id: number;
  title: string;
  problemStatement: string | null;
  tags: string[];
  visibility: PlaygroundVisibility;
  status: PlaygroundStatus;
  repoUrl: string | null;
  demoUrl: string | null;
  ownerId: number;
  version: number;
  members: PlaygroundMember[];
  createdAt: string;
  updatedAt: string;
}

export interface DemoSeed {
  opportunities: OpportunityDetail[];
  bookmarks: Map<number, Set<number>>;
  playgrounds: DemoPlayground[];
  tasks: Map<number, TaskCard[]>;
  milestones: Map<number, Milestone[]>;
  decisions: Map<number, Decision[]>;
  links: Map<number, Set<number>>;
}

const day = 24 * 60 * 60 * 1000;
const hour = 60 * 60 * 1000;

function iso(base: number, offsetMs: number): string {
  return new Date(base + offsetMs).toISOString();
}

function member(userId: number, role: PlaygroundRole, joinedAt: string): PlaygroundMember {
  const person = DEMO_PEOPLE[userId];
  return { userId, displayName: person.displayName, username: person.username, role, joinedAt };
}

function opportunity(
  now: number,
  id: number,
  fields: Partial<OpportunityDetail> & Pick<OpportunityDetail, 'title' | 'category' | 'organizer'>,
  deadlineInMs: number,
  createdAgoMs: number,
): OpportunityDetail {
  const deadline = iso(now, deadlineInMs);
  return {
    id,
    mode: 'ONLINE',
    status: 'VERIFIED',
    registrationDeadline: deadline,
    startDate: iso(now, deadlineInMs + 3 * day),
    endDate: iso(now, deadlineInMs + 4 * day),
    teamSizeMin: 1,
    teamSizeMax: 3,
    createdAt: iso(now, -createdAgoMs),
    updatedAt: iso(now, -createdAgoMs),
    expired: deadlineInMs <= 0,
    bookmarked: false,
    sourceUrl: 'https://www.kuet.ac.bd',
    eligibility: 'Undergraduate students of all departments',
    description: null,
    submittedBy: { id: 21, displayName: DEMO_PEOPLE[21].displayName },
    verifiedBy: 10,
    verifiedAt: iso(now, -createdAgoMs + hour),
    bookmarkCount: 0,
    ...fields,
  };
}

/**
 * Deterministic demo data whose dates are relative to `nowMs`, so deadline badges always look realistic.
 * Deadlines sit mid-day (+6h) so a badge never flips between "2 days" and "1 day" while a demo runs.
 */
export function buildDemoSeed(nowMs: number = Date.now()): DemoSeed {
  const opportunities: OpportunityDetail[] = [
    opportunity(nowMs, 101, {
      title: 'ICPC Asia Dhaka Regional 2026',
      category: 'PROGRAMMING_CONTEST',
      organizer: 'ICPC Bangladesh',
      mode: 'ON_SITE',
      sourceUrl: 'https://icpc.global',
      description: 'Three-person teams solve 10-13 algorithmic problems in five hours.',
      teamSizeMin: 3,
      teamSizeMax: 3,
      bookmarkCount: 14,
    }, 2 * day + 6 * hour, 9 * day),
    opportunity(nowMs, 102, {
      title: 'KUET Hackathon 2026: Tech for Impact',
      category: 'HACKATHON',
      organizer: 'KUET Computer Club',
      mode: 'ON_SITE',
      sourceUrl: 'https://hackathon.kuet.ac.bd',
      description: '48-hour build sprint addressing campus sustainability.',
      eligibility: 'Undergraduate students, all departments',
      teamSizeMin: 3,
      teamSizeMax: 5,
      bookmarkCount: 31,
    }, 10 * day, 2 * day),
    opportunity(nowMs, 103, {
      title: 'National Robotics Olympiad: Autonomous Line Follower',
      category: 'ROBOTICS',
      organizer: 'Bangladesh Robotics Society',
      mode: 'HYBRID',
      teamSizeMin: 2,
      teamSizeMax: 4,
      bookmarkCount: 6,
    }, 5 * day + 6 * hour, 4 * day),
    opportunity(nowMs, 104, {
      title: 'Undergraduate Research Grant: Applied Machine Learning',
      category: 'RESEARCH',
      organizer: 'KUET Research & Extension',
      teamSizeMin: 1,
      teamSizeMax: 2,
      status: 'UNVERIFIED',
      verifiedBy: null,
      verifiedAt: null,
    }, 30 * day, 1 * day),
    opportunity(nowMs, 105, {
      title: 'Hult Prize Campus Round',
      category: 'CASE_COMPETITION',
      organizer: 'Hult Prize at KUET',
      teamSizeMin: 3,
      teamSizeMax: 4,
      bookmarkCount: 9,
    }, -3 * day, 20 * day),
  ];

  const created = iso(nowMs, -6 * day);
  const playgrounds: DemoPlayground[] = [
    {
      id: 1,
      title: 'Team KUETcord: Campus Workflow System',
      problemStatement: 'Unifying CT schedules, competitions, and peer collaboration for KUET students.',
      tags: ['Spring Boot', 'React', 'PostgreSQL', 'WebSocket'],
      visibility: 'PUBLIC',
      status: 'IN_PROGRESS',
      repoUrl: 'https://github.com/kuetcord/kuetcord',
      demoUrl: null,
      ownerId: 2,
      version: 3,
      members: [
        member(2, 'LEAD', created),
        member(3, 'CONTRIBUTOR', created),
        member(21, 'CONTRIBUTOR', iso(nowMs, -5 * day)),
        member(10, 'VIEWER', iso(nowMs, -4 * day)),
      ],
      createdAt: created,
      updatedAt: iso(nowMs, -2 * hour),
    },
  ];

  const ref = (id: number) => ({ id, displayName: DEMO_PEOPLE[id].displayName });
  const tasks = new Map<number, TaskCard[]>([
    [1, [
      { id: 1, title: 'Author k6 load test suite for 50 concurrent VUs', status: 'BACKLOG', priority: 'MEDIUM', position: 0, dueDate: iso(nowMs, 5 * day), assignee: ref(2), version: 0 },
      { id: 2, title: 'Write ADR for transactional outbox', status: 'BACKLOG', priority: 'LOW', position: 1, dueDate: null, assignee: null, version: 0 },
      { id: 3, title: 'Integrate JPA @Version concurrency on Kanban cards', status: 'TODO', priority: 'HIGH', position: 0, dueDate: iso(nowMs, 3 * day), assignee: ref(3), version: 0 },
      { id: 4, title: 'Build reconnect /sync catch-up protocol', status: 'IN_PROGRESS', priority: 'HIGH', position: 0, dueDate: iso(nowMs, 2 * day), assignee: ref(21), version: 2 },
      { id: 5, title: 'Implement STOMP heartbeat dead-socket reaper', status: 'DONE', priority: 'CRITICAL', position: 0, dueDate: iso(nowMs, -1 * day), assignee: ref(21), version: 4 },
    ]],
  ]);

  const milestones = new Map<number, Milestone[]>([
    [1, [
      { id: 1, title: 'Proposal submitted', description: 'Abstract and team list sent to the organizers.', dueDate: iso(nowMs, -4 * day), status: 'COMPLETED', overdue: false, createdBy: 2, createdAt: created, completedAt: iso(nowMs, -4 * day - 2 * hour), completedBy: 2 },
      { id: 2, title: 'Backend MVP', description: 'Auth, CT Hub, messaging and Kanban APIs green in CI.', dueDate: iso(nowMs, -1 * day), status: 'PENDING', overdue: true, createdBy: 2, createdAt: created, completedAt: null, completedBy: null },
      { id: 3, title: 'Final demo', description: null, dueDate: iso(nowMs, 12 * day), status: 'PENDING', overdue: false, createdBy: 3, createdAt: created, completedAt: null, completedBy: null },
    ]],
  ]);

  const decisions = new Map<number, Decision[]>([
    [1, [
      { id: 2, decision: 'Adopt @dnd-kit for drag and drop', rationale: 'Accessible keyboard sensors and transform-only animation keep drags at 60fps.', author: ref(3), createdAt: iso(nowMs, -1 * day) },
      { id: 1, decision: 'Use PostgreSQL 16 as the single datastore', rationale: 'JSONB for outbox payloads, SKIP LOCKED for workers, and strong CHECK constraints.', author: ref(2), createdAt: iso(nowMs, -5 * day) },
    ]],
  ]);

  return {
    opportunities,
    bookmarks: new Map([[2, new Set([102])]]),
    playgrounds,
    tasks,
    milestones,
    decisions,
    links: new Map([[102, new Set([1])]]),
  };
}
