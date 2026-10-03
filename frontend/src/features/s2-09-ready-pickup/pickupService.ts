import { apiClient } from '../../core/api/apiClient'

export interface ReadyPickupReservation {
  id: number
  bookId: number
  bookTitle: string
  copyId: number | null
  barcode: string | null
  readerId: number
  readerName: string
  status: 'READY_FOR_PICKUP'
  reservedAt: string
  pickupDeadline: string | null
}

export const pickupRoles = ['LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN']

export function formatPickupDate(value: string | null): string {
  if (!value) return 'Chưa có hạn nhận'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return 'Chưa có hạn nhận'
  return new Intl.DateTimeFormat('vi-VN', {
    timeZone: 'Asia/Ho_Chi_Minh',
    day: '2-digit', month: '2-digit', year: 'numeric',
    hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
  }).format(date)
}

export const pickupService = {
  list: async (): Promise<ReadyPickupReservation[]> => {
    const response = await apiClient.get<ReadyPickupReservation[]>('/reservations/ready-for-pickup')
    return response.data
  },
  detail: async (id: number): Promise<ReadyPickupReservation> => {
    const response = await apiClient.get<ReadyPickupReservation>(`/reservations/ready-for-pickup/${id}`)
    return response.data
  },
}
