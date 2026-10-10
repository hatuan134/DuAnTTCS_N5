import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import vm from 'node:vm'
import ts from 'typescript'
import { createRequire } from 'node:module'
import { renderToStaticMarkup } from 'react-dom/server'
const require = createRequire(import.meta.url), react = require('react')
const root = new URL('../src/features/s3-04-my-borrowed-books/', import.meta.url)
function load(file, imports = {}) {
  const context = { exports: {}, require: (name) => imports[name] ?? require(name) }
  vm.runInNewContext(ts.transpileModule(readFileSync(new URL(file, root), 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX },
  }).outputText, context)
  return context.exports
}
const formatter = load('../s3-01-loans/loanService.ts', { '../../core/api/apiClient': { apiClient: {} } })
const Pagination = load('../../components/ui/TablePagination.tsx').default
const settle = () => new Promise((resolve) => setImmediate(resolve))
function find(node, predicate) {
  if (!node || typeof node !== 'object') return null
  if (Array.isArray(node)) { for (const child of node) { const value = find(child, predicate); if (value) return value } return null }
  return predicate(node) ? node : find(node.props?.children, predicate)
}
function row(id) { return { id, bookTitle: `BOOK-${id}`, barcode: `BAR-${id}`, loanNumber: `PM-${id}`,
  borrowedAt: '2026-10-01T23:30:00Z', returnedAt: '2026-10-08T10:00:00Z' } }
