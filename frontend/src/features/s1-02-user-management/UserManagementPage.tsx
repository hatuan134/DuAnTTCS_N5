import {
  useCallback,
  useEffect,
  useMemo,
  useState,
} from 'react'
import type { FormEvent } from 'react'

import {
  CheckCircle2,
  LockKeyhole,
  Plus,
  RefreshCw,
  Search,
  UnlockKeyhole,
  UserRoundCog,
  X,
} from 'lucide-react'

import Button from '../../components/ui/Button'
import Card from '../../components/ui/Card'
import EmptyState from '../../components/ui/EmptyState'
import Input from '../../components/ui/Input'
import LoadingState from '../../components/ui/LoadingState'
import PageHeader from '../../components/ui/PageHeader'
import StatusBadge from '../../components/ui/StatusBadge'
import { getCurrentUser } from '../../core/auth/authStorage'

import {
  createAccount,
  getAccounts,
  getApiErrorMessage,
  updateAccountStatus,
} from './accountService'

import type {
  Account,
  AccountRole,
  AccountStatus,
  CreateAccountPayload,
} from './accountService'

const roleOptions: Array<{
  value: AccountRole
  label: string
}> = [
  {
    value: 'ADMIN',
    label: 'Quản trị hệ thống',
  },
  {
    value: 'LIBRARY_MANAGER',
    label: 'Quản lý thư viện',
  },
  {
    value: 'LIBRARIAN',
    label: 'Thủ thư',
  },
]

const initialForm: CreateAccountPayload = {
  fullName: '',
  email: '',
  phone: '',
  role: 'LIBRARIAN',
  status: 'ACTIVE',
}

function statusLabel(status: string) {
  switch (status) {
    case 'ACTIVE':
      return 'Đang hoạt động'
    case 'LOCKED':
      return 'Đã khóa'
    case 'PENDING':
      return 'Chờ kích hoạt'
    case 'DISABLED':
      return 'Ngừng hoạt động'
    default:
      return status
  }
}

