import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import { createRequire } from 'node:module'
import vm from 'node:vm'
import ts from 'typescript'
import { renderToStaticMarkup } from 'react-dom/server'
const require = createRequire(import.meta.url), react = require('react')
const root = new URL('../src/features/s4-01-fee-reduction/', import.meta.url)
function load(file, imports) {
  const context = { exports: {}, require: name => imports[name] ?? require(name) }
  vm.runInNewContext(ts.transpileModule(readFileSync(new URL(file, root), 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX },
  }).outputText, context)
  return context.exports
}
const api = load('feeReductionService.ts', { '../../core/api/apiClient': { apiClient: {} } })
const pending = {
  id: 2, feeId: 5, loanId: 8, loanNumber: 'PM-008', readerName: 'Bạn đọc Bình', bookTitle: 'Mắt biếc',
  barcode: 'LIB-001', requestedBy: 10, requesterName: 'Thủ thư An', originalAmountVnd: 15000,
  requestedAmountVnd: 10000, outstandingAmountVnd: 15000, reason: 'Hoàn cảnh khó khăn',
  submittedAt: '2026-10-12T10:00:00+07:00', status: 'PENDING', paidAmountVnd: 0, approvedAmountVnd: 0,
  decidedBy: null, deciderName: null, decidedAt: null, decisionReason: null,
}
const approved = { ...pending, status: 'APPROVED', approvedAmountVnd: 10000, outstandingAmountVnd: 5000,
  decidedBy: 12, deciderName: 'Quản lý Hạnh', decidedAt: '2026-10-12T10:05:00+07:00' }
