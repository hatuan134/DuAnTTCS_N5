import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import { createRequire } from 'node:module'
import { renderToStaticMarkup } from 'react-dom/server'
import vm from 'node:vm'
import ts from 'typescript'

const require = createRequire(import.meta.url)
function load(path, imports = {}) {
  const context = { exports: {}, require: (name) => imports[name] ?? require(name) }
  vm.runInNewContext(ts.transpileModule(readFileSync(new URL(path, import.meta.url), 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX },
  }).outputText, context)
  return context.exports
}
const component = load('../src/features/s3-04-my-borrowed-books/BorrowedBookDueWarning.tsx', {
  '../../components/ui/StatusBadge': load('../src/components/ui/StatusBadge.tsx'),
}).default
const render = (remainingDays) => renderToStaticMarkup(component({ remainingDays }))

test('null deadline and invalid differences never fabricate warnings', () => {
  for (const value of [null, NaN, Infinity, -Infinity, 0.5, -0.5, Number.MAX_SAFE_INTEGER + 1]) assert.equal(render(value), '')
})
test('three or more days have no warning', () => {
  for (const value of [3, 4, 15]) assert.equal(render(value), '')
})
test('two one and zero days have amber upcoming badge without lateness', () => {
  for (const value of [2, 1, 0]) {
    const html = render(value)
    assert.match(html, /Sắp đến hạn/); assert.match(html, /border-amber-200/)
    assert.doesNotMatch(html, /Quá hạn|Trễ/)
  }
})
test('past days have red overdue badge and positive exact late count', () => {
  for (const value of [-1, -7, -31]) {
    const html = render(value)
    assert.match(html, /Quá hạn/); assert.match(html, /border-red-200/)
    assert.ok(html.includes(`Trễ ${-value} ngày`)); assert.doesNotMatch(html, /Sắp đến hạn|Trễ -/)
  }
})
