import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import vm from 'node:vm'
import { webcrypto } from 'node:crypto'
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
const settle = () => new Promise((done) => setImmediate(done))
function find(node, predicate) {
  if (!node || typeof node !== 'object') return null
  if (Array.isArray(node)) { for (const child of node) { const found = find(child, predicate); if (found) return found } return null }
  if (predicate(node)) return node
  return find(node.props?.children, predicate)
}
function fixture({ role = 'LIBRARIAN', check = async () => result } = {}) {
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
    '../../components/ui/FeedbackAlert': { __esModule: true, default: ({ message, onDismiss }) => react.createElement('div', { role: 'alert' }, message, react.createElement('button', { onClick: onDismiss }, 'Đóng thông báo')) },
    './DirectLoanItemsPanel': { __esModule: true, default: () => null },
    react: hooks,
    '../../core/auth/authStorage': { getCurrentUser: () => ({ role }) },
    '../s1-02-user-management/accountService': { getApiErrorMessage: (e) => e.message },
    '../s3-01-loans/loanService': { loanRoles: ['LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN'] },
    './directLoanService': { directLoanService: {
      checkReader(number) { calls.push(number); return check(number) },
      checkReaderExplicit(number, id) { calls.push(number); return check(number, id) },
    } },
  }
  for (const name of ['Button', 'Card', 'Input', 'PageHeader']) imports[`../../components/ui/${name}`] = load(`../../components/ui/${name}.tsx`)
  const Page = load('DirectLoanPage.tsx', imports, { crypto: webcrypto, window: {
    setTimeout(callback, delay) { timers.set(++timerId, { callback, delay }); return timerId },
    clearTimeout(id) { timers.delete(id) },
  } }).default
  function render() {
    cursor = 0; tree = Page()
    for (const effect of effects.splice(0)) effect()
    cursor = 0; tree = Page()
    return tree
  }
  render()
  return {
    calls,
    panel: () => find(tree, (n) => Boolean(n.props?.reader)),
    html: () => renderToStaticMarkup(render()),
    change(value) { find(tree, (n) => n.props?.id === 'direct-loan-card').props.onChange({ target: { value } }); render() },
    submit() { find(tree, (n) => n.type === 'form').props.onSubmit({ preventDefault() {} }); render() },
    async runTimer() { for (const [id, t] of Array.from(timers)) { timers.delete(id); t.callback() } await settle(); render() },
    delays: () => Array.from(timers.values(), (t) => t.delay),
    unmount() { for (const state of states) state?.cleanup?.() },
  }
}

test('client passes code as params through authenticated apiClient', async () => {
  const calls = []
  const { directLoanService } = load('directLoanService.ts', { '../../core/api/apiClient': { apiClient: {
    async get(url, config) { calls.push([url, config.params.cardNumber]); return { data: result } },
  } } })
  assert.equal((await directLoanService.checkReader(' TV/A&B ')).readerName, result.readerName)
  assert.deepEqual(calls, [['/loans/reader-eligibility', 'TV/A&B']])
})

test('initial page explains empty state without requests', () => {
  const f = fixture(); assert.match(f.html(), /Chưa có bạn đọc được chọn/); assert.deepEqual(f.calls, [])
})

