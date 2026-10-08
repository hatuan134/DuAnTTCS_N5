import { apiClient } from '../../core/api/apiClient'

export interface LoanRejectionReason {
  code: string
  message: string
}

export interface LoanRejection {
  id: number
  occurredAt: string
  source: 'CARD_CHECK' | 'DIRECT_CONFIRM' | 'RESERVATION_CONFIRM'
  readerId: number
  readerName: string
  cardNumber: string
  actorId: number
  actorName: string
  reservationId: number | null
  borrowedBooks: number
  maxBooks: number
  overdueLoans: number
  unpaidAmountVnd: number
  reasons: LoanRejectionReason[]
  eventType: 'BLOCKED' | 'OVERRIDDEN'
  overrideReason: string | null
  loanId: number | null
}

export interface LoanRejectionPage {
  items: LoanRejection[]
  page: number
  size: number
  total: number
}

export const loanRejectionService = {
  async page(page: number, cardNumber: string): Promise<LoanRejectionPage> {
    const result = await apiClient.get<LoanRejectionPage>('/loans/rejections', {
      params: { page, size: 20, cardNumber: cardNumber.trim() },
    })
    return result.data
  },
  async detail(id: number): Promise<LoanRejection> {
    const result = await apiClient.get<LoanRejection>(`/loans/rejections/${id}`)
    return result.data
  },
}
