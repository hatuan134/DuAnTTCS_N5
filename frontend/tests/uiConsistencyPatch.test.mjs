import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import { createRequire } from 'node:module'
import vm from 'node:vm'
import ts from 'typescript'
const require = createRequire(import.meta.url)
const src = path => readFileSync(new URL(path, import.meta.url), 'utf8')
const load = (path, imports) => {
  const context = { exports: {}, require: key => imports[key] ?? require(key) }
  vm.runInNewContext(ts.transpileModule(src(path), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX },
  }).outputText, context)
  return context.exports
}

test('nhà xuất bản được POST xuống API ngay khi thêm, không phụ thuộc lưu đầu sách', async () => {
  const calls = []
  const { catalogService } = load('../src/features/s1-08-catalog/catalogService.ts', {
    '../../core/api/apiClient': { apiClient: {
      post: async (...args) => { calls.push(args); return { data: { id: 123, name: 'NXB Trẻ' } } },
    } },
  })
  const created = await catalogService.createPublisher('NXB Trẻ')
  assert.equal(created.name, 'NXB Trẻ')
  assert.equal(calls[0][0], '/publishers')
  assert.equal(calls[0][1].name, 'NXB Trẻ')
})

test('sổ lịch sử đã trả giữ nguyên phân trang 20 dòng/trang', () => {
  const history = src('../src/features/s3-04-my-borrowed-books/MyReturnedBooksPanel.tsx')
  assert.match(history, /pageSize=\{20\}/)
  assert.match(history, /Mỗi trang tối đa 20 dòng/)
})

test('các danh sách mới dùng hook phân trang 10 mặc định và đánh STT theo trang', () => {
  const hook = src('../src/hooks/useTablePagination.ts')
  assert.match(hook, /TABLE_PAGE_SIZE = 10/)
  for (const path of [
    '../src/features/s3-04-my-borrowed-books/MyBorrowedBooksPage.tsx',
    '../src/features/s3-06-auto-cancellations/AutoCancelledReservationsPage.tsx',
    '../src/features/s3-09-overdue-loans/OverdueLoansPage.tsx',
  ]) {
    const page = src(path)
    assert.match(page, /useTablePagination\(/)
    assert.match(page, /pagination\.startIndex \+ index \+ 1/)
  }
})

test('mẫu xác nhận xóa và khóa dùng chung', () => {
  const account = src('../src/features/s1-02-user-management/UserManagementPage.tsx')
  const shelf = src('../src/features/s1-09-library-config/LibrarySettingsPage.tsx')
  assert.match(account, /<ConfirmActionDialog/)
  assert.match(shelf, /<ConfirmActionDialog/)
  assert.doesNotMatch(account, /window\.confirm/)
})
