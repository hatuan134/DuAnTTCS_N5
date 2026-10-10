import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { test } from 'node:test'
import vm from 'node:vm'
import ts from 'typescript'

const require = createRequire(import.meta.url)
const react = require('react')

function compile(path, imports, extra = {}) {
  const context = { exports: {}, require: name => imports[name] ?? require(name), ...extra }
  const js = ts.transpileModule(readFileSync(new URL(path, import.meta.url), 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX },
  }).outputText
  vm.runInNewContext(js, context)
  return context.exports.default
}

function hookHarness() {
  const slots = [], pending = []
  let cursor = 0
  const hooks = {
    ...react,
    useState(initial) {
      const slot = cursor++
      if (!(slot in slots)) slots[slot] = typeof initial === 'function' ? initial() : initial
      return [slots[slot], value => {
        slots[slot] = typeof value === 'function' ? value(slots[slot]) : value
      }]
    },
    useRef(value) { const slot = cursor++; return slots[slot] ??= { current: value } },
    useMemo(callback) { cursor++; return callback() },
    useEffect(callback, deps) {
      const slot = cursor++, old = slots[slot]
      if (!old || deps.some((value, index) => !Object.is(value, old.deps[index]))) {
        slots[slot] = { deps, cleanup: old?.cleanup }
        pending.push(() => { old?.cleanup?.(); slots[slot].cleanup = callback() })
      }
    },
  }
  return {
    hooks,
    render(callback) {
      cursor = 0
      const tree = callback()
      for (const effect of pending.splice(0)) effect()
      return tree
    },
    unmount() { for (const state of slots) state?.cleanup?.() },
  }
}

function find(node, predicate) {
  if (node == null || typeof node !== 'object') return undefined
  if (Array.isArray(node)) {
    for (const item of node) { const result = find(item, predicate); if (result) return result }
    return undefined
  }
  return predicate(node) ? node : find(node.props?.children, predicate)
}
function text(node) {
  if (node == null || typeof node === 'boolean') return ''
  if (Array.isArray(node)) return node.map(text).join(' ')
  return typeof node === 'object' ? text(node.props?.children) : String(node)
}
function stub() { return null }

function fixture(loginImplementation) {
  const f = hookHarness(), storage = new Map(), timers = new Map()
  const navigations = []
  let clock = 0, nextTimer = 0, tree
  const storageApi = {
    getItem: key => storage.get(key) ?? null,
    setItem: (key, value) => storage.set(key, value),
    removeItem: key => storage.delete(key),
  }
  const win = {
    localStorage: storageApi,
    setInterval(callback, delay) { timers.set(++nextTimer, { at: clock + delay, callback, interval: delay }); return nextTimer },
    clearInterval(id) { timers.delete(id) },
  }
  const FeedbackStub = Object.assign(function FeedbackStub() { return null }, { displayName: 'FeedbackAlert' })
  const page = compile('../src/features/s1-01-auth-login/LoginPage.tsx', {
    react: f.hooks,
    axios: { __esModule: true, default: { isAxiosError: e => e?.isAxiosError === true } },
    'react-router-dom': {
      Link: stub, useLocation: () => ({ state: null }), useNavigate: () => (...args) => navigations.push(args),
    },
    '../../components/ui/FeedbackAlert': { __esModule: true, default: FeedbackStub },
    '../../core/auth/authService': { login: loginImplementation },
  }, { window: win })
  const render = () => { tree = f.render(page); return tree }
  render()
  return {
    render,
    tree: () => tree,
    notice: () => find(tree, node => node.type === FeedbackStub),
    storage,
    navigations,
    form: () => find(tree, node => node.type === 'form'),
    submitButton: () => find(tree, node => node.type === 'button' && node.props.type === 'submit'),
    fill(email, password) {
      find(tree, node => node.type === 'input' && node.props.type === 'email').props.onChange({ target: { value: email } })
      render()
      find(tree, node => node.type === 'input' && node.props.type === 'password').props.onChange({ target: { value: password } })
      render()
    },
    async submit() { await this.form().props.onSubmit({ preventDefault() {} }); render() },
    /** Mounts the real notification component and advances fake time to prove S1-01 -> 3-second UI lifecycle. */
    assertNoticeExpires() {
      const props = this.notice()?.props
      assert.ok(props, 'error must be displayed by FeedbackAlert')
      assert.equal(props.tone, 'error', 'all login errors must be red')
      const notificationHooks = hookHarness(), notificationTimers = new Map()
      let now = 0, timerId = 0
      const Notification = compile('../src/components/ui/FeedbackAlert.tsx', {
        react: notificationHooks.hooks,
      }, { window: {
        setTimeout(callback, delay) { notificationTimers.set(++timerId, { at: now + delay, callback }); return timerId },
        clearTimeout(id) { notificationTimers.delete(id) },
      } })
      const alert = notificationHooks.render(() => Notification(props))
      assert.match(alert.props.className, /text-red-/)
      assert.equal(alert.props.role, 'alert')
      assert.equal(notificationTimers.size, 1)
      now = 2999
      for (const [id, t] of notificationTimers) if (t.at <= now) { notificationTimers.delete(id); t.callback() }
      render(); assert.ok(this.notice(), 'should still show at 2999ms')
      now = 3000
      for (const [id, t] of notificationTimers) if (t.at <= now) { notificationTimers.delete(id); t.callback() }
      render(); assert.equal(this.notice(), undefined, 'should vanish at 3000ms')
      notificationHooks.unmount()
    },
  }
}

