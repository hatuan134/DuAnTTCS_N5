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

for (const fail of [false, true]) test(`old ADMIN locks account: ${fail ? 'failure' : 'success'} expires at 3000ms`, async () => {
  const account = { id: 2, fullName: 'Thủ thư mẫu', email: 'staff@example.invalid', role: 'LIBRARIAN', status: 'ACTIVE' }
  const p = page('../src/features/s1-02-user-management/UserManagementPage.tsx', {
    '../../core/auth/authStorage': { getCurrentUser: () => ({ id: 1, role: 'ADMIN' }) },
    './accountService': { getAccounts: async () => [account], getApiErrorMessage: e => e.message,
      updateAccountStatus: async () => { if (fail) throw new Error('Không khóa được tài khoản.'); return { ...account, status: 'LOCKED' } },
    },
  })
  await settle(); p.render()
  find(p.tree(), n => n.props?.onClick && text(n).trim() === 'Khóa').props.onClick()
  await settle(); p.render(); assert.equal(p.notice().props.tone, fail ? 'error' : 'success'); verifyTimer(p)
})
for (const fail of [false, true]) test(`old LIBRARY_MANAGER toggles policy: ${fail ? 'failure' : 'success'} expires at 3000ms`, async () => {
  const card = { id: 2, name: 'Sinh viên', active: true, duration: 12, maxBooks: 5, loanDays: 14, maxRenewals: 2, renewalDays: 7 }
  const p = page('../src/features/s1-05-borrow-policy/CardTypesPage.tsx', {
    '../../core/auth/authStorage': { getCurrentUser: () => ({ id: 1, role: 'LIBRARY_MANAGER' }) },
    axios: { __esModule: true, default: { isAxiosError: () => true } },
    './cardTypeService': { cardTypeService: { getCardTypes: async () => [card], getHistory: async () => [],
      toggleStatus: async () => { if (fail) throw { response: { data: { message: 'Không đổi được chính sách.' } } }; return { ...card, active: false } },
    } },
  })
  await settle(); p.render()
  await find(p.tree(), n => n.props?.onClick && text(n).trim() === 'Ngừng áp dụng').props.onClick()
  p.render(); assert.equal(p.notice().props.tone, fail ? 'error' : 'success'); verifyTimer(p)
})
test('old librarian failed barcode preview now notifies for 3000ms and retains row diagnostics', async () => {
  const p = page('../src/features/s3-02-direct-loans/DirectLoanItemsPanel.tsx', {
    '../s3-01-loans/loanService': { formatLoanTimestamp: v => v },
    './directLoanService': { directLoanService: { previewItem: async () => { throw new Error('Mã vạch không tồn tại.') } } },
  }, { reader: { eligible: true, remainingBooks: 5, cardNumber: 'TV-1', blockReasons: [] } })
  find(p.tree(), n => n.props?.id === 'direct-loan-barcode').props.onChange({ target: { value: 'INVALID-1' } })
  p.render(); await find(p.tree(), n => n.type === 'form').props.onSubmit({ preventDefault() {} })
  p.render(); assert.equal(p.notice().props.tone, 'error'); verifyTimer(p)
  assert.ok(text(p.tree()).includes('Mã vạch không tồn tại.'))
  assert.ok(find(p.tree(), n => n.props?.['aria-label'] === 'Sửa mã vạch INVALID-1'))
})

for (const initialFailure of [true, false]) test(`old public catalog ${initialFailure ? 'search' : 'load-more'} API error expires but recovery state stays`, async () => {
  let calls = 0
  const p = page('../src/features/s1-08-catalog/PublicCatalogPage.tsx', {
    'axios': { isAxiosError: () => true },
    'react-router-dom': { Link: () => null },
    '../s2-10-book-cover/PublicBookCover': { __esModule: true, default: () => null },
    './PublicSiteFooter': { __esModule: true, default: () => null },
    './catalogService': { catalogService: {
      getPublicFilterOptions: async () => ({ categories: [], publicationYears: [] }),
      searchPublicBooks: async () => {
        if (initialFailure || calls++) throw { response: { data: { message: 'Không tải được kết quả.' } } }
        return { content: [{ id: 1, title: 'Sách mẫu', authorName: 'Tác giả', categoryName: 'Văn học', availableCount: 1 }],
          page: 0, last: false, totalElements: 2, totalPages: 2 }
      },
    } },
  }, {}, 'default', { AbortController, window: { setInterval: () => 1, clearInterval() {}, addEventListener() {}, removeEventListener() {} },
    document: { hidden: false, addEventListener() {}, removeEventListener() {} },
  })
  await settle(); p.render()
  if (!initialFailure) { p.button('Hiển thị thêm').props.onClick(); await settle(); p.render() }
  assert.equal(p.notice().props.tone, 'error'); verifyTimer(p)
  assert.ok(text(p.tree()).includes('Bấm Làm mới để thử lại.'))
  assert.ok(!text(p.tree()).includes('Không tìm thấy đầu sách phù hợp.'))
  assert.equal(p.notice(), undefined)
})

test('return validation warning expires at 3000ms while the field error stays', async () => {
  const p = page('../src/features/s3-07-returns/ReceiveReturnPage.tsx', {
    '../../core/auth/authStorage': { getCurrentUser: () => ({ role: 'LIBRARIAN' }) },
    '../s3-01-loans/loanService': { loanRoles: ['LIBRARIAN'], formatLoanTimestamp: v => v },
    './returnService': { ...returnHelpers, validateReturnBarcode: () => 'Vui lòng nhập mã vạch từ 1 đến 100 ký tự.', returnService: {} },
  }, {}, 'default', { AbortController })
  find(p.tree(), n => n.type === 'form').props.onSubmit({ preventDefault() {} }); p.render()
  assert.equal(p.notice().props.tone, 'warning'); verifyTimer(p)
  assert.equal(find(p.tree(), n => n.props?.id === 'return-barcode').props.error,
    'Vui lòng nhập mã vạch từ 1 đến 100 ký tự.')
})

