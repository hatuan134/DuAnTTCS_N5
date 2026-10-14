import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import { createRequire } from 'node:module'
import vm from 'node:vm'
import ts from 'typescript'

const require = createRequire(import.meta.url)
const react = require('react')
const source = readFileSync(new URL('../src/features/s4-07-loan-report/DailyLoansChart.tsx', import.meta.url), 'utf8')
const api = readFileSync(new URL('../src/features/s4-07-loan-report/loanReportService.ts', import.meta.url), 'utf8')
const page = readFileSync(new URL('../src/features/s4-07-loan-report/LoanReportPage.tsx', import.meta.url), 'utf8')

function fixture() {
  const states = []
  let index = 0
  const hooks = {
    ...react,
    useState(initial) {
      const i = index++
      if (!(i in states)) states[i] = initial
      return [states[i], value => { states[i] = value }]
    },
  }
  const context = { exports: {}, require: path => path === 'react' ? hooks : require(path) }
  vm.runInNewContext(ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX },
  }).outputText, context)
  return {
    render(props) { index = 0; return context.exports.default(props) },
  }
}

function findAll(node, predicate, items = []) {
  if (Array.isArray(node)) { node.forEach(item => findAll(item, predicate, items)); return items }
  if (!node || typeof node !== 'object') return items
  if (predicate(node)) items.push(node)
  findAll(node.props?.children, predicate, items)
  return items
}

function data(length, firstDate = '2026-09-01') {
  const start = new Date(firstDate + 'T12:00:00.000Z')
  return Array.from({ length }, (_, i) => {
    const day = new Date(start.getTime() + i * 86400000).toISOString().slice(0, 10)
    return { date: day, borrowCount: i === 9 ? 25 : i === 10 ? 18 : 0 }
  })
}

test('the chart shows a separate accessible bar for every day including zero days', () => {
  for (const n of [1, 7, 30, 92]) {
    const rows = data(n)
    const rendered = fixture().render({ data: rows, totalBorrowed: rows.reduce((sum, x) => sum + x.borrowCount, 0) })
    const bars = findAll(rendered, node => node.props?.['data-testid']?.startsWith('daily-bar-'))
    assert.equal(bars.length, n)
    assert.deepEqual(bars.map(bar => bar.props['data-count']), rows.map(row => row.borrowCount))
    assert.equal(bars[0].props.tabIndex, 0)
    assert.match(bars[0].props['aria-label'], /lượt mượn/)
    const dates = new Set(bars.map(bar => bar.props['data-testid']))
    assert.equal(dates.size, n)
  }
})

test('hover and keyboard focus reveal the exact borrowed count', () => {
  const f = fixture()
  const rows = data(30)
  let rendered = f.render({ data: rows, totalBorrowed: 43 })
  const bar = findAll(rendered, node => node.props?.['data-testid'] === 'daily-bar-2026-09-10')[0]
  bar.props.onFocus()
  rendered = f.render({ data: rows, totalBorrowed: 43 })
  const statuses = findAll(rendered, node => node.props?.role === 'status')
  assert.ok(statuses.length)
  assert.match(JSON.stringify(statuses), /25/)
})

test('chart total is derived from the same API data and changed date range triggers a fresh report', () => {
  assert.match(api, /dailyLoans: DailyLoanCount\[\]/)
  assert.match(page, /data=\{report\.dailyLoans\} totalBorrowed=\{report\.totalBorrowed\}/)
  assert.match(page, /getLoanReport\(startDate, endDate, controller\.signal\)/)
  assert.match(page, /\[allowed, dateError, startDate, endDate, refreshKey\]/)
})
