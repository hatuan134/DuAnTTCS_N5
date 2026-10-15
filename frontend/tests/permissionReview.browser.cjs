// Optional browser QA, matching the existing *.browser.cjs convention. Fixtures never enter application code.
const { chromium } = require(process.env.LIBRA_PLAYWRIGHT_MODULE || 'playwright')
const fs = require('node:fs'), path = require('node:path'), assert = require('node:assert/strict')
const root = path.resolve(__dirname, '..'), port = Number(process.env.LIBRA_QA_PORT || 5183)
const base = 'http://127.0.0.1:' + port
const policy = JSON.parse(fs.readFileSync(path.join(root, '../backend/src/main/resources/permissions/S4-09.1-policy.json'), 'utf8'))
function inspection(excess = false) {
  return { policyVersion: policy.version, decision: policy.decision, fingerprint: 'a'.repeat(64), inspectedAt: '2026-10-15T02:41:00Z',
    roles: policy.roles, modules: policy.modules, reviewedFunctions: policy.entries.length, differentFunctions: excess ? 1 : 0, unresolvedFunctions: 0,
    entries: policy.entries.map(entry => ({ policy: entry, mapped: true, guard: "hasRole('LIBRARIAN')", different: excess && entry.id === 'FeePaymentController.collect',
      roles: policy.roles.map(role => ({ role: role.code, documented: entry.rules[role.code], actualGate: excess && entry.id === 'FeePaymentController.collect' && role.code === 'READER' ? 'ALLOW' : entry.rules[role.code] === 'DENY' ? 'DENY' : 'ALLOW',
        matches: !(excess && entry.id === 'FeePaymentController.collect' && role.code === 'READER') })) })) }
}
const evidence = { schemaVersion: 1, runKey: '4b40f1ca-5cb8-47d7-b293-eeb16f63466a', policyVersion: policy.version, fingerprint: 'a'.repeat(64), kind: 'SIMULATION',
  environment: 'MOCKMVC', sourceReference: 'UI fixtures only', executedBy: 'Người chạy thử', startedAt: '2026-10-15T02:41:00Z', completedAt: '2026-10-15T02:42:00Z',
  checks: [{ endpointId: 'FeePaymentController.collect', role: 'READER', scenario: 'STANDARD', expectedStatus: 403, beforeStatus: 200, actualStatus: 403,
    resourceUnchanged: true, detectedAt: '2026-10-15T02:41:10Z', testedAt: '2026-10-15T02:41:50Z', evidence: 'Sổ thử không đổi sau HTTP 403.', correction: 'Khôi phục guard đúng trong mô phỏng.' }] }
const detail = { summary: { id: 1, kind: 'SIMULATION', environment: 'MOCKMVC', sourceReference: 'UI fixtures only', executedBy: evidence.executedBy, importedBy: 'Quản trị thử',
  importedAt: '2026-10-15T02:43:00Z', completedAt: evidence.completedAt, caseCount: 1, detectedCount: 1, fixedCount: 1, failedCount: 0 }, evidence, currentFingerprint: true,
  results: [{ test: evidence.checks[0], module: 'FEES', functionName: 'Thu toàn bộ phí', documented: 'DENY', outcome: 'FIXED', passed: true }] }
