// k6 load test for the read-heavy path (dashboard + kiosk polling).
//
// Run:  k6 run tests/load/measurements.js
//       BASE_URL=http://localhost:8080 k6 run tests/load/measurements.js
//
// This is NOT part of CI: it needs a running backend and the k6 binary (https://k6.io).
// The point is robustness, not raw throughput: under sustained load the app must degrade
// gracefully -- the rate limiter returns 429 (that is the app working, not a failure), and
// only 5xx / connection errors count as real problems. Thresholds assert latency stays bounded
// and server errors stay near zero.

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// Only 5xx / network errors are real failures; 200/404/429 are all "the app is fine".
const serverErrors = new Rate('server_errors');

export const options = {
  scenarios: {
    ramp_reads: {
      executor: 'ramping-vus',
      startVUs: 1,
      stages: [
        { duration: '30s', target: 20 },
        { duration: '1m', target: 50 },
        { duration: '30s', target: 0 },
      ],
    },
  },
  thresholds: {
    server_errors: ['rate<0.01'],     // under 1% of responses are 5xx / connection errors
    http_req_duration: ['p(95)<800'], // 95th percentile latency under 800 ms
  },
};

function isHealthy(res) {
  // 200 OK, 404 (empty dataset) and 429 (rate limiter doing its job) are all acceptable.
  return res.status === 200 || res.status === 404 || res.status === 429;
}

function record(res) {
  serverErrors.add(res.status >= 500 || res.status === 0);
}

export default function () {
  const latest = http.get(`${BASE_URL}/api/measurements/latest`);
  check(latest, { 'latest is healthy': isHealthy });
  record(latest);

  const page = http.get(`${BASE_URL}/api/measurements?page=0&size=20`);
  check(page, { 'history page is healthy': isHealthy });
  record(page);

  sleep(1);
}
