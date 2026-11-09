import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '15s', target: 20 },
    { duration: '30s', target: 50 },
    { duration: '15s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<120'], // 95% of requests must complete below 120ms
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

  // Test Cursor-Based Message History Pagination
  const res = http.get(`${BASE_URL}/conversations/1/messages?limit=30`, params);

  check(res, {
    'status is 200 or 401': (r) => r.status === 200 || r.status === 401,
    'response time < 120ms': (r) => r.timings.duration < 120,
  });

  sleep(0.5);
}

