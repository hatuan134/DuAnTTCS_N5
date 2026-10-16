import { apiClient } from '../../core/api/apiClient'
import type { ReturnFeeAssessment } from '../s3-07-returns/returnService'

export const loanRoles = ['LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN']

export interface LoanSummary {
  id: number
  loanNumber: string
  readerId: number
  readerName: string
  createdById: number
  createdByName: string
  borrowedAt: string
  itemCount: number
}

export interface LoanItem {
  id: number
  copyId: number
  barcode: string
  bookId: number
  bookTitle: string
  borrowedAt: string
  dueAt: string | null
  returnedAt?: string | null
  returnedById?: number | null
  returnedByName?: string | null
  resolutionReason?: string | null
  fee?: ReturnFeeAssessment | null
}

export interface LoanDetail {
  id: number
  loanNumber: string
  reservationId: number | null
  readerId: number
  readerName: string
  createdById: number
  createdByName: string
  borrowedAt: string
  items: LoanItem[]
}

export interface LoanSearchItem {
  barcode: string
  bookTitle: string
  dueAt: string | null
  status: 'BORROWED' | 'RETURNED' | 'LOST' | 'DAMAGED'
}

export interface LoanSearchResult {
  readerId?: number | null
  readerPhone?: string | null
  id: number
  loanNumber: string
  cardNumber: string | null
  readerName: string
  borrowedAt: string
  status: 'BORROWED' | 'PARTIALLY_RETURNED' | 'RETURNED' | 'EMPTY' | 'LOST' | 'DAMAGED' | 'MIXED_INCIDENT'
  items: LoanSearchItem[]
}

export interface LoanSearchFilters {
  fromDate: string
  toDate: string
  status: '' | LoanSearchResult['status']
}

export type LoanSearchEmptyReason =
  | 'CODE_NOT_FOUND'
  | 'NO_LOANS_FOR_CODE'
  | 'NO_LOANS_MATCH_FILTERS'

export interface LoanSearchPage {
  items: LoanSearchResult[]
  page: number // zero-based, matching the backend
  size: number
  total: number
  /** Absent for successful searches, or for pages outside the result range. */
  emptyReason?: LoanSearchEmptyReason | null
}

export function formatLoanTimestamp(value: string | null, dateOnly = false): string {
  if (!value) return 'Chưa có thông tin'
  const date = new Date(value)
  if (!Number.isFinite(date.getTime())) return 'Chưa có thông tin'
  return new Intl.DateTimeFormat('vi-VN', {
    timeZone: 'Asia/Ho_Chi_Minh', day: '2-digit', month: '2-digit', year: 'numeric',
    ...(dateOnly ? {} : { hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23' as const }),
  }).format(date)
}

export interface ReaderLoanLookupPage {
  items: { readerId: number; readerName: string; readerPhone: string | null; cardNumber: string | null }[]
  page: number
  size: number
  total: number
}

export const loanService = {
  searchReaders: async (keyword: string, page = 0): Promise<ReaderLoanLookupPage> => {
    const response = await apiClient.get<ReaderLoanLookupPage>('/loans/readers', { params: { keyword, page } })
    return response.data
  },
  search: async (code: string, page = 0, filters?: LoanSearchFilters): Promise<LoanSearchPage> => {
    const response = await apiClient.get<LoanSearchPage>('/loans/search', {
      params: {
        code, page,
        ...(filters?.fromDate ? { fromDate: filters.fromDate } : {}),
        ...(filters?.toDate ? { toDate: filters.toDate } : {}),
        ...(filters?.status ? { status: filters.status } : {}),
      },
    })
    return response.data
  },
  list: async (): Promise<LoanSummary[]> => {
    const response = await apiClient.get<LoanSummary[]>('/loans')
    return response.data
  },
  detail: async (id: number): Promise<LoanDetail> => {
    const response = await apiClient.get<LoanDetail>(`/loans/${id}`)
    return response.data
  },
}
