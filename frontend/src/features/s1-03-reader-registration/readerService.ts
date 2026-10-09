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

export const readerService = {
  getLoanHistory: async (id: number): Promise<ReaderLoanHistoryResponse> => {
    const response = await apiClient.get<ReaderLoanHistoryResponse>(`/readers/${id}/loan-history`)
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
