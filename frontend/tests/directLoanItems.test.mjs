import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import { webcrypto } from 'node:crypto'
import vm from 'node:vm'
import ts from 'typescript'
import { createRequire } from 'node:module'
import { renderToStaticMarkup } from 'react-dom/server'
const require = createRequire(import.meta.url), react = require('react')
const root = new URL('../src/features/s3-02-direct-loans/', import.meta.url)
function load(file, imports = {}, extra = {}) {
  const source = readFileSync(new URL(file, root), 'utf8').replaceAll('import.meta.env.VITE_API_URL', 'undefined')
  const code = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX },
  }).outputText
  const context = { exports: {}, crypto: webcrypto, require: (name) => imports[name] ?? (name === '../../components/ui/FeedbackAlert' ? load('../../components/ui/FeedbackAlert.tsx', {}) : require(name)), ...extra }
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
const confirmed = (barcodes) => ({
  loan: { id: 80, loanNumber: 'PM-TEST', createdByName: 'Thủ thư', items: barcodes.map((barcode, i) => ({
    id: i + 1, barcode, bookTitle: `Sách ${barcode}`, borrowedAt: '2026-10-08T09:00:00+07:00', dueAt: '2026-10-22T23:59:59+07:00',
  })) }, reader: { ...result, borrowedBooks: 2 + barcodes.length, remainingBooks: 3 - barcodes.length },
  message: 'Đã ghi toàn bộ lượt mượn thành công.',
})
function fixture({ reader = result, preview = async (_card, code) => copy(code),
  confirm = async (_card, codes) => confirmed(codes), errorMessage = (e) => e.message } = {}) {
  const states = [], effects = [], timers = new Map(), calls = [], confirmations = [], locks = [], created = []
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

    '../s1-02-user-management/accountService': { getApiErrorMessage: errorMessage },
    '../s3-01-loans/loanService': { loanRoles: ['LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN'], formatLoanTimestamp: (date) => date?.slice(0, 10) },
    './directLoanService': { directLoanService: { previewItem(card, code, rows) { calls.push([card, code, rows]); return preview(card, code, rows) }, confirm(card, codes, id) { confirmations.push([card, codes, id]); return confirm(card, codes, id) } } },
  }
  for (const name of ['Button', 'Card', 'Input', 'PageHeader']) imports[`../../components/ui/${name}`] = load(`../../components/ui/${name}.tsx`)
  const Page = load('DirectLoanItemsPanel.tsx', imports, { window: {
    setTimeout(callback, delay) { timers.set(++timerId, { callback, delay }); return timerId },
    clearTimeout(id) { timers.delete(id) },
  } }).default
  function render() {
    cursor = 0; tree = Page({ reader, onCreated: (data) => created.push(data), onLockChange: (value) => locks.push(value) })
    for (const effect of effects.splice(0)) effect()
    cursor = 0; tree = Page({ reader, onCreated: (data) => created.push(data), onLockChange: (value) => locks.push(value) })
    return tree
  }
  render()
  return {
    calls, confirmations, locks, created,
    html: () => renderToStaticMarkup(render()),
    change(value) { find(tree, (n) => n.props?.id === 'direct-loan-barcode').props.onChange({ target: { value } }); render() },
    submit() { find(tree, (n) => n.type === 'form').props.onSubmit({ preventDefault() {} }); render() },
    async runTimer() { for (const [id, t] of Array.from(timers)) { timers.delete(id); t.callback() } await settle(); render() },
    remove(code) { find(tree, (n) => n.props?.['aria-label'] === `Xóa sách có mã vạch ${code}`).props.onClick(); render() },
    edit(code) { find(tree, (n) => n.props?.['aria-label'] === `Sửa mã vạch ${code}`).props.onClick(); render() },
    cancelEdit() { find(tree, (n) => n.props?.children === 'Hủy sửa').props.onClick(); render() },
    confirm() { find(tree, (n) => n.props?.children === 'Xác nhận lượt mượn').props.onClick(); render() },
    confirmDisabled: () => Boolean(find(tree, (n) => n.props?.children === 'Xác nhận lượt mượn').props.disabled),
    rowHtml(code) { render(); return renderToStaticMarkup(find(tree, (n) => n.type === 'tr' && find(n.props?.children, (child) => child.type === 'td' && child.props?.children === code))) },
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
test('failure stays on its own row and can be retried through edit', async () => {
  let fail = true
  const f = fixture({ preview: async (_card, code) => {
    if (code === 'BC-2' && fail) throw new Error('Không thể thêm sách: lượt mượn sẽ vượt giới hạn.')
    return copy(code)
  } })
  f.change('BC-1'); f.submit(); await settle(); f.change('BC-2'); f.submit(); await settle()
  assert.match(f.html(), /Không thể thêm sách/); assert.match(f.html(), /Sách BC-1/)
  assert.match(f.rowHtml('BC-2'), /Không thể thêm sách/); assert.match(f.html(), /value=""/);
  fail = false; f.edit('BC-2'); assert.match(f.html(), /value="BC-2"/); f.submit(); await settle()
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

test('unknown and unavailable rows keep their errors while later valid barcodes are accepted', async () => {
  const f = fixture({ preview: async (_card, code) => {
    if (code === 'UNKNOWN') throw new Error('Không tìm thấy sách theo mã vạch đã nhập.')
    if (code === 'BORROWED') throw new Error('Bản sao không ở trạng thái Sẵn sàng. Trạng thái hiện tại: Đang mượn.')
    if (code === 'REPAIR') throw new Error('Bản sao không ở trạng thái Sẵn sàng. Trạng thái hiện tại: Đang sửa chữa.')
    return copy(code)
  } })
  for (const code of ['BC-1', 'UNKNOWN', 'BORROWED', 'REPAIR', 'BC-2', 'BC-3']) {
    f.change(code); f.submit(); await settle(); f.html()
  }
  assert.equal((f.html().match(/<tr/g) ?? []).length, 7)
  assert.match(f.rowHtml('UNKNOWN'), /Không tìm thấy sách/)
  assert.match(f.rowHtml('BORROWED'), /Trạng thái hiện tại: Đang mượn/)
  assert.match(f.rowHtml('REPAIR'), /Trạng thái hiện tại: Đang sửa chữa/)
  for (const code of ['BC-1', 'BC-2', 'BC-3']) {
    assert.match(f.rowHtml(code), /Hợp lệ · Sẵn sàng/)
    assert.doesNotMatch(f.rowHtml(code), /role="alert"/)
  }
  assert.match(f.html(), /Dự kiến mượn: 3 \/ 3 sách/)
  assert.deepEqual(f.calls.map((x) => Array.from(x[2])), [[], ['BC-1'], ['BC-1'], ['BC-1'], ['BC-1'], ['BC-1', 'BC-2']])
  assert.ok(f.confirmDisabled()); f.confirm()
  assert.doesNotMatch(f.html(), /Đã xác nhận danh sách/)
  for (const code of ['UNKNOWN', 'BORROWED', 'REPAIR']) f.remove(code)
  assert.equal(f.confirmDisabled(), false); f.confirm()
  await settle(); assert.match(f.html(), /Đã ghi toàn bộ 3 sách/)
  assert.equal(f.calls.length, 6)
})

test('editing a failed middle row replaces it in place and preserves valid rows on both sides', async () => {
  const f = fixture({ preview: async (_card, code) => {
    if (code === 'UNKNOWN') throw new Error('Mã vạch không tồn tại')
    return copy(code)
  } })
  for (const code of ['BC-1', 'UNKNOWN', 'BC-3']) { f.change(code); f.submit(); await settle(); f.html() }
  f.edit('UNKNOWN'); f.change('BC-2'); f.submit(); await settle()
  const page = f.html()
  assert.equal((page.match(/<tr/g) ?? []).length, 4)
  const html = page.split('<tbody')[1].split('</tbody>')[0]
  assert.ok(html.indexOf('Sách BC-1') < html.indexOf('Sách BC-2'))
  assert.ok(html.indexOf('Sách BC-2') < html.indexOf('Sách BC-3'))
  assert.doesNotMatch(html, /Mã vạch không tồn tại/)
  assert.deepEqual(Array.from(f.calls.at(-1)[2]), ['BC-1', 'BC-3'])
  assert.equal(f.confirmDisabled(), false)
})

test('failed recheck, duplicate edit and cancel leave original errors and valid rows intact', async () => {
  const f = fixture({ preview: async (_card, code) => {
    if (code.startsWith('BAD')) throw new Error('Không tìm thấy sách')
    return copy(code)
  } })
  for (const code of ['BC-1', 'BAD']) { f.change(code); f.submit(); await settle(); f.html() }
  f.edit('BAD'); f.change('BC-1'); f.submit(); await settle()
  assert.match(f.html(), /Mã vạch này đã có/); assert.equal(f.calls.length, 2)
  assert.match(f.rowHtml('BAD'), /Không tìm thấy sách/)
  f.change('BAD-2'); f.submit(); await settle(); f.html()
  assert.match(f.rowHtml('BAD-2'), /Không tìm thấy sách/); assert.match(f.rowHtml('BC-1'), /Hợp lệ/)
  f.edit('BAD-2'); f.cancelEdit(); assert.ok(f.confirmDisabled())
  assert.match(f.rowHtml('BAD-2'), /Không tìm thấy sách/)
  f.change('BC-2'); f.submit(); await settle(); assert.match(f.rowHtml('BC-2'), /Hợp lệ/)
  f.remove('BAD-2'); assert.equal(f.confirmDisabled(), false)
})

test('confirmation stays disabled for empty, ineligible, checking or unsubmitted drafts', async () => {
  const empty = fixture(); assert.ok(empty.confirmDisabled())
  const inactive = fixture({ reader: { ...result, eligible: false } }); assert.ok(inactive.confirmDisabled())
  let resolve
  const f = fixture({ preview: () => new Promise((done) => { resolve = done }) })
  f.change('BC-1'); f.submit(); assert.ok(f.confirmDisabled())
  assert.match(f.rowHtml('BC-1'), /Đang kiểm tra/)
  resolve(copy('BC-1')); await settle(); f.html(); assert.equal(f.confirmDisabled(), false)
  f.change('BC-2'); assert.ok(f.confirmDisabled()); f.confirm()
  assert.doesNotMatch(f.html(), /Đã xác nhận danh sách/)
  f.change(''); assert.equal(f.confirmDisabled(), false)
})

// S3-02.4 exercises the existing UI with the real shared API-error formatter.
const { getApiErrorMessage } = load('../s1-02-user-management/accountService.ts', {
  '../../core/api/apiClient': { apiClient: {} },
})
function heldError(ownReservation = false, name = 'Trần Thị Bình', id = 42) {
  return { isAxiosError: true, response: { status: 409, data: {
    code: ownReservation ? 'LOAN_DRAFT_COPY_HELD_FOR_CURRENT_READER' : 'LOAN_DRAFT_COPY_HELD_FOR_OTHER_READER',
    message: ownReservation
      ? `Bản sao đang được đặt giữ cho chính bạn đọc ${name} (đơn đặt giữ #${id}). Vui lòng lập phiếu mượn từ đơn này tại mục Sách đang chờ nhận.`
      : `Bản sao đang được đặt giữ cho bạn đọc ${name} (đơn đặt giữ #${id}). Không thể thêm vào lượt mượn của bạn đọc khác.`,
    details: { reservationId: id, readerName: name, ownReservation },
  } } }
}

test('S3-02.4 hold error names the correct owner and order only on its barcode row and blocks confirmation', async () => {
  const f = fixture({ errorMessage: getApiErrorMessage, preview: async (_card, code) => {
    if (code === 'HELD-42') throw heldError()
    return copy(code)
  } })
  for (const code of ['BC-1', 'HELD-42', 'BC-3']) { f.change(code); f.submit(); await settle(); f.html() }
  assert.match(f.rowHtml('HELD-42'), /Trần Thị Bình.*đơn đặt giữ #42/)
  assert.match(f.rowHtml('HELD-42'), /role="alert"/)
  for (const code of ['BC-1', 'BC-3']) {
    assert.match(f.rowHtml(code), /Hợp lệ · Sẵn sàng/)
    assert.doesNotMatch(f.rowHtml(code), /Trần Thị Bình|đơn đặt giữ/)
  }
  assert.match(f.html(), /Dự kiến mượn: 2 \/ 3 sách/)
  assert.deepEqual(Array.from(f.calls.at(-1)[2]), ['BC-1'])
  assert.ok(f.confirmDisabled()); f.confirm(); assert.doesNotMatch(f.html(), /Đã xác nhận danh sách/)
  f.remove('HELD-42'); assert.equal(f.confirmDisabled(), false); f.confirm()
  await settle(); assert.match(f.html(), /Đã ghi toàn bộ 2 sách/)
})

test('S3-02.4 own hold explains the reservation flow and keeps the row rejected', async () => {
  const f = fixture({ errorMessage: getApiErrorMessage, preview: async () => { throw heldError(true, 'Nguyễn Văn An') } })
  f.change('OWN-42'); f.submit(); await settle()
  assert.match(f.rowHtml('OWN-42'), /chính bạn đọc Nguyễn Văn An.*đơn đặt giữ #42/)
  assert.match(f.rowHtml('OWN-42'), /Sách đang chờ nhận/)
  assert.match(f.html(), /Dự kiến mượn: 0 \/ 3 sách/)
  assert.ok(f.confirmDisabled()); assert.equal(f.addDisabled(), false)
})

test('S3-02.4 two held barcodes show their own order and owner without mixing messages', async () => {
  const f = fixture({ errorMessage: getApiErrorMessage, preview: async (_card, code) => {
    throw code === 'HELD-42' ? heldError() : heldError(false, 'Lê Minh Long', 73)
  } })
  for (const code of ['HELD-42', 'HELD-73']) { f.change(code); f.submit(); await settle(); f.html() }
  assert.match(f.rowHtml('HELD-42'), /Trần Thị Bình.*#42/)
  assert.doesNotMatch(f.rowHtml('HELD-42'), /Lê Minh Long|#73/)
  assert.match(f.rowHtml('HELD-73'), /Lê Minh Long.*#73/)
  assert.doesNotMatch(f.rowHtml('HELD-73'), /Trần Thị Bình|#42/)
  assert.ok(f.confirmDisabled())
})

test('S3-02.4 rechecking a released hold replaces only the rejected row', async () => {
  let held = true
  const f = fixture({ errorMessage: getApiErrorMessage, preview: async (_card, code) => {
    if (code === 'HELD-42' && held) throw heldError()
    return copy(code)
  } })
  for (const code of ['BC-1', 'HELD-42', 'BC-3']) { f.change(code); f.submit(); await settle(); f.html() }
  held = false; f.edit('HELD-42'); f.submit(); await settle()
  assert.match(f.rowHtml('HELD-42'), /Hợp lệ · Sẵn sàng/)
  assert.doesNotMatch(f.rowHtml('HELD-42'), /Trần Thị Bình|#42/)
  assert.match(f.rowHtml('BC-1'), /Hợp lệ/); assert.match(f.rowHtml('BC-3'), /Hợp lệ/)
  assert.match(f.html(), /Dự kiến mượn: 3 \/ 3 sách/)
  assert.equal(f.confirmDisabled(), false)
})

test('S3-02.5 one confirmation sends all valid barcodes and shows one completed loan', async () => {
  const f = fixture()
  for (const code of ['BC-1', 'BC-2']) { f.change(code); f.submit(); await settle(); f.html() }
  f.confirm(); await settle()
  assert.equal(f.confirmations.length, 1)
  assert.equal(f.confirmations[0][0], 'TV-0012')
  assert.deepEqual(Array.from(f.confirmations[0][1]), ['BC-1', 'BC-2'])
  assert.match(f.confirmations[0][2], /^[0-9a-f-]{36}$/)
  assert.match(f.html(), /Đã ghi toàn bộ 2 sách/)
  assert.match(f.html(), /PM-TEST/); assert.match(f.html(), /Thủ thư/)
  assert.match(f.html(), /2026-10-22/)
  assert.equal((f.html().match(/Đang mượn/g) ?? []).length, 2)
  assert.doesNotMatch(f.html(), /Xác nhận lượt mượn/)
  assert.equal(f.created.length, 1); assert.equal(f.created[0].reader.remainingBooks, 1)
  assert.deepEqual(f.locks, [true, false])
})

test('S3-02.5 immediate repeated clicks send only one POST and freeze barcode actions', async () => {
  let resolve
  const f = fixture({ confirm: () => new Promise((done) => { resolve = done }) })
  f.change('BC-1'); f.submit(); await settle(); f.html()
  f.confirm(); f.confirm(); f.remove('BC-1')
  assert.equal(f.confirmations.length, 1)
  assert.ok(f.confirmDisabled()); assert.ok(f.addDisabled())
  assert.match(f.html(), /Đang kiểm tra lại thẻ/)
  assert.match(f.html(), /Sách BC-1/)
  resolve(confirmed(['BC-1'])); await settle(); assert.match(f.html(), /Đã ghi toàn bộ 1 sách/)
})

test('S3-02.5 a lost response preserves immutable draft and UUID on retry', async () => {
  let attempts = 0
  const f = fixture({ confirm: async (_card, codes) => {
    if (++attempts === 1) throw new Error('network timeout')
    return confirmed(codes)
  } })
  f.change('BC-1'); f.submit(); await settle(); f.html(); f.confirm(); await settle()
  assert.match(f.html(), /Chưa xác định được kết quả/)
  assert.equal(f.created.length, 0); assert.equal(f.locks.at(-1), true)
  f.remove('BC-1'); assert.match(f.html(), /Sách BC-1/)
  f.confirm(); await settle()
  assert.equal(f.confirmations.length, 2)
  assert.equal(f.confirmations[0][2], f.confirmations[1][2])
  assert.deepEqual(Array.from(f.confirmations[1][1]), ['BC-1'])
  assert.match(f.html(), /Đã ghi toàn bộ 1 sách/)
  assert.equal(f.locks.at(-1), false)
})

test('S3-02.5 business rejection keeps draft, explains failure and permits corrected submission', async () => {
  let fail = true
  const f = fixture({ confirm: async (_card, codes) => {
    if (fail) throw { response: { status: 409 }, message: 'Bản sao BC-2 không còn Sẵn sàng.' }
    return confirmed(codes)
  } })
  for (const code of ['BC-1', 'BC-2']) { f.change(code); f.submit(); await settle(); f.html() }
  f.confirm(); await settle()
  assert.match(f.html(), /Không thể ghi trọn vẹn lượt mượn.*BC-2/)
  assert.equal(f.created.length, 0); assert.equal(f.locks.at(-1), false)
  const previousKey = f.confirmations[0][2]
  f.remove('BC-2'); fail = false; f.confirm(); await settle()
  assert.notEqual(f.confirmations[1][2], previousKey)
  assert.deepEqual(Array.from(f.confirmations[1][1]), ['BC-1'])
  assert.match(f.html(), /Đã ghi toàn bộ 1 sách/)
})

test('S3-02.5 invalid drafts do not call confirmation API', async () => {
  const f = fixture({ preview: async () => { throw new Error('Không tìm thấy sách') } })
  f.confirm(); f.change('UNKNOWN'); f.submit(); await settle(); f.html(); f.confirm()
  assert.deepEqual(f.confirmations, [])
})

test('S3-02.5 a confirmed rollback reports failure and unlocks the unchanged draft', async () => {
  const f = fixture({ confirm: async () => { throw { response: { status: 500, data: { code: 'DIRECT_LOAN_SAVE_FAILED' } },
    message: 'Toàn bộ thay đổi của lượt đã được hủy.' } } })
  f.change('BC-1'); f.submit(); await settle(); f.html(); f.confirm(); await settle()
  assert.match(f.html(), /Không thể ghi trọn vẹn lượt mượn.*đã được hủy/)
  assert.equal(f.locks.at(-1), false); assert.equal(f.created.length, 0)
  assert.match(f.html(), /Sách BC-1/); assert.equal(f.confirmDisabled(), false)
})

test('S3-02.5 confirmation API uses the shared authenticated client and normalizes payload', async () => {
  const calls = []
  const { directLoanService } = load('directLoanService.ts', { '../../core/api/apiClient': { apiClient: {
    async post(url, body) { calls.push([url, body]); return { data: confirmed(body.barcodes) } },
  } } })
  const id = webcrypto.randomUUID()
  const response = await directLoanService.confirm(' TV-0012 ', [' BC-1 ', 'BC-2'], id)
  assert.equal(response.loan.items.length, 2)
  assert.equal(calls[0][0], '/loans/direct')
  assert.equal(JSON.stringify(calls[0][1]), JSON.stringify({ requestId: id, cardNumber: 'TV-0012', barcodes: ['BC-1', 'BC-2'] }))
})
