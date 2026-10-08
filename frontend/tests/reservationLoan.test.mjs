import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import vm from 'node:vm'
import ts from 'typescript'
import { createRequire } from 'node:module'
import { renderToStaticMarkup } from 'react-dom/server'

const require = createRequire(import.meta.url)
const root = new URL('../src/features/s2-09-ready-pickup/', import.meta.url)
const react = require('react')
let clockMs = 0
let timerCallbacks = []

function load(file, imports) {
  const source = readFileSync(new URL(file, root), 'utf8')
  const compiled = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX },
  }).outputText
  const context = { exports: {}, performance: { now: () => clockMs }, window: { setInterval: (callback) => { timerCallbacks.push(callback); return timerCallbacks.length }, clearInterval() {} }, require: (name) => imports[name] ?? (name === '../../components/ui/FeedbackAlert' ? load('../../components/ui/FeedbackAlert.tsx', {}) : require(name)) }
  vm.runInNewContext(compiled, context)
  return context.exports
}

function serviceFixture() {
  const requests = []
  const apiClient = {
    async post(url, body) {
      requests.push({ url, body })
      return { data: url.endsWith('pickup-check')
        ? { cardNumber: 'TV-0012', converted: true, loanNumber: 'PM-OLD', reservation: { id: 21, readerId: 12, barcode: 'LIB-031' } }
        : { id: 81, barcode: 'LIB-031' } }
    },
    async get(url) {
      requests.push({ url })
      return { data: url.endsWith('loan-context')
        ? { cardNumber: 'TV-0012', converted: true, loanNumber: 'PM-OLD' }
        : { id: 21, readerId: 12, barcode: 'LIB-031' } }
    },
  }
  return { ...load('pickupService.ts', { '../../core/api/apiClient': { apiClient } }), requests }
}

test('conversion posts only the confirmed card to the selected reservation', async () => {
  const f = serviceFixture()
  await f.pickupService.createLoan(21, '  TV-0012  ')
  assert.equal(f.requests[0].url, '/reservations/21/loan')
  assert.equal(f.requests[0].body.cardNumber, 'TV-0012')
  assert.deepEqual(Object.keys(f.requests[0].body), ['cardNumber'])
})

test('detail combines existing book/copy data with persisted card and conversion context', async () => {
  const f = serviceFixture()
  const row = await f.pickupService.detail(21)
  assert.equal(row.readerId, 12)
  assert.equal(row.barcode, 'LIB-031')
  assert.equal(row.cardNumber, 'TV-0012')
  assert.equal(row.converted, true)
  assert.equal(row.loanNumber, 'PM-OLD')
  assert.deepEqual(f.requests.map((r) => r.url).sort(), [
    '/reservations/21/pickup-check',
  ].sort())
})

const loanDates = {
  borrowDate: '2026-10-07', cardTypeName: 'Thẻ sinh viên', loanDays: 14,
  originalDueDate: '2026-10-21', dueDate: '2026-10-22', dueAt: '2026-10-22T17:00:00+07:00',
  adjusted: true, skippedClosedDates: ['2026-10-21'],
}

const reservation = {
  id: 21, bookId: 7, bookTitle: 'Mắt biếc', readerId: 12, readerName: 'Nguyễn Văn An',
  copyId: 31, barcode: 'LIB-031', cardNumber: 'TV-0012', converted: false, dates: loanDates, dateError: null,
}

