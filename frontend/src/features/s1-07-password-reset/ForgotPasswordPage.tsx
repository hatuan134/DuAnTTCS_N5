import { useState } from 'react'
import type { FormEvent } from 'react'
import {
  ArrowLeft,
  CheckCircle2,
  Mail,
  Send,
  Info,
} from 'lucide-react'
import {
  Link,
  useSearchParams,
} from 'react-router-dom'
import { passwordResetService } from './passwordResetService'

export default function ForgotPasswordPage() {
  const [searchParams] = useSearchParams()
  const initialEmail = searchParams.get('email') || ''
  const [email, setEmail] = useState(initialEmail)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState(false)
  const [successMessage, setSuccessMessage] = useState('')
  const [loading, setLoading] = useState(false)

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()

    setError('')
    setSuccess(false)

    const normalizedEmail = email.trim().toLowerCase()

    if (!normalizedEmail) {
      setError('Vui lòng nhập địa chỉ email.')
      return
    }

    const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
    if (!emailPattern.test(normalizedEmail)) {
      setError('Email không đúng định dạng.')
      return
    }

    setLoading(true)

    try {
      const response = await passwordResetService.requestForgotPassword(normalizedEmail)
      setSuccessMessage(
        response.message ||
          'Nếu email của bạn tồn tại trong hệ thống, hướng dẫn đặt lại mật khẩu đã được gửi đến hòm thư.',
      )
      setSuccess(true)
    } catch (err: unknown) {
      const apiError = err as { response?: { data?: { message?: string } }; message?: string }
      const message =
        apiError.response?.data?.message ||
        apiError.message ||
        'Không thể kết nối đến máy chủ. Vui lòng kiểm tra lại kết nối mạng.'
      setError(message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="auth-page flex min-h-screen items-center justify-center bg-slate-50 px-4 py-10">
      <div className="w-full max-w-md">
        <div className="mb-7 text-center">
          <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-blue-600 text-white shadow-sm">
            <Mail size={27} />
          </div>

          <h1 className="mt-4 text-2xl font-bold text-slate-900">
            Quên mật khẩu?
          </h1>

          <p className="mt-2 text-sm leading-6 text-slate-500">
            Nhập địa chỉ email đã đăng ký để nhận liên kết đặt lại mật khẩu.
          </p>
        </div>

        <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
          {!success ? (
            <form onSubmit={handleSubmit} className="space-y-5">
              <div>
                <label className="mb-1.5 block text-sm font-medium text-slate-700">
                  Email
                  <span className="ml-1 text-red-500">*</span>
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
                      setEmail(event.target.value)
                      setError('')
                    }}
                    placeholder="name@example.com"
                    className="w-full rounded-lg border border-slate-300 py-2.5 pl-10 pr-3 text-sm outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
                    disabled={loading}
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
                {loading ? 'Đang gửi...' : 'Gửi liên kết đặt lại'}
              </button>
            </form>
          ) : (
            <div className="text-center">
              <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-emerald-50 text-emerald-600">
                <CheckCircle2 size={28} />
              </div>

              <h2 className="mt-4 text-lg font-semibold text-slate-900">
                Kiểm tra email của bạn
              </h2>

              <p className="mt-2 text-sm leading-6 text-slate-600">
                {successMessage}
              </p>

              <p className="mt-2 text-sm leading-6 text-slate-500">
                Liên kết có hiệu lực trong 30 phút và chỉ sử dụng được một lần.
              </p>

              <button
                type="button"
                onClick={() => {
                  setSuccess(false)
                  setEmail('')
                  setError('')
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

        <div className="mt-5 rounded-xl border border-slate-200 bg-slate-50 p-4 text-xs text-slate-500">
          <div className="flex items-start gap-2">
            <Info size={16} className="mt-0.5 text-blue-600 shrink-0" />
            <p>
              Nếu bạn không nhận được email, vui lòng kiểm tra hộp thư rác (Spam) hoặc đảm bảo rằng email bạn nhập là chính xác.
            </p>
          </div>
        </div>
      </div>
    </div>
  )
}