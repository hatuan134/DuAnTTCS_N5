import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import vm from 'node:vm'
import ts from 'typescript'
const context = { exports: {} }
vm.runInNewContext(ts.transpileModule(readFileSync(new URL('../src/features/s1-10-audit-log/auditLabels.ts', import.meta.url), 'utf8'), {
  compilerOptions: { module: ts.ModuleKind.CommonJS },
}).outputText, context)
const { auditActionLabel, auditEntityLabel, auditTargetLabel, auditDetailLabel } = context.exports

test('audit: Vietnamese labels for the raw catalog actions reported by users', () => {
  for (const [code, expected] of [
    ['BOOK_CATALOGED', 'Biên mục đầu sách'], ['AUTHOR_CREATED', 'Thêm tác giả'],
    ['AUTHOR_DELETED', 'Xóa tác giả'], ['CATEGORY_CREATED', 'Thêm thể loại'],
    ['CLOSED_DATES_BULK_CREATED', 'Thêm nhiều ngày nghỉ'],
  ]) assert.equal(auditActionLabel(code, code), expected)
})
test('audit: existing Vietnamese labels and filter codes remain meaningful', () => {
  assert.equal(auditActionLabel('LOGIN', 'Đăng nhập'), 'Đăng nhập')
  assert.equal(auditActionLabel('LOGIN_SUCCESS', 'LOGIN_SUCCESS'), 'Đăng nhập thành công')
  assert.equal(auditActionLabel('FUTURE_ACTION', 'Nhãn mới do máy chủ cung cấp'), 'Nhãn mới do máy chủ cung cấp')
  assert.equal(auditActionLabel('FUTURE_ACTION', 'FUTURE_ACTION'), 'Hoạt động khác')
  assert.equal(auditActionLabel('__proto__', '__proto__'), 'Hoạt động khác')
})
test('audit: translate entity prefixes while preserving IDs and human titles', () => {
  assert.equal(auditEntityLabel('BOOK'), 'Đầu sách')
  assert.equal(auditTargetLabel({ target: 'BOOK #9385', entityType: 'BOOK', targetType: 'BOOK' }), 'Đầu sách #9385')
  assert.equal(auditTargetLabel({ target: 'Tác giả BOOK', entityType: 'AUTHOR', targetType: 'AUTHOR' }), 'Tác giả BOOK')
  assert.equal(auditTargetLabel({ target: 'FUTURE #42', entityType: 'FUTURE', targetType: 'FUTURE' }), 'FUTURE #42')
})
test('audit: display formatting never mutates the record or its raw action code', () => {
  const item = Object.freeze({ target: 'AUTHOR #3868', entityType: 'AUTHOR', targetType: 'AUTHOR', action: 'AUTHOR_DELETED', actionLabel: 'AUTHOR_DELETED', detail: 'AUTHOR_DELETED.' })
  assert.equal(auditDetailLabel(item), 'Xóa tác giả.')
  assert.equal(auditTargetLabel(item), 'Tác giả #3868')
  assert.equal(item.action, 'AUTHOR_DELETED')
  assert.equal(item.detail, 'AUTHOR_DELETED.')
  assert.equal(auditDetailLabel({ ...item, detail: 'Đã xóa tác giả theo yêu cầu quản lý.' }), 'Đã xóa tác giả theo yêu cầu quản lý.')
})
