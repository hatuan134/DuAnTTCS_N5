import test from 'node:test'
import assert from 'node:assert/strict'
import { counters, payload } from './permission-review-callback.mjs'
const env = { RUN_KEY: 'key', TEST_EXIT: '0', GITHUB_RUN_ID: '1', GITHUB_RUN_ATTEMPT: '1' }
const report = { runKey: 'key', schemaVersion: 2, environment: 'ISOLATED_POSTGRES', kind: 'REGRESSION',
  fingerprint: 'a'.repeat(64), checks: [{ assertionsPassed: true, expectedStatus: 403, actualStatus: 403, resourceUnchanged: true }] }
const xml = '<testsuite tests="1" errors="0" skipped="0" failures="0">'
test('requires completed, successful, non-skipped tests and evidence', () => {
  assert.equal(payload(env, report, xml).passed, true)
  assert.equal(payload(env, null, xml).passed, false)
  assert.equal(payload(env, report, '').passed, false)
  assert.equal(payload({ ...env, TEST_EXIT: '1' }, report, xml).passed, false)
  assert.equal(payload(env, report, xml.replace('skipped="0"', 'skipped="1"')).passed, false)
  assert.equal(payload(env, report, xml.replace('failures="0"', 'failures="1"')).passed, false)
})
test('does not pass wrong run, partial evidence or unauthorized mutation', () => {
  assert.equal(payload(env, { ...report, runKey: 'other' }, xml).passed, false)
  assert.equal(payload(env, { ...report, checks: [] }, xml).passed, false)
  assert.equal(payload(env, { ...report, checks: [{ ...report.checks[0], resourceUnchanged: false }] }, xml).passed, false)
  assert.deepEqual(counters(xml), { total: 1, failed: 0, skipped: 0 })
})
