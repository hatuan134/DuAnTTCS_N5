import { useState, useEffect } from 'react'
import type { FormEvent } from 'react'
import {
  AlertTriangle,
  CheckCircle2,
  Eye,
  EyeOff,
  KeyRound,
  ShieldCheck,
  User,
} from 'lucide-react'
import {
  Link,
  useSearchParams,
} from 'react-router-dom'
import { passwordResetService } from './passwordResetService'
import FeedbackAlert from '../../components/ui/FeedbackAlert'
import { clearAuthSession } from '../../core/auth/authStorage'

type TokenState = 'valid' | 'invalid' | 'expired' | 'used'

export default function ResetPasswordPage() {
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token') ?? ''

  const [isValidating, setIsValidating] = useState(true)
  const [tokenState, setTokenState] = useState<TokenState>('invalid')
  const [tokenErrorMessage, setTokenErrorMessage] = useState('')
  const [userEmail, setUserEmail] = useState('')
  const [userFullName, setUserFullName] = useState('')

  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [showNewPassword, setShowNewPassword] = useState(false)
  const [showConfirmPassword, setShowConfirmPassword] = useState(false)

  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState(false)

  useEffect(() => {
    if (!token.trim()) {
      setTokenState('invalid')
      setTokenErrorMessage('Mã liên kết không được để trống hoặc thiếu tham số token.')
      setIsValidating(false)
      return
    }

    let active = true
    setIsValidating(true)

    passwordResetService
      .validateToken(token)
      .then((data) => {
        if (!active) return
        if (data.valid) {
          setTokenState('valid')
          setUserEmail(data.email || '')
          setUserFullName(data.fullName || '')
        } else {
          setTokenState('invalid')
          setTokenErrorMessage(data.message || 'Liên kết đặt lại mật khẩu không hợp lệ.')
        }
      })
      .catch((err: unknown) => {
        if (!active) return
        const apiError = err as {
          response?: { data?: { code?: string; message?: string } }
          message?: string
        }
        const code = apiError.response?.data?.code
        const msg = apiError.response?.data?.message

        if (code === 'EXPIRED_TOKEN' || code === 'TOKEN_EXPIRED') {
          setTokenState('expired')
          setTokenErrorMessage(
            msg || 'Liên kết đặt lại mật khẩu đã hết hạn (chỉ có hiệu lực trong 30 phút).',
          )
        } else if (code === 'TOKEN_ALREADY_USED' || code === 'USED_TOKEN') {
          setTokenState('used')
          setTokenErrorMessage(msg || 'Liên kết đặt lại mật khẩu đã được sử dụng.')
        } else {
          setTokenState('invalid')
          setTokenErrorMessage(
            msg || 'Liên kết đặt lại mật khẩu không hợp lệ hoặc không tồn tại.',
          )
        }
      })
      .finally(() => {
        if (active) {
          setIsValidating(false)
        }
      })

    return () => {
      active = false
    }
  }, [token])

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setError('')

    if (tokenState !== 'valid') {
      return
    }

    if (!newPassword || !confirmPassword) {
      setError('Vui lòng nhập đầy đủ mật khẩu mới.')
      return
    }

    if (newPassword.length < 8) {
      setError('Mật khẩu mới phải có ít nhất 8 ký tự.')
      return
    }

    const hasLetter = /[A-Za-z]/.test(newPassword)
    const hasNumber = /\d/.test(newPassword)

    if (!hasLetter || !hasNumber) {
      setError('Mật khẩu mới phải có cả chữ và số.')
      return
    }

    if (newPassword !== confirmPassword) {
      setError('Xác nhận mật khẩu không khớp.')
      return
    }

    setSubmitting(true)

    try {
      await passwordResetService.resetPassword({
        token,
        newPassword,
        confirmPassword,
      })

      // Thu hồi session ở local storage phía client
      clearAuthSession()

      setNewPassword('')
      setConfirmPassword('')
      setSuccess(true)
    } catch (err: unknown) {
      const apiError = err as {
        response?: { data?: { code?: string; message?: string } }
        message?: string
      }
      const code = apiError.response?.data?.code
      const msg =
        apiError.response?.data?.message ||
        apiError.message ||
        'Có lỗi xảy ra khi đặt lại mật khẩu. Vui lòng thử lại.'
      setError(msg)

      if (code === 'TOKEN_ALREADY_USED' || code === 'USED_TOKEN') {
        setTokenState('used')
        setTokenErrorMessage(msg)
      } else if (code === 'TOKEN_EXPIRED' || code === 'EXPIRED_TOKEN') {
        setTokenState('expired')
        setTokenErrorMessage(msg)
      }
    } finally {
      setSubmitting(false)
    }
  }

  if (isValidating) {
    return (
      <div className="auth-page flex min-h-screen items-center justify-center bg-slate-50 px-4 py-10">
        <div className="w-full max-w-md rounded-2xl border border-slate-200 bg-white p-8 text-center shadow-sm">
          <div className="mx-auto h-10 w-10 animate-spin rounded-full border-4 border-blue-600 border-r-transparent"></div>
          <p className="mt-4 text-sm font-medium text-slate-600">
            Đang kiểm tra tính hợp lệ của liên kết...
          </p>
        </div>
      </div>
    )
  }

  if (success) {
    return (
      <div className="auth-page flex min-h-screen items-center justify-center bg-slate-50 px-4 py-10">
        <div className="w-full max-w-md rounded-2xl border border-slate-200 bg-white p-7 text-center shadow-sm">
          <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-emerald-50 text-emerald-600">
            <CheckCircle2 size={32} />
          </div>

          <h1 className="mt-5 text-2xl font-bold text-slate-900">
            Đặt lại mật khẩu thành công
          </h1>

          <p className="mt-3 text-sm leading-6 text-slate-600">
            Mật khẩu của bạn đã được thay đổi thành công.
          </p>

          <p className="mt-1 text-sm leading-6 text-slate-500">
            Tất cả phiên đăng nhập trước đó đã bị thu hồi để đảm bảo an toàn.
          </p>

          <Link
            to="/login"
            className="mt-6 inline-flex w-full items-center justify-center rounded-lg bg-blue-600 px-4 py-2.5 text-sm font-medium text-white transition hover:bg-blue-700 shadow-sm"
          >
            Đăng nhập bằng mật khẩu mới
          </Link>
        </div>
      </div>
    )
  }

  if (tokenState !== 'valid') {
    let displayMessage =
      tokenErrorMessage || 'Liên kết đặt lại mật khẩu không hợp lệ.'

    if (tokenState === 'expired') {
      displayMessage =
        tokenErrorMessage ||
        'Liên kết đặt lại mật khẩu đã hết hạn (chỉ có hiệu lực trong 30 phút).'
    }

    if (tokenState === 'used') {
      displayMessage =
        tokenErrorMessage || 'Liên kết đặt lại mật khẩu đã được sử dụng.'
    }

    return (
      <div className="auth-page flex min-h-screen items-center justify-center bg-slate-50 px-4 py-10">
        <div className="w-full max-w-md rounded-2xl border border-slate-200 bg-white p-7 text-center shadow-sm">
          <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-amber-50 text-amber-600">
            <AlertTriangle size={32} />
          </div>

          <h1 className="mt-5 text-xl font-bold text-slate-900">
            Không thể đặt lại mật khẩu
          </h1>

          <p className="mt-3 text-sm leading-6 text-slate-600">
            {displayMessage}
          </p>

          <Link
            to="/forgot-password"
            className="mt-6 inline-flex w-full items-center justify-center rounded-lg bg-blue-600 px-4 py-2.5 text-sm font-medium text-white transition hover:bg-blue-700 shadow-sm"
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
    <div className="auth-page flex min-h-screen items-center justify-center bg-slate-50 px-4 py-10">
      <div className="w-full max-w-md">
        <div className="mb-7 text-center">
          <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-blue-600 text-white shadow-sm">
            <ShieldCheck size={27} />
          </div>

          <h1 className="mt-4 text-2xl font-bold text-slate-900">
            Tạo mật khẩu mới
          </h1>

          <p className="mt-2 text-sm leading-6 text-slate-500">
            Mật khẩu mới phải có ít nhất 8 ký tự, gồm cả chữ và số.
          </p>
        </div>

        <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
          {(userFullName || userEmail) && (
            <div className="mb-5 flex items-center gap-3 rounded-xl bg-slate-50 p-3.5 border border-slate-100">
              <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-blue-100 text-blue-600">
                <User size={20} />
              </div>
              <div className="overflow-hidden text-left">
                {userFullName && (
                  <p className="truncate text-sm font-semibold text-slate-800">
                    {userFullName}
                  </p>
                )}
                {userEmail && (
                  <p className="truncate text-xs text-slate-500">
                    {userEmail}
                  </p>
                )}
              </div>
            </div>
          )}

          <form onSubmit={handleSubmit} className="space-y-5">
            <PasswordField
              label="Mật khẩu mới"
              value={newPassword}
              show={showNewPassword}
              onToggle={() => setShowNewPassword(!showNewPassword)}
              onChange={(value) => {
                setNewPassword(value)
                setError('')
              }}
              disabled={submitting}
            />

            <PasswordField
              label="Nhập lại mật khẩu mới"
              value={confirmPassword}
              show={showConfirmPassword}
              onToggle={() => setShowConfirmPassword(!showConfirmPassword)}
              onChange={(value) => {
                setConfirmPassword(value)
                setError('')
              }}
              disabled={submitting}
            />

            <div className="rounded-xl bg-slate-50 p-4 border border-slate-100">
              <p className="text-xs font-semibold text-slate-700 uppercase tracking-wider">
                Yêu cầu mật khẩu
              </p>

              <div className="mt-2 space-y-1 text-xs text-slate-600">
                <p>• Ít nhất 8 ký tự.</p>
                <p>• Có ít nhất một chữ cái.</p>
                <p>• Có ít nhất một chữ số.</p>
                <p>• Không được trùng với mật khẩu hiện tại.</p>
              </div>
            </div>

            {error && (
              <FeedbackAlert message={error} tone="error" onDismiss={() => setError('')} />
            )}

            <button
              type="submit"
              disabled={submitting}
              className="inline-flex w-full items-center justify-center gap-2 rounded-lg bg-blue-600 px-4 py-2.5 text-sm font-medium text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-60 shadow-sm"
            >
              <KeyRound size={17} />
              {submitting ? 'Đang cập nhật...' : 'Đặt lại mật khẩu'}
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
  disabled?: boolean
}

function PasswordField({
  label,
  value,
  show,
  onToggle,
  onChange,
  disabled = false,
}: PasswordFieldProps) {
  return (
    <div>
      <label className="mb-1.5 block text-sm font-medium text-slate-700">
        {label}
        <span className="ml-1 text-red-500">*</span>
      </label>

      <div className="relative">
        <KeyRound
          size={18}
          className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400"
        />

        <input
          type={show ? 'text' : 'password'}
          value={value}
          onChange={(event) => onChange(event.target.value)}
          placeholder="••••••••"
          disabled={disabled}
          className="w-full rounded-lg border border-slate-300 py-2.5 pl-10 pr-11 text-sm outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-100 disabled:bg-slate-50"
        />

        <button
          type="button"
          onClick={onToggle}
          tabIndex={-1}
          className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 transition hover:text-slate-700"
        >
          {show ? <EyeOff size={18} /> : <Eye size={18} />}
        </button>
      </div>
    </div>
  )
}