import { useEffect, useMemo, useState } from 'react'
import type { FormEvent } from 'react'

import axios from 'axios'

import {
  ArrowRight,
  BookOpen,
  CheckCircle2,
  Eye,
  EyeOff,
  KeyRound,
  LockKeyhole,
  Mail,
  ShieldCheck,
} from 'lucide-react'

import {
  Link,
  useLocation,
  useNavigate,
} from 'react-router-dom'

import FeedbackAlert from '../../components/ui/FeedbackAlert'
import {
  login,
} from '../../core/auth/authService'

type ApiErrorResponse = {
  message?: string
  code?: string
  timestamp?: string
  details?: {
    failedLoginAttempts?: number
    maxFailedAttempts?: number
    remainingAttempts?: number
    lockedUntil?: string
  }
}

type LoginLockState = {
  failedLoginAttempts: number
  maxFailedAttempts: number
  lockedUntil?: string
}

const LOGIN_STATE_PREFIX = 'libra_login_state:'

function getStoredLoginState(email: string): LoginLockState | null {
  if (!email) return null

  try {
    const raw = window.localStorage.getItem(`${LOGIN_STATE_PREFIX}${email}`)
    if (!raw) return null
    const parsed = JSON.parse(raw) as LoginLockState

    if (parsed.lockedUntil && new Date(parsed.lockedUntil).getTime() <= Date.now()) {
      window.localStorage.removeItem(`${LOGIN_STATE_PREFIX}${email}`)
      return null
    }

    return parsed
  } catch {
    return null
  }
}

function storeLoginState(email: string, state: LoginLockState | null) {
  if (!email) return
  const key = `${LOGIN_STATE_PREFIX}${email}`

  if (!state) {
    window.localStorage.removeItem(key)
    return
  }

  window.localStorage.setItem(key, JSON.stringify(state))
}