function fixture({ role = 'READER', total = 0, request, borrowedRequest, file = 'MyReturnedBooksPanel.tsx' } = {}) {
  const slots = [], effects = [], calls = []
  let cursor = 0, tree, user = { id: 12, role }
  const hooks = {
    ...react,
    useState(initial) {
      const i = cursor++; if (!(i in slots)) slots[i] = initial
      return [slots[i], (value) => { slots[i] = typeof value === 'function' ? value(slots[i]) : value }]
    },
    useRef(value) { const i = cursor++; return slots[i] ??= { current: value } },
    useEffect(callback, deps) {
      const i = cursor++, previous = slots[i]
      if (!previous || deps.some((value, n) => !Object.is(value, previous.deps[n]))) {
        slots[i] = { deps, cleanup: previous?.cleanup }
        effects.push(() => { previous?.cleanup?.(); slots[i].cleanup = callback() })
      }
    },
  }
  const imports = {
    react: hooks,
    '../../core/auth/authStorage': { getCurrentUser: () => user },
    '../s1-02-user-management/accountService': { getApiErrorMessage: (e) => e.message },
    '../s3-01-loans/loanService': formatter,
    '../../components/ui/TablePagination': { __esModule: true, default: Pagination },
    '../../components/ui/FeedbackAlert': { __esModule: true, default: ({ message }) => react.createElement('p', { role: 'alert' }, message) },
    './BorrowedBookDueWarning': { __esModule: true, default: () => null },
    './MyReturnedBooksPanel': { __esModule: true, default: () => react.createElement('p', {}, 'HISTORY PANEL') },
    '../../hooks/useTablePagination': { __esModule: true, default: items => ({ pageItems: items, startIndex: 0, page: 1, totalPages: 1, totalItems: items.length, pageSize: 10, goToPage() {} }) },
    '../../components/ui/TableActionButton': { __esModule: true, default: ({ children, ...props }) => react.createElement('button', props, children) },
    './myBorrowedBooksService': { myBorrowedBooksService: {
      async history(page) {
        calls.push({ page, user: user.id })
        return request ? request(page) : { page, size: 20, total,
          items: Array.from({ length: Math.min(20, Math.max(0, total - page * 20)) }, (_, i) => row(page * 20 + i + 1)) }
      },
      async list() { calls.push('borrowed'); return borrowedRequest ? borrowedRequest(user.id) : [] },
    } },
  }
  for (const name of ['Button', 'Card', 'EmptyState', 'LoadingState', 'PageHeader']) imports[`../../components/ui/${name}`] = load(`../../components/ui/${name}.tsx`)
  const Component = load(file, imports).default
  function render() { cursor = 0; tree = Component(); return tree }
  async function flush() {
    for (let i = 0; i < 5; i++) { render(); for (const effect of effects.splice(0)) effect(); await settle() }
    render()
  }
  render()
  return {
    calls, flush, html: () => renderToStaticMarkup(render()),
    next(page) { const control = find(tree, (n) => n.type === Pagination); assert.ok(control); control.props.onPageChange(page); render() },
    tab(id) { find(tree, (n) => n.props?.id === id).props.onClick(); render() },
    changeUser(id) { user = { ...user, id }; render() },
    unmount() { for (const slot of slots) slot?.cleanup?.() },
  }
}
test('history client supplies only zero-based page via authenticated client', async () => {
  const calls = []
  const api = load('myBorrowedBooksService.ts', { '../../core/api/apiClient': { apiClient: {
    async get(url, config) { calls.push({ url, config }); return { data: { items: [], page: 2, size: 20, total: 0 } } },
  } } }).myBorrowedBooksService
  await api.history(2); assert.equal(calls[0].url, '/loans/me/returned-books')
  assert.equal(calls[0].config.params.page, 2); assert.deepEqual(Object.keys(calls[0].config.params), ['page'])
})
for (const count of [0, 7, 20, 21, 45]) test(`${count} rows: empty or maximum twenty with correct pagination`, async () => {
  const f = fixture({ total: count }); await f.flush(); const html = f.html()
  if (count === 0) assert.match(html, /Bạn chưa có lịch sử trả sách/)
  assert.equal((html.match(/<tr class="hover:bg-slate-50"/g) ?? []).length, Math.min(20, count))
  assert.equal(html.includes('aria-label="Phân trang bảng"'), count > 20)
  if (count) for (const label of ['Tên sách', 'Mã vạch bản sao', 'Mã phiếu mượn', 'Ngày mượn', 'Thời điểm trả', 'PM-1', '02/10/2026', '08/10/2026', '17:00:00']) assert.ok(html.includes(label), label)
  assert.doesNotMatch(html, /href=.*loans/)
})
test('next previous and last page preserve rows without duplicate or stale-page display', async () => {
  const f = fixture({ total: 45 }); await f.flush(); const ids = []
  for (const page of [1, 2, 3]) {
    if (page > 1) { f.next(page); assert.doesNotMatch(f.html(), /BOOK-1</); await f.flush() }
    const html = f.html()
    ids.push(...Array.from(html.matchAll(/<td class="break-words px-4 py-4 font-medium">BOOK-(\d+)<\/td>/g), (m) => Number(m[1])))
    assert.match(html, new RegExp(`Trang ${page} / ${3}`))
    if (page === 3) assert.match(html, /disabled=""[^>]*aria-label="Trang sau"/)
  }
  assert.deepEqual(ids, Array.from({ length: 45 }, (_, i) => i + 1)); assert.equal(new Set(ids).size, 45)
  f.next(2); await f.flush(); assert.match(f.html(), /BOOK-21</); assert.doesNotMatch(f.html(), /BOOK-41</)
  f.next(1); await f.flush(); assert.match(f.html(), /BOOK-1</)
  assert.deepEqual(f.calls.map((call) => call.page), [0, 1, 2, 1, 0])
})
test('staff never requests history', async () => {
  for (const role of ['ADMIN', 'LIBRARY_MANAGER', 'LIBRARIAN']) {
    const f = fixture({ role }); await f.flush(); assert.deepEqual(f.calls, []); assert.match(f.html(), /dành cho Bạn đọc/)
  }
})
test('API failure is distinct from empty history', async () => {
  const f = fixture({ request: async () => { throw new Error('Lỗi API lịch sử') } }); await f.flush()
  assert.match(f.html(), /Lỗi API lịch sử/); assert.match(f.html(), /Chưa tải được lịch sử/)
  assert.doesNotMatch(f.html(), /Bạn chưa có lịch sử trả sách/)
})
test('tabs isolate returned content from borrowed content', async () => {
  const f = fixture({ file: 'MyBorrowedBooksPage.tsx' }); await f.flush()
  assert.match(f.html(), /aria-selected="true"[^>]*aria-controls="borrowed-books-panel"/)
  f.tab('returned-books-tab'); await f.flush(); assert.match(f.html(), /HISTORY PANEL/)
  assert.doesNotMatch(f.html(), /Bạn không có sách đang mượn/)
  f.tab('borrowed-books-tab'); await f.flush(); assert.doesNotMatch(f.html(), /HISTORY PANEL/)
  assert.deepEqual(f.calls, ['borrowed', 'borrowed'])
})
test('out-of-range page recovers when total shrinks', async () => {
  let count = 45
  const f = fixture({ request: async (page) => {
    if (page === 2) count = 21
    return { page, size: 20, total: count, items: page * 20 < count ? [row(page * 20 + 1)] : [] }
  } }); await f.flush(); f.next(3); await f.flush()
  assert.match(f.html(), /Trang 2 \/ 2/); assert.match(f.html(), /BOOK-21</)
  assert.deepEqual(f.calls.map((call) => call.page), [0, 2, 1])
})
test('unmount ignores delayed history result', async () => {
  let resolveOld
  const old = new Promise((resolve) => { resolveOld = resolve })
  const f = fixture({ request: async () => old }); await f.flush(); f.unmount()
  resolveOld({ page: 0, size: 20, total: 1, items: [row(1)] }); await settle()
  assert.doesNotMatch(f.html(), /BOOK-1</)
})
test('reader change cancels old response and hides previous reader data', async () => {
  let resolveOld, calls = 0
  const old = new Promise((resolve) => { resolveOld = resolve })
  const f = fixture({ request: async () => ++calls === 1 ? old : { page: 0, size: 20, total: 1, items: [row(99)] } })
  await f.flush(); f.changeUser(99); await f.flush(); assert.match(f.html(), /BOOK-99</)
  resolveOld({ page: 0, size: 20, total: 1, items: [row(12)] }); await settle()
  assert.match(f.html(), /BOOK-99</); assert.doesNotMatch(f.html(), /BOOK-12</)
  assert.deepEqual(f.calls.map((call) => call.user), [12, 99])
})


