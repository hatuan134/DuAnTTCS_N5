import { apiClient } from '../../core/api/apiClient'

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
  status: 'BORROWED' | 'RETURNED'
}

export interface LoanSearchResult {
  id: number
  loanNumber: string
  cardNumber: string | null
  readerName: string
  borrowedAt: string
  status: 'BORROWED' | 'PARTIALLY_RETURNED' | 'RETURNED' | 'EMPTY'
  items: LoanSearchItem[]
}

export interface LoanSearchPage {
  items: LoanSearchResult[]
  page: number // zero-based, matching the backend
  size: number
  total: number
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

export const loanService = {
  search: async (code: string, page = 0): Promise<LoanSearchPage> => {
    const response = await apiClient.get<LoanSearchPage>('/loans/search', { params: { code, page } })
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
