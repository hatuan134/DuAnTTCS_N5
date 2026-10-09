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
  const slots = [], effects = [], calls = [], confirmations = [], focusEvents = []
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
  dependencies['./returnService'] = { ...serviceModule, returnService: {
    lookup: (...args) => { calls.push(args); return lookup(...args) },
    confirm: (...args) => { confirmations.push(args); return confirm(...args) },
  } }
  const Page = evaluate('../src/features/s3-07-returns/ReceiveReturnPage.tsx', dependencies, { window: { confirm: () => accept } }).default
  function render() {
    cursor = 0; tree = Page()
    const form = find('form')
    if (form?.ref) form.ref.current = { querySelector: () => ({
      focus() { focusEvents.push({ action: 'focus', disabled: find('Input').props.disabled }) },
      select() { focusEvents.push({ action: 'select' }) },
    }) }
    for (const effect of effects.splice(0)) effect()
    return tree
  }
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
    calls, confirmations, focusEvents, render, text: () => text(tree).replace(/\s+/g, ' ').trim(), find,
    rows: () => nodes(tree).filter(node => node.props?.['data-return-result']),
    rowText: row => text(row),
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

for (const role of ['LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN']) test(`${role}: returned copy shows queue owner, pickup deadline and persistent hold details`, async () => {
  const response = returnedItem({
    message: 'Nhận trả sách thành công. Bản sao được giữ cho Bạn đọc Bình, đơn #30 đang Chờ nhận.',
    copyStatus: 'HELD', nextReservationId: 30, nextReaderName: 'Bạn đọc Bình',
    holdStartedAt: '2026-10-09T01:00:00+07:00', pickupDeadline: '2026-10-12T17:00:00+07:00',
  })
  const f = fixture(role, undefined, async () => response)
  await lookup(f); f.confirm(); await flush(); f.render()
  for (const value of ['Đang giữ cho đặt trước', 'Bạn đọc Bình', 'Đơn #', '30', 'Chờ nhận',
    'Bắt đầu giữ bản sao', response.holdStartedAt, 'Hạn cuối đến nhận', response.pickupDeadline]) assert.ok(f.text().includes(value), value)
  assert.ok(!f.text().includes('Sẵn sàng'))
  assert.equal(f.confirmButton(), undefined)
  assert.equal(f.find('FeedbackAlert').props.message, response.message)
  f.dismiss()
  assert.equal(f.find('FeedbackAlert'), undefined)
  assert.ok(f.text().includes('Bạn đọc Bình')); assert.ok(f.text().includes(response.pickupDeadline))
})

test('return without an eligible waiter does not show empty reservation details', async () => {
  const f = fixture('LIBRARIAN', undefined, async () => returnedItem({
    nextReservationId: null, nextReaderName: null, holdStartedAt: null, pickupDeadline: null,
  }))
  await lookup(f); f.confirm(); await flush(); f.render()
  assert.ok(f.text().includes('Sẵn sàng'))
  assert.ok(!f.text().includes('Bạn đọc được giữ sách'))
  assert.ok(!f.text().includes('Bắt đầu giữ bản sao'))
})

