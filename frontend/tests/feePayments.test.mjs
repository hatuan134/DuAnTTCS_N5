import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import { createRequire } from 'node:module'
import vm from 'node:vm'
import ts from 'typescript'
import { renderToStaticMarkup } from 'react-dom/server'
const require = createRequire(import.meta.url), react = require('react')
const root = new URL('../src/features/s4-02-fee-payments/', import.meta.url)
const reader = { id: 7, fullName: 'Bạn đọc Bình', cardNumber: 'THE-007', memberCode: 'BD-007', email: 'binh@example.invalid', phone: null, cardStatus: 'ACTIVE' }
const fees = [10000, 15000, 5000].map((amount, index) => ({ id: index + 1, feeType: 'RECORDED', description: `Khoản ${index + 1}`,
  originalAmountVnd: amount, paidAmountVnd: 0, waivedAmountVnd: 0, outstandingAmountVnd: amount }))
const debt = { reader, fees, totalOutstandingVnd: 30000, hasDebt: true, debtToken: 'a'.repeat(64), payments: [] }
const receipt = { id: 9, transactionNumber: 'GD-9', receiptNumber: 'BL-9', readerId: 7, readerName: reader.fullName,
  cardNumber: reader.cardNumber, collectedBy: 12, collectorName: 'Thủ thư An', collectedAt: '2026-10-12T20:00:00+07:00', totalAmountVnd: 30000,
  items: fees.map(f => ({ feeId: f.id, feeType: f.feeType, description: f.description, originalAmountVnd: f.originalAmountVnd,
    previouslyPaidVnd: 0, waivedAmountVnd: 0, collectedAmountVnd: f.outstandingAmountVnd })) }
