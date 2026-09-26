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
  memberCode: string
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
  registrationStatus: string
  rejectionReason?: string | null
  submittedAt: string
  reviewedAt?: string | null
  reviewedBy?: number | null
}

export const readerService = {
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
