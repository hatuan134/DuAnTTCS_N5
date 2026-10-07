import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import vm from 'node:vm'
import ts from 'typescript'
import { createRequire } from 'node:module'
import { renderToStaticMarkup } from 'react-dom/server'
const require = createRequire(import.meta.url), react = require('react')
const root = new URL('../src/features/s3-01-loans/', import.meta.url)
function load(file, imports = {}) {
  const compiled = ts.transpileModule(readFileSync(new URL(file, root), 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX },
  }).outputText
  const context = { exports: {}, require: (name) => imports[name] ?? require(name) }
  vm.runInNewContext(compiled, context)
  return context.exports
}
const serviceModule = load('loanService.ts', { '../../core/api/apiClient': { apiClient: {} } })
const loan = {
  id: 81, loanNumber: 'PM-SAVED-000081', reservationId: 21, readerId: 12, readerName: 'Nguyễn Văn An',
  createdById: 3, createdByName: 'Trần Thị Thủ Thư', borrowedAt: '2026-10-07T23:30:00Z',
  items: [{ id: 91, copyId: 31, barcode: 'LIB-031', bookId: 7, bookTitle: 'Mắt biếc',
    borrowedAt: '2026-10-07T23:30:00Z', dueAt: '2026-10-22T10:00:00Z' }],
}
const row = { ...loan, itemCount: 1 }
const settle = () => new Promise((done) => setImmediate(done))
async function pageFixture(file, { data, error, role = 'LIBRARIAN', id = '81', created = false } = {}) {
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
    'react-router-dom': {
      useParams: () => ({ loanId: id }), useLocation: () => ({ state: { loanCreated: created } }),
      Link: ({ to, children, ...props }) => react.createElement('a', { ...props, href: to }, children),
    },
    '../../components/ui/Button': { __esModule: true, default: ({ children, loading, ...props }) => react.createElement('button', { ...props, disabled: props.disabled || loading }, children) },
    '../../components/ui/Card': { __esModule: true, default: ({ children, ...props }) => react.createElement('div', props, children) },
    '../../components/ui/LoadingState': { __esModule: true, default: () => react.createElement('p', {}, 'Đang tải') },
    '../../components/ui/EmptyState': { __esModule: true, default: ({ title, description }) => react.createElement('p', {}, title, description) },
    '../../components/ui/PageHeader': { __esModule: true, default: ({ title, description, action }) => react.createElement('header', {}, title, description, action) },
    '../../components/ui/TablePagination': { __esModule: true, default: ({ page, totalItems, totalPages }) => react.createElement('p', {}, `Trang ${page}/${totalPages}, tổng ${totalItems}`) },
    '../../components/ui/TableActionButton': { tableActionClassName: () => 'action' },
    '../../hooks/useTablePagination': load('../../hooks/useTablePagination.ts', { react: hooks }),
    '../../core/auth/authStorage': { getCurrentUser: () => ({ role, id: 9, fullName: 'Người đang xem' }) },
    '../s1-02-user-management/accountService': { getApiErrorMessage: (e) => e.message },
    './loanService': { ...serviceModule, loanService: {
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
test('loan client reads list/detail through the existing authenticated API client', async () => {
  const requests = []
  const f = load('loanService.ts', { '../../core/api/apiClient': { apiClient: {
    async get(url) { requests.push(url); return { data: url === '/loans' ? [row] : loan } },
  } } })
  assert.equal((await f.loanService.list())[0].id, 81)
  assert.equal((await f.loanService.detail(81)).loanNumber, loan.loanNumber)
  assert.deepEqual(requests, ['/loans', '/loans/81'])
})
test('detail displays all six required fields from saved data in Vietnam time', async () => {
  const f = await pageFixture('LoanDetailPage.tsx', { created: true })
  assert.match(f.before, /Đang tải/)
  const html = f.html()
  for (const text of ['Mã phiếu mượn', 'PM-SAVED-000081', 'Mã vạch bản sao', 'LIB-031', 'Tên đầu sách', 'Mắt biếc', 'Ngày mượn', '08/10/2026', 'Hạn trả đã lưu', '22/10/2026', '17:00:00', 'Người lập phiếu', 'Trần Thị Thủ Thư']) assert.ok(html.includes(text), text)
  assert.match(html, /Đã lập phiếu mượn thành công/)
  assert.doesNotMatch(html, /Người đang xem/)
  assert.match(html, /href="\/book-copies\/31"/)
  assert.match(html, /href="\/books\/7"/)
  assert.deepEqual(f.calls, [81])
})
test('reopening a loan fetches the same saved fields without navigation state', async () => {
  const first = await pageFixture('LoanDetailPage.tsx'), reopened = await pageFixture('LoanDetailPage.tsx')
  assert.equal(first.html(), reopened.html())
  assert.deepEqual(reopened.calls, [81])
  assert.doesNotMatch(reopened.html(), /Đã lập phiếu mượn thành công/)
})
test('detail shows every legacy item and explains a missing saved due date', async () => {
  const data = structuredClone(loan); data.reservationId = null
  data.items.push({ id: 92, copyId: 32, barcode: 'LIB-032', bookId: 8, bookTitle: 'Dế Mèn', borrowedAt: loan.borrowedAt, dueAt: null })
  const f = await pageFixture('LoanDetailPage.tsx', { data })
  assert.match(f.html(), /LIB-031/); assert.match(f.html(), /LIB-032/); assert.match(f.html(), /Dế Mèn/)
  assert.match(f.html(), /Phiếu cũ chưa có hạn trả được lưu/)
  assert.doesNotMatch(f.html(), /Xem đơn #/)
})
test('legacy header without items shows an honest empty message', async () => {
  const f = await pageFixture('LoanDetailPage.tsx', { data: { ...loan, items: [] } })
  assert.match(f.html(), /chưa có chi tiết bản sao/); assert.doesNotMatch(f.html(), /LIB-031/)
})
test('missing loan displays retry without fabricated or stale fields', async () => {
  const f = await pageFixture('LoanDetailPage.tsx', { error: new Error('Không tìm thấy phiếu mượn.') })
  assert.match(f.html(), /Không tìm thấy phiếu mượn/); assert.match(f.html(), /Thử lại/)
  assert.doesNotMatch(f.html(), /PM-SAVED|LIB-031/)
})
test('reader and invalid ids cannot load staff details', async () => {
  const reader = await pageFixture('LoanDetailPage.tsx', { role: 'READER' })
  assert.match(reader.html(), /Bạn không có quyền/); assert.deepEqual(reader.calls, [])
  for (const id of ['0', '-1', 'abc', '9007199254740992']) {
    const f = await pageFixture('LoanDetailPage.tsx', { id })
    assert.match(f.html(), /Mã phiếu mượn không hợp lệ/); assert.deepEqual(f.calls, [])
  }
})
test('loan list offers saved links and displays the original creator', async () => {
  const f = await pageFixture('LoanListPage.tsx'), html = f.html()
  for (const text of ['PM-SAVED-000081', 'Nguyễn Văn An', 'Trần Thị Thủ Thư', '08/10/2026', 'Xem phiếu']) assert.ok(html.includes(text))
  assert.match(html, /href="\/loans\/81"/); assert.deepEqual(f.calls, ['list'])
})
test('loan list uses existing ten-row pagination', async () => {
  const rows = Array.from({ length: 11 }, (_, i) => ({ ...row, id: 81 + i, loanNumber: `PM-PAGE-${i}` }))
  const f = await pageFixture('LoanListPage.tsx', { data: rows })
  assert.match(f.html(), /Trang 1\/2, tổng 11/); assert.match(f.html(), /PM-PAGE-9/)
  assert.doesNotMatch(f.html(), /PM-PAGE-10/)
})
test('loan list distinguishes empty, error and forbidden states', async () => {
  const empty = await pageFixture('LoanListPage.tsx', { data: [] })
  assert.match(empty.html(), /Chưa có phiếu mượn/)
  const failed = await pageFixture('LoanListPage.tsx', { error: new Error('Lỗi tải phiếu') })
  assert.match(failed.html(), /Lỗi tải phiếu/); assert.doesNotMatch(failed.html(), /PM-SAVED/)
  const forbidden = await pageFixture('LoanListPage.tsx', { role: 'READER' })
  assert.match(forbidden.html(), /Bạn không có quyền/); assert.deepEqual(forbidden.calls, [])
})
test('dates keep Vietnam calendar date without inventing missing timestamps', () => {
  assert.equal(serviceModule.formatLoanTimestamp('2026-10-07T23:30:00Z', true), '08/10/2026')
  assert.equal(serviceModule.formatLoanTimestamp(null), 'Chưa có thông tin')
  assert.equal(serviceModule.formatLoanTimestamp('invalid'), 'Chưa có thông tin')
})
test('feature registers list/detail routes and staff sidebar roles', () => {
  const f = load('feature.tsx', {
    './LoanListPage': { __esModule: true, default: () => null }, './LoanDetailPage': { __esModule: true, default: () => null }, './loanService': serviceModule,
  }).default
  assert.deepEqual(Array.from(f.appRoutes, (r) => r.path), ['loans', 'loans/:loanId'])
  assert.equal(f.navItems[0].label, 'Phiếu mượn'); assert.equal(f.navItems[0].to, '/loans')
  assert.deepEqual(Array.from(f.navItems[0].roles), ['LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN'])
})
