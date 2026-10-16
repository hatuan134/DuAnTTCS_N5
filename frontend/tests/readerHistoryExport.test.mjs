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
  return predicate(node) ? node : (find(node.props?.children, predicate) ?? find(node.props?.action, predicate))
}
function text(node) {
  if (node == null || typeof node === 'boolean') return ''
  if (Array.isArray(node)) return node.map(text).join(' ')
  return typeof node === 'object' ? text(node.props?.children) : String(node)
}
function page(path, imports = {}, props = {}, exported = 'default', extra = {}) {
  const f = hooksFixture(); let tree
  const Stub = () => null
  const pagination = load('../src/hooks/useTablePagination.ts', { react: f.hooks }).default
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
    '../../hooks/useTablePagination': { __esModule: true, default: pagination },
    '../s1-02-user-management/accountService': { getApiErrorMessage: e => e.message },
    './pickupService': { formatPickupDate: v => v, pickupService: {} }, ...imports,
  }
  const Component = load(path, dependencies, { window: { confirm: () => true }, ...extra })[exported]
  const render = () => { tree = f.render(() => { const outer = Component(props); return exported === 'default' && typeof outer?.type === 'function' ? outer.type(outer.props) : outer }); return tree }; render()
  return { render, notice: () => find(tree, n => n.type === FeedbackStub),
    button: needle => find(tree, n => n.type === 'button' && n.props.onClick && text(n).includes(needle)), tree: () => tree, unmount: () => f.unmount() }
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
const serviceModule = load('../src/features/s1-03-reader-registration/readerService.ts', { '../../core/api/apiClient': { apiClient: {} } })
const timestampModule = load('../src/features/s3-01-loans/loanService.ts', { '../../core/api/apiClient': { apiClient: {} } })
const router = { Link: ({ children }) => children, useParams: () => ({ readerId: '20' }) }
const makeLoan = id => ({ id, loanNumber: `PM-${id}`, borrowedAt: `2026-10-${String(id).padStart(2, '0')}T09:00:00+07:00`,
  status: 'RETURNED', returnedLate: false, items: [] })
const full = [makeLoan(4), makeLoan(3), makeLoan(2), makeLoan(1)]
const response = loans => ({ profile: { userId: 20, fullName: 'Nguyễn An', memberCode: 'BD000020', email: 'an@example.invalid',
  dateOfBirth: '2005-01-01', submittedAt: '2026-10-01T09:00:00+07:00', userStatus: 'ACTIVE', registrationStatus: 'APPROVED' },
  openLoanCount: 2, totalBorrowCount: 4, lateReturnCount: 1, loans })

function fixture(exporter = async () => ({ rowCount: 12, filename: 'history.csv', blob: {} })) {
  const calls = [], downloads = [], props = { id: 20 }
  const p = page('../src/features/s1-03-reader-registration/ReaderProfilePage.tsx', {
    'react-router-dom': router,
    './readerService': { ...serviceModule, downloadReaderHistoryCsv: result => downloads.push(result), readerService: {
      getLoanHistory: async (id, filters) => ({ ...response(full), profile: { ...response(full).profile, userId: id } }),
      exportLoanHistory: (id, filters) => { calls.push({ id, ...filters }); return exporter() },
    } },
    '../s3-01-loans/loanService': timestampModule,
  }, props, 'ReaderProfile')
  return Object.assign(p, { calls, downloads, props,
    exportButton: () => find(p.tree(), n => n.props?.onClick && text(n).includes('Xuất CSV')),
    async export() { this.exportButton().props.onClick(); p.render(); await settle(); p.render() },
    edit(field, value) { find(p.tree(), n => n.props?.id === `reader-history-${field}-date`).props.onChange({ target: { value } }); p.render() },
    async apply() { find(p.tree(), n => n.type === 'form').props.onSubmit({ preventDefault() {} }); p.render(); await settle(); p.render() },
  })
}
async function ready(p) { await settle(); p.render() }