// S3-07.4: each barcode is independent; rows and counts belong to the open page.
async function receive(f, code) {
  f.input(code); f.submit(); await flush(); f.render()
  f.confirm(); await flush(); f.render()
}
function sequentialFixture(readers = ['Nguyễn An', 'Nguyễn An'], options = {}) {
  const previews = new Map(readers.map((readerName, i) => {
    const code = `LIB-${i + 1}`
    return [code, openItem({ barcode: code, copyId: i + 1, itemId: i + 11,
      loanId: i + 21, loanNumber: `PM-${i + 21}`, readerId: i + 31, readerName,
      bookTitle: `Sách ${i + 1}`, dueAt: '2026-10-08T17:00:00Z' })]
  }))
  return fixture(options.role ?? 'LIBRARIAN', async code => {
    const preview = previews.get(code)
    if (!preview) throw { response: { status: 404, data: { message: 'Mã vạch không tồn tại.' } } }
    return preview
  }, async (code, itemId) => {
    const preview = previews.get(code)
    return returnedItem({ barcode: code, itemId, copyId: preview.copyId,
      bookTitle: preview.bookTitle, loanId: preview.loanId, loanNumber: preview.loanNumber,
      returnedAt: '2026-10-10T00:01:00+07:00', ...options.returned })
  })
}
for (const readers of [['Nguyễn An', 'Nguyễn An'], ['Nguyễn An', 'Trần Bình']]) {
  test(`two consecutive returns preserve independent borrower rows: ${readers.join(', ')}`, async () => {
    const f = sequentialFixture(readers)
    await receive(f, 'LIB-1')
    assert.equal(f.find('Input').props.value, '')
    assert.equal(f.find('Input').props.disabled, false)
    assert.equal(f.confirmButton(), undefined)
    await receive(f, 'LIB-2')
    assert.equal(f.rows().length, 2)
    assert.deepEqual(f.confirmations, [['LIB-1', 11], ['LIB-2', 12]])
    for (const [i, row] of f.rows().entries()) {
      const value = f.rowText(row)
      for (const expected of [`LIB-${i + 1}`, `Sách ${i + 1}`, readers[i], '2026-10-08T17:00:00Z',
        '2026-10-10T00:01:00+07:00', 'Số ngày trễ', '1', 'Sẵn sàng']) assert.ok(value.includes(expected), expected)
    }
    assert.ok(f.text().includes('Đã nhận thành công: 2 cuốn'))
    f.dismiss(); assert.equal(f.rows().length, 2)
  })
}
test('an invalid barcode between two valid copies leaves successes intact and does not count as received', async () => {
  const f = sequentialFixture(['Nguyễn An', 'Trần Bình'])
  await receive(f, 'LIB-1')
  const first = f.rowText(f.rows()[0])
  f.input('INVALID'); f.submit(); await flush(); f.render()
  assert.equal(f.find('FeedbackAlert').props.tone, 'error')
  assert.equal(f.rows().length, 2)
  assert.equal(f.rowText(f.rows()[0]), first)
  assert.ok(f.rowText(f.rows()[1]).includes('INVALID'))
  assert.ok(f.rowText(f.rows()[1]).includes('Chưa ghi nhận thành công'))
  assert.ok(!f.rowText(f.rows()[1]).includes('Ngày trả thực tế'))
  assert.ok(f.text().includes('Đã nhận thành công: 1 cuốn'))
  f.dismiss(); assert.equal(f.rows().length, 2)
  await receive(f, 'LIB-2')
  assert.equal(f.rows().length, 3)
  assert.equal(f.rowText(f.rows()[0]), first)
  assert.ok(f.text().includes('Đã nhận thành công: 2 cuốn'))
})
test('a previously received barcode with spaces is rejected before lookup or write; no duplicate row or count', async () => {
  const f = sequentialFixture()
  await receive(f, 'LIB-1')
  f.input('  LIB-1  '); f.submit(); await flush(); f.render(); f.confirm()
  assert.equal(f.calls.length, 1); assert.equal(f.confirmations.length, 1)
  assert.equal(f.rows().length, 1)
  assert.equal(f.find('FeedbackAlert').props.tone, 'warning')
  assert.ok(f.text().includes('Đã nhận thành công: 1 cuốn'))
  await receive(f, 'LIB-2'); assert.equal(f.rows().length, 2)
})
test('canonical copy identity guards against a second barcode resolving to the same returned copy', async () => {
  const f = fixture('LIBRARIAN', async code => openItem({ barcode: code }), async () => returnedItem())
  await receive(f, 'LIB-001')
  f.input('ALIAS'); f.submit(); await flush(); f.render(); f.confirm()
  assert.equal(f.confirmations.length, 1); assert.equal(f.rows().length, 1)
  assert.equal(f.confirmButton(), undefined)
  assert.equal(f.find('FeedbackAlert').props.tone, 'warning')
})
test('retrying a rolled-back return replaces its error row and preserves other successful copies', async () => {
  let fail = true
  const f = fixture('LIBRARIAN', async code => openItem({ barcode: code, copyId: code === 'A' ? 1 : 2,
    itemId: code === 'A' ? 11 : 12, bookTitle: `Sách ${code}` }), async (code, itemId) => {
    if (code === 'B' && fail) { fail = false; throw { response: { status: 500, data: { message: 'Đã rollback.' } } } }
    return returnedItem({ barcode: code, itemId, copyId: code === 'A' ? 1 : 2, bookTitle: `Sách ${code}` })
  })
  await receive(f, 'A'); const first = f.rowText(f.rows()[0])
  await receive(f, 'B')
  assert.ok(f.confirmButton()); assert.equal(f.rows()[1].props['data-return-result'], 'ERROR')
  assert.ok(f.text().includes('Đã nhận thành công: 1 cuốn'))
  f.confirm(); await flush(); f.render()
  assert.equal(f.rows().length, 2); assert.equal(f.rows()[1].props['data-return-result'], 'SUCCESS')
  assert.equal(f.rowText(f.rows()[0]), first)
  assert.ok(f.text().includes('Đã nhận thành công: 2 cuốn'))
})
test('a repeated invalid barcode updates its own result and retains the original success', async () => {
  const f = sequentialFixture()
  await receive(f, 'LIB-1')
  for (let i = 0; i < 2; i++) { f.input('INVALID'); f.submit(); await flush(); f.render() }
  assert.equal(f.rows().length, 2); assert.ok(f.text().includes('Đã nhận thành công: 1 cuốn'))
})
test('changing input while a lookup is pending discards a late response without deleting session rows', async () => {
  let resolve
  const f = fixture('LIBRARIAN', code => code === 'LATE' ? new Promise(r => { resolve = r }) : Promise.resolve(openItem()),
    async () => returnedItem())
  await receive(f, 'LIB-001')
  f.input('LATE'); f.submit(); const signal = f.calls.at(-1)[1]
  f.input('NEXT'); assert.equal(signal.aborted, true)
  resolve(openItem({ barcode: 'LATE', readerName: 'Dữ liệu lỗi thời' })); await flush(); f.render()
  assert.equal(f.rows().length, 1); assert.ok(!f.text().includes('Dữ liệu lỗi thời'))
  assert.ok(f.text().includes('Đã nhận thành công: 1 cuốn'))
})
test('each completed copy retains its own queue outcome when the next copy becomes available', async () => {
  const f = fixture('LIBRARIAN', async code => openItem({ barcode: code, copyId: code === 'A' ? 1 : 2,
    itemId: code === 'A' ? 11 : 12 }), async (code, itemId) => returnedItem({ barcode: code, itemId,
      copyId: code === 'A' ? 1 : 2, copyStatus: code === 'A' ? 'HELD' : 'AVAILABLE',
      nextReservationId: code === 'A' ? 30 : null, nextReaderName: code === 'A' ? 'Bạn đọc chờ' : null,
      holdStartedAt: code === 'A' ? '2026-10-09T01:00:00+07:00' : null,
      pickupDeadline: code === 'A' ? '2026-10-12T17:00:00+07:00' : null }))
  await receive(f, 'A'); await receive(f, 'B')
  assert.ok(f.rowText(f.rows()[0]).includes('Đang giữ cho đặt trước'))
  assert.ok(f.rowText(f.rows()[0]).includes('Bạn đọc chờ'))
  assert.ok(f.rowText(f.rows()[1]).includes('Sẵn sàng'))
  assert.ok(!f.rowText(f.rows()[1]).includes('Bạn đọc chờ'))
})
test('newly opened page starts a new empty session', async () => {
  const first = sequentialFixture(); await receive(first, 'LIB-1'); first.unmount()
  const next = sequentialFixture(); assert.equal(next.rows().length, 0)
  assert.ok(next.text().includes('Đã nhận thành công: 0 cuốn'))
})
test('return day is derived from server time in Vietnam, including a midnight after preview', () => {
  const days = serviceModule.returnOverdueDays
  assert.equal(days('2026-10-08T17:00:00Z', '2026-10-09T16:59:59Z'), 0)
  assert.equal(days('2026-10-08T17:00:00Z', '2026-10-09T17:00:00Z'), 1)
  assert.equal(days('2026-10-09T23:00:00+07:00', '2026-10-09T23:59:59+07:00'), 0)
  assert.equal(days('2026-10-10T17:00:00+07:00', '2026-10-09T23:59:59+07:00'), 0)
  assert.equal(days(null, '2026-10-09T17:00:00Z'), null)
  assert.equal(days('invalid', '2026-10-09T17:00:00Z'), null)
  assert.equal(days('2026-10-09T17:00:00Z', 'invalid'), null)
})
test('returned row snapshots metadata instead of holding references to a mutable preview', () => {
  const preview = openItem(), result = returnedItem()
  const row = serviceModule.returnSessionSuccess(preview, result)
  preview.readerName = 'Khác'; preview.dueAt = null; result.bookTitle = 'Khác'
  assert.equal(row.readerName, 'Nguyễn An'); assert.equal(row.dueAt, '2026-10-09T10:00:00Z')
  assert.equal(row.bookTitle, 'Mắt biếc')
})


