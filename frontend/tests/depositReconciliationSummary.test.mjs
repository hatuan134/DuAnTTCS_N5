import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { renderToStaticMarkup } from 'react-dom/server'
import vm from 'node:vm'
import ts from 'typescript'
const require = createRequire(import.meta.url), react = require('react')
const sample = { id: 1, readerId: 20, bookTitle: 'Bộ não và tâm trí', barcode: 'S4102-000208', loanId: 6, loanNumber: 'PM-000006',
  collectedVnd: 82500, refundedVnd: 0, deductedVnd: 0, heldVnd: 82500, requiredVnd: 82500,
  itemId: 209, returnedAt: '2026-10-23T23:53:46+07:00', outstandingVnd: 0, coverPrice: 165000, percentage: 50 }
const flush = () => new Promise(resolve => setImmediate(resolve))
async function fixture({ items = [sample], loanId = 6, loanNumber = 'PM-000006' } = {}) {
  const states = [], effects = [], calls = []
  let cursor = 0
  const hooks = { ...react,
    useState(initial) { const i = cursor++; if (!(i in states)) states[i] = initial; return [states[i], v => { states[i] = typeof v === 'function' ? v(states[i]) : v }] },
    useEffect(effect) { cursor++; effects.push(effect) },
  }
  const imports = {
    react: hooks,
    'react-router-dom': { Link: ({ to, children, ...props }) => react.createElement('a', { ...props, href: to }, children) },
    '../../components/ui/Card': { __esModule: true, default: ({ children, ...props }) => react.createElement('section', props, children) },
    '../s1-02-user-management/accountService': { getApiErrorMessage: () => 'Lỗi truy vấn cọc' },
    '../s3-07-returns/ReturnFeeDetails': { formatVnd: value => `${Number(value).toLocaleString('vi-VN')} VND` },
    './depositService': { depositService: { list: async (query, page, readerId) => { calls.push({ query, page, readerId }); return { items: page === 0 ? items : [], totalItems: items.length } } } },
  }
  const s = readFileSync(new URL('../src/features/deposits/DepositReconciliationSummary.tsx', import.meta.url), 'utf8')
  const context = { exports: {}, require: name => imports[name] ?? require(name) }
  vm.runInNewContext(ts.transpileModule(s, { compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX } }).outputText, context)
  const render = () => { cursor = 0; return renderToStaticMarkup(context.exports.default({ readerId: 20, loanId, loanNumber })) }
  render()
  for (const f of effects.splice(0)) f()
  await flush()
  return { html: render(), calls }
}
test('linked paid and returned account is shown as awaiting refund without fabricating receipts', async () => {
  const f = await fixture()
  assert.match(f.html, /82.500 VND/)
  assert.match(f.html, /chờ đối soát hoàn cọc/)
  assert.match(f.html, /Xem giao dịch cọc/)
  assert.equal(f.calls[0].query, 'PM-000006')
  assert.equal(f.calls[0].readerId, 20)
})
test('legacy loan without a deposit shows no retroactive demand', async () => {
  const f = await fixture({ items: [] })
  assert.match(f.html, /Không ghi nhận khoản cọc liên kết/)
  assert.match(f.html, /không bị truy thu hồi tố/)
})
test('other loans deposits are not mixed into the opened loan totals', async () => {
  const f = await fixture({ items: [sample, { ...sample, id: 2, loanId: 7, loanNumber: 'PM-000007' }] })
  assert.equal((f.html.match(/Bộ não và tâm trí/g) ?? []).length, 1)
})