test('borrowed tab hides cached previous-reader rows before reload', async () => {
  const book = id => ({ id, bookTitle: `PRIVATE-BOOK-${id}`, barcode: `BC-${id}`,
    borrowedAt: '2026-10-01T10:00:00Z', dueAt: null, remainingDays: null })
  const f = fixture({ file: 'MyBorrowedBooksPage.tsx', borrowedRequest: async id => [book(id)] })
  await f.flush(); assert.match(f.html(), /PRIVATE-BOOK-12/)
  f.changeUser(99); assert.doesNotMatch(f.html(), /PRIVATE-BOOK-12/)
  await f.flush(); assert.match(f.html(), /PRIVATE-BOOK-99/); assert.doesNotMatch(f.html(), /PRIVATE-BOOK-12/)
})
test('borrowed tab discards a delayed result from the previous reader', async () => {
  let finish
  const old = new Promise(resolve => { finish = resolve })
  const f = fixture({ file: 'MyBorrowedBooksPage.tsx', borrowedRequest: async id => id === 12 ? old : [] })
  await f.flush(); f.changeUser(99); await f.flush()
  finish([{ id: 12, bookTitle: 'PRIVATE-OLD-READER', barcode: 'BC-12', borrowedAt: '2026-10-01T10:00:00Z', dueAt: null, remainingDays: null }])
  await settle(); assert.doesNotMatch(f.html(), /PRIVATE-OLD-READER/)
})
