import { apiClient } from '../../core/api/apiClient'

export type ReminderGroupType =
  | 'DUE_IN_2_DAYS'
  | 'DUE_TODAY'
  | 'OVERDUE_1_DAY'
  | 'OVERDUE_7_DAYS'
  | 'OVERDUE_30_DAYS'

export interface LoanReminderItemDto {
  itemId: number
  loanId: number
  loanNumber: string
  bookId: number
  bookTitle: string
  copyBarcode: string
  dueDate: string
  daysOverdue: number
  openOverdueDays?: number
  estimatedFeeVnd: number
}

export interface LoanReminderGroupDto {
  readerId: number
  readerName: string
  recipientEmail: string
  reminderGroup: ReminderGroupType
  items: LoanReminderItemDto[]
  totalEstimatedFeeVnd?: number
  subject: string
  emailContentHtml: string
  alreadySentToday: boolean
}

export interface LoanReminderPreviewResponse {
  targetDate: string
  totalRecipients: number
  totalItems: number
  grandTotalEstimatedFeeVnd?: number
  reminders: LoanReminderGroupDto[]
}

export interface LoanReminderExecutionResult {
  date: string
  totalRecipients: number
  sentCount: number
  failedCount: number
  skippedCount: number
  totalItemsCount: number
  grandTotalEstimatedFeeVnd?: number
  triggeredBy: string
}

export interface LoanReminderRetryAttempt {
  id: number
  reminderLogId: number
  attemptNumber: number
  attemptedAt: string
  status: 'SUCCESS' | 'FAILED'
  errorMessage: string | null
  triggeredBy: string
  triggeredByUserName: string | null
}

export interface LoanReminderRetryExecutionResult {
  totalProcessed: number
  succeededCount: number
  stillPendingCount: number
  maxRetriesReachedCount: number
  executionSummary?: string
}

export function retryFeedback(result: LoanReminderRetryExecutionResult): { message: string; tone: 'success' | 'warning' | 'info' } {
  const total = result.totalProcessed ?? 0
  const succeeded = result.succeededCount ?? 0
  const pending = result.stillPendingCount ?? 0
  const failed = result.maxRetriesReachedCount ?? 0
  return {
    message: result.executionSummary || (total === 0 ? 'Không có email chờ thử lại.' :
      `Đã xử lý ${total} email: ${succeeded} thành công, ${pending} chờ thử lại, ${failed} hết lượt thử.`),
    tone: failed > 0 ? 'warning' : pending > 0 || total === 0 ? 'info' : 'success',
  }
}

export interface LoanReminderLog {
  id: number
  reminderDate: string
  readerId: number
  readerName: string
  readerCardNumber?: string | null
  recipientEmail: string
  reminderGroup: ReminderGroupType | string
  loanItemCount: number
  totalEstimatedFeeVnd?: number
  loanNumbers: string
  bookTitles: string
  itemsDetail: string
  subject: string
  emailContent: string
  status: 'SENT' | 'FAILED' | 'PENDING_RETRY' | 'FAILED_MAX_RETRIES' | 'SKIPPED'
  errorMessage: string | null
  retryCount: number
  maxRetries: number
  triggeredBy: string
  triggeredByUserName: string | null
  isDryRun: boolean
  sentAt: string
  attempts?: LoanReminderRetryAttempt[]
}

export interface LoanReminderHistoryResponse {
  content: LoanReminderLog[]
  totalElements: number
  page: number
  size: number
  totalPages: number
}

export const loanReminderService = {
  async getPreview(date?: string): Promise<LoanReminderPreviewResponse> {
    const params = date ? { date } : {}
    const response = await apiClient.get<LoanReminderPreviewResponse>('/loan-reminders/preview', { params })
    return response.data
  },

  async trigger(params?: string | { targetDate?: string; simulateFailure?: boolean }): Promise<LoanReminderExecutionResult> {
    const body: { targetDate?: string; simulateFailure?: boolean } = {}
    if (typeof params === 'string') {
      body.targetDate = params
    } else if (params) {
      if (params.targetDate !== undefined) {
        body.targetDate = params.targetDate
      }
      if (params.simulateFailure !== undefined) {
        body.simulateFailure = params.simulateFailure
      }
    }
    const response = await apiClient.post<LoanReminderExecutionResult>('/loan-reminders/trigger', body)
    return response.data
  },

  async retryPending(params?: { logId?: number; simulateFailure?: boolean }): Promise<LoanReminderRetryExecutionResult> {
    const body = {
      logId: params?.logId,
      simulateFailure: params?.simulateFailure ?? false,
    }
    const response = await apiClient.post<LoanReminderRetryExecutionResult>('/loan-reminders/retry-pending', body)
    return response.data
  },

  async retrySingle(id: number, params?: { simulateFailure?: boolean }): Promise<LoanReminderRetryExecutionResult> {
    const body = {
      logId: id,
      simulateFailure: params?.simulateFailure ?? false,
    }
    const response = await apiClient.post<LoanReminderRetryExecutionResult>(`/loan-reminders/${id}/retry`, body)
    return response.data
  },

  async getHistory(params?: {
    date?: string
    reminderGroup?: string
    status?: string
    search?: string
    cardNumber?: string
    page?: number
    size?: number
  }): Promise<LoanReminderHistoryResponse> {
    const response = await apiClient.get<LoanReminderHistoryResponse>('/loan-reminders/history', { params })
    return response.data
  },
}