export default function LoginPage() {
  const navigate =
    useNavigate()

  const location =
    useLocation()

  const [email, setEmail] =
    useState('')

  const [password, setPassword] =
    useState('')

  const [
    showPassword,
    setShowPassword,
  ] = useState(false)

  const [error, setError] =
    useState('')

  const [, setFailedLoginAttempts] =
    useState<number | null>(null)

  const [maxFailedAttempts, setMaxFailedAttempts] =
    useState(5)

  const [lockedUntil, setLockedUntil] =
    useState<string | null>(null)

  const [clockTick, setClockTick] =
    useState(0)

  const [loading, setLoading] =
    useState(false)

  const normalizedEmail = email.trim().toLowerCase()

  useEffect(() => {
    const stored = getStoredLoginState(normalizedEmail)
    setFailedLoginAttempts(stored?.failedLoginAttempts ?? null)
    setMaxFailedAttempts(stored?.maxFailedAttempts ?? 5)
    setLockedUntil(stored?.lockedUntil ?? null)
  }, [normalizedEmail])

  useEffect(() => {
    if (!lockedUntil) return

    const timer = window.setInterval(() => {
      setClockTick((value) => value + 1)
    }, 1000)

    return () => window.clearInterval(timer)
  }, [lockedUntil])

  const isTemporarilyLocked = useMemo(() => {
    void clockTick
    return Boolean(lockedUntil && new Date(lockedUntil).getTime() > Date.now())
  }, [lockedUntil, clockTick])

  useEffect(() => {
    if (lockedUntil && !isTemporarilyLocked) {
      storeLoginState(normalizedEmail, null)
      setLockedUntil(null)
      setFailedLoginAttempts(null)
    }
  }, [isTemporarilyLocked, lockedUntil, normalizedEmail])

  const lockedUntilLabel = useMemo(() => {
    if (!lockedUntil) return ''
    return new Date(lockedUntil).toLocaleString('vi-VN')
  }, [lockedUntil])

  const handleSubmit = async (
    event: FormEvent<HTMLFormElement>,
  ) => {
    event.preventDefault()
    setError('')

    if (isTemporarilyLocked) {
      setError(`Tài khoản đang bị khóa tạm đến ${lockedUntilLabel}.`)
      return
    }

    if (
      !normalizedEmail ||
      !password
    ) {
      setError(
        'Vui lòng nhập email và mật khẩu.',
      )
      return
    }

    setLoading(true)

    try {
      const session = await login(
        normalizedEmail,
        password,
      )

      storeLoginState(normalizedEmail, null)
      setFailedLoginAttempts(null)
      setLockedUntil(null)

      const state =
        location.state as
          | {
              from?: string
            }
          | null

      navigate(
        state?.from ||
          (session.user.role === 'READER' ? '/catalog' : '/dashboard'),
        {
          replace: true,
        },
      )
    } catch (requestError) {
      if (axios.isAxiosError<ApiErrorResponse>(requestError)) {
        const responseData = requestError.response?.data
        const details = responseData?.details

        if (typeof details?.failedLoginAttempts === 'number') {
          setFailedLoginAttempts(details.failedLoginAttempts)
        }
        if (typeof details?.maxFailedAttempts === 'number') {
          setMaxFailedAttempts(details.maxFailedAttempts)
        }
        if (details?.lockedUntil) {
          setLockedUntil(details.lockedUntil)
        }

        if (typeof details?.failedLoginAttempts === 'number') {
          storeLoginState(normalizedEmail, {
            failedLoginAttempts: details.failedLoginAttempts,
            maxFailedAttempts: details.maxFailedAttempts ?? maxFailedAttempts,
            lockedUntil: details.lockedUntil,
          })
        }

        const fallbackMessage = 'Không thể kết nối tới hệ thống. Vui lòng thử lại.'
        const responseMessage = responseData?.message ?? fallbackMessage
        const remainingAttempts = details?.remainingAttempts

        if (
          responseData?.code === 'INVALID_CREDENTIALS' &&
          typeof remainingAttempts === 'number'
        ) {
          setError(
            `${responseMessage} Bạn còn ${remainingAttempts} lượt đăng nhập trước khi tài khoản bị khóa tạm.`,
          )
        } else if (
          responseData?.code === 'ACCOUNT_TEMPORARILY_LOCKED' &&
          details?.lockedUntil
        ) {
          const retryAt = new Date(details.lockedUntil).toLocaleString('vi-VN')
          setError(`${responseMessage} Có thể thử lại sau ${retryAt}.`)
        } else {
          setError(responseMessage)
        }
      } else {
        setError(
          'Không thể kết nối tới hệ thống. Vui lòng thử lại.',
        )
      }
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="auth-page min-h-screen bg-slate-50">
      <div className="grid min-h-screen lg:grid-cols-[1.05fr_0.95fr]">

        {/* =========================
            BÊN TRÁI
        ========================= */}
        <section className="relative hidden overflow-hidden bg-slate-950 p-12 text-white lg:flex lg:flex-col lg:justify-between">
          {/* Logo */}
          <div className="relative z-10">
            <div className="flex items-center gap-3">
              <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-blue-600 shadow-lg shadow-blue-600/20">
                <BookOpen
                  size={25}
                />
              </div>

              <div>
                <p className="text-xl font-bold tracking-wide">
                  LIBRA
                </p>

                <p className="text-sm text-slate-400">
                  Hệ thống quản lý thư viện
                </p>
              </div>
            </div>
          </div>

          {/* Nội dung chính */}
          <div className="relative z-10 max-w-xl">
            <div className="mb-5 inline-flex items-center gap-2 rounded-full border border-slate-700 bg-slate-900 px-3 py-1.5 text-sm text-slate-300">
              <ShieldCheck
                size={16}
              />
              Quản lý thư viện an toàn & tập trung
            </div>

            <h1 className="text-5xl font-bold leading-[1.12] tracking-tight">
              Quản lý thư viện
              <span className="block text-blue-400">
                đơn giản hơn mỗi ngày.
              </span>
            </h1>

            <p className="mt-6 max-w-lg text-base leading-7 text-slate-400">
              Quản lý tài khoản, bạn đọc,
              thẻ thư viện, chính sách mượn
              và các nghiệp vụ quan trọng
              trong một hệ thống thống nhất.
            </p>

            <div className="mt-10 grid grid-cols-3 gap-3">
              <FeatureBox
                title="4 vai trò"
                text="Phân quyền rõ ràng"
              />

              <FeatureBox
                title="Bảo mật"
                text="Khóa đăng nhập"
              />

              <FeatureBox
                title="Theo dõi"
                text="Nhật ký hoạt động"
              />
            </div>
          </div>

          {/* Footer */}
          <div className="relative z-10 flex items-center justify-between text-sm text-slate-500">
            <span>
              LIBRA © 2026
            </span>

            <span>
              Giai đoạn 1
            </span>
          </div>

          {/* Trang trí */}
          <div className="absolute -right-40 -top-40 h-96 w-96 rounded-full bg-blue-600/10 blur-3xl" />
          <div className="absolute -bottom-40 left-20 h-96 w-96 rounded-full bg-indigo-500/10 blur-3xl" />
        </section>

        {/* =========================
            BÊN PHẢI
        ========================= */}
        <section className="flex items-center justify-center px-5 py-10 sm:px-10 lg:px-14">
          <div className="w-full max-w-md">

            {/* Logo mobile */}
            <div className="mb-10 lg:hidden">
              <div className="flex items-center gap-3">
                <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-blue-600 text-white">
                  <BookOpen
                    size={22}
                  />
                </div>

                <div>
                  <p className="font-bold text-slate-900">
                    LIBRA
                  </p>

                  <p className="text-xs text-slate-500">
                    Quản lý thư viện
                  </p>
                </div>
              </div>
            </div>

            {/* Heading */}
            <div>
              <p className="text-sm font-semibold uppercase tracking-[0.18em] text-blue-600">
                Chào mừng trở lại
              </p>

              <h2 className="mt-2 text-3xl font-bold tracking-tight text-slate-950">
                Đăng nhập hệ thống
              </h2>

              <p className="mt-2 text-sm leading-6 text-slate-500">
                Sử dụng tài khoản được cấp để
                truy cập hệ thống quản lý thư viện.
              </p>
            </div>

            {/* Form */}
            <form
              onSubmit={handleSubmit}
              className="mt-8 space-y-5"
            >
              <div>
                <label className="mb-2 block text-sm font-semibold text-slate-700">
                  Email
                </label>

                <div className="relative">
                  <Mail
                    size={18}
                    className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400"
                  />

                  <input
                    type="email"
                    value={email}
                    onChange={(event) => {
                      setEmail(
                        event.target.value,
                      )

                      setError('')
                    }}
                    placeholder="name@example.com"
                    className="w-full rounded-xl border border-slate-300 bg-white py-3.5 pl-11 pr-4 text-sm text-slate-900 outline-none transition placeholder:text-slate-400 focus:border-blue-500 focus:ring-4 focus:ring-blue-100"
                  />
                </div>
              </div>

              <div>
                <div className="mb-2 flex items-center justify-between">
                  <label className="text-sm font-semibold text-slate-700">
                    Mật khẩu
                  </label>

                  <Link
                    to="/forgot-password"
                    className="text-sm font-medium text-blue-600 transition hover:text-blue-700"
                  >
                    Quên mật khẩu?
                  </Link>
                </div>

                <div className="relative">
                  <LockKeyhole
                    size={18}
                    className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400"
                  />

                  <input
                    type={
                      showPassword
                        ? 'text'
                        : 'password'
                    }
                    value={password}
                    onChange={(event) => {
                      setPassword(
                        event.target.value,
                      )

                      setError('')
                    }}
                    className="w-full rounded-xl border border-slate-300 bg-white py-3.5 pl-11 pr-12 text-sm text-slate-900 outline-none transition focus:border-blue-500 focus:ring-4 focus:ring-blue-100"
                  />

                  <button
                    type="button"
                    onClick={() =>
                      setShowPassword(
                        !showPassword,
                      )
                    }
                    className="absolute right-3.5 top-1/2 -translate-y-1/2 text-slate-400 transition hover:text-slate-700"
                    aria-label="Hiện hoặc ẩn mật khẩu"
                  >
                    {showPassword ? (
                      <EyeOff
                        size={19}
                      />
                    ) : (
                      <Eye
                        size={19}
                      />
                    )}
                  </button>
                </div>
              </div>

              {error && (
                <FeedbackAlert
                  message={error}
                  tone="error"
                  onDismiss={() => setError('')}
                />
              )}

              <button
                type="submit"
                disabled={loading || isTemporarilyLocked}
                className="group flex w-full items-center justify-center gap-2 rounded-xl bg-blue-600 px-4 py-3.5 text-sm font-semibold text-white shadow-sm transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-60"
              >
                {loading ? (
                  <>
                    <span className="h-4 w-4 animate-spin rounded-full border-2 border-white/30 border-t-white" />
                    Đang đăng nhập...
                  </>
                ) : isTemporarilyLocked ? (
                  <>
                    <LockKeyhole size={18} />
                    Đang tạm khóa
                  </>
                ) : (
                  <>
                    Đăng nhập
                    <ArrowRight
                      size={18}
                      className="transition group-hover:translate-x-0.5"
                    />
                  </>
                )}
              </button>
            </form>

            {/* Security */}
            <div className="mt-5 flex items-start gap-3 rounded-xl border border-slate-200 bg-white p-4">
              <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-emerald-50 text-emerald-600">
                <ShieldCheck
                  size={18}
                />
              </div>

              <div>
                <p className="text-sm font-semibold text-slate-800">
                  Bảo vệ tài khoản
                </p>

                <p className="mt-1 text-xs leading-5 text-slate-500">
                  Hệ thống sẽ khóa đăng nhập
                  tạm thời khi đạt số lần nhập sai
                  tối đa được cấu hình.
                </p>
              </div>
            </div>

            {/* Demo account */}
            <div className="mt-4 rounded-xl border border-blue-100 bg-blue-50 p-4">
              <div className="flex items-center gap-2">
                <KeyRound
                  size={17}
                  className="text-blue-600"
                />

                <p className="text-sm font-semibold text-blue-900">
                  Tài khoản kiểm thử
                </p>
              </div>

              <div className="mt-3 grid gap-2 text-sm">
                <div className="flex items-center justify-between gap-3">
                  <span className="text-blue-700">
                    Email
                  </span>

                  <strong className="text-blue-950">
                    admin@libra.edu.vn
                  </strong>
                </div>

                <div className="flex items-center justify-between gap-3">
                  <span className="text-blue-700">
                    Mật khẩu
                  </span>

                  <strong className="text-right text-blue-950">
                    Admin123
                  </strong>
                </div>
              </div>
            </div>

            {/* Link to reader registration */}
            <div className="mt-5 text-center text-sm text-slate-600">
              Chưa có tài khoản bạn đọc?{' '}
              <Link
                to="/register"
                className="font-semibold text-blue-600 transition hover:text-blue-700"
              >
                Đăng ký ngay
              </Link>
            </div>

            <div className="mt-3 text-center text-sm">
              <Link
                to="/catalog"
                className="font-semibold text-emerald-700 transition hover:text-emerald-800"
              >
                Tra cứu đầu sách công khai
              </Link>
            </div>

            <div className="mt-8 flex items-center justify-center gap-2 text-xs text-slate-400">
              <CheckCircle2
                size={14}
              />
              Phiên truy cập 30 phút · Phiên làm mới 7 ngày
            </div>
          </div>
        </section>
      </div>
    </div>
  )
}

type FeatureBoxProps = {
  title: string
  text: string
}

function FeatureBox({
  title,
  text,
}: FeatureBoxProps) {
  return (
    <div className="rounded-xl border border-slate-800 bg-slate-900/80 p-4">
      <p className="font-semibold text-white">
        {title}
      </p>

      <p className="mt-1 text-xs leading-5 text-slate-400">
        {text}
      </p>
    </div>
  )
}