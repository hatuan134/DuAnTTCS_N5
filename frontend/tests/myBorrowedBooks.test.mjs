import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import vm from 'node:vm'
import ts from 'typescript'
import { createRequire } from 'node:module'
import { renderToStaticMarkup } from 'react-dom/server'
const require = createRequire(import.meta.url), react = require('react')
const root = new URL('../src/features/s3-04-my-borrowed-books/', import.meta.url)
function load(file, imports = {}) {
  const compiled = ts.transpileModule(readFileSync(new URL(file, root), 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX },
  }).outputText
  const context = { exports: {}, require: (name) => imports[name] ?? require(name) }
  vm.runInNewContext(compiled, context)
  return context.exports
}
const dueWarningModule = load('BorrowedBookDueWarning.tsx', {
  '../../components/ui/StatusBadge': load('../../components/ui/StatusBadge.tsx'),
})
const serviceModule = load('../s3-01-loans/loanService.ts', { '../../core/api/apiClient': { apiClient: {} } })
const loan = {
  id: 81, loanNumber: 'PM-SAVED-000081', reservationId: 21, readerId: 12, readerName: 'Nguyễn Văn An',
  createdById: 3, createdByName: 'Trần Thị Thủ Thư', borrowedAt: '2026-10-07T23:30:00Z',
  items: [{ id: 91, copyId: 31, barcode: 'LIB-031', bookId: 7, bookTitle: 'Mắt biếc',
    borrowedAt: '2026-10-07T23:30:00Z', dueAt: '2026-10-22T10:00:00Z' }],
}
const row = { ...loan, itemCount: 1 }
const settle = () => new Promise((done) => setImmediate(done))
async function pageFixture(file, { data, error, role = 'READER', id = '81', created = false } = {}) {
  const states = [], effects = [], calls = []
  let cursor = 0
  const hooks = {
    ...react,
    useState(initial) {
      const index = cursor++
      if (!(index in states)) states[index] = initial
      return [states[index], (value) => { states[index] = typeof value === 'function' ? value(states[index]) : value }]
    },
    useEffect(callback, dependencies) {
      const index = cursor++, previous = states[index]
      if (!previous || dependencies.some((value, i) => value !== previous[i])) effects.push(callback)
      states[index] = dependencies
    },
    useMemo(callback) { return callback() },
  }
  const page = load(file, {
    react: hooks,
    './BorrowedBookDueWarning': dueWarningModule,
    './MyReturnedBooksPanel': { __esModule: true, default: () => null },
    'react-router-dom': {
      useParams: () => ({ loanId: id }), useLocation: () => ({ state: { loanCreated: created } }),
      Link: ({ to, children, ...props }) => react.createElement('a', { ...props, href: to }, children),
    },
    '../../components/ui/Button': { __esModule: true, default: ({ children, loading, ...props }) => react.createElement('button', { ...props, disabled: props.disabled || loading }, children) },
    '../../components/ui/Card': { __esModule: true, default: ({ children, ...props }) => react.createElement('div', props, children) },
    '../../components/ui/LoadingState': { __esModule: true, default: () => react.createElement('p', {}, 'Đang tải') },
    '../../components/ui/EmptyState': { __esModule: true, default: ({ title, description }) => react.createElement('p', {}, title, description) },
    // Page uses the shared 3-second notification; timing itself is covered by feedbackAlert.test.mjs.
    '../../components/ui/FeedbackAlert': { __esModule: true, default: ({ message }) => react.createElement('p', { role: 'status' }, message) },
    '../../components/ui/PageHeader': { __esModule: true, default: ({ title, description, action }) => react.createElement('header', {}, title, description, action) },
    '../../components/ui/TablePagination': { __esModule: true, default: ({ page, totalItems, totalPages }) => react.createElement('p', {}, `Trang ${page}/${totalPages}, tổng ${totalItems}`) },
    '../../components/ui/TableActionButton': { tableActionClassName: () => 'action' },
    '../../core/auth/authStorage': { getCurrentUser: () => ({ role, id: 9, fullName: 'Người đang xem' }) },
    '../s1-02-user-management/accountService': { getApiErrorMessage: (e) => e.message },
    './myBorrowedBooksService': { myBorrowedBooksService: { async list() { calls.push('list'); if (error) throw error; return data ?? [] } } },
    '../s3-01-loans/loanService': { ...serviceModule, loanService: {
      async detail(loanId) { calls.push(loanId); if (error) throw error; return data ?? structuredClone(loan) },
      async list() { calls.push('list'); if (error) throw error; return data ?? [row] },
    } },
  }).default
  function render() {
    cursor = 0
    const outer = page()
    return typeof outer.type === 'function' ? outer.type(outer.props) : outer
  }
  const before = renderToStaticMarkup(render())
  for (const effect of effects.splice(0)) effect()
  await settle()
  return { render, calls, before, html: () => renderToStaticMarkup(render()) }
}
const book = { id: 1, bookTitle: 'Mắt biếc', barcode: 'LIB-001', borrowedAt: '2026-10-07T23:30:00Z', dueAt: '2026-10-08T10:00:00Z', remainingDays: 0 }
test('empty reader sees honest empty state', async () => {
  const f = await pageFixture('MyBorrowedBooksPage.tsx', { data: [] })
  assert.match(f.before, /Đang tải/); assert.match(f.html(), /Bạn không có sách đang mượn/)
})
test('one book shows all required fields in Vietnam calendar and today zero', async () => {
  const f = await pageFixture('MyBorrowedBooksPage.tsx', { data: [book] })
  for (const value of ['Mắt biếc', 'LIB-001', '08/10/2026', '0 ngày', 'Ngày mượn', 'Hạn trả', 'Số ngày còn lại']) assert.ok(f.html().includes(value), value)
})
test('all copies including same title negative days and missing due dates are shown', async () => {
  const f = await pageFixture('MyBorrowedBooksPage.tsx', { data: [book,
    { ...book, id: 2, barcode: 'LIB-002', remainingDays: -1 },
    { ...book, id: 3, barcode: 'LIB-003', dueAt: null, remainingDays: null }] })
  for (const value of ['LIB-001', 'LIB-002', 'LIB-003', '-1 ngày', 'Chưa có hạn trả', 'Chưa xác định']) assert.ok(f.html().includes(value))
  assert.match(f.html(), /Sắp đến hạn/); assert.match(f.html(), /Quá hạn/); assert.match(f.html(), /Trễ 1 ngày/)
  assert.doesNotMatch(f.html(), /href=.*loans/)
})
test('staff never requests reader books', async () => {
  for (const role of ['ADMIN', 'LIBRARY_MANAGER', 'LIBRARIAN']) {
    const f = await pageFixture('MyBorrowedBooksPage.tsx', { role })
    assert.deepEqual(f.calls, []); assert.match(f.html(), /dành cho Bạn đọc/)
  }
})
test('API error is not confused with no borrowed books', async () => {
  const f = await pageFixture('MyBorrowedBooksPage.tsx', { error: new Error('Lỗi máy chủ') })
  assert.match(f.html(), /Lỗi máy chủ/); assert.match(f.html(), /Chưa tải được danh sách/)
  assert.doesNotMatch(f.html(), /Bạn không có sách đang mượn/)
})
test('client supplies no reader identity', async () => {
  let url
  const f = load('myBorrowedBooksService.ts', { '../../core/api/apiClient': { apiClient: { async get(value) { url = value; return { data: [book] } } } } })
  assert.equal((await f.myBorrowedBooksService.list())[0].barcode, 'LIB-001')
  assert.equal(url, '/loans/me/borrowed-books')
})

