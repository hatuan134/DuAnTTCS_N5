import { apiClient } from '../../core/api/apiClient'

export interface TopBorrowedBook {
  bookId: number
  bookTitle: string
  borrowCount: number
  stockCount: number
}

export interface LoanReport {
  startDate: string
  endDate: string
  totalBorrowed: number
  totalReturned: number
  openLoans: number
  overdueLoans: number
  overdueRate: number
  topBooks: TopBorrowedBook[]
}

export async function getLoanReport(startDate: string, endDate: string, signal?: AbortSignal): Promise<LoanReport> {
  const response = await apiClient.get<LoanReport>('/reports/loans', {
    params: { startDate, endDate },
    signal,
  })
  return response.data
}
