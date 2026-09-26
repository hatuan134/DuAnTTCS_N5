import { useState } from 'react'
import type { FormEvent } from 'react'

import {
  CheckCircle2,
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
import PageHeader from '../../components/ui/PageHeader'

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

const MOCK_CURRENT_PASSWORD =
  'Admin123'

const RECENT_PASSWORDS = [
  'Library123',
  'Admin456',
  'Password789',
]

export default function ChangePasswordPage() {
  const [profile, setProfile] =
    useState<ProfileForm>({
      phone: '0912345678',
      address:
        'Phường Phan Đình Phùng, Thái Nguyên',
      email: 'bandoc@ictu.edu.vn',
    })

  const [savedProfile, setSavedProfile] =
    useState(profile)

  const [passwordForm, setPasswordForm] =
    useState<PasswordForm>({
      currentPassword: '',
      newPassword: '',
      confirmPassword: '',
    })

  const [profilePassword, setProfilePassword] =
    useState('')

  const [
    showProfilePassword,
    setShowProfilePassword,
  ] = useState(false)

  const [
    showCurrentPassword,
    setShowCurrentPassword,
  ] = useState(false)

  const [
    showNewPassword,
    setShowNewPassword,
  ] = useState(false)

  const [
    showConfirmPassword,
    setShowConfirmPassword,
  ] = useState(false)

  const [profileMessage, setProfileMessage] =
    useState('')

  const [profileError, setProfileError] =
    useState('')

  const [
    passwordMessage,
    setPasswordMessage,
  ] = useState('')

  const [passwordError, setPasswordError] =
    useState('')

  const emailChanged =
    profile.email.trim() !==
    savedProfile.email.trim()

  const handleProfileSubmit = (
    event: FormEvent<HTMLFormElement>,
  ) => {
    event.preventDefault()

    setProfileError('')
    setProfileMessage('')

    if (!profile.phone.trim()) {
      setProfileError(
        'Số điện thoại không được để trống.',
      )
      return
    }

    if (!profile.address.trim()) {
      setProfileError(
        'Địa chỉ không được để trống.',
      )
      return
    }

    if (!profile.email.trim()) {
      setProfileError(
        'Email không được để trống.',
      )
      return
    }

    const emailPattern =
      /^[^\s@]+@[^\s@]+\.[^\s@]+$/

    if (
      !emailPattern.test(
        profile.email.trim(),
      )
    ) {
      setProfileError(
        'Email không đúng định dạng.',
      )
      return
    }

    if (emailChanged) {
      if (!profilePassword) {
        setProfileError(
          'Bạn phải nhập mật khẩu hiện tại để đổi email.',
        )
        return
      }

      if (
        profilePassword !==
        MOCK_CURRENT_PASSWORD
      ) {
        setProfileError(
          'Mật khẩu hiện tại không chính xác.',
        )
        return
      }
    }

    setSavedProfile(profile)
    setProfilePassword('')

    setProfileMessage(
      'Cập nhật thông tin cá nhân thành công.',
    )
  }

  const handlePasswordSubmit = (
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
      setPasswordError(
        'Vui lòng nhập đầy đủ thông tin.',
      )
      return
    }

    if (
      passwordForm.currentPassword !==
      MOCK_CURRENT_PASSWORD
    ) {
      setPasswordError(
        'Mật khẩu hiện tại không chính xác.',
      )
      return
    }

    if (
      passwordForm.newPassword.length < 8
    ) {
      setPasswordError(
        'Mật khẩu mới phải có ít nhất 8 ký tự.',
      )
      return
    }

    const hasLetter =
      /[A-Za-z]/.test(
        passwordForm.newPassword,
      )

    const hasNumber =
      /\d/.test(
        passwordForm.newPassword,
      )

    if (!hasLetter || !hasNumber) {
      setPasswordError(
        'Mật khẩu mới phải có cả chữ và số.',
      )
      return
    }

    if (
      passwordForm.newPassword !==
      passwordForm.confirmPassword
    ) {
      setPasswordError(
        'Xác nhận mật khẩu không khớp.',
      )
      return
    }

    if (
      passwordForm.newPassword ===
      passwordForm.currentPassword
    ) {
      setPasswordError(
        'Mật khẩu mới phải khác mật khẩu hiện tại.',
      )
      return
    }

    if (
      RECENT_PASSWORDS.includes(
        passwordForm.newPassword,
      )
    ) {
      setPasswordError(
        'Mật khẩu mới không được trùng 3 mật khẩu gần nhất.',
      )
      return
    }

    setPasswordForm({
      currentPassword: '',
      newPassword: '',
      confirmPassword: '',
    })

    setPasswordMessage(
      'Đổi mật khẩu thành công.',
    )
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Hồ sơ cá nhân"
        description="Quản lý thông tin liên hệ, thông tin thẻ thư viện và mật khẩu tài khoản."
      />

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
                Thông tin này do thư viện quản lý
                và bạn đọc không thể tự thay đổi.
              </p>
            </div>
          </div>
        </div>

        <div className="grid gap-5 p-5 md:grid-cols-2 xl:grid-cols-4">
          <InfoItem
            label="Mã thẻ"
            value="TV20260001"
          />

          <InfoItem
            label="Loại thẻ"
            value="Thẻ sinh viên"
          />

          <InfoItem
            label="Ngày hết hạn"
            value="26/09/2027"
          />

          <div>
            <p className="text-sm text-slate-500">
              Trạng thái
            </p>

            <span className="mt-2 inline-flex rounded-full bg-emerald-50 px-3 py-1 text-sm font-medium text-emerald-700">
              Đang hoạt động
            </span>
          </div>
        </div>
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
                  Thông tin cá nhân
                </h2>

                <p className="mt-1 text-sm text-slate-500">
                  Cập nhật thông tin liên hệ của bạn.
                </p>
              </div>
            </div>
          </div>

          <form
            onSubmit={handleProfileSubmit}
            className="space-y-5 p-5"
          >
            <ReadOnlyField
              label="Họ và tên"
              value="Nguyễn Văn An"
            />

            <ReadOnlyField
              label="Ngày sinh"
              value="15/08/2005"
            />

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
                  value={profile.phone}
                  onChange={(event) => {
                    setProfile({
                      ...profile,
                      phone:
                        event.target.value,
                    })

                    setProfileError('')
                    setProfileMessage('')
                  }}
                  className="w-full rounded-lg border border-slate-300 py-2.5 pl-10 pr-3 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
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
                  rows={3}
                  value={profile.address}
                  onChange={(event) => {
                    setProfile({
                      ...profile,
                      address:
                        event.target.value,
                    })

                    setProfileError('')
                    setProfileMessage('')
                  }}
                  className="w-full resize-none rounded-lg border border-slate-300 py-2.5 pl-10 pr-3 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
                />
              </div>
            </div>

            <div>
              <label className="mb-1.5 block text-sm font-medium text-slate-700">
                Email
              </label>

              <div className="relative">
                <Mail
                  size={18}
                  className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400"
                />

                <input
                  type="email"
                  value={profile.email}
                  onChange={(event) => {
                    setProfile({
                      ...profile,
                      email:
                        event.target.value,
                    })

                    setProfileError('')
                    setProfileMessage('')
                  }}
                  className="w-full rounded-lg border border-slate-300 py-2.5 pl-10 pr-3 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
                />
              </div>
            </div>

            {emailChanged && (
              <div>
                <label className="mb-1.5 block text-sm font-medium text-slate-700">
                  Mật khẩu hiện tại
                </label>

                <p className="mb-2 text-xs text-slate-500">
                  Bạn đang thay đổi email nên cần
                  xác nhận mật khẩu.
                </p>

                <div className="relative">
                  <KeyRound
                    size={18}
                    className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400"
                  />

                  <input
                    type={
                      showProfilePassword
                        ? 'text'
                        : 'password'
                    }
                    value={profilePassword}
                    onChange={(event) => {
                      setProfilePassword(
                        event.target.value,
                      )

                      setProfileError('')
                    }}
                    className="w-full rounded-lg border border-slate-300 py-2.5 pl-10 pr-11 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
                  />

                  <button
                    type="button"
                    onClick={() =>
                      setShowProfilePassword(
                        !showProfilePassword,
                      )
                    }
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-700"
                  >
                    {showProfilePassword ? (
                      <EyeOff size={18} />
                    ) : (
                      <Eye size={18} />
                    )}
                  </button>
                </div>
              </div>
            )}

            {profileError && (
              <div className="rounded-lg border border-red-100 bg-red-50 px-4 py-3 text-sm text-red-700">
                {profileError}
              </div>
            )}

            {profileMessage && (
              <div className="flex items-center gap-2 rounded-lg border border-emerald-100 bg-emerald-50 px-4 py-3 text-sm text-emerald-700">
                <CheckCircle2 size={18} />
                {profileMessage}
              </div>
            )}

            <div className="flex justify-end border-t border-slate-100 pt-5">
              <button
                type="submit"
                className="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-4 py-2.5 text-sm font-medium text-white hover:bg-blue-700"
              >
                <Save size={17} />
                Lưu thay đổi
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
                  Đổi mật khẩu
                </h2>

                <p className="mt-1 text-sm text-slate-500">
                  Thay đổi mật khẩu đăng nhập của tài khoản.
                </p>
              </div>
            </div>
          </div>

          <form
            onSubmit={handlePasswordSubmit}
            className="space-y-5 p-5"
          >
            <PasswordField
              label="Mật khẩu hiện tại"
              value={
                passwordForm.currentPassword
              }
              show={showCurrentPassword}
              onToggle={() =>
                setShowCurrentPassword(
                  !showCurrentPassword,
                )
              }
              onChange={(value) => {
                setPasswordForm({
                  ...passwordForm,
                  currentPassword:
                    value,
                })

                setPasswordError('')
                setPasswordMessage('')
              }}
            />

            <PasswordField
              label="Mật khẩu mới"
              value={
                passwordForm.newPassword
              }
              show={showNewPassword}
              onToggle={() =>
                setShowNewPassword(
                  !showNewPassword,
                )
              }
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
              value={
                passwordForm.confirmPassword
              }
              show={showConfirmPassword}
              onToggle={() =>
                setShowConfirmPassword(
                  !showConfirmPassword,
                )
              }
              onChange={(value) => {
                setPasswordForm({
                  ...passwordForm,
                  confirmPassword:
                    value,
                })

                setPasswordError('')
                setPasswordMessage('')
              }}
            />

            <div className="rounded-xl bg-slate-50 p-4">
              <p className="text-sm font-medium text-slate-700">
                Yêu cầu mật khẩu
              </p>

              <ul className="mt-2 space-y-1 text-sm text-slate-500">
                <li>
                  • Ít nhất 8 ký tự.
                </li>

                <li>
                  • Có ít nhất một chữ và một số.
                </li>

                <li>
                  • Không trùng mật khẩu hiện tại.
                </li>

                <li>
                  • Không trùng 3 mật khẩu gần nhất.
                </li>
              </ul>
            </div>

            {passwordError && (
              <div className="rounded-lg border border-red-100 bg-red-50 px-4 py-3 text-sm text-red-700">
                {passwordError}
              </div>
            )}

            {passwordMessage && (
              <div className="flex items-center gap-2 rounded-lg border border-emerald-100 bg-emerald-50 px-4 py-3 text-sm text-emerald-700">
                <CheckCircle2 size={18} />
                {passwordMessage}
              </div>
            )}

            <div className="flex justify-end border-t border-slate-100 pt-5">
              <button
                type="submit"
                className="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-4 py-2.5 text-sm font-medium text-white hover:bg-blue-700"
              >
                <KeyRound size={17} />
                Đổi mật khẩu
              </button>
            </div>
          </form>
        </Card>
      </div>

      <div className="rounded-xl border border-blue-100 bg-blue-50 p-4">
        <p className="text-sm font-semibold text-blue-900">
          Dữ liệu thử nghiệm
        </p>

        <p className="mt-1 text-sm leading-6 text-blue-700">
          Mật khẩu hiện tại dùng để kiểm thử frontend:
          <strong> Admin123</strong>.
          Dữ liệu hiện mới lưu trong giao diện và chưa kết nối backend.
        </p>
      </div>
    </div>
  )
}

type InfoItemProps = {
  label: string
  value: string
}

function InfoItem({
  label,
  value,
}: InfoItemProps) {
  return (
    <div>
      <p className="text-sm text-slate-500">
        {label}
      </p>

      <p className="mt-2 font-medium text-slate-900">
        {value}
      </p>
    </div>
  )
}

type ReadOnlyFieldProps = {
  label: string
  value: string
}

function ReadOnlyField({
  label,
  value,
}: ReadOnlyFieldProps) {
  return (
    <div>
      <label className="mb-1.5 block text-sm font-medium text-slate-700">
        {label}
      </label>

      <input
        value={value}
        readOnly
        className="w-full cursor-not-allowed rounded-lg border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm text-slate-500"
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
          type={show ? 'text' : 'password'}
          value={value}
          onChange={(event) =>
            onChange(event.target.value)
          }
          className="w-full rounded-lg border border-slate-300 py-2.5 pl-10 pr-11 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
        />

        <button
          type="button"
          onClick={onToggle}
          className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-700"
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