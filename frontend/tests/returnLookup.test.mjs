import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import { createRequire } from 'node:module'
import vm from 'node:vm'
import ts from 'typescript'
const require = createRequire(import.meta.url)
const react = require('react')
const flush = () => new Promise(resolve => setImmediate(resolve))

function evaluate(path, dependencies) {
  const context = { exports: {}, require: name => dependencies[name] ?? require(name), AbortController }
  vm.runInNewContext(ts.transpileModule(readFileSync(new URL(path, import.meta.url), 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX },
  }).outputText, context)
  return context.exports
}
const serviceModule = evaluate('../src/features/s3-07-returns/returnService.ts', {
  '../../core/api/apiClient': { apiClient: { get: async () => ({ data: {} }) } },
})

function fixture(role = 'LIBRARIAN', lookup = async () => openItem()) {
  const slots = [], effects = [], calls = []
  let cursor = 0, tree
  const hooks = { ...react,
    useState(initial) { const i = cursor++; if (!(i in slots)) slots[i] = typeof initial === 'function' ? initial() : initial
      return [slots[i], value => { slots[i] = typeof value === 'function' ? value(slots[i]) : value }] },
    useRef(value) { const i = cursor++; return slots[i] ??= { current: value } },
    useEffect(fn, deps) { const i = cursor++, old = slots[i]
      if (!old || deps.some((value, n) => !Object.is(value, old.deps[n]))) {
        slots[i] = { deps, cleanup: old?.cleanup }
        effects.push(() => { old?.cleanup?.(); slots[i].cleanup = fn() })
      }
    },
  }
  const names = ['ConfirmActionDialog', 'Button', 'Card', 'EmptyState', 'FeedbackAlert', 'Input', 'PageHeader']
  const dependencies = Object.fromEntries(names.map(n => [`../../components/ui/${n}`, { __esModule: true, default: Object.assign(() => null, { displayName: n }) }]))
  dependencies['../../components/ui/TablePagination'] = { __esModule: true, default: Object.assign(() => null, { displayName: 'TablePagination' }) }
  dependencies['../../hooks/useTablePagination'] = { __esModule: true, default: items => ({ pageItems: items, startIndex: 0, page: 1, totalPages: 1, totalItems: items.length, pageSize: 10, goToPage() {} }) }
  dependencies.react = hooks
  dependencies['../../core/auth/authStorage'] = { getCurrentUser: () => ({ role }) }
  dependencies['../s1-02-user-management/accountService'] = { getApiErrorMessage: (e, fallback) => e?.response?.data?.message ?? fallback }
  dependencies['../s3-01-loans/loanService'] = { loanRoles: ['ADMIN', 'LIBRARY_MANAGER', 'LIBRARIAN'], formatLoanTimestamp: value => value ?? 'Chưa có thông tin' }
  dependencies['./returnService'] = { ...serviceModule, returnService: {
    lookup: (...args) => { calls.push(args); return lookup(...args) },
  } }
  const Page = evaluate('../src/features/s3-07-returns/ReceiveReturnPage.tsx', dependencies).default
  function render() { cursor = 0; tree = Page(); for (const effect of effects.splice(0)) effect(); return tree }
  function nodes(node) {
    if (!node || typeof node !== 'object') return []
    if (Array.isArray(node)) return node.flatMap(n => nodes(n))
    return [node, ...nodes(node.props?.children)]
  }
  function find(name) { return nodes(tree).find(node => typeof node.type === 'function' ? node.type.displayName === name : node.type === name) }
  // Expand the page's presentational Detail components without hooks.
  function text(node) {
    if (node == null || typeof node === 'boolean') return ''
    if (Array.isArray(node)) return node.map(n => text(n)).join(' ')
    if (typeof node !== 'object') return String(node)
    if (typeof node.type === 'function' && node.type.name === 'Detail') return text(node.type(node.props))
    return text(node.props?.children)
  }
  render()
  return {
    calls, render, text: () => text(tree), find,
    input(value) { find('Input').props.onChange({ target: { value } }); render() },
    submit() { const promise = find('form').props.onSubmit({ preventDefault() {} }); render(); return promise },
    dismiss() { find('FeedbackAlert').props.onDismiss(); render() },
    unmount() { for (const slot of slots) slot?.cleanup?.() },
  }
}
function openItem(overrides = {}) {
  return { status: 'ON_TIME', message: 'Sách đang trong hạn trả.', copyId: 4, barcode: 'LIB-001', bookTitle: 'Mắt biếc',
    loanId: 8, loanNumber: 'PM-008', itemId: 9, readerId: 20, readerName: 'Nguyễn An',
    borrowedAt: '2026-10-01T10:00:00Z', dueAt: '2026-10-09T10:00:00Z', checkedOn: '2026-10-09', overdueDays: 0, ...overrides }
}