test('exports current reader without pagination and uses applied filters rather than drafts', async () => {
  const p = fixture(); await ready(p); await p.export()
  assert.deepEqual(p.calls[0], { id: 20, fromDate: '', toDate: '' })
  p.edit('from', '2026-10-02'); p.edit('to', '2026-10-09'); await p.apply()
  p.edit('from', '2026-10-05'); await p.export()
  assert.deepEqual(p.calls[1], { id: 20, fromDate: '2026-10-02', toDate: '2026-10-09' })
  assert.equal(p.downloads.length, 2); assert.equal(p.notice().props.tone, 'success'); verifyTimer(p)
})
test('empty result still downloads and info expires after 3000ms', async () => {
  const p = fixture(async () => ({ rowCount: 0, filename: 'empty.csv', blob: {} })); await ready(p); await p.export()
  assert.equal(p.downloads.length, 1); assert.equal(p.notice().props.tone, 'info')
  assert.match(p.notice().props.message, /chỉ có tiêu đề/); verifyTimer(p)
})
test('failed export never downloads; error expires and retry works', async () => {
  let fail = true
  const p = fixture(async () => { if (fail) throw new Error('Không có quyền xuất.'); return { rowCount: 1 } })
  await ready(p); await p.export(); assert.equal(p.downloads.length, 0)
  assert.equal(p.notice().props.tone, 'error'); verifyTimer(p)
  fail = false; await p.export(); assert.equal(p.downloads.length, 1)
})
test('double click produces one export; filter is disabled while downloading', async () => {
  let resolve
  const p = fixture(() => new Promise(done => { resolve = done })); await ready(p)
  const click = p.exportButton().props.onClick; click(); click(); p.render()
  assert.equal(p.calls.length, 1); assert.equal(p.exportButton().props.loading, true)
  assert.equal(find(p.tree(), n => n.props?.id === 'reader-history-from-date').props.disabled, true)
  resolve({ rowCount: 1 }); await settle(); p.render(); assert.equal(p.downloads.length, 1)
})
test('late response after changing reader or leaving page does not download another reader data', async () => {
  for (const unmount of [false, true]) {
    let resolve; const p = fixture(() => new Promise(done => { resolve = done })); await ready(p)
    p.exportButton().props.onClick(); p.render()
    if (unmount) p.unmount(); else { p.props.id = 21; p.render(); await ready(p) }
    resolve({ rowCount: 1 }); await settle(); assert.equal(p.downloads.length, 0)
  }
})
test('service sends blob request, trimmed date query, and server filename/count', async () => {
  const requests = [], blob = new Blob(['CSV'])
  const m = load('../src/features/s1-03-reader-registration/readerService.ts', {
    '../../core/api/apiClient': { apiClient: { get: async (...args) => { requests.push(args); return {
      data: blob, headers: { 'content-disposition': 'attachment; filename="lich-su_BD20.csv"', 'x-csv-row-count': '15' },
    } } } },
  }, { Blob })
  const result = await m.readerService.exportLoanHistory(20, { fromDate: ' 2026-10-01 ', toDate: '' })
  assert.equal(requests[0][0], '/readers/20/loan-history/export')
  assert.equal(requests[0][1].responseType, 'blob')
  assert.deepEqual(JSON.parse(JSON.stringify(requests[0][1].params)), { fromDate: '2026-10-01' })
  assert.equal(result.blob, blob); assert.equal(result.rowCount, 15); assert.equal(result.filename, 'lich-su_BD20.csv')
})
test('service decodes JSON errors received as Blob and rejects download', async () => {
  const failure = { response: { status: 403, data: new Blob(['{"message":"Không có quyền xuất."}']) } }
  const m = load('../src/features/s1-03-reader-registration/readerService.ts', {
    '../../core/api/apiClient': { apiClient: { get: async () => { throw failure } } },
  }, { Blob })
  await assert.rejects(m.readerService.exportLoanHistory(20, { fromDate: '', toDate: '' }), e => e === failure)
  assert.equal(failure.response.data.message, 'Không có quyền xuất.')
})
test('download attaches anchor, keeps server filename and releases object URL even if click fails', () => {
  for (const fail of [false, true]) {
    const events = [], anchor = { click() { events.push('click'); if (fail) throw new Error('blocked') }, remove() { events.push('remove') } }
    const m = load('../src/features/s1-03-reader-registration/readerService.ts', {
      '../../core/api/apiClient': { apiClient: {} },
    }, { URL: { createObjectURL: () => 'blob:test', revokeObjectURL: url => events.push(url) },
      document: { createElement: () => anchor, body: { appendChild: () => events.push('append') } } })
    const run = () => m.downloadReaderHistoryCsv({ filename: 'reader.csv', blob: {} })
    if (fail) assert.throws(run); else run()
    assert.equal(anchor.download, 'reader.csv'); assert.deepEqual(events, ['append', 'click', 'remove', 'blob:test'])
  }
})
