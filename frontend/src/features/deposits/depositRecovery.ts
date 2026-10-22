import type { DepositKind, DepositRequest } from './depositService'

export type PendingDeposit = { reader: number; kind: DepositKind; request: DepositRequest }
const key = (actor: number) => `libra.deposit.pending.${actor}`
// Persist before dispatch. A reload must retry the same request, never infer payment success.
export function savePendingDeposit(storage: Storage, actor: number, pending: PendingDeposit) {
  storage.setItem(key(actor), JSON.stringify(pending))
}
export function clearPendingDeposit(storage: Storage, actor: number) {
  storage.removeItem(key(actor))
}
export function readPendingDeposit(storage: Storage, actor: number): PendingDeposit | null {
  const raw = storage.getItem(key(actor))
  if (!raw) return null
  const value = JSON.parse(raw) as PendingDeposit
  const request = value?.request
  if (!Number.isSafeInteger(value?.reader) || value.reader <= 0 || !['COLLECT', 'REFUND', 'DEDUCT'].includes(value.kind)
    || request?.confirmation !== value.kind || !/^[a-f0-9]{8}-(?:[a-f0-9]{4}-){3}[a-f0-9]{12}$/i.test(request.requestKey)
    || !Number.isSafeInteger(request.expectedAmountVnd) || request.expectedAmountVnd <= 0
    || !Array.isArray(request.accountIds) || !request.accountIds.length || request.accountIds.length > 10
    || request.accountIds.some(id => !Number.isSafeInteger(id) || id <= 0)
    || new Set(request.accountIds).size !== request.accountIds.length) throw new Error('Invalid pending deposit')
  return value
}
