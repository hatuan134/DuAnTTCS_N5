import { apiClient } from '../../core/api/apiClient'

export interface AutoCancelledReservation {
  id: number
  bookId: number
  bookTitle: string
  readerId: number
  readerName: string
  copyId: number | null
  barcode: string | null
  status: string
  reservedAt: string
  pickupDeadline: string | null
  cancelledAt: string
  cancelledByName: string
  cancellationReason: string
  copyOutcome: 'TRANSFERRED' | 'AVAILABLE' | 'NO_COPY' | string
  nextReservationId: number | null
  nextReaderName: string | null
  nextPickupDeadline: string | null
}

export interface AutoCancellationRun {
  id: number
  runDate: string
  startedAt: string
  finishedAt: string
  status: 'SUCCESS' | 'PARTIAL_FAILURE' | 'FAILED' | string
  totalIdentified: number
  totalCancelled: number
  totalTransferred: number
  totalReleased: number
  errorCount: number
  errorMessage: string | null
  triggeredBy: string
  cancelledReservations?: AutoCancelledReservation[]
}

export const autoCancellationService = {
  /**
   * S3-06.4: Lấy danh sách các đơn bị hệ thống tự động huỷ trong 30 ngày gần nhất
   */
  getLast30Days: async (): Promise<AutoCancelledReservation[]> => {
    const response = await apiClient.get<AutoCancelledReservation[]>(
      '/reservations/auto-cancelled-last-30-days',
    )
    return response.data
  },

  /**
   * S3-06.3: Lấy thông tin lần chạy kiểm tra tự động gần nhất
   */
  getLatestRun: async (): Promise<AutoCancellationRun | null> => {
    try {
      const response = await apiClient.get<AutoCancellationRun>(
        '/reservations/auto-cancel-runs/latest',
      )
      return response.data
    } catch (err: any) {
      if (err?.response?.status === 204 || err?.response?.status === 404) {
        return null
      }
      throw err
    }
  },

  /**
   * S3-06.3 / S3-06.4: Kích hoạt chạy quét đơn quá hạn ngay lập tức
   */
  triggerRun: async (): Promise<AutoCancellationRun> => {
    const response = await apiClient.post<AutoCancellationRun>(
      '/reservations/auto-cancel-runs/trigger',
    )
    return response.data
  },
}
