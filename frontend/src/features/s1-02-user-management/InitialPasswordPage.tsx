import {
  useEffect,
  useState,
} from 'react'
import type { FormEvent } from 'react'

import {
  AlertTriangle,
  CheckCircle2,
  Eye,
  EyeOff,
  KeyRound,
  LoaderCircle,
} from 'lucide-react'
import {
  Link,
  useSearchParams,
} from 'react-router-dom'

import AuthLayout from '../../layouts/AuthLayout'

import {
  getApiErrorMessage,
  setInitialPassword,
  validateInitialPasswordToken,
} from './accountService'

import type {
  InitialPasswordTokenInfo,
} from './accountService'

export default function InitialPasswordPage() {
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token')?.trim() ?? ''

  const [tokenInfo, setTokenInfo] = useState<InitialPasswordTokenInfo | null>(null)
  const [checking, setChecking] = useState(true)
  const [tokenError, setTokenError] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [success, setSuccess] = useState(false)

  useEffect(() => {
    let active = true

    const validate = async () => {
      if (!token) {
        setTokenError('Liên kết thiết lập mật khẩu không hợp lệ.')
        setChecking(false)
        return
      }

      try {
        const info = await validateInitialPasswordToken(token)
        if (active) {
          setTokenInfo(info)
        }
      } catch (requestError) {
        if (active) {
          setTokenError(
            getApiErrorMessage(
              requestError,
              'Liên kết không hợp lệ, đã hết hạn hoặc đã được sử dụng.',
            ),
          )
        }
      } finally {
        if (active) {
          setChecking(false)
        }
      }
    }

    void validate()

    return () => {
      active = false
    }
  }, [token])

  const handleSubmit = async (
    event: FormEvent<HTMLFormElement>,
  ) => {
    event.preventDefault()
    setError('')

    if (password.length < 8) {
      setError('Mật khẩu phải có ít nhất 8 ký tự.')
      return
    }

    if (!/[A-Za-z]/.test(password) || !/\d/.test(password)) {
      setError('Mật khẩu phải chứa ít nhất 1 chữ cái và 1 chữ số.')
      return
    }

    if (password !== confirmPassword) {
      setError('Xác nhận mật khẩu không khớp.')
      return
    }

    setSubmitting(true)

    try {
      await setInitialPassword(token, password)
      setPassword('')
      setConfirmPassword('')
      setSuccess(true)
    } catch (requestError) {
      setError(
        getApiErrorMessage(
          requestError,
          'Không thể thiết lập mật khẩu. Vui lòng thử lại.',
        ),
      )
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthLayout>
      {checking ? (
        <div className="rounded-xl border border-slate-200 bg-white p-7 shadow-sm">
          <div className="flex items-center justify-center gap-3 py-10 text-sm text-slate-500">
            <LoaderCircle className="animate-spin" size={22} />
            Đang kiểm tra liên kết...
          </div>
        </div>
      ) : success ? (
        <div className="rounded-xl border border-slate-200 bg-white p-7 text-center shadow-sm">
          <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-emerald-50 text-emerald-600">
            <CheckCircle2 size={28} />
          </div>
          <h1 className="mt-5 text-2xl font-semibold text-slate-900">
            Thiết lập mật khẩu thành công
          </h1>
          <p className="mt-3 text-sm leading-6 text-slate-500">
            Tài khoản của bạn đã sẵn sàng. Liên kết này không thể sử dụng lại.
          </p>
          <Link
            to="/login"
            className="mt-6 inline-flex h-11 w-full items-center justify-center rounded-lg bg-blue-600 px-4 text-sm font-medium text-white transition hover:bg-blue-700"
          >
            Đến trang đăng nhập
          </Link>
        </div>
      ) : tokenError ? (
        <div className="rounded-xl border border-slate-200 bg-white p-7 text-center shadow-sm">
          <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-amber-50 text-amber-600">
            <AlertTriangle size={28} />
          </div>
          <h1 className="mt-5 text-xl font-semibold text-slate-900">
            Không thể sử dụng liên kết
          </h1>
          <p className="mt-3 text-sm leading-6 text-slate-500">
            {tokenError}
          </p>
          <Link
            to="/login"
            className="mt-6 inline-flex text-sm font-medium text-blue-600 hover:text-blue-700"
          >
            Quay lại đăng nhập
          </Link>
        </div>
      ) : (
        <div className="rounded-xl border border-slate-200 bg-white p-7 shadow-sm">
          <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-blue-50 text-blue-700">
            <KeyRound size={23} />
          </div>

          <h1 className="mt-5 text-2xl font-semibold text-slate-900">
            Đặt mật khẩu lần đầu
          </h1>
          <p className="mt-2 text-sm leading-6 text-slate-500">
            Xin chào <strong className="font-medium text-slate-700">{tokenInfo?.fullName}</strong>. Hãy tạo mật khẩu để hoàn tất tài khoản {tokenInfo?.email}.
          </p>

          <form className="mt-6 space-y-4" onSubmit={handleSubmit}>
            <div>
              <label htmlFor="initial-password" className="mb-2 block text-sm font-medium text-slate-700">
                Mật khẩu mới
              </label>
              <div className="relative">
                <input
                  id="initial-password"
                  type={showPassword ? 'text' : 'password'}
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  autoComplete="new-password"
                  className="h-11 w-full rounded-lg border border-slate-300 bg-white px-3 pr-11 text-sm outline-none focus:border-blue-500 focus:ring-4 focus:ring-blue-100"
                  placeholder="Ít nhất 8 ký tự, có chữ và số"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword((value) => !value)}
                  className="absolute inset-y-0 right-0 flex w-11 items-center justify-center text-slate-400 hover:text-slate-700"
                  aria-label={showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
                >
                  {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
                </button>
              </div>
            </div>

            <div>
              <label htmlFor="confirm-initial-password" className="mb-2 block text-sm font-medium text-slate-700">
                Xác nhận mật khẩu
              </label>
              <input
                id="confirm-initial-password"
                type={showPassword ? 'text' : 'password'}
                value={confirmPassword}
                onChange={(event) => setConfirmPassword(event.target.value)}
                autoComplete="new-password"
                className="h-11 w-full rounded-lg border border-slate-300 bg-white px-3 text-sm outline-none focus:border-blue-500 focus:ring-4 focus:ring-blue-100"
                placeholder="Nhập lại mật khẩu"
              />
            </div>

            <div className="rounded-lg bg-slate-50 px-4 py-3 text-xs leading-5 text-slate-600">
              Liên kết đặt mật khẩu lần đầu có hiệu lực 24 giờ và chỉ sử dụng được một lần.
            </div>

            {error && (
              <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
                {error}
              </div>
            )}

            <button
              type="submit"
              disabled={submitting}
              className="inline-flex h-11 w-full items-center justify-center gap-2 rounded-lg bg-blue-600 px-4 text-sm font-medium text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-60"
            >
              {submitting && (
                <LoaderCircle className="animate-spin" size={17} />
              )}
              Thiết lập mật khẩu
            </button>
          </form>
        </div>
      )}
    </AuthLayout>
  )
}
