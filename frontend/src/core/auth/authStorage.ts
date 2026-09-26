export type AuthUser = {
  id: number
  fullName: string
  email: string
  role: string
}

export type AuthSession = {
  tokenType: string
  accessToken: string
  accessTokenExpiresAt: string
  refreshToken: string
  refreshTokenExpiresAt: string
  user: AuthUser
}

const ACCESS_TOKEN_KEY =
  'libra_access_token'

const ACCESS_EXPIRES_KEY =
  'libra_access_expires_at'

const REFRESH_TOKEN_KEY =
  'libra_refresh_token'

const REFRESH_EXPIRES_KEY =
  'libra_refresh_expires_at'

const CURRENT_USER_KEY =
  'libra_current_user'

export function saveAuthSession(
  session: AuthSession,
) {
  localStorage.setItem(
    ACCESS_TOKEN_KEY,
    session.accessToken,
  )

  localStorage.setItem(
    ACCESS_EXPIRES_KEY,
    String(
      new Date(
        session.accessTokenExpiresAt,
      ).getTime(),
    ),
  )

  localStorage.setItem(
    REFRESH_TOKEN_KEY,
    session.refreshToken,
  )

  localStorage.setItem(
    REFRESH_EXPIRES_KEY,
    String(
      new Date(
        session.refreshTokenExpiresAt,
      ).getTime(),
    ),
  )

  localStorage.setItem(
    CURRENT_USER_KEY,
    JSON.stringify(session.user),
  )
}

export function clearAuthSession() {
  localStorage.removeItem(
    ACCESS_TOKEN_KEY,
  )
  localStorage.removeItem(
    ACCESS_EXPIRES_KEY,
  )
  localStorage.removeItem(
    REFRESH_TOKEN_KEY,
  )
  localStorage.removeItem(
    REFRESH_EXPIRES_KEY,
  )
  localStorage.removeItem(
    CURRENT_USER_KEY,
  )
}

export function getAccessToken() {
  return localStorage.getItem(
    ACCESS_TOKEN_KEY,
  )
}

export function getAccessExpiresAt() {
  return Number(
    localStorage.getItem(
      ACCESS_EXPIRES_KEY,
    ) ?? '0',
  )
}

export function getRefreshToken() {
  return localStorage.getItem(
    REFRESH_TOKEN_KEY,
  )
}

export function getRefreshExpiresAt() {
  return Number(
    localStorage.getItem(
      REFRESH_EXPIRES_KEY,
    ) ?? '0',
  )
}

export function getCurrentUser(): AuthUser | null {
  const raw = localStorage.getItem(
    CURRENT_USER_KEY,
  )

  if (!raw) {
    return null
  }

  try {
    return JSON.parse(raw) as AuthUser
  } catch {
    return null
  }
}
