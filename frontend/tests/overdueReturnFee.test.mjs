import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import { createRequire } from 'node:module'
import vm from 'node:vm'
import ts from 'typescript'
import { renderToStaticMarkup } from 'react-dom/server'
const require = createRequire(import.meta.url), react = require('react')
function load(path, imports) {
  const context = { exports: {}, require: name => imports[name] ?? require(name) }
  vm.runInNewContext(ts.transpileModule(readFileSync(new URL(path, import.meta.url), 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX },
  }).outputText, context)
  return context.exports
}
const details = load('../src/features/s3-07-returns/ReturnFeeDetails.tsx', {
  '../s3-01-loans/loanService': { formatLoanTimestamp: value => value ?? 'Chưa có thông tin' },
  'react-router-dom': { Link: ({ to, children, ...props }) => react.createElement('a', { href: to, ...props }, children) },
})
const fee = {
  status: 'READY', message: '', policyVersion: 'S4-01.1-OPEN-DAYS-HALF-UP-V1',
  dueAt: '2026-10-07T17:00:00+07:00', returnedAt: '2026-10-12T09:00:00+07:00',
  overdueDays: 3, dailyRateVnd: 5000, rawAmountVnd: 15000, amountVnd: 15000, feeId: 17,
  excludedDates: ['2026-10-10', '2026-10-11'], weeklySchedule: [],
  closedDates: [{ date: '2026-10-10', reason: 'Ngày nghỉ lễ' }],
}
function html(data, preview = false) {
  return renderToStaticMarkup(react.createElement(details.default, { fee: data, preview }))
}
test('fee details display saved due date, return date, open days, daily price and receivable', () => {
  const result = html(fee)
  for (const value of ['2026-10-07', '2026-10-12', '3 ngày', '5.000 VND', '15.000 VND', '#17']) assert.ok(result.includes(value), value)
  assert.match(result, /Ngày nghỉ lễ/); assert.match(result, /Đóng cửa theo lịch tuần/)
})
test('same persisted fee renders identically for two staff views', () => {
  assert.equal(html(fee), html(structuredClone(fee)))
})
test('zero fee clearly states 0 VND and no receivable even when no price was needed', () => {
  const result = html({ ...fee, overdueDays: 0, dailyRateVnd: null, rawAmountVnd: 0, amountVnd: 0, feeId: null })
  assert.match(result, /0 VND/); assert.match(result, /Không tạo khoản phải thu/); assert.match(result, /Chưa khai báo/)
})
test('unconfigured fee is unknown, never shown as a zero charge', () => {
  const result = html({ ...fee, status: 'OVERDUE_FEE_NOT_CONFIGURED', message: 'Chưa khai báo mức phí trễ hạn.',
    dailyRateVnd: null, rawAmountVnd: null, amountVnd: null, feeId: null }, true)
  assert.match(result, /Chưa khai báo mức phí/); assert.match(result, /Chưa xác định/)
  assert.doesNotMatch(result, /Không tạo khoản phải thu/)
})
test('historical return with no snapshot explains absence without recalculating', () => {
  assert.match(html(null), /Phiếu cũ chưa ghi nhận/)
})

test('percentage details use the saved price, percentage and cap rather than the legacy daily amount', () => {
  const result = html({ ...fee, policyVersion: 'PERCENT-OPEN-DAYS-HALF-UP-V1',
    percentagePolicyId: 5, coverPrice: 100000, dailyPercent: 1, capPercent: 10,
    dailyRateVnd: null, rawAmountVnd: 3000, amountVnd: 3000 }, true)
  for (const value of ['100.000', '1%', '10%', '3.000']) assert.ok(result.includes(value), value)
  assert.doesNotMatch(result, /5.000 VND/)
})
test('preview tells staff that actual return time and active rate are applied at confirmation', () => {
  assert.match(html(fee, true), /Phí trễ hạn dự kiến/)
  assert.match(html(fee, true), /Khi xác nhận/)
  assert.doesNotMatch(html(fee, true), /Đã tạo khoản phải thu/)
})
test('fee settings API reuses existing client, exact path, and zero is sent without falsy substitution', async () => {
  const calls = [], saved = { dailyRateVnd: 0 }
  const service = load('../src/features/s1-09-library-config/librarySettingsService.ts', {
    '../../core/api/apiClient': { apiClient: {
      get: async path => { calls.push([path]); return { data: saved } },
      put: async (path, body) => { calls.push([path, body]); return { data: saved } },
    } },
  }).librarySettingsService
  assert.equal(await service.getOverdueFee(), saved)
  assert.equal(await service.updateOverdueFee(0), saved)
  assert.equal(calls[0][0], '/library-settings/overdue-fee')
  assert.equal(calls[1][0], '/library-settings/overdue-fee')
  assert.equal(calls[1][1].dailyRateVnd, 0)
})
