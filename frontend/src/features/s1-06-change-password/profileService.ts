import { apiClient } from '../../core/api/apiClient'

export interface UserProfile {
  id: number
  fullName: string
  email: string
  phone?: string | null
  address?: string | null
  dateOfBirth?: string | null
  memberCode?: string | null
  role: string
  roleName: string
  hasCard: boolean
  cardNumber?: string | null
  cardTypeName?: string | null
  cardStatus?: string | null
  cardIssuedAt?: string | null
  cardExpiresAt?: string | null
}

export interface UpdateProfileRequest {
  email: string
  phone?: string
  address?: string
  currentPassword?: string
}

export interface ChangePasswordRequest {
  currentPassword: string
  newPassword: string
  confirmPassword: string
}

export interface MessageResponse {
  message: string
}

export const profileService = {
  getProfile: async (): Promise<UserProfile> => {
    const response = await apiClient.get<UserProfile>('/profile')
    return response.data
  },

  updateProfile: async (data: UpdateProfileRequest): Promise<UserProfile> => {
    const response = await apiClient.put<UserProfile>('/profile', data)
    return response.data
  },

  changePassword: async (data: ChangePasswordRequest): Promise<MessageResponse> => {
    const response = await apiClient.put<MessageResponse>('/profile/change-password', data)
    return response.data
  },
}