async function authenticate(page, role) {
  await page.evaluate(role => {
    localStorage.setItem('libra_access_token', 'synthetic-ui-access'); localStorage.setItem('libra_refresh_token', 'synthetic-ui-refresh')
    localStorage.setItem('libra_access_expires_at', String(Date.now() + 3600000)); localStorage.setItem('libra_refresh_expires_at', String(Date.now() + 86400000))
    localStorage.setItem('libra_current_user', JSON.stringify({ id: 1, fullName: 'Người thử giao diện', email: 'ui@example.invalid', role }))
  }, role)
}
;(async () => {
  const { createServer } = await import('vite')
  const server = await createServer({ root, server: { host: '127.0.0.1', port, strictPort: true } }); await server.listen()
  const browser = await chromium.launch({ headless: true, executablePath: process.env.LIBRA_BROWSER_EXECUTABLE || undefined,
    args: ['--no-sandbox', '--disable-dev-shm-usage', '--disable-gpu'] })
  const page = await browser.newPage({ viewport: { width: 1366, height: 1000 } })
  const errors = [], results = []; let role = 'ADMIN', excess = false, rejectImport = false, imports = 0, reviewReads = 0, saved = false
  page.on('pageerror', error => errors.push(error.message))
  await page.route('**/api/v1/**', async route => {
    const pathname = new URL(route.request().url()).pathname.replace('/api/v1', '')
    let data = [], status = 200
    if (pathname === '/auth/me') data = { id: 1, role, fullName: 'Người thử giao diện', email: 'ui@example.invalid' }
    else if (pathname === '/permission-review') { reviewReads++; data = inspection(excess) }
    else if (pathname === '/permission-review/reports' && route.request().method() === 'POST') {
      imports++; status = rejectImport ? 409 : 201; saved = !rejectImport
      data = rejectImport ? { code: 'PERMISSION_BUILD_CHANGED', message: 'Source đã thay đổi. Chạy lại kiểm thử.' } : detail
    } else if (pathname === '/permission-review/reports') data = { items: saved ? [detail.summary] : [], page: 0, size: 10, totalItems: saved ? 1 : 0 }
    else if (pathname === '/permission-review/reports/1') data = detail
    await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(data) })
  })
  async function check(name, action) {
    try { await action(); results.push({ name, result: 'PASS' }); console.log('PASS', name) }
    catch (error) { console.log('UI STATE', (await page.locator('body').innerText()).slice(-5000)); throw error }
  }
  async function overflow() {
    const diagnostics = await page.evaluate(() => ({ width: innerWidth, scroll: document.documentElement.scrollWidth,
      offenders: [...document.querySelectorAll('main *')].filter(element => element.getBoundingClientRect().right > innerWidth + 1)
        .slice(0, 8).map(element => ({ tag: element.tagName, className: element.className, width: element.getBoundingClientRect().width, text: element.textContent?.slice(0, 80) })) }))
    assert.ok(diagnostics.scroll <= diagnostics.width + 1, JSON.stringify(diagnostics))
  }
  const file = value => ({ name: 'evidence.json', mimeType: 'application/json', buffer: Buffer.from(value) })
  try {
    await page.goto(base + '/login'); await authenticate(page, role); await page.goto(base + '/permission-review')
    await page.getByRole('heading', { name: 'Rà soát phân quyền', exact: true }).waitFor()
    await check('Admin sees all four roles and filters the fee API', async () => {
      await page.getByLabel('Phân hệ').selectOption('FEES')
      await page.getByLabel('Tìm chức năng hoặc API').fill('collect-full')
      await page.getByRole('heading', { name: 'Thu toàn bộ phí', exact: true }).waitFor()
      for (const label of policy.roles.map(value => value.name)) assert.ok(await page.getByText(label, { exact: true }).count() >= 1)
      assert.equal(await page.getByText('1 / 84 thao tác khớp bộ lọc', { exact: true }).count(), 1)
    })
    for (const width of [1366, 768, 390]) await check('Responsive matrix ' + width, async () => {
      await page.setViewportSize({ width, height: 1000 })
      await page.evaluate(() => Promise.all(document.getAnimations().filter(animation => animation.effect?.getTiming().iterations !== Infinity)
        .map(animation => animation.finished.catch(() => {}))))
      await overflow()
      if (width >= 1024) {
        const sidebar = await page.locator('.desktop-sidebar').boundingBox()
        const heading = await page.getByRole('heading', { name: 'Rà soát phân quyền', exact: true }).boundingBox()
        assert.ok(heading.x >= sidebar.x + sidebar.width, 'heading must not be hidden behind the sidebar')
      }
      if (process.env.LIBRA_QA_OUTPUT) {
        fs.mkdirSync(process.env.LIBRA_QA_OUTPUT, { recursive: true })
        await page.screenshot({ path: path.join(process.env.LIBRA_QA_OUTPUT, 'permission-review-' + width + '.png'), fullPage: true, animations: 'disabled' })
      }
    })
    await page.setViewportSize({ width: 1366, height: 1000 })
    await check('Discrepancy filter exposes a test-only excess permission', async () => {
      excess = true; await page.getByRole('button', { name: 'Làm mới', exact: true }).click()
      await page.getByText('Cần kiểm tra', { exact: true }).waitFor()
      await page.getByLabel('Chỉ hiện sai lệch hoặc chưa xác định').check()
      assert.equal(await page.getByRole('heading', { name: 'Thu toàn bộ phí', exact: true }).count(), 1)
    })
    await page.clock.install({ time: new Date('2030-01-01T00:00:00Z') })
    await page.clock.pauseAt(new Date('2030-01-01T00:00:05Z'))
    await check('Invalid-file warning disappears at 3000ms; fixed validation stays', async () => {
      await page.locator('input[type=file]').setInputFiles(file('broken'))
      const notice = page.locator('[data-feedback-tone=warning]'); await notice.waitFor()
      await page.clock.runFor(2999); assert.equal(await notice.count(), 1)
      await page.clock.runFor(1); assert.equal(await notice.count(), 0)
      assert.equal(await page.getByText('Tệp không phải JSON hợp lệ.', { exact: true }).count(), 1)
    })
    await check('Import and persistent simulation report survive 3000ms success dismissal', async () => {
      await page.locator('input[type=file]').setInputFiles(file(JSON.stringify(evidence)))
      await page.getByRole('button', { name: 'Lưu biên bản', exact: true }).click()
      const dialog = page.getByRole('dialog'); await dialog.waitFor()
      assert.equal(imports, 1); assert.ok((await dialog.textContent()).includes('MÔ PHỎNG'))
      await dialog.locator('[data-feedback-tone=success]').waitFor()
      await page.clock.runFor(2999); assert.equal(await dialog.locator('[data-feedback-tone=success]').count(), 1)
      await page.clock.runFor(1); assert.equal(await dialog.locator('[data-feedback-tone=success]').count(), 0)
      assert.equal(await dialog.count(), 1)
      await page.setViewportSize({ width: 390, height: 900 }); await overflow()
      assert.ok((await dialog.boundingBox()).width <= 390)
      await page.getByRole('button', { name: 'Đóng biên bản', exact: true }).click()
    })
    await check('Rejected report retains retry input and a 3000ms API-error notice', async () => {
      rejectImport = true
      await page.locator('input[type=file]').setInputFiles(file(JSON.stringify(evidence)))
      await page.getByRole('button', { name: 'Lưu biên bản', exact: true }).click()
      await page.locator('[data-feedback-tone=error]').waitFor()
      await page.clock.runFor(3000); assert.equal(await page.locator('[data-feedback-tone=error]').count(), 0)
      assert.ok(await page.getByRole('button', { name: 'Lưu biên bản', exact: true }).isEnabled())
    })
    for (role of ['READER', 'LIBRARIAN', 'LIBRARY_MANAGER']) await check(role + ' cannot navigate directly or see the review menu', async () => {
      const before = reviewReads; await authenticate(page, role); await page.goto(base + '/permission-review')
      await page.getByRole('heading', { name: 'Không có quyền truy cập' }).waitFor()
      assert.equal(reviewReads, before); assert.equal(await page.getByRole('link', { name: 'Rà soát phân quyền', exact: true }).count(), 0)
      await overflow()
    })
    assert.deepEqual(errors, [])
    console.log(JSON.stringify({ checks: results.length, results }, null, 2))
  } finally { await browser.close(); await server.close() }
})().catch(error => { console.error(error); process.exitCode = 1 })
