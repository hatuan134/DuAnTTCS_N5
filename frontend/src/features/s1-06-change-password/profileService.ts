import { apiClient } from '../../core/api/apiClient'

export interface ReaderSelfProfile {
  userId: number
  fullName: string
  dateOfBirth: string
  memberCode: string
  email: string
  phone?: string | null
  address?: string | null
  registrationStatus: string
  rejectionReason?: string | null
  cardNumber?: string | null
  cardTypeName?: string | null
  issuedAt?: string | null
  expiresAt?: string | null
  cardStatus?: string | null
}

export interface UpdateReaderContactPayload {
  phone: string
  address: string
  email: string
  currentPassword?: string
}

export interface ChangeReaderPasswordPayload {
  currentPassword: string
  newPassword: string
  confirmPassword: string
}

export interface MessageResponse {
  message: string
}

export const profileService = {
  getMine: async (): Promise<ReaderSelfProfile> => {
    const response = await apiClient.get<ReaderSelfProfile>(
      '/readers/me/profile',
    )
    return response.data
  },

  updateContact: async (
    payload: UpdateReaderContactPayload,
  ): Promise<ReaderSelfProfile> => {
    const response = await apiClient.put<ReaderSelfProfile>(
      '/readers/me/contact',
      payload,
    )
    return response.data
  },

  changePassword: async (
    payload: ChangeReaderPasswordPayload,
  ): Promise<MessageResponse> => {
    const response = await apiClient.put<MessageResponse>(
      '/readers/me/password',
      payload,
    )
    return response.data
  },
}
