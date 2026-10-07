import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import vm from 'node:vm'
import ts from 'typescript'
import { createRequire } from 'node:module'
import { renderToStaticMarkup } from 'react-dom/server'
const require = createRequire(import.meta.url), react = require('react')
const root = new URL('../src/features/s3-02-direct-loans/', import.meta.url)
function load(file, imports = {}, extra = {}) {
  const code = ts.transpileModule(readFileSync(new URL(file, root), 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX },
  }).outputText
  const context = { exports: {}, require: (name) => imports[name] ?? require(name), ...extra }
  vm.runInNewContext(code, context)
  return context.exports
}
const result = { readerId: 12, readerName: 'Nguyễn Văn An', cardNumber: 'TV-0012', cardTypeName: 'Thẻ sinh viên',
  cardStatus: 'ACTIVE', expiresAt: '2026-12-31', maxBooks: 5, borrowedBooks: 2, remainingBooks: 3,
  eligible: true, reasonCode: 'ELIGIBLE', message: 'Bạn đọc đủ điều kiện mượn thêm 3 sách.' }
const copy = (barcode) => ({ bookCopyId: Number(barcode.replace(/\D/g, '')) || 1, bookId: 50, barcode, bookTitle: `Sách ${barcode}`, remainingBooks: 3 })
const settle = () => new Promise((done) => setImmediate(done))
function find(node, predicate) {
  if (!node || typeof node !== 'object') return null
  if (Array.isArray(node)) { for (const child of node) { const found = find(child, predicate); if (found) return found } return null }
  if (predicate(node)) return node
  return find(node.props?.children, predicate)
}
function fixture({ reader = result, preview = async (_card, code) => copy(code) } = {}) {
  const states = [], effects = [], timers = new Map(), calls = []
  let cursor = 0, timerId = 0, tree
  const hooks = {
    ...react,
    useState(initial) {
      const i = cursor++
      if (!(i in states)) states[i] = initial
      return [states[i], (value) => { states[i] = typeof value === 'function' ? value(states[i]) : value }]
    },
    useRef(initial) { const i = cursor++; if (!(i in states)) states[i] = { current: initial }; return states[i] },
    useEffect(callback, deps) {
      const i = cursor++, previous = states[i]
      if (!previous || deps.some((value, n) => value !== previous.deps[n])) {
        effects.push(() => { previous?.cleanup?.(); states[i].cleanup = callback() })
        states[i] = { deps, cleanup: previous?.cleanup }
      }
    },
  }
  const imports = {

    react: hooks,

    '../s1-02-user-management/accountService': { getApiErrorMessage: (e) => e.message },
    '../s3-01-loans/loanService': { loanRoles: ['LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN'] },
    './directLoanService': { directLoanService: { previewItem(card, code, rows) { calls.push([card, code, rows]); return preview(card, code, rows) } } },
  }
  for (const name of ['Button', 'Card', 'Input', 'PageHeader']) imports[`../../components/ui/${name}`] = load(`../../components/ui/${name}.tsx`)
  const Page = load('DirectLoanItemsPanel.tsx', imports, { window: {
    setTimeout(callback, delay) { timers.set(++timerId, { callback, delay }); return timerId },
    clearTimeout(id) { timers.delete(id) },
  } }).default
  function render() {
    cursor = 0; tree = Page({ reader })
    for (const effect of effects.splice(0)) effect()
    cursor = 0; tree = Page({ reader })
    return tree
  }
  render()
  return {
    calls,
    html: () => renderToStaticMarkup(render()),
    change(value) { find(tree, (n) => n.props?.id === 'direct-loan-barcode').props.onChange({ target: { value } }); render() },
    submit() { find(tree, (n) => n.type === 'form').props.onSubmit({ preventDefault() {} }); render() },
    async runTimer() { for (const [id, t] of Array.from(timers)) { timers.delete(id); t.callback() } await settle(); render() },
    remove(code) { find(tree, (n) => n.props?.['aria-label'] === `Xóa sách có mã vạch ${code}`).props.onClick(); render() },
    hasBarcode: () => Boolean(find(tree, (n) => n.props?.id === 'direct-loan-barcode')),
    addDisabled: () => Boolean(find(tree, (n) => n.props?.type === 'submit').props.disabled),
    delays: () => Array.from(timers.values(), (t) => t.delay),
    unmount() { for (const state of states) state?.cleanup?.() },
  }
}

