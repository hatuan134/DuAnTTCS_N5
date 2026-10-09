import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import { createRequire } from 'node:module'
import vm from 'node:vm'
import ts from 'typescript'
const require = createRequire(import.meta.url)
const react = require('react')
const flush = () => new Promise(resolve => setImmediate(resolve))

function evaluate(path, dependencies, extra = {}) {
  const context = { exports: {}, require: name => dependencies[name] ?? require(name), AbortController, window: { confirm: () => true }, ...extra }
  vm.runInNewContext(ts.transpileModule(readFileSync(new URL(path, import.meta.url), 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX },
  }).outputText, context)
  return context.exports
}
const serviceModule = evaluate('../src/features/s3-07-returns/returnService.ts', {
  '../../core/api/apiClient': { apiClient: { get: async () => ({ data: {} }) } },
})

function fixture(role = 'LIBRARIAN', lookup = async () => openItem(), confirm = async () => returnedItem(), accept = true) {
  const slots = [], effects = [], calls = [], confirmations = []
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
  const names = ['Button', 'Card', 'EmptyState', 'FeedbackAlert', 'Input', 'PageHeader']
  const dependencies = Object.fromEntries(names.map(n => [`../../components/ui/${n}`, { __esModule: true, default: Object.assign(() => null, { displayName: n }) }]))
  dependencies.react = hooks
  dependencies['../../core/auth/authStorage'] = { getCurrentUser: () => ({ role }) }
  dependencies['../s1-02-user-management/accountService'] = { getApiErrorMessage: (e, fallback) => e?.response?.data?.message ?? fallback }
  dependencies['../s3-01-loans/loanService'] = { loanRoles: ['ADMIN', 'LIBRARY_MANAGER', 'LIBRARIAN'], formatLoanTimestamp: value => value ?? 'Chưa có thông tin' }
  dependencies['./returnService'] = { validateReturnBarcode: serviceModule.validateReturnBarcode, returnService: {
    lookup: (...args) => { calls.push(args); return lookup(...args) },
    confirm: (...args) => { confirmations.push(args); return confirm(...args) },
  } }
  const Page = evaluate('../src/features/s3-07-returns/ReceiveReturnPage.tsx', dependencies, { window: { confirm: () => accept } }).default
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
    calls, confirmations, render, text: () => text(tree), find,
    input(value) { find('Input').props.onChange({ target: { value } }); render() },
    submit() { const promise = find('form').props.onSubmit({ preventDefault() {} }); render(); return promise },
    dismiss() { find('FeedbackAlert').props.onDismiss(); render() },
    confirm() { const button = nodes(tree).find(n => n.props?.children === 'Xác nhận nhận trả'); button?.props.onClick(); render() },
    confirmButton() { return nodes(tree).find(n => n.props?.children === 'Xác nhận nhận trả') },
    unmount() { for (const slot of slots) slot?.cleanup?.() },
  }
}
function openItem(overrides = {}) {
  return { status: 'ON_TIME', message: 'Sách đang trong hạn trả.', copyId: 4, barcode: 'LIB-001', bookTitle: 'Mắt biếc',
    loanId: 8, loanNumber: 'PM-008', itemId: 9, readerId: 20, readerName: 'Nguyễn An',
    borrowedAt: '2026-10-01T10:00:00Z', dueAt: '2026-10-09T10:00:00Z', checkedOn: '2026-10-09', overdueDays: 0, ...overrides }
}


