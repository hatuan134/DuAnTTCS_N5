import { apiClient } from '../../core/api/apiClient'

export interface MyBorrowedBook {
  id: number
  bookTitle: string
  barcode: string
  borrowedAt: string
  dueAt: string | null
  remainingDays: number | null
  renewalsUsed: number
  maxRenewals: number | null
}

export interface MyReturnedBook {
  id: number
  bookTitle: string
  barcode: string
  loanNumber: string
  borrowedAt: string
  returnedAt: string
}

export interface MyReturnedBooksPage {
  items: MyReturnedBook[]
  page: number
  size: number
  total: number
}

export interface RenewalCheckResponse {
  eligible: boolean
  message: string
  renewalsUsed: number
  maxRenewals: number
}

export const myBorrowedBooksService = {
  async checkRenewal(itemId: number): Promise<RenewalCheckResponse> {
    const { data } = await apiClient.post<RenewalCheckResponse>(`/loans/me/borrowed-books/${itemId}/renewal-check`)
    return data
  },
  async history(page: number): Promise<MyReturnedBooksPage> {
    const { data } = await apiClient.get<MyReturnedBooksPage>('/loans/me/returned-books', { params: { page } })
    return data
  },
  async list(): Promise<MyBorrowedBook[]> {
    const { data } = await apiClient.get<MyBorrowedBook[]>('/loans/me/borrowed-books')
    return data
  },
}
