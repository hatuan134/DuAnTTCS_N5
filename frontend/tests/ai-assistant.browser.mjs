// Browser contract tests use mock business responses; they are NOT database/provider integration tests.
import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { mkdirSync, writeFileSync } from 'node:fs'
const require = createRequire(import.meta.url)
const { chromium } = require(process.env.LIBRA_PLAYWRIGHT_MODULE || 'playwright')
const origin = process.env.LIBRA_TEST_ORIGIN || 'http://127.0.0.1:5179'
const output = process.env.LIBRA_TEST_OUTPUT || './ai-browser-results'
mkdirSync(output, { recursive: true })
const browser = await chromium.launch({ headless: true, ...(process.env.LIBRA_CHROMIUM_PATH ? { executablePath: process.env.LIBRA_CHROMIUM_PATH, args: ['--no-sandbox', '--disable-dev-shm-usage', '--disable-gpu'] } : {}) })
const context = await browser.newContext({ viewport: { width: 1366, height: 900 } })
const page = await context.newPage()
const results = []
const browserErrors = []
page.on('pageerror', error => browserErrors.push(error.message))
page.on('dialog', dialog => dialog.accept())
let mode = 'books', hold = null, requested = [], deletion = 0
const book = id => ({ id, title: `Sách kiểm thử Java ${id}`, authorName: 'Tác giả kiểm thử', categoryName: 'Lập trình', categoryId: 1, copyCount: 3, availableCount: 2, hasCopies: true, authors: [], createdAt: '2026-10-17T08:00:00Z', description: 'Dữ liệu mock chỉ dùng kiểm thử giao diện', href: `/catalog/books/${id}` })
const books = [book(1), book(2), book(3)]
const loanBlock = { type: 'loans', title: 'Sách bạn đang mượn', items: [{ id: 5, title: 'Sách riêng tài khoản A', borrowedAt: '2026-10-01', dueAt: '2026-10-10', overdue: true, status: 'Quá hạn', href: '/loans/9' }], meta: { total: 1, page: 0, size: 10 }, actions: [] }
const response = blocks => ({ conversationId: '00000000-0000-0000-0000-000000000001', message: '**Kết quả đã xác minh** từ dữ liệu mock. <img src=x onerror=alert(1)> [Độc hại](javascript:alert(1))', blocks, provider: 'GROQ', degraded: false, turnsRemaining: 19 })
await page.route('**/*', async route => {
  const url = new URL(route.request().url())
  if (!['localhost', '127.0.0.1'].includes(url.hostname)) return route.abort()
  if (!url.pathname.startsWith('/api/v1/')) return route.continue()
  const path = url.pathname.slice('/api/v1'.length)
  const json = (data, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(data) })
  if (path.startsWith('/ai/history/')) { deletion++; return route.fulfill({ status: 204 }) }
  if (path === '/ai/history') return json([])
  if (path.startsWith('/ai/conversations/')) { deletion++; return route.fulfill({ status: 204 }) }
  if (path === '/ai/chat') {
    requested.push(route.request().postDataJSON())
    if (mode === 'error') return json({ code: 'AI_PROVIDER_UNAVAILABLE', message: 'Dịch vụ AI tạm thời chưa sẵn sàng.' }, 503)
    if (mode === 'delay') { hold = () => json(response([loanBlock])); return }
    if (mode === 'personal') return json(response([
      { type: 'libraryCard', title: 'Thẻ thư viện', items: [{ title: 'Thẻ của bạn', status: 'ACTIVE', cardNumber: 'CARD001', expiresAt: '2027-01-01', href: '/my-library-card' }], meta: {}, actions: [] },
      { type: 'reservations', title: 'Đơn đặt giữ của bạn', items: [{ id: 1, title: 'Sách đã đặt', status: 'WAITING', reservedAt: '2026-10-16', queuePosition: 2, href: '/my-reservations' }], meta: { total: 1 }, actions: [] }
    ]))
    if (mode === 'loans') return json(response([loanBlock]))
    if (mode === 'stats') return json(response([{ type: 'notice', title: 'Chưa lấy được dữ liệu', items: [{text:'Thông báo dư do công cụ cũ'}], meta: {}, actions: [] }, { type: 'statistics', title: 'Tình hình mượn trả', items: [{ label: 'Lượt mượn bản sao', value: 25 }, { label: 'Lượt trả bản sao', value: 2 }], meta: { startDate: '2026-10-01', endDate: '2026-10-17', daily: [{ date: '2026-10-01', count: 5 }, { date: '2026-10-02', count: 20 }] }, actions: [{ label: 'Mở báo cáo chi tiết', href: '/reports/loans' }] }]))
    if (mode === 'navigation') return json(response([{ type: 'navigation', title: 'Điều hướng', items: [{ text: 'Mở đúng chức năng.' }], meta: {}, actions: [{ label: 'Mở danh mục', href: '/catalog' }, { label: 'URL độc hại', href: 'javascript:alert(1)' }, { label: 'Vượt quyền', href: '/permission-review' }] }]))
    if (mode === 'empty') return json(response([{ type: 'books', title: 'Sách trong thư viện', items: [], meta: { total: 0 }, actions: [] }]))
    return json(response([{ type: 'books', title: 'Sách trong thư viện', items: books.map(b => ({ ...b, canOpenReservation: true })), meta: { total: 3 }, actions: [] }]))
  }
  if (path === '/books/public/featured') return json(books.map(b => ({ book: b, borrowCount: 5 })))
  if (path === '/books/public/search') return json({ content: books, page: 0, size: 6, totalElements: 3, totalPages: 1, first: true, last: true, sort: 'relevance' })
  if (path === '/books/public') return json(books)
  if (path === '/books/public/filters') return json({ categories: [], publicationYears: [], authors: [] })
  if (/^\/books\/public\/\d+$/.test(path)) return json(book(Number(path.split('/').pop())))
  if (path === '/public/overview') return json({ totalTitles: 3, totalCopies: 9, totalReaders: 2, totalLoans: 6, weeklySchedule: [] })
  if (path === '/library-cards/me') return json({ fullName: 'Bạn đọc kiểm thử', memberCode: 'TEST001', registrationStatus: 'APPROVED', cardNumber: 'CARD001', cardTypeName: 'Sinh viên', cardStatus: 'ACTIVE', issuedAt: '2026-01-01', expiresAt: '2027-01-01' })
  if (path === '/loans/me/borrowed-books' || path === '/reservations/mine') return json([])
  if (path === '/loans/me/returned-books') return json({ items: [], total: 0, page: 0, size: 20 })
  if (path === '/dashboard/stats') return json({ totalAccounts: 2, activeReaders: 1, issuedLibraryCards: 1, pendingReaderRequests: 0 })
  return json([])
})
const check = async (name, fn) => { try { await fn(); results.push({ name, passed: true }) } catch (error) { results.push({ name, passed: false, error: error.message }); throw error } }
const open = async () => { await page.getByRole('button', { name: 'Mở trợ lý LIBRA AI', exact: true }).click(); await page.getByRole('dialog', { name: 'LIBRA AI Assistant' }).waitFor() }
const send = async text => { await page.getByLabel('Câu hỏi cho LIBRA AI', { exact: true }).fill(text); await page.getByRole('button', { name: 'Gửi câu hỏi', exact: true }).click(); await page.locator('.lai-thinking').waitFor({ state: 'hidden' }) }
const login = async (role, id = 11) => page.evaluate(({ role, id }) => {
  localStorage.setItem('libra_current_user', JSON.stringify({ id, role, fullName: 'Tài khoản kiểm thử', email: 'test@example.invalid' }))
  localStorage.setItem('libra_access_token', 'mock-ui-token')
  localStorage.setItem('libra_access_expires_at', String(Date.now() + 3600000))
  window.dispatchEvent(new Event('libra-session-changed'))
}, { role, id })
try {
  await page.goto(origin)
  await check('Landing launcher and welcome render', async () => { await open(); assert.match(await page.locator('.lai-welcome').innerText(), /khám phá/); assert.equal(await page.locator('main').count(), 1) })
  await page.screenshot({ animations: 'disabled', path: `${output}/desktop-welcome.png` })
  await check('Book cards, carousel, safe Markdown, guest buttons', async () => {
    await send('Tìm sách Java'); assert.equal(await page.locator('.lai-book').count(), 3); assert.equal(await page.locator('.lai-bubble img,.lai-bubble a').count(), 0)
    assert.equal(await page.getByRole('link', { name: 'Mở trang để đặt giữ' }).count(), 0)
    await page.getByRole('button', { name: 'Sách tiếp theo' }).click(); assert.equal(await page.locator('.lai-book-track').evaluate(el => el.scrollWidth > el.clientWidth), true)
    assert.equal(requested.at(-1).role, undefined); assert.equal(requested.at(-1).userId, undefined)
  })
  await page.screenshot({ animations: 'disabled', path: `${output}/desktop-books.png` })
  await check('Expand, mobile fit and reduced motion', async () => {
    await page.getByRole('button', { name: 'Phóng to', exact: true }).click(); assert.equal(await page.locator('.lai-large').count(), 1)
    await page.setViewportSize({ width: 390, height: 844 }); const box = await page.locator('.lai-window').boundingBox(); assert.ok(box.x >= 0 && box.x + box.width <= 390)
    await page.emulateMedia({ reducedMotion: 'reduce' }); assert.equal(await page.locator('.lai-window').evaluate(el => getComputedStyle(el).animationName), 'none')
  })
  await page.screenshot({ animations: 'disabled', path: `${output}/mobile-books.png` })
  await check('Delete conversation, empty results and safe navigation', async () => {
    await page.getByRole('button', { name: 'Xóa cuộc trò chuyện hiện tại', exact: true }).click(); await page.locator('.lai-welcome').waitFor(); assert.ok(deletion > 0)
    mode = 'empty'; await send('Không tìm thấy'); assert.equal(await page.locator('.lai-empty-result').count(), 1)
    mode = 'navigation'; await send('Mở danh mục'); assert.equal(await page.getByRole('link', { name: 'URL độc hại' }).count(), 0); assert.equal(await page.getByRole('link', { name: 'Vượt quyền' }).count(), 0)
    await page.getByRole('link', { name: 'Mở danh mục', exact: true }).click(); await page.waitForURL('**/catalog'); assert.equal(await page.locator('.lai-window').count(), 0)
  })
  await check('Provider error, 3-second notice and retry', async () => {
    await open(); mode = 'error'; await send('Thử lỗi'); await page.getByRole('alert').filter({ hasText: 'Dịch vụ AI' }).waitFor(); await page.getByRole('alert').filter({ hasText: 'Dịch vụ AI' }).waitFor({ state: 'hidden', timeout: 5000 })
    mode = 'empty'; await page.getByRole('button', { name: 'Thử lại', exact: true }).click(); await page.locator('.lai-retry').waitFor({ state: 'hidden' })
  })
  await check('READER cards and loan navigation', async () => {
    await login('READER'); await open(); assert.equal(await page.locator('.lai-message').count(), 0); mode = 'loans'; await send('Tôi đang mượn gì?')
    assert.equal(await page.getByRole('link', { name: 'Xem phiếu mượn', exact: true }).getAttribute('href'), '/loans/9'); assert.equal(await page.locator('.lai-overdue').count(), 1)
  })
  await check('Library card and reservation components link to real routes', async () => {
    mode = 'personal'; await send('Xem thẻ và đơn đặt giữ'); assert.equal(await page.locator('.lai-library-card').count(), 1)
    assert.equal(await page.locator('.lai-library-card a').getAttribute('href'), '/my-library-card')
    assert.equal(await page.locator('.lai-record a[href="/my-reservations"]').count(), 1)
  })
  await check('Logout cancels and ignores late private result', async () => {
    mode = 'delay'; await page.getByLabel('Câu hỏi cho LIBRA AI', { exact: true }).fill('Câu hỏi đang chờ'); await page.getByRole('button', { name: 'Gửi câu hỏi', exact: true }).click(); await page.locator('.lai-thinking').waitFor()
    await page.evaluate(() => { localStorage.clear(); window.dispatchEvent(new Event('libra-session-changed')) })
    if (hold) await hold().catch(() => {}); await open(); assert.equal(await page.getByText('Sách riêng tài khoản A', { exact: true }).count(), 0)
  })
  await check('Account switch clears history and statistics render', async () => {
    await login('LIBRARY_MANAGER', 22); await open(); assert.equal(await page.getByRole('button', { name: 'Mở báo cáo mượn trả chi tiết' }).count(), 1); mode = 'stats'; await send('Thống kê tháng'); assert.equal(await page.locator('.lai-metrics strong').first().innerText(), '25'); assert.equal(await page.locator('.lai-bars>div').count(), 2)
    assert.equal(await page.locator('.lai-daily-table thead th').count(), 4); assert.equal(await page.locator('.lai-daily-table tbody tr').count(), 2)
    assert.match(await page.locator('.lai-bubble').last().innerText(), /25/); assert.doesNotMatch(await page.locator('.lai-bubble').last().innerText(), /Tổng số lượt mượn: 6/)
    assert.equal(await page.locator('.lai-results').filter({ hasText: 'Chưa lấy được dữ liệu' }).count(), 0)
    await login('ADMIN', 33); await open(); assert.equal(await page.locator('.lai-message').count(), 0); assert.match(await page.locator('.lai-brand p').innerText(), /Quản trị/); assert.equal(await page.getByRole('button', { name: 'Mở trang quản lý tài khoản' }).count(), 1); assert.equal(await page.getByRole('button', { name: 'Mở rà soát phân quyền' }).count(), 0)
    await login('LIBRARIAN', 44); await open(); assert.match(await page.locator('.lai-brand p').innerText(), /Thủ thư/)
  })
  await check('Dedicated reader card route uses existing API', async () => {
    await login('READER', 55); await page.goto(`${origin}/my-library-card`); await page.getByText('CARD001', { exact: true }).waitFor(); assert.equal(await page.getByRole('button', { name: 'Mở trợ lý LIBRA AI', exact: true }).count(), 1)
  })
  await check('No uncaught browser errors', async () => assert.deepEqual(browserErrors, []))
} finally {
  writeFileSync(`${output}/results.json`, JSON.stringify({ mockOnly: true, results, browserErrors }, null, 2))
  console.log(JSON.stringify(results, null, 2))
  await browser.close()
}
