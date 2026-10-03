import { apiClient } from '../../core/api/apiClient'

export interface BookReservation {
  id: number
  bookId: number
  status: string
  reservedAt: string
  queuePosition: number
  message: string
}

export const reservationService = {
  reserve: async (bookId: number): Promise<BookReservation> => {
    const response = await apiClient.post<BookReservation>(`/books/${bookId}/reservations`)
    return response.data
  },
}
