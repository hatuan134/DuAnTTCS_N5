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
function collector({ collect = async () => receipt, collectPartial, detail, role = 'LIBRARIAN', initial = debt } = {}) {
  const h = harness(), calls = []; let collected = false
  const api = { search: async () => [reader],
    detail: detail ?? (async () => collected ? zero : initial),
    async collect(id, input) { calls.push({ id, input }); const saved = await collect(id, input); collected = true; return saved },
    async collectPartial(id, input) { calls.push({ id, input, partial: true }); return collectPartial(id, input) },
    receipt: async () => receipt,
  }
  const module = load('FeeCollectionPage.tsx', { ...imports(h, role), './feePaymentService': { ...paymentHelpers, feePaymentService: api, feeTypeLabel: () => 'Khoản phí đã ghi nhận' } })
  const render = () => { h.begin(); return module.FeeCollector() }
  function click(label) { const node = find(render(), e => e.props?.children === label && e.props.onClick); assert.ok(node, label); return node.props.onClick() }
  async function select() {
    find(render(), e => e.props?.label === 'Mã thẻ hoặc thông tin định danh').props.onChange({ target: { value: 'THE-007' } })
    find(render(), e => e.type === 'form').props.onSubmit({ preventDefault() {} }); await flush()
    find(render(), e => e.props.onClick && e.props.children?.[0]?.props?.children === reader.fullName).props.onClick(); await flush()
  }
  async function pay() { click('Thu đủ'); find(render(), e => e.props?.confirmLabel === 'Xác nhận thu đủ').props.onConfirm(); await flush() }
  function partialAmount(value) {
    find(render(), e => e.props?.value === 'PARTIAL' && e.props.onChange).props.onChange()
    find(render(), e => e.props?.label === 'Số tiền bạn đọc thực tế nộp (VND)').props.onChange({ target: { value } })
  }
  async function payPartial() {
    click('Thu một phần')
    find(render(), e => e.props?.confirmLabel === 'Xác nhận thu một phần').props.onConfirm(); await flush()
  }
  return { render, html: () => renderToStaticMarkup(render()), calls, select, pay, partialAmount, payPartial, click, module, h }
}
const paymentHelpers = load('feePaymentService.ts', { '../../core/api/apiClient': { apiClient: {} } })
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

