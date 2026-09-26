import axios from 'axios'

import { apiClient } from '../../core/api/apiClient'

export type AccountRole =
  | 'ADMIN'
  | 'LIBRARY_MANAGER'
  | 'LIBRARIAN'

export type AccountStatus =
  | 'ACTIVE'
  | 'LOCKED'

export type Account = {
  id: number
  fullName: string
  email: string
  phone: string | null
  role: AccountRole
  roleName: string
  status: string
  createdAt: string
  updatedAt: string
}

export type CreateAccountPayload = {
  fullName: string
  email: string
  phone: string
  role: AccountRole
  status: AccountStatus
}

export type AccountFilters = {
  search?: string
  role?: string
  status?: string
}

export type InitialPasswordTokenInfo = {
  valid: boolean
  email: string
  fullName: string
}

type ApiErrorBody = {
  message?: string
  code?: string
}

const API_BASE_URL =
  import.meta.env.VITE_API_URL ??
  'http://localhost:8080/api/v1'

const publicApi = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
  timeout: 10000,
})

export async function getAccounts(
  filters: AccountFilters = {},
) {
  const response = await apiClient.get<Account[]>(
    '/admin/accounts',
    {
      params: {
        search: filters.search || undefined,
        role: filters.role || undefined,
        status: filters.status || undefined,
      },
    },
  )

  return response.data
}

export async function createAccount(
  payload: CreateAccountPayload,
) {
  const response = await apiClient.post<Account>(
    '/admin/accounts',
    payload,
  )

  return response.data
}

export async function updateAccountStatus(
  accountId: number,
  status: AccountStatus,
) {
  const response = await apiClient.patch<Account>(
    `/admin/accounts/${accountId}/status`,
    { status },
  )

  return response.data
}

export async function validateInitialPasswordToken(
  token: string,
) {
  const response = await publicApi.get<InitialPasswordTokenInfo>(
    '/auth/initial-password',
    {
      params: { token },
    },
  )

  return response.data
}

export async function setInitialPassword(
  token: string,
  password: string,
) {
  const response = await publicApi.post<{ message: string }>(
    '/auth/initial-password',
    {
      token,
      password,
    },
  )

  return response.data
}

export function getApiErrorMessage(
  error: unknown,
  fallback: string,
) {
  if (axios.isAxiosError<ApiErrorBody>(error)) {
    return (
      error.response?.data?.message ||
      (error.code === 'ECONNABORTED'
        ? 'Yêu cầu mất quá nhiều thời gian. Vui lòng thử lại.'
        : fallback)
    )
  }

  return fallback
}
