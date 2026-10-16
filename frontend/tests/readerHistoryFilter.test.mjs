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
  const pagination = load('../src/hooks/useTablePagination.ts', { react: f.hooks }).default
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
    '../../hooks/useTablePagination': { __esModule: true, default: pagination },
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
const serviceModule = load('../src/features/s1-03-reader-registration/readerService.ts', { '../../core/api/apiClient': { apiClient: {} } })
const timestampModule = load('../src/features/s3-01-loans/loanService.ts', { '../../core/api/apiClient': { apiClient: {} } })
const router = { Link: ({ children }) => children, useParams: () => ({ readerId: '20' }) }
const makeLoan = id => ({ id, loanNumber: `PM-${id}`, borrowedAt: `2026-10-${String(id).padStart(2, '0')}T09:00:00+07:00`,
  status: 'RETURNED', returnedLate: false, items: [] })
const full = [makeLoan(4), makeLoan(3), makeLoan(2), makeLoan(1)]
const response = loans => ({ profile: { userId: 20, fullName: 'Nguyễn An', memberCode: 'BD000020', email: 'an@example.invalid',
  dateOfBirth: '2005-01-01', submittedAt: '2026-10-01T09:00:00+07:00', userStatus: 'ACTIVE', registrationStatus: 'APPROVED' },
  openLoanCount: 2, totalBorrowCount: 4, lateReturnCount: 1, loans })
function all(node, predicate, output = []) {
  if (Array.isArray(node)) { for (const child of node) all(child, predicate, output); return output }
  if (node && typeof node === 'object') { if (predicate(node)) output.push(node); all(node.props?.children, predicate, output) }
  return output
}
function fixture(getLoanHistory) {
  const calls = [], props = { id: 20 }
  const p = page('../src/features/s1-03-reader-registration/ReaderProfilePage.tsx', {
    'react-router-dom': router,
    './readerService': { ...serviceModule, readerService: { getLoanHistory: (id, filters) => {
      calls.push({ id, ...filters }); return getLoanHistory(id, filters, calls.length)
    } } },
    '../s3-01-loans/loanService': timestampModule,
  }, props, 'ReaderProfile')
  return Object.assign(p, {
    calls, props,
    input: field => find(p.tree(), n => n.props?.id === `reader-history-${field}-date`),
    edit(field, value) { this.input(field).props.onChange({ target: { value } }); p.render() },
    async apply() { find(p.tree(), n => n.type === 'form').props.onSubmit({ preventDefault() {} }); p.render(); await settle(); p.render() },
    async clear() { find(p.tree(), n => n.props?.onClick && text(n).includes('Xóa bộ lọc')).props.onClick(); p.render(); await settle(); p.render() },
    ids() { return all(p.tree(), n => n.props?.to?.startsWith('/loans/')).map(n => n.props.to) },
    counts() { return all(p.tree(), n => ['Phiếu đang mở', 'Tổng lượt đã mượn', 'Lượt từng trả trễ'].includes(n.props?.label)).map(n => n.props.value) },
  })
}
async function ready(p) { await settle(); p.render() }

for (const [name, start, end, selected] of [
  ['start only', '2026-10-03', '', [full[0], full[1]]],
  ['end only', '', '2026-10-02', [full[2], full[3]]],
  ['both bounds', '2026-10-02', '2026-10-03', [full[1], full[2]]],
  ['same day', '2026-10-03', '2026-10-03', [full[1]]],
]) test(`${name}: sends optional dates, shows filtered count and preserves global summary`, async () => {
  const p = fixture(async (id, filters, call) => response(call === 1 ? full : selected))
  await ready(p); p.edit('from', start); p.edit('to', end)
  assert.equal(p.calls.length, 1, 'draft changes must not filter before submit')
  await p.apply(); assert.equal(p.calls.length, 2)
  assert.deepEqual(p.calls[1], { id: 20, fromDate: start, toDate: end })
  assert.deepEqual(p.ids(), selected.map(loan => `/loans/${loan.id}`))
  assert.deepEqual(p.counts(), [2, 4, 1]); assert.ok(text(p.tree()).includes(`Tìm thấy ${selected.length} phiếu mượn`))
})

test('no matches has a filtered empty state and does not zero totals', async () => {
  const p = fixture(async (id, filters, call) => response(call === 1 ? full : []))
  await ready(p); p.edit('from', '2027-01-01'); await p.apply()
  assert.ok(find(p.tree(), n => n.props?.title === 'Không có phiếu mượn trong khoảng ngày đã chọn'))
  assert.deepEqual(p.counts(), [2, 4, 1]); assert.ok(text(p.tree()).includes('Tìm thấy 0 phiếu mượn'))
})

test('reversed range sends no request, retains old results, and expires error notice without erasing field error', async () => {
  const p = fixture(async () => response(full)); await ready(p)
  p.edit('from', '2026-10-09'); p.edit('to', '2026-10-01'); await p.apply()
  assert.equal(p.calls.length, 1); assert.deepEqual(p.ids(), full.map(loan => `/loans/${loan.id}`))
  assert.equal(p.notice().props.tone, 'error'); verifyTimer(p)
  assert.equal(p.input('to').props.error, 'Từ ngày không được lớn hơn Đến ngày.')
  p.edit('to', '2026-10-10'); assert.equal(p.input('to').props.error, undefined)
})

test('clearing dates restores all loans and global summary', async () => {
  const p = fixture(async (id, filters) => response(filters.fromDate ? [full[0]] : full))
  await ready(p); p.edit('from', '2026-10-04'); await p.apply(); await p.clear()
  assert.deepEqual(p.calls.at(-1), { id: 20, fromDate: '', toDate: '' })
  assert.equal(p.input('from').props.value, ''); assert.equal(p.input('to').props.value, '')
  assert.deepEqual(p.ids(), full.map(loan => `/loans/${loan.id}`)); assert.deepEqual(p.counts(), [2, 4, 1])
  assert.ok(text(p.tree()).includes('Toàn bộ lịch sử: 4 phiếu mượn'))
})

