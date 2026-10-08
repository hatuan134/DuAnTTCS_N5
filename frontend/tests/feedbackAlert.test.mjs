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
for (const tone of ['success', 'error']) test(`${tone}: remains at 2999ms and dismisses at 3000ms`, () => {
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
