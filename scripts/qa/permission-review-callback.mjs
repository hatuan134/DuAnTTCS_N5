import { createHmac } from 'node:crypto'
import { readFileSync, writeFileSync, existsSync } from 'node:fs'
import { pathToFileURL } from 'node:url'

// Use Surefire's actual suite counters; Maven exit 0 alone also permits skipped tests.
export function counters(xml = '') {
  const suite = xml.match(/<testsuite\s[^>]*>/)?.[0]
  if (!suite) return { total: 0, failed: 0, skipped: 0 }
  const n = name => Number(suite.match(new RegExp(`\\b${name}="(\\d+)"`))?.[1] ?? 0)
  return { total: n('tests'), failed: n('failures') + n('errors'), skipped: n('skipped') }
}

export function payload(env, report, xml) {
  const counts = counters(xml)
  if (report && (report.runKey !== env.RUN_KEY || report.schemaVersion !== 2
      || report.environment !== 'ISOLATED_POSTGRES' || report.kind !== 'REGRESSION'
      || !/^[a-f0-9]{64}$/.test(report.fingerprint))) report = null
  const passed = env.TEST_EXIT === '0' && counts.total > 0 && counts.failed === 0 && counts.skipped === 0
    && report !== null && report.checks.length === counts.total
    && report.checks.every(c => c.assertionsPassed === true && c.expectedStatus === c.actualStatus
      && (![401, 403, 404, 405].includes(c.expectedStatus) || c.resourceUnchanged === true))
  if (report) report = { ...report, sourceReference: `${env.GITHUB_SHA} / ${env.GITHUB_REF_NAME} / ${env.GITHUB_RUN_ID}:${env.GITHUB_RUN_ATTEMPT}`, executedBy: 'GitHub Actions' }
  return { runKey: env.RUN_KEY, fingerprint: report?.fingerprint ?? (env.EXPECTED_FINGERPRINT || '0'.repeat(64)),
    passed, report, ci: { repository: env.GITHUB_REPOSITORY, runId: Number(env.GITHUB_RUN_ID),
      attempt: Number(env.GITHUB_RUN_ATTEMPT), commitSha: env.GITHUB_SHA, branch: env.GITHUB_REF_NAME,
      event: env.GITHUB_EVENT_NAME, startedAt: env.REVIEW_STARTED_AT, completedAt: new Date().toISOString(), ...counts } }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const env = process.env
  const report = existsSync(env.PERMISSION_REVIEW_RESULT) ? JSON.parse(readFileSync(env.PERMISSION_REVIEW_RESULT, 'utf8')) : null
  const xmlPath = 'backend/target/surefire-reports/TEST-com.duanttcsn5.library.FullPermissionDatabaseTest.xml'
  const body = JSON.stringify(payload(env, report, existsSync(xmlPath) ? readFileSync(xmlPath, 'utf8') : ''))
  if (!env.CALLBACK_SECRET || env.CALLBACK_SECRET.length < 32) throw new Error('Callback secret missing or too short')
  const stamp = String(Math.floor(Date.now() / 1000))
  const signature = createHmac('sha256', env.CALLBACK_SECRET).update(stamp + '.' + body).digest('hex')
  writeFileSync(`${env.RUNNER_TEMP}/callback.json`, body)
  writeFileSync(`${env.RUNNER_TEMP}/callback.headers`, `X-Libra-Timestamp: ${stamp}\nX-Libra-Signature: sha256=${signature}\n`)
  writeFileSync(`${env.RUNNER_TEMP}/review-passed`, JSON.parse(body).passed ? 'true' : 'false')
}
