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
test('disabled activation is visible before submitting and blocks saving without choosing a default rate', async () => {
  const h = harness(); await flush(); h.render()
  assert.match(h.text(), /DEPOSIT_POLICY_ACTIVATION_ENABLED/)
  assert.equal(h.node(n => n.type === 'Input').props.disabled, true)
  assert.equal(h.node(n => n.type === 'Input').props.value, '')
  assert.equal(h.node(n => n.type === 'Button').props.disabled, true)
  assert.equal(h.node(n => n.type === 'details'), undefined)
  h.unmount()
})
test('enabled activation requires confirmation and sends immediate policy without scheduling or history controls', async () => {
  const h = harness({ activationEnabled: true }); await flush(); h.render()
  h.node(n => n.type === 'Input').props.onChange({target:{value:'35'}}); h.render()
  h.node(n => n.type === 'form').props.onSubmit({preventDefault(){}}); h.render()
  assert.equal(h.calls.length,0)
  h.node(n => n.type === 'Button' && n.props.children === 'Xác nhận lưu').props.onClick()
  await flush(); h.render()
  assert.deepEqual(h.calls,[{percentage:35,effectiveAt:null}])
  assert.equal(h.node(n => n.props?.type === 'datetime-local'),undefined)
  h.unmount()
})
function harness({ readOnly = false, current = null, history = [], activationEnabled = false, save = async () => {} } = {}) {
  let cursor = 0, tree
  const state = [], pending = [], calls = []
  const hooks = { ...react,
    useState(initial) { const i = cursor++; if (!(i in state)) state[i] = initial; return [state[i], v => { state[i] = typeof v === 'function' ? v(state[i]) : v }] },
    useRef(initial) { const i = cursor++; return state[i] ??= { current: initial } },
    useEffect(fn, deps) { const i = cursor++, old = state[i]; if (!old || deps.some((v,n) => v !== old.deps[n])) { state[i] = { deps }; pending.push(() => { old?.cleanup?.(); state[i].cleanup = fn() }) } },
  }
  const modules = { react: hooks,
    './depositService': { depositService: { policies: async () => ({ current, history, activationEnabled }), policy: async (percentage, effectiveAt) => { calls.push({percentage,effectiveAt}); return save() } } },
    '../s1-02-user-management/accountService': { getApiErrorMessage: e => e.message },
    '../s3-01-loans/loanService': { formatLoanTimestamp: v => v },
  }
  for (const name of ['Button','Card','FeedbackAlert','Input']) modules[`../../components/ui/${name}`] = { __esModule: true, default: name }
  const context = { exports: {}, require: n => modules[n] ?? require(n) }
  vm.runInNewContext(ts.transpileModule(readFileSync(new URL('../src/features/deposits/DepositPolicyPanel.tsx', import.meta.url), 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX, target: ts.ScriptTarget.ES2022 },
  }).outputText, context)
  function render() { cursor = 0; tree = context.exports.default({ readOnly }); for (const effect of pending.splice(0)) effect(); return tree }
  render()
  return { render, calls, node: predicate => find(tree,predicate), text: () => JSON.stringify(tree), unmount: () => state.forEach(v => v?.cleanup?.()) }
}
