import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import { createRequire } from 'node:module'
import vm from 'node:vm'
import ts from 'typescript'
const require = createRequire(import.meta.url), react = require('react')
function fixture() {
  const slots = [], effects = [], timers = new Map(), listeners = new Map()
  let cursor = 0, now = 0, next = 0, tree
  const form = { addEventListener: (name, fn) => listeners.set(name, fn), removeEventListener: (name, fn) => { if (listeners.get(name) === fn) listeners.delete(name) } }
  const hooks = { ...react,
    useState(initial) { const i = cursor++; if (!(i in slots)) slots[i] = { value: initial }; return [slots[i].value, value => { slots[i].value = typeof value === 'function' ? value(slots[i].value) : value }] },
    useRef(initial) { const i = cursor++; return slots[i] ??= { current: initial } },
    useEffect(fn, deps) { const i = cursor++, old = slots[i]; if (!old || deps.some((v, j) => !Object.is(v, old.deps[j]))) { slots[i] = { deps }; effects.push(() => { old?.cleanup?.(); slots[i].cleanup = fn() }) } },
  }
  const ctx = { exports: {}, require: name => name === 'react' ? hooks : name === './FeedbackAlert' ? { FEEDBACK_DURATION_MS: 3000 } : require(name), window: {
    setTimeout(fn, ms) { timers.set(++next, { at: now + ms, fn }); return next }, clearTimeout(id) { timers.delete(id) },
  } }
  vm.runInNewContext(ts.transpileModule(readFileSync(new URL('../src/components/ui/TimedFieldMessage.tsx', import.meta.url), 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX },
  }).outputText, ctx)
  return {
    render(message) { cursor = 0; tree = ctx.exports.default({ children: message }); if (tree?.ref) tree.ref.current = { closest: () => form }; effects.splice(0).forEach(fn => fn()); return tree },
    advance(ms) { now += ms; for (const [id, timer] of timers) if (timer.at <= now) { timers.delete(id); timer.fn() } },
    submit() { listeners.get('submit')?.() },
    unmount() { slots.forEach(slot => slot?.cleanup?.()) },
    timerCount: () => timers.size,
  }
}
test('field feedback expires at 3000ms while validation text in the parent remains unchanged', () => {
  const f = fixture(), validation = 'Mã thẻ không hợp lệ'
  f.render(validation); f.advance(2999); assert.ok(f.render(validation))
  f.advance(1); assert.equal(f.render(validation), null); assert.equal(validation, 'Mã thẻ không hợp lệ')
  f.unmount()
})
test('first error arriving after an empty render is shown and a repeat submission rearms the same error', () => {
  const f = fixture(); assert.equal(f.render(''), null); assert.ok(f.render('Bắt buộc nhập lý do'))
  f.advance(3000); assert.equal(f.render('Bắt buộc nhập lý do'), null)
  f.submit(); assert.ok(f.render('Bắt buộc nhập lý do')); f.advance(2999); assert.ok(f.render('Bắt buộc nhập lý do'))
  f.advance(1); assert.equal(f.render('Bắt buộc nhập lý do'), null); f.unmount()
})
test('a different error receives its own full duration, and unmount cancels the timer', () => {
  const f = fixture(); f.render('Lỗi A'); f.advance(2500); f.render('Lỗi B')
  f.advance(500); assert.ok(f.render('Lỗi B')); f.unmount(); assert.equal(f.timerCount(), 0)
})