function returnedItem(overrides = {}) {
  return { message: 'Nhận trả sách thành công.', copyId: 4, barcode: 'LIB-001', bookTitle: 'Mắt biếc',
    loanId: 8, loanNumber: 'PM-008', itemId: 9, itemStatus: 'RETURNED', loanStatus: 'RETURNED', copyStatus: 'AVAILABLE',
    returnedAt: '2026-10-09T01:00:00+07:00', returnedById: 12, returnedByName: 'Thủ thư An', ...overrides }
}
async function lookup(f) { f.input('LIB-001'); f.submit(); await flush(); f.render() }
test('confirmation API trims barcode and sends previewed item, never a browser return date or actor', async () => {
  const calls = [], response = returnedItem()
  const module = evaluate('../src/features/s3-07-returns/returnService.ts', { '../../core/api/apiClient': {
    apiClient: { post: async (...args) => { calls.push(args); return { data: response } } },
  } })
  assert.equal(await module.returnService.confirm(' LIB-001 ', 9), response)
  assert.equal(calls[0][0], '/loans/return-confirmation')
  assert.equal(JSON.stringify(calls[0][1]), JSON.stringify({ barcode: 'LIB-001', itemId: 9 }))
})
for (const status of ['ON_TIME', 'OVERDUE', 'MISSING_DUE_DATE']) test(`${status}: shows confirmation only for open item and displays saved return`, async () => {
  const f = fixture('LIBRARIAN', async () => openItem({ status }))
  assert.equal(f.confirmButton(), undefined); await lookup(f); assert.ok(f.confirmButton())
  f.confirm(); await flush(); f.render()
  assert.deepEqual(f.confirmations, [['LIB-001', 9]])
  for (const value of ['Đã trả', 'Ngày trả thực tế', '2026-10-09T01:00:00+07:00', 'Thủ thư An', 'Sẵn sàng']) assert.ok(f.text().includes(value), value)
  assert.equal(f.confirmButton(), undefined)
  assert.equal(f.find('FeedbackAlert').props.tone, 'success')
  f.dismiss(); assert.ok(f.text().includes('Ngày trả thực tế')); assert.ok(f.text().includes('Sẵn sàng'))
})
test('unborrowed copy and reader have no confirmation action', async () => {
  const f = fixture('LIBRARIAN', async () => openItem({ status: 'NOT_BORROWED', itemId: null }))
  await lookup(f); assert.equal(f.confirmButton(), undefined); f.confirm(); assert.equal(f.confirmations.length, 0)
  const reader = fixture('READER'); assert.equal(reader.confirmButton(), undefined)
})
test('other items of a partially returned loan remain visible as outstanding', async () => {
  const f = fixture('LIBRARIAN', undefined, async () => returnedItem({ loanStatus: 'BORROWED' }))
  await lookup(f); f.confirm(); await flush(); f.render(); assert.ok(f.text().includes('Còn cuốn chưa trả'))
})
test('duplicate click and Enter cannot submit again or change barcode while confirmation is pending', async () => {
  let resolve; const f = fixture('LIBRARIAN', undefined, () => new Promise(r => { resolve = r }))
  await lookup(f); f.confirm(); f.confirm(); f.submit(); f.input('LIB-OTHER')
  assert.equal(f.confirmations.length, 1); assert.equal(f.calls.length, 1)
  assert.equal(f.find('Input').props.disabled, true); assert.equal(f.find('Input').props.value, 'LIB-001')
  resolve(returnedItem()); await flush(); f.render(); assert.equal(f.find('Input').props.disabled, false)
})
test('409 rejects stale preview and requires fresh lookup without fabricating success', async () => {
  const f = fixture('LIBRARIAN', undefined, async () => { throw { response: { status: 409, data: { message: 'Cuốn sách đã trả.' } } } })
  await lookup(f); f.confirm(); await flush(); f.render()
  assert.equal(f.confirmButton(), undefined); assert.equal(f.find('FeedbackAlert').props.tone, 'error')
  assert.ok(!f.text().includes('Ngày trả thực tế'))
})
test('rolled-back server failure retains preview and allows a deliberate retry', async () => {
  let count = 0
  const f = fixture('LIBRARIAN', undefined, async () => { if (!count++) throw { response: { status: 500, data: { message: 'Dữ liệu được giữ nguyên.' } } }; return returnedItem() })
  await lookup(f); f.confirm(); await flush(); f.render(); assert.ok(f.confirmButton())
  assert.equal(f.find('FeedbackAlert').props.message, 'Dữ liệu được giữ nguyên.')
  f.confirm(); await flush(); f.render(); assert.ok(f.text().includes('Ngày trả thực tế'))
})
test('lost response clears stale action and asks staff to check the persisted result', async () => {
  const f = fixture('LIBRARIAN', undefined, async () => { throw new Error('network timeout') })
  await lookup(f); f.confirm(); await flush(); f.render(); assert.equal(f.confirmButton(), undefined)
  assert.ok(f.find('FeedbackAlert').props.message.includes('Chưa xác định được kết quả'))
})
test('unmount ignores a late confirmation result', async () => {
  let resolve; const f = fixture('LIBRARIAN', undefined, () => new Promise(r => { resolve = r }))
  await lookup(f); f.confirm(); f.unmount(); resolve(returnedItem()); await flush(); f.render()
  assert.ok(!f.text().includes('Ngày trả thực tế'))
})

test('cancelling confirmation keeps preview and never writes', async () => {
  const f = fixture('LIBRARIAN', undefined, undefined, false)
  await lookup(f); f.confirm(); await flush(); f.render()
  assert.equal(f.confirmations.length, 0); assert.ok(f.confirmButton())
  assert.equal(f.find('Input').props.disabled, false)
})
