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
    './readerPermissions': load('../src/features/s1-03-reader-registration/readerPermissions.ts', {}),
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

const permissions = load('../src/features/s1-03-reader-registration/readerPermissions.ts', {})
for (const role of ['LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN', 'READER', 'AUDITOR', 'UNKNOWN', undefined]) {
  test(`history route for ${role ?? 'anonymous'} checks role before mounting data page`, () => {
    let mounts = 0
    const History = () => { mounts++; return null }
    const Basic = () => null, List = () => null, Denied = () => null
    const feature = load('../src/features/s1-03-reader-registration/feature.tsx', {
      '../../core/auth/authStorage': { getCurrentUser: () => role ? { role } : null },
      './readerPermissions': permissions,
      './RegisterPage': { __esModule: true, default: () => null },
      './ReadersPage': { __esModule: true, default: List },
      './ReaderProfilePage': { __esModule: true, default: History },
      './ReaderBasicProfilePage': { __esModule: true, default: Basic },
      './ReaderHistoryDenied': { __esModule: true, default: Denied },
    }).default
    const route = feature.appRoutes.find(r => r.path === 'readers/:readerId')
    const result = route.element.type(route.element.props)
    const allowed = role === 'LIBRARIAN' || role === 'LIBRARY_MANAGER' || role === 'ADMIN'
    assert.equal(result.type, allowed ? History : Denied)
    result.type(result.props)
    assert.equal(mounts, allowed ? 1 : 0, 'denied role must not mount/request history')
    if (role === 'ADMIN') {
      const basicRoute = feature.appRoutes.find(r => r.path === 'readers/:readerId/profile')
      assert.equal(basicRoute.element.type(basicRoute.element.props).type, Basic)
      const listRoute = feature.appRoutes.find(r => r.path === 'readers')
      assert.equal(listRoute.element.type(listRoute.element.props).type, List)
      assert.ok(feature.navItems[0].roles.includes('ADMIN'))
    }
  })
}
for (const role of ['READER', 'UNKNOWN']) test(`denied ${role} notice expires at 3000ms, recovery stays`, () => {
  const p = page('../src/features/s1-03-reader-registration/ReaderHistoryDenied.tsx', {
    '../../core/auth/authStorage': { getCurrentUser: () => ({ role }) },
    'react-router-dom': { Link: () => null },
  })
  assert.match(p.notice().props.message, /không có quyền truy cập/)
  verifyTimer(p)
  assert.match(text(p.tree()), /Không thể mở lịch sử mượn trả/)
  assert.ok(find(p.tree(), n => n.props?.to === (role === 'ADMIN' ? '/readers' : '/dashboard')))
})
for (const role of ['ADMIN', 'LIBRARIAN', 'LIBRARY_MANAGER']) test(`reader list ${role}: history link obeys permission`, async () => {
  const p = page('../src/features/s1-03-reader-registration/ReadersPage.tsx', {
    '../../core/auth/authStorage': { getCurrentUser: () => ({ role }) },
    './readerPermissions': permissions,
    './readerService': { readerService: { getAllReaders: async () => [{ userId: 20, fullName: 'Nguyễn An',
      memberCode: 'BD000020', email: 'an@example.invalid', registrationStatus: 'APPROVED', userStatus: 'ACTIVE',
      submittedAt: '2026-10-01T09:00:00+07:00' }] } },
    'react-router-dom': { Link: () => null },
  })
  await settle(); p.render()
  assert.ok(find(p.tree(), n => n.props?.to === '/readers/20'))
})
test('ADMIN basic profile uses only basic API and exposes no history fields or actions', async () => {
  const calls = []
  const p = page('../src/features/s1-03-reader-registration/ReaderBasicProfilePage.tsx', {
    './readerService': { readerService: {
      getReaderById: async id => { calls.push(['basic', id]); return { userId: id, fullName: 'Nguyễn An',
        memberCode: 'BD000020', email: 'an@example.invalid', dateOfBirth: '2005-01-01',
        submittedAt: '2026-10-01T09:00:00+07:00', userStatus: 'ACTIVE', registrationStatus: 'APPROVED' } },
      getLoanHistory: () => { calls.push(['history']); throw new Error('Must never request history') },
    } },
    '../s3-01-loans/loanService': { formatLoanTimestamp: v => v },
    'react-router-dom': { Link: () => null },
  }, { id: 20 }, 'ReaderBasicProfile')
  await settle(); p.render()
  assert.deepEqual(calls, [['basic', 20]])
  assert.ok(find(p.tree(), n => n.props?.value === 'BD000020'))
  assert.doesNotMatch(text(p.tree()), /Phiếu đang mở|Tổng lượt đã mượn|Lượt từng trả trễ|Lọc lịch sử/)
  assert.equal(find(p.tree(), n => n.props?.to?.startsWith('/loans/')), undefined)
})
test('basic profile API failure notice expires without losing recovery state', async () => {
  const p = page('../src/features/s1-03-reader-registration/ReaderBasicProfilePage.tsx', {
    './readerService': { readerService: { getReaderById: async () => { throw new Error('Lỗi API hồ sơ') } } },
    '../s3-01-loans/loanService': { formatLoanTimestamp: v => v }, 'react-router-dom': { Link: () => null },
  }, { id: 20 }, 'ReaderBasicProfile')
  await settle(); p.render(); verifyTimer(p)
  assert.match(text(p.tree()), /Chưa tải được hồ sơ Bạn đọc/)
})
const CopyStub = () => null
const copyService = { copyError: e => ({ message: e.message }),
  physicalConditions: {}, todayInVietnam: () => '2026-10-09', validReceivedDate: () => true,
  bookCopyService: {
  getBook: async () => { throw new Error('Lỗi API tải dữ liệu') },
  getCopySummary: async () => { throw new Error('Lỗi API tải dữ liệu') },
  getCopy: async () => { throw new Error('Lỗi API tải dữ liệu') },
  getSummary: async () => { throw new Error('Lỗi API tải dữ liệu') },
  getWarehouses: async () => { throw new Error('Lỗi API tải dữ liệu') },
  getShelves: async () => [], history: async () => { throw new Error('Lỗi API tải dữ liệu') },
} }
const location = { state: null }
const oldImports = {
  '../s1-09-library-config/librarySettingsService': { librarySettingsService: { getWarehouses: async () => { throw new Error('Lỗi API tải dữ liệu') }, getShelves: async () => [] } },
  'react-router-dom': { useNavigate: () => () => {}, Link: CopyStub, useParams: () => ({ bookId: '1', copyId: '1' }), useLocation: () => location },
  '../../core/auth/authStorage': { getCurrentUser: () => ({ role: 'LIBRARIAN' }) },
  './bookCopyService': copyService,
  '../s1-08-catalog/catalogService': { catalogService: { getBook: async () => { throw new Error('Lỗi API tải dữ liệu') } } },
  '../s2-10-book-cover/BookCoverImage': { __esModule: true, default: CopyStub },
  '../s2-10-book-cover/BookCoverEditorDialog': { __esModule: true, default: CopyStub },
  './BulkCreateBookCopiesForm': { __esModule: true, default: CopyStub },
  './BulkCreateBookCopiesResult': { __esModule: true, default: CopyStub },
  './CreateBookCopyForm': { __esModule: true, default: CopyStub },
  './EditBookCopyForm': { __esModule: true, default: CopyStub },
  './RepairBookCopyPanel': { __esModule: true, default: CopyStub },
  './BookCopyStatusBadge': { __esModule: true, default: CopyStub },
}
for (const [file, props, recovery] of [
  ['BookDetailPage', {}, 'Chưa tải được đầu sách và bản sao.'],
  ['BookCopyDetailPage', {}, 'Chưa tải được thông tin bản sao.'],
  ['CreateBookCopyForm', { bookId: 1, bookTitle: 'Mắt biếc', onCreated() {} }, 'Chưa tải được kho và kệ.'],
  ['EditBookCopyForm', { copy: { id: 1, warehouseId: 1, shelfId: 1 }, onSaved() {}, onCancel() {} }, 'Chưa tải được kho và kệ.'],
  ['BulkCreateBookCopiesForm', { bookId: 1, bookTitle: 'Mắt biếc', onCreated() {} }, 'Chưa tải được kho và kệ.'],
  ['RepairBookCopyPanel', { copy: { id: 1, status: 'AVAILABLE' }, onSaved() {} }, 'Chưa tải được lịch sử thay đổi trạng thái.'],
]) test(`old ${file}: failure has 3000ms feedback and persistent retry`, async () => {
  const p = page(`../src/features/s2-02-book-copies/${file}.tsx`, oldImports, props, 'default', { window: { setInterval: () => 1, clearInterval() {}, addEventListener() {}, removeEventListener() {}, setTimeout: () => 1, clearTimeout() {} }, document: { visibilityState: 'visible', addEventListener() {}, removeEventListener() {} } })
  await settle(); p.render()
  verifyTimer(p)
  assert.ok(text(p.tree()).includes(recovery))
  assert.ok(find(p.tree(), n => n.props?.onClick && /Thử lại|Tải lại/.test(text(n))))
})
test('old LoanRejectionsPage: failure feedback expires and retry remains', async () => {
  const p = page('../src/features/s3-02-direct-loans/LoanRejectionsPage.tsx', {
    '../../core/auth/authStorage': { getCurrentUser: () => ({ role: 'LIBRARIAN' }) },
    '../s3-01-loans/loanService': { loanRoles: ['LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN'] },
    './loanRejectionService': { loanRejectionService: { page: async () => { throw new Error('Lỗi API nhật ký') } } },
  })
  await settle(); p.render(); verifyTimer(p)
  assert.match(text(p.tree()), /Chưa tải được nhật ký từ chối cho mượn/)
})
