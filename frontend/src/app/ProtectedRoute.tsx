import {
  Navigate,
  Outlet,
  useLocation,
} from 'react-router-dom'

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

const THIRTY_MINUTES =
  30 * 60 * 1000

function hasValidSession() {
  const accessToken =
    localStorage.getItem(
      ACCESS_TOKEN_KEY,
    )

  const accessExpiresAt =
    Number(
      localStorage.getItem(
        ACCESS_EXPIRES_KEY,
      ),
    )

  const refreshToken =
    localStorage.getItem(
      REFRESH_TOKEN_KEY,
    )

  const refreshExpiresAt =
    Number(
      localStorage.getItem(
        REFRESH_EXPIRES_KEY,
      ),
    )

  const now = Date.now()

  // Access token vẫn còn hạn
  if (
    accessToken &&
    accessExpiresAt > now
  ) {
    return true
  }

  // Access token hết hạn nhưng
  // refresh token vẫn còn hạn
  if (
    refreshToken &&
    refreshExpiresAt > now
  ) {
    localStorage.setItem(
      ACCESS_TOKEN_KEY,
      `mock-access-${now}`,
    )

    localStorage.setItem(
      ACCESS_EXPIRES_KEY,
      String(
        now + THIRTY_MINUTES,
      ),
    )

    return true
  }

  // Phiên đã hết hạn hoàn toàn
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

  return false
}

export default function ProtectedRoute() {
  const location =
    useLocation()

  if (!hasValidSession()) {
    return (
      <Navigate
        to="/login"
        replace
        state={{
          from: location.pathname,
        }}
      />
    )
  }

  return <Outlet />
}