function panelFixture({ card = '', api, overrides = {} } = {}) {
  clockMs = 0
  timerCallbacks = []
  const effects = []
  const states = []
  let cursor = 0
  const hooks = {
    ...react,
    useEffect(callback, dependencies) {
      const index = cursor++
      const previous = states[index]
      if (!previous || dependencies.some((value, i) => value !== previous[i])) effects.push(callback)
      states[index] = dependencies
    },
    useState(initial) {
      const index = cursor++
      if (!(index in states)) states[index] = index === 0 ? card : initial
      return [states[index], (value) => { states[index] = typeof value === 'function' ? value(states[index]) : value }]
    },
    useRef(initial) {
      const index = cursor++
      if (!(index in states)) states[index] = { current: initial }
      return states[index]
    },
  }
  const calls = []
  const busy = []
  const successes = []
  let alreadyConverted = 0
  const expiries = []
  const service = api ?? {
    async createLoan(id, number) { calls.push([id, number]); return { id: 81, loanNumber: 'PM-NEW', readerName: 'Nguyễn Văn An', cardNumber: number, bookTitle: 'Mắt biếc', barcode: 'LIB-031', borrowedAt: '2026-10-07T10:00:00Z', message: 'Đã lập phiếu mượn thành công.', dates: loanDates } },
    async loanContext() { return { converted: false, dates: loanDates, dateError: null } },
  }
  const panel = load('CreateReservationLoanPanel.tsx', {
    react: hooks,
    '../../components/ui/Button': { __esModule: true, default: (props) => react.createElement('button', { disabled: props.disabled || props.loading }, props.children) },
    '../../components/ui/StatusBadge': load('../../components/ui/StatusBadge.tsx', {}),
    '../../components/ui/Input': { __esModule: true, default: (props) => react.createElement('label', {}, props.label, react.createElement('input', { id: props.id, required: props.required, value: props.value, onChange: props.onChange, disabled: props.disabled })) },
    '../s1-02-user-management/accountService': { getApiErrorMessage: (error) => error.message },
    './pickupService': { ...serviceFixture(), pickupService: service, formatPickupDate: (v) => v, formatLoanDate: (v) => v },
  }).default
  function render() {
    cursor = 0
    return panel({ reservation: { ...reservation, ...overrides }, disabled: false,
      onBusyChange: (value) => busy.push(value), onSuccess: (value) => successes.push(value),
      onAlreadyConverted: () => { alreadyConverted++ },
      onExpired: (context) => expiries.push(context),
    })
  }
  return { render, states, calls, busy, successes, expiries, runEffects: () => { for (const effect of effects.splice(0)) effect() }, get alreadyConverted() { return alreadyConverted } }
}

function find(element, type) {
  if (!element || typeof element !== 'object') return null
  if (element.type === type) return element
  const children = element.props?.children
  for (const child of Array.isArray(children) ? children.flat(Infinity) : [children]) {
    const found = find(child, type)
    if (found) return found
  }
  return null
}
const settle = () => new Promise((resolve) => setImmediate(resolve))

test('blank card displays validation and sends no request', async () => {
  const f = panelFixture({ card: '   ' })
  find(f.render(), 'form').props.onSubmit({ preventDefault() {} })
  await settle()
  assert.equal(f.calls.length, 0)
  assert.match(renderToStaticMarkup(f.render()), /Vui lòng nhập mã thẻ hợp lệ/)
})

test('success renders matching reader and barcode and removes the conversion form', async () => {
  const f = panelFixture({ card: 'TV-0012' })
  find(f.render(), 'form').props.onSubmit({ preventDefault() {} })
  await settle()
  const tree = f.render()
  assert.equal(f.calls.length, 1)
  assert.equal(f.successes[0].barcode, 'LIB-031')
  assert.deepEqual(f.busy, [true, false])
  assert.equal(find(tree, 'form'), null)
  const html = renderToStaticMarkup(tree)
  for (const text of ['PM-NEW', 'Nguyễn Văn An', 'LIB-031', 'TV-0012', 'Đã chuyển thành phiếu mượn', 'Đang mượn']) assert.ok(html.includes(text))
})

test('two immediate submissions issue one request while saving', async () => {
  let resolve
  let calls = 0
  const promise = new Promise((done) => { resolve = done })
  const f = panelFixture({ card: 'TV-0012', api: {
    createLoan() { calls++; return promise }, loanContext() { return { converted: false, dates: loanDates, dateError: null } },
  } })
  const form = find(f.render(), 'form')
  form.props.onSubmit({ preventDefault() {} })
  form.props.onSubmit({ preventDefault() {} })
  assert.equal(calls, 1)
  resolve({ loanNumber: 'PM-ONE' })
  await settle()
})

test('API mismatch remains visible and allows correcting the card', async () => {
  const f = panelFixture({ card: 'WRONG', api: {
    async createLoan() { throw new Error('Mã thẻ không đúng với bạn đọc sở hữu đơn đặt giữ.') },
    async loanContext() { return { converted: false, dates: loanDates, dateError: null } },
  } })
  find(f.render(), 'form').props.onSubmit({ preventDefault() {} })
  await settle()
  assert.match(renderToStaticMarkup(f.render()), /Mã thẻ không đúng/)
  assert.ok(find(f.render(), 'form'))
  assert.equal(f.successes.length, 0)
  assert.deepEqual(f.busy, [true, false])
})

