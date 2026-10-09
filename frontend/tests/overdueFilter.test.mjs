import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import vm from 'node:vm'
import ts from 'typescript'

const source = readFileSync(new URL('../src/features/s3-09-overdue-loans/overdueFilter.ts', import.meta.url), 'utf8')
const context = { exports: {}, require() { throw new Error('Bộ lọc không được gọi API') } }
vm.runInNewContext(ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.CommonJS },
}).outputText, context)
const { validateOverdueFilter, filterOverdueLoans, countOverdueLoanVouchers, describeOverdueFilter } = context.exports

const loans = [
  { loanId: 11, itemId: 1, overdueDays: 7 },
  { loanId: 12, itemId: 2, overdueDays: 31 },
  { loanId: 12, itemId: 3, overdueDays: 31 },
  { loanId: 13, itemId: 4, overdueDays: 8 },
  { loanId: 14, itemId: 5, overdueDays: 30 },
  { loanId: 15, itemId: 6, overdueDays: 0 },
  { loanId: 16, itemId: 7, overdueDays: 45 },
]
const days = entries => [...entries.map(entry => entry.overdueDays)]

function apply(min, max, exclusive = false) {
  const validation = validateOverdueFilter(min, max, exclusive)
  assert.equal(validation.ok, true)
  return filterOverdueLoans(loans, validation.filter)
}

test('Trên 7 ngày chỉ lấy từ 8; giữ thứ tự giảm dần', () => {
  assert.deepEqual(days(apply('7', '', true)), [45, 31, 31, 30, 8])
})
test('Trên 30 ngày chỉ lấy từ 31 và đếm số phiếu không trùng', () => {
  const found = apply('30', '', true)
  assert.deepEqual(days(found), [45, 31, 31])
  assert.equal(countOverdueLoanVouchers(found), 2)
})
test('Khoảng từ 7 đến 30 bao gồm hai đầu mút và vẫn giảm dần', () => {
  assert.deepEqual(days(apply('7', '30')), [30, 8, 7])
})
test('Không có kết quả nếu mức quá hạn quá lớn', () => {
  assert.deepEqual(days(apply('99', '')), [])
})
test('Chỉ có mức tối đa và từ ngày 0 hỗ trợ phiếu trễ 0 ngày mở cửa', () => {
  assert.deepEqual(days(apply('', '7')), [7, 0])
  assert.deepEqual(days(apply('0', '0')), [0])
})
test('Chặn khoảng tối thiểu lớn hơn tối đa hoặc không còn ngày hợp lệ do điều kiện trên', () => {
  for (const args of [['31', '7', false], ['7', '7', true], ['10', '9', true]]) {
    const result = validateOverdueFilter(...args)
    assert.equal(result.ok, false)
    assert.match(result.message, /Khoảng lọc không hợp lệ/)
  }
})
test('Chặn giá trị âm, số thập phân, ký tự lạ, số quá lớn', () => {
  for (const value of ['-1', '2.5', 'abc', '1e3', '999999999999999999']) {
    assert.equal(validateOverdueFilter(value, '', false).ok, false)
    assert.equal(validateOverdueFilter('', value, false).ok, false)
  }
})
test('Không chọn điều kiện trả lại toàn bộ danh sách, không làm biến đổi dữ liệu gốc', () => {
  const before = days(loans)
  assert.equal(apply('', '').length, loans.length)
  assert.equal(filterOverdueLoans(loans, null).length, loans.length)
  assert.deepEqual(days(loans), before)
})
test('Mô tả điều kiện đúng ngữ nghĩa lọc', () => {
  assert.match(describeOverdueFilter({ minimum: 7, maximum: 30, exclusiveMinimum: false }), /Từ 7 đến 30/)
  assert.match(describeOverdueFilter({ minimum: 30, maximum: null, exclusiveMinimum: true }), /Trên 30/)
})
