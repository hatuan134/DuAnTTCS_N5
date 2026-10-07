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

function load(file, imports) {
  const source = readFileSync(new URL(file, root), 'utf8')
  const compiled = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX },
  }).outputText
  const context = { exports: {}, require: (name) => imports[name] ?? require(name) }
  vm.runInNewContext(compiled, context)
  return context.exports
}

function serviceFixture() {
  const requests = []
  const apiClient = {
    async post(url, body) { requests.push({ url, body }); return { data: { id: 81, barcode: 'LIB-031' } } },
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
    '/reservations/21/loan-context', '/reservations/ready-for-pickup/21',
  ].sort())
})

const reservation = {
  id: 21, bookId: 7, bookTitle: 'Mắt biếc', readerId: 12, readerName: 'Nguyễn Văn An',
  copyId: 31, barcode: 'LIB-031', cardNumber: 'TV-0012', converted: false,
}

function panelFixture({ card = '', api, overrides = {} } = {}) {
  const states = []
  let cursor = 0
  const hooks = {
    ...react,
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
  const service = api ?? {
    async createLoan(id, number) { calls.push([id, number]); return { id: 81, loanNumber: 'PM-NEW', readerName: 'Nguyễn Văn An', cardNumber: number, bookTitle: 'Mắt biếc', barcode: 'LIB-031', borrowedAt: '2026-10-07T10:00:00Z', message: 'Đã lập phiếu mượn thành công.' } },
    async loanContext() { return { converted: false } },
  }
  const panel = load('CreateReservationLoanPanel.tsx', {
    react: hooks,
    '../../components/ui/Button': { __esModule: true, default: (props) => react.createElement('button', { disabled: props.disabled || props.loading }, props.children) },
    '../../components/ui/Input': { __esModule: true, default: (props) => react.createElement('label', {}, props.label, react.createElement('input', { id: props.id, required: props.required, value: props.value, onChange: props.onChange, disabled: props.disabled })) },
    '../s1-02-user-management/accountService': { getApiErrorMessage: (error) => error.message },
    './pickupService': { pickupService: service, formatPickupDate: (v) => v },
  }).default
  function render() {
    cursor = 0
    return panel({ reservation: { ...reservation, ...overrides }, disabled: false,
      onBusyChange: (value) => busy.push(value), onSuccess: (value) => successes.push(value),
      onAlreadyConverted: () => { alreadyConverted++ },
    })
  }
  return { render, states, calls, busy, successes, get alreadyConverted() { return alreadyConverted } }
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
  for (const text of ['PM-NEW', 'Nguyễn Văn An', 'LIB-031', 'TV-0012']) assert.ok(html.includes(text))
})

test('two immediate submissions issue one request while saving', async () => {
  let resolve
  let calls = 0
  const promise = new Promise((done) => { resolve = done })
  const f = panelFixture({ card: 'TV-0012', api: {
    createLoan() { calls++; return promise }, loanContext() { return { converted: false } },
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
    async loanContext() { return { converted: false } },
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
