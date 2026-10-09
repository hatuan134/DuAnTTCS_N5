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
  const ctx = { exports: {}, require: (name) => imports[name] ?? require(name) }
  vm.runInNewContext(compiled, ctx)
  return ctx.exports
}
const sample = [
  { id: 10, loanNumber: 'PM-0010', cardNumber: 'CARD-01', readerName: 'Nguyễn An',
    borrowedAt: '2026-10-01T08:00:00+07:00', status: 'PARTIALLY_RETURNED',
    items: [
      { barcode: 'COPY-01', bookTitle: 'Mắt biếc', dueAt: '2026-10-15T08:00:00+07:00', status: 'BORROWED' },
      { barcode: 'COPY-02', bookTitle: 'Dế Mèn', dueAt: null, status: 'RETURNED' },
    ] },
  { id: 11, loanNumber: 'PM-0011', cardNumber: 'CARD-01', readerName: 'Nguyễn An',
    borrowedAt: '2026-09-01T08:00:00+07:00', status: 'RETURNED',
    items: [{ barcode: 'COPY-01', bookTitle: 'Mắt biếc', dueAt: '2026-09-15T08:00:00+07:00', status: 'RETURNED' }] },
]

function InputStub(props) { return react.createElement('input', props) }
function ButtonStub({ children, loading, ...props }) { return react.createElement('button', { ...props, disabled: props.disabled || loading }, children) }
function PanelStub({ children, className }) { return react.createElement('div', { className }, children) }
function HeaderStub({ title, description }) { return react.createElement('header', {}, title, description) }
function AlertStub({ message, tone }) { return react.createElement('p', { 'data-tone': tone }, message) }
function find(tree, predicate) {
  if (!tree || typeof tree !== 'object') return null
  if (predicate(tree)) return tree
  const children = react.Children.toArray(tree.props?.children)
  for (const child of children) { const found = find(child, predicate); if (found) return found }
  return null
}
function pageFixture({ role = 'LIBRARIAN', result = sample, reject = false } = {}) {
  const slots = [], effects = [], calls = []
  let cursor = 0
  const hooks = { ...react,
    useState(initial) {
      const index = cursor++
      if (!(index in slots)) slots[index] = { state: initial }
      return [slots[index].state, value => { slots[index].state = typeof value === 'function' ? value(slots[index].state) : value }]
    },
    useRef(initial) { const index = cursor++; return slots[index] ??= { current: initial } },
    useEffect(callback, deps) {
      const index = cursor++, old = slots[index]
      if (!old || deps.some((v, i) => !Object.is(v, old.deps[i]))) {
        slots[index] = { deps, cleanup: old?.cleanup }
        effects.push(() => { old?.cleanup?.(); slots[index].cleanup = callback() })
      }
    },
  }
  const service = load('loanService.ts', { '../../core/api/apiClient': { apiClient: {} } })
  const Page = load('LoanSearchPage.tsx', {
    react: hooks,
    'react-router-dom': { Link: ({ children, to }) => react.createElement('a', { href: to }, children) },
    '../../components/ui/Button': { __esModule: true, default: ButtonStub },
    '../../components/ui/Card': { __esModule: true, default: PanelStub },
    '../../components/ui/FeedbackAlert': { __esModule: true, default: AlertStub },
    '../../components/ui/Input': { __esModule: true, default: InputStub },
    '../../components/ui/LoadingState': { __esModule: true, default: () => react.createElement('p', {}, 'Đang tìm') },
    '../../components/ui/PageHeader': { __esModule: true, default: HeaderStub },
    '../../core/auth/authStorage': { getCurrentUser: () => ({ role }) },
    '../s1-02-user-management/accountService': { getApiErrorMessage: (e) => e.message },
    './loanService': { ...service, loanService: { search: async code => {
      calls.push(code)
      if (reject) throw Error('Không thể tra cứu')
      return result
    } } },
  }).default
  function render() {
    cursor = 0
    const tree = Page()
    for (const effect of effects.splice(0)) effect()
    return tree
  }
  function html() { return renderToStaticMarkup(render()) }
  function input(value) { const input = find(render(), element => element.type === InputStub); input.props.onChange({ target: { value } }) }
  function submit() { const form = find(render(), element => element.type === 'form'); form.props.onSubmit({ preventDefault() {} }) }
  return { html, input, submit, calls, unmount: () => { for (const slot of slots) slot?.cleanup?.() } }
}
const settle = async () => { await new Promise(resolve => setImmediate(resolve)); await new Promise(resolve => setImmediate(resolve)) }

test('the search client sends one trimmed-code query to the existing API client', async () => {
  const calls = []
  const service = load('loanService.ts', { '../../core/api/apiClient': { apiClient: { async get(url, opts) {
    calls.push([url, opts]); return { data: sample }
  } } } })
  assert.equal((await service.loanService.search('PM-0010')).length, 2)
  assert.equal(calls[0][0], '/loans/search')
  assert.equal(calls[0][1].params.code, 'PM-0010')
})
for (const code of ['CARD-01', 'COPY-01', 'PM-0010']) {
  test(`same input accepts ${code} and shows unique loans and all items`, async () => {
    const fixture = pageFixture()
    fixture.input(`  ${code}  `); fixture.submit(); await settle()
    const html = fixture.html()
    assert.deepEqual(fixture.calls, [code])
    assert.equal((html.match(/Xem chi tiết/g) ?? []).length, 2)
    for (const value of ['PM-0010', 'PM-0011', 'CARD-01', 'Nguyễn An', 'COPY-01', 'COPY-02', 'Mắt biếc', 'Dế Mèn', '15/10/2026', 'Đã trả một phần']) assert.ok(html.includes(value), value)
    assert.match(html, /href="\/loans\/10"/)
    fixture.unmount()
  })
}
test('invalid empty and too-long values do not call the API', async () => {
  const fixture = pageFixture()
  fixture.input('  '); fixture.submit()
  assert.match(fixture.html(), /1 đến 100 ký tự/)
  fixture.input('X'.repeat(101)); fixture.submit()
  assert.match(fixture.html(), /1 đến 100 ký tự/)
  assert.equal(fixture.calls.length, 0)
  fixture.unmount()
})
test('unmatched code shows only an empty result, not suggestions', async () => {
  const fixture = pageFixture({ result: [] })
  fixture.input('NOT-FOUND'); fixture.submit(); await settle()
  assert.match(fixture.html(), /Không tìm thấy phiếu mượn/)
  assert.equal(fixture.calls.length, 1)
  fixture.unmount()
})
test('API errors show shared transient feedback and do not keep stale results', async () => {
  const fixture = pageFixture({ reject: true })
  fixture.input('PM-0010'); fixture.submit(); await settle()
  assert.match(fixture.html(), /data-tone="error"/) ; assert.match(fixture.html(), /Không thể tra cứu/)
  assert.doesNotMatch(fixture.html(), /PM-0011/)
  fixture.unmount()
})
test('readers cannot call staff search or see borrower information', () => {
  const fixture = pageFixture({ role: 'READER' })
  assert.match(fixture.html(), /không có quyền tra cứu/)
  assert.doesNotMatch(fixture.html(), /CARD-01|Mắt biếc/)
  assert.equal(fixture.calls.length, 0)
  fixture.unmount()
})
