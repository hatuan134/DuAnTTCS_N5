import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import vm from 'node:vm'
import ts from 'typescript'
import { renderToStaticMarkup } from 'react-dom/server'
const require = createRequire(import.meta.url), React = require('react')
const wrap = ({ children }) => React.createElement('div', null, children)
function page(role) {
  const imports = {
    '../../core/auth/authStorage': { getCurrentUser: () => ({ id: 1, role }) },
    './depositService': { depositService: {}, depositActionAmount: () => 0, depositKindLabel: {} },
    './depositRecovery': { readPendingDeposit: () => null },
    './DepositPolicyPanel': { __esModule: true, default: () => React.createElement('div', null, 'policy') },
    '../s1-02-user-management/accountService': {},
    '../s3-07-returns/ReturnFeeDetails': { formatVnd: String },
    '../s3-01-loans/loanService': { formatLoanTimestamp: String },
  }
  const context = { exports: {}, sessionStorage: {}, require: name => imports[name] ?? (name.startsWith('../../components/ui/') ? { __esModule: true, default: wrap } : require(name)) }
  vm.runInNewContext(ts.transpileModule(readFileSync(new URL('../src/features/deposits/DepositPage.tsx', import.meta.url), 'utf8'), { compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX } }).outputText, context)
  return context.exports
}
test('staff deposit page contains configuration without collection or search', () => {
  assert.equal(renderToStaticMarkup(React.createElement(page('ADMIN').default)).includes('<form'), false)
  assert.match(renderToStaticMarkup(React.createElement(page('ADMIN').default)), /policy/)
})
test('embedded collection uses selected books without nesting a form inside the loan form', () => {
  const html = renderToStaticMarkup(React.createElement(page('LIBRARIAN').InlineDeposits, { mode: 'collect', card: 'TV-1', barcodes: ['A', 'B'] }))
  assert.match(html, /2 cuốn đã chọn/)
  assert.match(html, /Thu tiền cọc trước bàn giao/)
  assert.equal(html.includes('<form'), false)
  assert.equal(html.includes('policy'), false)
})
test('readers and managers cannot use the embedded cash confirmation controls', () => {
  for (const role of ['READER', 'LIBRARY_MANAGER']) assert.equal(renderToStaticMarkup(React.createElement(page(role).InlineDeposits, { mode: 'return', readerId: 8, loanNumber: 'PM-8' })), '')
})