const vnd = value => `${new Intl.NumberFormat('vi-VN').format(value)} VND`
const settle = () => new Promise(resolve => setImmediate(resolve))
function harness() {
  const state = [], effects = []; let cursor = 0
  const hooks = { ...react,
    useId: () => 'qa-id',
    useState(initial) { const i = cursor++; if (!(i in state)) state[i] = initial; return [state[i], v => { state[i] = typeof v === 'function' ? v(state[i]) : v }] },
    useRef(initial) { const i = cursor++; if (!(i in state)) state[i] = { current: initial }; return state[i] },
    useEffect(callback, deps) { const i = cursor++, old = state[i]; if (!old || deps.some((v, n) => !Object.is(v, old.deps[n]))) { state[i] = { deps }; effects.push(callback) } },
  }
  return { hooks, begin: () => { cursor = 0 }, async flush() { for (const effect of effects.splice(0)) effect(); await settle() } }
}
function imports(h, role) {
  const component = fn => ({ __esModule: true, default: fn })
  return {
    react: h.hooks,
    'react-router-dom': { useParams: () => ({ feeId: '5' }), Link: ({ to, children }) => react.createElement('a', { href: to }, children) },
    '../../components/ui/Button': component(({ children, loading, ...p }) => react.createElement('button', { ...p, disabled: p.disabled || loading }, children)),
    '../../components/ui/Card': component(({ children, ...p }) => react.createElement('section', p, children)),
    '../../components/ui/Modal': component(({ children }) => react.createElement('section', { role: 'dialog' }, children)),
    '../../components/ui/FeedbackAlert': component(({ message }) => react.createElement('p', { role: 'alert' }, message)),
    '../../components/ui/PageHeader': component(({ title }) => react.createElement('h1', {}, title)),
    '../../components/ui/LoadingState': component(() => react.createElement('p', {}, 'Đang tải')),
    '../../components/ui/TablePagination': component(() => null),
    '../../core/auth/authStorage': { getCurrentUser: () => ({ id: 12, role }) },
    '../s1-02-user-management/accountService': { getApiErrorMessage: error => error.message },
    '../s3-01-loans/loanService': { loanRoles: ['LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN'], formatLoanTimestamp: value => value ?? 'Chưa có' },
    '../s3-07-returns/ReturnFeeDetails': { formatVnd: vnd },
  }
}
function find(tree, predicate) {
  if (!tree || typeof tree !== 'object') return null
  if (Array.isArray(tree)) { for (const child of tree) { const found = find(child, predicate); if (found) return found } return null }
  if (predicate(tree)) return tree
  return find(tree.props?.children, predicate)
}
function actions({ role = 'LIBRARY_MANAGER', request = pending, approve = async () => approved, reject = async () => ({ ...pending, status: 'REJECTED' }) } = {}) {
  const h = harness(), results = [], conflicts = [], calls = []
  const Component = load('ReviewFeeReductionActions.tsx', { ...imports(h, role),
    './feeReductionService': { ...api, feeReductionService: {
      async approve(id) { calls.push(['approve', id]); return approve(id) },
      async reject(id, reason) { calls.push(['reject', id, reason]); return reject(id, reason) },
    } },
  }).default
  const render = () => { h.begin(); return Component({ request, onReviewed: value => results.push(value), onConflict: value => conflicts.push(value) }) }
  return { render, calls, results, conflicts, html: () => renderToStaticMarkup(render()),
    click(label) { const element = find(render(), e => e.props?.children === label && e.props.onClick); assert.ok(element, label); element.props.onClick() },
    reason(value) { find(render(), e => e.type === 'textarea').props.onChange({ target: { value } }) },
    submit() { find(render(), e => e.type === 'form').props.onSubmit({ preventDefault() {} }) },
  }
}
test('decision API uses the authenticated client and never sends actor or a replacement amount', async () => {
  const calls = []
  const service = load('feeReductionService.ts', { '../../core/api/apiClient': { apiClient: {
    async post(path, body) { calls.push([path, body]); return { data: approved } },
  } } }).feeReductionService
  assert.equal(await service.approve(2), approved); assert.equal(await service.reject(2, 'Chưa đủ căn cứ'), approved)
  assert.equal(calls[0][0], '/overdue-fees/reduction-requests/2/approve'); assert.equal(calls[0][1], undefined)
  assert.equal(calls[1][0], '/overdue-fees/reduction-requests/2/reject'); assert.equal(calls[1][1].reason, 'Chưa đủ căn cứ')
  assert.equal(Object.keys(calls[1][1]).length, 1)
})
test('preview shows 5000 for partial and zero for full waiver', () => {
  assert.equal(api.feeAfterApproval(pending), 5000)
  assert.equal(api.feeAfterApproval({ ...pending, requestedAmountVnd: 15000 }), 0)
})
test('any payment, processed status or excessive amount disables approval preview', () => {
  for (const request of [{ ...pending, paidAmountVnd: 1 }, { ...pending, paidAmountVnd: 15000 }, approved,
    { ...pending, status: 'REJECTED' }, { ...pending, outstandingAmountVnd: 5000 }]) {
    assert.equal(api.canApproveFeeReduction(request), false); assert.equal(api.feeAfterApproval(request), null)
  }
})
test('only manager receives decision controls and processed requests receive none', () => {
  for (const role of ['LIBRARIAN', 'ADMIN', 'READER']) assert.equal(actions({ role }).html(), '')
  assert.equal(actions({ request: approved }).html(), '')
  assert.match(actions().html(), /Phê duyệt/); assert.match(actions().html(), /Từ chối/)
})
test('approval opens amount confirmation and double submit invokes the API once', async () => {
  let resolve; const f = actions({ approve: () => new Promise(done => { resolve = done }) })
  f.click('Phê duyệt'); assert.match(f.html(), /10.000 VND/); assert.match(f.html(), /5.000 VND/)
  f.submit(); f.submit(); assert.equal(f.calls.length, 1); assert.match(f.html(), /disabled/)
  resolve(approved); await settle(); assert.equal(f.results[0], approved); assert.doesNotMatch(f.html(), /role="dialog"/)
})
test('cancel closes decision dialog and performs no write', () => {
  const f = actions(); f.click('Phê duyệt'); f.click('Hủy'); assert.equal(f.calls.length, 0)
  assert.doesNotMatch(f.html(), /role="dialog"/)
})
test('rejection requires meaningful reason and sends its trimmed content', async () => {
  const f = actions(); f.click('Từ chối'); f.reason('\u00a0\uFEFF'); f.submit(); assert.equal(f.calls.length, 0)
  assert.match(f.html(), /Vui lòng nhập lý do/)
  f.reason('x'.repeat(1001)); f.submit(); assert.equal(f.calls.length, 0)
  f.reason('  Chưa đủ căn cứ  '); f.submit(); await settle(); assert.equal(f.calls[0][2], 'Chưa đủ căn cứ')
  assert.equal(f.results[0].status, 'REJECTED')
})
test('paid fee disables approve but permits rejection with an explanation', () => {
  const f = actions({ request: { ...pending, paidAmountVnd: 1000, outstandingAmountVnd: 14000 } })
  assert.match(f.html(), /đã thu tiền/)
  assert.equal(find(f.render(), e => e.props?.children === 'Phê duyệt').props.disabled, true)
  f.click('Từ chối'); assert.match(f.html(), /14.000 VND/)
})
test('409 closes stale dialog and asks the parent to reload while retaining the conflict message', async () => {
  const f = actions({ approve: async () => { throw { isAxiosError: true, response: { status: 409 }, message: 'Đề nghị đã được xử lý.' } } })
  f.click('Phê duyệt'); f.submit(); await settle()
  assert.deepEqual(f.conflicts, ['Đề nghị đã được xử lý.']); assert.equal(f.results.length, 0)
  assert.doesNotMatch(f.html(), /role="dialog"/)
})
test('network error keeps the dialog retryable without reporting success', async () => {
  const f = actions({ approve: async () => { throw new Error('Mất kết nối kiểm thử') } })
  f.click('Phê duyệt'); f.submit(); await settle()
  assert.match(f.html(), /Mất kết nối kiểm thử/); assert.match(f.html(), /role="dialog"/); assert.equal(f.results.length, 0)
})
async function page(file, role, data) {
  const h = harness(), calls = []
  const Component = load(file, { ...imports(h, role),
    './ReviewFeeReductionActions': { __esModule: true, default: () => null },
    './feeReductionService': { ...api, feeReductionService: {
      async detail(id) { calls.push(id); return data }, async pending(index) { calls.push(index); return data },
    } },
  }).default
  const render = () => { h.begin(); const result = Component(); return typeof result.type === 'function' ? result.type(result.props) : result }
  render(); await h.flush()
  return { calls, html: renderToStaticMarkup(render()) }
}
test('librarian fee detail shows original, waived, remaining and saved manager decision', async () => {
  const data = { id: 5, loanId: 8, loanNumber: 'PM-008', readerName: 'Bạn đọc Bình', bookTitle: 'Mắt biếc', barcode: 'LIB-001',
    originalAmountVnd: 15000, paidAmountVnd: 0, waivedAmountVnd: 10000, outstandingAmountVnd: 5000,
    returnedAt: pending.submittedAt, pendingRequest: null, reductionRequests: [approved] }
  const f = await page('OverdueFeeDetailPage.tsx', 'LIBRARIAN', data)
  for (const value of ['15.000 VND', '10.000 VND', '5.000 VND', 'Đã duyệt', 'Quản lý Hạnh', 'Thủ thư An', 'Lịch sử quyết định']) assert.ok(f.html.includes(value), value)
  const rejected = { ...approved, status: 'REJECTED', approvedAmountVnd: 0, decisionReason: 'Chưa đủ căn cứ' }
  const other = await page('OverdueFeeDetailPage.tsx', 'LIBRARIAN', { ...data, waivedAmountVnd: 0, outstandingAmountVnd: 15000, reductionRequests: [rejected] })
  assert.match(other.html, /Từ chối/); assert.match(other.html, /Chưa đủ căn cứ/)
})
test('full waiver shows zero receivable and retained history without a new request form', async () => {
  const f = await page('OverdueFeeDetailPage.tsx', 'LIBRARIAN', { id: 5, loanId: 8, originalAmountVnd: 15000,
    paidAmountVnd: 0, waivedAmountVnd: 15000, outstandingAmountVnd: 0, returnedAt: pending.submittedAt,
    pendingRequest: null, reductionRequests: [{ ...approved, requestedAmountVnd: 15000, approvedAmountVnd: 15000 }] })
  assert.match(f.html, /không còn nghĩa vụ phải thu/); assert.doesNotMatch(f.html, /Gửi đề nghị miễn giảm/)
})
test('manager pending list shows projected amount and blocks nonmanager data access', async () => {
  const data = { items: [pending], page: 0, size: 20, totalItems: 1, totalPages: 1 }
  const f = await page('PendingFeeReductionsPage.tsx', 'LIBRARY_MANAGER', data)
  assert.match(f.html, /Còn phải thu nếu được duyệt/); assert.match(f.html, /5.000 VND/)
  const blocked = await page('PendingFeeReductionsPage.tsx', 'LIBRARIAN', data)
  assert.match(blocked.html, /Chỉ Quản lý/); assert.equal(blocked.calls.length, 0)
})