test('preview API uses authenticated client and passes the current draft', async () => {
  const calls = []
  const { directLoanService } = load('directLoanService.ts', { '../../core/api/apiClient': { apiClient: {
    async post(url, body) { calls.push([url, body]); return { data: copy('BC-1') } },
  } } })
  assert.equal((await directLoanService.previewItem(' TV-0012 ', ' BC-1 ', ['BC-2'])).barcode, 'BC-1')
  assert.equal(calls[0][0], '/loans/direct/items/preview')
  assert.equal(JSON.stringify(calls[0][1]), JSON.stringify({ cardNumber: 'TV-0012', barcode: 'BC-1', selectedBarcodes: ['BC-2'] }))
})
test('identified reader sees barcode input, empty draft and zero count', () => {
  const f = fixture(); assert.ok(f.hasBarcode()); assert.match(f.html(), /Chưa có sách trong lượt mượn/)
  assert.match(f.html(), /Dự kiến mượn: 0 \/ 3 sách/); assert.deepEqual(f.calls, [])
})
test('each consecutive barcode produces exactly one row while keeping preceding rows', async () => {
  const f = fixture()
  for (const code of ['BC-1', 'BC-2', 'BC-3']) { f.change(code); f.submit(); await settle() }
  const html = f.html()
  assert.equal((html.match(/<tr/g) ?? []).length, 4)
  for (const code of ['BC-1', 'BC-2', 'BC-3']) assert.ok(html.includes(`Sách ${code}`))
  assert.match(html, /Dự kiến mượn: 3 \/ 3 sách/)
  assert.deepEqual(f.calls.map((x) => Array.from(x[2])), [[], ['BC-1'], ['BC-1', 'BC-2']])
})
test('duplicate barcode with outer spaces does not add a row or call API', async () => {
  const f = fixture(); f.change('BC-1'); f.submit(); await settle(); f.html()
  f.change(' BC-1 '); f.submit(); await settle()
  assert.match(f.html(), /Mã vạch này đã có/); assert.equal(f.calls.length, 1)
  assert.match(f.html(), /Dự kiến mượn: 1 \/ 3 sách/)
})
test('remove updates count, preserves other rows and allows readding', async () => {
  const f = fixture()
  for (const code of ['BC-1', 'BC-2']) { f.change(code); f.submit(); await settle() }
  f.html(); f.remove('BC-1'); assert.match(f.html(), /Dự kiến mượn: 1 \/ 3 sách/)
  assert.doesNotMatch(f.html(), /Sách BC-1/); assert.match(f.html(), /Sách BC-2/)
  f.change('BC-1'); f.submit(); await settle(); assert.match(f.html(), /Dự kiến mượn: 2 \/ 3 sách/)
  assert.deepEqual(Array.from(f.calls[2][2]), ['BC-2'])
})
test('at quota disables add and warns, Enter cannot bypass, deleting unblocks', async () => {
  const f = fixture()
  for (const code of ['BC-1', 'BC-2', 'BC-3']) { f.change(code); f.submit(); await settle() }
  assert.match(f.html(), /sẽ vượt số sách/); assert.ok(f.addDisabled())
  f.change('BC-4'); f.submit(); await settle()
  assert.match(f.html(), /Không thể thêm sách/); assert.equal(f.calls.length, 3)
  f.remove('BC-2'); assert.equal(f.addDisabled(), false); assert.match(f.html(), /Dự kiến mượn: 2 \/ 3 sách/)
  f.submit(); await settle(); assert.match(f.html(), /Sách BC-4/)
})
test('parallel repeated Enter produces only one request and one row', async () => {
  let resolve
  const f = fixture({ preview: () => new Promise((done) => { resolve = done }) })
  f.change('BC-1'); f.submit(); f.submit(); assert.equal(f.calls.length, 1)
  assert.match(f.html(), /Đang tìm sách/); resolve(copy('BC-1')); await settle()
  assert.match(f.html(), /Dự kiến mượn: 1 \/ 3 sách/); assert.equal((f.html().match(/<tr/g) ?? []).length, 2)
})
test('failure keeps existing rows and barcode so it can be retried', async () => {
  let fail = true
  const f = fixture({ preview: async (_card, code) => {
    if (code === 'BC-2' && fail) throw new Error('Không thể thêm sách: lượt mượn sẽ vượt giới hạn.')
    return copy(code)
  } })
  f.change('BC-1'); f.submit(); await settle(); f.change('BC-2'); f.submit(); await settle()
  assert.match(f.html(), /Không thể thêm sách/); assert.match(f.html(), /Sách BC-1/)
  assert.match(f.html(), /value="BC-2"/); fail = false; f.submit(); await settle()
  assert.match(f.html(), /Dự kiến mượn: 2 \/ 3 sách/)
})
test('ineligible reader and invalid barcode never call API', async () => {
  const f = fixture({ reader: { ...result, eligible: false, remainingBooks: 0, message: 'Thẻ bị khóa' } })
  assert.match(f.html(), /Thẻ bị khóa/); assert.ok(f.addDisabled())
  f.change('BC-1'); f.submit(); await settle(); assert.deepEqual(f.calls, [])
  const g = fixture()
  for (const code of ['', ' ', 'x'.repeat(101)]) { g.change(code); g.submit(); assert.match(g.html(), /100 ký tự/) }
  assert.deepEqual(g.calls, [])
})
test('unmounted draft ignores delayed API result when switching reader', async () => {
  let resolve
  const f = fixture({ preview: () => new Promise((done) => { resolve = done }) })
  f.change('BC-1'); f.submit(); f.unmount(); resolve(copy('BC-1')); await settle()
  assert.doesNotMatch(f.html(), /Sách BC-1/)
})
test('server refreshed quota is used for following additions', async () => {
  const f = fixture({ preview: async (_card, code) => ({ ...copy(code), remainingBooks: 1 }) })
  f.change('BC-1'); f.submit(); await settle(); assert.match(f.html(), /Dự kiến mượn: 1 \/ 1 sách/)
  assert.ok(f.addDisabled()); f.change('BC-2'); f.submit(); await settle(); assert.equal(f.calls.length, 1)
})
