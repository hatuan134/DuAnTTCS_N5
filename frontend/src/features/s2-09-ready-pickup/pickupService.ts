import { apiClient } from '../../core/api/apiClient'
import type { ReaderLoanEligibility } from '../s3-02-direct-loans/directLoanService'

export interface ReadyPickupReservation {
  id: number
  bookId: number
  bookTitle: string
  copyId: number | null
  barcode: string | null
  readerId: number
  readerName: string
  status: 'READY_FOR_PICKUP' | 'FULFILLED' | 'EXPIRED'
  reservedAt: string
  pickupDeadline: string | null
  cardNumber?: string | null
  converted?: boolean
  loanNumber?: string | null
  dates?: LoanDatePreview | null
  dateError?: string | null
  expired?: boolean
  checkedAt?: string | null
  pickupMessage?: string | null
  copyStatus?: string | null
}

export interface LoanDatePreview {
  borrowDate: string
  cardTypeName: string
  loanDays: number
  originalDueDate: string
  dueDate: string
  dueAt: string
  adjusted: boolean
  skippedClosedDates: string[]
}

export interface ReservationLoanContext {
  cardNumber: string | null
  converted: boolean
  loanNumber: string | null
  dates: LoanDatePreview | null
  dateError: string | null
  status: ReadyPickupReservation['status']
  expired: boolean
  pickupDeadline: string | null
  checkedAt: string
  pickupMessage: string | null
  copyStatus: string | null
  reservation: ReadyPickupReservation
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
  dates: LoanDatePreview
}

export type ReservationStatus = 'PENDING' | 'READY_FOR_PICKUP' | 'FULFILLED' | 'CANCELLED' | 'EXPIRED'

export const reservationFilterStatuses = ['PENDING', 'READY_FOR_PICKUP', 'FULFILLED', 'CANCELLED', 'EXPIRED'] as const
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

export function formatLoanDate(value: string): string {
  const parts = value.split('-')
  return parts.length === 3 ? `${parts[2]}/${parts[1]}/${parts[0]}` : value
}

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

export const pickupExpiredMessage = 'Đơn đặt giữ đã quá hạn nhận. Không thể lập phiếu mượn. Vui lòng yêu cầu bạn đọc đặt giữ lại.'

// Use the server's checked time plus elapsed time; the computer's wall clock is not authoritative.
export function isPickupExpired(reservation: Pick<ReadyPickupReservation, 'status' | 'expired' | 'pickupDeadline' | 'checkedAt'>, elapsedMs = 0): boolean {
  if (reservation.status === 'FULFILLED') return false
  if (reservation.expired || reservation.status === 'EXPIRED') return true
  if (!reservation.pickupDeadline || !reservation.checkedAt) return false
  const deadline = Date.parse(reservation.pickupDeadline)
  const checked = Date.parse(reservation.checkedAt)
  return Number.isFinite(deadline) && Number.isFinite(checked) && checked + Math.max(0, elapsedMs) > deadline
}

export const pickupService = {
  checkReader: async (cardNumber: string): Promise<ReaderLoanEligibility> => {
    const response = await apiClient.get<ReaderLoanEligibility>('/loans/reader-eligibility', {
      params: { cardNumber: cardNumber.trim() },
    })
    return response.data
  },
  createLoan: async (id: number, cardNumber: string, dates?: LoanDatePreview,
    requestId?: string, overrideRequested = false, overrideReason = ''): Promise<ReservationLoanResult> => {
    const response = await apiClient.post<ReservationLoanResult>(`/reservations/${id}/loan`, {
      cardNumber: cardNumber.trim(),
      ...(requestId ? { requestId } : {}),
      ...(overrideRequested ? { overrideRequested: true, overrideReason: overrideReason.trim() } : {}),
      ...(dates ? { expectedBorrowDate: dates.borrowDate, expectedDueAt: dates.dueAt, expectedLoanDays: dates.loanDays } : {}),
    })
    return response.data
  },
  loanContext: async (id: number): Promise<ReservationLoanContext> => {
    const response = await apiClient.post<ReservationLoanContext>(`/reservations/${id}/pickup-check`)
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
    const context = await pickupService.loanContext(id)
    if (!context.reservation || context.reservation.id !== id) {
      throw new Error('Không tải được thông tin đơn đặt giữ. Vui lòng thử lại.')
    }
    return { ...context.reservation, ...context }
  },
}
