import { apiClient } from '../../core/api/apiClient'

export interface FeeReader {
  id: number
  fullName: string
  cardNumber: string | null
  memberCode: string | null
  email: string
  phone: string | null
  cardStatus: string | null
}
export interface ReaderFee {
  id: number
  feeType: 'OVERDUE' | 'RECORDED' | 'COMPENSATION'
  description: string | null
  originalAmountVnd: number
  paidAmountVnd: number
  waivedAmountVnd: number
  outstandingAmountVnd: number
}
export interface PaymentSummary {
  id: number
  transactionNumber: string
  receiptNumber: string
  collectedAt: string
  totalAmountVnd: number
  collectorName: string
  debtAfterVnd: number
}
export interface FeeCollection {
  reader: FeeReader
  fees: ReaderFee[]
  totalOutstandingVnd: number
  hasDebt: boolean
  debtToken: string
  payments: PaymentSummary[]
}
export interface FullFeePaymentRequest {
  requestKey: string
  expectedTotalVnd: number
  debtToken: string
}
export interface PartialFeePaymentRequest extends FullFeePaymentRequest {
  amountVnd: number
}
export interface FeeReceipt extends PaymentSummary {
  debtBeforeVnd: number
  paymentMode: 'FULL' | 'PARTIAL'
  readerId: number
  readerName: string
  cardNumber: string | null
  collectedBy: number | null
  items: {
    feeId: number
    feeType: ReaderFee['feeType']
    description: string | null
    originalAmountVnd: number
    previouslyPaidVnd: number
    waivedAmountVnd: number
    collectedAmountVnd: number
  }[]
}
export const feePaymentService = {
  async search(query: string): Promise<FeeReader[]> {
    return (await apiClient.get<FeeReader[]>('/fee-payments/readers', { params: { query } })).data
  },
  async detail(readerId: number): Promise<FeeCollection> {
    return (await apiClient.get<FeeCollection>(`/fee-payments/readers/${readerId}`)).data
  },
  async collect(readerId: number, request: FullFeePaymentRequest): Promise<FeeReceipt> {
    return (await apiClient.post<FeeReceipt>(`/fee-payments/readers/${readerId}/collect-full`, request)).data
  },
  async collectPartial(readerId: number, request: PartialFeePaymentRequest): Promise<FeeReceipt> {
    return (await apiClient.post<FeeReceipt>(`/fee-payments/readers/${readerId}/collect-partial`, request)).data
  },
  async receipt(paymentId: number): Promise<FeeReceipt> {
    return (await apiClient.get<FeeReceipt>(`/fee-payments/${paymentId}/receipt`)).data
  },
}
export function feeTypeLabel(type: ReaderFee['feeType']): string {
  if (type === 'COMPENSATION') return 'Bồi thường sách'
  return type === 'OVERDUE' ? 'Phí trễ hạn' : 'Khoản phí đã ghi nhận'
}

/** VND amounts are whole dong. Do not reinterpret separators, decimals or scientific notation. */
export function parsePartialAmount(raw: string, total: number): { amount: number | null; error: string } {
  const value = raw.trim()
  if (!/^\d+$/.test(value)) return { amount: null, error: 'Nhập số nguyên VND, không dùng dấu chấm, dấu phẩy hoặc chữ.' }
  const amount = Number(value)
  if (!Number.isSafeInteger(amount) || amount < 1) return { amount: null, error: 'Số tiền thu phải là số nguyên VND, tối thiểu 1 VND và trong giới hạn xử lý.' }
  if (amount > total) return { amount: null, error: 'Số tiền thu không được vượt quá tổng số tiền còn nợ.' }
  return { amount, error: '' }
}
export function previewFeeAllocation(fees: ReaderFee[], amount: number): { fee: ReaderFee; collectedAmountVnd: number }[] {
  let remaining = amount
  return [...fees].sort((a, b) => a.id - b.id).flatMap(fee => {
    const allocated = Math.min(Math.max(remaining, 0), fee.outstandingAmountVnd)
    remaining -= allocated
    return allocated > 0 ? [{ fee, collectedAmountVnd: allocated }] : []
  })
}
