import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import axios from 'axios'
import {
  AlertCircle,
  CheckCircle2,
  CreditCard,
  Eye,
  EyeOff,
  KeyRound,
  Mail,
  MapPin,
  Phone,
  RefreshCw,
  Save,
  ShieldCheck,
  User,
} from 'lucide-react'

import Card from '../../components/ui/Card'
import PageHeader from '../../components/ui/PageHeader'
import LoadingState from '../../components/ui/LoadingState'
import {
  profileService,
  type UserProfile,
} from './profileService'

type ProfileFormState = {
  phone: string
  address: string
  email: string
}

type PasswordFormState = {
  currentPassword: string
  newPassword: string
  confirmPassword: string
}

function getErrorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    return (
      error.response?.data?.message ||
      error.message ||
      'Không thể kết nối đến máy chủ API.'
    )
  }
  if (error instanceof Error) {
    return error.message
  }
  return 'Đã xảy ra lỗi không xác định.'
}

function formatDate(dateStr?: string | null) {
  if (!dateStr) return '-'
  try {
    const d = new Date(dateStr)
    if (isNaN(d.getTime())) return dateStr
    return d.toLocaleDateString('vi-VN', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
    })
  } catch {
    return dateStr
  }
}

export default function ChangePasswordPage() {
  const [loading, setLoading] = useState(true)
  const [profileData, setProfileData] = useState<UserProfile | null>(null)

  const [profileForm, setProfileForm] = useState<ProfileFormState>({
    phone: '',
    address: '',
    email: '',
  })
  const [savedEmail, setSavedEmail] = useState('')
  const [profilePassword, setProfilePassword] = useState('')
  const [showProfilePassword, setShowProfilePassword] = useState(false)
  const [isUpdatingProfile, setIsUpdatingProfile] = useState(false)
  const [profileMessage, setProfileMessage] = useState('')
  const [profileError, setProfileError] = useState('')

  const [passwordForm, setPasswordForm] = useState<PasswordFormState>({
    currentPassword: '',
    newPassword: '',
    confirmPassword: '',
  })
  const [showCurrentPassword, setShowCurrentPassword] = useState(false)
  const [showNewPassword, setShowNewPassword] = useState(false)
  const [showConfirmPassword, setShowConfirmPassword] = useState(false)
  const [isChangingPassword, setIsChangingPassword] = useState(false)
  const [passwordMessage, setPasswordMessage] = useState('')
  const [passwordError, setPasswordError] = useState('')

  const fetchProfile = async () => {
    try {
      setLoading(true)
      setProfileError('')
      const data = await profileService.getProfile()
      setProfileData(data)
      setProfileForm({
        phone: data.phone || '',
        address: data.address || '',
        email: data.email || '',
      })
      setSavedEmail(data.email || '')
    } catch (err) {
      setProfileError(getErrorMessage(err))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchProfile()
  }, [])

  useEffect(() => {
    if (!profileMessage) return
    const timer = setTimeout(() => setProfileMessage(''), 4000)
    return () => clearTimeout(timer)
  }, [profileMessage])

  useEffect(() => {
    if (!passwordMessage) return
    const timer = setTimeout(() => setPasswordMessage(''), 4000)
    return () => clearTimeout(timer)
  }, [passwordMessage])

  const emailChanged =
    profileForm.email.trim().toLowerCase() !== savedEmail.trim().toLowerCase()

  const handleProfileSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setProfileError('')
    setProfileMessage('')

    const email = profileForm.email.trim()
    if (!email) {
      setProfileError('Email không được để trống.')
      return
    }

    const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
    if (!emailPattern.test(email)) {
      setProfileError('Email không đúng định dạng.')
      return
    }

    if (emailChanged) {
      if (!profilePassword) {
        setProfileError('Bạn phải nhập mật khẩu hiện tại để xác nhận đổi email.')
        return
      }
    }

    try {
      setIsUpdatingProfile(true)
      const updated = await profileService.updateProfile({
        email,
        phone: profileForm.phone.trim(),
        address: profileForm.address.trim(),
        currentPassword: emailChanged ? profilePassword : undefined,
      })

      setProfileData(updated)
      setSavedEmail(updated.email)
      setProfilePassword('')
      setProfileMessage('Cập nhật thông tin liên hệ thành công!')
    } catch (err) {
      setProfileError(getErrorMessage(err))
    } finally {
      setIsUpdatingProfile(false)
    }
  }

  const handlePasswordSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setPasswordError('')
    setPasswordMessage('')

    if (
      !passwordForm.currentPassword ||
      !passwordForm.newPassword ||
      !passwordForm.confirmPassword
    ) {
      setPasswordError('Vui lòng nhập đầy đủ tất cả các trường mật khẩu.')
      return
    }

    if (passwordForm.newPassword.length < 8) {
      setPasswordError('Mật khẩu mới phải có ít nhất 8 ký tự.')
      return
    }

    const hasLetter = /[A-Za-z]/.test(passwordForm.newPassword)
    const hasNumber = /\d/.test(passwordForm.newPassword)
    if (!hasLetter || !hasNumber) {
      setPasswordError('Mật khẩu mới phải có cả chữ cái và chữ số.')
      return
    }

    if (passwordForm.newPassword !== passwordForm.confirmPassword) {
      setPasswordError('Xác nhận mật khẩu mới không khớp.')
      return
    }

    if (passwordForm.newPassword === passwordForm.currentPassword) {
      setPasswordError('Mật khẩu mới phải khác mật khẩu hiện tại.')
      return
    }

    try {
      setIsChangingPassword(true)
      const res = await profileService.changePassword({
        currentPassword: passwordForm.currentPassword,
        newPassword: passwordForm.newPassword,
        confirmPassword: passwordForm.confirmPassword,
      })

      setPasswordMessage(res.message || 'Đổi mật khẩu thành công!')
      setPasswordForm({
        currentPassword: '',
        newPassword: '',
        confirmPassword: '',
      })
    } catch (err) {
      setPasswordError(getErrorMessage(err))
    } finally {
      setIsChangingPassword(false)
    }
  }

  if (loading) {
    return (
      <div className="space-y-6">
        <PageHeader
          title="Hồ sơ cá nhân"
          description="Quản lý thông tin liên hệ, thông tin thẻ thư viện và mật khẩu tài khoản."
        />
        <div className="p-12">
          <LoadingState />
        </div>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <PageHeader
          title="Hồ sơ cá nhân"
          description="Quản lý thông tin liên hệ, thông tin thẻ thư viện và mật khẩu tài khoản."
        />

        <button
          type="button"
          onClick={fetchProfile}
          title="Tải lại dữ liệu"
          className="inline-flex items-center gap-2 rounded-lg border border-slate-200 bg-white px-3.5 py-2 text-sm font-medium text-slate-700 shadow-sm transition hover:bg-slate-50"
        >
          <RefreshCw size={16} />
          Làm mới
        </button>
      </div>

      {/* THÔNG TIN THẺ */}
      <Card>
        <div className="border-b border-slate-200 p-5">
          <div className="flex items-center gap-3">
            <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-blue-50 text-blue-600">
              <CreditCard size={22} />
            </div>

            <div>
              <h2 className="text-lg font-semibold text-slate-900">
                Thông tin thẻ thư viện
              </h2>

              <p className="mt-1 text-sm text-slate-500">
                Thông tin này do thư viện quản lý và bạn đọc không thể tự thay đổi.
              </p>
            </div>
          </div>
        </div>

        {profileData?.hasCard ? (
          <div className="grid gap-5 p-5 md:grid-cols-2 xl:grid-cols-4">
            <InfoItem
              label="Mã thẻ"
              value={profileData.cardNumber || '-'}
            />

            <InfoItem
              label="Loại thẻ"
              value={profileData.cardTypeName || '-'}
            />

            <InfoItem
              label="Ngày hết hạn"
              value={formatDate(profileData.cardExpiresAt)}
            />

            <div>
              <p className="text-sm text-slate-500">Trạng thái thẻ</p>
              <span
                className={`mt-2 inline-flex rounded-full px-3 py-1 text-xs font-semibold ${
                  profileData.cardStatus === 'ACTIVE'
                    ? 'bg-emerald-50 text-emerald-700 ring-1 ring-emerald-600/20'
                    : 'bg-amber-50 text-amber-700 ring-1 ring-amber-600/20'
                }`}
              >
                {profileData.cardStatus === 'ACTIVE'
                  ? 'Đang hoạt động'
                  : profileData.cardStatus || 'Khóa'}
              </span>
            </div>
          </div>
        ) : (
          <div className="p-5 text-sm text-slate-500">
            Tài khoản hiện chưa được gắn thẻ thư viện vật lý/điện tử (áp dụng cho tài khoản Quản trị / Thủ thư hoặc Bạn đọc chờ cấp thẻ).
          </div>
        )}
      </Card>

      <div className="grid gap-6 xl:grid-cols-2">
        {/* THÔNG TIN CÁ NHÂN */}
        <Card>
          <div className="border-b border-slate-200 p-5">
            <div className="flex items-center gap-3">
              <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-slate-100 text-slate-600">
                <User size={20} />
              </div>

              <div>
                <h2 className="text-lg font-semibold text-slate-900">
                  Thông tin cá nhân & liên hệ
                </h2>

                <p className="mt-1 text-sm text-slate-500">
                  Cập nhật số điện thoại, địa chỉ và email nhận thông báo.
                </p>
              </div>
            </div>
          </div>

          <form onSubmit={handleProfileSubmit} className="space-y-5 p-5">
            <ReadOnlyField
              label="Họ và tên"
              value={profileData?.fullName || '-'}
            />

            <ReadOnlyField
              label="Ngày sinh"
              value={formatDate(profileData?.dateOfBirth)}
            />

            {profileData?.memberCode && (
              <ReadOnlyField
                label="Mã sinh viên / Mã cán bộ"
                value={profileData.memberCode}
              />
            )}

            <div>
              <label className="mb-1.5 block text-sm font-medium text-slate-700">
                Số điện thoại
              </label>

              <div className="relative">
                <Phone
                  size={18}
                  className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400"
                />

                <input
                  value={profileForm.phone}
                  onChange={(event) => {
                    setProfileForm({
                      ...profileForm,
                      phone: event.target.value,
                    })
                    setProfileError('')
                    setProfileMessage('')
                  }}
                  placeholder="Ví dụ: 0912345678"
                  className="w-full rounded-lg border border-slate-300 py-2.5 pl-10 pr-3 text-sm outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
                />
              </div>
            </div>

            <div>
              <label className="mb-1.5 block text-sm font-medium text-slate-700">
                Địa chỉ
              </label>

              <div className="relative">
                <MapPin
                  size={18}
                  className="absolute left-3 top-3 text-slate-400"
                />

                <textarea
                  rows={2}
                  value={profileForm.address}
                  onChange={(event) => {
                    setProfileForm({
                      ...profileForm,
                      address: event.target.value,
                    })
                    setProfileError('')
                    setProfileMessage('')
                  }}
                  placeholder="Nhập địa chỉ liên hệ..."
                  className="w-full resize-none rounded-lg border border-slate-300 py-2.5 pl-10 pr-3 text-sm outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
                />
              </div>
            </div>

            <div>
              <label className="mb-1.5 block text-sm font-medium text-slate-700">
                Email nhận thông báo
                <span className="ml-1 text-red-500">*</span>
              </label>

              <div className="relative">
                <Mail
                  size={18}
                  className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400"
                />

                <input
                  type="email"
                  value={profileForm.email}
                  onChange={(event) => {
                    setProfileForm({
                      ...profileForm,
                      email: event.target.value,
                    })
                    setProfileError('')
                    setProfileMessage('')
                  }}
                  className="w-full rounded-lg border border-slate-300 py-2.5 pl-10 pr-3 text-sm outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
                />
              </div>
            </div>

            {emailChanged && (
              <div className="rounded-xl border border-amber-200 bg-amber-50/70 p-4">
                <label className="mb-1.5 block text-sm font-medium text-amber-900">
                  Xác nhận mật khẩu hiện tại
                  <span className="ml-1 text-red-500">*</span>
                </label>

                <p className="mb-2 text-xs text-amber-700">
                  Bạn đang thay đổi địa chỉ email tài khoản nên cần xác nhận lại mật khẩu hiện tại để đảm bảo an toàn.
                </p>

                <div className="relative">
                  <KeyRound
                    size={18}
                    className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400"
                  />

                  <input
                    type={showProfilePassword ? 'text' : 'password'}
                    value={profilePassword}
                    onChange={(event) => {
                      setProfilePassword(event.target.value)
                      setProfileError('')
                    }}
                    placeholder="Nhập mật khẩu hiện tại của bạn"
                    className="w-full rounded-lg border border-slate-300 bg-white py-2.5 pl-10 pr-11 text-sm outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
                  />

                  <button
                    type="button"
                    onClick={() => setShowProfilePassword(!showProfilePassword)}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-700"
                  >
                    {showProfilePassword ? <EyeOff size={18} /> : <Eye size={18} />}
                  </button>
                </div>
              </div>
            )}

            {profileError && (
              <div className="flex items-center gap-2 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
                <AlertCircle size={18} className="shrink-0 text-red-500" />
                <span>{profileError}</span>
              </div>
            )}

            {profileMessage && (
              <div className="flex items-center gap-2 rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-700">
                <CheckCircle2 size={18} className="shrink-0 text-emerald-500" />
                <span>{profileMessage}</span>
              </div>
            )}

            <div className="flex justify-end border-t border-slate-100 pt-5">
              <button
                type="submit"
                disabled={isUpdatingProfile}
                className="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-4 py-2.5 text-sm font-medium text-white shadow-sm transition hover:bg-blue-700 disabled:opacity-50"
              >
                {isUpdatingProfile ? (
                  <>
                    <RefreshCw size={16} className="animate-spin" />
                    Đang lưu...
                  </>
                ) : (
                  <>
                    <Save size={17} />
                    Lưu thay đổi
                  </>
                )}
              </button>
            </div>
          </form>
        </Card>

        {/* ĐỔI MẬT KHẨU */}
        <Card>
          <div className="border-b border-slate-200 p-5">
            <div className="flex items-center gap-3">
              <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-amber-50 text-amber-600">
                <ShieldCheck size={20} />
              </div>

              <div>
                <h2 className="text-lg font-semibold text-slate-900">
                  Đổi mật khẩu tài khoản
                </h2>

                <p className="mt-1 text-sm text-slate-500">
                  Cập nhật mật khẩu định kỳ để bảo vệ tài khoản thư viện.
                </p>
              </div>
            </div>
          </div>

          <form onSubmit={handlePasswordSubmit} className="space-y-5 p-5">
            <PasswordField
              label="Mật khẩu hiện tại"
              value={passwordForm.currentPassword}
              show={showCurrentPassword}
              onToggle={() => setShowCurrentPassword(!showCurrentPassword)}
              onChange={(value) => {
                setPasswordForm({
                  ...passwordForm,
                  currentPassword: value,
                })
                setPasswordError('')
                setPasswordMessage('')
              }}
            />

            <PasswordField
              label="Mật khẩu mới"
              value={passwordForm.newPassword}
              show={showNewPassword}
              onToggle={() => setShowNewPassword(!showNewPassword)}
              onChange={(value) => {
                setPasswordForm({
                  ...passwordForm,
                  newPassword: value,
                })
                setPasswordError('')
                setPasswordMessage('')
              }}
            />

            <PasswordField
              label="Nhập lại mật khẩu mới"
              value={passwordForm.confirmPassword}
              show={showConfirmPassword}
              onToggle={() => setShowConfirmPassword(!showConfirmPassword)}
              onChange={(value) => {
                setPasswordForm({
                  ...passwordForm,
                  confirmPassword: value,
                })
                setPasswordError('')
                setPasswordMessage('')
              }}
            />

            <div className="rounded-xl border border-slate-200 bg-slate-50 p-4">
              <p className="text-sm font-semibold text-slate-700">
                Quy định bảo mật mật khẩu (S1-06)
              </p>

              <ul className="mt-2 space-y-1.5 text-xs text-slate-600">
                <li className="flex items-center gap-2">
                  <span className="h-1.5 w-1.5 rounded-full bg-slate-400" />
                  Mật khẩu có ít nhất 8 ký tự.
                </li>
                <li className="flex items-center gap-2">
                  <span className="h-1.5 w-1.5 rounded-full bg-slate-400" />
                  Bắt buộc chứa cả chữ cái và chữ số.
                </li>
                <li className="flex items-center gap-2">
                  <span className="h-1.5 w-1.5 rounded-full bg-slate-400" />
                  Mật khẩu mới phải khác mật khẩu hiện tại.
                </li>
                <li className="flex items-center gap-2">
                  <span className="h-1.5 w-1.5 rounded-full bg-slate-400" />
                  Không được trùng với 3 mật khẩu đã sử dụng gần nhất.
                </li>
              </ul>
            </div>

            {passwordError && (
              <div className="flex items-center gap-2 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
                <AlertCircle size={18} className="shrink-0 text-red-500" />
                <span>{passwordError}</span>
              </div>
            )}

            {passwordMessage && (
              <div className="flex items-center gap-2 rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-700">
                <CheckCircle2 size={18} className="shrink-0 text-emerald-500" />
                <span>{passwordMessage}</span>
              </div>
            )}

            <div className="flex justify-end border-t border-slate-100 pt-5">
              <button
                type="submit"
                disabled={isChangingPassword}
                className="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-5 py-2.5 text-sm font-medium text-white shadow-sm transition hover:bg-blue-700 disabled:opacity-50"
              >
                {isChangingPassword ? (
                  <>
                    <RefreshCw size={16} className="animate-spin" />
                    Đang xử lý...
                  </>
                ) : (
                  <>
                    <KeyRound size={17} />
                    Đổi mật khẩu
                  </>
                )}
              </button>
            </div>
          </form>
        </Card>
      </div>
    </div>
  )
}

type InfoItemProps = {
  label: string
  value: string
}

function InfoItem({ label, value }: InfoItemProps) {
  return (
    <div>
      <p className="text-sm text-slate-500">{label}</p>
      <p className="mt-1.5 font-semibold text-slate-900">{value}</p>
    </div>
  )
}

type ReadOnlyFieldProps = {
  label: string
  value: string
}

function ReadOnlyField({ label, value }: ReadOnlyFieldProps) {
  return (
    <div>
      <label className="mb-1.5 block text-sm font-medium text-slate-700">
        {label}
      </label>
      <input
        value={value}
        readOnly
        className="w-full cursor-not-allowed rounded-lg border border-slate-200 bg-slate-50 px-3.5 py-2.5 text-sm text-slate-500 select-none"
      />
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
          className="w-full rounded-lg border border-slate-300 py-2.5 pl-10 pr-11 text-sm outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
        />

        <button
          type="button"
          onClick={onToggle}
          className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-700"
        >
          {show ? <EyeOff size={18} /> : <Eye size={18} />}
        </button>
      </div>
    </div>
  )
}