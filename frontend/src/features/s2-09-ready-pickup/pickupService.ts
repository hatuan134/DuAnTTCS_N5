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

export type ReservationStatus = 'PENDING' | 'READY_FOR_PICKUP' | 'FULFILLED' | 'CANCELLED' | 'EXPIRED'

export interface ReservationQueueEntry {
  id: number
  readerId: number
  readerName: string
  reservedAt: string
  status: ReservationStatus
  queuePosition: number | null
  copyId: number | null
  barcode: string | null
}

export interface BookReservationQueue {
  bookId: number
  bookTitle: string
  items: ReservationQueueEntry[]
}

export function reservationStatusLabel(status: ReservationStatus): string {
  const labels: Record<ReservationStatus, string> = {
    PENDING: 'Đang xếp hàng',
    READY_FOR_PICKUP: 'Đang chờ nhận',
    FULFILLED: 'Đã hoàn tất',
    CANCELLED: 'Đã hủy',
    EXPIRED: 'Hết hạn nhận',
  }
  return labels[status] ?? 'Chưa xác định'
}

export const pickupRoles = ['LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN']

export function formatPickupDate(value: string | null, includeSeconds = false): string {
  if (!value) return 'Chưa có hạn nhận'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return 'Chưa có hạn nhận'
  return new Intl.DateTimeFormat('vi-VN', {
    timeZone: 'Asia/Ho_Chi_Minh',
    day: '2-digit', month: '2-digit', year: 'numeric',
    hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
    second: includeSeconds ? '2-digit' : undefined,
  }).format(date)
}

export const pickupService = {
  queue: async (bookId: number): Promise<BookReservationQueue> => {
    const response = await apiClient.get<BookReservationQueue>(`/books/${bookId}/reservations`)
    return response.data
  },
  list: async (): Promise<ReadyPickupReservation[]> => {
    const response = await apiClient.get<ReadyPickupReservation[]>('/reservations/ready-for-pickup')
    return response.data
  },
  detail: async (id: number): Promise<ReadyPickupReservation> => {
    const response = await apiClient.get<ReadyPickupReservation>(`/reservations/ready-for-pickup/${id}`)
    return response.data
  },
}
