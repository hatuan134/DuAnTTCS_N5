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
const FeedbackStub = Object.assign(() => null, { displayName: 'FeedbackAlert' })
function find(node, predicate) {
  if (!node || typeof node !== 'object') return undefined
  if (Array.isArray(node)) { for (const child of node) { const result = find(child, predicate); if (result) return result } return undefined }
  return predicate(node) ? node : find(node.props?.children, predicate)
}
function text(node) {
  if (node == null || typeof node === 'boolean') return ''
  if (Array.isArray(node)) return node.map(text).join(' ')
  return typeof node === 'object' ? text(node.props?.children) : String(node)
}
function page(path, imports = {}, props = {}, exported = 'default') {
  const f = hooksFixture(); let tree
  const Stub = () => null
  const dependencies = {
    react: f.hooks,
    '../../components/ui/FeedbackAlert': { __esModule: true, default: FeedbackStub },
    '../../components/ui/Button': { __esModule: true, default: Stub },
    '../../components/ui/Card': { __esModule: true, default: Stub },
    '../s1-02-user-management/accountService': { getApiErrorMessage: e => e.message },
    './pickupService': { formatPickupDate: v => v, pickupService: {} }, ...imports,
  }
  const Component = load(path, dependencies, { window: { confirm: () => true } })[exported]
  const render = () => { tree = f.render(() => Component(props)); return tree }; render()
  return { render, notice: () => find(tree, n => n.type === FeedbackStub),
    button: needle => find(tree, n => n.type === 'button' && n.props.onClick && text(n).includes(needle)), tree: () => tree }
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
for (const fail of [false, true]) test(`old auto-cancellation manual scan ${fail ? 'failure' : 'success'} uses 3000ms notification`, async () => {
  const p = page('../src/features/s3-06-auto-cancellations/AutoCancelledReservationsPage.tsx', {
    './autoCancellationService': { autoCancellationService: {
      getLast30Days: async () => [], getLatestRun: async () => null,
      triggerRun: async () => { if (fail) throw { response: { data: { message: 'Quét thất bại.' } } }
        return { totalIdentified: 2, totalCancelled: 2, totalTransferred: 0, totalReleased: 2 } },
    } },
  })
  await settle(); p.render(); await p.button('Quét thủ công').props.onClick(); p.render()
  assert.equal(p.notice().props.tone, fail ? 'error' : 'success'); verifyTimer(p)
})
for (const fail of [false, true]) test(`old reader reservation ${fail ? 'failure' : 'success'} uses 3000ms notification`, async () => {
  const p = page('../src/features/s2-07-reservations/ReserveBookPanel.tsx', {
    '../../core/auth/authStorage': { getCurrentUser: () => ({ role: 'READER' }), getAccessToken: () => 'token' },
    './reservationService': { activeReservationCount: () => 0, reservationService: {
      listMine: async () => [], reserveMany: async () => { if (fail) throw new Error('Không thể đặt giữ.')
        return { message: 'Đặt giữ thành công.', createdCount: 1, remainingActiveSlots: 2, activeReservationCount: 1,
          reservations: [{ id: 4, reservedAt: '2026-10-09T01:00:00Z' }] } },
    } },
  }, { bookId: 4, availableCount: 1, onReserved() {} })
  await settle(); p.render(); p.button('Đặt giữ').props.onClick(); await settle(); p.render()
  assert.equal(p.notice().props.tone, fail ? 'error' : 'success'); verifyTimer(p)
})
test('old staff cancellation notification expires while audit result remains available', () => {
  const p = page('../src/features/s2-09-ready-pickup/CancelReservationPanel.tsx', {}, {
    result: { message: 'Đã huỷ đơn.', cancellation: { cancelledAt: '2026-10-09', cancelledByName: 'Thủ thư', reason: 'Theo yêu cầu' }, copyOutcome: 'NO_COPY' },
  }, 'CancellationNotice')
  verifyTimer(p); assert.ok(find(p.tree(), n => n.type === 'div' && n.props.role === 'status'))
})
