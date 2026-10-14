import { apiClient } from '../../core/api/apiClient'

export interface TopBorrowedBook {
  bookId: number
  bookTitle: string
  borrowCount: number
  stockCount: number
}

export interface TopQueuedBook {
  bookId: number
  bookTitle: string
  waitCount: number
  stockCount: number
}

export interface DailyLoanCount {
  date: string
  borrowCount: number
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
  dailyLoans: DailyLoanCount[]
  queueSnapshotAt: string
  topQueuedBooks: TopQueuedBook[]
}

export async function getLoanReport(startDate: string, endDate: string, signal?: AbortSignal): Promise<LoanReport> {
  const response = await apiClient.get<LoanReport>('/reports/loans', {
    params: { startDate, endDate },
    signal,
  })
  return response.data
}
