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
      useCallback(fn, deps) { const i = cursor++, old = slots[i]
        if (!old || deps.some((v, n) => !Object.is(v, old.deps[n]))) slots[i] = { deps, value: fn }
        return slots[i].value
      },
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
const returnHelpers = load('../src/features/s3-07-returns/returnService.ts', {
  '../../core/api/apiClient': { apiClient: {} },
})
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
function page(path, imports = {}, props = {}, exported = 'default', extra = {}) {
  const f = hooksFixture(); let tree
  const Stub = () => null
  const dependencies = {
    react: f.hooks,
    '../../components/ui/FeedbackAlert': { __esModule: true, default: FeedbackStub },
    '../../components/ui/Button': { __esModule: true, default: Stub },
    '../../components/ui/Card': { __esModule: true, default: Stub },
    '../../components/ui/EmptyState': { __esModule: true, default: Stub },
    '../../components/ui/Input': { __esModule: true, default: Stub },
    '../../components/ui/LoadingState': { __esModule: true, default: Stub },
    '../../components/ui/PageHeader': { __esModule: true, default: Stub },
    '../../components/ui/StatusBadge': { __esModule: true, default: Stub },
    '../../components/ui/TableActionButton': { __esModule: true, default: Stub, TableActions: Stub },
    '../../components/ui/TablePagination': { __esModule: true, default: Stub },
    '../../hooks/useTablePagination': { __esModule: true, default: items => ({ pageItems: items, startIndex: 0, page: 1, totalPages: 1, totalItems: items.length, pageSize: 10, goToPage() {} }) },
    '../s1-02-user-management/accountService': { getApiErrorMessage: e => e.message },
    './pickupService': { formatPickupDate: v => v, pickupService: {} }, ...imports,
  }
  const Component = load(path, dependencies, { window: { confirm: () => true }, ...extra })[exported]
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

for (const fail of [false, true]) test(`reader reservation page load ${fail ? 'failure' : 'success'} retains fixed state and 3000ms error notice`, async () => {
  const p = page('../src/features/s2-08-my-reservations/MyReservationsPage.tsx', {
    'react-router-dom': { Link: () => null },
    '../../core/auth/authStorage': { getCurrentUser: () => ({ role: 'READER' }) },
    '../s2-07-reservations/reservationService': { reservationService: { listMine: async () => { if (fail) throw new Error('Lỗi tải đơn.'); return [] } } },
  }, {}, 'default', { window: { addEventListener() {}, removeEventListener() {} }, document: { addEventListener() {}, removeEventListener() {} } })
  await settle(); p.render()
  if (fail) { assert.equal(p.notice().props.tone, 'error'); verifyTimer(p); assert.match(text(p.tree()), /Chưa tải được đơn đặt giữ/); assert.ok(find(p.tree(), n => n.props.action)?.props.action.props.onClick) }
  else { assert.equal(p.notice(), undefined); assert.ok(find(p.tree(), n => n.props.title === 'Bạn chưa có đơn đặt giữ nào')) }
})
test('public detail API error expires but fixed recovery content remains', async () => {
  const p = page('../src/features/s1-08-catalog/PublicBookDetailPage.tsx', {
    'react-router-dom': { Link: () => null, useParams: () => ({ bookId: '1' }) },
    '../s2-10-book-cover/PublicBookCover': { __esModule: true, default: () => null },
    '../s2-07-reservations/ReserveBookPanel': { __esModule: true, default: () => null },
    './PublicSiteFooter': { __esModule: true, default: () => null },
    './catalogService': { catalogService: { getPublicBookById: async () => { throw new Error('offline') } } },
  }, {}, 'default', { window: { setInterval: () => 1, clearInterval() {}, addEventListener() {}, removeEventListener() {} },
    document: { addEventListener() {}, removeEventListener() {} } })
  await settle(); p.render(); assert.equal(p.notice().props.tone, 'error'); verifyTimer(p)
  assert.match(text(p.tree()), /Không thể hiển thị đầu sách này/)
})
