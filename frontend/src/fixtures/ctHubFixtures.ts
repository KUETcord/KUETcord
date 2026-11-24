import type { CourseOffering, CtEventSummary, UserSummary } from '../types/domain';

export type DataFixtureMode = 'demo' | 'worst' | 'empty' | 'one';

export const DEMO_CR_USER: UserSummary = {
  id: 2,
  email: 'karim.cr@stud.kuet.ac.bd',
  displayName: 'Karim Rahman',
  username: 'karim.cr',
  globalRole: 'CR',
  roles: ['ROLE_STUDENT', 'ROLE_CR'],
  department: 'CSE',
  batch: '2k20',
  emailVerified: true,
  status: 'ACTIVE',
};

export const DEMO_STUDENT_USER: UserSummary = {
  id: 3,
  email: 'sadia.student@stud.kuet.ac.bd',
  displayName: 'Sadia Afrin',
  username: 'sadia.student',
  globalRole: 'STUDENT',
  roles: ['ROLE_STUDENT'],
  department: 'CSE',
  batch: '2k20',
  emailVerified: true,
  status: 'ACTIVE',
};

export const WORST_CASE_USER: UserSummary = {
  id: 99,
  email: 'bartholomew.fitzgerald.2007099@stud.kuet.ac.bd',
  displayName: 'Aleksandra Wiśniewska-Kowalczyk',
  username: 'aleksandra.wk',
  globalRole: 'CR',
  roles: ['ROLE_STUDENT', 'ROLE_CR'],
  department: 'CSE',
  batch: '2k20',
  emailVerified: true,
  status: 'ACTIVE',
};

export const DEMO_COURSES: CourseOffering[] = [
  {
    id: 101,
    courseCode: 'CSE 3201',
    courseTitle: 'Software Engineering & Design Patterns',
    department: 'CSE',
    semester: '3-2',
    academicYear: '2026',
    followed: true,
  },
  {
    id: 102,
    courseCode: 'CSE 3103',
    courseTitle: 'Algorithms & Graph Theory',
    department: 'CSE',
    semester: '3-2',
    academicYear: '2026',
    followed: true,
  },
  {
    id: 103,
    courseCode: 'EEE 3110',
    courseTitle: 'Digital Signal Processing Fundamentals',
    department: 'EEE',
    semester: '3-1',
    academicYear: '2026',
    followed: false,
  },
];

export const DEMO_CT_EVENTS: CtEventSummary[] = [
  {
    id: 501,
    courseOfferingId: 101,
    courseCode: 'CSE 3201',
    courseTitle: 'Software Engineering & Design Patterns',
    title: 'CT 2: State Pattern, Optimistic Locking & Outbox',
    scheduledAt: '2026-11-27T04:00:00Z',
    durationMinutes: 45,
    coveredTopics: 'State Pattern transitions, JPA @Version 409 conflicts, Transactional Outbox with SKIP LOCKED, and Spring Security RBAC.',
    status: 'VERIFIED',
    submittedById: 3,
    submittedByName: 'Sadia Afrin',
    verifiedById: 2,
    verifiedByName: 'Karim Rahman',
    version: 1,
    createdAt: '2026-11-20T09:00:00Z',
    updatedAt: '2026-11-21T10:30:00Z',
  },
  {
    id: 502,
    courseOfferingId: 102,
    courseCode: 'CSE 3103',
    courseTitle: 'Algorithms & Graph Theory',
    title: 'CT 3: Shortest Paths & Minimum Spanning Trees',
    scheduledAt: '2026-11-29T05:30:00Z',
    durationMinutes: 40,
    coveredTopics: 'Dijkstra, Bellman-Ford, Floyd-Warshall, Kruskal and Prim algorithms with complexity proofs.',
    status: 'UNVERIFIED',
    submittedById: 3,
    submittedByName: 'Sadia Afrin',
    verifiedById: null,
    verifiedByName: null,
    version: 0,
    createdAt: '2026-11-23T12:15:00Z',
    updatedAt: '2026-11-23T12:15:00Z',
  },
  {
    id: 503,
    courseOfferingId: 103,
    courseCode: 'EEE 3110',
    courseTitle: 'Digital Signal Processing Fundamentals',
    title: 'CT 1: Discrete-Time Fourier Transform & Z-Transform',
    scheduledAt: '2026-12-06T04:30:00Z',
    durationMinutes: 50,
    coveredTopics: 'DTFT convergence, Region of Convergence (ROC), inverse Z-transform via partial fractions.',
    status: 'RESCHEDULED',
    submittedById: 3,
    submittedByName: 'Sadia Afrin',
    verifiedById: 2,
    verifiedByName: 'Karim Rahman',
    version: 2,
    createdAt: '2026-11-18T08:00:00Z',
    updatedAt: '2026-11-22T14:00:00Z',
  },
];