test('typing debounces and renders required eligibility facts', async () => {
  const f = fixture(); f.change(' TV-0012 ')
  assert.deepEqual(f.delays(), [350]); assert.match(f.html(), /Đang kiểm tra/); await f.runTimer()
  for (const text of ['Nguyễn Văn An', 'Thẻ sinh viên', 'Sách đang mượn chưa trả', 'Sách còn được mượn thêm', 'Đủ điều kiện mượn']) assert.ok(f.html().includes(text), text)
  assert.match(f.html(), />2\/5<\/dd>/); assert.match(f.html(), />3<\/dd>/); assert.deepEqual(f.calls, ['TV-0012'])
  assert.doesNotMatch(f.html(), /id=".*barcode|Xác nhận phiếu/)
})

test('unknown card API error uses dismissible feedback and removes old reader', async () => {
  const f = fixture({ check: async (code) => { if (code === 'UNKNOWN') throw new Error('Không tìm thấy bạn đọc với mã thẻ này.'); return result } })
  f.change('TV-0012'); await f.runTimer(); assert.match(f.html(), /Nguyễn Văn An/)
  f.change('UNKNOWN'); assert.doesNotMatch(f.html(), /Nguyễn Văn An/); await f.runTimer()
  assert.match(f.html(), /role="alert"/); assert.doesNotMatch(f.html(), /id="direct-loan-card-error"/)
  assert.match(f.html(), /Không tìm thấy bạn đọc/); assert.doesNotMatch(f.html(), /Nguyễn Văn An/)
})

test('at limit shows reader and zero remaining with blocked message', async () => {
  const f = fixture({ check: async () => ({ ...result, borrowedBooks: 5, remainingBooks: 0, eligible: false,
    reasonCode: 'LOAN_LIMIT_REACHED', message: 'Bạn đọc đã đạt giới hạn mượn.' }) })
  f.change('TV-0012'); await f.runTimer()
  for (const text of ['Nguyễn Văn An', 'Thẻ sinh viên', 'Không đủ điều kiện mượn', 'đã đạt giới hạn']) assert.ok(f.html().includes(text))
  assert.match(f.html(), />0<\/dd>/)
})

test('slow old success and rejection never replace current reader', async () => {
  for (const rejectOld of [false, true]) {
    let resolveOld, reject
    const old = new Promise((resolve, rejecter) => { resolveOld = resolve; reject = rejecter })
    const f = fixture({ check: (code) => code === 'OLD' ? old : Promise.resolve({ ...result, readerName: 'Bạn đọc mới', cardNumber: 'NEW' }) })
    f.change('OLD'); await f.runTimer(); f.change('NEW'); await f.runTimer()
    if (rejectOld) reject(new Error('Lỗi của mã cũ')); else resolveOld(result)
    await settle(); assert.match(f.html(), /Bạn đọc mới/); assert.doesNotMatch(f.html(), /Nguyễn Văn An|Lỗi của mã cũ/)
  }
})

test('Enter bypasses debounce and repeated submits while in flight are ignored', async () => {
  let resolve
  const pending = new Promise((done) => { resolve = done })
  const f = fixture({ check: () => pending })
  f.change('TV-0012'); f.submit(); assert.deepEqual(f.delays(), [0])
  await f.runTimer(); f.submit(); await f.runTimer(); assert.deepEqual(f.calls, ['TV-0012'])
  resolve(result); await settle(); assert.match(f.html(), /Nguyễn Văn An/)
})

test('clearing code removes result and ignores eventual response', async () => {
  let resolve
  const f = fixture({ check: () => new Promise((done) => { resolve = done }) })
  f.change('TV-0012'); await f.runTimer(); f.change(''); resolve(result); await settle()
  assert.match(f.html(), /Chưa có bạn đọc được chọn/); assert.doesNotMatch(f.html(), /Nguyễn Văn An/)
  f.submit(); assert.match(f.html(), /Vui lòng nhập mã thẻ/)
})

test('same code can be retried after an error', async () => {
  let tries = 0
  const f = fixture({ check: async () => { if (++tries === 1) throw new Error('Lỗi kết nối'); return result } })
  f.change('TV-0012'); await f.runTimer(); assert.match(f.html(), /Lỗi kết nối/)
  f.submit(); await f.runTimer(); assert.match(f.html(), /Nguyễn Văn An/); assert.deepEqual(f.calls, ['TV-0012', 'TV-0012'])
})

test('reader role cannot access screen or call service', () => {
  const f = fixture({ role: 'READER' }); assert.match(f.html(), /Bạn không có quyền/); assert.deepEqual(f.calls, [])
})

test('oversize validation and unmount cancellation prevent lookup', async () => {
  const f = fixture(); f.change('x'.repeat(101)); assert.match(f.html(), /100 ký tự/)
  await f.runTimer(); assert.deepEqual(f.calls, [])
  f.change('TV-0012'); f.unmount(); await f.runTimer(); assert.deepEqual(f.calls, [])
})

test('feature registers direct loan route and staff sidebar roles', () => {
  const f = load('feature.tsx', {
    './DirectLoanPage': { __esModule: true, default: () => null },
    './LoanRejectionsPage': { __esModule: true, default: () => null },
    '../s3-01-loans/loanService': { loanRoles: ['LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN'] },
  }).default
  assert.equal(f.appRoutes[0].path, 'loans/direct'); assert.equal(f.navItems[0].label, 'Cho mượn tại quầy')
  assert.deepEqual(Array.from(f.navItems[0].roles), ['LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN'])
})

test('barcode draft mounts only for an identified reader and disappears on card change', async () => {
  const f = fixture(); assert.equal(f.panel(), null)
  f.change('TV-0012'); await f.runTimer(); assert.equal(f.panel().props.reader.cardNumber, 'TV-0012')
  const oldKey = f.panel().key
  f.submit(); assert.equal(f.panel(), null); await f.runTimer()
  assert.notEqual(f.panel().key, oldKey)
  f.change('OTHER'); assert.equal(f.panel(), null)
})


test('S3-03.1 renders exact X/Y for empty, below, equal and above quota', async () => {
  for (const borrowedBooks of [0, 2, 5, 7]) {
    const eligible = borrowedBooks < 5
    const f = fixture({ check: async () => ({ ...result, borrowedBooks, eligible,
      remainingBooks: Math.max(0, 5 - borrowedBooks),
      reasonCode: eligible ? 'ELIGIBLE' : 'LOAN_LIMIT_REACHED',
      message: `Bạn đọc đang mượn ${borrowedBooks}/5 sách.` }) })
    f.change('TV-0012'); await f.runTimer()
    assert.ok(f.html().includes(`>${borrowedBooks}/5</dd>`))
    assert.equal(f.panel().props.reader.eligible, eligible)
  }
})

test('S3-03.3 renders formatted VND debt and blocks draft until the reader is rechecked after payment', async () => {
  let unpaid = true
  const f = fixture({ check: async () => unpaid
    ? { ...result, eligible: false, remainingBooks: 0, reasonCode: 'LOAN_UNPAID_FEES',
      blockReasons: [{ code: 'LOAN_UNPAID_FEES', message: 'Bạn đọc còn nợ phí chưa thanh toán: 150.500 ₫.' }],
      message: 'Bạn đọc còn nợ phí chưa thanh toán: 150.500 ₫.' }
    : result })
  f.change('TV-0012'); await f.runTimer()
  assert.match(f.html(), /Không đủ điều kiện mượn/)
  assert.match(f.html(), /150.500 ₫/)
  assert.equal(f.panel().props.reader.eligible, false)
  unpaid = false
  f.submit(); await f.runTimer()
  assert.match(f.html(), /Đủ điều kiện mượn/)
  assert.equal(f.panel().props.reader.eligible, true)
})
