import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import vm from 'node:vm'
import ts from 'typescript'
import { createRequire } from 'node:module'
import { renderToStaticMarkup } from 'react-dom/server'

const require = createRequire(import.meta.url)
const react = require('react')
const root = new URL('../src/features/s3-01-loans/', import.meta.url)

function load(file, imports = {}) {
  const compiled = ts.transpileModule(readFileSync(new URL(file, root), 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX },
  }).outputText
  const ctx = { exports: {}, require: name => imports[name] ?? require(name) }
  vm.runInNewContext(compiled, ctx)
  return ctx.exports
}

const items = [
  { id: 10, loanNumber: 'PM-0010', cardNumber: 'CARD-01', readerName: 'Nguyễn An',
    borrowedAt: '2026-10-01T08:00:00+07:00', status: 'PARTIALLY_RETURNED', items: [
      { barcode: 'COPY-01', bookTitle: 'Mắt biếc', dueAt: '2026-10-15T08:00:00+07:00', status: 'BORROWED' },
      { barcode: 'COPY-02', bookTitle: 'Dế Mèn', dueAt: null, status: 'RETURNED' },
    ] },
  { id: 11, loanNumber: 'PM-0011', cardNumber: 'CARD-01', readerName: 'Nguyễn An',
    borrowedAt: '2026-09-01T08:00:00+07:00', status: 'RETURNED', items: [
      { barcode: 'COPY-01', bookTitle: 'Mắt biếc', dueAt: '2026-09-15T08:00:00+07:00', status: 'RETURNED' },
    ] },
]
function find(tree, predicate) {
  if (tree === null || typeof tree !== 'object') return null
  if (predicate(tree)) return tree
  for (const child of react.Children.toArray(tree.props?.children)) {
    const result = find(child, predicate)
    if (result) return result
  }
  return null
}
function InputStub(props) { return react.createElement('input', { ...props, label: undefined }) }
function ButtonStub({ children, loading, ...props }) {
  return react.createElement('button', { ...props, disabled: props.disabled || loading }, children)
}
function CardStub({ children, className }) { return react.createElement('div', { className }, children) }
function AlertStub({ message, tone }) { return react.createElement('p', { 'data-tone': tone }, message) }
function PaginationStub() { return null }

function pageFixture({ role = 'LIBRARIAN', reject = false, initialTotal = 2 } = {}) {
  const hooks = { ...react }
  const slots = [], effects = [], calls = []
  let cursor = 0
  hooks.useState = initial => {
    const index = cursor++
    if (!(index in slots)) slots[index] = { value: initial }
    return [slots[index].value, value => {
      slots[index].value = typeof value === 'function' ? value(slots[index].value) : value
    }]
  }
  hooks.useRef = initial => slots[cursor++] ??= { current: initial }
  hooks.useEffect = (fn, deps) => {
    const index = cursor++, old = slots[index]
    if (!old || deps.some((v, i) => !Object.is(v, old.deps[i]))) {
      slots[index] = { deps, cleanup: old?.cleanup }
      effects.push(() => { old?.cleanup?.(); slots[index].cleanup = fn() })
    }
  }
  const serviceModule = load('loanService.ts', { '../../core/api/apiClient': { apiClient: {} } })
  const Page = load('LoanSearchPage.tsx', {
    react: hooks,
    'react-router-dom': { Link: ({ children, to }) => react.createElement('a', { href: to }, children) },
    '../../components/ui/Button': { __esModule: true, default: ButtonStub },
    '../../components/ui/Card': { __esModule: true, default: CardStub },
    '../../components/ui/FeedbackAlert': { __esModule: true, default: AlertStub },
    '../../components/ui/Input': { __esModule: true, default: InputStub },
    '../../components/ui/TablePagination': { __esModule: true, default: PaginationStub },
    '../../components/ui/LoadingState': { __esModule: true, default: () => react.createElement('p', {}, 'Đang tìm') },
    '../../components/ui/PageHeader': { __esModule: true, default: ({ title }) => react.createElement('h1', {}, title) },
    '../../core/auth/authStorage': { getCurrentUser: () => ({ role }) },
    '../s1-02-user-management/accountService': { getApiErrorMessage: e => e.message },
    './loanService': { ...serviceModule, loanService: { search: async (code, page = 0, filters) => {
      calls.push({ code, page, filters: { ...filters } })
      if (reject) throw new Error('Không thể tra cứu')
      const filtered = items.filter(loan =>
        (!filters?.fromDate || loan.borrowedAt.slice(0, 10) >= filters.fromDate)
        && (!filters?.toDate || loan.borrowedAt.slice(0, 10) <= filters.toDate)
        && (!filters?.status || loan.status === filters.status))
      return { items: filtered, page, size: 20, total: filters?.fromDate || filters?.toDate || filters?.status ? filtered.length : initialTotal }
    } } },
  }).default
  function tree() {
    cursor = 0
    const result = Page()
    for (const effect of effects.splice(0)) effect()
    return result
  }
  function html() { return renderToStaticMarkup(tree()) }
  function enterCode(value) {
    find(tree(), element => element.type === InputStub && element.props.required)?.props.onChange({ target: { value } })
  }
  function search() { find(tree(), element => element.type === 'form').props.onSubmit({ preventDefault() {} }) }
  function filter(id, value) {
    find(tree(), element => element.props?.id === id).props.onChange({ target: { value } })
  }
  function clear() {
    find(tree(), element => element.type === ButtonStub && element.props.children === 'Xóa bộ lọc').props.onClick()
  }
  return { tree, html, enterCode, search, filter, clear, calls,
    unmount() { for (const slot of slots) slot?.cleanup?.() } }
}
async function settle() {
  await new Promise(resolve => setImmediate(resolve))
  await new Promise(resolve => setImmediate(resolve))
}

