import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import vm from 'node:vm'
import ts from 'typescript'
import { createRequire } from 'node:module'
import { renderToStaticMarkup } from 'react-dom/server'

const require = createRequire(import.meta.url)
const react = require('react')
const root = new URL('../src/features/s2-09-ready-pickup/', import.meta.url)

function load(file, imports = {}) {
  const source = readFileSync(new URL(file, root), 'utf8')
  const compiled = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX },
  }).outputText
  const context = { exports: {}, require: (name) => imports[name] ?? require(name) }
  vm.runInNewContext(compiled, context)
  return context.exports
}

const serviceModule = load('pickupService.ts', { '../../core/api/apiClient': { apiClient: {} } })
const row = {
  id: 21, bookId: 7, bookTitle: 'Mắt biếc', copyId: 31, barcode: 'LIB-031',
  readerId: 12, readerName: 'Nguyễn Văn An', status: 'READY_FOR_PICKUP',
  reservedAt: '2026-10-07T08:00:00+07:00', pickupDeadline: '2026-10-10T17:00:00+07:00',
  cardNumber: 'TV-0012', converted: false,
}

function find(element, predicate) {
  if (!element || typeof element !== 'object') return null
  if (predicate(element)) return element
  const children = element.props?.children
  for (const child of Array.isArray(children) ? children.flat(Infinity) : [children]) {
    const match = find(child, predicate)
    if (match) return match
  }
  return null
}

async function detailFixture({ error, role = 'LIBRARIAN', overrides = {} } = {}) {
  const states = []
  const effects = []
  let cursor = 0
  let effectStarted = false
  const hooks = {
    ...react,
    useState(initial) {
      const index = cursor++
      if (!(index in states)) states[index] = initial
      return [states[index], (value) => {
        states[index] = typeof value === 'function' ? value(states[index]) : value
      }]
    },
    useEffect(callback) {
      if (!effectStarted) effects.push(callback)
    },
  }
  function LoanPanel() { return react.createElement('section', {}, 'Lập phiếu') }
  const page = load('ReadyPickupDetailPage.tsx', {
    react: hooks,
    'react-router-dom': {
      useParams: () => ({ reservationId: '21' }),
      Link: ({ to, children, ...props }) => react.createElement('a', { ...props, href: to }, children),
    },
    '../../components/ui/Button': { __esModule: true, default: ({ children, ...props }) => react.createElement('button', props, children) },
    '../../components/ui/Card': { __esModule: true, default: ({ children, ...props }) => react.createElement('div', props, children) },
    '../../components/ui/LoadingState': { __esModule: true, default: () => react.createElement('p', {}, 'Đang tải') },
    '../../components/ui/PageHeader': { __esModule: true, default: ({ title }) => react.createElement('h2', {}, title) },
    '../../components/ui/StatusBadge': load('../../components/ui/StatusBadge.tsx'),
    '../../core/auth/authStorage': { getCurrentUser: () => ({ role }) },
    '../s1-02-user-management/accountService': { getApiErrorMessage: (e) => e.message },
    './CreateReservationLoanPanel': { __esModule: true, default: LoanPanel },
    './CancelReservationPanel': { __esModule: true, default: () => null, CancellationNotice: () => null },
    './pickupService': { ...serviceModule, pickupService: {
      async detail() { if (error) throw error; return { ...row, ...overrides } },
    } },
  }).default
  function render() {
    cursor = 0
    const outer = page()
    return typeof outer.type === 'function' ? outer.type(outer.props) : outer
  }
  render()
  for (const effect of effects) effect()
  effectStarted = true
  await new Promise((resolve) => setImmediate(resolve))
  return { render, loan: () => find(render(), (element) => element.type === LoanPanel) }
}

