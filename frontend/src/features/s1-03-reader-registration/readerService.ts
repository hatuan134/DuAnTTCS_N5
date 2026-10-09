import { apiClient } from '../../core/api/apiClient'

export interface DuplicateCheckResponse {
  emailExists: boolean
  memberCodeExists: boolean
  emailMessage?: string | null
  memberCodeMessage?: string | null
  suggestForgotPassword: boolean
  forgotPasswordUrl: string
}

export interface ReaderRegistrationRequest {
  fullName: string
  email: string
  dateOfBirth: string
  phone?: string
  address?: string
  password: string
}

export interface ReaderRegistrationResponse {
  userId: number
  fullName: string
  email: string
  memberCode: string
  registrationStatus: string
  submittedAt: string
  message: string
}

export interface ReaderProfileResponse {
  userId: number
  fullName: string
  email: string
  phone?: string | null
  address?: string | null
  userStatus: string
  memberCode: string
  dateOfBirth: string
  cardTypeName?: string | null
  registrationStatus: string
  rejectionReason?: string | null
  submittedAt: string
  reviewedAt?: string | null
  reviewedBy?: number | null
}


export interface ReaderHistoryItem {
  id: number
  bookTitle: string
  barcode: string
  borrowedAt: string
  dueAt: string | null
  returnedAt: string | null
  status: 'BORROWED' | 'RETURNED'
  returnedLate: boolean
}

export interface ReaderHistoryLoan {
  id: number
  loanNumber: string
  borrowedAt: string
  status: 'EMPTY' | 'BORROWED' | 'PARTIALLY_RETURNED' | 'RETURNED'
  returnedLate: boolean
  items: ReaderHistoryItem[]
}

export interface ReaderLoanHistoryResponse {
  profile: ReaderProfileResponse
  openLoanCount: number
  totalBorrowCount: number
  lateReturnCount: number
  loans: ReaderHistoryLoan[]
}

export interface ReaderHistoryFilters {
  fromDate: string
  toDate: string
}

export interface ReaderHistoryDateError {
  field: keyof ReaderHistoryFilters
  message: string
}

export function validateReaderHistoryDates(filters: ReaderHistoryFilters): ReaderHistoryDateError | null {
  for (const [field, label] of [['fromDate', 'Từ ngày'], ['toDate', 'Đến ngày']] as const) {
    const value = filters[field].trim()
    if (!value) continue
    const date = new Date(`${value}T00:00:00Z`)
    if (!/^\d{4}-\d{2}-\d{2}$/.test(value) || value.slice(0, 4) === '0000'
      || !Number.isFinite(date.getTime()) || date.toISOString().slice(0, 10) !== value) {
      return { field, message: `${label} phải là ngày hợp lệ, định dạng yyyy-MM-dd, năm từ 0001 đến 9999.` }
    }
  }
  if (filters.fromDate.trim() && filters.toDate.trim() && filters.fromDate.trim() > filters.toDate.trim()) {
    return { field: 'toDate', message: 'Từ ngày không được lớn hơn Đến ngày.' }
  }
  return null
}

export interface ReaderHistoryCsvExport {
  blob: Blob
  filename: string
  rowCount: number
}

export function downloadReaderHistoryCsv(result: ReaderHistoryCsvExport): void {
  const url = URL.createObjectURL(result.blob)
  const anchor = document.createElement('a')
  try {
    anchor.href = url
    anchor.download = result.filename
    document.body.appendChild(anchor)
    anchor.click()
  } finally {
    anchor.remove()
    URL.revokeObjectURL(url)
  }
}

export const readerService = {
  exportLoanHistory: async (id: number, filters: ReaderHistoryFilters): Promise<ReaderHistoryCsvExport> => {
    try {
      const response = await apiClient.get<Blob>(`/readers/${id}/loan-history/export`, {
        responseType: 'blob',
        params: {
          ...(filters.fromDate.trim() ? { fromDate: filters.fromDate.trim() } : {}),
          ...(filters.toDate.trim() ? { toDate: filters.toDate.trim() } : {}),
        },
      })
      const disposition = String(response.headers['content-disposition'] ?? '')
      const filename = /filename="?([A-Za-z0-9_.-]+\.csv)"?/i.exec(disposition)?.[1]
      const rowCount = Number(response.headers['x-csv-row-count'])
      if (!filename || !Number.isSafeInteger(rowCount) || rowCount < 0) {
        throw new Error('Không nhận được thông tin tệp CSV hợp lệ. Vui lòng thử lại.')
      }
      return { blob: response.data, filename, rowCount }
    } catch (error) {
      // Axios receives JSON error responses as a Blob when responseType is blob.
      const failure = error as { response?: { data?: unknown } }
      if (failure.response?.data instanceof Blob) {
        try { failure.response.data = JSON.parse(await failure.response.data.text()) } catch { /* Keep fallback message. */ }
      }
      throw error
    }
  },

  getLoanHistory: async (id: number, filters?: ReaderHistoryFilters): Promise<ReaderLoanHistoryResponse> => {
    const fromDate = filters?.fromDate.trim()
    const toDate = filters?.toDate.trim()
    const response = await apiClient.get<ReaderLoanHistoryResponse>(`/readers/${id}/loan-history`, {
      params: {
        ...(fromDate ? { fromDate } : {}),
        ...(toDate ? { toDate } : {}),
      },
    })
    return response.data
  },

  checkDuplicate: async (
    email?: string,
    memberCode?: string,
  ): Promise<DuplicateCheckResponse> => {
    const response = await apiClient.get<DuplicateCheckResponse>(
      '/readers/check-duplicate',
      {
        params: {
          email: email?.trim() || undefined,
          memberCode: memberCode?.trim() || undefined,
        },
      },
    )
    return response.data
  },

  register: async (
    data: ReaderRegistrationRequest,
  ): Promise<ReaderRegistrationResponse> => {
    const response = await apiClient.post<ReaderRegistrationResponse>(
      '/readers/register',
      data,
    )
    return response.data
  },

  getAllReaders: async (): Promise<ReaderProfileResponse[]> => {
    const response = await apiClient.get<ReaderProfileResponse[]>('/readers')
    return response.data
  },

  getReaderById: async (id: number): Promise<ReaderProfileResponse> => {
    const response = await apiClient.get<ReaderProfileResponse>(`/readers/${id}`)
    return response.data
  },
}
