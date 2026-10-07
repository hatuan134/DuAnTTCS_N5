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

export interface DirectLoanItem {
  bookCopyId: number
  bookId: number
  barcode: string
  bookTitle: string
  remainingBooks: number
}

export const directLoanService = {
  async previewItem(cardNumber: string, barcode: string, selectedBarcodes: string[]): Promise<DirectLoanItem> {
    const response = await apiClient.post<DirectLoanItem>('/loans/direct/items/preview', {
      cardNumber: cardNumber.trim(), barcode: barcode.trim(), selectedBarcodes,
    })
    return response.data
  },
  async checkReader(cardNumber: string): Promise<ReaderLoanEligibility> {
    const response = await apiClient.get<ReaderLoanEligibility>('/loans/reader-eligibility', {
      params: { cardNumber: cardNumber.trim() },
    })
    return response.data
  },
}
