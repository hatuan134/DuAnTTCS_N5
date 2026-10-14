import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import { createRequire } from 'node:module'
import vm from 'node:vm'
import ts from 'typescript'
const require = createRequire(import.meta.url), react = require('react')
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
function hooksFixture() {
  const slots = [], effects = []; let cursor = 0
  return {
    hooks: { ...react,
      useState(initial) { const i = cursor++; if (!(i in slots)) slots[i] = initial; return [slots[i], value => { slots[i] = typeof value === 'function' ? value(slots[i]) : value }] },
      useRef(initial) { const i = cursor++; return slots[i] ??= { current: initial } },
      useEffect(fn, deps) { const i = cursor++, old = slots[i]; if (!old || deps.some((v,n) => !Object.is(v,old.deps[n]))) { slots[i] = { deps, cleanup: old?.cleanup }; effects.push(() => { old?.cleanup?.(); slots[i].cleanup = fn() }) } },
    },
    render(fn) { cursor = 0; const tree = fn(); effects.splice(0).forEach(fn => fn()); return tree },
    unmount() { for (const slot of slots) slot?.cleanup?.() },
  }
}
const Stub = () => null, Button = () => null, Feedback = () => null, Confirm = () => null, History = () => null
const imports = {
  '../../components/ui/Button': { __esModule: true, default: Button },
  '../../components/ui/FeedbackAlert': { __esModule: true, default: Feedback },
  '../../components/ui/ConfirmActionDialog': { __esModule: true, default: Confirm },
  './ReadyPickupEmailPanel': { __esModule: true, default: History },
  './pickupService': { formatPickupDate: value => value || 'Chưa khai báo' },
  '../s1-02-user-management/accountService': { getApiErrorMessage: error => error.message },
}
function trial(api, onChanged = () => {}) {
  const f = hooksFixture()
  const Component = load('../src/features/s2-09-ready-pickup/PickupCancellationTrialPanel.tsx',
    { ...imports, react: f.hooks, './readyEmailService': { readyEmailService: api } }).default
  let tree; const render = () => (tree = f.render(() => Component({ onChanged }))); render()
  return { render, tree: () => tree, find: predicate => find(tree,predicate), notice: () => find(tree,n => n.type === Feedback),
    input(field,value) { find(tree,n => n.props?.id === field).props.onChange({ target: { value } }); render() },
    async submit() { find(tree,n => n.type === 'form').props.onSubmit({ preventDefault() {} }); await settle(); render() },
    unmount: f.unmount,
  }
}
const preview = canRunNow => ({ reservationId: 20, bookId: 9, bookTitle: 'Mạng máy tính', readerName: 'Bạn đọc A',
  recipientEmail: 'a@example.com', pickupDeadline: '2026-10-15T17:00:00+07:00',
  evaluatedAt: '2026-10-15T17:01:00+07:00', actualNow: '2026-10-14T20:00:00+07:00',
  eligible: true, canRunNow, reason: canRunNow ? 'Đơn đã quá hạn theo giờ thực tế.' : 'Mốc tương lai chỉ xem trước.', emailContent: '<p>Dự kiến</p>' })