const zero = { ...debt, fees: [], totalOutstandingVnd: 0, hasDebt: false, payments: [receipt] }
const vnd = value => `${new Intl.NumberFormat('vi-VN').format(value)} VND`
function load(file, imports = {}, globals = {}) {
  const context = { exports: {}, require: name => imports[name] ?? require(name), crypto: { randomUUID: () => 'e4c9cc1f-490c-4d15-9280-e45b7e50ab7e' }, ...globals }
  vm.runInNewContext(ts.transpileModule(readFileSync(new URL(file, root), 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX, target: ts.ScriptTarget.ES2022 },
  }).outputText, context)
  return context.exports
}
function harness() {
  const state = []; let cursor = 0
  return { hooks: { ...react,
    useState(initial) { const index = cursor++; if (!(index in state)) state[index] = initial; return [state[index], value => { state[index] = typeof value === 'function' ? value(state[index]) : value }] },
    useRef(initial) { const index = cursor++; if (!(index in state)) state[index] = { current: initial }; return state[index] },
  }, begin: () => { cursor = 0 } }
}
const component = fn => ({ __esModule: true, default: fn })
function imports(h, role = 'LIBRARIAN') {
  return {
    react: h.hooks,
    axios: { isAxiosError: error => Boolean(error.response) },
    '../../components/ui/Button': component(({ children, loading, ...p }) => react.createElement('button', { ...p, disabled: p.disabled || loading }, children)),
    '../../components/ui/Card': component(({ children }) => react.createElement('section', {}, children)),
    '../../components/ui/Input': component(p => react.createElement('input', p)),
    '../../components/ui/ConfirmActionDialog': component(({ onConfirm, confirmLabel }) => react.createElement('button', { onClick: onConfirm }, confirmLabel)),
    '../../components/ui/FeedbackAlert': component(({ message }) => react.createElement('p', {}, message)),
    '../../components/ui/LoadingState': component(() => react.createElement('p', {}, 'Đang tải')),
    '../../components/ui/PageHeader': component(({ title }) => react.createElement('h1', {}, title)),
    '../../core/auth/authStorage': { getCurrentUser: () => ({ id: 12, role }) },
    '../s1-02-user-management/accountService': { getApiErrorMessage: e => e.message },
    '../s3-01-loans/loanService': { formatLoanTimestamp: s => s },
    '../s3-07-returns/ReturnFeeDetails': { formatVnd: vnd },
    './FeeReceiptDialog': component(() => react.createElement('p', {}, 'Biên lai')),
  }
}
function find(tree, predicate) {
  if (!tree || typeof tree !== 'object') return null
  if (Array.isArray(tree)) { for (const node of tree) { const result = find(node, predicate); if (result) return result } return null }
  return predicate(tree) ? tree : find(tree.props?.children, predicate)
}
const flush = () => new Promise(resolve => setImmediate(resolve))
function collector({ collect = async () => receipt, detail, role = 'LIBRARIAN', initial = debt } = {}) {
  const h = harness(), calls = []; let collected = false
  const api = { search: async () => [reader],
    detail: detail ?? (async () => collected ? zero : initial),
    async collect(id, input) { calls.push({ id, input }); const saved = await collect(id, input); collected = true; return saved },
    receipt: async () => receipt,
  }
  const module = load('FeeCollectionPage.tsx', { ...imports(h, role), './feePaymentService': { feePaymentService: api, feeTypeLabel: () => 'Khoản phí đã ghi nhận' } })
  const render = () => { h.begin(); return module.FeeCollector() }
  function click(label) { const node = find(render(), e => e.props?.children === label && e.props.onClick); assert.ok(node, label); return node.props.onClick() }
  async function select() {
    find(render(), e => e.props?.label === 'Mã thẻ hoặc thông tin định danh').props.onChange({ target: { value: 'THE-007' } })
    find(render(), e => e.type === 'form').props.onSubmit({ preventDefault() {} }); await flush()
    find(render(), e => e.props.onClick && e.props.children?.[0]?.props?.children === reader.fullName).props.onClick(); await flush()
  }
  async function pay() { click('Thu đủ'); find(render(), e => e.props?.confirmLabel === 'Xác nhận thu đủ').props.onConfirm(); await flush() }
  return { render, html: () => renderToStaticMarkup(render()), calls, select, pay, click, module, h }
}
test('API calls reuse authenticated client and send no actor or arbitrary allocation', async () => {
  const calls = []
  const api = load('feePaymentService.ts', { '../../core/api/apiClient': { apiClient: {
    async get(path, options) { calls.push({ path, options }); return { data: debt } },
    async post(path, body) { calls.push({ path, body }); return { data: receipt } },
  } } }).feePaymentService
  await api.search('THE-007'); await api.detail(7); await api.collect(7, { requestKey: 'key', expectedTotalVnd: 30000, debtToken: debt.debtToken }); await api.receipt(9)
  assert.equal(calls[0].options.params.query, 'THE-007'); assert.equal(calls[2].path, '/fee-payments/readers/7/collect-full')
  assert.deepEqual(Object.keys(calls[2].body), ['requestKey', 'expectedTotalVnd', 'debtToken']); assert.equal(calls[3].path, '/fee-payments/9/receipt')
})
test('three debts total 30000, confirmation clears debt and exposes stored receipt', async () => {
  const c = collector(); await c.select(); assert.match(c.html(), /30\.000 VND/); assert.match(c.html(), /Nợ phí/)
  await c.pay(); assert.equal(c.calls.length, 1); assert.match(c.html(), /Không nợ phí/); assert.match(c.html(), /Số dư nợ bằng 0 VND/)
  assert.match(c.html(), /BL-9/); assert.match(c.html(), /Xem \/ in lại biên lai/); assert.doesNotMatch(c.html(), />Thu đủ</)
})
test('zero debt has no collection button', async () => {
  const c = collector({ initial: zero }); await c.select(); assert.doesNotMatch(c.html(), />Thu đủ</); assert.equal(c.calls.length, 0)
})
test('double confirmation is guarded before the first response arrives', async () => {
  let resolve; const pending = new Promise(r => { resolve = r }), c = collector({ collect: () => pending })
  await c.select(); c.click('Thu đủ'); const dialog = find(c.render(), e => e.props?.confirmLabel === 'Xác nhận thu đủ')
  dialog.props.onConfirm(); dialog.props.onConfirm(); assert.equal(c.calls.length, 1)
  resolve(receipt); await flush(); assert.match(c.html(), /Không nợ phí/)
})
test('retry after lost response keeps exactly the same request key', async () => {
  let attempt = 0; const c = collector({ collect: async () => { if (++attempt === 1) throw new Error('Mất kết nối'); return receipt } })
  await c.select(); await c.pay(); await c.pay(); assert.equal(c.calls.length, 2)
  assert.equal(c.calls[0].input, c.calls[1].input); assert.match(c.html(), /BL-9/)
})
test('changed debt conflict refreshes amount before another confirmation', async () => {
  let reads = 0
  const c = collector({ detail: async () => ++reads === 1 ? debt : { ...debt, totalOutstandingVnd: 10000, fees: [fees[0]] },
    collect: async () => { throw Object.assign(new Error('Khoản nợ đã thay đổi'), { response: { data: { code: 'FEE_DEBT_CHANGED' } } }) } })
  await c.select(); await c.pay(); assert.equal(reads, 2); assert.match(c.html(), /10\.000 VND/); assert.doesNotMatch(c.html(), /30\.000 VND/)
})
test('other roles cannot open collection page', () => {
  for (const role of ['ADMIN', 'LIBRARY_MANAGER', 'READER']) { const c = collector({ role }); assert.match(renderToStaticMarkup(c.module.default()), /Chỉ Thủ thư/) }
})
test('receipt has code, time, reader, three allocations, total and collector; HTML is escaped', () => {
  const h = harness(), m = load('FeeReceiptDialog.tsx', { ...imports(h), '../../components/ui/Modal': component(({ children }) => children), './feePaymentService': { feeTypeLabel: () => 'Khoản phí' } })
  const html = renderToStaticMarkup(react.createElement(m.ReceiptContent, { receipt: { ...receipt, readerName: '<script>bad()</script>' } }))
  for (const value of ['BL-9', 'GD-9', 'THE-007', '2026-10-12T20:00:00+07:00', '10.000 VND', '15.000 VND', '5.000 VND', '30.000 VND', 'Thủ thư An']) assert.ok(html.includes(value), value)
  assert.ok(html.includes('&lt;script&gt;')); assert.ok(!html.includes('<script>'))
})
test('print failure only removes its frame and retains the committed receipt', async () => {
  let frame, removed = false
  const document = { createElement() { frame = { style: {}, remove: () => { removed = true }, contentWindow: {
    addEventListener() {}, focus() {}, print() { throw new Error('Printer offline') },
  } }; return frame }, body: { appendChild(f) { f.onload() } } }
  const h = harness(), m = load('FeeReceiptDialog.tsx', { ...imports(h), '../../components/ui/Modal': component(({ children }) => children), './feePaymentService': { feeTypeLabel: () => 'Khoản phí' } }, { document, window: { setTimeout() {} } })
  await assert.rejects(m.printFeeReceipt(receipt), /Printer offline/)
  assert.ok(removed); assert.equal(receipt.totalAmountVnd, 30000); assert.ok(frame.srcdoc.includes('BL-9'))
})
