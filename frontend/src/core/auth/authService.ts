import axios from 'axios'

import {
  clearAuthSession,
  getAccessExpiresAt,
  getAccessToken,
  getRefreshExpiresAt,
  getRefreshToken,
  saveAuthSession,
} from './authStorage'

import type {
  AuthSession,
} from './authStorage'

const API_BASE_URL =
  import.meta.env.VITE_API_URL ??
  'http://localhost:8080/api/v1'

const authApi = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
  timeout: 10000,
})

let refreshPromise:
  | Promise<AuthSession>
  | null = null

export async function login(
  email: string,
  password: string,
) {
  const response =
    await authApi.post<AuthSession>(
      '/auth/login',
      {
        email,
        password,
      },
    )

  saveAuthSession(response.data)
  return response.data
}

export async function refreshSession() {
  if (refreshPromise) {
    return refreshPromise
  }

  const refreshToken =
    getRefreshToken()

  if (!refreshToken) {
    clearAuthSession()
    throw new Error(
      'Không có refresh token.',
    )
  }

  refreshPromise = authApi
    .post<AuthSession>(
      '/auth/refresh',
      {
        refreshToken,
      },
    )
    .then((response) => {
      saveAuthSession(response.data)
      return response.data
    })
    .catch((error) => {
      clearAuthSession()
      throw error
    })
    .finally(() => {
      refreshPromise = null
    })

  return refreshPromise
}

export async function ensureValidSession() {
  const now = Date.now()
  const accessToken = getAccessToken()
  const accessExpiresAt =
    getAccessExpiresAt()

  if (
    accessToken &&
    accessExpiresAt > now + 5000
  ) {
    return true
  }

  const refreshToken =
    getRefreshToken()
  const refreshExpiresAt =
    getRefreshExpiresAt()

  if (
    !refreshToken ||
    refreshExpiresAt <= now
  ) {
    clearAuthSession()
    return false
  }

  try {
    await refreshSession()
    return true
  } catch {
    return false
  }
}
