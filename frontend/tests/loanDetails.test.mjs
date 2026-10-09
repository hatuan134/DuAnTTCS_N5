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
async function pageFixture(file, { data, error, role = 'LIBRARIAN', id = '81', created = false, userId = 9, request } = {}) {
  const states = [], effects = [], calls = []
  let cursor = 0, currentId = id, currentUserId = userId, dismissNotice
  const hooks = {
    ...react,
    useState(initial) {
      const index = cursor++
      if (!(index in states)) states[index] = initial
      return [states[index], (value) => { states[index] = typeof value === 'function' ? value(states[index]) : value }]
    },
    useEffect(callback, dependencies) {
      const index = cursor++, previous = states[index]
      if (!previous || dependencies.some((value, i) => !Object.is(value, previous.deps[i]))) {
        states[index] = { deps: dependencies, cleanup: previous?.cleanup }
        effects.push(() => { previous?.cleanup?.(); states[index].cleanup = callback() })
      }
    },
    useMemo(callback) { return callback() },
  }
  const page = load(file, {
    react: hooks,
    'react-router-dom': {
      useParams: () => ({ loanId: currentId }), useLocation: () => ({ state: { loanCreated: created } }),
      Link: ({ to, children, ...props }) => react.createElement('a', { ...props, href: to }, children),
    },
    '../../components/ui/Button': { __esModule: true, default: ({ children, loading, ...props }) => react.createElement('button', { ...props, disabled: props.disabled || loading }, children) },
    '../../components/ui/Card': { __esModule: true, default: ({ children, ...props }) => react.createElement('div', props, children) },
    '../../components/ui/LoadingState': { __esModule: true, default: () => react.createElement('p', {}, 'Đang tải') },
    '../../components/ui/EmptyState': { __esModule: true, default: ({ title, description }) => react.createElement('p', {}, title, description) },
    // Page uses the shared 3-second notification; timing itself is covered by feedbackAlert.test.mjs.
    '../../components/ui/FeedbackAlert': { __esModule: true, default: ({ message, onDismiss }) => { dismissNotice = onDismiss; return react.createElement('p', { role: 'status' }, message) } },
    '../../components/ui/PageHeader': { __esModule: true, default: ({ title, description, action }) => react.createElement('header', {}, title, description, action) },
    '../../components/ui/TablePagination': { __esModule: true, default: ({ page, totalItems, totalPages }) => react.createElement('p', {}, `Trang ${page}/${totalPages}, tổng ${totalItems}`) },
    '../../components/ui/TableActionButton': { tableActionClassName: () => 'action' },
    '../../hooks/useTablePagination': load('../../hooks/useTablePagination.ts', { react: hooks }),
    '../../core/auth/authStorage': { getCurrentUser: () => ({ role, id: currentUserId, fullName: 'Người đang xem' }) },
    '../s1-02-user-management/accountService': { getApiErrorMessage: (e) => e.message },
    './loanService': { ...serviceModule, loanService: {
      async detail(loanId) { calls.push(loanId); if (error) throw error; return request ? request(loanId, currentUserId) : data ?? structuredClone(loan) },
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
  async function flush() {
    for (let i = 0; i < 4; i++) { render(); for (const effect of effects.splice(0)) effect(); await settle() }
  }
  return { render, calls, before, flush, html: () => renderToStaticMarkup(render()),
    dismiss() { dismissNotice() }, changeId(value) { currentId = value }, changeUser(value) { currentUserId = value },
    unmount() { for (const state of states) state?.cleanup?.() },
  }
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
test('unsupported roles and invalid ids cannot load details', async () => {
  const reader = await pageFixture('LoanDetailPage.tsx', { role: 'UNKNOWN' })
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
    './LoanListPage': { __esModule: true, default: () => null }, './LoanSearchPage': { __esModule: true, default: () => null }, './LoanDetailPage': { __esModule: true, default: () => null }, './loanService': serviceModule,
  }).default
  assert.deepEqual(Array.from(f.appRoutes, (r) => r.path), ['loans', 'loans/search', 'loans/:loanId'])
  assert.equal(f.navItems[0].label, 'Phiếu mượn'); assert.equal(f.navItems[0].to, '/loans')
  assert.equal(f.navItems[1].label, 'Tra cứu phiếu mượn'); assert.equal(f.navItems[1].to, '/loans/search')
  assert.deepEqual(Array.from(f.navItems[0].roles), ['LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN'])
})


test('reader opens own loan directly with reader navigation and no staff actions', async () => {
  const f = await pageFixture('LoanDetailPage.tsx', { role: 'READER', userId: 12, created: true })
  const html = f.html()
  for (const text of ['PM-SAVED-000081', 'LIB-031', 'Mắt biếc', 'Sách của tôi']) assert.ok(html.includes(text))
  assert.match(html, /href="\/my-borrowed-books"/); assert.match(html, /href="\/catalog\/books\/7"/)
  assert.doesNotMatch(html, /href="\/loans"|href="\/book-copies|href="\/reservations\/ready|Đã lập phiếu mượn thành công/)
  assert.deepEqual(f.calls, [81])
})
for (const status of [403, 404]) test(`reader denied ${status} shows safe dismissible feedback without loan data`, async () => {
  const f = await pageFixture('LoanDetailPage.tsx', { role: 'READER', userId: 12,
    error: { response: { status }, message: 'SECRET-OTHER-READER' } })
  assert.match(f.html(), /Phiếu không tồn tại hoặc bạn không có quyền truy cập/)
  assert.doesNotMatch(f.html(), /SECRET-OTHER-READER|PM-SAVED|LIB-031|Nguyễn Văn An|Thử lại/)
  f.dismiss(); assert.doesNotMatch(f.html(), /Phiếu không tồn tại hoặc bạn không có quyền truy cập/)
  assert.match(f.html(), /Không thể mở phiếu mượn này/); assert.match(f.html(), /Sách của tôi/)
})
test('changing direct URL hides old content and rejects the next loan', async () => {
  const f = await pageFixture('LoanDetailPage.tsx', { role: 'READER', userId: 12, request: async (id) => {
    if (id === 81) return structuredClone(loan)
    throw { response: { status: 404 } }
  } })
  assert.match(f.html(), /PM-SAVED/); f.changeId('82'); assert.doesNotMatch(f.html(), /PM-SAVED/)
  await f.flush(); assert.match(f.html(), /bạn không có quyền truy cập/)
  assert.doesNotMatch(f.html(), /PM-SAVED|LIB-031/); assert.deepEqual(f.calls, [81, 82])
})
test('changing reader hides cached loan before reload and cancels delayed old response', async () => {
  let finishOld
  const old = new Promise(resolve => { finishOld = resolve })
  const f = await pageFixture('LoanDetailPage.tsx', { role: 'READER', userId: 12, request: async (_id, user) => {
    if (user === 12) return old
    throw { response: { status: 404 } }
  } })
  f.changeUser(13); assert.doesNotMatch(f.html(), /PM-SAVED/); await f.flush()
  finishOld(structuredClone(loan)); await settle()
  assert.match(f.html(), /bạn không có quyền truy cập/); assert.doesNotMatch(f.html(), /PM-SAVED|LIB-031/)
})
test('loaded reader data never renders under a different reader identity', async () => {
  const f = await pageFixture('LoanDetailPage.tsx', { role: 'READER', userId: 12 })
  assert.match(f.html(), /PM-SAVED/); f.changeUser(13)
  assert.doesNotMatch(f.html(), /PM-SAVED|LIB-031/)
})
test('unmount prevents delayed loan result from becoming visible', async () => {
  let finish
  const pending = new Promise(resolve => { finish = resolve })
  const f = await pageFixture('LoanDetailPage.tsx', { role: 'READER', userId: 12, request: async () => pending })
  f.unmount(); finish(structuredClone(loan)); await settle()
  assert.doesNotMatch(f.html(), /PM-SAVED|LIB-031/)
})

test('S3-07.2 reopened loan persists returned date, receiver and completed status', async () => {
  const data = structuredClone(loan)
  Object.assign(data.items[0], { returnedAt: '2026-10-08T18:00:00Z', returnedById: 3, returnedByName: 'Thủ thư nhận trả' })
  const f = await pageFixture('LoanDetailPage.tsx', { data })
  for (const expected of ['Đã trả', 'Ngày trả thực tế', '09/10/2026', '01:00:00', 'Thủ thư nhận trả']) assert.ok(f.html().includes(expected), expected)
})
test('S3-07.2 partially returned loan shows separate statuses without closing sibling', async () => {
  const data = structuredClone(loan)
  data.items.push({ ...data.items[0], id: 92, copyId: 32, barcode: 'LIB-032' })
  Object.assign(data.items[0], { returnedAt: '2026-10-08T18:00:00Z', returnedByName: 'Thủ thư nhận trả' })
  const f = await pageFixture('LoanDetailPage.tsx', { data })
  assert.ok(f.html().includes('Đang mượn · Đã trả một phần'))
  assert.ok(f.html().includes('LIB-032'))
})