test('return queue success expires at 3000ms while saved hold details stay', async () => {
  const returned = { message: 'Bản sao được giữ cho Bạn đọc Bình.', copyStatus: 'HELD',
    copyId: 4, barcode: 'LIB-001', bookTitle: 'Mắt biếc', itemId: 9, loanNumber: 'PM-008',
    returnedAt: '2026-10-09T01:00:00+07:00', returnedByName: 'Thủ thư An', loanStatus: 'RETURNED',
    nextReservationId: 30, nextReaderName: 'Bạn đọc Bình', holdStartedAt: '2026-10-09T01:00:00+07:00',
    pickupDeadline: '2026-10-13T17:00:00+07:00', loanId: 8 }
  const p = page('../src/features/s3-07-returns/ReceiveReturnPage.tsx', {
    '../../core/auth/authStorage': { getCurrentUser: () => ({ role: 'LIBRARIAN' }) },
    '../s3-01-loans/loanService': { loanRoles: ['LIBRARIAN'], formatLoanTimestamp: v => v },
    './returnService': { ...returnHelpers, validateReturnBarcode: () => '', returnService: {
      lookup: async () => ({ status: 'ON_TIME', bookTitle: 'Mắt biếc', barcode: 'LIB-001', itemId: 9, loanNumber: 'PM-008' }),
      confirm: async () => returned,
    } },
  }, {}, 'default', { AbortController })
  find(p.tree(), n => n.props?.id === 'return-barcode').props.onChange({ target: { value: 'LIB-001' } }); p.render()
  find(p.tree(), n => n.type === 'form').props.onSubmit({ preventDefault() {} }); await settle(); p.render()
  find(p.tree(), n => n.props?.children === 'Xác nhận nhận trả').props.onClick(); await settle(); p.render()
  assert.equal(p.notice().props.tone, 'success'); verifyTimer(p)
  assert.equal(find(p.tree(), n => n.props?.label === 'Bạn đọc được giữ sách').props.children, returned.nextReaderName)
  assert.equal(find(p.tree(), n => n.props?.label === 'Hạn cuối đến nhận').props.children, returned.pickupDeadline)
})

for (const scenario of ['success', 'lookup-error', 'duplicate', 'confirmation-error']) {
  test(`S3-07.4 ${scenario}: 3000ms notice dismissal preserves previous session results`, async () => {
    const lookups = [], writes = []
    const p = page('../src/features/s3-07-returns/ReceiveReturnPage.tsx', {
      '../../core/auth/authStorage': { getCurrentUser: () => ({ role: 'LIBRARIAN' }) },
      '../s3-01-loans/loanService': { loanRoles: ['LIBRARIAN'], formatLoanTimestamp: v => v },
      './returnService': { ...returnHelpers, returnService: {
        lookup: async code => {
          lookups.push(code)
          if (code === 'BAD') throw new Error('Mã vạch không tồn tại.')
          return { status: 'ON_TIME', bookTitle: `Sách ${code}`, barcode: code,
            copyId: code === 'A' ? 1 : 2, itemId: code === 'A' ? 11 : 12,
            loanNumber: 'PM-008', readerName: 'Bạn đọc An', dueAt: '2026-10-09T17:00:00+07:00' }
        },
        confirm: async (code, itemId) => {
          writes.push(code)
          if (code === 'B' && scenario === 'confirmation-error') {
            const error = new Error('Chưa ghi nhận trả. Dữ liệu giữ nguyên.')
            error.response = { status: 500 }; throw error
          }
          return { message: `Nhận trả ${code} thành công.`, copyStatus: 'AVAILABLE',
            copyId: code === 'A' ? 1 : 2, itemId, barcode: code, bookTitle: `Sách ${code}`,
            returnedAt: '2026-10-09T12:00:00+07:00', returnedByName: 'Thủ thư An',
            loanStatus: 'RETURNED', loanNumber: 'PM-008', loanId: 8 }
        },
      } },
    }, {}, 'default', { AbortController })
    async function lookupCode(code) {
      find(p.tree(), n => n.props?.id === 'return-barcode').props.onChange({ target: { value: code } }); p.render()
      find(p.tree(), n => n.type === 'form').props.onSubmit({ preventDefault() {} }); await settle(); p.render()
    }
    async function confirmCode() {
      find(p.tree(), n => n.props?.children === 'Xác nhận nhận trả').props.onClick(); await settle(); p.render()
    }
    await lookupCode('A'); await confirmCode()
    await lookupCode(scenario === 'duplicate' ? 'A' : scenario === 'lookup-error' ? 'BAD' : 'B')
    if (scenario === 'success' || scenario === 'confirmation-error') await confirmCode()
    assert.equal(p.notice().props.tone, scenario === 'success' ? 'success' : scenario === 'duplicate' ? 'warning' : 'error')
    verifyTimer(p)
    const first = find(p.tree(), n => n.props?.['data-return-result'] === 'SUCCESS')
    assert.ok(first); assert.ok(text(first).includes('Sách A'))
    assert.equal(find(p.tree(), n => n.props?.id === 'return-barcode').props.disabled, false)
    if (scenario === 'duplicate') { assert.equal(lookups.length, 1); assert.equal(writes.length, 1) }
  })
}