export default function UserManagementPage() {
  const currentUser = getCurrentUser()

  const [accounts, setAccounts] = useState<Account[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')

  const [search, setSearch] = useState('')
  const [roleFilter, setRoleFilter] = useState('')
  const [statusFilter, setStatusFilter] = useState('')

  const [showCreate, setShowCreate] = useState(false)
  const [form, setForm] = useState<CreateAccountPayload>(initialForm)
  const [formError, setFormError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [changingId, setChangingId] = useState<number | null>(null)

  const loadAccounts = useCallback(async () => {
    setLoading(true)
    setError('')

    try {
      const data = await getAccounts({
        search: search.trim(),
        role: roleFilter,
        status: statusFilter,
      })
      setAccounts(data)
    } catch (requestError) {
      setError(
        getApiErrorMessage(
          requestError,
          'Không thể tải danh sách tài khoản. Vui lòng thử lại.',
        ),
      )
    } finally {
      setLoading(false)
    }
  }, [roleFilter, search, statusFilter])

  useEffect(() => {
    void loadAccounts()
  }, [loadAccounts])

  const staffCount = useMemo(
    () =>
      accounts.filter((account) =>
        ['ADMIN', 'LIBRARY_MANAGER', 'LIBRARIAN'].includes(account.role),
      ).length,
    [accounts],
  )

  const activeCount = useMemo(
    () => accounts.filter((account) => account.status === 'ACTIVE').length,
    [accounts],
  )

  const lockedCount = useMemo(
    () => accounts.filter((account) => account.status === 'LOCKED').length,
    [accounts],
  )

  const closeCreate = () => {
    if (submitting) {
      return
    }
    setShowCreate(false)
    setForm(initialForm)
    setFormError('')
  }

  const validateForm = () => {
    const fullName = form.fullName.trim()
    const email = form.email.trim().toLowerCase()
    const phone = form.phone.trim()

    if (!fullName) {
      return 'Vui lòng nhập họ và tên.'
    }

    if (fullName.length > 150) {
      return 'Họ và tên tối đa 150 ký tự.'
    }

    if (!email) {
      return 'Vui lòng nhập email.'
    }

    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      return 'Email không đúng định dạng.'
    }

    if (phone.length > 20) {
      return 'Số điện thoại tối đa 20 ký tự.'
    }

    if (
      phone &&
      !/^[0-9+().\s-]{8,20}$/.test(phone)
    ) {
      return 'Số điện thoại không đúng định dạng.'
    }

    return ''
  }

  const handleCreate = async (
    event: FormEvent<HTMLFormElement>,
  ) => {
    event.preventDefault()
    setFormError('')
    setSuccess('')

    const validationError = validateForm()
    if (validationError) {
      setFormError(validationError)
      return
    }

    setSubmitting(true)

    try {
      const created = await createAccount({
        ...form,
        fullName: form.fullName.trim(),
        email: form.email.trim().toLowerCase(),
        phone: form.phone.trim(),
      })

      setAccounts((current) => [created, ...current])
      setSuccess(
        `Đã tạo tài khoản ${created.email}. Hệ thống đã tạo liên kết đặt mật khẩu lần đầu có hiệu lực 24 giờ.`,
      )
      closeCreate()
    } catch (requestError) {
      setFormError(
        getApiErrorMessage(
          requestError,
          'Không thể tạo tài khoản. Vui lòng thử lại.',
        ),
      )
    } finally {
      setSubmitting(false)
    }
  }

  const handleStatusChange = async (
    account: Account,
  ) => {
    const targetStatus: AccountStatus =
      account.status === 'ACTIVE'
        ? 'LOCKED'
        : 'ACTIVE'

    const action =
      targetStatus === 'LOCKED'
        ? 'khóa'
        : 'mở khóa'

    const confirmed = window.confirm(
      `Bạn có chắc muốn ${action} tài khoản ${account.email}?`,
    )

    if (!confirmed) {
      return
    }

    setChangingId(account.id)
    setError('')
    setSuccess('')

    try {
      const updated = await updateAccountStatus(
        account.id,
        targetStatus,
      )

      setAccounts((current) =>
        current.map((item) =>
          item.id === updated.id
            ? updated
            : item,
        ),
      )

      setSuccess(
        targetStatus === 'LOCKED'
          ? `Đã khóa ${updated.email}. Các phiên đăng nhập hiện tại và refresh token của tài khoản đã bị vô hiệu hóa.`
          : `Đã mở khóa ${updated.email}.`,
      )
    } catch (requestError) {
      setError(
        getApiErrorMessage(
          requestError,
          `Không thể ${action} tài khoản. Vui lòng thử lại.`,
        ),
      )
    } finally {
      setChangingId(null)
    }
  }

  return (
    <div>
      <PageHeader
        title="Quản lý tài khoản"
        description="Tạo tài khoản nhân viên, gán một vai trò và quản lý trạng thái truy cập."
        action={(
          <Button
            type="button"
            onClick={() => {
              setForm(initialForm)
              setFormError('')
              setShowCreate(true)
            }}
          >
            <Plus size={17} />
            Tạo tài khoản
          </Button>
        )}
      />

      {success && (
        <div className="mb-5 flex items-start gap-3 rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800">
          <CheckCircle2 className="mt-0.5 shrink-0" size={18} />
          <span>{success}</span>
        </div>
      )}

      {error && (
        <div className="mb-5 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
          {error}
        </div>
      )}

      <div className="mb-5 grid gap-4 sm:grid-cols-3">
        <Card className="p-4">
          <div className="text-sm text-slate-500">Tài khoản hiển thị</div>
          <div className="mt-1 text-2xl font-semibold text-slate-900">{staffCount}</div>
        </Card>
        <Card className="p-4">
          <div className="text-sm text-slate-500">Đang hoạt động</div>
          <div className="mt-1 text-2xl font-semibold text-emerald-700">{activeCount}</div>
        </Card>
        <Card className="p-4">
          <div className="text-sm text-slate-500">Đã khóa</div>
          <div className="mt-1 text-2xl font-semibold text-red-700">{lockedCount}</div>
        </Card>
      </div>

      <Card>
        <div className="grid gap-3 border-b border-slate-200 p-4 lg:grid-cols-[minmax(260px,1fr)_220px_190px_auto]">
          <div className="relative">
            <Search
              size={17}
              className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400"
            />
            <input
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Tìm theo tên, email hoặc số điện thoại"
              className="h-10 w-full rounded-lg border border-slate-300 bg-white pl-10 pr-3 text-sm outline-none focus:border-blue-500 focus:ring-4 focus:ring-blue-100"
            />
          </div>

          <select
            value={roleFilter}
            onChange={(event) => setRoleFilter(event.target.value)}
            className="h-10 rounded-lg border border-slate-300 bg-white px-3 text-sm text-slate-700 outline-none focus:border-blue-500 focus:ring-4 focus:ring-blue-100"
          >
            <option value="">Tất cả vai trò</option>
            {roleOptions.map((role) => (
              <option key={role.value} value={role.value}>
                {role.label}
              </option>
            ))}
          </select>

          <select
            value={statusFilter}
            onChange={(event) => setStatusFilter(event.target.value)}
            className="h-10 rounded-lg border border-slate-300 bg-white px-3 text-sm text-slate-700 outline-none focus:border-blue-500 focus:ring-4 focus:ring-blue-100"
          >
            <option value="">Tất cả trạng thái</option>
            <option value="ACTIVE">Đang hoạt động</option>
            <option value="LOCKED">Đã khóa</option>
          </select>

          <Button
            type="button"
            variant="secondary"
            size="sm"
            onClick={() => void loadAccounts()}
          >
            <RefreshCw size={16} />
            Làm mới
          </Button>
        </div>

        {loading ? (
          <div className="p-8">
            <LoadingState />
          </div>
        ) : accounts.length === 0 ? (
          <div className="p-8">
            <EmptyState
              title="Chưa có tài khoản phù hợp"
              description="Thử thay đổi bộ lọc hoặc tạo một tài khoản nhân viên mới."
            />
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-slate-200 text-sm">
              <thead className="bg-slate-50 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
                <tr>
                  <th className="px-5 py-3">Họ tên</th>
                  <th className="px-5 py-3">Liên hệ</th>
                  <th className="px-5 py-3">Vai trò</th>
                  <th className="px-5 py-3">Trạng thái</th>
                  <th className="px-5 py-3 text-right">Thao tác</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 bg-white">
                {accounts.map((account) => {
                  const isSelf = currentUser?.id === account.id
                  const canToggle =
                    account.status === 'ACTIVE' ||
                    account.status === 'LOCKED'

                  return (
                    <tr key={account.id} className="hover:bg-slate-50/70">
                      <td className="px-5 py-4">
                        <div className="font-medium text-slate-900">
                          {account.fullName}
                        </div>
                        <div className="mt-1 text-xs text-slate-400">
                          ID #{account.id}
                        </div>
                      </td>
                      <td className="px-5 py-4">
                        <div className="text-slate-700">{account.email}</div>
                        <div className="mt-1 text-xs text-slate-500">
                          {account.phone || 'Chưa có số điện thoại'}
                        </div>
                      </td>
                      <td className="px-5 py-4 text-slate-700">
                        {account.roleName}
                      </td>
                      <td className="px-5 py-4">
                        <div className="flex flex-col items-start gap-1">
                          <StatusBadge status={account.status} />
                          <span className="text-xs text-slate-500">
                            {statusLabel(account.status)}
                          </span>
                        </div>
                      </td>
                      <td className="px-5 py-4 text-right">
                        {canToggle && (
                          <Button
                            type="button"
                            size="sm"
                            variant={
                              account.status === 'ACTIVE'
                                ? 'danger'
                                : 'secondary'
                            }
                            disabled={isSelf}
                            loading={changingId === account.id}
                            title={isSelf ? 'Không thể tự khóa tài khoản của chính mình' : undefined}
                            onClick={() => void handleStatusChange(account)}
                          >
                            {account.status === 'ACTIVE' ? (
                              <LockKeyhole size={15} />
                            ) : (
                              <UnlockKeyhole size={15} />
                            )}
                            {account.status === 'ACTIVE' ? 'Khóa' : 'Mở khóa'}
                          </Button>
                        )}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      {showCreate && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 px-4 py-8">
          <div className="w-full max-w-xl rounded-xl border border-slate-200 bg-white shadow-xl">
            <div className="flex items-start justify-between border-b border-slate-200 px-6 py-5">
              <div className="flex gap-3">
                <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-blue-50 text-blue-700">
                  <UserRoundCog size={20} />
                </div>
                <div>
                  <h3 className="text-lg font-semibold text-slate-900">
                    Tạo tài khoản nhân viên
                  </h3>
                  <p className="mt-1 text-sm text-slate-500">
                    Người dùng sẽ nhận liên kết đặt mật khẩu lần đầu có hiệu lực 24 giờ.
                  </p>
                </div>
              </div>

              <button
                type="button"
                onClick={closeCreate}
                className="rounded-lg p-2 text-slate-400 hover:bg-slate-100 hover:text-slate-700"
                aria-label="Đóng"
              >
                <X size={18} />
              </button>
            </div>

            <form onSubmit={handleCreate}>
              <div className="grid gap-4 px-6 py-5 sm:grid-cols-2">
                <div className="sm:col-span-2">
                  <Input
                    label="Họ và tên"
                    value={form.fullName}
                    onChange={(event) =>
                      setForm((current) => ({
                        ...current,
                        fullName: event.target.value,
                      }))
                    }
                    placeholder="Nguyễn Văn A"
                    maxLength={150}
                    autoFocus
                  />
                </div>

                <div className="sm:col-span-2">
                  <Input
                    label="Email"
                    type="email"
                    value={form.email}
                    onChange={(event) =>
                      setForm((current) => ({
                        ...current,
                        email: event.target.value,
                      }))
                    }
                    placeholder="nhanvien@libra.edu.vn"
                    maxLength={255}
                  />
                </div>

                <Input
                  label="Số điện thoại"
                  value={form.phone}
                  onChange={(event) =>
                    setForm((current) => ({
                      ...current,
                      phone: event.target.value,
                    }))
                  }
                  placeholder="0987654321"
                  maxLength={20}
                />

                <div>
                  <label className="mb-2 block text-sm font-medium text-slate-700" htmlFor="account-role">
                    Vai trò
                  </label>
                  <select
                    id="account-role"
                    value={form.role}
                    onChange={(event) =>
                      setForm((current) => ({
                        ...current,
                        role: event.target.value as AccountRole,
                      }))
                    }
                    className="h-11 w-full rounded-lg border border-slate-300 bg-white px-3 text-sm text-slate-900 outline-none focus:border-blue-500 focus:ring-4 focus:ring-blue-100"
                  >
                    {roleOptions.map((role) => (
                      <option key={role.value} value={role.value}>
                        {role.label}
                      </option>
                    ))}
                  </select>
                </div>

                <div className="sm:col-span-2">
                  <label className="mb-2 block text-sm font-medium text-slate-700" htmlFor="account-status">
                    Trạng thái hoạt động
                  </label>
                  <select
                    id="account-status"
                    value={form.status}
                    onChange={(event) =>
                      setForm((current) => ({
                        ...current,
                        status: event.target.value as AccountStatus,
                      }))
                    }
                    className="h-11 w-full rounded-lg border border-slate-300 bg-white px-3 text-sm text-slate-900 outline-none focus:border-blue-500 focus:ring-4 focus:ring-blue-100"
                  >
                    <option value="ACTIVE">Đang hoạt động</option>
                    <option value="LOCKED">Khóa ngay sau khi tạo</option>
                  </select>
                </div>

                {formError && (
                  <div className="sm:col-span-2 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
                    {formError}
                  </div>
                )}
              </div>

              <div className="flex justify-end gap-3 border-t border-slate-200 bg-slate-50 px-6 py-4">
                <Button
                  type="button"
                  variant="secondary"
                  onClick={closeCreate}
                  disabled={submitting}
                >
                  Hủy
                </Button>
                <Button type="submit" loading={submitting}>
                  <Plus size={16} />
                  Tạo tài khoản
                </Button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}