test('barcode input validates blank, spaces and 101 chars, accepts exactly 100', () => {
  for (const value of ['', '  ', 'X'.repeat(101)]) assert.ok(serviceModule.validateReturnBarcode(value))
  assert.equal(serviceModule.validateReturnBarcode('X'.repeat(100)), '')
})
test('lookup API trims barcode, sends query params and cancellation signal', async () => {
  const calls = [], result = openItem(), signal = new AbortController().signal
  const module = evaluate('../src/features/s3-07-returns/returnService.ts', { '../../core/api/apiClient': {
    apiClient: { get: async (...args) => { calls.push(args); return { data: result } } },
  } })
  assert.equal(await module.returnService.lookup(' LIB-001 ', signal), result)
  assert.equal(calls[0][0], '/loans/return-lookup')
  assert.equal(calls[0][1].params.barcode, 'LIB-001'); assert.equal(calls[0][1].signal, signal)
})
for (const role of ['LIBRARIAN', 'ADMIN', 'LIBRARY_MANAGER']) test(`${role} finds correct borrower, title, dates and on-time status`, async () => {
  const f = fixture(role); f.input('LIB-001'); f.submit(); await flush(); f.render()
  for (const expected of ['Mắt biếc', 'Nguyễn An', 'PM-008', '2026-10-01T10:00:00Z', '2026-10-09T10:00:00Z', 'Đúng hạn']) assert.ok(f.text().includes(expected), expected)
  assert.equal(f.find('FeedbackAlert').props.tone, 'success')
  f.dismiss(); assert.equal(f.find('FeedbackAlert'), undefined); assert.ok(f.text().includes('Nguyễn An'))
})
test('overdue displays server-calculated days, never recalculates using browser timezone', async () => {
  const f = fixture('LIBRARIAN', async () => openItem({ status: 'OVERDUE', overdueDays: 3 }));
  f.input('LIB-001'); f.submit(); await flush(); f.render()
  assert.ok(f.text().includes('Quá hạn 3 ngày')); assert.ok(f.text().includes('Số ngày trễ'))
})
test('existing unborrowed copy gives distinct informational notice and no historical reader', async () => {
  const f = fixture('LIBRARIAN', async () => openItem({ status: 'NOT_BORROWED', message: 'Bản sao này hiện không có ai mượn.',
    loanId: null, loanNumber: null, itemId: null, readerId: null, readerName: null, borrowedAt: null, dueAt: null, overdueDays: null }));
  f.input('LIB-001'); f.submit(); await flush(); f.render()
  assert.equal(f.find('FeedbackAlert').props.message, 'Bản sao này hiện không có ai mượn.')
  assert.equal(f.find('FeedbackAlert').props.tone, 'info'); assert.ok(!f.text().includes('Nguyễn An'))
  f.dismiss(); assert.ok(f.text().includes('Mắt biếc'))
})
test('unknown barcode and network failure show contextual API errors and clear previous result', async () => {
  let count = 0
  const f = fixture('LIBRARIAN', async () => { if (!count++) return openItem(); throw { response: { data: { message: 'Mã vạch này không tồn tại trong thư viện.' } } } })
  f.input('LIB-001'); f.submit(); await flush(); f.render()
  f.input('UNKNOWN'); f.submit(); await flush(); f.render()
  assert.equal(f.find('FeedbackAlert').props.message, 'Mã vạch này không tồn tại trong thư viện.')
  assert.ok(!f.text().includes('Nguyễn An'))
  const network = fixture('LIBRARIAN', async () => { throw new Error('offline') })
  network.input('LIB-001'); network.submit(); await flush(); network.render()
  assert.equal(network.find('FeedbackAlert').props.tone, 'error')
})
test('missing deadline is a warning, never says on time or zero days', async () => {
  const f = fixture('LIBRARIAN', async () => openItem({ status: 'MISSING_DUE_DATE', dueAt: null, overdueDays: null, message: 'Phiếu mượn chưa có hạn trả.' }))
  f.input('LIB-001'); f.submit(); await flush(); f.render()
  assert.ok(f.text().includes('Chưa có hạn trả')); assert.ok(!f.text().includes('Đúng hạn'))
  assert.equal(f.find('FeedbackAlert').props.tone, 'warning')
})
test('reader has no form and sends no requests', () => {
  const f = fixture('READER'); assert.equal(f.find('form'), undefined); assert.equal(f.calls.length, 0)
})
test('invalid barcode never requests API; validation survives notice dismissal', () => {
  const f = fixture(); f.input('  '); f.submit(); f.render()
  assert.equal(f.calls.length, 0); assert.ok(f.find('Input').props.error)
  f.dismiss(); assert.ok(f.find('Input').props.error)
})
test('duplicate Enter during request is ignored', async () => {
  let resolve
  const f = fixture('LIBRARIAN', () => new Promise(r => { resolve = r }))
  f.input('LIB-001'); f.submit(); f.submit(); assert.equal(f.calls.length, 1)
  assert.equal(f.find('Button').props.loading, true)
  resolve(openItem()); await flush(); f.render(); assert.equal(f.find('Button').props.loading, false)
})
test('changing barcode aborts lookup and ignores a late response from previous barcode', async () => {
  const pending = []
  const f = fixture('LIBRARIAN', () => new Promise(resolve => pending.push(resolve)))
  f.input('LIB-001'); f.submit(); const firstSignal = f.calls[0][1]
  f.input('LIB-002'); assert.equal(firstSignal.aborted, true); f.submit()
  pending[1](openItem({ barcode: 'LIB-002', readerName: 'Bạn đọc mới' })); await flush(); f.render()
  pending[0](openItem()); await flush(); f.render()
  assert.ok(f.text().includes('Bạn đọc mới')); assert.ok(!f.text().includes('Nguyễn An'))
})
test('unmount cancels pending lookup without rendering a delayed result', async () => {
  let resolve
  const f = fixture('LIBRARIAN', () => new Promise(r => { resolve = r }))
  f.input('LIB-001'); f.submit(); f.unmount(); assert.equal(f.calls[0][1].aborted, true)
  resolve(openItem()); await flush(); assert.ok(!f.text().includes('Nguyễn An'))
})
