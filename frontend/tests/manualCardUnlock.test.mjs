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

const path = '../src/features/s1-04-library-card/LibraryCardDetailsDialog.tsx'
const stub = () => null
const basic = {
  '../../components/ui/Modal': { __esModule: true, default: stub }, '../../components/ui/Button': { __esModule: true, default: stub },
  '../../components/ui/FeedbackAlert': { __esModule: true, default: stub }, '../../components/ui/StatusBadge': { __esModule: true, default: stub },
  '../../core/auth/authStorage': { getCurrentUser: () => ({ role: 'LIBRARY_MANAGER' }) },
  '../s1-02-user-management/accountService': { getApiErrorMessage: e => e.message },
  './ManualCardLockDetails': { __esModule: true, default: stub, formatLockTime: v => v },
}
const original = { id: 5, cardNumber: 'LIB-A', readerName: 'Nguyễn Văn A', memberCode: 'BD12',
  cardTypeName: 'Sinh viên', expiresAt: '2099-01-01', status: 'LOCKED', unpaidFees: 15000,
  manualLock: { active: true, reason: 'Vi phạm', startedAt: '2026-10-14T10:00:00+07:00', endsAt: null, actorUserId: 8 } }
const details = { card: original, lockActorName: 'Quản lý A', history: [] }
const settle = () => new Promise(resolve => setImmediate(resolve))
function dialog(api, role = 'LIBRARY_MANAGER', getDetails = async () => details) {
  const f = fixture(), saved = [], requests = []
  const Component = load(path, { ...basic, react: f.react,
    '../../core/auth/authStorage': { getCurrentUser: () => ({ role }) },
    './libraryCardService': { libraryCardService: { getDetails,
      unlockCard: (...args) => { requests.push(args); return api(...args) } } },
  }).default
  let tree; const props = { cardId: 5, onClose() {}, onUpdated: value => saved.push(value) }
  const render = () => tree = f.render(() => Component(props)); render()
  return { render, saved, requests, f,
    find: predicate => find(tree, predicate),
    button: label => find(tree, n => n.props.children === label),
    notice: () => find(tree, n => n.props.tone && n.props.message),
    form: () => find(tree, n => n.type === 'form'),
  }
}
async function opened(api, role, getDetails) {
  const d = dialog(api, role, getDetails); await settle(); d.render()
  d.button('Mở khóa thẻ')?.props.onClick(); d.render(); return d
}
test('required and max length validation', () => {
  const exports = load(path, { ...basic, './libraryCardService': { libraryCardService: {} } })
  for (const value of ['', '   ', '\n\t']) assert.match(exports.validateUnlockReason(value), /Bắt buộc/)
  assert.match(exports.validateUnlockReason('x'.repeat(1001)), /1000/)
  assert.equal(exports.validateUnlockReason('  Đã xử lý  '), '')
})
test('blank submission has temporary feedback and persistent field validation; no API call', async () => {
  const d = await opened(async () => { throw Error('Should not post') })
  d.form().props.onSubmit({ preventDefault() {} }); d.render()
  assert.match(d.notice().props.message, /Bắt buộc/)
  d.notice().props.onDismiss(); d.render(); assert.equal(d.notice(), undefined)
  assert.ok(d.find(n => n.props.id === 'card-unlock-error')); assert.ok(d.form()); assert.equal(d.requests.length, 0)
})
test('successful release posts exact lock identity, normalizes reason and updates parent status', async () => {
  const result = { ...original, status: 'ACTIVE', manualLock: { ...original.manualLock, active: false } }
  let loads = 0
  const d = await opened(async () => result, undefined, async () => ++loads > 1 ? { ...details, card: result } : details)
  d.find(n => n.props.id === 'card-unlock-reason').props.onChange({ target: { value: '  Đã xử lý  ' } }); d.render()
  d.form().props.onSubmit({ preventDefault() {} }); await settle(); d.render(); await settle(); d.render()
  assert.deepEqual(d.requests, [[5, 'Đã xử lý', original.manualLock.startedAt]])
  assert.deepEqual(d.saved, [result]); assert.match(d.notice().props.message, /Hoạt động.*nợ phí/)
  assert.equal(d.button('Mở khóa thẻ'), undefined)
})
test('in-flight submit cannot post twice', async () => {
  let finish; const d = await opened(() => new Promise(resolve => { finish = resolve }))
  d.find(n => n.props.id === 'card-unlock-reason').props.onChange({ target: { value: 'Đã xử lý' } }); d.render()
  d.form().props.onSubmit({ preventDefault() {} }); d.form().props.onSubmit({ preventDefault() {} })
  assert.equal(d.requests.length, 1); finish({ ...original, status: 'ACTIVE' }); await settle(); d.f.unmount()
})
test('conflict keeps dialog open and reloads authoritative status without updating parent as success', async () => {
  let loads = 0; const d = await opened(async () => { throw Error('Thẻ không còn khóa có hiệu lực.') }, undefined,
    async () => { loads++; return loads === 1 ? details : { ...details, card: { ...original, manualLock: { ...original.manualLock, active: false } } } })
  d.find(n => n.props.id === 'card-unlock-reason').props.onChange({ target: { value: 'Đã xử lý' } }); d.render()
  d.form().props.onSubmit({ preventDefault() {} }); await settle(); d.render(); await settle(); d.render()
  assert.match(d.notice().props.message, /không còn khóa/); assert.equal(d.saved.length, 0)
  assert.equal(d.button('Mở khóa thẻ'), undefined); assert.ok(d.button('Đóng'))
})
for (const role of ['LIBRARIAN', 'READER']) test(`${role} never sees unlock action`, async () => {
  const d = dialog(async () => original, role); await settle(); d.render(); assert.equal(d.button('Mở khóa thẻ'), undefined); d.f.unmount()
})
test('load error produces feedback and retry without enabling an unlock from stale data', async () => {
  const d = dialog(async () => original, undefined, async () => { throw Error('Mất kết nối') })
  await settle(); d.render(); assert.match(d.notice().props.message, /Mất kết nối/)
  assert.ok(d.button('Thử lại')); assert.equal(d.button('Mở khóa thẻ'), undefined)
})
test('API detail and unlock routes retain contract and exact lock reference', async () => {
  const calls = []
  const service = load('../src/features/s1-04-library-card/libraryCardService.ts', {
    '../../core/api/apiClient': { apiClient: {
      get: async (...args) => { calls.push(args); return { data: details } },
      post: async (...args) => { calls.push(args); return { data: original } },
    } },
  }).libraryCardService
  assert.equal((await service.getDetails(5)).card.id, 5)
  await service.unlockCard(5, 'Đã xử lý', original.manualLock.startedAt)
  assert.equal(calls[0][0], '/library-cards/5'); assert.equal(calls[1][0], '/library-cards/5/unlock')
  assert.deepEqual(JSON.parse(JSON.stringify(calls[1][1])), { reason: 'Đã xử lý', lockStartedAt: original.manualLock.startedAt })
})

test('reader watches remote manual release only while active; interval is cleaned on status change and unmount', () => {
  const f = fixture(), intervals = new Map(), listeners = new Map(); let next = 0, calls = 0
  const document = { visibilityState: 'visible' }
  const hook = load('../src/features/s1-04-library-card/useCardLockExpiry.ts', { react: f.react }, { document, window: {
    setInterval(fn, ms) { intervals.set(++next, { fn, ms }); return next }, clearInterval(id) { intervals.delete(id) },
    addEventListener(name, fn) { listeners.set(name, fn) }, removeEventListener(name) { listeners.delete(name) },
  } }).default
  f.render(() => hook([{ active: true, endsAt: null }], () => calls++, true))
  assert.equal(intervals.size, 1); assert.equal([...intervals.values()][0].ms, 15000)
  ;[...intervals.values()][0].fn(); assert.equal(calls, 1)
  document.visibilityState = 'hidden'; [...intervals.values()][0].fn(); assert.equal(calls, 1)
  f.render(() => hook([{ active: false, endsAt: null }], () => calls++, true))
  assert.equal(intervals.size, 0); f.unmount(); assert.equal(listeners.size, 0)
})
