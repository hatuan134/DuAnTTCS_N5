import { apiClient } from '../../core/api/apiClient'

export interface MyBorrowedBook {
  id: number
  bookTitle: string
  barcode: string
  borrowedAt: string
  dueAt: string | null
  remainingDays: number | null
}

export const myBorrowedBooksService = {
  async list(): Promise<MyBorrowedBook[]> {
    const { data } = await apiClient.get<MyBorrowedBook[]>('/loans/me/borrowed-books')
    return data
  },
}