test('refresh keeps the applied range rather than unsent draft edits', async () => {
  const p = fixture(async () => response([full[0]])); await ready(p)
  p.edit('from', '2026-10-04'); await p.apply(); p.edit('from', '2026-10-02')
  find(p.tree(), n => n.props?.onClick && text(n).includes('Làm mới')).props.onClick()
  p.render(); await settle(); p.render()
  assert.equal(p.calls.at(-1).fromDate, '2026-10-04'); assert.equal(p.input('from').props.value, '2026-10-02')
})

test('filter resets actual table pagination and totalItems counts all matching loans', async () => {
  const many = Array.from({ length: 12 }, (_, i) => makeLoan(12 - i))
  const p = fixture(async (id, filters) => response(filters.fromDate ? [many[0], many[1]] : many))
  await ready(p)
  let pagination = find(p.tree(), n => n.props?.totalItems === 12 && n.props?.onPageChange)
  assert.ok(pagination); pagination.props.onPageChange(2); p.render(); assert.equal(p.ids().length, 2)
  p.edit('from', '2026-10-11'); await p.apply()
  pagination = find(p.tree(), n => n.props?.onPageChange)
  assert.equal(pagination.props.page, 1); assert.equal(pagination.props.totalItems, 2)
})

test('API failure during filter retains dates, expires error and retries the same query', async () => {
  let fail = true
  const p = fixture(async (id, filters, call) => {
    if (call > 1 && fail) throw new Error('Lỗi truy vấn ngày')
    return response(filters.fromDate ? [full[0]] : full)
  })
  await ready(p); p.edit('from', '2026-10-04'); await p.apply()
  assert.equal(p.notice().props.tone, 'error'); verifyTimer(p)
  assert.equal(p.input('from').props.value, '2026-10-04')
  assert.ok(text(p.tree()).includes('Chưa tải được hồ sơ và lịch sử mượn trả.'))
  fail = false; find(p.tree(), n => n.props?.onClick && text(n).includes('Thử lại')).props.onClick()
  p.render(); await settle(); p.render(); assert.deepEqual(p.ids(), ['/loans/4'])
  assert.equal(p.calls.at(-1).fromDate, '2026-10-04'); assert.deepEqual(p.counts(), [2, 4, 1])
})

test('double-submit while request is pending produces only one filter request', async () => {
  let resolve
  const p = fixture(async (id, filters, call) => call === 1 ? response(full) : new Promise(done => { resolve = done }))
  await ready(p); p.edit('from', '2026-10-04')
  const submit = find(p.tree(), n => n.type === 'form').props.onSubmit
  submit({ preventDefault() {} }); submit({ preventDefault() {} }); p.render()
  assert.equal(p.calls.length, 2); resolve(response([full[0]])); await settle(); p.render()
  assert.deepEqual(p.ids(), ['/loans/4'])
})

test('a delayed filter response cannot replace the next reader profile', async () => {
  let resolve
  const p = fixture(async (id, filters, call) => {
    if (id === 21) return { ...response([]), profile: { ...response([]).profile, userId: 21, fullName: 'Bạn đọc B' } }
    return call === 1 ? response(full) : new Promise(done => { resolve = done })
  })
  await ready(p); p.edit('from', '2026-10-04')
  find(p.tree(), n => n.type === 'form').props.onSubmit({ preventDefault() {} }); p.render()
  p.props.id = 21; p.render(); await settle(); p.render()
  resolve(response([full[0]])); await settle(); p.render()
  assert.equal(p.ids().length, 0); assert.ok(text(p.tree()).includes('Bạn đọc B'))
})

test('date validation accepts blank/equal/leap dates and rejects malformed dates and year zero', () => {
  for (const dates of [{ fromDate: '', toDate: '' }, { fromDate: '2024-02-29', toDate: '2024-02-29' }, { fromDate: '0001-01-01', toDate: '9999-12-31' }]) {
    assert.equal(serviceModule.validateReaderHistoryDates(dates), null)
  }
  for (const invalid of ['2026-02-29', '2026-02-30', '2026-13-01', '2026-1-01', '09/10/2026', '0000-01-01', '10000-01-01']) {
    assert.equal(serviceModule.validateReaderHistoryDates({ fromDate: invalid, toDate: '' }).field, 'fromDate')
    assert.equal(serviceModule.validateReaderHistoryDates({ fromDate: '', toDate: invalid }).field, 'toDate')
  }
})

test('readerService encodes only nonempty trimmed optional query dates', async () => {
  const requests = []
  const m = load('../src/features/s1-03-reader-registration/readerService.ts', {
    '../../core/api/apiClient': { apiClient: { get: async (...args) => { requests.push(args); return { data: response(full) } } } },
  })
  await m.readerService.getLoanHistory(20)
  await m.readerService.getLoanHistory(20, { fromDate: ' 2026-10-01 ', toDate: '' })
  await m.readerService.getLoanHistory(20, { fromDate: '', toDate: '2026-10-09' })
  await m.readerService.getLoanHistory(20, { fromDate: '2026-10-01', toDate: '2026-10-09' })
  assert.deepEqual(requests.map(([url]) => url), Array(4).fill('/readers/20/loan-history'))
  assert.deepEqual(JSON.parse(JSON.stringify(requests.map(([, options]) => options.params))), [{}, { fromDate: '2026-10-01' }, { toDate: '2026-10-09' }, { fromDate: '2026-10-01', toDate: '2026-10-09' }])
})
