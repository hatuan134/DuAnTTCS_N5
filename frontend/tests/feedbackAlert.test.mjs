import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import { createRequire } from 'node:module'
import vm from 'node:vm'
import ts from 'typescript'
const require = createRequire(import.meta.url)
const react = require('react')

function fixture() {
  const slots = [], pending = [], timers = new Map()
  let cursor = 0, now = 0, sequence = 0, tree
  const hooks = {
    ...react,
    useRef(value) { const i = cursor++; return slots[i] ??= { current: value } },
    useEffect(fn, deps) {
      const i = cursor++, old = slots[i]
      if (!old || deps.some((v, n) => !Object.is(v, old.deps[n]))) {
        slots[i] = { deps, cleanup: old?.cleanup }
        pending.push(() => { old?.cleanup?.(); slots[i].cleanup = fn() })
      }
    },
  }
  const context = { exports: {}, require: n => n === 'react' ? hooks : require(n), window: {
    setTimeout(fn, delay) { timers.set(++sequence, { at: now + delay, fn }); return sequence },
    clearTimeout(id) { timers.delete(id) },
  } }
  vm.runInNewContext(ts.transpileModule(readFileSync(new URL('../src/components/ui/FeedbackAlert.tsx', import.meta.url), 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX },
  }).outputText, context)
  return {
    render(props) { cursor = 0; tree = context.exports.default(props); for (const fn of pending.splice(0)) fn(); return tree },
    advance(ms) { now += ms; for (const [id, timer] of timers) if (timer.at <= now) { timers.delete(id); timer.fn() } },
    close() { tree.props.children[1].props.onClick() },
    unmount() { for (const slot of slots) slot?.cleanup?.() },
    timerCount: () => timers.size,
  }
}
for (const tone of ['success', 'error', 'warning', 'info']) test(`${tone}: remains at 2999ms and dismisses at 3000ms`, () => {
  const f = fixture(); let calls = 0
  f.render({ message: 'Kết quả thao tác', tone, onDismiss: () => calls++ })
  f.advance(2999); assert.equal(calls, 0)
  f.advance(1); assert.equal(calls, 1)
  f.advance(3000); assert.equal(calls, 1)
})
test('parent render does not extend timer and uses latest dismissal callback', () => {
  const f = fixture(); let old = 0, latest = 0
  f.render({ message: 'Lỗi API', tone: 'error', onDismiss: () => old++ })
  f.advance(2000)
  f.render({ message: 'Lỗi API', tone: 'error', onDismiss: () => latest++ })
  f.advance(1000); assert.equal(old, 0); assert.equal(latest, 1)
})
test('new message gets a full 3000ms and previous timer is cancelled', () => {
  const f = fixture(); let calls = 0
  const props = { tone: 'error', onDismiss: () => calls++ }
  f.render({ ...props, message: 'Lỗi A' }); f.advance(2000)
  f.render({ ...props, message: 'Lỗi B' }); f.advance(1000)
  assert.equal(calls, 0); f.advance(2000); assert.equal(calls, 1)
})
test('a new notification tone with the same text restarts the 3000ms timer', () => {
  const f = fixture(); let calls = 0
  f.render({ message: 'Luu du lieu', tone: 'error', onDismiss: () => calls++ })
  f.advance(2200)
  f.render({ message: 'Luu du lieu', tone: 'success', onDismiss: () => calls++ })
  f.advance(800); assert.equal(calls, 0)
  f.advance(2199); assert.equal(calls, 0)
  f.advance(1); assert.equal(calls, 1)
})
test('early close and unmount cancel pending work', () => {
  const f = fixture(); let calls = 0
  f.render({ message: 'Đã lưu', tone: 'success', onDismiss: () => calls++ })
  f.close(); assert.equal(calls, 1); f.unmount(); f.advance(3000)
  assert.equal(calls, 1); assert.equal(f.timerCount(), 0)
})
test('empty message has neither alert nor timer', () => {
  const f = fixture()
  assert.equal(f.render({ message: '', tone: 'error', onDismiss() {} }), null)
  assert.equal(f.timerCount(), 0)
})

test('errors and invalid-data warnings are red; successes are green', () => {
  for (const tone of ['error', 'warning', 'success']) {
    const f = fixture()
    const tree = f.render({ message: 'Kết quả', tone, onDismiss() {} })
    assert.equal(tree.props['data-feedback-tone'], tone)
    assert.match(tree.props.className, tone === 'success' ? /bg-emerald-50.*text-emerald-800/ : /bg-red-50.*text-red-700/)
    f.unmount()
  }
})

test('standalone ThreeUI toast also hides exactly at 3000ms without a delayed exit', () => {
  const html = readFileSync(new URL('../public/threeui/bestsellers-book-showcase.html', import.meta.url), 'utf8')
  const functionSource = html.match(/function showToast\(message, tone = "info"\) \{[\s\S]*?\n      \}/)[0]
  const durationSource = html.match(/const FEEDBACK_DURATION_MS = \d+;/)[0]
  let now = 0, next = 0; const timers = new Map(), toast = { dataset: {}, textContent: '' }
  const context = { toast, exports: {}, window: {
    setTimeout(fn, ms) { timers.set(++next, { at: now + ms, fn }); return next }, clearTimeout(id) { timers.delete(id) },
  } }
  vm.runInNewContext(durationSource + '\nlet toastTimer = 0;\n' + functionSource + '\nexports.show = showToast;', context)
  context.exports.show('Đã lưu.', 'success'); assert.equal(toast.dataset.tone, 'success')
  now = 2999; for (const timer of timers.values()) if (timer.at <= now) timer.fn()
  assert.equal(toast.dataset.show, 'true')
  now = 3000; for (const timer of timers.values()) if (timer.at <= now) timer.fn()
  assert.equal(toast.dataset.show, 'false')
  assert.match(html, /\.toast \{[\s\S]*?display: none;[\s\S]*?transition: none;/)
})
