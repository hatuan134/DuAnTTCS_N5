import assert from 'node:assert/strict'
import { test } from 'node:test'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import vm from 'node:vm'
import ts from 'typescript'
const require = createRequire(import.meta.url), react = require('react')
function load(path, imports, globals = {}) {
  const context = { exports: {}, require: name => imports[name] ?? require(name), ...globals }
  vm.runInNewContext(ts.transpileModule(readFileSync(new URL(path, import.meta.url), 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX },
  }).outputText, context)
  return context.exports
}
function fixture() {
  const slots = [], effects = []; let cursor = 0
  return {
    react: { ...react,
      useState(initial) { const i = cursor++; if (!(i in slots)) slots[i] = typeof initial === 'function' ? initial() : initial
        return [slots[i], value => { slots[i] = typeof value === 'function' ? value(slots[i]) : value }] },
      useRef(initial) { const i = cursor++; return slots[i] ??= { current: initial } },
      useEffect(fn, deps) { const i = cursor++, old = slots[i]
        if (!old || deps.some((v, n) => !Object.is(v, old.deps[n]))) {
          slots[i] = { deps }; effects.push(() => { old?.cleanup?.(); slots[i].cleanup = fn() })
        } },
    },
    render(fn) { cursor = 0; const result = fn(); for (const effect of effects.splice(0)) effect(); return result },
    unmount() { for (const slot of slots) slot?.cleanup?.() },
  }
}
function find(node, predicate) {
  if (!node || typeof node !== 'object') return undefined
  if (Array.isArray(node)) { for (const child of node) { const found = find(child, predicate); if (found) return found } return undefined }
  return predicate(node) ? node : find(node.props?.children, predicate)
}
const path = '../src/features/s1-04-library-card/LockLibraryCardDialog.tsx'
const stub = () => null
const basic = {
  '../../components/ui/Modal': { default: stub }, '../../components/ui/Button': { default: stub },
  '../../components/ui/FeedbackAlert': { default: stub },
  '../s1-02-user-management/accountService': { getApiErrorMessage: e => e.message },
}
const funcs = load(path, { ...basic, './libraryCardService': { libraryCardService: {} } })
for (const reason of ['', '  ', '\n\t']) test(`blank reason ${JSON.stringify(reason)} is refused`, () => {
  assert.match(funcs.validateLockForm(reason, true, ''), /Bắt buộc/)
})
test('reason length, past date, empty date, malformed date and valid duration', () => {
  assert.match(funcs.validateLockForm('x'.repeat(1001), true, ''), /1000/)
  for (const date of ['', 'sai ngày', '2020-01-01T10:00', '2099-02-30T10:00']) assert.match(funcs.validateLockForm('Vi phạm', false, date), /hết hạn khóa/)
  assert.equal(funcs.validateLockForm('Vi phạm', false, funcs.sevenDaysLater()), '')
  assert.equal(funcs.validateLockForm('Vi phạm', true, ''), '')
})
function dialog(api) {
  const f = fixture(), saved = []
  const Component = load(path, { ...basic, react: f.react, './libraryCardService': { libraryCardService: { lockCard: api } } }).default
  const props = { card: { id: 5, readerName: 'Nguyễn Văn A', cardNumber: 'LIB-A', unpaidFees: 15000 }, onClose() {}, onLocked: card => saved.push(card) }
  let tree; const render = () => { tree = f.render(() => Component(props)); return tree }; render()
  return { render, saved, f,
    field: id => find(tree, n => n.props.id === id),
    checkbox: () => find(tree, n => n.type === 'input' && n.props.type === 'checkbox'),
    submit: () => find(tree, n => n.type === 'form').props.onSubmit({ preventDefault() {} }),
    notice: () => find(tree, n => n.props.tone === 'error' && n.props.message),
  }
}
const settle = () => new Promise(resolve => setImmediate(resolve))
test('invalid reason produces feedback and never posts', async () => {
  let calls = 0; const d = dialog(async () => { calls++ })
  d.field('card-lock-reason').props.onChange({ target: { value: '   ' } }); d.render(); d.submit(); await settle(); d.render()
  assert.equal(calls, 0); assert.match(d.notice().props.message, /Bắt buộc/)
  d.notice().props.onDismiss(); d.render(); assert.equal(d.notice(), undefined)
})
test('timed lock sends normalized reason and explicit Vietnam offset', async () => {
  const calls = [], result = { id: 5, status: 'LOCKED' }
  const d = dialog(async (...args) => { calls.push(args); return result })
  d.field('card-lock-reason').props.onChange({ target: { value: '  Vi phạm  ' } }); d.render()
  const end = d.field('card-lock-end').props.value; d.submit(); await settle()
  assert.deepEqual(calls, [[5, 'Vi phạm', `${end}:00+07:00`]]); assert.deepEqual(d.saved, [result])
})
test('indefinite lock sends null expiry and suppresses duplicate in-flight submissions', async () => {
  let finish, calls = 0; const d = dialog(() => { calls++; return new Promise(resolve => { finish = resolve }) })
  d.field('card-lock-reason').props.onChange({ target: { value: 'Vi phạm' } })
  d.checkbox().props.onChange({ target: { checked: true } }); d.render()
  assert.equal(d.field('card-lock-end'), undefined); d.submit(); d.submit(); assert.equal(calls, 1)
  finish({ id: 5 }); await settle(); assert.equal(d.saved.length, 1)
})
test('API rejection is shown and does not close the confirmation dialog', async () => {
  const d = dialog(async () => { throw new Error('Thẻ đang có lệnh khóa còn hiệu lực.') })
  d.field('card-lock-reason').props.onChange({ target: { value: 'Vi phạm' } }); d.render(); d.submit(); await settle(); d.render()
  assert.match(d.notice().props.message, /còn hiệu lực/); assert.equal(d.saved.length, 0)
  assert.ok(d.field('card-lock-reason'))
})
test('lock API keeps card identity and request payload', async () => {
  const calls = []
  const service = load('../src/features/s1-04-library-card/libraryCardService.ts', {
    '../../core/api/apiClient': { apiClient: { post: async (...args) => { calls.push(args); return { data: { status: 'LOCKED' } } } } },
  }).libraryCardService
  assert.equal((await service.lockCard(5, 'Vi phạm', null)).status, 'LOCKED')
  assert.equal(calls[0][0], '/library-cards/5/lock'); assert.deepEqual(JSON.parse(JSON.stringify(calls[0][1])), { reason: 'Vi phạm', endsAt: null })
})
test('lock explanation is fixed page content and disappears only when inactive', () => {
  const render = load('../src/features/s1-04-library-card/ManualCardLockDetails.tsx', {}).default
  const active = { active: true, reason: 'Vi phạm', startedAt: '2026-10-14T10:00:00+07:00', endsAt: null }
  const tree = render({ lock: active }); assert.ok(tree)
  assert.equal(render({ lock: { ...active, active: false } }), null)
})
test('expiry refresh cleans timers, refreshes at expiry, and reacts to focus', () => {
  const f = fixture(), timers = new Map(), listeners = new Map(); let id = 0, calls = 0
  const hook = load('../src/features/s1-04-library-card/useCardLockExpiry.ts', { react: f.react }, { window: {
    setTimeout(fn, ms) { timers.set(++id, { fn, ms }); return id }, clearTimeout(key) { timers.delete(key) },
    addEventListener(name, fn) { listeners.set(name, fn) }, removeEventListener(name) { listeners.delete(name) },
  } }).default
  const lock = { active: true, endsAt: new Date(Date.now() - 1).toISOString() }
  f.render(() => hook([lock], () => calls++)); assert.equal(timers.size, 1)
  assert.ok([...timers.values()][0].ms <= 1100); [...timers.values()][0].fn(); assert.equal(calls, 1)
  listeners.get('focus')(); assert.equal(calls, 2)
  f.unmount(); assert.equal(timers.size, 0); assert.equal(listeners.size, 0)
})
