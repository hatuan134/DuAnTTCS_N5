import { useState } from 'react'
import type { FormEvent } from 'react'
import axios from 'axios'
import {
  AlertCircle,
  AlertTriangle,
  ArrowLeft,
  ArrowRight,
  BookOpen,
  Calendar,
  CheckCircle2,
  CreditCard,
  Eye,
  EyeOff,
  IdCard,
  KeyRound,
  LockKeyhole,
  Mail,
  MapPin,
  Phone,
  ShieldCheck,
  User,
} from 'lucide-react'
import { Link } from 'react-router-dom'
import { readerService } from './readerService'
import type { ReaderRegistrationResponse } from './readerService'

type ApiErrorResponse = {
  message?: string
  code?: string
  timestamp?: string
}

export default function RegisterPage() {
  // Form states
  const [fullName, setFullName] = useState('')
  const [email, setEmail] = useState('')
  const [memberCode, setMemberCode] = useState('')
  const [dateOfBirth, setDateOfBirth] = useState('')
  const [phone, setPhone] = useState('')
  const [address, setAddress] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')

  const [showPassword, setShowPassword] = useState(false)
  const [showConfirmPassword, setShowConfirmPassword] = useState(false)

  // Feedback states
  const [loading, setLoading] = useState(false)
  const [generalError, setGeneralError] = useState('')
  const [emailError, setEmailError] = useState('')
  const [memberCodeError, setMemberCodeError] = useState('')
  const [duplicateType, setDuplicateType] = useState<
    'EMAIL' | 'MEMBER_CODE' | 'BOTH' | null
  >(null)
  const [forgotPasswordUrl, setForgotPasswordUrl] = useState('/forgot-password')

  // Success state
  const [successData, setSuccessData] =
    useState<ReaderRegistrationResponse | null>(null)

  // Real-time blur checks for early detection
  const handleEmailBlur = async () => {
    const trimmed = email.trim()
    if (!trimmed || !trimmed.includes('@')) return

    try {
      const res = await readerService.checkDuplicate(trimmed, undefined)
      if (res.emailExists) {
        setEmailError(
          res.emailMessage ||
            'Email này đã được đăng ký trong hệ thống thư viện.',
        )
        setDuplicateType((prev) => (prev === 'MEMBER_CODE' ? 'BOTH' : 'EMAIL'))
        setForgotPasswordUrl(
          res.forgotPasswordUrl ||
            `/forgot-password?email=${encodeURIComponent(trimmed)}`,
        )
      } else {
        setEmailError('')
        setDuplicateType((prev) => (prev === 'EMAIL' ? null : prev))
      }
    } catch {
      // Ignore network errors on background blur check
    }
  }

  const handleMemberCodeBlur = async () => {
    const trimmed = memberCode.trim()
    if (!trimmed) return

    try {
      const res = await readerService.checkDuplicate(undefined, trimmed)
      if (res.memberCodeExists) {
        setMemberCodeError(
          res.memberCodeMessage ||
            'Mã sinh viên / cán bộ này đã được đăng ký hồ sơ bạn đọc.',
        )
        setDuplicateType((prev) => (prev === 'EMAIL' ? 'BOTH' : 'MEMBER_CODE'))
        if (!forgotPasswordUrl.includes('email=')) {
          setForgotPasswordUrl('/forgot-password')
        }
      } else {
        setMemberCodeError('')
        setDuplicateType((prev) => (prev === 'MEMBER_CODE' ? null : prev))
      }
    } catch {
      // Ignore network errors on background blur check
    }
  }

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setGeneralError('')
    setEmailError('')
    setMemberCodeError('')
    setDuplicateType(null)

    // Basic frontend validations
    const normEmail = email.trim().toLowerCase()
    const normMemberCode = memberCode.trim().toUpperCase()

    if (!fullName.trim()) {
      setGeneralError('Vui lòng nhập họ và tên.')
      return
    }
    if (!normEmail) {
      setGeneralError('Vui lòng nhập địa chỉ email.')
      return
    }
    if (!normMemberCode) {
      setGeneralError('Vui lòng nhập mã sinh viên hoặc mã cán bộ.')
      return
    }
    if (!dateOfBirth) {
      setGeneralError('Vui lòng chọn ngày sinh.')
      return
    }
    if (password.length < 6) {
      setGeneralError('Mật khẩu phải có tối thiểu 6 ký tự.')
      return
    }
    if (password !== confirmPassword) {
      setGeneralError('Mật khẩu xác nhận không khớp.')
      return
    }

    setLoading(true)

    try {
      const result = await readerService.register({
        fullName: fullName.trim(),
        email: normEmail,
        memberCode: normMemberCode,
        dateOfBirth,
        phone: phone.trim() || undefined,
        address: address.trim() || undefined,
        password,
      })

      setSuccessData(result)
    } catch (err: unknown) {
      if (axios.isAxiosError<ApiErrorResponse>(err)) {
        const errorData = err.response?.data
        const errorCode = errorData?.code
        const errorMessage =
          errorData?.message || 'Đăng ký hồ sơ không thành công.'

        if (errorCode === 'DUPLICATE_EMAIL') {
          setEmailError(errorMessage)
          setDuplicateType('EMAIL')
          setForgotPasswordUrl(
            `/forgot-password?email=${encodeURIComponent(normEmail)}`,
          )
        } else if (errorCode === 'DUPLICATE_MEMBER_CODE') {
          setMemberCodeError(errorMessage)
          setDuplicateType('MEMBER_CODE')
          setForgotPasswordUrl('/forgot-password')
        } else {
          setGeneralError(errorMessage)
        }
      } else {
        setGeneralError('Không thể kết nối đến máy chủ. Vui lòng thử lại sau.')
      }
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen bg-slate-50">
      <div className="grid min-h-screen lg:grid-cols-[1fr_1.15fr]">
        {/* =========================
            BÊN TRÁI: SHOWCASE & BRANDING
        ========================= */}
        <section className="relative hidden overflow-hidden bg-slate-950 p-12 text-white lg:flex lg:flex-col lg:justify-between">
          <div className="relative z-10">
            <Link to="/login" className="inline-flex items-center gap-3">
              <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-blue-600 shadow-lg shadow-blue-600/20">
                <BookOpen size={25} />
              </div>
              <div>
                <p className="text-xl font-bold tracking-wide">LIBRA</p>
                <p className="text-sm text-slate-400">
                  Library Management System
                </p>
              </div>
            </Link>
          </div>

          <div className="relative z-10 max-w-xl">
            <div className="mb-5 inline-flex items-center gap-2 rounded-full border border-blue-500/30 bg-blue-950/60 px-3 py-1.5 text-sm text-blue-300">
              <IdCard size={16} />
              Cổng tiếp nhận bạn đọc trực tuyến
            </div>

            <h1 className="text-4xl font-bold leading-tight tracking-tight lg:text-5xl">
              Đăng ký bạn đọc,
              <span className="block text-blue-400">mở ra tri thức mới.</span>
            </h1>

            <p className="mt-6 text-base leading-7 text-slate-400">
              Chỉ cần mã sinh viên hoặc mã cán bộ của trường, bạn có thể đăng ký
              thẻ thư viện trực tuyến, mượn sách giáo trình và tài liệu nghiên
              cứu hoàn toàn miễn phí.
            </p>

            <div className="mt-8 space-y-4">
              <div className="flex items-start gap-3.5 rounded-xl border border-slate-800 bg-slate-900/60 p-4">
                <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-blue-600/20 text-blue-400">
                  <CreditCard size={18} />
                </div>
                <div>
                  <p className="font-semibold text-white">Thẻ thư viện số</p>
                  <p className="mt-0.5 text-xs text-slate-400 leading-relaxed">
                    Hồ sơ được số hóa tự động, liên kết trực tiếp với mã định
                    danh sinh viên / cán bộ.
                  </p>
                </div>
              </div>

              <div className="flex items-start gap-3.5 rounded-xl border border-slate-800 bg-slate-900/60 p-4">
                <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-emerald-600/20 text-emerald-400">
                  <ShieldCheck size={18} />
                </div>
                <div>
                  <p className="font-semibold text-white">Bảo mật tài khoản</p>
                  <p className="mt-0.5 text-xs text-slate-400 leading-relaxed">
                    Mỗi mã định danh và email chỉ gắn duy nhất với một hồ sơ bạn
                    đọc được bảo vệ.
                  </p>
                </div>
              </div>
            </div>
          </div>

          <div className="relative z-10 flex items-center justify-between text-sm text-slate-500">
            <span>LIBRA © 2026</span>
            <span>Sprint 1 · S1-03</span>
          </div>

          {/* Trang trí background */}
          <div className="absolute -right-40 -top-40 h-96 w-96 rounded-full bg-blue-600/10 blur-3xl" />
          <div className="absolute -bottom-40 left-20 h-96 w-96 rounded-full bg-indigo-500/10 blur-3xl" />
        </section>

        {/* =========================
            BÊN PHẢI: FORM ĐĂNG KÝ
        ========================= */}
        <section className="flex items-center justify-center px-5 py-8 sm:px-10 lg:px-12">
          <div className="w-full max-w-xl">
            {/* Header / Mobile Logo */}
            <div className="mb-6 flex items-center justify-between">
              <Link
                to="/login"
                className="inline-flex items-center gap-1.5 text-sm font-medium text-slate-500 transition hover:text-slate-800"
              >
                <ArrowLeft size={16} />
                Quay lại đăng nhập
              </Link>

              <div className="lg:hidden">
                <span className="text-xs font-semibold uppercase tracking-wider text-blue-600">
                  LIBRA SPRINT 1
                </span>
              </div>
            </div>

            {/* Success View */}
            {successData ? (
              <div className="rounded-2xl border border-emerald-200 bg-white p-8 shadow-sm text-center">
                <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-2xl bg-emerald-100 text-emerald-600">
                  <CheckCircle2 size={36} />
                </div>

                <h2 className="mt-5 text-2xl font-bold text-slate-900">
                  Đăng ký hồ sơ thành công!
                </h2>

                <p className="mt-2 text-sm text-slate-600 leading-relaxed">
                  Hồ sơ bạn đọc của bạn đã được ghi nhận vào hệ thống và đang ở
                  trạng thái{' '}
                  <span className="font-semibold text-amber-600">
                    Chờ duyệt
                  </span>
                  .
                </p>

                <div className="mt-6 rounded-xl border border-slate-200 bg-slate-50 p-4 text-left text-sm space-y-2">
                  <div className="flex justify-between">
                    <span className="text-slate-500">Họ và tên:</span>
                    <strong className="text-slate-800">
                      {successData.fullName}
                    </strong>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-slate-500">Email:</span>
                    <strong className="text-slate-800">
                      {successData.email}
                    </strong>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-slate-500">
                      Mã sinh viên / cán bộ:
                    </span>
                    <strong className="text-blue-700 font-mono">
                      {successData.memberCode}
                    </strong>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-slate-500">Trạng thái hồ sơ:</span>
                    <span className="inline-flex items-center rounded-md bg-amber-100 px-2 py-0.5 text-xs font-semibold text-amber-800">
                      CHỜ THỦ THƯ DUYỆT
                    </span>
                  </div>
                </div>

                <div className="mt-8 flex flex-col sm:flex-row gap-3">
                  <Link
                    to="/login"
                    className="flex-1 rounded-xl bg-blue-600 py-3.5 text-center text-sm font-semibold text-white shadow-sm transition hover:bg-blue-700"
                  >
                    Đến trang Đăng nhập
                  </Link>
                  <button
                    type="button"
                    onClick={() => {
                      setSuccessData(null)
                      setEmail('')
                      setMemberCode('')
                      setFullName('')
                      setPassword('')
                      setConfirmPassword('')
                    }}
                    className="rounded-xl border border-slate-300 bg-white px-5 py-3.5 text-sm font-semibold text-slate-700 hover:bg-slate-50 transition"
                  >
                    Đăng ký thêm hồ sơ
                  </button>
                </div>
              </div>
            ) : (
              <div>
                {/* Form Title */}
                <div>
                  <p className="text-xs font-bold uppercase tracking-[0.2em] text-blue-600">
                    S1-03 · Tiếp nhận hồ sơ
                  </p>
                  <h2 className="mt-1 text-2xl sm:text-3xl font-bold tracking-tight text-slate-900">
                    Đăng ký tài khoản bạn đọc
                  </h2>
                  <p className="mt-1.5 text-sm text-slate-500">
                    Điền đầy đủ thông tin để kích hoạt thẻ thư viện và tài khoản
                    mượn trả sách.
                  </p>
                </div>

                {/* =========================================================
                    THÔNG BÁO TRÙNG LẶP NỔI BẬT & GỢI Ý QUÊN MẬT KHẨU
                ========================================================= */}
                {duplicateType && (
                  <div className="mt-5 overflow-hidden rounded-2xl border border-amber-300 bg-gradient-to-br from-amber-50 to-orange-50 p-5 shadow-sm transition-all">
                    <div className="flex items-start gap-3.5">
                      <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-amber-500 text-white shadow-md shadow-amber-500/20">
                        <AlertTriangle size={22} />
                      </div>
                      <div className="flex-1">
                        <h3 className="text-base font-bold text-amber-950">
                          {duplicateType === 'EMAIL'
                            ? 'Email này đã tồn tại trong hệ thống'
                            : duplicateType === 'MEMBER_CODE'
                              ? 'Mã sinh viên / cán bộ đã được đăng ký'
                              : 'Thông tin định danh đã tồn tại trong hệ thống'}
                        </h3>
                        <p className="mt-1 text-sm leading-relaxed text-amber-900">
                          {emailError ||
                            memberCodeError ||
                            'Bạn có thể đã có tài khoản bạn đọc trước đây. Vui lòng không tạo hồ sơ mới trùng lặp.'}
                        </p>

                        <div className="mt-4 flex flex-wrap items-center gap-3">
                          <Link
                            to={forgotPasswordUrl}
                            className="inline-flex items-center gap-2 rounded-xl bg-amber-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm hover:bg-amber-700 transition"
                          >
                            <KeyRound size={16} />
                            Sử dụng chức năng Quên mật khẩu
                          </Link>

                          <Link
                            to="/login"
                            className="inline-flex items-center gap-1.5 rounded-xl border border-amber-300 bg-white/80 px-3.5 py-2.5 text-sm font-semibold text-amber-900 hover:bg-white transition"
                          >
                            Đăng nhập ngay
                            <ArrowRight size={15} />
                          </Link>
                        </div>
                      </div>
                    </div>
                  </div>
                )}

                {/* Lỗi chung khác */}
                {generalError && (
                  <div className="mt-5 flex items-start gap-3 rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700">
                    <AlertCircle size={18} className="shrink-0 mt-0.5" />
                    <span>{generalError}</span>
                  </div>
                )}

                {/* Form fields */}
                <form onSubmit={handleSubmit} className="mt-6 space-y-4">
                  {/* Họ và tên */}
                  <div>
                    <label className="mb-1.5 block text-xs font-semibold uppercase tracking-wider text-slate-700">
                      Họ và tên <span className="text-red-500">*</span>
                    </label>
                    <div className="relative">
                      <User
                        size={17}
                        className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400"
                      />
                      <input
                        type="text"
                        required
                        value={fullName}
                        onChange={(e) => {
                          setFullName(e.target.value)
                          setGeneralError('')
                        }}
                        placeholder="Nguyễn Văn A"
                        className="w-full rounded-xl border border-slate-300 bg-white py-3 pl-10 pr-4 text-sm text-slate-900 outline-none transition placeholder:text-slate-400 focus:border-blue-500 focus:ring-4 focus:ring-blue-100"
                      />
                    </div>
                  </div>

                  {/* 2 cột: Email & Mã sinh viên/cán bộ */}
                  <div className="grid sm:grid-cols-2 gap-4">
                    {/* Email */}
                    <div>
                      <div className="mb-1.5 flex items-center justify-between">
                        <label className="block text-xs font-semibold uppercase tracking-wider text-slate-700">
                          Email <span className="text-red-500">*</span>
                        </label>
                      </div>
                      <div className="relative">
                        <Mail
                          size={17}
                          className={`absolute left-3.5 top-1/2 -translate-y-1/2 ${
                            emailError ? 'text-red-500' : 'text-slate-400'
                          }`}
                        />
                        <input
                          type="email"
                          required
                          value={email}
                          onBlur={handleEmailBlur}
                          onChange={(e) => {
                            setEmail(e.target.value)
                            setEmailError('')
                            setGeneralError('')
                          }}
                          placeholder="sv@ictu.edu.vn"
                          className={`w-full rounded-xl border py-3 pl-10 pr-4 text-sm text-slate-900 outline-none transition placeholder:text-slate-400 focus:ring-4 ${
                            emailError
                              ? 'border-red-400 bg-red-50/50 focus:border-red-500 focus:ring-red-100'
                              : 'border-slate-300 bg-white focus:border-blue-500 focus:ring-blue-100'
                          }`}
                        />
                      </div>
                      {emailError && (
                        <p className="mt-1 text-xs text-red-600 font-medium">
                          {emailError}
                        </p>
                      )}
                    </div>

                    {/* Mã sinh viên / cán bộ */}
                    <div>
                      <div className="mb-1.5 flex items-center justify-between">
                        <label className="block text-xs font-semibold uppercase tracking-wider text-slate-700">
                          Mã SV / Mã Cán bộ{' '}
                          <span className="text-red-500">*</span>
                        </label>
                      </div>
                      <div className="relative">
                        <IdCard
                          size={17}
                          className={`absolute left-3.5 top-1/2 -translate-y-1/2 ${
                            memberCodeError ? 'text-red-500' : 'text-slate-400'
                          }`}
                        />
                        <input
                          type="text"
                          required
                          value={memberCode}
                          onBlur={handleMemberCodeBlur}
                          onChange={(e) => {
                            setMemberCode(e.target.value)
                            setMemberCodeError('')
                            setGeneralError('')
                          }}
                          placeholder="DTC215480201..."
                          className={`w-full rounded-xl border py-3 pl-10 pr-4 text-sm text-slate-900 uppercase font-mono outline-none transition placeholder:text-slate-400 focus:ring-4 ${
                            memberCodeError
                              ? 'border-red-400 bg-red-50/50 focus:border-red-500 focus:ring-red-100'
                              : 'border-slate-300 bg-white focus:border-blue-500 focus:ring-blue-100'
                          }`}
                        />
                      </div>
                      {memberCodeError && (
                        <p className="mt-1 text-xs text-red-600 font-medium">
                          {memberCodeError}
                        </p>
                      )}
                    </div>
                  </div>

                  {/* 2 cột: Ngày sinh & Số điện thoại */}
                  <div className="grid sm:grid-cols-2 gap-4">
                    {/* Ngày sinh */}
                    <div>
                      <label className="mb-1.5 block text-xs font-semibold uppercase tracking-wider text-slate-700">
                        Ngày sinh <span className="text-red-500">*</span>
                      </label>
                      <div className="relative">
                        <Calendar
                          size={17}
                          className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400"
                        />
                        <input
                          type="date"
                          required
                          value={dateOfBirth}
                          onChange={(e) => setDateOfBirth(e.target.value)}
                          className="w-full rounded-xl border border-slate-300 bg-white py-3 pl-10 pr-4 text-sm text-slate-900 outline-none transition focus:border-blue-500 focus:ring-4 focus:ring-blue-100"
                        />
                      </div>
                    </div>

                    {/* Số điện thoại */}
                    <div>
                      <label className="mb-1.5 block text-xs font-semibold uppercase tracking-wider text-slate-700">
                        Số điện thoại
                      </label>
                      <div className="relative">
                        <Phone
                          size={17}
                          className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400"
                        />
                        <input
                          type="tel"
                          value={phone}
                          onChange={(e) => setPhone(e.target.value)}
                          placeholder="0987654321"
                          className="w-full rounded-xl border border-slate-300 bg-white py-3 pl-10 pr-4 text-sm text-slate-900 outline-none transition placeholder:text-slate-400 focus:border-blue-500 focus:ring-4 focus:ring-blue-100"
                        />
                      </div>
                    </div>
                  </div>

                  {/* Địa chỉ */}
                  <div>
                    <label className="mb-1.5 block text-xs font-semibold uppercase tracking-wider text-slate-700">
                      Địa chỉ / Khoa / Đơn vị
                    </label>
                    <div className="relative">
                      <MapPin
                        size={17}
                        className="absolute left-3.5 top-3.5 text-slate-400"
                      />
                      <input
                        type="text"
                        value={address}
                        onChange={(e) => setAddress(e.target.value)}
                        placeholder="Khoa CNTT, Ký túc xá K1..."
                        className="w-full rounded-xl border border-slate-300 bg-white py-3 pl-10 pr-4 text-sm text-slate-900 outline-none transition placeholder:text-slate-400 focus:border-blue-500 focus:ring-4 focus:ring-blue-100"
                      />
                    </div>
                  </div>

                  {/* 2 cột: Mật khẩu & Nhập lại mật khẩu */}
                  <div className="grid sm:grid-cols-2 gap-4">
                    {/* Mật khẩu */}
                    <div>
                      <label className="mb-1.5 block text-xs font-semibold uppercase tracking-wider text-slate-700">
                        Mật khẩu <span className="text-red-500">*</span>
                      </label>
                      <div className="relative">
                        <LockKeyhole
                          size={17}
                          className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400"
                        />
                        <input
                          type={showPassword ? 'text' : 'password'}
                          required
                          value={password}
                          onChange={(e) => setPassword(e.target.value)}
                          placeholder="Tối thiểu 6 ký tự"
                          className="w-full rounded-xl border border-slate-300 bg-white py-3 pl-10 pr-10 text-sm text-slate-900 outline-none transition focus:border-blue-500 focus:ring-4 focus:ring-blue-100"
                        />
                        <button
                          type="button"
                          onClick={() => setShowPassword(!showPassword)}
                          className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600"
                        >
                          {showPassword ? (
                            <EyeOff size={16} />
                          ) : (
                            <Eye size={16} />
                          )}
                        </button>
                      </div>
                    </div>

                    {/* Nhập lại mật khẩu */}
                    <div>
                      <label className="mb-1.5 block text-xs font-semibold uppercase tracking-wider text-slate-700">
                        Xác nhận mật khẩu <span className="text-red-500">*</span>
                      </label>
                      <div className="relative">
                        <LockKeyhole
                          size={17}
                          className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400"
                        />
                        <input
                          type={showConfirmPassword ? 'text' : 'password'}
                          required
                          value={confirmPassword}
                          onChange={(e) => setConfirmPassword(e.target.value)}
                          placeholder="Khớp với mật khẩu trên"
                          className="w-full rounded-xl border border-slate-300 bg-white py-3 pl-10 pr-10 text-sm text-slate-900 outline-none transition focus:border-blue-500 focus:ring-4 focus:ring-blue-100"
                        />
                        <button
                          type="button"
                          onClick={() =>
                            setShowConfirmPassword(!showConfirmPassword)
                          }
                          className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600"
                        >
                          {showConfirmPassword ? (
                            <EyeOff size={16} />
                          ) : (
                            <Eye size={16} />
                          )}
                        </button>
                      </div>
                    </div>
                  </div>

                  {/* Nút gửi */}
                  <button
                    type="submit"
                    disabled={loading}
                    className="group mt-2 flex w-full items-center justify-center gap-2 rounded-xl bg-blue-600 px-4 py-3.5 text-sm font-semibold text-white shadow-sm transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-60"
                  >
                    {loading ? (
                      <>
                        <span className="h-4 w-4 animate-spin rounded-full border-2 border-white/30 border-t-white" />
                        Đang xử lý hồ sơ...
                      </>
                    ) : (
                      <>
                        Nộp hồ sơ đăng ký bạn đọc
                        <ArrowRight
                          size={18}
                          className="transition group-hover:translate-x-0.5"
                        />
                      </>
                    )}
                  </button>
                </form>

                {/* Footer links */}
                <div className="mt-6 flex flex-col sm:flex-row items-center justify-between gap-3 border-t border-slate-200 pt-5 text-sm">
                  <span className="text-slate-500">
                    Đã có tài khoản bạn đọc?{' '}
                    <Link
                      to="/login"
                      className="font-semibold text-blue-600 hover:text-blue-700"
                    >
                      Đăng nhập
                    </Link>
                  </span>

                  <Link
                    to="/forgot-password"
                    className="font-medium text-slate-600 hover:text-blue-600 transition"
                  >
                    Quên mật khẩu?
                  </Link>
                </div>
              </div>
            )}
          </div>
        </section>
      </div>
    </div>
  )
}
