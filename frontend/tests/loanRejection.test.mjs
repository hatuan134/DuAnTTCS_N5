import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import vm from 'node:vm'
import ts from 'typescript'
const require = createRequire(import.meta.url)
function load(file, imports) {
  const code = ts.transpileModule(readFileSync(new URL(file, import.meta.url), 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS },
  }).outputText
  const context = { exports: {}, require: (id) => imports[id] ?? require(id) }
  vm.runInNewContext(code, context)
  return context.exports
}
test('deliberate card check is POST with stable key; live preview remains GET', async () => {
  const calls = []
  const client = {
    async post(url, data) { calls.push({ method: 'POST', url, data }); return { data: {} } },
    async get(url, config) { calls.push({ method: 'GET', url, config }); return { data: {} } },
  }
  const { directLoanService } = load('../src/features/s3-02-direct-loans/directLoanService.ts', {
    '../../core/api/apiClient': { apiClient: client },
  })
  const key = '0e19b909-4795-4dfa-b5a5-cdb613f62e11'
  await directLoanService.checkReader(' TV-0012 ')
  await directLoanService.checkReaderExplicit(' TV-0012 ', key)
  assert.equal(calls[0].method, 'GET')
  assert.equal(calls[1].url, '/loans/reader-eligibility/check')
  assert.equal(calls[1].data.cardNumber, 'TV-0012')
  assert.equal(calls[1].data.requestId, key)
})
test('history supports filtering, paging and detail', async () => {
  const calls = []
  const { loanRejectionService } = load('../src/features/s3-02-direct-loans/loanRejectionService.ts', {
    '../../core/api/apiClient': { apiClient: { async get(url, config) {
      calls.push({ url, config }); return { data: { items: [], total: 0 } }
    } } },
  })
  await loanRejectionService.page(0, ' TV-0012 ')
  await loanRejectionService.detail(42)
  assert.equal(calls[0].url, '/loans/rejections')
  assert.equal(calls[0].config.params.cardNumber, 'TV-0012')
  assert.equal(calls[0].config.params.size, 20)
  assert.equal(calls[1].url, '/loans/rejections/42')
})
