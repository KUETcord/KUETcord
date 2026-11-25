import http from 'k6/http';
import { check, group, sleep } from 'k6';

/**
 * KUETcord Phase 5 k6 Load Test — CT Hub Read Path (50 Concurrent VUs)
 *
 * Verifies:
 * 1. Composite PostgreSQL index `idx_ct_course_schedule (course_offering_id, scheduled_at)`
 *    on `GET /api/v1/ct/events`.
 * 2. Caffeine L1 cache + SingleFlight RequestCoalescer on `GET /api/v1/ct/courses`.
 * 3. SLA compliance: p95 latency < 150ms and error rate < 1%.
 *
 * Usage:
 *   k6 run -e BASE_URL=http://localhost:8080/api/v1 tests/load/k6_ct_read.js
 *   k6 run -e BASE_URL=http://localhost:8080/api/v1 -e JWT_TOKEN=<token> tests/load/k6_ct_read.js
 */
export const options = {
  stages: [
    { duration: '15s', target: 20 },
    { duration: '30s', target: 50 },
    { duration: '15s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<150'], // 95% of requests must complete below 150ms
    http_req_failed: ['rate<0.01'],   // Error rate must be less than 1%
    checks: ['rate>0.99'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080/api/v1';
const TEST_EMAIL = __ENV.TEST_EMAIL || 'student.1907001@stud.kuet.ac.bd';
const TEST_PASSWORD = __ENV.TEST_PASSWORD || 'Password123!';

export function setup() {
  if (__ENV.JWT_TOKEN) {
    return { token: __ENV.JWT_TOKEN };
  }

  const loginRes = http.post(
    `${BASE_URL}/auth/login`,
    JSON.stringify({ email: TEST_EMAIL, password: TEST_PASSWORD }),
    { headers: { 'Content-Type': 'application/json' } }
  );

  check(loginRes, {
    'setup login succeeded (200)': (r) => r.status === 200,
  });

  const body = loginRes.json();
  const token = body && body.data ? body.data.accessToken : null;
  if (!token) {
    throw new Error(`k6 setup login failed (status ${loginRes.status}). Seed test_fixtures.sql or pass -e JWT_TOKEN=<jwt>.`);
  }

  return { token };
}

export default function (data) {
  const params = {
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${data.token}`,
    },
  };

  group('CT Events Indexed Read', () => {
    const res = http.get(`${BASE_URL}/ct/events?courseId=1&status=VERIFIED`, params);
    check(res, {
      'ct events status is 200': (r) => r.status === 200,
      'ct events envelope success=true': (r) => {
        const body = r.json();
        return body && body.success === true;
      },
      'ct events latency < 150ms': (r) => r.timings.duration < 150,
    });
  });

  group('Course Catalog L1 Cached Read', () => {
    const res = http.get(`${BASE_URL}/ct/courses?department=CSE`, params);
    check(res, {
      'courses status is 200': (r) => r.status === 200,
      'courses envelope success=true': (r) => {
        const body = r.json();
        return body && body.success === true;
      },
      'courses latency < 150ms': (r) => r.timings.duration < 150,
    });
  });

  sleep(0.5);
}
