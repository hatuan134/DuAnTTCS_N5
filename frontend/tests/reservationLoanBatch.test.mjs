import { createS4Require } from './s4TestImports.mjs'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import { createRequire } from 'node:module'
import vm from 'node:vm'
import ts from 'typescript'
const require = createS4Require(import.meta.url), react = require('react')
const settle = () => new Promise(resolve => setImmediate(resolve))
const Feedback = () => null
const Stub = () => null

function load(path, imports, globals = {}) {
  const context = { exports: {}, require: name => imports[name] ?? require(name), ...globals }
  vm.runInNewContext(ts.transpileModule(readFileSync(new URL(path, import.meta.url), 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX },
  }).outputText, context)
  return context.exports
}
function fixture(path, imports, props = {}, globals = {}) {
  const slots = [], pending = []; let cursor = 0, tree
  const hooks = { ...react,
    useRef(value) { const i = cursor++; return slots[i] ??= { current: value } },
    useState(value) { const i = cursor++; if (!(i in slots)) slots[i] = typeof value === 'function' ? value() : value
      return [slots[i], next => { slots[i] = typeof next === 'function' ? next(slots[i]) : next }] },
    useEffect(fn, deps) { const i = cursor++, old = slots[i]
      if (!old || deps.some((v, n) => !Object.is(v, old.deps[n]))) {
        slots[i] = { deps, cleanup: old?.cleanup }; pending.push(() => { old?.cleanup?.(); slots[i].cleanup = fn() })
      }
    },
  }
  const Component = load(path, { react: hooks,
    '../../components/ui/FeedbackAlert': { __esModule: true, default: Feedback },
    '../../components/ui/Button': { __esModule: true, default: Stub },
    '../s1-02-user-management/accountService': { getApiErrorMessage: e => e.message },
    '../../layouts/AuthLayout': { __esModule: true, default: Stub },
    '../../components/brand/LibraLogo': { __esModule: true, default: Stub },
    'react-router-dom': { Link: Stub, useSearchParams: () => [new URLSearchParams('token=demo-token&email=demo@example.invalid')] },
    ...imports,
  }, globals).default
  return {
    render() { cursor = 0; tree = Component(props); for (const fn of pending.splice(0)) fn(); return tree },
    tree: () => tree,
    unmount() { for (const slot of slots) slot?.cleanup?.() },
  }
}
function find(node, predicate) {
  if (!node || typeof node !== 'object') return undefined
  if (Array.isArray(node)) { for (const child of node) { const result = find(child, predicate); if (result) return result } return undefined }
  return predicate(node) ? node : find(node.props?.children, predicate)
}
function text(node) {
  if (node == null || typeof node === 'boolean') return ''
  if (Array.isArray(node)) return node.map(text).join(' ')
  return typeof node === 'object' ? text(node.props?.children) : String(node)
}
function verifyNotice(f) {
  const notice = find(f.tree(), n => n.type === Feedback)
  assert.ok(notice); assert.equal(notice.props.tone, 'success')
  let timer, delay
  const TimerFixture = fixture('../src/components/ui/FeedbackAlert.tsx', {}, notice.props, { window: {
    setTimeout(fn, ms) { timer = fn; delay = ms; return 1 }, clearTimeout() {},
  } })
  TimerFixture.render(); assert.equal(delay, 3000)
  assert.ok(find(f.render(), n => n.type === Feedback))
  timer(); f.render(); assert.equal(find(f.tree(), n => n.type === Feedback), undefined)
  TimerFixture.unmount()
}
const dates = { borrowDate: '2026-10-12', dueAt: '2026-10-26T17:00:00+07:00', loanDays: 14 }
const row = { id: 21, readerId: 12, readerName: 'Bạn đọc A', copyId: 31, barcode: 'LIB-31', bookTitle: 'Sách A',
  pickupDeadline: '2026-10-14T17:00:00+07:00', checkedAt: '2026-10-12T10:00:00+07:00' }

test('batch client sends one request with selected ids and existing confirmation fields', async () => {
  const calls = []
  const service = load('../src/features/s2-09-ready-pickup/pickupService.ts', {
    '../../core/api/apiClient': { apiClient: { async post(url, body) {
      calls.push({ url, body }); return { data: { id: 81, loanNumber: 'PM-ONE', message: 'Đã lập phiếu.',
        items: [{ id: 81, reservationId: 21 }, { id: 81, reservationId: 22 }, { id: 81, reservationId: 23 }] } }
    } } },
  }).pickupService
  const result = await service.createLoans([21, 22, 23], ' TV-12 ', dates, 'same-request')
  assert.equal(calls.length, 1); assert.equal(calls[0].url, '/reservations/loans')
  assert.deepEqual([...calls[0].body.reservationIds], [21, 22, 23])
  assert.equal(calls[0].body.confirmation.cardNumber, 'TV-12')
  assert.equal(calls[0].body.confirmation.expectedDueAt, dates.dueAt)
  assert.equal('readerId' in calls[0].body, false)
  assert.equal(result.items.length, 3); assert.equal(result.id, 81)
})

