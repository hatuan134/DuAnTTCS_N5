import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'

const source = (path) => readFileSync(new URL(path, import.meta.url), 'utf8')
const page = source('../src/features/s4-07-loan-report/LoanReportPage.tsx')
const api = source('../src/features/s4-07-loan-report/loanReportService.ts')

test('two Top 10 tables share the same report response', () => {
  assert.match(api, /topBooks: TopBorrowedBook\[\]/)
  assert.match(api, /topQueuedBooks: TopQueuedBook\[\]/)
  assert.match(page, /Top 10 đầu sách được mượn nhiều nhất/)
  assert.match(page, /Top 10 đầu sách có hàng đợi đặt giữ dài nhất/)
  assert.match(page, /report\.topQueuedBooks\.map/)
  assert.match(page, /formatNumber\(book\.waitCount\)/)
  assert.match(page, /formatNumber\(book\.stockCount\)/)
})

test('queue shows timestamp, reload and empty message without changing report routes', () => {
  assert.match(page, /queueSnapshotAt/)
  assert.match(page, /Asia\/Ho_Chi_Minh/)
  assert.match(page, /setRefreshKey\(key => key \+ 1\)/)
  assert.match(page, /Chưa có đầu sách nào đang được đặt giữ/)
  assert.match(page, /getCurrentUser\(\)\?\.role === 'LIBRARY_MANAGER'/)
  assert.match(page, /<Navigate to="\/dashboard" replace/)
})

test('date filter and refresh re-fetch the complete report, without chart expansion', () => {
  assert.match(page, /getLoanReport\(startDate, endDate, controller\.signal\)/)
  assert.match(page, /\[allowed, dateError, startDate, endDate, refreshKey\]/)
  assert.doesNotMatch(page, /<BarChart/)
})
