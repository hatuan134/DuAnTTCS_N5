import { useState } from 'react'
import type { FormEvent } from 'react'

import {
  AlertTriangle,
  CheckCircle2,
  Eye,
  EyeOff,
  KeyRound,
  ShieldCheck,
} from 'lucide-react'

import {
  Link,
  useSearchParams,
} from 'react-router-dom'

type ResetRequest = {
  email: string
  token: string
  createdAt: number
  expiresAt: number
  used: boolean
}

type TokenState =
  | 'valid'
  | 'invalid'
  | 'expired'
  | 'used'

const RESET_REQUEST_KEY =
  'libra_password_reset_requests'

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

function getTokenState(
  token: string,
): TokenState {
  if (!token) {
    return 'invalid'
  }

  const record =
    readResetRequests().find(
      (item) =>
        item.token === token,
    )

  if (!record) {
    return 'invalid'
  }

  if (record.used) {
    return 'used'
  }

  if (
    Date.now() >
    record.expiresAt
  ) {
    return 'expired'
  }

  return 'valid'
}

export default function ResetPasswordPage() {
  const [searchParams] =
    useSearchParams()

  const token =
    searchParams.get('token') ?? ''

  const [
    tokenState,
    setTokenState,
  ] = useState<TokenState>(
    () =>
      getTokenState(token),
  )

  const [
    newPassword,
    setNewPassword,
  ] = useState('')

  const [
    confirmPassword,
    setConfirmPassword,
  ] = useState('')

  const [
    showNewPassword,
    setShowNewPassword,
  ] = useState(false)

  const [
    showConfirmPassword,
    setShowConfirmPassword,
  ] = useState(false)

  const [error, setError] =
    useState('')

  const [success, setSuccess] =
    useState(false)

  const handleSubmit = (
    event: FormEvent<HTMLFormElement>,
  ) => {
    event.preventDefault()

    setError('')

    const currentState =
      getTokenState(token)

    if (
      currentState !== 'valid'
    ) {
      setTokenState(
        currentState,
      )
      return
    }

    if (
      !newPassword ||
      !confirmPassword
    ) {
      setError(
        'Vui lòng nhập đầy đủ mật khẩu mới.',
      )
      return
    }

    if (
      newPassword.length < 8
    ) {
      setError(
        'Mật khẩu mới phải có ít nhất 8 ký tự.',
      )
      return
    }

    const hasLetter =
      /[A-Za-z]/.test(
        newPassword,
      )

    const hasNumber =
      /\d/.test(
        newPassword,
      )

    if (
      !hasLetter ||
      !hasNumber
    ) {
      setError(
        'Mật khẩu mới phải có cả chữ và số.',
      )
      return
    }

    if (
      newPassword !==
      confirmPassword
    ) {
      setError(
        'Xác nhận mật khẩu không khớp.',
      )
      return
    }

    /*
     * Đánh dấu link đã được dùng.
     */
    const updatedRequests =
      readResetRequests().map(
        (item) =>
          item.token === token
            ? {
                ...item,
                used: true,
              }
            : item,
      )

    saveResetRequests(
      updatedRequests,
    )

    /*
     * MOCK FRONTEND:
     * xóa các token đăng nhập cũ
     * để mô phỏng việc logout
     * toàn bộ phiên.
     */
    const authKeys = [
      'accessToken',
      'refreshToken',
      'token',
      'authToken',
      'currentUser',
    ]

    authKeys.forEach(
      (key) =>
        localStorage.removeItem(
          key,
        ),
    )

    localStorage.setItem(
      'libra_sessions_invalidated_at',
      new Date().toISOString(),
    )

    setNewPassword('')
    setConfirmPassword('')

    setTokenState('used')
    setSuccess(true)
  }

  if (success) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-slate-50 px-4 py-10">
        <div className="w-full max-w-md rounded-2xl border border-slate-200 bg-white p-7 text-center shadow-sm">
          <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-emerald-50 text-emerald-600">
            <CheckCircle2
              size={32}
            />
          </div>

          <h1 className="mt-5 text-2xl font-bold text-slate-900">
            Đặt lại mật khẩu thành công
          </h1>

          <p className="mt-3 text-sm leading-6 text-slate-500">
            Mật khẩu của bạn đã được thay đổi.
          </p>

          <p className="mt-1 text-sm leading-6 text-slate-500">
            Tất cả phiên đăng nhập trước đó
            đã bị vô hiệu hóa.
          </p>

          <Link
            to="/login"
            className="mt-6 inline-flex w-full items-center justify-center rounded-lg bg-blue-600 px-4 py-2.5 text-sm font-medium text-white transition hover:bg-blue-700"
          >
            Đăng nhập lại
          </Link>
        </div>
      </div>
    )
  }

  if (
    tokenState !== 'valid'
  ) {
    let message =
      'Liên kết đặt lại mật khẩu không hợp lệ.'

    if (
      tokenState === 'expired'
    ) {
      message =
        'Liên kết đặt lại mật khẩu đã hết hạn.'
    }

    if (
      tokenState === 'used'
    ) {
      message =
        'Liên kết đặt lại mật khẩu đã được sử dụng.'
    }

    return (
      <div className="flex min-h-screen items-center justify-center bg-slate-50 px-4 py-10">
        <div className="w-full max-w-md rounded-2xl border border-slate-200 bg-white p-7 text-center shadow-sm">
          <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-amber-50 text-amber-600">
            <AlertTriangle
              size={32}
            />
          </div>

          <h1 className="mt-5 text-xl font-bold text-slate-900">
            Không thể đặt lại mật khẩu
          </h1>

          <p className="mt-3 text-sm leading-6 text-slate-500">
            {message}
          </p>

          <Link
            to="/forgot-password"
            className="mt-6 inline-flex w-full items-center justify-center rounded-lg bg-blue-600 px-4 py-2.5 text-sm font-medium text-white transition hover:bg-blue-700"
          >
            Yêu cầu liên kết mới
          </Link>

          <Link
            to="/login"
            className="mt-4 inline-flex text-sm font-medium text-slate-500 hover:text-blue-600"
          >
            Quay lại đăng nhập
          </Link>
        </div>
      </div>
    )
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50 px-4 py-10">
      <div className="w-full max-w-md">
        <div className="mb-7 text-center">
          <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-blue-600 text-white">
            <ShieldCheck
              size={27}
            />
          </div>

          <h1 className="mt-4 text-2xl font-bold text-slate-900">
            Tạo mật khẩu mới
          </h1>

          <p className="mt-2 text-sm leading-6 text-slate-500">
            Mật khẩu mới phải có ít nhất
            8 ký tự, gồm chữ và số.
          </p>
        </div>

        <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
          <form
            onSubmit={handleSubmit}
            className="space-y-5"
          >
            <PasswordField
              label="Mật khẩu mới"
              value={newPassword}
              show={showNewPassword}
              onToggle={() =>
                setShowNewPassword(
                  !showNewPassword,
                )
              }
              onChange={(value) => {
                setNewPassword(value)
                setError('')
              }}
            />

            <PasswordField
              label="Nhập lại mật khẩu mới"
              value={
                confirmPassword
              }
              show={
                showConfirmPassword
              }
              onToggle={() =>
                setShowConfirmPassword(
                  !showConfirmPassword,
                )
              }
              onChange={(value) => {
                setConfirmPassword(
                  value,
                )
                setError('')
              }}
            />

            <div className="rounded-xl bg-slate-50 p-4">
              <p className="text-sm font-medium text-slate-700">
                Yêu cầu mật khẩu
              </p>

              <div className="mt-2 space-y-1 text-sm text-slate-500">
                <p>• Ít nhất 8 ký tự.</p>
                <p>• Có ít nhất một chữ.</p>
                <p>• Có ít nhất một số.</p>
              </div>
            </div>

            {error && (
              <div className="rounded-lg border border-red-100 bg-red-50 px-4 py-3 text-sm leading-6 text-red-700">
                {error}
              </div>
            )}

            <button
              type="submit"
              className="inline-flex w-full items-center justify-center gap-2 rounded-lg bg-blue-600 px-4 py-2.5 text-sm font-medium text-white transition hover:bg-blue-700"
            >
              <KeyRound size={17} />
              Đặt lại mật khẩu
            </button>
          </form>
        </div>
      </div>
    </div>
  )
}

type PasswordFieldProps = {
  label: string
  value: string
  show: boolean
  onToggle: () => void
  onChange: (value: string) => void
}

function PasswordField({
  label,
  value,
  show,
  onToggle,
  onChange,
}: PasswordFieldProps) {
  return (
    <div>
      <label className="mb-1.5 block text-sm font-medium text-slate-700">
        {label}
        <span className="ml-1 text-red-500">
          *
        </span>
      </label>

      <div className="relative">
        <KeyRound
          size={18}
          className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400"
        />

        <input
          type={
            show
              ? 'text'
              : 'password'
          }
          value={value}
          onChange={(event) =>
            onChange(
              event.target.value,
            )
          }
          className="w-full rounded-lg border border-slate-300 py-2.5 pl-10 pr-11 text-sm outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
        />

        <button
          type="button"
          onClick={onToggle}
          className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 transition hover:text-slate-700"
        >
          {show ? (
            <EyeOff size={18} />
          ) : (
            <Eye size={18} />
          )}
        </button>
      </div>
    </div>
  )
}