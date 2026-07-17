// Pre-switchover functional gate for blue/green deploys (issue #72).
//
// Runs directly against the green VM's private IP:port (not through the
// load balancer -- the LB health check only proves the port answers, not
// that the app behaves correctly). A failing check here must block
// promotion the same way a failed OCI backend health check does.
//
// Usage:
//   k6 run tests/k6/switchover-checks.js
// Env vars:
//   TARGET_URL   base URL of the instance under test, e.g. http://10.0.1.23:8080
//                (default: http://localhost:8080, for local/manual runs)
import http from 'k6/http';
import { check, fail } from 'k6';

export const options = {
  vus: 1,
  iterations: 1,
  thresholds: {
    checks: ['rate==1.0'],
  },
};

const BASE_URL = __ENV.TARGET_URL || 'http://localhost:8080';

function unique(prefix) {
  return `${prefix}-${Date.now()}-${Math.floor(Math.random() * 100000)}`;
}

export default function () {
  // ── Static assets ─────────────────────────────────────────────────────
  check(http.get(`${BASE_URL}/`), {
    'GET / is 200': (r) => r.status === 200,
    'GET / is html': (r) => (r.headers['Content-Type'] || '').includes('html'),
  }) || fail('static: GET / failed');

  check(http.get(`${BASE_URL}/app.js`), {
    'GET /app.js is 200': (r) => r.status === 200,
  }) || fail('static: GET /app.js failed');

  check(http.get(`${BASE_URL}/styles.css`), {
    'GET /styles.css is 200': (r) => r.status === 200,
  }) || fail('static: GET /styles.css failed');

  // ── Link creation + dedupe ────────────────────────────────────────────
  const createUrl = `https://k6-switchover-${Date.now()}.example.com`;

  const created = http.post(
    `${BASE_URL}/link`,
    JSON.stringify({ url: createUrl }),
    { headers: { 'Content-Type': 'application/json' } },
  );
  check(created, {
    'POST /link (valid) is 201': (r) => r.status === 201,
    'POST /link (valid) has Location header': (r) => !!r.headers['Location'],
  }) || fail('link creation: POST /link failed');
  const createdId = created.body;

  const dupe = http.post(
    `${BASE_URL}/link`,
    JSON.stringify({ url: createUrl }),
    { headers: { 'Content-Type': 'application/json' } },
  );
  check(dupe, {
    'POST /link (dedupe) is 200': (r) => r.status === 200,
    'POST /link (dedupe) returns same id': (r) => r.body === createdId,
  }) || fail('link creation: dedupe failed');

  const badUrl = http.post(
    `${BASE_URL}/link`,
    JSON.stringify({ url: 'not a url' }),
    { headers: { 'Content-Type': 'application/json' } },
  );
  check(badUrl, {
    'POST /link (invalid url) is 400': (r) => r.status === 400,
  }) || fail('link creation: invalid url check failed');

  // ── Alias ──────────────────────────────────────────────────────────────
  const aliasName = unique('k6-switchover-alias');
  const aliasUrl = 'https://k6-switchover-alias-test.example.com';

  const aliased = http.post(
    `${BASE_URL}/link`,
    JSON.stringify({ url: aliasUrl, alias: aliasName }),
    { headers: { 'Content-Type': 'application/json' } },
  );
  check(aliased, {
    'POST /link (alias) is 201': (r) => r.status === 201,
    'POST /link (alias) Location matches alias': (r) => r.headers['Location'] === `/${aliasName}`,
  }) || fail('alias: creation failed');

  const aliasConflict = http.post(
    `${BASE_URL}/link`,
    JSON.stringify({ url: 'https://different-url.example.com', alias: aliasName }),
    { headers: { 'Content-Type': 'application/json' } },
  );
  check(aliasConflict, {
    'POST /link (taken alias) is 409': (r) => r.status === 409,
  }) || fail('alias: conflict check failed');

  const aliasInvalid = http.post(
    `${BASE_URL}/link`,
    JSON.stringify({ url: 'https://example.com', alias: 'has space' }),
    { headers: { 'Content-Type': 'application/json' } },
  );
  check(aliasInvalid, {
    'POST /link (invalid alias) is 400': (r) => r.status === 400,
  }) || fail('alias: invalid alias check failed');

  // ── Redirection ────────────────────────────────────────────────────────
  const redirectById = http.get(`${BASE_URL}/${createdId}`, { redirects: 0 });
  check(redirectById, {
    'GET /{id} is 301': (r) => r.status === 301,
    'GET /{id} Location matches original url': (r) => r.headers['Location'] === createUrl,
  }) || fail('redirection: GET /{id} failed');

  const redirectByAlias = http.get(`${BASE_URL}/${aliasName}`, { redirects: 0 });
  check(redirectByAlias, {
    'GET /{alias} is 301': (r) => r.status === 301,
    'GET /{alias} Location matches aliased url': (r) => r.headers['Location'] === aliasUrl,
  }) || fail('redirection: GET /{alias} failed');

  const unknownId = unique('k6-switchover-missing');
  check(http.get(`${BASE_URL}/${unknownId}`), {
    'GET /{unknown-id} is 404': (r) => r.status === 404,
  }) || fail('redirection: unknown id check failed');

  // ── HEAD /{id} ─────────────────────────────────────────────────────────
  const head = http.request('HEAD', `${BASE_URL}/${createdId}`);
  check(head, {
    'HEAD /{id} is 200': (r) => r.status === 200,
  }) || fail('HEAD: existing id failed');

  check(http.request('HEAD', `${BASE_URL}/${unknownId}`), {
    'HEAD /{unknown-id} is 404': (r) => r.status === 404,
  }) || fail('HEAD: unknown id failed');

  // ── DELETE /{id} ───────────────────────────────────────────────────────
  check(http.del(`${BASE_URL}/${createdId}`), {
    'DELETE /{id} is 204': (r) => r.status === 204,
  }) || fail('DELETE: existing id failed');

  check(http.get(`${BASE_URL}/${createdId}`, { redirects: 0 }), {
    'GET /{id} after DELETE is 404': (r) => r.status === 404,
  }) || fail('DELETE: post-delete GET failed');

  check(http.del(`${BASE_URL}/${unknownId}`), {
    'DELETE /{unknown-id} is 404': (r) => r.status === 404,
  }) || fail('DELETE: unknown id failed');

  // ── Healthcheck (informational only -- MySQL may lag on a brand-new VM) ─
  const healthz = http.get(`${BASE_URL}/healthz`);
  check(healthz, {
    '/healthz responds (200 or 503)': (r) => r.status === 200 || r.status === 503,
  }) || fail('healthz: no response');
}
