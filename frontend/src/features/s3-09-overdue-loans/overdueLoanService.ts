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
  lastContactedAt: string | null
  lastContactNote: string | null
  lastContactStaffName: string | null
}

export interface OverdueContact {
  id: number
  loanId: number
  staffId: number
  staffName: string
  note: string
  contactedAt: string
}

export const overdueLoanService = {
  list: async (): Promise<OverdueLoanItem[]> => {
    const response = await apiClient.get<OverdueLoanItem[]>('/loans/overdue')
    return response.data
  },
  history: async (loanId: number): Promise<OverdueContact[]> => {
    const response = await apiClient.get<OverdueContact[]>(`/loans/${loanId}/overdue-contacts`)
    return response.data
  },
  record: async (loanId: number, note: string): Promise<OverdueContact> => {
    const response = await apiClient.post<OverdueContact>(`/loans/${loanId}/overdue-contacts`, { note })
    return response.data
  },
}
