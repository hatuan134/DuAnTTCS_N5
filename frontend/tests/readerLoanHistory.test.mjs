import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import { createRequire } from 'node:module'
import vm from 'node:vm'
import ts from 'typescript'
const require = createRequire(import.meta.url), react = require('react')
const settle = () => new Promise(resolve => setImmediate(resolve))
function load(path, imports, extra = {}) {
  const context = { exports: {}, require: n => imports[n] ?? require(n), ...extra }
  vm.runInNewContext(ts.transpileModule(readFileSync(new URL(path, import.meta.url), 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX },
  }).outputText, context)
  return context.exports
}
function hooksFixture() {
  const slots = [], effects = []; let cursor = 0
  return {
    hooks: { ...react,
      useState(initial) { const i = cursor++; if (!(i in slots)) slots[i] = typeof initial === 'function' ? initial() : initial
        return [slots[i], v => { slots[i] = typeof v === 'function' ? v(slots[i]) : v }] },
      useRef(value) { const i = cursor++; return slots[i] ??= { current: value } },
      useMemo(fn) { cursor++; return fn() },
      useCallback(fn, deps) { const i = cursor++, old = slots[i]
        if (!old || deps.some((v, n) => !Object.is(v, old.deps[n]))) slots[i] = { deps, value: fn }
        return slots[i].value
      },
      useEffect(fn, deps) { const i = cursor++, old = slots[i]
        if (!old || deps.some((v, n) => !Object.is(v, old.deps[n]))) {
          slots[i] = { deps, cleanup: old?.cleanup }; effects.push(() => { old?.cleanup?.(); slots[i].cleanup = fn() })
        }
      },
    },
    render(fn) { cursor = 0; const tree = fn(); for (const e of effects.splice(0)) e(); return tree },
    unmount() { for (const slot of slots) slot?.cleanup?.() },
  }
}
const returnHelpers = load('../src/features/s3-07-returns/returnService.ts', {
  '../../core/api/apiClient': { apiClient: {} },
})
const FeedbackStub = Object.assign(() => null, { displayName: 'FeedbackAlert' })
function find(node, predicate) {
  if (!node || typeof node !== 'object') return undefined
  if (Array.isArray(node)) { for (const child of node) { const result = find(child, predicate); if (result) return result } return undefined }
  return predicate(node) ? node : (find(node.props?.children, predicate) ?? find(node.props?.action, predicate))
}
function text(node) {
  if (node == null || typeof node === 'boolean') return ''
  if (Array.isArray(node)) return node.map(text).join(' ')
  return typeof node === 'object' ? text(node.props?.children) : String(node)
}
function page(path, imports = {}, props = {}, exported = 'default', extra = {}) {
  const f = hooksFixture(); let tree
  const Stub = () => null
  const dependencies = {
    react: f.hooks,
    '../../components/ui/FeedbackAlert': { __esModule: true, default: FeedbackStub },
    '../../components/ui/Button': { __esModule: true, default: Stub },
    '../../components/ui/Card': { __esModule: true, default: Stub },
    '../../components/ui/EmptyState': { __esModule: true, default: Stub },
    '../../components/ui/Input': { __esModule: true, default: Stub },
    '../../components/ui/LoadingState': { __esModule: true, default: Stub },
    '../../components/ui/PageHeader': { __esModule: true, default: Stub },
    '../../components/ui/StatusBadge': { __esModule: true, default: Stub },
    '../../components/ui/TableActionButton': { __esModule: true, default: Stub, TableActions: Stub },
    '../../components/ui/TablePagination': { __esModule: true, default: Stub },
    '../../hooks/useTablePagination': { __esModule: true, default: items => ({ pageItems: items, startIndex: 0, page: 1, totalPages: 1, totalItems: items.length, pageSize: 10, goToPage() {} }) },
    '../s1-02-user-management/accountService': { getApiErrorMessage: e => e.message },
    './pickupService': { formatPickupDate: v => v, pickupService: {} }, ...imports,
  }
  const Component = load(path, dependencies, { window: { confirm: () => true }, ...extra })[exported]
  const render = () => { tree = f.render(() => { const outer = Component(props); return exported === 'default' && typeof outer?.type === 'function' ? outer.type(outer.props) : outer }); return tree }; render()
  return { render, notice: () => find(tree, n => n.type === FeedbackStub),
    button: needle => find(tree, n => n.type === 'button' && n.props.onClick && text(n).includes(needle)), tree: () => tree, unmount: () => f.unmount() }
}
function verifyTimer(p) {
  const notice = p.notice(); assert.ok(notice, 'action must produce a notification')
  const f = hooksFixture(), timers = new Map(); let now = 0, next = 0
  const Feedback = load('../src/components/ui/FeedbackAlert.tsx', { react: f.hooks }, { window: {
    setTimeout(fn, ms) { timers.set(++next, { at: now + ms, fn }); return next }, clearTimeout(id) { timers.delete(id) },
  } }).default
  f.render(() => Feedback(notice.props))
  now = 2999; for (const timer of timers.values()) if (timer.at <= now) timer.fn()
  p.render(); assert.ok(p.notice())
  now = 3000; for (const [id, timer] of timers) if (timer.at <= now) { timers.delete(id); timer.fn() }
  p.render(); assert.equal(p.notice(), undefined); f.unmount()
}
const summary = (loans = []) => ({
  profile: { userId: 20, fullName: 'Nguyễn An', memberCode: 'BD000020', email: 'an@example.invalid',
    dateOfBirth: '2005-01-01', submittedAt: '2026-10-01T09:00:00+07:00', userStatus: 'ACTIVE', registrationStatus: 'APPROVED' },
  openLoanCount: 1, totalBorrowCount: loans.length, lateReturnCount: 1, loans,
})
const loan = (id, returnedLate = false) => ({ id, loanNumber: `PM-${id}`, borrowedAt: `2026-10-0${id}T09:00:00+07:00`,
  status: id === 3 ? 'PARTIALLY_RETURNED' : 'RETURNED', returnedLate,
  items: [{ id: id * 10, bookTitle: 'Mắt biếc', barcode: `BC-${id}`, borrowedAt: '2026-10-01T09:00:00+07:00',
    dueAt: '2026-10-07T09:00:00+07:00', returnedAt: id === 3 ? null : '2026-10-08T09:00:00+07:00', status: id === 3 ? 'BORROWED' : 'RETURNED', returnedLate }],
})
const router = { Link: ({ children }) => children, useParams: () => ({ readerId: '20', reservationId: '1', bookId: '1' }) }
const loanHelpers = load('../src/features/s3-01-loans/loanService.ts', { '../../core/api/apiClient': { apiClient: {} } })
function profile(getLoanHistory) {
  return page('../src/features/s1-03-reader-registration/ReaderProfilePage.tsx', {
    'react-router-dom': router, './readerService': { readerService: { getLoanHistory } },
    '../s3-01-loans/loanService': loanHelpers,
  }, { id: 20 }, 'ReaderProfile')
}
function all(node, predicate, output = []) {
  if (Array.isArray(node)) { for (const child of node) all(child, predicate, output); return output }
  if (node && typeof node === 'object') { if (predicate(node)) output.push(node); all(node.props?.children, predicate, output) }
  return output
}
test('profile requests selected reader and renders loan counts, latest-first links and item fields', async () => {
  const calls = []
  const p = profile(async id => { calls.push(id); return summary([loan(3, true), loan(2, true), loan(1)]) })
  await settle(); p.render(); assert.deepEqual(calls, [20])
  const stats = all(p.tree(), n => ['Phiếu đang mở', 'Tổng lượt đã mượn', 'Lượt từng trả trễ'].includes(n.props?.label))
  assert.deepEqual(stats.map(n => [n.props.label, n.props.value]), [['Phiếu đang mở', 1], ['Tổng lượt đã mượn', 3], ['Lượt từng trả trễ', 1]])
  assert.deepEqual(all(p.tree(), n => n.props?.to?.startsWith('/loans/')).map(n => n.props.to), ['/loans/3', '/loans/2', '/loans/1'])
  assert.equal(all(p.tree(), n => n.props?.label === 'Từng trả trễ').length, 2)
  assert.equal(all(p.tree(), n => n.props?.label === 'Đã trả một phần').length, 1)
  assert.deepEqual(all(p.tree(), n => n.props?.label === 'Mã vạch').map(n => n.props.value), ['BC-3', 'BC-2', 'BC-1'])
  assert.ok(all(p.tree(), n => n.props?.label === 'Ngày trả thực tế').some(n => n.props.value === 'Chưa trả'))
})
test('reader without history shows zero counts and honest empty state', async () => {
  const p = profile(async () => ({ ...summary(), openLoanCount: 0, totalBorrowCount: 0, lateReturnCount: 0 }))
  await settle(); p.render(); assert.ok(find(p.tree(), n => n.props?.title === 'Bạn đọc chưa từng mượn sách'))
  assert.equal(all(p.tree(), n => n.props?.value === 0).length, 3)
})
test('legacy due dates and empty loans remain explicit', async () => {
  const legacy = loan(2); legacy.items[0].dueAt = null
  const p = profile(async () => summary([legacy, { ...loan(1), status: 'EMPTY', items: [] }]))
  await settle(); p.render(); assert.ok(find(p.tree(), n => n.props?.value === 'Phiếu cũ chưa lưu hạn trả'))
  assert.ok(text(p.tree()).includes('Phiếu cũ chưa có chi tiết bản sao.'))
})
test('API failure expires at 3000ms, keeps recovery state, and retry succeeds', async () => {
  let calls = 0
  const p = profile(async () => { if (++calls === 1) throw new Error('Lỗi máy chủ'); return summary() })
  await settle(); p.render(); assert.equal(p.notice().props.tone, 'error'); verifyTimer(p)
  assert.ok(text(p.tree()).includes('Chưa tải được hồ sơ và lịch sử mượn trả.'))
  find(p.tree(), n => n.props?.onClick && text(n).includes('Thử lại')).props.onClick()
  p.render(); await settle(); p.render(); assert.equal(calls, 2); assert.equal(p.notice(), undefined)
  assert.ok(find(p.tree(), n => n.props?.title === 'Bạn đọc chưa từng mượn sách'))
})
for (const status of [403, 404]) test(`profile ${status} stays unavailable after notification expires`, async () => {
  const p = profile(async () => { throw { response: { status } } })
  await settle(); p.render(); verifyTimer(p); assert.ok(text(p.tree()).includes('Không thể mở hồ sơ này.'))
  assert.equal(find(p.tree(), n => n.props?.title === 'Bạn đọc chưa từng mượn sách'), undefined)
})
test('unmounted profile ignores delayed response', async () => {
  let resolve
  const p = profile(() => new Promise(done => { resolve = done }))
  p.unmount(); resolve(summary([loan(1)])); await settle(); p.render()
  assert.equal(find(p.tree(), n => n.props?.to === '/loans/1'), undefined)
})
test('wrong-reader response is rejected instead of displayed', async () => {
  const p = profile(async () => ({ ...summary(), profile: { ...summary().profile, userId: 21 } }))
  await settle(); p.render(); assert.ok(p.notice()); verifyTimer(p)
  assert.equal(find(p.tree(), n => n.props?.title === 'Bạn đọc chưa từng mượn sách'), undefined)
})
const browser = { window: { setInterval: () => 1, clearInterval() {}, addEventListener() {}, removeEventListener() {} },
  document: { visibilityState: 'visible', addEventListener() {}, removeEventListener() {} } }
