// Optional browser QA, using isolated fixtures only. No fixture is imported by the application.
const { chromium } = require(process.env.LIBRA_PLAYWRIGHT_MODULE || 'playwright')
const fs = require('node:fs'), path = require('node:path'), assert = require('node:assert/strict')
const root = path.resolve(__dirname, '..'), port = Number(process.env.LIBRA_QA_PORT || 5187)
const output = process.env.LIBRA_QA_OUTPUT || path.join(require('node:os').tmpdir(), 'libra-s4-00-1-qa')
fs.mkdirSync(output, { recursive: true })
const base = `http://127.0.0.1:${port}`
const makeLoan = id => ({ id, loanNumber: `PM-${String(id).padStart(6, '0')}`, readerId: 12, readerName: 'Nguyễn Văn An', readerPhone: '0901234567', cardNumber: 'THE-12', borrowedAt: '2026-10-01T08:00:00+07:00', status: 'BORROWED', items: [{ barcode: `COPY-${id}`, bookTitle: 'Kiểm thử giao diện mượn trả sách thư viện', dueAt: '2026-10-20T17:00:00+07:00', status: 'BORROWED' }] })
;(async () => {
  const { createServer } = await import('vite')
  const server = await createServer({ root, server: { host: '127.0.0.1', port, strictPort: true } }); await server.listen()
  const browser = await chromium.launch({ headless: true, executablePath: process.env.LIBRA_BROWSER_EXECUTABLE || undefined, args: ['--no-sandbox', '--disable-dev-shm-usage'] })
  const page = await browser.newPage({ viewport: { width: 1366, height: 900 } })
  page.setDefaultTimeout(10000)
  const errors = [], requests = [], results = []; let reject = false
  page.on('pageerror', error => errors.push(error.message))
  await page.addInitScript(() => {
    localStorage.setItem('libra_access_token', 'isolated-ui-test'); localStorage.setItem('libra_refresh_token', 'isolated-refresh')
    localStorage.setItem('libra_access_expires_at', String(Date.now() + 3600000)); localStorage.setItem('libra_refresh_expires_at', String(Date.now() + 86400000))
    localStorage.setItem('libra_current_user', JSON.stringify({ id: 1, role: 'ADMIN', fullName: 'Quản trị kiểm thử', email: 'qa@example.invalid' }))
  })
  await page.route('**/api/v1/**', async route => {
    const url = new URL(route.request().url()), endpoint = url.pathname.replace('/api/v1', '')
    requests.push({ endpoint, method: route.request().method(), query: Object.fromEntries(url.searchParams) })
    let data = [], status = 200
    if (endpoint === '/loans/search') {
      if (reject) { status = 503; data = { message: 'Không tải được phiếu mượn trong lượt kiểm thử.' } }
      else { const page = Number(url.searchParams.get('page') || 0); data = { items: Array.from({ length: page === 2 ? 1 : 10 }, (_, i) => makeLoan(page * 10 + i + 1)), page, size: 10, total: 21 } }
    } else if (endpoint === '/loans/readers') data = { items: [{ readerId: 12, readerName: 'Bạn đọc chưa có phiếu', cardNumber: 'THE-12', readerPhone: '0901234567' }], page: 0, size: 10, total: 1 }
    else if (endpoint === '/loans/reader-eligibility') data = { readerId: 12, readerName: 'Nguyễn Văn An', cardNumber: 'THE-12', cardTypeName: 'Sinh viên', maxBooks: 5, borrowedBooks: 1, remainingBooks: 4, eligible: true, message: 'Đủ điều kiện mượn', blockReasons: [] }
    else if (endpoint === '/loans/rejections') data = { items: [], total: 0, page: 0, size: 10 }
    else if (endpoint === '/loans/return-lookup') data = { status: 'NOT_BORROWED', barcode: url.searchParams.get('barcode'), bookTitle: 'Sách kiểm thử', copyId: 1, itemId: null, message: 'Bản sao đã được nhận trả hoặc chưa được cho mượn.' }
    else if (endpoint === '/library-cards/pending') data = []
    else if (endpoint === '/library-cards') data = [{ id: 5, userId: 12, readerName: 'Nguyễn Văn An', cardNumber: 'THE-12', cardTypeName: 'Sinh viên', issuedAt: '2026-01-01', expiresAt: '2027-01-01', status: 'ACTIVE', memberCode: 'SV012', manualLock: { active: false } }]
    await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(data) })
  })
  try {
    await page.goto(base + '/loans/search'); await page.getByRole('heading', { name: 'Phiếu mượn hiện có', exact: true }).waitFor()
    await page.getByText('Trang 1 / 3', { exact: true }).waitFor(); assert.equal(await page.getByRole('link', { name: 'Chi tiết phiếu', exact: true }).count(), 10)
    await page.getByRole('button', { name: 'Trang sau', exact: true }).click(); await page.getByText('Trang 2 / 3', { exact: true }).waitFor()
    assert.equal(requests.filter(x => x.endpoint === '/loans/search').at(-1).query.page, '1')
    await page.getByLabel('Tìm phiếu mượn hoặc bạn đọc').fill('090 123 4567'); await page.getByRole('button', { name: 'Tìm kiếm', exact: true }).click()
    await page.getByText('Trang 1 / 3', { exact: true }).waitFor(); assert.equal(requests.filter(x => x.endpoint === '/loans/search').at(-1).query.code, '090 123 4567')
    await page.screenshot({ path: path.join(output, 'loan-search-desktop.png'), fullPage: false }); results.push('Desktop: ten loans, paging and phone search')
    reject = true; await page.getByRole('button', { name: 'Tìm kiếm', exact: true }).click()
    const feedback = page.locator('[data-feedback-tone="error"]'); await feedback.waitFor(); await feedback.waitFor({ state: 'hidden', timeout: 4500 })
    assert.equal(await page.getByRole('button', { name: 'Thử lại', exact: true }).count(), 1); results.push('Error disappears after 3s; retry stays available')
    reject = false
    await page.goto(base + '/loans/direct'); await page.getByRole('button', { name: 'Tìm bạn đọc kể cả chưa mượn', exact: true }).click()
    await page.getByRole('button', { name: 'Chọn bạn đọc', exact: true }).click(); await page.locator('#direct-loan-card').waitFor()
    assert.equal(await page.locator('#direct-loan-card').inputValue(), 'THE-12'); await page.getByRole('heading', { name: 'Thông tin bạn đọc', exact: true }).waitFor()
    results.push('Direct lending: selecting a reader populates existing eligibility flow')
    await page.goto(base + '/loans/receive-return'); await page.getByRole('button', { name: 'Chọn để nhận trả', exact: true }).first().click()
    await page.getByText('Bản sao đã được nhận trả hoặc chưa được cho mượn.', { exact: true }).first().waitFor()
    assert.equal(requests.filter(x => x.endpoint === '/loans/return-confirmation').length, 0); results.push('Return selection previews without a write')
    await page.goto(base + '/loans/rejections'); await page.getByRole('heading', { name: 'Phiếu mượn hiện có', exact: true }).waitFor()
    await page.getByRole('button', { name: 'Chọn bạn đọc', exact: true }).first().click()
    assert.equal(await page.getByPlaceholder('Nhập tên bạn đọc, số điện thoại, mã thẻ hoặc mã phiếu').inputValue(), 'THE-12'); results.push('Rejection log: selecting a loan filters the card')
    await page.goto(base + '/library-cards'); await page.getByRole('button', { name: /Thẻ đã cấp/ }).click(); await page.getByRole('button', { name: 'Khóa thẻ', exact: true }).first().waitFor(); results.push('Admin sees card locking action')
    for (const route of ['/loans/search', '/loans/direct', '/loans/receive-return', '/loans/rejections']) {
      await page.setViewportSize({ width: 390, height: 844 }); await page.goto(base + route); await page.getByRole('heading', { name: 'Phiếu mượn hiện có', exact: true }).waitFor()
      await page.waitForTimeout(100)
      const overflow = await page.evaluate(() => document.documentElement.scrollWidth > innerWidth + 1)
      assert.equal(overflow, false, route + ' has horizontal overflow')
    }
    await page.screenshot({ path: path.join(output, 'loan-search-mobile.png'), fullPage: false }); results.push('390px: four search screens have no horizontal overflow')
    assert.deepEqual(errors, []); fs.writeFileSync(path.join(output, 'results.json'), JSON.stringify({ results, errors }, null, 2)); console.log(JSON.stringify({ results, errors }, null, 2))
  } catch (error) { await page.screenshot({ path: path.join(output, 'failure.png'), fullPage: false }); fs.writeFileSync(path.join(output, 'failure.txt'), JSON.stringify({ error: String(error), errors, body: await page.locator('body').innerText() }, null, 2)); throw error
  } finally { await browser.close(); await server.close() }
})().catch(error => { console.error(error); process.exitCode = 1 })
