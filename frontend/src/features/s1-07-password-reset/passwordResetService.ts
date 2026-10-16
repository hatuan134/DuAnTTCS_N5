import { apiClient } from '../../core/api/apiClient'

export interface ForgotPasswordResponse {
  message: string
}

export interface ValidateResetPasswordTokenResponse {
  valid: boolean
  email?: string
  fullName?: string
  message: string
}

export interface ResetPasswordRequest {
  token: string
  newPassword: string
  confirmPassword: string
}

export interface ResetPasswordResponse {
  message: string
}

export const passwordResetService = {
  async requestForgotPassword(email: string): Promise<ForgotPasswordResponse> {
    const response = await apiClient.post<ForgotPasswordResponse>('/auth/forgot-password', {
      email: email.trim().toLowerCase(),
    })
    return response.data
  },

  async validateToken(token: string): Promise<ValidateResetPasswordTokenResponse> {
    const response = await apiClient.get<ValidateResetPasswordTokenResponse>(
      `/auth/reset-password/validate?token=${encodeURIComponent(token.trim())}`,
    )
    return response.data
  },

  async resetPassword(data: ResetPasswordRequest): Promise<ResetPasswordResponse> {
    const response = await apiClient.post<ResetPasswordResponse>('/auth/reset-password', {
      token: data.token.trim(),
      newPassword: data.newPassword,
      confirmPassword: data.confirmPassword,
    })
    return response.data
  },
}
