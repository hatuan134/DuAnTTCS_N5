import assert from 'node:assert/strict'
import { test } from 'node:test'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import vm from 'node:vm'
import ts from 'typescript'
const require = createRequire(import.meta.url), react = require('react')
const flush = () => new Promise(resolve => setImmediate(resolve))
function find(node, predicate) {
  if (!node || typeof node !== 'object') return
  if (Array.isArray(node)) { for (const child of node) { const hit = find(child, predicate); if (hit) return hit }; return }
  return predicate(node) ? node : find(node.props?.children, predicate)
}
function harness({ readOnly = false, current = null, history = [], save = async () => {} } = {}) {
  let cursor = 0, tree
  const state = [], pending = [], calls = []
  const hooks = { ...react,
    useState(initial) { const i = cursor++; if (!(i in state)) state[i] = initial; return [state[i], v => { state[i] = typeof v === 'function' ? v(state[i]) : v }] },
    useRef(initial) { const i = cursor++; return state[i] ??= { current: initial } },
    useEffect(fn, deps) { const i = cursor++, old = state[i]; if (!old || deps.some((v,n) => v !== old.deps[n])) { state[i] = { deps }; pending.push(() => { old?.cleanup?.(); state[i].cleanup = fn() }) } },
  }
  const modules = { react: hooks,
    './librarySettingsService': { librarySettingsService: { percentagePolicies: async () => ({ current, history }), savePercentagePolicy: async value => { calls.push(value); return save(value) } } },
    '../s1-02-user-management/accountService': { getApiErrorMessage: e => e.message },
    '../s3-01-loans/loanService': { formatLoanTimestamp: v => v },
  }
  for (const name of ['Button','Card','FeedbackAlert','Input']) modules[`../../components/ui/${name}`] = { __esModule: true, default: name }
  const context = { exports: {}, require: n => modules[n] ?? require(n) }
  vm.runInNewContext(ts.transpileModule(readFileSync(new URL('../src/features/s1-09-library-config/OverdueFeeSettingsPanel.tsx', import.meta.url), 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX, target: ts.ScriptTarget.ES2022 },
  }).outputText, context)
  function render() { cursor = 0; tree = context.exports.default({ readOnly }); for (const effect of pending.splice(0)) effect(); return tree }
  render()
  return { render, calls, node: predicate => find(tree,predicate), text: () => JSON.stringify(tree), unmount: () => state.forEach(v => v?.cleanup?.()) }
}
test('current policy stays visible while policy history and scheduling controls are removed; read-only cannot save', async () => {
  const current = { id: 1, dailyPercent: 1, capPercent: 100, effectiveAt: '2026-01-01', createdAt: '2026-01-01' }
  const h = harness({ readOnly: true, current, history: [{ ...current, id: 2, dailyPercent: 2, effectiveAt: '2099-01-01' }] })
  await flush(); h.render()
  assert.match(h.text(), /Đang áp dụng cho bàn giao mới/)
  assert.equal(h.text().includes('2099-01-01'), false)
  assert.equal(h.node(n => n.props?.type === 'datetime-local'), undefined)
  assert.equal(h.node(n => n.type === 'form'), undefined)
  assert.equal(h.calls.length, 0); h.unmount()
})
test('no default monetary rate; saving requires explicit valid rate and confirmation; duplicate click is guarded', async () => {
  let release
  const h = harness({ save: () => new Promise(resolve => { release = resolve }) })
  await flush(); h.render()
  assert.equal(h.node(n => n.type === 'Input').props.value, '')
  h.node(n => n.type === 'form').props.onSubmit({ preventDefault() {} }); h.render()
  assert.equal(h.calls.length, 0)
  assert.ok(h.node(n => n.type === 'FeedbackAlert' && n.props.tone === 'error'))
  h.node(n => n.type === 'Input').props.onChange({ target: { value: '1.25' } }); h.render()
  h.node(n => n.type === 'form').props.onSubmit({ preventDefault() {} }); h.render()
  assert.equal(h.calls.length, 0)
  const confirm = h.node(n => n.type === 'Button' && n.props.children === 'Xác nhận lưu chính sách')
  confirm.props.onClick(); confirm.props.onClick(); h.render()
  assert.equal(h.calls.length, 1); assert.equal(h.calls[0].dailyPercent, 1.25)
  assert.equal(h.calls[0].capPercent, null); assert.equal(h.calls[0].effectiveAt, null)
  release(); await flush(); h.render()
  assert.ok(h.node(n => n.type === 'FeedbackAlert' && n.props.tone === 'success')); h.unmount()
})
