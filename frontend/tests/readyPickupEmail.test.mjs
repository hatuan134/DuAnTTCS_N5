import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import { createRequire } from 'node:module'
import vm from 'node:vm'
import ts from 'typescript'
const require = createRequire(import.meta.url), react = require('react')
const base = '../src/features/s2-09-ready-pickup/'
const settle = () => new Promise(resolve => setImmediate(resolve))
function load(file, imports = {}, extra = {}) {
  const context = { exports: {}, require: name => imports[name] ?? require(name), ...extra }
  vm.runInNewContext(ts.transpileModule(readFileSync(new URL(file, import.meta.url), 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX },
  }).outputText, context)
  return context.exports
}
function find(node, predicate) {
  if (!node || typeof node !== 'object') return undefined
  if (Array.isArray(node)) { for (const child of node) { const result = find(child, predicate); if (result) return result } return undefined }
  return predicate(node) ? node : find(node.props?.children, predicate)
}
function text(node) {
  if (node == null || typeof node === 'boolean') return ''
  return Array.isArray(node) ? node.map(text).join(' ') : typeof node === 'object' ? text(node.props?.children) : String(node)
}
const stub = () => null, Feedback = () => null, Confirm = () => null, Button = () => null
function fixture(api, data = {}) {
  const slots = [], effects = [], intervals = new Map(); let cursor = 0, tree
  const hooks = { ...react,
    useState(initial) { const i = cursor++; if (!(i in slots)) slots[i] = initial; return [slots[i], value => { slots[i] = typeof value === 'function' ? value(slots[i]) : value }] },
    useRef(initial) { const i = cursor++; return slots[i] ??= { current: initial } },
    useCallback(fn, deps) { const i = cursor++, old = slots[i]; if (!old || deps.some((v,n) => !Object.is(v,old.deps[n]))) slots[i] = { deps, fn }; return slots[i].fn },
    useEffect(fn, deps) { const i = cursor++, old = slots[i]; if (!old || deps.some((v,n) => !Object.is(v,old.deps[n]))) { slots[i] = { deps, cleanup: old?.cleanup }; effects.push(() => { old?.cleanup?.(); slots[i].cleanup = fn() }) } },
  }
  const Component = load(base + 'ReadyPickupEmailPanel.tsx', {
    react: hooks, '../../components/ui/Button': { __esModule: true, default: Button },
    '../../components/ui/FeedbackAlert': { __esModule: true, default: Feedback },
    '../../components/ui/StatusBadge': { __esModule: true, default: stub },
    '../../components/ui/ConfirmActionDialog': { __esModule: true, default: Confirm },
    '../s1-02-user-management/accountService': { getApiErrorMessage: e => e.message },
    './pickupService': { formatPickupDate: v => v || 'Chưa gửi' },
    './readyEmailService': { readyEmailService: api, pickupEmailLabels: { PENDING: 'Chờ gửi', RETRY: 'Chờ thử lại', SENT: 'Đã gửi' } },
  }, { document: { visibilityState: 'visible' }, window: {
    setInterval(fn) { intervals.set(intervals.size + 1, fn); return intervals.size }, clearInterval(id) { intervals.delete(id) },
  } }).default
  const render = () => { cursor = 0; tree = Component({ reservationId: 20, ...data }); effects.splice(0).forEach(fn => fn()); return tree }
  render()
  return { render, tree: () => tree, notice: () => find(tree, n => n.type === Feedback),
    button: label => find(tree, n => n.type === Button && text(n).includes(label)),
    confirm: () => find(tree, n => n.type === Confirm),
    unmount() { for (const slot of slots) slot?.cleanup?.() }, intervals }
}
const history = status => ({ reservationId: 20, reservationStatus: 'READY_FOR_PICKUP', pickupLocation: 'Quầy nhận sách số 1', timeZone: 'Asia/Ho_Chi_Minh', notifications: [{ id: 1, status, bookTitle: 'Lập trình Java', recipientEmail: 'a@example.com', eventAt: '2026-10-14T09:00:00+07:00', sendBefore: '2026-10-14T09:05:00+07:00', sentAt: null, attemptCount: 0, pickupDeadline: '2026-10-15T17:00:00+07:00', pickupLocation: 'Quầy nhận sách số 1', emailContent: '', attempts: [] }] })