const fail = async () => { throw new Error('Lỗi API kiểm thử') }
const pickup = { pickupService: { list: fail, queue: fail, detail: fail }, pickupRoles: ['LIBRARIAN'],
  formatPickupDate: v => v, reservationFilterStatuses: [], reservationStatusLabel: v => v }
const common = {
  'react-router-dom': router, '../../core/auth/authStorage': { getCurrentUser: () => ({ role: 'LIBRARIAN' }) },
  './CancelReservationPanel': { __esModule: true, default: () => null, CancellationNotice: () => null },
  './CreateReservationLoanPanel': { __esModule: true, default: () => null },
  './OverdueContactDialog': { __esModule: true, default: () => null },
  '../s3-01-loans/loanService': { ...loanHelpers, loanRoles: ['LIBRARIAN'], loanService: { list: fail } },
}
const overdue = load('../src/features/s3-09-overdue-loans/overdueFilter.ts', {})
for (const [file, imports, permanent] of [
  ['s1-00-dashboard/DashboardPage.tsx', { './dashboardService': { getDashboardStats: fail } }, 'Chưa tải được số liệu tổng quan'],
  ['s1-03-reader-registration/ReadersPage.tsx', { './readerService': { readerService: { getAllReaders: fail } } }, 'Không tải được danh sách hồ sơ'],
  ['s3-01-loans/LoanListPage.tsx', { './loanService': { ...loanHelpers, loanRoles: ['LIBRARIAN'], loanService: { list: fail } } }, 'Chưa tải được danh sách phiếu mượn'],
  ['s2-09-ready-pickup/ReadyPickupPage.tsx', { './pickupService': pickup }, 'Chưa tải được sách đang chờ nhận'],
  ['s2-09-ready-pickup/ReadyPickupDetailPage.tsx', { './pickupService': pickup, 'react-router-dom': { ...router, useNavigate: () => () => {} } }, 'Chưa tải được chi tiết đơn đặt giữ'],
  ['s2-09-ready-pickup/BookReservationQueuePage.tsx', { './pickupService': pickup }, 'Chưa tải được hàng đợi đặt giữ'],
  ['s3-09-overdue-loans/OverdueLoansPage.tsx', { './overdueFilter': overdue, './overdueLoanService': { overdueLoanService: { list: fail } } }, 'Chưa tải được danh sách quá hạn'],
]) test(`existing ${file}: error expires at 3000ms and recovery stays`, async () => {
  const p = page(`../src/features/${file}`, { ...common, ...imports }, {}, 'default', browser)
  await settle(); p.render(); assert.ok(p.notice()); verifyTimer(p); assert.ok(text(p.tree()).includes(permanent))
  assert.equal(find(p.tree(), n => n.props?.title === 'Chưa có phiếu mượn'), undefined)
})
