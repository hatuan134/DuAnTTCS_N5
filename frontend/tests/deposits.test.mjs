import assert from 'node:assert/strict'
import { test } from 'node:test'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import ts from 'typescript'
const calls = []
const context = { exports: {}, require: () => ({ apiClient: {
  get: async (...args) => { calls.push(['GET', ...args]); return { data: {} } },
  post: async (...args) => { calls.push(['POST', ...args]); return { data: {} } },
} }) }
vm.runInNewContext(ts.transpileModule(readFileSync(new URL('../src/features/deposits/depositService.ts', import.meta.url), 'utf8'), { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText, context)
const { depositService, depositActionAmount } = context.exports
const base = { id: 1, readerId: 7, requiredVnd: 50000, collectedVnd: 0, heldVnd: 0, itemId: null, returnedAt: null, outstandingVnd: 0 }
test('deposit actions distinguish unpaid, held, returned, deducted and actually refunded amounts', () => {
  assert.equal(depositActionAmount(base, 'COLLECT'), 50000)
  const held = { ...base, collectedVnd: 50000, heldVnd: 50000, itemId: 10 }
  for (const kind of ['COLLECT', 'REFUND', 'DEDUCT']) assert.equal(depositActionAmount(held, kind), 0)
  assert.equal(depositActionAmount({ ...held, returnedAt: '2026-10-22' }, 'REFUND'), 50000)
  const returned = { ...held, returnedAt: '2026-10-22', outstandingVnd: 15000 }
  assert.equal(depositActionAmount(returned, 'REFUND'), 0)
  assert.equal(depositActionAmount(returned, 'DEDUCT'), 15000)
  assert.equal(depositActionAmount({ ...returned, outstandingVnd: 100000 }, 'DEDUCT'), 50000)
  assert.equal(depositActionAmount({ ...base, collectedVnd: 50000, heldVnd: 50000 }, 'REFUND'), 50000)
  assert.equal(depositActionAmount({ ...base, collectedVnd: 50000, heldVnd: 0 }, 'COLLECT'), 0)
})
test('deposit API reuses authenticated client and preserves the original idempotency payload on retry', async () => {
  const request = { requestKey: 'same-key', accountIds: [1, 2], expectedAmountVnd: 150000, confirmation: 'COLLECT' }
  await depositService.transact(7, 'COLLECT', request); await depositService.transact(7, 'COLLECT', request)
  assert.equal(calls[0][1], '/deposits/readers/7/COLLECT'); assert.equal(calls[0][2], calls[1][2])
  assert.equal(calls[0][2].actorId, undefined)
  await depositService.list('THE-01', 2)
  assert.equal(calls.at(-1)[2].params.page, 2)
  await depositService.prepare('THE-01', ['A', 'B'])
  assert.equal(calls.at(-1)[2].cardNumber, 'THE-01')
  await depositService.policy(35, null)
  assert.equal(calls.at(-1)[2].percentage, 35)
})

// Pending transaction recovery shares the deposit financial safety suite.
{
const context = { exports: {} }
vm.runInNewContext(ts.transpileModule(readFileSync(new URL('../src/features/deposits/depositRecovery.ts', import.meta.url), 'utf8'), { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText, context)
const { readPendingDeposit, savePendingDeposit, clearPendingDeposit } = context.exports
const pending = { reader: 7, kind: 'REFUND', request: { requestKey: '12345678-1234-4234-8234-123456789abc', accountIds: [1, 2], expectedAmountVnd: 150000, confirmation: 'REFUND' } }
function storage() { const data = new Map(); return { getItem: key => data.get(key) ?? null, setItem: (key, value) => data.set(key, value), removeItem: key => data.delete(key) } }
test('reload preserves the exact refund request and isolates staff identities', () => {
  const store = storage()
  savePendingDeposit(store, 42, pending)
  assert.equal(JSON.stringify(readPendingDeposit(store, 42)), JSON.stringify(pending))
  assert.equal(readPendingDeposit(store, 43), null)
  clearPendingDeposit(store, 43)
  assert.ok(readPendingDeposit(store, 42))
  clearPendingDeposit(store, 42)
  assert.equal(readPendingDeposit(store, 42), null)
})
test('corrupt or inconsistent pending requests fail closed instead of producing a new transaction', () => {
  const store = storage()
  for (const invalid of [null, {}, { ...pending, kind: 'COLLECT' }, { ...pending, request: { ...pending.request, accountIds: [1, 1] } }, { ...pending, request: { ...pending.request, expectedAmountVnd: -1 } }]) {
    store.setItem('libra.deposit.pending.42', JSON.stringify(invalid))
    assert.throws(() => readPendingDeposit(store, 42))
  }
})
test('unavailable storage prevents dispatch persistence from pretending success', () => {
  assert.throws(() => savePendingDeposit({ setItem() { throw new Error('storage unavailable') } }, 42, pending))
})

}
