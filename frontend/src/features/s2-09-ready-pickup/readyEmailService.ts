import { apiClient } from '../../core/api/apiClient'
export interface PickupEmailAttempt {
  id: number
  attemptNumber: number
  startedAt: string
  finishedAt: string | null
  outcome: string
  errorCode: string | null
  errorMessage: string | null
  simulated: boolean
  actorId: number | null
}
export interface PickupEmailNotification {
  id: number
  reservationId: number
  notificationType: string
  recipientEmail: string | null
  readerName: string
  bookTitle: string
  pickupDeadline: string | null
  pickupLocation: string | null
  eventAt: string
  sendBefore: string
  sentAt: string | null
  completedAt: string | null
  status: 'PENDING' | 'RETRY' | 'SENDING' | 'SENT' | 'FAILED' | 'SKIPPED'
  attemptCount: number
  subject: string
  emailContent: string
  errorCode: string | null
  errorMessage: string | null
  attempts: PickupEmailAttempt[]
}
export interface PickupEmailHistory {
  reservationId: number
  reservationStatus: string
  pickupLocation: string | null
  timeZone: string
  notifications: PickupEmailNotification[]
}
export const pickupEmailLabels: Record<string, string> = {
  SENDING: 'Đang gửi', STARTED: 'Bắt đầu gửi', PENDING: 'Chờ gửi', RETRY: 'Chờ thử lại', SENT: 'Đã gửi', FAILED: 'Gửi thất bại', SKIPPED: 'Không gửi',
}
export const readyEmailService = {
  async history(id: number): Promise<PickupEmailHistory> {
    return (await apiClient.get<PickupEmailHistory>(`/reservations/${id}/ready-email`)).data
  },
  async trigger(id: number, simulateFailure: boolean): Promise<{ message: string; history: PickupEmailHistory }> {
    return (await apiClient.post<{ message: string; history: PickupEmailHistory }>(
      `/reservations/${id}/ready-email/trigger`, { simulateFailure })).data
  },
}
