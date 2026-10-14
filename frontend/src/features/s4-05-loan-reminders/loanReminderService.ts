import { apiClient } from '../../core/api/apiClient'

export interface LoanReminderItemDto {
  itemId: number
  loanId: number
  loanNumber: string
  bookId: number
  bookTitle: string
  copyBarcode: string
  dueDate: string
  daysOverdue: number
  estimatedFeeVnd: number
}

export interface LoanReminderGroupDto {
  readerId: number
  readerName: string
  recipientEmail: string
  reminderGroup: 'DUE_IN_2_DAYS' | 'DUE_TODAY'
  items: LoanReminderItemDto[]
  subject: string
  emailContentHtml: string
  alreadySentToday: boolean
}

export interface LoanReminderPreviewResponse {
  targetDate: string
  totalRecipients: number
  totalItems: number
  reminders: LoanReminderGroupDto[]
}

export interface LoanReminderExecutionResult {
  date: string
  totalRecipients: number
  sentCount: number
  failedCount: number
  skippedCount: number
  totalItemsCount: number
  triggeredBy: string
}

export interface LoanReminderLog {
  id: number
  reminderDate: string
  readerId: number
  readerName: string
  recipientEmail: string
  reminderGroup: 'DUE_IN_2_DAYS' | 'DUE_TODAY' | string
  loanItemCount: number
  loanNumbers: string
  bookTitles: string
  itemsDetail: string
  subject: string
  emailContent: string
  status: 'SENT' | 'FAILED' | 'SKIPPED'
  errorMessage: string | null
  triggeredBy: string
  triggeredByUserName: string | null
  isDryRun: boolean
  sentAt: string
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

  async trigger(targetDate?: string): Promise<LoanReminderExecutionResult> {
    const body = targetDate ? { targetDate } : {}
    const response = await apiClient.post<LoanReminderExecutionResult>('/loan-reminders/trigger', body)
    return response.data
  },

  async getHistory(params?: {
    date?: string
    reminderGroup?: string
    status?: string
    page?: number
    size?: number
  }): Promise<LoanReminderHistoryResponse> {
    const response = await apiClient.get<LoanReminderHistoryResponse>('/loan-reminders/history', { params })
    return response.data
  },
}
