import http from 'k6/http';
import { check, fail, group, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

/**
 * KUETcord k6 load test: Kanban board reads and card moves (Member C, 40 concurrent VUs)
 *
 * Each VU spends most iterations reading the whole board, the request every open workspace makes,
 * and the rest moving a card with optimistic locking (PATCH carrying expectedVersion):
 *   - board read:  GET   /api/v1/playgrounds/{id}/tasks           (idx_tasks_playground_status, one query)
 *   - card read:   GET   /api/v1/playgrounds/{id}/tasks/{taskId}  (fresh version before a move)
 *   - card move:   PATCH /api/v1/playgrounds/{id}/tasks/{taskId}  { status, expectedVersion }
 *
 * Every VU owns one card, so moves rarely collide. A further 10% of moves go to one shared "hot" card,
 * so concurrent writers do collide; those must come back as 409 Conflict (never 500), and are counted in
 * kanban_conflicts instead of being treated as failures.
 *
 * SLA (MEMBER_C_IMPLEMENTATION_PLAN.md section 8): board read p95 <= 100 ms under 40 VUs, < 1% errors.
 *
 * Usage (backend running with the seed fixtures, see LOCAL_DEVELOPMENT_GUIDE.md):
 *   k6 run tests/load/k6_kanban_tasks.js
 *   k6 run -e BASE_URL=http://localhost:8080/api/v1 -e JWT_TOKEN=<jwt> tests/load/k6_kanban_tasks.js
 *   k6 run -e PLAYGROUND_ID=<id> tests/load/k6_kanban_tasks.js   # reuse a board instead of creating one
 */

const VUS = 40;
const STATUSES = ['BACKLOG', 'TODO', 'IN_PROGRESS', 'REVIEW', 'DONE'];

export const options = {
  stages: [
    { duration: '15s', target: 20 },
    { duration: '45s', target: VUS },
    { duration: '15s', target: 0 },
  ],
  thresholds: {
    'http_req_duration{op:board}': ['p(95)<100'],
    'http_req_duration{op:move}': ['p(95)<200'],
    http_req_failed: ['rate<0.01'], // 409 on the hot card is expected and excluded below
    kanban_server_errors: ['count==0'],
    checks: ['rate>0.99'],
  },
};

// A 409 is the correct answer to a stale expectedVersion, so it must not count as a failed request.
http.setResponseCallback(http.expectedStatuses(200, 201, 409));

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080/api/v1';
const TEST_EMAIL = __ENV.TEST_EMAIL || 'student.1907001@stud.kuet.ac.bd';
const TEST_PASSWORD = __ENV.TEST_PASSWORD || 'Password123!';

const conflicts = new Counter('kanban_conflicts');
const serverErrors = new Counter('kanban_server_errors');
const moveSuccess = new Rate('kanban_move_success');
const boardCards = new Trend('kanban_board_cards');

function headers(token) {
  return { headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` } };
}

function dataOf(res) {
  const body = res.json();
  return body && body.data;
}

function login() {
  if (__ENV.JWT_TOKEN) {
    return __ENV.JWT_TOKEN;
  }
  const res = http.post(`${BASE_URL}/auth/login`, JSON.stringify({ email: TEST_EMAIL, password: TEST_PASSWORD }),
    { headers: { 'Content-Type': 'application/json' } });
  const token = res.status === 200 ? dataOf(res).accessToken : null;
  if (!token) {
    fail(`k6 setup login failed (status ${res.status}). Seed test_fixtures.sql or pass -e JWT_TOKEN=<jwt>.`);
  }
  return token;
}

/** One board for the run: VUS owned cards plus one shared hot card, created through the API. */
export function setup() {
  const token = login();
  const params = headers(token);

  let playgroundId = Number(__ENV.PLAYGROUND_ID || 0);
  if (!playgroundId) {
    const res = http.post(`${BASE_URL}/playgrounds`, JSON.stringify({
      title: `k6 load board ${new Date().toISOString()}`,
      problemStatement: 'Synthetic board for the Kanban load test',
      visibility: 'PRIVATE',
    }), params);
    if (res.status !== 201) {
      fail(`could not create the load-test playground (status ${res.status}): ${res.body}`);
    }
    playgroundId = dataOf(res).id;
  }

  const taskIds = [];
  for (let i = 0; i <= VUS; i += 1) {
    const res = http.post(`${BASE_URL}/playgrounds/${playgroundId}/tasks`, JSON.stringify({
      title: i === VUS ? 'Hot card (shared by every VU)' : `Load card ${i + 1}`,
      status: STATUSES[i % STATUSES.length],
      priority: 'MEDIUM',
    }), params);
    if (res.status !== 201) {
      fail(`could not create load-test card ${i} (status ${res.status}): ${res.body}`);
    }
    taskIds.push(dataOf(res).id);
  }

  return { token, playgroundId, ownCards: taskIds.slice(0, VUS), hotCard: taskIds[VUS] };
}

function readBoard(data, params) {
  const res = http.get(`${BASE_URL}/playgrounds/${data.playgroundId}/tasks`, { ...params, tags: { op: 'board' } });
  const ok = check(res, {
    'board 200': (r) => r.status === 200,
    'board has five columns': (r) => r.status === 200 && dataOf(r).columns.length === 5,
  });
  if (ok) {
    boardCards.add(dataOf(res).totalTasks);
  }
  if (res.status >= 500) {
    serverErrors.add(1);
  }
}

function moveCard(data, params, taskId) {
  const path = `${BASE_URL}/playgrounds/${data.playgroundId}/tasks/${taskId}`;
  const current = http.get(path, { ...params, tags: { op: 'card' } });
  if (current.status !== 200) {
    if (current.status >= 500) serverErrors.add(1);
    check(current, { 'card read 200': (r) => r.status === 200 });
    return;
  }
  const card = dataOf(current);
  const next = STATUSES[(STATUSES.indexOf(card.status) + 1) % STATUSES.length];

  const res = http.patch(path, JSON.stringify({ status: next, expectedVersion: card.version }),
    { ...params, tags: { op: 'move' } });
  check(res, {
    'move is 200 or a clean 409': (r) => r.status === 200 || r.status === 409,
    'version bumps on success': (r) => r.status !== 200 || dataOf(r).version === card.version + 1,
  });
  if (res.status === 409) conflicts.add(1);
  if (res.status >= 500) serverErrors.add(1);
  moveSuccess.add(res.status === 200);
}

export default function (data) {
  const params = headers(data.token);
  const roll = Math.random();

  if (roll < 0.8) {
    group('read board', () => readBoard(data, params));
  } else if (roll < 0.98) {
    group('move own card', () => moveCard(data, params, data.ownCards[(__VU - 1) % data.ownCards.length]));
  } else {
    group('move hot card', () => moveCard(data, params, data.hotCard));
  }

  sleep(0.5);
}
