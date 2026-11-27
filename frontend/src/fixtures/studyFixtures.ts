import type {
  Announcement,
  CircleMember,
  CourseRef,
  RevisionItem,
  StudyCircleRole,
  StudyResource,
  StudySession,
} from '../types/study';
import { DEMO_PEOPLE } from './collabFixtures';

export interface DemoCircle {
  id: number;
  name: string;
  description: string | null;
  course: CourseRef | null;
  creatorId: number;
  members: CircleMember[];
  createdAt: string;
}

export interface DemoStudySeed {
  circles: DemoCircle[];
  sessions: Map<number, StudySession[]>;
  resources: Map<number, StudyResource[]>;
  revisionItems: Map<number, RevisionItem[]>;
  announcements: Announcement[];
}

const minute = 60 * 1000;
const hour = 60 * minute;
const day = 24 * hour;

const iso = (ms: number) => new Date(ms).toISOString();
const ref = (id: number) => ({ id, displayName: DEMO_PEOPLE[id].displayName });

function member(userId: number, role: StudyCircleRole, joinedAt: string): CircleMember {
  return { userId, displayName: DEMO_PEOPLE[userId].displayName, role, joinedAt };
}

/** 18:00 local time `days` from now, so session cards show realistic evening slots. */
function eveningIn(nowMs: number, days: number): number {
  const date = new Date(nowMs + days * day);
  date.setHours(18, 0, 0, 0);
  return date.getTime();
}

function session(id: number, title: string, startMs: number, durationMinutes: number, by: number,
  locationOrLink: string | null, agenda: string | null, createdMs: number): StudySession {
  return {
    id,
    title,
    scheduledAt: iso(startMs),
    durationMinutes,
    endsAt: iso(startMs + durationMinutes * minute),
    agenda,
    locationOrLink,
    scheduledBy: ref(by),
    createdAt: iso(createdMs),
  };
}

/**
 * Preview data for Study Circles and Campus Pulse, relative to `nowMs`. The CR preview user (Karim, id 2)
 * hosts circle 1 and is not in circle 2, so joining can be demonstrated; the Teacher (id 10) is in none.
 */