const invalid = (attempt) => ({
  isAxiosError: true, response: { data: {
    code: 'INVALID_CREDENTIALS', message: 'Email hoặc mật khẩu không chính xác.',
    details: { failedLoginAttempts: attempt, maxFailedAttempts: 5, remainingAttempts: 5 - attempt },
  } },
})
const locked = (until) => ({
  isAxiosError: true, response: { data: {
    code: 'ACCOUNT_TEMPORARILY_LOCKED', message: 'Tài khoản đã bị khóa tạm.',
    details: { failedLoginAttempts: 5, maxFailedAttempts: 5, remainingAttempts: 0, lockedUntil: until },
  } },
})

test('S1-01: 4 lần sai hiển thị một thông báo đỏ gồm lỗi và 4/3/2/1 lượt còn lại, tự ẩn đúng 3 giây', async () => {
  let failures = 0
  const p = fixture(async () => { throw invalid(++failures) })
  p.fill(' Test@Libra.Local ', 'wrong-password')
  for (const remaining of [4, 3, 2, 1]) {
    await p.submit()
    const notice = p.notice()
    assert.equal(notice.props.tone, 'error')
    assert.equal(notice.props.message,
      `Email hoặc mật khẩu không chính xác. Bạn còn ${remaining} lượt đăng nhập trước khi tài khoản bị khóa tạm.`)
    const saved = JSON.parse(p.storage.get('libra_login_state:test@libra.local'))
    assert.equal(saved.failedLoginAttempts, 5 - remaining)
    p.assertNoticeExpires()
  }
  assert.equal(failures, 4)
})

test('S1-01: lần sai thứ năm khóa tạm, hiển thị đỏ rồi ẩn sau 3 giây, nút đăng nhập bị khóa', async () => {
  let requests = 0
  const until = new Date(Date.now() + 15 * 60_000).toISOString()
  const p = fixture(async () => { requests++; throw locked(until) })
  p.fill('reader@example.invalid', 'wrong-password')
  await p.submit()
  const notice = p.notice()
  assert.equal(notice.props.tone, 'error')
  assert.match(notice.props.message, /Tài khoản đã bị khóa tạm/)
  assert.match(notice.props.message, /Có thể thử lại sau/)
  assert.equal(p.submitButton().props.disabled, true)
  const saved = JSON.parse(p.storage.get('libra_login_state:reader@example.invalid'))
  assert.equal(saved.lockedUntil, until)
  p.assertNoticeExpires()
  assert.equal(p.submitButton().props.disabled, true, 'notification expiry must not unlock the account')
  await p.submit()
  assert.equal(requests, 1, 'locked form must not send another login request')
})

test('S1-01: lỗi máy chủ hoặc thiếu trường vẫn đỏ và tự ẩn 3 giây', async () => {
  const p = fixture(async () => { throw { isAxiosError: true, response: { data: { message: 'Máy chủ đang bận.' } } } })
  await p.submit()
  assert.match(p.notice().props.message, /Vui lòng nhập email và mật khẩu/)
  p.assertNoticeExpires()
  p.fill('user@libra.local', 'anything')
  await p.submit()
  assert.equal(p.notice().props.message, 'Máy chủ đang bận.')
  p.assertNoticeExpires()
})

test('S1-01: đăng nhập đúng chuyển trang và xóa trạng thái đếm sai đã lưu', async () => {
  const p = fixture(async (email, password) => {
    assert.equal(email, 'reader@example.invalid')
    assert.equal(password, 'correct-password')
    return { user: { role: 'READER' } }
  })
  p.storage.set('libra_login_state:reader@example.invalid', JSON.stringify({ failedLoginAttempts: 2, maxFailedAttempts: 5 }))
  p.fill('reader@example.invalid', 'correct-password')
  await p.submit()
  assert.equal(p.storage.has('libra_login_state:reader@example.invalid'), false)
  assert.equal(p.notice(), undefined)
  assert.equal(p.navigations.length, 1)
  assert.equal(p.navigations[0][0], '/catalog')
})