for (const [days, label, late] of [[5, null, null], [3, null, null], [2, 'Sắp đến hạn', null],
  [1, 'Sắp đến hạn', null], [0, 'Sắp đến hạn', null], [-1, 'Quá hạn', 'Trễ 1 ngày'], [-12, 'Quá hạn', 'Trễ 12 ngày']]) {
  test(`day ${days}: both mobile and desktop rows display the exact warning`, async () => {
    const f = await pageFixture('MyBorrowedBooksPage.tsx', { data: [{ ...book, remainingDays: days }] })
    const html = f.html()
    // The explanation includes Sắp đến hạn; count actual badge text only.
    const badges = html.match(/>Sắp đến hạn<|>Quá hạn</g) ?? []
    assert.equal(badges.length, label ? 2 : 0)
    if (label) assert.equal(badges.filter((value) => value === `>${label}<`).length, 2)
    if (late) assert.equal((html.match(new RegExp(`>${late}<`, 'g')) ?? []).length, 2)
    else assert.doesNotMatch(html, />Trễ \d+ ngày</)
  })
}
test('missing legacy deadline has no warning on either layout', async () => {
  const f = await pageFixture('MyBorrowedBooksPage.tsx', { data: [{ ...book, dueAt: null, remainingDays: null }] })
  assert.doesNotMatch(f.html(), />Sắp đến hạn<|>Quá hạn<|>Trễ \d+ ngày</)
})
