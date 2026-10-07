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
  cardNumber?: string | null
  converted?: boolean
  loanNumber?: string | null
}

export interface ReservationLoanContext {
  cardNumber: string | null
  converted: boolean
  loanNumber: string | null
}

export interface ReservationLoanResult {
  id: number
  loanNumber: string
  reservationId: number
  readerId: number
  readerName: string
  cardNumber: string
  bookId: number
  bookTitle: string
  copyId: number
  barcode: string
  borrowedAt: string
  message: string
}

export type ReservationStatus = 'PENDING' | 'READY_FOR_PICKUP' | 'FULFILLED' | 'CANCELLED' | 'EXPIRED'

export const reservationFilterStatuses = ['PENDING', 'READY_FOR_PICKUP', 'FULFILLED', 'CANCELLED'] as const
export type ReservationStatusFilter = typeof reservationFilterStatuses[number] | ''

export interface ReservationCancellationAudit {
  actorId: number | null
  actorName: string
  cancelledAt: string
  reason: string
}

export interface CancelReservationResult {
  id: number
  bookId: number
  status: 'CANCELLED'
  cancellation: ReservationCancellationAudit
  copyId: number | null
  barcode: string | null
  copyOutcome: 'NO_COPY' | 'TRANSFERRED' | 'AVAILABLE'
  nextReservationId: number | null
  nextReaderName: string | null
  pickupDeadline: string | null
  message: string
}

export function canCancelReservation(status: ReservationStatus): boolean {
  return status === 'PENDING' || status === 'READY_FOR_PICKUP'
}

export interface ReservationQueueEntry {
  id: number
  readerId: number
  readerName: string
  reservedAt: string
  status: ReservationStatus
  queuePosition: number | null
  copyId: number | null
  barcode: string | null
  cancellation?: ReservationCancellationAudit | null
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
    FULFILLED: 'Đã chuyển thành phiếu mượn',
    CANCELLED: 'Đã huỷ',
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
  createLoan: async (id: number, cardNumber: string): Promise<ReservationLoanResult> => {
    const response = await apiClient.post<ReservationLoanResult>(`/reservations/${id}/loan`, { cardNumber: cardNumber.trim() })
    return response.data
  },
  loanContext: async (id: number): Promise<ReservationLoanContext> => {
    const response = await apiClient.get<ReservationLoanContext>(`/reservations/${id}/loan-context`)
    return response.data
  },
  cancel: async (id: number, reason: string): Promise<CancelReservationResult> => {
    const response = await apiClient.post<CancelReservationResult>(`/reservations/${id}/cancel`, { reason: reason.trim() })
    return response.data
  },
  queue: async (bookId: number, status: ReservationStatusFilter = ''): Promise<BookReservationQueue> => {
    const response = await apiClient.get<BookReservationQueue>(`/books/${bookId}/reservations`, {
      params: status ? { status } : undefined,
    })
    return response.data
  },
  list: async (): Promise<ReadyPickupReservation[]> => {
    const response = await apiClient.get<ReadyPickupReservation[]>('/reservations/ready-for-pickup')
    return response.data
  },
  detail: async (id: number): Promise<ReadyPickupReservation> => {
    const [response, context] = await Promise.all([
      apiClient.get<ReadyPickupReservation>(`/reservations/ready-for-pickup/${id}`),
      pickupService.loanContext(id),
    ])
    return { ...response.data, ...context }
  },
}
