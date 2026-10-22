import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import { createRequire } from 'node:module'
import vm from 'node:vm'
import ts from 'typescript'
const require = createRequire(import.meta.url), react = require('react')
const root = new URL('../src/features/s4-02-fee-payments/', import.meta.url)
const stub = name => ({ __esModule: true, default: name })
const flush = () => new Promise(resolve => setImmediate(resolve))
function find(node, predicate) {
  if (!node || typeof node !== 'object') return undefined
  if (Array.isArray(node)) { for (const child of node) { const hit = find(child, predicate); if (hit) return hit }; return undefined }
  return predicate(node) ? node : find(node.props?.children, predicate)
}
function harness(list) {
  let cursor = 0, tree
  const state = [], pending = [], calls = []
  const hooks = { ...react,
    useState(initial) { const i = cursor++; if (!(i in state)) state[i] = initial; return [state[i], v => { state[i] = typeof v === 'function' ? v(state[i]) : v }] },
    useEffect(fn, deps) { const i = cursor++, old = state[i]; if (!old || deps.some((v,n) => v !== old.deps[n])) { state[i] = { deps, cleanup: old?.cleanup }; pending.push(() => { old?.cleanup?.(); state[i].cleanup = fn() }) } },
  }
  const modules = { react: hooks,
    './feePaymentService': { feeTypeLabel: () => 'Khoản phí', feePaymentService: { list: async (...args) => { calls.push(args); return list(...args) } } },
    '../s1-02-user-management/accountService': { getApiErrorMessage: e => e.message },
    '../s3-07-returns/ReturnFeeDetails': { formatVnd: n => `${n} VND` },
    '../s3-01-loans/loanService': { formatLoanTimestamp: v => v },
  }
  for (const name of ['Button','Card','EmptyState','LoadingState','TablePagination']) modules[`../../components/ui/${name}`] = stub(name)
  const context = { exports: {}, require: name => modules[name] ?? require(name), AbortController }
  vm.runInNewContext(ts.transpileModule(readFileSync(new URL('FeePaymentList.tsx', root), 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX, target: ts.ScriptTarget.ES2022 },
  }).outputText, context)
  function render() { cursor = 0; tree = context.exports.default({ disabled: false, revision: 0, onSelect() {}, onReceipt() {} }); for (const effect of pending.splice(0)) effect(); return tree }
  render()
  return { render, calls, node: predicate => find(tree,predicate), unmount: () => state.forEach(v => v?.cleanup?.()) }
}
const page = { page: 0, size: 10, totalItems: 23, items: [{ id: 1, reader: { id: 7, fullName: 'Bạn đọc', email: 'test@example.invalid' }, feeType: 'RECORDED', amountVnd: 10000, paidAmountVnd: 4000, waivedAmountVnd: 0, outstandingAmountVnd: 6000, paymentStatus: 'PARTIAL', latestPaymentId: 5 }] }
test('loads a default server page, changes page and resets to first page for filters/search', async () => {
  const h=harness(async () => page)
  await flush();h.render();assert.equal(h.calls.length,1);assert.equal(h.calls[0][1],0)
  assert.equal(h.node(n => n.type === 'TablePagination').props.pageSize,10)
  h.node(n => n.type === 'TablePagination').props.onPageChange(3);h.render();await flush();h.render()
  assert.equal(h.calls.at(-1)[1],2)
  const select=h.node(n => n.type === 'select')
  select.props.onChange({ target: { value: 'PAID' } });h.render();await flush();h.render()
  assert.equal(h.calls.at(-1)[0].status,'PAID');assert.equal(h.calls.at(-1)[1],0)
  h.node(n => n.type === 'input' && n.props.maxLength === 255).props.onChange({ target: { value: ' THE-01 ' } });h.render()
  h.node(n => n.type === 'form').props.onSubmit({ preventDefault() {} });h.render();await flush();h.render()
  assert.equal(h.calls.at(-1)[0].query,'THE-01');assert.equal(h.calls.at(-1)[1],0)
  h.unmount()
})
test('empty and failed requests render recovery states without fake records', async () => {
  const empty=harness(async () => ({ ...page, items: [], totalItems: 0 }));await flush();empty.render()
  assert.ok(empty.node(n => n.type === 'EmptyState'));empty.unmount()
  const failed=harness(async () => { throw new Error('Offline') });await flush();failed.render()
  assert.equal(failed.node(n => n.props?.role === 'alert').props.children,'Offline');failed.unmount()
})
