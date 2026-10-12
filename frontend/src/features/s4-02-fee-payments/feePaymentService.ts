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
  feeType: 'OVERDUE' | 'RECORDED'
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
export interface FeeReceipt extends PaymentSummary {
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
  async receipt(paymentId: number): Promise<FeeReceipt> {
    return (await apiClient.get<FeeReceipt>(`/fee-payments/${paymentId}/receipt`)).data
  },
}
export function feeTypeLabel(type: ReaderFee['feeType']): string {
  return type === 'OVERDUE' ? 'Phí trễ hạn' : 'Khoản phí đã ghi nhận'
}
