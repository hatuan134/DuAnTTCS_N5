import { apiClient } from '../../core/api/apiClient'
export type DepositPolicy = { id: number; percentage: number; effectiveAt: string; createdAt: string; creatorName: string; previousPolicyId: number | null }
export type DepositAccount = { id: number; readerId: number; readerName: string; cardNumber: string | null; copyId: number; barcode: string; bookTitle: string; policyId: number; coverPrice: number; percentage: number; requiredVnd: number; collectedVnd: number; refundedVnd: number; deductedVnd: number; heldVnd: number; itemId: number | null; loanId: number | null; loanNumber: string | null; returnedAt: string | null; outstandingVnd: number; createdAt: string }
export type DepositKind = 'COLLECT' | 'REFUND' | 'DEDUCT'
export type DepositRequest = { requestKey: string; accountIds: number[]; expectedAmountVnd: number; confirmation: DepositKind }
export type DepositTransaction = { id: number; transactionNumber: string; readerId: number; kind: DepositKind; method: string; totalVnd: number; actorName: string; recordedAt: string; feePaymentId: number | null; entries: { accountId: number; amountVnd: number }[] }
export const depositKindLabel: Record<DepositKind, string> = { COLLECT: 'Thu cọc', REFUND: 'Hoàn cọc', DEDUCT: 'Khấu trừ cọc' }
export function depositActionAmount(account: DepositAccount, kind: DepositKind): number {
  if (kind === 'COLLECT') return account.collectedVnd === 0 && account.itemId === null ? account.requiredVnd : 0
  if (account.itemId !== null && !account.returnedAt) return 0
  if (kind === 'REFUND') return account.outstandingVnd === 0 ? account.heldVnd : 0
  return account.itemId !== null ? Math.min(account.heldVnd, account.outstandingVnd) : 0
}
export const depositService = {
  async list(query = '', page = 0, readerId?: number) { return (await apiClient.get<{ items: DepositAccount[]; page: number; size: number; totalItems: number }>('/deposits', { params: { query, page, readerId } })).data },
  async prepare(cardNumber: string, barcodes: string[]) { return (await apiClient.post<DepositAccount[]>('/deposits/prepare', { cardNumber, barcodes })).data },
  async transact(readerId: number, kind: DepositKind, body: DepositRequest) { return (await apiClient.post<DepositTransaction>(`/deposits/readers/${readerId}/${kind}`, body)).data },
  async history(readerId: number, page = 0) { return (await apiClient.get<{ items: DepositTransaction[]; page: number; size: number; totalItems: number }>(`/deposits/readers/${readerId}/transactions`, { params: { page } })).data },
  async policies(page = 0) { return (await apiClient.get<{ current: DepositPolicy | null; history: DepositPolicy[]; activationEnabled: boolean }>('/deposits/policies', { params: { page } })).data },
  async policy(percentage: number, effectiveAt: string | null) { return (await apiClient.post('/deposits/policies', { percentage, effectiveAt })).data },
}
