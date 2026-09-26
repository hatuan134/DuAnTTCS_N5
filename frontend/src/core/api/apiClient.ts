import axios from 'axios'

import {
  clearAuthSession,
  getAccessToken,
} from '../auth/authStorage'

import {
  refreshSession,
} from '../auth/authService'

export const apiClient = axios.create({
  baseURL:
    import.meta.env.VITE_API_URL ??
    'http://localhost:8080/api/v1',

  headers: {
    'Content-Type': 'application/json',
  },

  timeout: 10000,
})

apiClient.interceptors.request.use(
  (config) => {
    const accessToken =
      getAccessToken()

    if (accessToken) {
      config.headers.Authorization =
        `Bearer ${accessToken}`
    }

    return config
  },
)

apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest =
      error.config as
        | (typeof error.config & {
            _retry?: boolean
          })
        | undefined

    if (
      error.response?.status !== 401 ||
      !originalRequest ||
      originalRequest._retry
    ) {
      return Promise.reject(error)
    }

    originalRequest._retry = true

    try {
      const session =
        await refreshSession()

      originalRequest.headers =
        originalRequest.headers ?? {}

      originalRequest.headers.Authorization =
        `Bearer ${session.accessToken}`

      return apiClient(
        originalRequest,
      )
    } catch (refreshError) {
      clearAuthSession()

      if (
        window.location.pathname !==
        '/login'
      ) {
        window.location.assign(
          '/login',
        )
      }

      return Promise.reject(
        refreshError,
      )
    }
  },
)
