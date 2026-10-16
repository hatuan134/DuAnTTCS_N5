import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import { createRequire } from 'node:module'
import vm from 'node:vm'
import ts from 'typescript'
const require = createRequire(import.meta.url), react = require('react')
function load(file, imports) {
  const ctx = { exports: {}, require: name => imports[name] ?? require(name) }
  vm.runInNewContext(ts.transpileModule(readFileSync(new URL(file, import.meta.url), 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX, esModuleInterop: false },
  }).outputText, ctx)
  return ctx.exports
}
function all(node, predicate, out = []) {
  if (!node || typeof node !== 'object') return out
  if (Array.isArray(node)) { node.forEach(n => all(n, predicate, out)); return out }
  if (predicate(node)) out.push(node)
  all(node.props?.children, predicate, out); return out
}
function text(node) {
  if (typeof node === 'string' || typeof node === 'number') return String(node)
  if (Array.isArray(node)) return node.map(text).join(' ')
  return node?.props ? text(node.props.children) : ''
}
const loan = { id: 7, loanNumber: 'PM-000007', cardNumber: 'THE-07', readerName: 'Nguyễn An', readerPhone: '0901234567', borrowedAt: '2026-10-01T08:00:00+07:00', status: 'PARTIALLY_RETURNED', items: [
  { barcode: 'COPY-01', bookTitle: 'Mắt biếc', dueAt: '2026-10-15T08:00:00+07:00', status: 'BORROWED' },
  { barcode: 'COPY-02', bookTitle: 'Dế Mèn', dueAt: null, status: 'RETURNED' },
] }
const settle = () => new Promise(resolve => setImmediate(resolve))
function fixture(props = {}, behavior = {}) {
  const slots = [], effects = [], calls = []; let cursor = 0, tree
  const hooks = { ...react,
    useState(initial) { const i = cursor++; if (!(i in slots)) slots[i] = { value: initial }; return [slots[i].value, value => { slots[i].value = typeof value === 'function' ? value(slots[i].value) : value }] },
    useRef(initial) { const i = cursor++; return slots[i] ??= { current: initial } },
    useEffect(fn, deps) { const i = cursor++, previous = slots[i]; if (!previous || deps.some((v, j) => !Object.is(v, previous.deps[j]))) { slots[i] = { deps }; effects.push(() => { previous?.cleanup?.(); slots[i].cleanup = fn() }) } },
  }
  const api = {
    search: async (keyword, page, filters) => { calls.push({ mode: 'loans', keyword, page, filters }); return behavior.search ? behavior.search(keyword, page, filters) : { items: [loan], page, size: 10, total: 21 } },
    searchReaders: async (keyword, page) => { calls.push({ mode: 'readers', keyword, page }); return { items: [{ readerId: 99, readerName: 'Bạn đọc mới', readerPhone: '0907777777', cardNumber: 'THE-99' }], page, size: 10, total: 1 } },
  }
  const Panel = load('../src/features/s3-01-loans/LoanLookupPanel.tsx', {
    react: hooks, 'react-router-dom': { Link: 'a' },
    '../../components/ui/Button': { default: 'button' }, '../../components/ui/Card': { default: 'article' },
    '../../components/ui/FeedbackAlert': { default: 'feedback' }, '../../components/ui/Input': { default: 'input' },
    '../../components/ui/TablePagination': { default: 'pagination' },
    '../s1-02-user-management/accountService': { getApiErrorMessage: e => e.message },
    './loanService': { loanService: api, formatLoanTimestamp: value => value || 'Chưa có thông tin' },
  }).default
  const f = {
    calls,
    render() { cursor = 0; tree = Panel(props); effects.splice(0).forEach(fn => fn()); return tree },
    async flush() { for (let i = 0; i < 3; i++) { await settle(); f.render() } },
    find(predicate) { return all(tree, predicate)[0] },
    nodes(predicate) { return all(tree, predicate) },
    input(label, value) { const input = f.find(n => n.type === 'input' && n.props.label === label); assert.ok(input, label); input.props.onChange({ target: { value } }); f.render() },
    click(label) { const button = f.find(n => n.type === 'button' && text(n).trim() === label); assert.ok(button, label); button.props.onClick?.(); f.render() },
    submit() { f.find(n => n.type === 'form').props.onSubmit({ preventDefault() {} }); f.render() },
    text: () => text(tree),
    unmount() { slots.forEach(slot => slot?.cleanup?.()) },
  }
  f.render(); return f
}
test('opening lists existing loans without requiring a search, with phone and ten-item pagination', async () => {
  const f = fixture(); await f.flush()
  assert.equal(f.calls[0].keyword, ''); assert.equal(f.calls[0].page, 0)
  assert.match(f.text(), /Nguyễn An/); assert.match(f.text(), /0901234567/)
  const pagination = f.find(n => n.type === 'pagination')
  assert.equal(pagination.props.pageSize, 10); assert.equal(pagination.props.totalPages, 3)
  f.unmount()
})
for (const keyword of ['Nguyễn An', '090 123 4567', 'THE-07', 'COPY-01', 'PM-000007', '']) test(`search ${keyword || '(all)'} preserves full loan item history`, async () => {
  const f = fixture(); await f.flush(); f.input('Tìm phiếu mượn hoặc bạn đọc', ` ${keyword} `); f.submit(); await f.flush()
  assert.equal(f.calls.at(-1).keyword, keyword); assert.match(f.text(), /Mắt biếc/); assert.match(f.text(), /Dế Mèn/)
  f.unmount()
})
test('paging keeps keyword and applied date/status filters; a new query resets to page one', async () => {
  const f = fixture({ showFilters: true }); await f.flush()
  f.input('Tìm phiếu mượn hoặc bạn đọc', 'Nguyễn'); f.input('Từ ngày mượn', '2026-10-01'); f.input('Đến ngày mượn', '2026-10-15')
  f.find(n => n.type === 'select').props.onChange({ target: { value: 'BORROWED' } }); f.render(); f.submit(); await f.flush()
  f.find(n => n.type === 'pagination').props.onPageChange(2); f.render(); await f.flush()
  assert.equal(f.calls.at(-1).page, 1); assert.equal(f.calls.at(-1).keyword, 'Nguyễn'); assert.equal(f.calls.at(-1).filters.status, 'BORROWED')
  f.submit(); await f.flush(); assert.equal(f.calls.at(-1).page, 0)
})
test('invalid date range and long keyword produce dismissible error without a request', async () => {
  const f = fixture({ showFilters: true }); await f.flush(); const count = f.calls.length
  f.input('Từ ngày mượn', '2026-10-20'); f.input('Đến ngày mượn', '2026-10-01'); f.submit()
  assert.match(f.find(n => n.type === 'feedback').props.message, /Từ ngày/); assert.equal(f.calls.length, count)
  f.input('Tìm phiếu mượn hoặc bạn đọc', 'x'.repeat(101)); f.submit(); assert.match(f.find(n => n.type === 'feedback').props.message, /100/)
})
test('empty results retain search and recovery guidance', async () => {
  const f = fixture({}, { search: async () => ({ items: [], total: 0, size: 10, page: 0 }) }); await f.flush()
  assert.ok(f.find(n => n.type === 'form')); assert.match(f.text(), /Thử tìm bằng tên/)
})
test('API error dismissal leaves a retry control and never fabricates results', async () => {
  const f = fixture({}, { search: async () => { throw Error('Mất kết nối') } }); await f.flush()
  const notice = f.find(n => n.type === 'feedback'); assert.match(notice.props.message, /Mất kết nối/)
  notice.props.onDismiss(); f.render(); assert.equal(f.find(n => n.type === 'feedback'), undefined); assert.match(f.text(), /Thử lại/)
})
test('return selection offers only outstanding copies and collapses list without confirming', async () => {
  const selected = []; const f = fixture({ onSelectCopy: code => selected.push(code) }); await f.flush()
  assert.equal(f.nodes(n => n.type === 'button' && text(n) === 'Chọn để nhận trả').length, 1)
  f.click('Chọn để nhận trả'); assert.deepEqual(selected, ['COPY-01']); assert.match(f.text(), /Hiện danh sách để chọn tiếp/)
})
test('direct lending can select a reader who has no previous loan', async () => {
  const selected = []; const f = fixture({ onSelectReader: number => selected.push(number) }); await f.flush()
  f.click('Tìm bạn đọc kể cả chưa mượn'); await f.flush(); assert.equal(f.calls.at(-1).mode, 'readers')
  f.click('Chọn bạn đọc'); assert.deepEqual(selected, ['THE-99'])
})
test('slow obsolete search cannot replace a newer result', async () => {
  const pending = []
  const f = fixture({}, { search: () => new Promise(resolve => pending.push(resolve)) })
  f.input('Tìm phiếu mượn hoặc bạn đọc', 'Mới'); f.submit(); await settle(); f.render()
  pending.at(-1)({ items: [{ ...loan, readerName: 'Kết quả mới' }], total: 1, page: 0, size: 10 }); await f.flush()
  pending[0]({ items: [{ ...loan, readerName: 'Kết quả cũ' }], total: 1, page: 0, size: 10 }); await f.flush()
  assert.match(f.text(), /Kết quả mới/); assert.doesNotMatch(f.text(), /Kết quả cũ/)
})
test('reader access is denied before rendering the staff search panel', () => {
  const Page = load('../src/features/s3-01-loans/LoanSearchPage.tsx', {
    '../../components/ui/PageHeader': { default: () => null }, '../../core/auth/authStorage': { getCurrentUser: () => ({ role: 'READER' }) },
    './LoanLookupPanel': { default: () => { throw Error('must not mount') } }, './loanService': { loanRoles: ['LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN'] },
  }).default
  assert.match(text(Page()), /không có quyền/)
})
