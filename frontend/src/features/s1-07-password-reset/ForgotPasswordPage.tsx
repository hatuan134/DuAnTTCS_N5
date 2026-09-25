import { useState } from 'react'
import type { FormEvent } from 'react'

import {
  ArrowLeft,
  CheckCircle2,
  Mail,
  Send,
} from 'lucide-react'

import {
  Link,
} from 'react-router-dom'

type RateLimitRecord = {
  email: string
  requestedAt: number
}

type ResetRequest = {
  email: string
  token: string
  createdAt: number
  expiresAt: number
  used: boolean
}

const RATE_LIMIT_KEY =
  'libra_password_reset_rate_limit'

const RESET_REQUEST_KEY =
  'libra_password_reset_requests'

const DEMO_EMAIL =
  'bandoc@ictu.edu.vn'

const MAX_REQUESTS_PER_HOUR = 3

const ONE_HOUR_MS =
  60 * 60 * 1000

const RESET_EXPIRE_MS =
  30 * 60 * 1000

function readRateLimit():
  RateLimitRecord[] {
  try {
    const raw =
      localStorage.getItem(
        RATE_LIMIT_KEY,
      )

    if (!raw) {
      return []
    }

    return JSON.parse(raw)
  } catch {
    return []
  }
}

function saveRateLimit(
  records: RateLimitRecord[],
) {
  localStorage.setItem(
    RATE_LIMIT_KEY,
    JSON.stringify(records),
  )
}

function readResetRequests():
  ResetRequest[] {
  try {
    const raw =
      localStorage.getItem(
        RESET_REQUEST_KEY,
      )

    if (!raw) {
      return []
    }

    return JSON.parse(raw)
  } catch {
    return []
  }
}

function saveResetRequests(
  records: ResetRequest[],
) {
  localStorage.setItem(
    RESET_REQUEST_KEY,
    JSON.stringify(records),
  )
}

function generateToken() {
  return `${Date.now().toString(36)}-${Math.random()
    .toString(36)
    .slice(2)}-${Math.random()
    .toString(36)
    .slice(2)}`
}

