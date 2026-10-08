import { apiClient } from '../../core/api/apiClient'
import type { LoanDetail } from '../s3-01-loans/loanService'

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
  blockReasons?: { code: string; message: string }[]
}

export interface DirectLoanItem {
  bookCopyId: number
  bookId: number
  barcode: string
  bookTitle: string
  remainingBooks: number
}

export interface DirectLoanResult {
  loan: LoanDetail
  reader: ReaderLoanEligibility
  message: string
}

export const directLoanService = {
  async confirm(cardNumber: string, barcodes: string[], requestId: string): Promise<DirectLoanResult> {
    const response = await apiClient.post<DirectLoanResult>('/loans/direct', {
      requestId, cardNumber: cardNumber.trim(), barcodes: barcodes.map((code) => code.trim()),
    })
    return response.data
  },
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