export function buildStudySeed(nowMs: number = Date.now()): DemoStudySeed {
  // Ids and codes match the CT Hub demo courses, so a CT card's Study Circles link lands on a real circle.
  const algorithms: CourseRef = { id: 102, courseCode: 'CSE 3103', courseTitle: 'Algorithms & Graph Theory', semester: '3-2', academicYear: '2026' };
  const software: CourseRef = { id: 101, courseCode: 'CSE 3201', courseTitle: 'Software Engineering & Design Patterns', semester: '3-2', academicYear: '2026' };

  const circles: DemoCircle[] = [
    {
      id: 1,
      name: 'CT-2 Graph Algorithms Sprint',
      description: 'Shortest paths, MSTs and flows before CT-2. Two problems a night, then we compare solutions.',
      course: algorithms,
      creatorId: 2,
      members: [member(2, 'HOST', iso(nowMs - 8 * day)), member(3, 'ATTENDEE', iso(nowMs - 7 * day)), member(21, 'ATTENDEE', iso(nowMs - 6 * day))],
      createdAt: iso(nowMs - 8 * day),
    },
    {
      id: 2,
      name: 'Design Patterns Case Drill',
      description: 'One GoF pattern per session, applied to a past CT case study.',
      course: software,
      creatorId: 3,
      members: [member(3, 'HOST', iso(nowMs - 5 * day)), member(22, 'ATTENDEE', iso(nowMs - 4 * day))],
      createdAt: iso(nowMs - 5 * day),
    },
    {
      id: 3,
      name: 'Digital Electronics Lab Prep',
      description: null,
      course: null,
      creatorId: 21,
      members: [member(21, 'HOST', iso(nowMs - 2 * day))],
      createdAt: iso(nowMs - 2 * day),
    },
  ];

  const sessions = new Map<number, StudySession[]>([
    [1, [
      session(1, 'Dijkstra and Bellman-Ford walkthrough', eveningIn(nowMs, 1), 90, 2, 'Room 302, CSE Building',
        'Trace both on the CT-1 graph, then solve two UVa problems.', nowMs - 3 * day),
      session(2, 'MST mock CT (timed)', eveningIn(nowMs, 4), 60, 3, 'https://meet.google.com/kuet-cse-3103', null, nowMs - 1 * day),
    ]],
    [2, [
      session(3, 'Strategy vs State on the CT-1 case', eveningIn(nowMs, 2), 75, 3, 'Central Library, 2nd floor', null, nowMs - 2 * day),
    ]],
    [3, []],
  ]);

  const resources = new Map<number, StudyResource[]>([
    [1, [
      { id: 2, title: 'CP-Algorithms: Dijkstra on sparse graphs', url: 'https://cp-algorithms.com/graph/dijkstra_sparse.html', category: 'NOTES', contributor: ref(3), createdAt: iso(nowMs - 2 * day) },
      { id: 1, title: 'CSE 3103 CT-1 question paper (2025)', url: 'https://drive.google.com/file/d/kuet-cse3103-ct1', category: 'QUESTION_BANK', contributor: ref(2), createdAt: iso(nowMs - 6 * day) },
    ]],
    [2, [
      { id: 3, title: 'Refactoring.Guru: Strategy pattern', url: 'https://refactoring.guru/design-patterns/strategy', category: 'NOTES', contributor: ref(3), createdAt: iso(nowMs - 4 * day) },
    ]],
    [3, []],
  ]);

  const revisionItems = new Map<number, RevisionItem[]>([
    [1, [
      { id: 1, title: 'BFS / DFS and topological sort', done: true, createdBy: ref(2), completedBy: ref(3), completedAt: iso(nowMs - 2 * day), createdAt: iso(nowMs - 7 * day) },
      { id: 2, title: 'Dijkstra with a binary heap', done: false, createdBy: ref(2), completedBy: null, completedAt: null, createdAt: iso(nowMs - 7 * day + minute) },
      { id: 3, title: 'Bellman-Ford and negative cycles', done: false, createdBy: ref(3), completedBy: null, completedAt: null, createdAt: iso(nowMs - 6 * day) },
      { id: 4, title: "Kruskal vs Prim: when each wins", done: false, createdBy: ref(21), completedBy: null, completedAt: null, createdAt: iso(nowMs - 5 * day) },
    ]],
    [2, [
      { id: 5, title: 'Observer and the transactional outbox', done: false, createdBy: ref(3), completedBy: null, completedAt: null, createdAt: iso(nowMs - 4 * day) },
    ]],
    [3, []],
  ]);

  const announcements: Announcement[] = [
    { id: 6, organization: 'KUET Computer Club', category: 'CLUB', title: 'Intra-KUET Programming Contest: registration open',
      body: 'Teams of three, all departments welcome. The contest runs on the club judge with ICPC rules. Register at the club room or online before Thursday.',
      sourceUrl: 'https://kuet.ac.bd', publishedAt: iso(nowMs - 2 * hour), author: ref(10) },
    { id: 5, organization: 'Department of CSE', category: 'ACADEMIC', title: 'CSE 3103 CT-2 moved to Sunday, 10:00',
      body: 'The second class test for Algorithms & Graph Theory is rescheduled to Sunday at 10:00 in Room 302. Syllabus: graph algorithms (lectures 9-14).',
      sourceUrl: null, publishedAt: iso(nowMs - 9 * hour), author: ref(10) },
    { id: 4, organization: 'IEEE KUET Student Branch', category: 'SEMINAR', title: 'Seminar: Edge AI on low-power microcontrollers',
      body: 'A talk on deploying quantized neural networks to microcontrollers, with a live demo. Venue: Seminar Room 1. Certificates for attendees.',
      sourceUrl: 'https://ieee.org', publishedAt: iso(nowMs - 1 * day), author: ref(10) },
    { id: 3, organization: 'Career Development Cell', category: 'CAREER', title: 'Internship fair with 12 software companies',
      body: 'Bring a one-page CV. Final and pre-final year students get priority interview slots.',
      sourceUrl: null, publishedAt: iso(nowMs - 2 * day), author: ref(10) },
    { id: 2, organization: 'KUET Robotics Society', category: 'CLUB', title: 'Line-follower workshop for first years',
      body: 'Hands-on session: build and tune a PID line follower. Kits are provided; seats are limited to 40.',
      sourceUrl: null, publishedAt: iso(nowMs - 3 * day), author: ref(10) },
    { id: 1, organization: 'Department of EEE', category: 'SEMINAR', title: 'Power systems colloquium',
      body: 'Monthly colloquium on smart grid reliability. Open to all departments.',
      sourceUrl: null, publishedAt: iso(nowMs - 6 * day), author: ref(10) },
  ];

  return { circles, sessions, resources, revisionItems, announcements };
}
