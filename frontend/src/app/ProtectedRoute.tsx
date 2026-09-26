import {
  useEffect,
  useState,
} from 'react'

import {
  Navigate,
  Outlet,
  useLocation,
} from 'react-router-dom'

import {
  ensureValidSession,
} from '../core/auth/authService'

type SessionState =
  | 'checking'
  | 'authenticated'
  | 'anonymous'

export default function ProtectedRoute() {
  const location =
    useLocation()

  const [sessionState, setSessionState] =
    useState<SessionState>(
      'checking',
    )

  useEffect(() => {
    let active = true

    ensureValidSession()
      .then((valid) => {
        if (!active) {
          return
        }

        setSessionState(
          valid
            ? 'authenticated'
            : 'anonymous',
        )
      })

    return () => {
      active = false
    }
  }, [])

  if (
    sessionState ===
    'checking'
  ) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-slate-50">
        <div className="flex items-center gap-3 text-sm text-slate-500">
          <span className="h-5 w-5 animate-spin rounded-full border-2 border-slate-300 border-t-blue-600" />
          Đang kiểm tra phiên đăng nhập...
        </div>
      </div>
    )
  }

  if (
    sessionState ===
    'anonymous'
  ) {
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