/**
 * Realistic adversarial dataset for break-ui stress testing:
 * - 80-character course title & 120-character CT title
 * - Long hyphenated Unicode names & 1-word short names ("Jo")
 * - Missing optional fields (null coveredTopics)
 * - Unbreakable URL inside syllabus description
 */
export const WORST_CASE_COURSES: CourseOffering[] = [
  {
    id: 901,
    courseCode: 'CSE 3103-EXT',
    courseTitle: 'CSE 3103: Advanced Algorithms, Graph Traversals, Dynamic Programming & NP-Completeness',
    department: 'CSE',
    semester: '3-2',
    academicYear: '2026',
    followed: true,
  },
  {
    id: 902,
    courseCode: 'ME 4299',
    courseTitle: 'Computational Fluid Dynamics, Heat Transfer & Turbomachinery Laboratory Instrumentation',
    department: 'ME',
    semester: '4-2',
    academicYear: '2026',
    followed: false,
  },
];

export const WORST_CASE_CT_EVENTS: CtEventSummary[] = [
  {
    id: 951,
    courseOfferingId: 901,
    courseCode: 'CSE 3103-EXT',
    courseTitle: 'CSE 3103: Advanced Algorithms, Graph Traversals, Dynamic Programming & NP-Completeness',
    title: 'CT 4: Amortized Analysis, Fibonacci Heaps, Network Flow Max-Flow Min-Cut Theorem & Polynomial-Time Reductions',
    scheduledAt: '2026-12-19T04:00:00Z',
    durationMinutes: 120,
    coveredTopics: 'Reference slides: https://classroom.google.com/c/kuet-cse-3103-advanced-algorithms-graph-traversals-np-completeness-2026-section-a/materials — bring non-programmable calculators.',
    status: 'RESCHEDULED',
    submittedById: 99,
    submittedByName: 'Aleksandra Wiśniewska-Kowalczyk',
    verifiedById: 99,
    verifiedByName: 'Aleksandra Wiśniewska-Kowalczyk',
    version: 14,
    createdAt: '2026-11-01T09:00:00Z',
    updatedAt: '2026-11-23T18:45:00Z',
  },
  {
    id: 952,
    courseOfferingId: 902,
    courseCode: 'ME 4299',
    courseTitle: 'Computational Fluid Dynamics, Heat Transfer & Turbomachinery Laboratory Instrumentation',
    title: 'Quiz',
    scheduledAt: '2026-11-25T05:00:00Z',
    durationMinutes: 15,
    coveredTopics: null,
    status: 'UNVERIFIED',
    submittedById: 100,
    submittedByName: 'Jo',
    verifiedById: null,
    verifiedByName: null,
    version: 0,
    createdAt: '2026-11-24T01:00:00Z',
    updatedAt: '2026-11-24T01:00:00Z',
  },
  {
    id: 953,
    courseOfferingId: 901,
    courseCode: 'CSE 3103-EXT',
    courseTitle: 'CSE 3103: Advanced Algorithms, Graph Traversals, Dynamic Programming & NP-Completeness',
    title: 'Cancelled Makeup CT (Merged With Lab Quiz)',
    scheduledAt: '2026-11-10T04:00:00Z',
    durationMinutes: 45,
    coveredTopics: 'Cancelled due to campus inter-department robotics fest.',
    status: 'CANCELLED',
    submittedById: 100,
    submittedByName: 'Jo',
    verifiedById: 99,
    verifiedByName: 'Aleksandra Wiśniewska-Kowalczyk',
    version: 3,
    createdAt: '2026-11-02T09:00:00Z',
    updatedAt: '2026-11-09T11:00:00Z',
  },
];