test('client passes combined query and omits blank filters, retaining page', async () => {
  const calls = []
  const service = load('loanService.ts', { '../../core/api/apiClient': { apiClient: { async get(url, opts) {
    calls.push([url, opts]); return { data: { items: [], page: 0, size: 20, total: 0 } }
  } } } })
  await service.loanService.search('CARD-01', 2, { fromDate: '2026-10-01', toDate: '', status: 'BORROWED' })
  assert.equal(calls[0][0], '/loans/search')
  assert.equal(calls[0][1].params.code, 'CARD-01')
  assert.equal(calls[0][1].params.page, 2)
  assert.equal(calls[0][1].params.fromDate, '2026-10-01')
  assert.equal(calls[0][1].params.status, 'BORROWED')
  assert.equal(Object.hasOwn(calls[0][1].params, 'toDate'), false)
})

for (const code of ['CARD-01', 'COPY-01', 'PM-0010']) {
  test(`search ${code} shows unique loans and full item histories`, async () => {
    const p = pageFixture(); p.enterCode(`  ${code}  `); p.search(); await settle()
    assert.equal(p.calls[0].code, code)
    for (const value of ['PM-0010', 'PM-0011', 'Mắt biếc', 'Dế Mèn', 'Đã trả một phần']) {
      assert.ok(p.html().includes(value), value)
    }
    assert.match(p.html(), /2 phiếu mượn/)
    p.unmount()
  })
}

test('fromDate, toDate, status, combined filter and clearing retain search code and refresh total', async () => {
  const p = pageFixture(); p.enterCode('CARD-01'); p.search(); await settle()
  p.filter('loan-from-date', '2026-09-15'); await settle()
  assert.equal(p.calls.at(-1).filters.fromDate, '2026-09-15')
  assert.equal(p.calls.at(-1).page, 0)
  assert.match(p.html(), /1 phiếu mượn/)
  p.filter('loan-to-date', '2026-10-09'); await settle()
  assert.equal(p.calls.at(-1).filters.toDate, '2026-10-09')
  p.filter('loan-status', 'PARTIALLY_RETURNED'); await settle()
  assert.equal(p.calls.at(-1).filters.status, 'PARTIALLY_RETURNED')
  assert.deepEqual(p.calls.map(c => c.code), ['CARD-01', 'CARD-01', 'CARD-01', 'CARD-01'])
  p.clear(); await settle()
  assert.deepEqual(p.calls.at(-1).filters, { fromDate: '', toDate: '', status: '' })
  assert.equal(p.calls.at(-1).page, 0)
  assert.match(p.html(), /2 phiếu mượn/)
  p.unmount()
})

test('date end only, status only and all statuses are allowed', async () => {
  const p = pageFixture(); p.enterCode('CARD-01'); p.search(); await settle()
  p.filter('loan-to-date', '2026-09-15'); await settle()
  assert.equal(p.calls.at(-1).filters.toDate, '2026-09-15')
  p.clear(); await settle()
  p.filter('loan-status', 'RETURNED'); await settle()
  assert.equal(p.calls.at(-1).filters.status, 'RETURNED')
  p.filter('loan-status', ''); await settle()
  assert.equal(p.calls.at(-1).filters.status, '')
  p.unmount()
})

test('invalid dates prevent API calls and show transient red error', async () => {
  const p = pageFixture(); p.enterCode('CARD-01'); p.search(); await settle()
  p.filter('loan-from-date', '2026-10-09'); await settle()
  const before = p.calls.length
  p.filter('loan-to-date', '2026-10-01')
  assert.equal(p.calls.length, before)
  assert.match(p.html(), /Từ ngày không được lớn hơn Đến ngày/)
  assert.match(p.html(), /data-tone="error"/)
  p.filter('loan-to-date', '2026-10-09'); await settle()
  assert.equal(p.calls.length, before + 1)
  p.unmount()
})

test('changing a filter on page two resets to first page, paging keeps filters', async () => {
  const p = pageFixture({ initialTotal: 21 }); p.enterCode('CARD-01'); p.search(); await settle()
  const next = find(p.tree(), el => el.type === PaginationStub)
  next.props.onPageChange(2); await settle()
  assert.equal(p.calls.at(-1).page, 1)
  p.filter('loan-status', 'RETURNED'); await settle()
  assert.equal(p.calls.at(-1).page, 0)
  assert.equal(p.calls.at(-1).filters.status, 'RETURNED')
  p.unmount()
})

test('validation errors, API failures, and reader access', async () => {
  const p = pageFixture()
  p.enterCode(' '); p.search()
  assert.match(p.html(), /1 đến 100 ký tự/)
  p.enterCode('X'.repeat(101)); p.search()
  assert.equal(p.calls.length, 0)
  p.unmount()
  const failed = pageFixture({ reject: true }); failed.enterCode('PM-0010'); failed.search(); await settle()
  assert.match(failed.html(), /data-tone="error"/)
  assert.doesNotMatch(failed.html(), /PM-0011/)
  failed.unmount()
  const reader = pageFixture({ role: 'READER' }); assert.match(reader.html(), /không có quyền/)
  assert.equal(reader.calls.length, 0)
  reader.unmount()
})
