import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import { createRequire } from 'node:module'
import { renderToStaticMarkup } from 'react-dom/server'
import vm from 'node:vm'
import ts from 'typescript'

const require = createRequire(import.meta.url)
const context = { exports: {}, require }
vm.runInNewContext(ts.transpileModule(readFileSync(new URL('../src/features/s3-04-my-borrowed-books/BorrowedBookDueWarning.tsx', import.meta.url), 'utf8'), {
  compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX },
}).outputText, context)
const render = (remainingDays) => renderToStaticMarkup(context.exports.default({ remainingDays }))

test('unknown or invalid day counts show a neutral message, never a negative sign', () => {
  for (const value of [null, NaN, Infinity, -Infinity, 0.5, -0.5, Number.MAX_SAFE_INTEGER + 1]) {
    assert.match(render(value), /Chưa xác định hạn trả/)
    assert.doesNotMatch(render(value), /Quá hạn|Sắp đến hạn/)
  }
})
test('three or more remaining days show friendly positive numbers', () => {
  for (const value of [3, 4, 15]) {
    const html = render(value)
    assert.match(html, new RegExp(`Còn ${value} ngày`))
    assert.doesNotMatch(html, /Quá hạn/)
  }
})
test('two/one days are upcoming, zero is due today rather than upcoming', () => {
  for (const value of [1, 2]) assert.match(render(value), new RegExp(`Sắp đến hạn · Còn ${value} ngày`))
  assert.match(render(0), /Đến hạn hôm nay/)
  assert.doesNotMatch(render(0), /Sắp đến hạn/)
})
test('overdue days are positive user-facing day counts, never signed numerals', () => {
  for (const value of [-1, -7, -31]) {
    const html = render(value)
    assert.match(html, new RegExp(`Quá hạn ${-value} ngày`))
    assert.doesNotMatch(html, /Trễ -|>-/)
  }
})