function verifyTimer(p) {
  const f = hooksFixture(), timers = new Map(); let now = 0, next = 0
  const RealFeedback = load('../src/components/ui/FeedbackAlert.tsx', { react: f.hooks }, { window: {
    setTimeout(fn, ms) { timers.set(++next, { at: now + ms, fn }); return next }, clearTimeout(id) { timers.delete(id) },
  } }).default
  f.render(() => RealFeedback(p.notice().props)); now = 2999
  for (const timer of timers.values()) if (timer.at <= now) timer.fn()
  p.render(); assert.ok(p.notice()); now = 3000
  for (const [id,timer] of timers) if (timer.at <= now) { timers.delete(id); timer.fn() }
  p.render(); assert.equal(p.notice(),undefined); f.unmount()
}
test('preview time is explicitly Vietnam time, blank means actual time', () => {
  const helper = load('../src/features/s2-09-ready-pickup/PickupCancellationTrialPanel.tsx', { ...imports, './readyEmailService': {} }).cancellationCheckTime
  assert.equal(helper('2026-10-15T17:01'),'2026-10-15T17:01:00+07:00')
  assert.equal(helper(''),undefined);assert.throws(() => helper('invalid'))
})
test('future preview never posts cancellation and has no destructive button', async () => {
  const calls = []; const p = trial({ previewCancellation: async (...args) => { calls.push(args); return preview(false) },
    runCancellation: async () => { throw new Error('must not run') } })
  p.input('cancellation-trial-id','20');p.input('cancellation-trial-time','2026-10-15T17:01');await p.submit()
  assert.deepEqual(calls,[[20,'2026-10-15T17:01:00+07:00']])
  assert.equal(p.find(n => n.type === Button && text(n).includes('Hủy đơn quá hạn')),undefined)
  assert.match(text(p.tree()),/Mốc tương lai/);verifyTimer(p);assert.match(text(p.tree()),/Mạng máy tính/);p.unmount()
})
test('real run requires confirmation, posts one selected order once, then refreshes history', async () => {
  const calls = []; let changed = 0, resolve
  const p = trial({ previewCancellation: async () => preview(true), runCancellation: (...args) => {
    calls.push(args);return new Promise(done => { resolve = done })
  } }, () => changed++)
  p.input('cancellation-trial-id','20');await p.submit()
  p.find(n => n.type === Button && text(n).includes('Hủy đơn quá hạn')).props.onClick();p.render()
  const confirm = p.find(n => n.type === Confirm);assert.ok(confirm);assert.equal(calls.length,0)
  confirm.props.onConfirm();confirm.props.onConfirm();assert.equal(calls.length,1)
  resolve({ message: 'Đã hủy đơn. Email đang được xử lý.' });await settle();p.render()
  assert.deepEqual(calls,[[20,'2026-10-15T17:01:00+07:00']]);assert.equal(changed,1)
  assert.equal(p.find(n => n.type === Confirm),undefined);assert.ok(p.find(n => n.type === History))
  verifyTimer(p);assert.ok(p.find(n => n.type === History));p.unmount()
})
test('invalid ID warning expires but field validation stays', async () => {
  const p = trial({ previewCancellation: async () => { throw new Error('must not call') } })
  p.input('cancellation-trial-id','0');await p.submit();assert.equal(p.notice().props.tone,'warning')
  verifyTimer(p);assert.ok(p.find(n => n.props?.id === 'cancellation-trial-error'));p.unmount()
})
test('API error uses a 3000ms notice without inventing a preview', async () => {
  const p = trial({ previewCancellation: async () => { throw new Error('Không tìm thấy đơn đặt giữ.') } })
  p.input('cancellation-trial-id','20');await p.submit();assert.equal(p.notice().props.tone,'error')
  verifyTimer(p);assert.equal(p.find(n => n.type === History),undefined);p.unmount()
})
test('run API recheck failure leaves stored preview and history visible', async () => {
  const p = trial({ previewCancellation: async () => preview(true), runCancellation: async () => { throw new Error('Sách vừa được nhận.') } })
  p.input('cancellation-trial-id','20');await p.submit()
  p.find(n => n.type === Button && text(n).includes('Hủy đơn quá hạn')).props.onClick();p.render()
  p.find(n => n.type === Confirm).props.onConfirm();await settle();p.render()
  assert.equal(p.notice().props.tone,'error');verifyTimer(p);assert.ok(p.find(n => n.type === History));p.unmount()
})
test('changing the chosen order clears old preview before another run', async () => {
  const p = trial({ previewCancellation: async () => preview(true) });p.input('cancellation-trial-id','20');await p.submit()
  p.input('cancellation-trial-id','21');assert.equal(p.find(n => n.type === History),undefined)
  assert.equal(p.find(n => n.type === Button && text(n).includes('Hủy đơn quá hạn')),undefined);p.unmount()
})
test('cancellation API reuses ready-email endpoints and sends explicit evaluation time', async () => {
  const calls = []
  const api = load('../src/features/s2-09-ready-pickup/readyEmailService.ts', { '../../core/api/apiClient': { apiClient: {
    get: async (...args) => { calls.push(args);return { data: {} } }, post: async (...args) => { calls.push(args);return { data: {} } },
  } } }).readyEmailService
  await api.previewCancellation(20,'2026-10-15T17:01:00+07:00');await api.runCancellation(20)
  assert.equal(calls[0][0],'/reservations/20/ready-email/cancellation-preview')
  assert.equal(calls[0][1].params.checkTime,'2026-10-15T17:01:00+07:00')
  assert.equal(calls[1][0],'/reservations/20/ready-email/cancellation-run')
})
test('reader cancelled rows render the reason, timestamp and rebooking link to existing flow', async () => {
  const f = hooksFixture(), Link = () => null
  const Component = load('../src/features/s2-08-my-reservations/MyReservationsPage.tsx', {
    react: f.hooks, 'react-router-dom': { Link },
    '../../core/auth/authStorage': { getCurrentUser: () => ({ role: 'READER' }) },
    '../s2-07-reservations/reservationService': { reservationService: { listMine: async () => [{ id: 20,bookId: 9,bookTitle: 'Mạng máy tính',
      status: 'CANCELLED',reservedAt: '2026-10-12T09:00:00+07:00',pickupDeadline: '2026-10-15T17:00:00+07:00',
      cancellationReason: 'Đã huỷ do quá hạn nhận',cancelledAt: '2026-10-15T17:01:00+07:00' }] } },
    './pickupLastDay': { isLastDayPickup: () => false },
    '../s1-02-user-management/accountService': imports['../s1-02-user-management/accountService'],
    '../../hooks/useTablePagination': { __esModule: true, default: items => ({ pageItems: items,startIndex: 0,page: 1,totalPages: 1,totalItems: items.length,pageSize: 10,goToPage() {} }) },
    ...Object.fromEntries(['Button','Card','EmptyState','LoadingState','PageHeader','StatusBadge','TableActionButton','TablePagination','FeedbackAlert'].map(name => [
      `../../components/ui/${name}`, { __esModule: true, default: Stub },
    ])),
  }, { window: { setInterval: () => 1,clearInterval() {},addEventListener() {},removeEventListener() {} },
    document: { visibilityState: 'visible',addEventListener() {},removeEventListener() {} } }).default
  f.render(() => Component());await settle();const tree=f.render(() => Component())
  assert.match(text(tree),/Đã huỷ do quá hạn nhận/);assert.match(text(tree),/Thời điểm hủy/)
  assert.ok(find(tree,n => n.type === Link && n.props.to === '/catalog/books/9#reserve-book' && text(n) === 'Đặt giữ lại'))
  assert.match(text(tree),/không giữ vị trí cũ/);f.unmount()
})
