import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '15s', target: 20 },
    { duration: '30s', target: 50 },
    { duration: '15s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<150'], // 95% of requests must complete below 150ms
    http_req_failed: ['rate<0.01'],    // Error rate must be less than 1%
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080/api/v1';

export default function () {
  const params = {
    headers: {
      'Content-Type': 'application/json',
      'Authorization': 'Bearer mock-jwt-token-for-load-testing',
    },
  };

  // Test CT Event Filtering with Composite Index (course_offering_id, scheduled_at)
  const res = http.get(`${BASE_URL}/ct/events?courseId=1&status=VERIFIED`, params);

  check(res, {
    'status is 200 or 401': (r) => r.status === 200 || r.status === 401,
    'response time < 150ms': (r) => r.timings.duration < 150,
  });

  sleep(0.5);
}

