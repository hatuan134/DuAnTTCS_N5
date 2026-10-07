import axios from 'axios'
import { useEffect, useMemo, useState } from 'react'
import type { FormEvent, ReactNode } from 'react'

import {
  AlertCircle,
  CreditCard,
  Eye,
  EyeOff,
  KeyRound,
  Mail,
  MapPin,
  Phone,
  Save,
  ShieldCheck,
  User,
} from 'lucide-react'

import Card from '../../components/ui/Card'
import FeedbackAlert from '../../components/ui/FeedbackAlert'
import LoadingState from '../../components/ui/LoadingState'
import PageHeader from '../../components/ui/PageHeader'
import {
  getCurrentUser,
  updateCurrentUser,
} from '../../core/auth/authStorage'
import {
  profileService,
} from './profileService'
import type {
  ReaderSelfProfile,
} from './profileService'

type ProfileForm = {
  phone: string
  address: string
  email: string
}

type PasswordForm = {
  currentPassword: string
  newPassword: string
  confirmPassword: string
}

type ApiErrorBody = {
  message?: string
}

const PHONE_PATTERN = /^0\d{9}$/
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export default function ChangePasswordPage() {
  const currentUser = getCurrentUser()

  const [profileData, setProfileData] =
    useState<ReaderSelfProfile | null>(null)

  const [profile, setProfile] =
    useState<ProfileForm>({
      phone: '',
      address: '',
      email: '',
    })

  const [savedEmail, setSavedEmail] =
    useState('')

  const [profilePassword, setProfilePassword] =
    useState('')

  const [passwordForm, setPasswordForm] =
    useState<PasswordForm>({
      currentPassword: '',
      newPassword: '',
      confirmPassword: '',
    })

  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')
  const [savingProfile, setSavingProfile] =
    useState(false)
  const [savingPassword, setSavingPassword] =
    useState(false)

  const [profileMessage, setProfileMessage] =
    useState('')
  const [profileError, setProfileError] =
    useState('')
  const [passwordMessage, setPasswordMessage] =
    useState('')
  const [passwordError, setPasswordError] =
    useState('')

  const [showProfilePassword, setShowProfilePassword] =
    useState(false)
  const [showCurrentPassword, setShowCurrentPassword] =
    useState(false)
  const [showNewPassword, setShowNewPassword] =
    useState(false)
  const [showConfirmPassword, setShowConfirmPassword] =
    useState(false)

  useEffect(() => {
    let active = true

    const loadProfile = async () => {
      if (currentUser?.role !== 'READER') {
        setLoadError(
          'Chức năng hồ sơ cá nhân S1-06 dành cho tài khoản Bạn đọc.',
        )
        setLoading(false)
        return
      }

      try {
        const data = await profileService.getMine()

        if (!active) return

        setProfileData(data)
        setProfile({
          phone: data.phone ?? '',
          address: data.address ?? '',
          email: data.email,
        })
        setSavedEmail(data.email)
        setLoadError('')
      } catch (error) {
        if (!active) return
        setLoadError(
          getApiErrorMessage(
            error,
            'Không thể tải hồ sơ cá nhân. Vui lòng thử lại.',
          ),
        )
      } finally {
        if (active) {
          setLoading(false)
        }
      }
    }

    void loadProfile()

    return () => {
      active = false
    }
  }, [currentUser?.role])

  const emailChanged = useMemo(
    () =>
      profile.email.trim().toLowerCase() !==
      savedEmail.trim().toLowerCase(),
    [profile.email, savedEmail],
  )

  const cardStatusLabel = getCardStatusLabel(profileData)

  const handleProfileSubmit = async (
    event: FormEvent<HTMLFormElement>,
  ) => {
    event.preventDefault()
    setProfileError('')
    setProfileMessage('')

    const phone = profile.phone.trim()
    const address = profile.address.trim()
    const email = profile.email.trim().toLowerCase()

    if (!PHONE_PATTERN.test(phone)) {
      setProfileError(
        'Số điện thoại phải gồm 10 chữ số và bắt đầu bằng 0.',
      )
      return
    }

    if (address.length < 5 || address.length > 500) {
      setProfileError(
        'Địa chỉ phải có từ 5 đến 500 ký tự.',
      )
      return
    }

    if (!EMAIL_PATTERN.test(email)) {
      setProfileError('Email không đúng định dạng.')
      return
    }

    if (emailChanged && !profilePassword.trim()) {
      setProfileError(
        'Bạn phải nhập mật khẩu hiện tại để đổi email.',
      )
      return
    }

    setSavingProfile(true)

    try {
      const updated = await profileService.updateContact({
        phone,
        address,
        email,
        currentPassword: emailChanged
          ? profilePassword
          : undefined,
      })

      setProfileData(updated)
      setProfile({
        phone: updated.phone ?? '',
        address: updated.address ?? '',
        email: updated.email,
      })
      setSavedEmail(updated.email)
      setProfilePassword('')
      updateCurrentUser({ email: updated.email })
      setProfileMessage(
        emailChanged
          ? 'Cập nhật thông tin liên hệ và email thành công.'
          : 'Cập nhật số điện thoại và địa chỉ thành công.',
      )
    } catch (error) {
      setProfileError(
        getApiErrorMessage(
          error,
          'Không thể cập nhật thông tin liên hệ.',
        ),
      )
    } finally {
      setSavingProfile(false)
    }
  }

  const handlePasswordSubmit = async (
    event: FormEvent<HTMLFormElement>,
  ) => {
    event.preventDefault()
    setPasswordError('')
    setPasswordMessage('')

    if (
      !passwordForm.currentPassword ||
      !passwordForm.newPassword ||
      !passwordForm.confirmPassword
    ) {
      setPasswordError('Vui lòng nhập đầy đủ thông tin.')
      return
    }

    if (
      passwordForm.newPassword.length < 8 ||
      passwordForm.newPassword.length > 72
    ) {
      setPasswordError(
        'Mật khẩu mới phải có từ 8 đến 72 ký tự.',
      )
      return
    }

    if (
      !/[A-Za-zÀ-ỹ]/.test(passwordForm.newPassword) ||
      !/\d/.test(passwordForm.newPassword)
    ) {
      setPasswordError(
        'Mật khẩu mới phải chứa cả chữ và số.',
      )
      return
    }

    if (
      passwordForm.newPassword !==
      passwordForm.confirmPassword
    ) {
      setPasswordError('Xác nhận mật khẩu mới không khớp.')
      return
    }

    setSavingPassword(true)

    try {
      const response = await profileService.changePassword(
        passwordForm,
      )

      setPasswordForm({
        currentPassword: '',
        newPassword: '',
        confirmPassword: '',
      })
      setPasswordMessage(response.message)
    } catch (error) {
      setPasswordError(
        getApiErrorMessage(
          error,
          'Không thể đổi mật khẩu.',
        ),
      )
    } finally {
      setSavingPassword(false)
    }
  }

  if (loading) {
    return <LoadingState />
  }

  if (loadError || !profileData) {
    return (
      <div className="space-y-6">
        <PageHeader
          title="Hồ sơ cá nhân"
          description="Quản lý thông tin liên hệ, thông tin thẻ thư viện và mật khẩu tài khoản."
        />

        <Card>
          <div className="flex items-start gap-3 p-5 text-sm text-red-700">
            <AlertCircle className="mt-0.5 shrink-0" size={19} />
            <div>
              <p className="font-medium">Không tải được hồ sơ</p>
              <p className="mt-1 text-red-600">
                {loadError || 'Không tìm thấy dữ liệu hồ sơ.'}
              </p>
            </div>
          </div>
        </Card>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Hồ sơ cá nhân"
        description="Xem thẻ thư viện, cập nhật thông tin liên hệ và đổi mật khẩu tài khoản."
      />

      <Card>
        <div className="border-b border-slate-200 p-5">
          <div className="flex items-center gap-3">
            <IconBox>
              <CreditCard size={22} />
            </IconBox>

            <div>
              <h2 className="text-lg font-semibold text-slate-900">
                Thông tin thẻ thư viện
              </h2>
              <p className="mt-1 text-sm text-slate-500">
                Các thông tin thẻ chỉ đọc và do thư viện quản lý.
              </p>
            </div>
          </div>
        </div>

        <div className="grid gap-5 p-5 md:grid-cols-2 xl:grid-cols-4">
          <InfoItem
            label="Mã thẻ"
            value={profileData.cardNumber ?? 'Chưa được cấp'}
          />
          <InfoItem
            label="Loại thẻ"
            value={profileData.cardTypeName ?? '-'}
          />
          <InfoItem
            label="Ngày hết hạn"
            value={formatDate(profileData.expiresAt)}
          />

          <div>
            <p className="text-sm text-slate-500">Trạng thái thẻ</p>
            <span
              className={`mt-2 inline-flex rounded-full px-3 py-1 text-sm font-medium ${getStatusClass(
                profileData,
              )}`}
            >
              {cardStatusLabel}
            </span>
          </div>
        </div>

        {profileData.registrationStatus === 'REJECTED' &&
          profileData.rejectionReason && (
            <div className="mx-5 mb-5 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
              <strong>Lý do từ chối:</strong>{' '}
              {profileData.rejectionReason}
            </div>
          )}
      </Card>

      <div className="grid gap-6 xl:grid-cols-2">
        <Card>
          <SectionHeader
            icon={<User size={20} />}
            title="Thông tin cá nhân"
            description="Chỉ số điện thoại, địa chỉ và email được phép chỉnh sửa."
          />

          <form
            onSubmit={handleProfileSubmit}
            className="space-y-5 p-5"
          >
            <ReadOnlyField
              label="Họ và tên"
              value={profileData.fullName}
            />

            <ReadOnlyField
              label="Ngày sinh"
              value={formatDate(profileData.dateOfBirth)}
            />

            <ReadOnlyField
              label="Mã thẻ"
              value={profileData.cardNumber ?? 'Chưa được cấp'}
            />

            <EditableField
              label="Số điện thoại"
              icon={<Phone size={18} />}
            >
              <input
                value={profile.phone}
                onChange={(event) => {
                  setProfile({
                    ...profile,
                    phone: event.target.value,
                  })
                  clearProfileFeedback()
                }}
                maxLength={10}
                inputMode="numeric"
                placeholder="Ví dụ: 0912345678"
                className={inputClassName('pl-10')}
              />
            </EditableField>

            <EditableField
              label="Địa chỉ liên hệ"
              icon={<MapPin size={18} />}
              multiline
            >
              <textarea
                rows={3}
                value={profile.address}
                onChange={(event) => {
                  setProfile({
                    ...profile,
                    address: event.target.value,
                  })
                  clearProfileFeedback()
                }}
                maxLength={500}
                placeholder="Nhập địa chỉ đang sử dụng"
                className={inputClassName('resize-none pl-10')}
              />
            </EditableField>

            <EditableField
              label="Email nhận thông báo"
              icon={<Mail size={18} />}
            >
              <input
                type="email"
                value={profile.email}
                onChange={(event) => {
                  setProfile({
                    ...profile,
                    email: event.target.value,
                  })
                  clearProfileFeedback()
                }}
                className={inputClassName('pl-10')}
              />
            </EditableField>

            {emailChanged && (
              <div className="rounded-lg border border-amber-200 bg-amber-50 p-4">
                <label className="block text-sm font-medium text-amber-900">
                  Xác nhận mật khẩu hiện tại
                </label>
                <p className="mt-1 text-xs text-amber-700">
                  Email đang thay đổi. S1-06 yêu cầu nhập đúng mật khẩu hiện tại trước khi lưu.
                </p>

                <div className="relative mt-3">
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
                    className={inputClassName('pl-10 pr-11')}
                  />
                  <PasswordToggle
                    shown={showProfilePassword}
                    onClick={() =>
                      setShowProfilePassword((value) => !value)
                    }
                  />
                </div>
              </div>
            )}

            <Feedback
              success={profileMessage}
              error={profileError}
              onDismissSuccess={() => setProfileMessage('')}
              onDismissError={() => setProfileError('')}
            />

            <div className="flex justify-end">
              <button
                type="submit"
                disabled={savingProfile}
                className="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-4 py-2.5 text-sm font-medium text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-60"
              >
                <Save size={17} />
                {savingProfile ? 'Đang lưu...' : 'Lưu thay đổi'}
              </button>
            </div>
          </form>
        </Card>

        <Card>
          <SectionHeader
            icon={<ShieldCheck size={20} />}
            title="Đổi mật khẩu"
            description="Mật khẩu mới phải khác 3 mật khẩu gần nhất của tài khoản."
          />

          <form
            onSubmit={handlePasswordSubmit}
            className="space-y-5 p-5"
          >
            <PasswordField
              label="Mật khẩu hiện tại"
              value={passwordForm.currentPassword}
              shown={showCurrentPassword}
              onToggle={() =>
                setShowCurrentPassword((value) => !value)
              }
              onChange={(value) => {
                setPasswordForm({
                  ...passwordForm,
                  currentPassword: value,
                })
                clearPasswordFeedback()
              }}
            />

            <PasswordField
              label="Mật khẩu mới"
              value={passwordForm.newPassword}
              shown={showNewPassword}
              onToggle={() =>
                setShowNewPassword((value) => !value)
              }
              onChange={(value) => {
                setPasswordForm({
                  ...passwordForm,
                  newPassword: value,
                })
                clearPasswordFeedback()
              }}
            />

            <PasswordField
              label="Nhập lại mật khẩu mới"
              value={passwordForm.confirmPassword}
              shown={showConfirmPassword}
              onToggle={() =>
                setShowConfirmPassword((value) => !value)
              }
              onChange={(value) => {
                setPasswordForm({
                  ...passwordForm,
                  confirmPassword: value,
                })
                clearPasswordFeedback()
              }}
            />

            <div className="rounded-lg border border-blue-100 bg-blue-50 p-4 text-sm text-blue-800">
              <div className="flex gap-2">
                <ShieldCheck className="mt-0.5 shrink-0" size={18} />
                <div>
                  <p className="font-medium">Quy tắc mật khẩu</p>
                  <p className="mt-1 text-xs leading-5 text-blue-700">
                    Từ 8 đến 72 ký tự, có ít nhất một chữ và một số; không được trùng mật khẩu hiện tại hoặc 2 mật khẩu liền trước.
                  </p>
                </div>
              </div>
            </div>

            <Feedback
              success={passwordMessage}
              error={passwordError}
              onDismissSuccess={() => setPasswordMessage('')}
              onDismissError={() => setPasswordError('')}
            />

            <div className="flex justify-end">
              <button
                type="submit"
                disabled={savingPassword}
                className="inline-flex items-center gap-2 rounded-lg bg-slate-900 px-4 py-2.5 text-sm font-medium text-white transition hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-60"
              >
                <KeyRound size={17} />
                {savingPassword ? 'Đang đổi...' : 'Đổi mật khẩu'}
              </button>
            </div>
          </form>
        </Card>
      </div>
    </div>
  )

  function clearProfileFeedback() {
    setProfileError('')
    setProfileMessage('')
  }

  function clearPasswordFeedback() {
    setPasswordError('')
    setPasswordMessage('')
  }
}

function formatDate(value?: string | null) {
  if (!value) return '-'
  return new Date(`${value}T00:00:00`).toLocaleDateString('vi-VN')
}

function getCardStatusLabel(profile: ReaderSelfProfile | null) {
  if (!profile) return '-'
  if (profile.registrationStatus === 'PENDING') return 'Chờ duyệt'
  if (profile.registrationStatus === 'REJECTED') return 'Hồ sơ bị từ chối'
  if (!profile.cardNumber) return 'Chưa được cấp'

  switch (profile.cardStatus) {
    case 'ACTIVE':
      return 'Đang hoạt động'
    case 'LOCKED':
      return 'Đã khóa'
    case 'EXPIRED':
      return 'Hết hạn'
    default:
      return profile.cardStatus ?? 'Không xác định'
  }
}

function getStatusClass(profile: ReaderSelfProfile) {
  if (profile.registrationStatus === 'REJECTED') {
    return 'bg-red-50 text-red-700'
  }
  if (profile.registrationStatus === 'PENDING') {
    return 'bg-amber-50 text-amber-700'
  }
  if (profile.cardStatus === 'ACTIVE') {
    return 'bg-emerald-50 text-emerald-700'
  }
  return 'bg-slate-100 text-slate-700'
}

function getApiErrorMessage(error: unknown, fallback: string) {
  if (axios.isAxiosError<ApiErrorBody>(error)) {
    return error.response?.data?.message ?? fallback
  }
  return fallback
}

function inputClassName(extra = '') {
  return [
    'w-full rounded-lg border border-slate-300 bg-white py-2.5 pr-3 text-sm text-slate-900 outline-none transition',
    'focus:border-blue-500 focus:ring-2 focus:ring-blue-100',
    extra,
  ].join(' ')
}

function IconBox({ children }: { children: ReactNode }) {
  return (
    <div className="flex h-11 w-11 items-center justify-center rounded-lg bg-blue-50 text-blue-600">
      {children}
    </div>
  )
}

function SectionHeader({
  icon,
  title,
  description,
}: {
  icon: ReactNode
  title: string
  description: string
}) {
  return (
    <div className="border-b border-slate-200 p-5">
      <div className="flex items-center gap-3">
        <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-slate-100 text-slate-600">
          {icon}
        </div>
        <div>
          <h2 className="text-lg font-semibold text-slate-900">{title}</h2>
          <p className="mt-1 text-sm text-slate-500">{description}</p>
        </div>
      </div>
    </div>
  )
}

function InfoItem({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <p className="text-sm text-slate-500">{label}</p>
      <p className="mt-2 break-words text-sm font-semibold text-slate-900">
        {value}
      </p>
    </div>
  )
}

function ReadOnlyField({
  label,
  value,
}: {
  label: string
  value: string
}) {
  return (
    <div>
      <label className="mb-1.5 block text-sm font-medium text-slate-700">
        {label}
      </label>
      <input
        value={value}
        readOnly
        disabled
        className="w-full cursor-not-allowed rounded-lg border border-slate-200 bg-slate-100 px-3 py-2.5 text-sm text-slate-500"
      />
    </div>
  )
}

function EditableField({
  label,
  icon,
  children,
  multiline = false,
}: {
  label: string
  icon: ReactNode
  children: ReactNode
  multiline?: boolean
}) {
  return (
    <div>
      <label className="mb-1.5 block text-sm font-medium text-slate-700">
        {label}
      </label>
      <div className="relative">
        <span
          className={`absolute left-3 text-slate-400 ${
            multiline ? 'top-3' : 'top-1/2 -translate-y-1/2'
          }`}
        >
          {icon}
        </span>
        {children}
      </div>
    </div>
  )
}

function PasswordField({
  label,
  value,
  shown,
  onToggle,
  onChange,
}: {
  label: string
  value: string
  shown: boolean
  onToggle: () => void
  onChange: (value: string) => void
}) {
  return (
    <div>
      <label className="mb-1.5 block text-sm font-medium text-slate-700">
        {label}
      </label>
      <div className="relative">
        <KeyRound
          size={18}
          className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400"
        />
        <input
          type={shown ? 'text' : 'password'}
          value={value}
          onChange={(event) => onChange(event.target.value)}
          className={inputClassName('pl-10 pr-11')}
        />
        <PasswordToggle shown={shown} onClick={onToggle} />
      </div>
    </div>
  )
}

function PasswordToggle({
  shown,
  onClick,
}: {
  shown: boolean
  onClick: () => void
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 transition hover:text-slate-700"
      aria-label={shown ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
    >
      {shown ? <EyeOff size={18} /> : <Eye size={18} />}
    </button>
  )
}

function Feedback({
  success,
  error,
  onDismissSuccess,
  onDismissError,
}: {
  success: string
  error: string
  onDismissSuccess: () => void
  onDismissError: () => void
}) {
  if (error) {
    return <FeedbackAlert message={error} tone="error" onDismiss={onDismissError} />
  }

  if (success) {
    return <FeedbackAlert message={success} tone="success" onDismiss={onDismissSuccess} />
  }

  return null

}
