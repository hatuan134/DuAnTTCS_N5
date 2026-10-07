import { apiClient } from '../../core/api/apiClient'

export interface ReaderLoanEligibility {
  readerId: number
  readerName: string
  cardNumber: string
  cardTypeName: string
  cardStatus: string
  expiresAt: string | null
  maxBooks: number
  borrowedBooks: number
  remainingBooks: number
  eligible: boolean
  reasonCode: string
  message: string
}

export const directLoanService = {
  async checkReader(cardNumber: string): Promise<ReaderLoanEligibility> {
    const response = await apiClient.get<ReaderLoanEligibility>('/loans/reader-eligibility', {
      params: { cardNumber: cardNumber.trim() },
    })
    return response.data
  },
}