test('successful confirmation immediately replaces the pickup badge and offers history and copy links', async () => {
  const f = await detailFixture()
  const before = renderToStaticMarkup(f.render())
  assert.match(before, /Đang chờ nhận/)
  assert.match(before, /Huỷ đơn/)
  f.loan().props.onSuccess({ loanNumber: 'PM-NEW' })
  const after = renderToStaticMarkup(f.render())
  assert.match(after, /Đã chuyển thành phiếu mượn/)
  assert.doesNotMatch(after, /Đang chờ nhận/)
  assert.doesNotMatch(after, /Huỷ đơn/)
  assert.match(after, /Đơn đã được loại khỏi danh sách Chờ nhận/)
  assert.match(after, /href="\/books\/7\/reservations"/)
  assert.match(after, /href="\/book-copies\/31"/)
})

test('a saved conversion recovered after network failure also locks cancellation and changes the badge', async () => {
  const f = await detailFixture()
  f.loan().props.onAlreadyConverted()
  const html = renderToStaticMarkup(f.render())
  assert.match(html, /Đã chuyển thành phiếu mượn/)
  assert.doesNotMatch(html, /Huỷ đơn/)
})

test('busy confirmation disables cancellation without prematurely changing pickup status', async () => {
  const f = await detailFixture()
  f.loan().props.onBusyChange(true)
  const html = renderToStaticMarkup(f.render())
  assert.match(html, /Đang chờ nhận/)
  assert.match(html, /disabled=""[^>]*aria-label="Huỷ đơn #21"/)
  f.loan().props.onBusyChange(false)
  const restored = renderToStaticMarkup(f.render())
  assert.match(restored, /Đang chờ nhận/)
  assert.doesNotMatch(restored, /disabled=""/)
})

test('detail load error displays the error and retry without inventing a conversion', async () => {
  const f = await detailFixture({ error: new Error('Không tải được đơn.') })
  const html = renderToStaticMarkup(f.render())
  assert.match(html, /Không tải được đơn/)
  assert.match(html, /Thử lại/)
  assert.doesNotMatch(html, /Đã chuyển thành phiếu mượn/)
  assert.equal(f.loan(), null)
})

test('readers cannot open the staff confirmation view', async () => {
  const f = await detailFixture({ role: 'READER' })
  assert.match(renderToStaticMarkup(f.render()), /Bạn không có quyền/)
  assert.equal(f.loan(), null)
})

test('existing history labels and cancellation rules recognize fulfilled reservations', () => {
  assert.equal(serviceModule.reservationStatusLabel('FULFILLED'), 'Đã chuyển thành phiếu mượn')
  assert.equal(serviceModule.canCancelReservation('FULFILLED'), false)
  assert.ok(serviceModule.reservationFilterStatuses.includes('FULFILLED'))
})


test('persisted expiry updates status, available copy and history immediately', async () => {
  const f = await detailFixture()
  f.loan().props.onExpired({ status: 'EXPIRED', expired: true, copyStatus: 'AVAILABLE' })
  const html = renderToStaticMarkup(f.render())
  assert.match(html, /Hết hạn nhận/)
  assert.match(html, /Sẵn sàng/)
  assert.doesNotMatch(html, /Huỷ đơn/)
  assert.match(html, /href="\/books\/7\/reservations"/)
  assert.match(html, /Đơn hết hạn không còn trong danh sách Chờ nhận/)
})

test('reloading an expired order shows its summary and keeps cancellation hidden', async () => {
  const f = await detailFixture({ overrides: { status: 'EXPIRED', expired: true, copyStatus: 'AVAILABLE' } })
  const html = renderToStaticMarkup(f.render())
  assert.match(html, /Hết hạn nhận/)
  assert.match(html, /LIB-031/)
  assert.doesNotMatch(html, /Huỷ đơn/)
  assert.equal(f.loan().props.reservation.expired, true)
  assert.ok(serviceModule.reservationFilterStatuses.includes('EXPIRED'))
  assert.equal(serviceModule.canCancelReservation('EXPIRED'), false)
})