test('a lost response discovers persisted conversion and tells the parent to lock actions', async () => {
  const f = panelFixture({ card: 'TV-0012', api: {
    async createLoan() { throw new Error('Mất kết nối') },
    async loanContext() { return { converted: true, loanNumber: 'PM-SAVED' } },
  } })
  find(f.render(), 'form').props.onSubmit({ preventDefault() {} })
  await settle()
  assert.equal(f.alreadyConverted, 1)
})

test('reloaded converted order shows its loan number and offers no conversion form', () => {
  const f = panelFixture({ overrides: { converted: true, loanNumber: 'PM-OLD' } })
  const tree = f.render()
  assert.equal(find(tree, 'form'), null)
  assert.match(renderToStaticMarkup(tree), /PM-OLD/)
})


test('client sends preview expectations without sending an editable due date for persistence', async () => {
  const f = serviceFixture()
  await f.pickupService.createLoan(21, 'TV-0012', loanDates)
  assert.equal(f.requests[0].body.expectedBorrowDate, '2026-10-07')
  assert.equal(f.requests[0].body.expectedDueAt, '2026-10-22T17:00:00+07:00')
  assert.equal(f.requests[0].body.expectedLoanDays, 14)
  assert.equal('due_date' in f.requests[0].body, false)
  assert.equal('readerId' in f.requests[0].body, false)
})

test('preview shows calendar-day policy and adjusted due date before confirming', () => {
  const f = panelFixture()
  const html = renderToStaticMarkup(f.render())
  for (const text of ['Thẻ sinh viên', '14 ngày', '2026-10-07', '2026-10-21', '2026-10-22T17:00:00+07:00', 'ngày mở cửa kế tiếp']) {
    assert.ok(html.includes(text), text)
  }
})

test('missing configuration locks confirmation and displays the server error', async () => {
  const f = panelFixture({ card: 'TV-0012', overrides: { dates: null, dateError: 'Lịch làm việc chưa được cấu hình đủ 7 ngày.' } })
  const tree = f.render()
  assert.match(renderToStaticMarkup(tree), /chưa được cấu hình đủ 7 ngày/)
  find(tree, 'form').props.onSubmit({ preventDefault() {} })
  await settle()
  assert.equal(f.calls.length, 0)
})

test('changed preview refreshes dates and requires another confirmation before creating', async () => {
  let calls = 0
  const seen = []
  const revised = { ...loanDates, dueDate: '2026-10-26', dueAt: '2026-10-26T17:00:00+07:00' }
  const f = panelFixture({ card: 'TV-0012', api: {
    async createLoan(id, card, dates) {
      calls++; seen.push(dates.dueAt)
      if (calls === 1) throw new Error('Ngày mượn hoặc hạn trả đã thay đổi. Vui lòng xác nhận lại.')
      return { loanNumber: 'PM-NEW', dates: revised }
    },
    async loanContext() { return { converted: false, dates: revised, dateError: null } },
  } })
  find(f.render(), 'form').props.onSubmit({ preventDefault() {} })
  await settle()
  assert.equal(calls, 1)
  assert.equal(f.successes.length, 0)
  assert.match(renderToStaticMarkup(f.render()), /2026-10-26T17:00:00\+07:00/)
  find(f.render(), 'form').props.onSubmit({ preventDefault() {} })
  await settle()
  assert.equal(calls, 2)
  assert.deepEqual(seen, [loanDates.dueAt, revised.dueAt])
  assert.equal(f.successes.length, 1)
})

test('date-only formatting keeps the server Vietnam date without timezone parsing', () => {
  const f = serviceFixture()
  assert.equal(f.formatLoanDate('2026-10-07'), '07/10/2026')
})

const pickupBoundary = {
  status: 'READY_FOR_PICKUP', pickupDeadline: '2026-10-07T17:00:00+07:00', checkedAt: '2026-10-07T16:59:59+07:00',
}

test('server time plus elapsed duration distinguishes before, exactly at and after the deadline', () => {
  const { isPickupExpired } = serviceFixture()
  assert.equal(isPickupExpired(pickupBoundary, 999), false)
  assert.equal(isPickupExpired(pickupBoundary, 1000), false)
  assert.equal(isPickupExpired(pickupBoundary, 1001), true)
  assert.equal(isPickupExpired({ ...pickupBoundary, checkedAt: '2026-10-07T10:00:00Z' }), false)
  assert.equal(isPickupExpired({ ...pickupBoundary, checkedAt: '2026-10-07T10:00:00.001Z' }), true)
})

