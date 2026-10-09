import { apiClient } from '../../core/api/apiClient'

export interface OverdueLoanItem {
  loanId: number
  loanNumber: string
  itemId: number
  readerId: number
  readerName: string
  readerPhone: string | null
  bookId: number
  bookTitle: string
  dueAt: string
  overdueDays: number
}

export const overdueLoanService = {
  list: async (): Promise<OverdueLoanItem[]> => {
    const response = await apiClient.get<OverdueLoanItem[]>('/loans/overdue')
    return response.data
  },
}
