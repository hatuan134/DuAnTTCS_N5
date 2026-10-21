// Layout regression only. All API traffic is intercepted; no real accounts or writes.
const { chromium } = require(process.env.LIBRA_PLAYWRIGHT_MODULE || 'playwright')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const os = require('node:os')
const origin = process.env.LIBRA_TEST_ORIGIN || 'http://127.0.0.1:5183'
const output = process.env.LIBRA_TEST_OUTPUT || path.join(os.tmpdir(), 'libra-responsive-after')
const widths = process.env.LIBRA_TEST_WIDTHS
  ? process.env.LIBRA_TEST_WIDTHS.split(',').map(Number)
  : [320, 360, 375, 390, 430, 768, 820, 1024, 1280, 1440]
const results = []
const card = { id: 1, name: 'Sinh viên', description: 'Chính sách bạn đọc sinh viên', duration: 12, maxBooks: 5, loanDays: 14, maxRenewals: 2, renewalDays: 7, active: true, usageCount: 100 }
const book = { id: 1, title: 'Lập trình và thiết kế hệ thống thư viện', authors: [], authorName: 'Tác giả kiểm thử', categoryName: 'Khoa học', categoryId: 1, publicationYear: 2025, availableCount: 2, copyCount: 3, hasCopies: true }
const fixtures = {
  '/reservations/auto-cancelled-last-30-days': ['AVAILABLE', 'TRANSFERRED', 'NO_COPY'].map((copyOutcome, i) => ({ id: 76 + i, readerId: 167, readerName: 'Nguyễn Minh Quân', bookTitle: 'Bông hồng vàng và những câu chuyện về cuộc sống', barcode: 'DVT26-000085', reservedAt: '2026-10-15T10:20:00Z', pickupDeadline: '2026-10-19T10:00:00Z', cancelledAt: '2026-10-20T00:30:00Z', cancelledByName: 'Hệ thống', copyOutcome, nextReaderName: 'Trần Thị Minh Ngọc', nextPickupDeadline: '2026-10-23T10:00:00Z' })),
  '/reservations/auto-cancel-runs/latest': { status: 'SUCCESS', triggeredBy: 'SYSTEM', startedAt: '2026-10-20T00:30:00Z' },
  '/card-types': [card], '/card-types/history': [],
  '/categories': [{ id: 1, name: 'Khoa học máy tính', description: 'Sách chuyên ngành', level: 1, parentName: null, bookCount: 12, active: true }],
  '/authors': [], '/books': [book], '/books/publishers': [],
  '/dashboard/stats': { totalAccounts: 120, activeReaders: 98, issuedLibraryCards: 105, pendingReaderRequests: 7 },
  '/public/overview': { totalTitles: 12, totalCopies: 36, totalReaders: 98, totalLoans: 200, weeklySchedule: [] },
  '/books/public/featured': [{ book, borrowCount: 5 }],
  '/books/public/search': { content: [book], page: 0, size: 12, totalElements: 1, totalPages: 1, first: true, last: true },
  '/books/public/filters': { categories: [], authors: [], publicationYears: [2025] },
  '/ai/history': [],
  '/readers/me/profile': { userId: 1, fullName: 'Bạn đọc kiểm thử', dateOfBirth: '2000-01-01', memberCode: 'BD0001', email: 'reader@example.invalid', phone: '0912345678', address: 'Địa chỉ kiểm thử', registrationStatus: 'APPROVED', cardNumber: 'CARD001', cardTypeName: 'Sinh viên', issuedAt: '2026-01-01', expiresAt: '2027-01-01', cardStatus: 'ACTIVE' },
  '/reports/loans': { startDate: '2026-10-01', endDate: '2026-10-20', totalBorrowed: 20, totalReturned: 10, openLoans: 10, overdueLoans: 2, overdueRate: 20, topBooks: [], topQueuedBooks: [], queueSnapshotAt: '2026-10-21T00:00:00Z', dailyLoans: Array.from({ length: 20 }, (_, i) => ({ date: '2026-10-' + String(i + 1).padStart(2, '0'), borrowCount: 1 })) },
  '/loan-reminders/history': { content: [{ id: 1, readerName: 'Đặng Minh Quân', recipientEmail: 'reader.long.address@example.invalid', readerCardNumber: 'CARD-2026-0001', sentAt: '2026-10-21T00:00:00Z', reminderDate: '2026-10-21', reminderGroup: 'DUE_TODAY', loanItemCount: 3, bookTitles: 'Lập trình và thiết kế hệ thống thư viện, Đắc nhân tâm', totalEstimatedFeeVnd: 15000, status: 'PENDING_RETRY', retryCount: 1, triggeredBy: 'SYSTEM_SCHEDULED' }], totalElements: 1, page: 0, size: 20, totalPages: 1 },
  '/loan-reminders/preview': { targetDate: '2026-10-21', totalRecipients: 1, totalItems: 1, reminders: [{
    readerId: 1, readerName: 'Bạn đọc kiểm thử', recipientEmail: 'reader@example.invalid', reminderGroup: 'DUE_TODAY',
    alreadySentToday: false, totalEstimatedFeeVnd: 10000,
    items: [{ itemId: 1, loanNumber: 'PM-0001', bookTitle: book.title, copyBarcode: 'COPY-0001', daysOverdue: 0, estimatedFeeVnd: 10000 }],
  }] },
}
;(async () => {
  fs.mkdirSync(output, { recursive: true })
  const browser = await chromium.launch({ headless: true, executablePath: process.env.LIBRA_BROWSER_EXECUTABLE || undefined })
  const page = await browser.newPage()
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  await page.route('**/*', route => {
    const url = new URL(route.request().url())
    if (!['127.0.0.1', 'localhost'].includes(url.hostname)) return route.abort()
    if (!url.pathname.startsWith('/api/v1/')) return url.origin === origin ? route.continue() : route.abort()
    const data = fixtures[url.pathname.slice('/api/v1'.length)]
    return route.fulfill({ status: data === undefined ? 503 : 200, headers: { 'Access-Control-Allow-Origin': origin }, json: data ?? { message: 'No fixture for this screen' } })
  })
  async function auth(role) {
    await page.goto(origin + '/login')
    await page.evaluate(role => {
      localStorage.clear()
      if (role === 'PUBLIC') return
      localStorage.setItem('libra_current_user', JSON.stringify({ id: 1, fullName: 'Người dùng kiểm thử', role }))
      localStorage.setItem('libra_access_token', 'layout-fixture-only')
      localStorage.setItem('libra_access_expires_at', String(Date.now() + 3600000))
    }, role)
  }
  async function check(name, action) {
    try { await action(); results.push({ name, passed: true }) }
    catch (error) { results.push({ name, passed: false, error: error.message }) }
    await page.screenshot({ path: path.join(output, name + '.png'), animations: 'disabled' })
  }
  async function noOverflow() {
    assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), 'Whole page overflows')
  }
  try {
    await auth('ADMIN')
    await page.goto(origin + '/manager/auto-cancellations')
    await page.locator('.auto-cancellations-table tbody tr').first().waitFor()
    for (const width of [...new Set([...widths, 1165, 960, 640])]) {
      await page.setViewportSize({ width, height: 844 })
      await page.waitForTimeout(400)
      await check('auto-cancellations-' + width, async () => {
        await noOverflow()
        const table = page.locator('.auto-cancellations-table')
        await table.scrollIntoViewIfNeeded()
        assert.equal(await table.locator('tbody tr').count(), 3)
        if (width >= 768) {
          assert.ok(await table.locator('tbody tr').first().locator('td').nth(2).evaluate(e => e.clientWidth >= 220), 'Reader column squeezed')
          assert.ok(await table.locator('tbody tr').first().evaluate(e => e.clientHeight < 180), 'Row wraps into vertical letters')
        } else {
          assert.equal(await table.locator('tbody tr').first().evaluate(e => getComputedStyle(e).display), 'grid')
          assert.equal(await table.locator('td[data-label]').count(), 21)
        }
      })
    }
    await page.locator('#auto-cancellation-outcome').selectOption('TRANSFERRED')
    assert.equal(await page.locator('.auto-cancellations-table tbody tr').count(), 1)
    for (const route of ['/borrow-policy', '/categories']) {
      await page.goto(origin + route)
      await page.locator('table tbody tr').first().waitFor()
      for (const width of widths) {
        await page.setViewportSize({ width, height: 844 })
        await page.waitForTimeout(250)
        await check(route.slice(1) + '-' + width, async () => {
          await noOverflow()
          await page.locator('table').first().scrollIntoViewIfNeeded()
          assert.ok(await page.locator('table tbody tr').first().evaluate(row =>
            [...row.cells].every(cell => cell.scrollWidth <= cell.clientWidth + 2)), 'Table cell overlaps adjacent column')
          for (const label of ['Trang trước', 'Trang sau']) {
            const button = page.getByRole('button', { name: label, exact: true })
            assert.equal(await button.evaluate(e => getComputedStyle(e).whiteSpace), 'nowrap')
          }
        })
      }
    }
    await page.goto(origin + '/loans/reminders')
    await page.locator('.reminder-history-table tbody tr').waitFor()
    for (const width of [...new Set([...widths, 1165, 960, 640])]) {
      await page.setViewportSize({ width, height: 642 })
      await page.waitForTimeout(250)
      await check('reminder-history-' + width, async () => {
        await noOverflow()
        const table = page.locator('.reminder-history-table')
        if (width >= 768) {
          assert.ok(await table.locator('thead').evaluate(e => e.clientHeight < 80), 'Headers wrap into vertical letters')
          assert.ok(await table.locator('td').nth(1).evaluate(e => e.clientWidth >= 220), 'Reader column squeezed')
        } else {
          assert.equal(await table.locator('tbody tr').evaluate(e => getComputedStyle(e).display), 'grid')
          assert.equal(await table.locator('td[data-label]').count(), 8)
        }
        const detail = page.getByRole('button', { name: 'Xem chi tiết', exact: true })
        await detail.scrollIntoViewIfNeeded()
        await detail.click({ trial: true })
      })
    }
    await page.getByRole('button', { name: 'Kích hoạt gửi / Chạy thử', exact: true }).click()
    await page.locator('dialog table').waitFor()
    for (const width of widths) {
      await page.setViewportSize({ width, height: 844 })
      await page.waitForTimeout(250)
      await check('reminder-' + width, async () => {
        await noOverflow()
        assert.ok(await page.locator('dialog').evaluate(e => e.scrollWidth <= e.clientWidth + 1), 'Modal overflows')
        assert.equal(await page.locator('dialog table').evaluate(e => getComputedStyle(e.parentElement).overflowX), 'auto')
        const preview = page.getByRole('button', { name: 'Xem mẫu', exact: true })
        await preview.scrollIntoViewIfNeeded()
        await preview.click({ trial: true })
      })
    }
    await page.keyboard.press('Escape')
    await page.goto(origin + '/reports/loans')
    await page.locator('svg[role="group"]').waitFor()
    for (const width of widths) {
      await page.setViewportSize({ width, height: 844 })
      await page.waitForTimeout(300)
      await check('chart-' + width, async () => {
        await noOverflow()
        const chart = page.locator('svg[role="group"]')
        await chart.scrollIntoViewIfNeeded()
        assert.ok(await chart.evaluate(e => e.getBoundingClientRect().width <= e.parentElement.clientWidth + 1))
        await page.locator('[data-testid^="daily-bar-"]').first().focus()
        await page.keyboard.press('Enter')
        assert.ok(await page.getByRole('status').filter({ hasText: '01/10/2026' }).isVisible())
      })
    }
    for (const role of ['PUBLIC', 'READER', 'LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN']) {
      await auth(role)
      for (const width of widths) {
        await page.setViewportSize({ width, height: 844 })
        await page.goto(origin + (role === 'PUBLIC' || role === 'READER' ? '/catalog' : '/dashboard'))
        await page.locator('.lai-launcher').waitFor()
        await check(role + '-chat-' + width, async () => {
          await noOverflow()
          await page.locator('.lai-launcher').click()
          await page.locator('.lai-window').waitFor()
          await page.waitForTimeout(250)
          const bounds = await page.locator('.lai-window').boundingBox()
          assert.ok(bounds.x >= 0 && bounds.x + bounds.width <= width + 1 && bounds.y >= 0 && bounds.y + bounds.height <= 845)
          if (width <= 520) {
            for (let i = 0; i < 16; i++) {
              await page.keyboard.press('Tab')
              assert.ok(await page.evaluate(() => Boolean(document.activeElement.closest('.lai-window'))), 'Focus escaped mobile chat')
            }
            const send = await page.getByRole('button', { name: 'Gửi câu hỏi', exact: true }).boundingBox()
            assert.ok(send.width >= 44 && send.height >= 44)
          }
        })
      }
    }
    await auth('READER')
    await page.goto(origin + '/change-password')
    await page.getByText('Thông tin cá nhân', { exact: true }).waitFor()
    await page.setViewportSize({ width: 320, height: 568 })
    await page.waitForTimeout(250)
    await check('profile-labels-320', async () => {
      await noOverflow()
      assert.ok(await page.locator('main input,main textarea').evaluateAll(es => es.every(e => e.labels.length > 0)))
    })
    for (const size of [{ width: 844, height: 390 }, { width: 768, height: 360 }, { width: 320, height: 320 }]) {
      await page.setViewportSize(size)
      await page.goto(origin + '/catalog')
      await page.locator('.lai-launcher').click()
      await page.waitForTimeout(250)
      await check(`chat-short-${size.width}-${size.height}`, async () => {
        await noOverflow()
        const send = await page.getByRole('button', { name: 'Gửi câu hỏi', exact: true }).boundingBox()
        assert.ok(send.y >= 0 && send.y + send.height <= size.height)
      })
    }
    assert.deepEqual(errors, [])
  } finally {
    fs.writeFileSync(path.join(output, 'results.json'), JSON.stringify({ mockOnly: true, results, errors }, null, 2))
    await browser.close()
  }
  console.log(JSON.stringify({ checks: results.length, failed: results.filter(r => !r.passed), errors, output }, null, 2))
  if (results.some(r => !r.passed)) process.exitCode = 1
})().catch(error => { console.error(error); process.exitCode = 1 })
