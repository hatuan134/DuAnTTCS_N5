import { apiClient } from '../../core/api/apiClient'

export interface BookReservation {
  id: number
  bookId: number
  status: string
  reservedAt: string
  queuePosition: number | null
  message: string
  pickupDeadline: string | null
  reservedCopy: {
    copyId: number
    barcode: string
    warehouseCode: string
    warehouseName: string
    shelfCode: string
    shelfName: string | null
  } | null
}

export const reservationService = {
  reserve: async (bookId: number): Promise<BookReservation> => {
    const response = await apiClient.post<BookReservation>(`/books/${bookId}/reservations`)
    return response.data
  },
}