test('partial API posts actual amount through existing client', async () => {
  let sent
  const api = load('feePaymentService.ts', { '../../core/api/apiClient': { apiClient: {
    async post(path, body) { sent = { path, body }; return { data: receipt } },
  } } }).feePaymentService
  const body = { requestKey: 'key', expectedTotalVnd: 30000, debtToken: debt.debtToken, amountVnd: 10000 }
  await api.collectPartial(7, body); assert.equal(sent.path, '/fee-payments/readers/7/collect-partial')
  assert.equal(sent.body, body); assert.equal(sent.body.amountVnd, 10000)
})
test('whole VND validation accepts 1 and the exact balance, rejects invalid and excessive cash', () => {
  for (const value of ['0', '-1', '1.5', '10.000', '10,000', 'abc', '', '1e3', '30001', '9007199254740992']) {
    const parsed = paymentHelpers.parsePartialAmount(value, 30000)
    assert.equal(parsed.amount, null, value); assert.ok(parsed.error, value)
  }
  for (const value of ['1', '10000', '30000']) assert.equal(paymentHelpers.parsePartialAmount(value, 30000).amount, Number(value))
})
test('preview sorts fee IDs and pays only the remaining amounts', () => {
  const p = paymentHelpers.previewFeeAllocation([fees[2], fees[1], { ...fees[0], paidAmountVnd: 4000, outstandingAmountVnd: 6000 }], 10000)
  assert.equal(p.length, 2); assert.equal(p[0].fee.id, 1); assert.equal(p[0].collectedAmountVnd, 6000)
  assert.equal(p[1].fee.id, 2); assert.equal(p[1].collectedAmountVnd, 4000)
})
function partialLedger() {
  let current = structuredClone(debt), sequence = 20
  return {
    detail: async () => structuredClone(current),
    collectPartial: async (id, input) => {
      const items = [], before = current.totalOutstandingVnd; let remaining = input.amountVnd
      const nextFees = current.fees.map(fee => {
        const amount = Math.min(remaining, fee.outstandingAmountVnd); remaining -= amount
        if (amount > 0) items.push({ feeId: fee.id, feeType: fee.feeType, description: fee.description,
          originalAmountVnd: fee.originalAmountVnd, previouslyPaidVnd: fee.paidAmountVnd, waivedAmountVnd: fee.waivedAmountVnd, collectedAmountVnd: amount })
        return { ...fee, paidAmountVnd: fee.paidAmountVnd + amount, outstandingAmountVnd: fee.outstandingAmountVnd - amount }
      }).filter(fee => fee.outstandingAmountVnd > 0)
      const saved = { ...receipt, id: ++sequence, receiptNumber: `BL-${sequence}`, transactionNumber: `GD-${sequence}`,
        totalAmountVnd: input.amountVnd, debtBeforeVnd: before, debtAfterVnd: before - input.amountVnd, paymentMode: 'PARTIAL', items }
      current = { ...current, fees: nextFees, totalOutstandingVnd: saved.debtAfterVnd, hasDebt: saved.debtAfterVnd > 0,
        debtToken: String(sequence).padStart(64, '0'), payments: [saved, ...current.payments] }
      return saved
    },
  }
}
test('demo collects 10000 then 15000 then 5000, retains debt until final receipt', async () => {
  const ledger = partialLedger(), c = collector(ledger); await c.select()
  for (const [amount, remaining, code] of [[10000, 20000, 'BL-21'], [15000, 5000, 'BL-22'], [5000, 0, 'BL-23']]) {
    c.partialAmount(String(amount)); assert.match(c.html(), new RegExp(vnd(remaining).replaceAll('.', '\\.')))
    await c.payPartial(); assert.match(c.html(), new RegExp(code)); assert.equal(c.calls.at(-1).input.amountVnd, amount)
    if (remaining > 0) assert.match(c.html(), /giữ trạng thái Nợ phí/)
    else { assert.match(c.html(), /Không nợ phí/); assert.doesNotMatch(c.html(), />Thu một phần</) }
  }
  assert.equal(c.calls.length, 3); assert.equal(c.calls.reduce((total, call) => total + call.input.amountVnd, 0), 30000)
  assert.match(c.html(), /Số dư sau lần thu: 20.000 VND/); assert.match(c.html(), /Số dư sau lần thu: 5.000 VND/)
})
test('partial UI rejects invalid cash and never sends it', async () => {
  const c = collector(partialLedger()); await c.select()
  for (const amount of ['0', '-1', '1.5', 'abc', '30001']) {
    c.partialAmount(amount)
    const button = find(c.render(), e => e.props?.children === 'Thu một phần' && e.props.onClick)
    assert.equal(button.props.disabled, true, amount)
    await c.payPartial(); assert.equal(c.calls.length, 0)
  }
})
test('partial double confirmation sends one request and one receipt', async () => {
  const ledger = partialLedger(); let resolve
  const waiting = new Promise(r => { resolve = r })
  const c = collector({ detail: ledger.detail, collectPartial: async (...args) => { await waiting; return ledger.collectPartial(...args) } })
  await c.select(); c.partialAmount('4000'); c.click('Thu một phần')
  const dialog = find(c.render(), e => e.props?.confirmLabel === 'Xác nhận thu một phần')
  dialog.props.onConfirm(); dialog.props.onConfirm(); assert.equal(c.calls.length, 1)
  resolve(); await flush(); assert.match(c.html(), /26.000 VND/)
})
test('ambiguous partial response freezes amount and retries the identical payload', async () => {
  const ledger = partialLedger(); let attempt = 0
  const c = collector({ detail: ledger.detail, collectPartial: async (...args) => {
    if (++attempt === 1) throw new Error('Mất phản hồi'); return ledger.collectPartial(...args)
  } })
  await c.select(); c.partialAmount('10000'); await c.payPartial()
  const input = find(c.render(), e => e.props?.label === 'Số tiền bạn đọc thực tế nộp (VND)')
  assert.equal(input.props.disabled, true); assert.match(c.html(), /Xác nhận lại giao dịch/)
  c.click('Xác nhận lại giao dịch'); await flush()
  assert.equal(c.calls.length, 2); assert.equal(c.calls[0].input, c.calls[1].input)
  assert.equal(c.calls[1].input.amountVnd, 10000); assert.match(c.html(), /20.000 VND/)
})
test('partial stale balance refreshes debts and requires another confirmation', async () => {
  let reads = 0
  const c = collector({ detail: async () => ++reads === 1 ? debt : { ...debt, fees: [fees[1], fees[2]], totalOutstandingVnd: 20000 },
    collectPartial: async () => { throw Object.assign(new Error('Nợ đã đổi'), { response: { data: { code: 'FEE_DEBT_CHANGED' } } }) } })
  await c.select(); c.partialAmount('10000'); await c.payPartial(); assert.equal(reads, 2)
  assert.match(c.html(), /20.000 VND/); assert.doesNotMatch(c.html(), /Xác nhận lại giao dịch/)
})
test('refresh failure after committed partial receipt keeps updated fees and prevents further collection', async () => {
  const ledger = partialLedger(); let reads = 0
  const c = collector({ detail: async () => { if (++reads > 1) throw new Error('Không tải được'); return ledger.detail() }, collectPartial: ledger.collectPartial })
  await c.select(); c.partialAmount('4000'); await c.payPartial()
  assert.match(c.html(), /26.000 VND/); assert.match(c.html(), /4.000 VND/); assert.match(c.html(), /BL-21/)
  const button = find(c.render(), e => e.props?.children === 'Thu một phần' && e.props.onClick)
  assert.equal(button.props.disabled, true); assert.match(c.html(), /Bấm Tải lại/)
})
test('partial receipt prints only collected allocations and the historical remaining balance', () => {
  const h = harness(), m = load('FeeReceiptDialog.tsx', { ...imports(h), '../../components/ui/Modal': component(({ children }) => children), './feePaymentService': { feeTypeLabel: () => 'Khoản phí' } })
  const partial = { ...receipt, totalAmountVnd: 4000, debtBeforeVnd: 30000, debtAfterVnd: 26000, paymentMode: 'PARTIAL',
    items: [{ ...receipt.items[0], collectedAmountVnd: 4000 }] }
  const html = renderToStaticMarkup(react.createElement(m.ReceiptContent, { receipt: partial }))
  assert.match(html, /4.000 VND/); assert.match(html, /26.000 VND/); assert.doesNotMatch(html, /10.000 VND|15.000 VND|5.000 VND/)
  assert.match(html, /vẫn còn nợ phí/); assert.doesNotMatch(html, /Đã thu đủ các khoản phí/)
})