test('success returns focus to the enabled barcode input; invalid input remains selected for replacement', async () => {
  const f = sequentialFixture()
  await receive(f, 'LIB-1')
  assert.equal(f.focusEvents.at(-1).action, 'focus')
  assert.equal(f.focusEvents.at(-1).disabled, false)
  assert.equal(f.find('Input').props.value, '')
  f.input('INVALID'); f.submit(); await flush(); f.render()
  assert.equal(f.focusEvents.at(-1).action, 'select')
  assert.ok(f.focusEvents.every(event => event.disabled !== true))
})

for (const role of ['LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN']) test(`${role}: four books of one reader stay in the same session`, async () => {
  const f = sequentialFixture(Array(4).fill('Nguyễn An'), { role })
  for (let i = 1; i <= 4; i++) await receive(f, `LIB-${i}`)
  assert.equal(f.rows().length, 4); assert.equal(f.confirmations.length, 4)
  assert.ok(f.rows().every(row => f.rowText(row).includes('Nguyễn An')))
  assert.ok(f.text().includes('Đã nhận thành công: 4 cuốn'))
})
for (const failure of [{ response: { status: 409, data: { message: 'Phiếu đã đổi.' } } }, new Error('timeout')]) {
  test(`uncertain or conflicting second return keeps earlier success: ${failure.response?.status ?? 'timeout'}`, async () => {
    const f = fixture('LIBRARIAN', async code => openItem({ barcode: code, copyId: code === 'A' ? 1 : 2,
      itemId: code === 'A' ? 11 : 12 }), async (code, itemId) => {
        if (code === 'B') throw failure
        return returnedItem({ barcode: code, itemId, copyId: 1 })
      })
    await receive(f, 'A'); const first = f.rowText(f.rows()[0])
    await receive(f, 'B')
    assert.equal(f.rowText(f.rows()[0]), first); assert.equal(f.rows()[1].props['data-return-result'], 'ERROR')
    assert.equal(f.confirmButton(), undefined); assert.ok(f.text().includes('Đã nhận thành công: 1 cuốn'))
    assert.equal(f.find('Input').props.disabled, false)
  })
}