export default function ForgotPasswordPage() {
  const [email, setEmail] =
    useState('')

  const [error, setError] =
    useState('')

  const [success, setSuccess] =
    useState(false)

  const [loading, setLoading] =
    useState(false)

  const [demoLink, setDemoLink] =
    useState('')

  const handleSubmit = (
    event: FormEvent<HTMLFormElement>,
  ) => {
    event.preventDefault()

    setError('')
    setSuccess(false)
    setDemoLink('')

    const normalizedEmail =
      email.trim().toLowerCase()

    if (!normalizedEmail) {
      setError(
        'Vui lòng nhập địa chỉ email.',
      )
      return
    }

    const emailPattern =
      /^[^\s@]+@[^\s@]+\.[^\s@]+$/

    if (
      !emailPattern.test(
        normalizedEmail,
      )
    ) {
      setError(
        'Email không đúng định dạng.',
      )
      return
    }

    const now = Date.now()

    const freshRecords =
      readRateLimit().filter(
        (item) =>
          now -
            item.requestedAt <
          ONE_HOUR_MS,
      )

    const requestCount =
      freshRecords.filter(
        (item) =>
          item.email ===
          normalizedEmail,
      ).length

    if (
      requestCount >=
      MAX_REQUESTS_PER_HOUR
    ) {
      saveRateLimit(
        freshRecords,
      )

      setError(
        'Bạn đã gửi quá nhiều yêu cầu. Vui lòng thử lại sau.',
      )

      return
    }

    saveRateLimit([
      ...freshRecords,
      {
        email: normalizedEmail,
        requestedAt: now,
      },
    ])

    setLoading(true)

    /*
     * MOCK:
     * bandoc@ictu.edu.vn được xem
     * như email đã đăng ký.
     *
     * Email khác vẫn nhận cùng
     * thông báo trung tính nhưng
     * không sinh reset token.
     */
    if (
      normalizedEmail ===
      DEMO_EMAIL
    ) {
      const token =
        generateToken()

      const currentRequests =
        readResetRequests()

      /*
       * Các link cũ chưa dùng của
       * cùng email được vô hiệu.
       */
      const invalidatedRequests =
        currentRequests.map(
          (item) =>
            item.email ===
              normalizedEmail &&
            !item.used
              ? {
                  ...item,
                  used: true,
                }
              : item,
        )

      const request:
        ResetRequest = {
        email: normalizedEmail,
        token,
        createdAt: now,
        expiresAt:
          now +
          RESET_EXPIRE_MS,
        used: false,
      }

      saveResetRequests([
        ...invalidatedRequests,
        request,
      ])

      setDemoLink(
        `/reset-password?token=${token}`,
      )
    }

    window.setTimeout(() => {
      setLoading(false)
      setSuccess(true)
    }, 400)
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50 px-4 py-10">
      <div className="w-full max-w-md">
        <div className="mb-7 text-center">
          <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-blue-600 text-white shadow-sm">
            <Mail size={27} />
          </div>

          <h1 className="mt-4 text-2xl font-bold text-slate-900">
            Quên mật khẩu?
          </h1>

          <p className="mt-2 text-sm leading-6 text-slate-500">
            Nhập địa chỉ email đã đăng ký
            để nhận liên kết đặt lại mật khẩu.
          </p>
        </div>

        <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
          {!success ? (
            <form
              onSubmit={handleSubmit}
              className="space-y-5"
            >
              <div>
                <label className="mb-1.5 block text-sm font-medium text-slate-700">
                  Email
                  <span className="ml-1 text-red-500">
                    *
                  </span>
                </label>

                <div className="relative">
                  <Mail
                    size={18}
                    className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400"
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
                    className="w-full rounded-lg border border-slate-300 py-2.5 pl-10 pr-3 text-sm outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
                  />
                </div>
              </div>

              {error && (
                <div className="rounded-lg border border-red-100 bg-red-50 px-4 py-3 text-sm leading-6 text-red-700">
                  {error}
                </div>
              )}

              <button
                type="submit"
                disabled={loading}
                className="inline-flex w-full items-center justify-center gap-2 rounded-lg bg-blue-600 px-4 py-2.5 text-sm font-medium text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-60"
              >
                <Send size={17} />

                {loading
                  ? 'Đang gửi...'
                  : 'Gửi liên kết đặt lại'}
              </button>
            </form>
          ) : (
            <div className="text-center">
              <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-emerald-50 text-emerald-600">
                <CheckCircle2
                  size={28}
                />
              </div>

              <h2 className="mt-4 text-lg font-semibold text-slate-900">
                Kiểm tra email của bạn
              </h2>

              <p className="mt-2 text-sm leading-6 text-slate-500">
                Nếu email khớp với một tài khoản
                trong hệ thống, chúng tôi đã gửi
                liên kết đặt lại mật khẩu.
              </p>

              <p className="mt-2 text-sm leading-6 text-slate-500">
                Liên kết có hiệu lực trong
                30 phút và chỉ sử dụng được
                một lần.
              </p>

              <button
                type="button"
                onClick={() => {
                  setSuccess(false)
                  setEmail('')
                  setError('')
                  setDemoLink('')
                }}
                className="mt-5 text-sm font-medium text-blue-600 hover:text-blue-700"
              >
                Gửi yêu cầu khác
              </button>
            </div>
          )}

          <div className="mt-6 border-t border-slate-100 pt-5">
            <Link
              to="/login"
              className="inline-flex items-center gap-2 text-sm font-medium text-slate-600 transition hover:text-blue-600"
            >
              <ArrowLeft size={16} />
              Quay lại đăng nhập
            </Link>
          </div>
        </div>

        <div className="mt-5 rounded-xl border border-blue-100 bg-blue-50 p-4">
          <p className="text-sm font-semibold text-blue-900">
            Kiểm thử frontend
          </p>

          <p className="mt-1 text-sm text-blue-700">
            Email demo:
            <strong>
              {' '}
              {DEMO_EMAIL}
            </strong>
          </p>

          <p className="mt-2 text-xs leading-5 text-blue-600">
            Hiện đây là Mock Frontend nên chưa
            gửi email thật. Khi nối backend,
            liên kết này sẽ được gửi qua email.
          </p>

          {demoLink && (
            <Link
              to={demoLink}
              className="mt-3 inline-flex rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white transition hover:bg-blue-700"
            >
              Mở liên kết đặt lại thử nghiệm
            </Link>
          )}
        </div>
      </div>
    </div>
  )
}