test('selection defaults to all eligible holds of the same reader and lets staff exclude a sibling', async () => {
  const changes = [], readiness = []
  const props = { reservation: row, disabled: false, selectedIds: [21, 22, 23],
    onChange: ids => changes.push([...ids]), onReady: value => readiness.push(value) }
  const f = fixture('../src/features/s2-09-ready-pickup/ReservationLoanSelection.tsx', {
    './pickupService': { formatPickupDate: v => v, pickupService: { list: async () => [
      row, { ...row, id: 22, copyId: 32 }, { ...row, id: 23, copyId: 33 },
      { ...row, id: 99, readerId: 99 },
    ] } },
  }, props)
  f.render(); await settle(); f.render()
  assert.deepEqual(changes[0], [21, 22, 23]); assert.deepEqual(readiness, [false, true])
  const sibling = find(f.tree(), n => n.type === 'input' && n.props['aria-label'].includes('#22:'))
  sibling.props.onChange({ target: { checked: false } }); assert.deepEqual(changes.at(-1), [21, 23])
  assert.equal(find(f.tree(), n => n.type === 'input' && n.props['aria-label'].includes('#21:')).props.disabled, true)
  f.unmount()
})

test('failed selection load blocks confirmation and has a temporary error plus recovery action', async () => {
  const readiness = []
  const f = fixture('../src/features/s2-09-ready-pickup/ReservationLoanSelection.tsx', {
    './pickupService': { formatPickupDate: v => v, pickupService: { list: async () => { throw new Error('Không tải được danh sách.') } } },
  }, { reservation: row, selectedIds: [21], disabled: false, onChange() {}, onReady: value => readiness.push(value) })
  f.render(); await settle(); f.render()
  assert.deepEqual(readiness, [false])
  const notice = find(f.tree(), n => n.type === Feedback); assert.equal(notice.props.tone, 'error')
  notice.props.onDismiss(); f.render()
  assert.equal(find(f.tree(), n => n.type === Feedback), undefined)
  assert.ok(text(f.tree()).includes('Cập nhật danh sách'))
  f.unmount()
})

test('old forgot-password success becomes green temporary feedback while email instructions stay', async () => {
  const f = fixture('../src/features/s1-07-password-reset/ForgotPasswordPage.tsx', {
    './passwordResetService': { passwordResetService: { requestForgotPassword: async () => ({ message: 'Đã gửi hướng dẫn.' }) } },
  })
  f.render(); await find(f.tree(), n => n.type === 'form').props.onSubmit({ preventDefault() {} }); f.render()
  verifyNotice(f); assert.ok(text(f.tree()).includes('Kiểm tra email của bạn')); f.unmount()
})

test('old initial-password success expires while the login link remains', async () => {
  const f = fixture('../src/features/s1-02-user-management/InitialPasswordPage.tsx', {
    './accountService': { validateInitialPasswordToken: async () => ({ valid: true }), setInitialPassword: async () => {}, getApiErrorMessage: e => e.message },
  })
  f.render(); await settle(); f.render()
  for (const id of ['initial-password', 'confirm-initial-password']) {
    find(f.tree(), n => n.props?.id === id).props.onChange({ target: { value: 'DemoPass123' } }); f.render()
  }
  await find(f.tree(), n => n.type === 'form').props.onSubmit({ preventDefault() {} }); f.render()
  verifyNotice(f); assert.ok(text(f.tree()).includes('Đến trang đăng nhập')); f.unmount()
})

test('old reset-password success expires while next-step content remains', async () => {
  const f = fixture('../src/features/s1-07-password-reset/ResetPasswordPage.tsx', {
    './passwordResetService': { passwordResetService: { validateToken: async () => ({ valid: true }), resetPassword: async () => {} } },
    '../../core/auth/authStorage': { clearAuthSession() {} },
  })
  f.render(); await settle(); f.render()
  for (const label of ['Mật khẩu mới', 'Nhập lại mật khẩu mới']) {
    const field = find(f.tree(), n => n.props?.label === label)
    assert.ok(field, label); field.props.onChange('DemoPass123'); f.render()
  }
  await find(f.tree(), n => n.type === 'form').props.onSubmit({ preventDefault() {} }); f.render()
  verifyNotice(f); assert.ok(text(f.tree()).includes('Đăng nhập bằng mật khẩu mới')); f.unmount()
})

test('old registration success expires while assigned reader code and pending status stay', async () => {
  const f = fixture('../src/features/s1-03-reader-registration/RegisterPage.tsx', {
    './readerService': { readerService: { register: async () => ({ fullName: 'Bạn đọc mẫu', email: 'demo@example.invalid', memberCode: 'BD000123' }) } },
    './passwordPolicy': { validateRegistrationPassword: () => '' },
  })
  f.render()
  for (const [id, value] of Object.entries({ 'register-fullName': 'Bạn đọc mẫu', 'register-email': 'demo@example.invalid',
    'register-dateOfBirth': '2005-01-01', 'register-password': 'DemoPass123', 'register-confirmPassword': 'DemoPass123' })) {
    find(f.tree(), n => n.props?.id === id).props.onChange({ target: { value } }); f.render()
  }
  await find(f.tree(), n => n.type === 'form').props.onSubmit({ preventDefault() {} }); f.render()
  verifyNotice(f); assert.ok(text(f.tree()).includes('BD000123')); assert.ok(text(f.tree()).includes('CHỜ THỦ THƯ DUYỆT')); f.unmount()
})
