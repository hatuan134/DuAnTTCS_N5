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

export interface BookSearchItem {
  id: number
  isbn: string | null
  title: string
  subtitle: string | null
  authorName: string
  authors?: { id: number; name: string }[]
  categoryName: string
  publisher: string | null
  publicationYear: number | null
  copyCount: number
  availableCount: number
}

export interface BookCopyLocation {
  copyId: number
  barcode: string | null
  warehouseCode: string
  warehouseName: string
  shelfCode: string
  shelfName: string | null
}

export const directLoanService = {
  async confirm(cardNumber: string, barcodes: string[], requestId: string,
    overrideRequested = false, overrideReason = ''): Promise<DirectLoanResult> {
    const response = await apiClient.post<DirectLoanResult>('/loans/direct', {
      requestId, cardNumber: cardNumber.trim(), barcodes: barcodes.map((code) => code.trim()),
      ...(overrideRequested ? { overrideRequested: true, overrideReason: overrideReason.trim() } : {}),
    })
    return response.data
  },
  async previewItem(cardNumber: string, barcode: string, selectedBarcodes: string[],
    overridePreview = false): Promise<DirectLoanItem> {
    const response = await apiClient.post<DirectLoanItem>('/loans/direct/items/preview', {
      cardNumber: cardNumber.trim(), barcode: barcode.trim(), selectedBarcodes,
      ...(overridePreview ? { overridePreview: true } : {}),
    })
    return response.data
  },
  async checkReaderExplicit(cardNumber: string, requestId: string): Promise<ReaderLoanEligibility> {
    const response = await apiClient.post<ReaderLoanEligibility>('/loans/reader-eligibility/check', {
      cardNumber: cardNumber.trim(), requestId,
    })
    return response.data
  },
  async checkReader(cardNumber: string): Promise<ReaderLoanEligibility> {
    const response = await apiClient.get<ReaderLoanEligibility>('/loans/reader-eligibility', {
      params: { cardNumber: cardNumber.trim() },
    })
    return response.data
  },
  async searchBooks(keyword: string): Promise<BookSearchItem[]> {
    const response = await apiClient.get<BookSearchItem[]>('/books/public', {
      params: { keyword: keyword.trim() || undefined, availableOnly: false },
    })
    return response.data
  },
  async getAvailableCopies(bookId: number): Promise<BookCopyLocation[]> {
    const response = await apiClient.get<BookCopyLocation[]>(`/books/public/${bookId}/available-copies`)
    return response.data
  },
}