test('email API uses authenticated reservation endpoints and explicit simulation flag', async () => {
  const calls = []
  const api = load(base + 'readyEmailService.ts', { '../../core/api/apiClient': { apiClient: {
    get: async path => { calls.push([path]); return { data: {} } },
    post: async (path, body) => { calls.push([path, body]); return { data: {} } },
  } } }).readyEmailService
  await api.history(20); await api.trigger(20, true)
  assert.equal(calls[0][0], '/reservations/20/ready-email')
  assert.equal(calls[1][0], '/reservations/20/ready-email/trigger')
  assert.equal(calls[1][1].simulateFailure, true)
})
for (const simulate of [false,true]) test(`staff confirmation ${simulate ? 'simulation' : 'real send'} remains open before approval and produces dismissible result`, async () => {
  const calls = [], p = fixture({ history: async () => history('PENDING'), trigger: async (id, flag) => { calls.push([id,flag]); return { message: 'Đã xử lý', history: history(simulate ? 'RETRY' : 'SENT') } } })
  await settle(); p.render(); p.button(simulate ? 'Mô phỏng lỗi tạm thời' : 'Chạy thử gửi thật').props.onClick(); p.render()
  assert.ok(p.confirm()); assert.equal(calls.length, 0)
  assert.match(p.confirm().props.description, simulate ? /không gửi thư thật/ : /email thật/)
  p.confirm().props.onConfirm(); await settle(); p.render()
  assert.deepEqual(calls, [[20,simulate]]); assert.equal(p.confirm(), undefined)
  assert.ok(p.notice()); p.notice().props.onDismiss(); p.render(); assert.equal(p.notice(), undefined)
  assert.match(text(p.tree()), /Lập trình Java/); p.unmount()
})
test('already-sent history remains visible without a resend button', async () => {
  const p = fixture({ history: async () => history('SENT') }); await settle();p.render()
  assert.equal(p.button('Chạy thử gửi thật'),undefined); assert.match(text(p.tree()), /Quầy nhận sách số 1/); p.unmount()
})
test('claimed event refreshes automatically but cannot be sent concurrently by staff', async () => {
  const p = fixture({ history: async () => history('SENDING') }); await settle();p.render()
  assert.equal(p.button('Chạy thử gửi thật').props.disabled,true); assert.equal(p.intervals.size,1);p.unmount();assert.equal(p.intervals.size,0)
})
test('API load failure uses temporary error and persistent retry guidance', async () => {
  const p = fixture({ history: async () => { throw new Error('Lỗi API') } });await settle();p.render()
  assert.equal(p.notice().props.tone,'error');p.notice().props.onDismiss();p.render()
  assert.match(text(p.tree()), /Chưa tải được lịch sử/);assert.equal(p.notice(),undefined);p.unmount()
})
test('trigger API failure uses temporary error without removing stored history', async () => {
  const p=fixture({ history: async () => history('PENDING'), trigger: async () => { throw new Error('Không thể chạy thử') } })
  await settle();p.render();p.button('Chạy thử gửi thật').props.onClick();p.render();p.confirm().props.onConfirm();await settle();p.render()
  assert.equal(p.notice().props.tone,'error');assert.match(text(p.tree()), /Lập trình Java/);p.unmount()
})

test('combined history labels last-day reminder and still targets the pending ready email', async () => {
  const data=history('PENDING')
  data.notifications[0].notificationType='READY_FOR_PICKUP'
  data.notifications.unshift({...data.notifications[0],id:2,notificationType:'LAST_DAY_REMINDER',status:'SENT'})
  const p=fixture({history:async()=>data});await settle();p.render()
  assert.match(text(p.tree()),/Nhắc ngày cuối/);assert.match(text(p.tree()),/Sách sẵn sàng/)
  assert.equal(p.button('Chạy thử gửi thật').props.disabled,false);p.unmount()
})
test('pending reminder cannot make an already-sent ready email resendable', async () => {
  const data=history('SENT')
  data.notifications[0].notificationType='READY_FOR_PICKUP'
  data.notifications.unshift({...data.notifications[0],id:2,notificationType:'LAST_DAY_REMINDER',status:'PENDING'})
  const p=fixture({history:async()=>data});await settle();p.render()
  assert.equal(p.button('Chạy thử gửi thật'),undefined);assert.equal(p.intervals.size,1);p.unmount()
})