test('explicit expired status blocks action and fulfilled status takes precedence over an old deadline', () => {
  const { isPickupExpired } = serviceFixture()
  assert.equal(isPickupExpired({ ...pickupBoundary, status: 'EXPIRED' }), true)
  assert.equal(isPickupExpired({ ...pickupBoundary, expired: true }), true)
  assert.equal(isPickupExpired({ ...pickupBoundary, status: 'FULFILLED' }, 99999), false)
  assert.equal(isPickupExpired({ ...pickupBoundary, pickupDeadline: null }), false)
})

test('opening an expired order disables confirmation and asks the reader to reserve again', () => {
  const f = panelFixture({ overrides: { ...pickupBoundary, status: 'EXPIRED', expired: true, copyStatus: 'AVAILABLE' } })
  const tree = f.render()
  assert.equal(find(tree, 'form'), null)
  const html = renderToStaticMarkup(tree)
  assert.match(html, /quá hạn nhận/)
  assert.match(html, /đặt giữ lại/)
  assert.match(html, /Sẵn sàng/)
  assert.match(html, /disabled=""[^>]*>Xác nhận và lập phiếu mượn/)
  assert.equal(f.calls.length, 0)
})

test('clicking an old form after deadline sends no loan request', async () => {
  const f = panelFixture({ card: 'TV-0012', overrides: pickupBoundary })
  const form = find(f.render(), 'form')
  clockMs = 1001
  form.props.onSubmit({ preventDefault() {} })
  await settle()
  assert.equal(f.calls.length, 0)
  assert.equal(find(f.render(), 'form'), null)
  assert.match(renderToStaticMarkup(f.render()), /quá hạn nhận/)
})

test('a backend expiry conflict refreshes persisted state and locks further conversion', async () => {
  let calls = 0
  const f = panelFixture({ card: 'TV-0012', overrides: pickupBoundary, api: {
    async createLoan() { calls++; throw new Error('Đơn đặt giữ đã quá hạn nhận.') },
    async loanContext() { return { status: 'EXPIRED', expired: true, copyStatus: 'AVAILABLE', pickupMessage: 'Vui lòng đặt giữ lại.' } },
  } })
  find(f.render(), 'form').props.onSubmit({ preventDefault() {} })
  await settle()
  assert.equal(calls, 1)
  assert.equal(f.expiries.length, 1)
  assert.equal(f.expiries[0].copyStatus, 'AVAILABLE')
  assert.equal(find(f.render(), 'form'), null)
  assert.equal(f.successes.length, 0)
})

test('lost conversion response renders recovered loan number in the panel itself', async () => {
  const f = panelFixture({ card: 'TV-0012', api: {
    async createLoan() { throw new Error('Mất kết nối') },
    async loanContext() { return { converted: true, status: 'FULFILLED', loanNumber: 'PM-SAVED' } },
  } })
  find(f.render(), 'form').props.onSubmit({ preventDefault() {} })
  await settle()
  assert.equal(find(f.render(), 'form'), null)
  assert.match(renderToStaticMarkup(f.render()), /PM-SAVED/)
})


test('an open confirmation view persists expiry when server time plus elapsed time passes the deadline', async () => {
  let checks = 0
  const f = panelFixture({ overrides: pickupBoundary, api: {
    async createLoan() { throw new Error('Must not create a loan') },
    async loanContext() { checks++; return { ...pickupBoundary, expired: true, status: 'EXPIRED', copyStatus: 'AVAILABLE' } },
  } })
  f.render(); f.runEffects()
  assert.equal(checks, 0)
  clockMs = 1001
  timerCallbacks[0]()
  f.render(); f.runEffects()
  await settle()
  assert.equal(checks, 1)
  assert.equal(f.expiries.length, 1)
  assert.equal(find(f.render(), 'form'), null)
})

test('a failed automatic expiry check keeps confirmation locked and waits for manual retry', async () => {
  let checks = 0
  const f = panelFixture({ overrides: pickupBoundary, api: {
    async createLoan() { throw new Error('Must not create a loan') },
    async loanContext() { checks++; throw new Error('Mất kết nối') },
  } })
  f.render(); f.runEffects()
  clockMs = 1001; timerCallbacks[0]()
  f.render(); f.runEffects(); await settle()
  f.render(); f.runEffects(); await settle()
  f.render(); f.runEffects(); await settle()
  assert.equal(checks, 1)
  assert.equal(find(f.render(), 'form'), null)
  assert.match(renderToStaticMarkup(f.render()), /Kiểm tra lại trạng thái/)
})
