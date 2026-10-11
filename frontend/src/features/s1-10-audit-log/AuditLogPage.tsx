import { auditActionLabel, auditEntityLabel, auditTargetLabel, auditDetailLabel } from './auditLabels'
import AccessibleModal from '../../components/ui/Modal'
import FeedbackAlert from '../../components/ui/FeedbackAlert'
import { useEffect, useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import type { AxiosError } from 'axios'
import {
  Activity,
  Eye,
  Filter,
  LogIn,
  RefreshCw,
  Search,
  ShieldCheck,
  UserCog,
  UserPlus,
  X,
} from 'lucide-react'

import Card from '../../components/ui/Card'
import PageHeader from '../../components/ui/PageHeader'
import TableActionButton from '../../components/ui/TableActionButton'
import TablePagination from '../../components/ui/TablePagination'
import useTablePagination from '../../hooks/useTablePagination'
import { getCurrentUser } from '../../core/auth/authStorage'
import {
  auditLogService,
} from './auditLogService'
import type {
  AuditActionGroup,
  AuditFilterOptions,
  AuditLogItem,
} from './auditLogService'

type ApiErrorPayload = {
  message?: string
  code?: string
}

type Filters = {
  keyword: string
  actorId: string
  action: string
  fromDate: string
  toDate: string
}

const emptyFilters: Filters = {
  keyword: '',
  actorId: 'ALL',
  action: 'ALL',
  fromDate: '',
  toDate: '',
}

function getErrorMessage(error: unknown, fallback: string) {
  const axiosError = error as AxiosError<ApiErrorPayload>
  return axiosError.response?.data?.message || fallback
}

function formatDateTime(value: string) {
  return new Date(value).toLocaleString('vi-VN', {
    timeZone: 'Asia/Ho_Chi_Minh',
  })
}

function getActionStyle(action: AuditActionGroup) {
  switch (action) {
    case 'LOGIN':
      return {
        className: 'bg-blue-50 text-blue-700',
        icon: LogIn,
      }
    case 'CREATE_ACCOUNT':
      return {
        className: 'bg-emerald-50 text-emerald-700',
        icon: UserPlus,
      }
    case 'UPDATE_ACCOUNT':
      return {
        className: 'bg-amber-50 text-amber-700',
        icon: UserCog,
      }
    case 'ISSUE_CARD':
      return {
        className: 'bg-violet-50 text-violet-700',
        icon: ShieldCheck,
      }
    case 'UPDATE_POLICY':
      return {
        className: 'bg-cyan-50 text-cyan-700',
        icon: Activity,
      }
    default:
      return {
        className: 'bg-slate-100 text-slate-700',
        icon: Activity,
      }
  }
}

export default function AuditLogPage() {
  const currentUser = getCurrentUser()
  const allowed = ['LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN'].includes(currentUser?.role ?? '')

  if (!allowed) {
    return <AccessDenied />
  }

  return <AuditLogContent />
}

function AuditLogContent() {
  const [filters, setFilters] = useState<Filters>(emptyFilters)
  const [logs, setLogs] = useState<AuditLogItem[]>([])
  const [options, setOptions] = useState<AuditFilterOptions>({ actors: [], actions: [] })
  const [selectedLog, setSelectedLog] = useState<AuditLogItem | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [detailError, setDetailError] = useState('')

  const loadOptions = async () => {
    try {
      const data = await auditLogService.getFilterOptions()
      setOptions(data)
    } catch (err) {
      setError(getErrorMessage(err, 'Không thể tải danh sách bộ lọc nhật ký.'))
    }
  }

  const loadLogs = async (nextFilters: Filters = filters) => {
    if (nextFilters.fromDate && nextFilters.toDate && nextFilters.fromDate > nextFilters.toDate) {
      setError('Ngày bắt đầu không được sau ngày kết thúc.')
      return
    }

    setLoading(true)
    setError('')
    try {
      const data = await auditLogService.search({
        keyword: nextFilters.keyword,
        actorId: nextFilters.actorId === 'ALL' ? undefined : Number(nextFilters.actorId),
        action: nextFilters.action,
        fromDate: nextFilters.fromDate || undefined,
        toDate: nextFilters.toDate || undefined,
      })
      setLogs(data)
    } catch (err) {
      setError(getErrorMessage(err, 'Không thể tải nhật ký hoạt động.'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void Promise.all([loadOptions(), loadLogs(emptyFilters)])
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const counts = useMemo(() => ({
    total: logs.length,
    login: logs.filter((item) => item.actionGroup === 'LOGIN').length,
    account: logs.filter((item) =>
      item.actionGroup === 'CREATE_ACCOUNT' || item.actionGroup === 'UPDATE_ACCOUNT').length,
    library: logs.filter((item) =>
      item.actionGroup === 'ISSUE_CARD' || item.actionGroup === 'UPDATE_POLICY').length,
  }), [logs])

  const submitFilters = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    void loadLogs(filters)
  }

  const resetFilters = () => {
    setFilters(emptyFilters)
    void loadLogs(emptyFilters)
  }

  const openDetail = async (item: AuditLogItem) => {
    setSelectedLog(item)
    setDetailError('')
    try {
      const detail = await auditLogService.getById(item.id)
      setSelectedLog(detail)
    } catch {
      setDetailError('Không tải được chi tiết mới nhất. Đang hiển thị dữ liệu đã tải trong danh sách.')
    }
  }

  const logPagination = useTablePagination(logs, logs.map((item) => item.id).join(','))

  return (
    <div className="space-y-6">
      <PageHeader
        title="Nhật ký hoạt động"
        description="Tra cứu lịch sử đăng nhập và các thao tác quản trị quan trọng trong hệ thống."
      />

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard label="Kết quả đang hiển thị" value={counts.total} valueClass="text-slate-900" />
        <StatCard label="Đăng nhập" value={counts.login} valueClass="text-blue-700" />
        <StatCard label="Tài khoản" value={counts.account} valueClass="text-amber-700" />
        <StatCard label="Nghiệp vụ thư viện" value={counts.library} valueClass="text-violet-700" />
      </div>

      {error && (
        <FeedbackAlert message={error} tone="error" onDismiss={() => setError('')} />
      )}

      <Card>
        <div className="border-b border-slate-200 p-5">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-slate-100 text-slate-600">
              <Filter size={19} />
            </div>
            <div>
              <h2 className="text-lg font-semibold text-slate-900">Bộ lọc nhật ký</h2>
              <p className="mt-1 text-sm text-slate-500">
                Có thể kết hợp khoảng ngày, người thực hiện, loại hành động và từ khóa.
              </p>
            </div>
          </div>
        </div>

        <form onSubmit={submitFilters} className="p-5">
          <div className="grid items-end gap-4 sm:grid-cols-2 xl:grid-cols-5">
            <label className="flex min-w-0 flex-col gap-1">
              <span className="text-xs font-medium text-slate-500">Từ khóa</span>
              <div className="relative">
                <Search
                  size={17}
                  className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400"
                />
                <input
                  value={filters.keyword}
                  onChange={(event) => setFilters((current) => ({
                    ...current,
                    keyword: event.target.value,
                  }))}
                  placeholder="Tên, email, đối tượng, IP..."
                  className="h-11 min-w-0 w-full rounded-lg border border-slate-300 bg-white pl-9 pr-3 text-sm outline-none focus:border-blue-500"
                />
              </div>
            </label>

            <label className="flex min-w-0 flex-col gap-1">
              <span className="text-xs font-medium text-slate-500">Người thực hiện</span>
              <select
                value={filters.actorId}
                onChange={(event) => setFilters((current) => ({
                  ...current,
                  actorId: event.target.value,
                }))}
                className="h-11 min-w-0 w-full rounded-lg border border-slate-300 bg-white px-3 text-sm outline-none focus:border-blue-500"
              >
                <option value="ALL">Tất cả người thực hiện</option>
                {options.actors.map((actor) => (
                  <option key={actor.id} value={actor.id}>
                    {actor.fullName} — {actor.email}
                  </option>
                ))}
              </select>
            </label>

            <label className="flex min-w-0 flex-col gap-1">
              <span className="text-xs font-medium text-slate-500">Loại hành động</span>
              <select
                value={filters.action}
                onChange={(event) => setFilters((current) => ({
                  ...current,
                  action: event.target.value,
                }))}
                className="h-11 min-w-0 w-full rounded-lg border border-slate-300 bg-white px-3 text-sm outline-none focus:border-blue-500"
              >
                <option value="ALL">Tất cả hành động</option>
                {options.actions.map((action) => (
                  <option key={action.value} value={action.value}>
                    {auditActionLabel(action.value, action.label)}
                  </option>
                ))}
              </select>
            </label>

            <label className="flex min-w-0 flex-col gap-1">
              <span className="text-xs font-medium text-slate-500">Từ ngày</span>
              <input
                type="date"
                value={filters.fromDate}
                onChange={(event) => setFilters((current) => ({
                  ...current,
                  fromDate: event.target.value,
                }))}
                className="h-11 min-w-0 w-full rounded-lg border border-slate-300 bg-white px-3 text-sm outline-none focus:border-blue-500"
              />
            </label>

            <label className="flex min-w-0 flex-col gap-1">
              <span className="text-xs font-medium text-slate-500">Đến ngày</span>
              <input
                type="date"
                value={filters.toDate}
                onChange={(event) => setFilters((current) => ({
                  ...current,
                  toDate: event.target.value,
                }))}
                className="h-11 min-w-0 w-full rounded-lg border border-slate-300 bg-white px-3 text-sm outline-none focus:border-blue-500"
              />
            </label>
          </div>

          <div className="mt-4 flex flex-wrap items-center justify-between gap-3 border-t border-slate-100 pt-4">
            <p className="text-sm text-slate-500">
              Tìm thấy <strong className="text-slate-800">{logs.length}</strong> nhật ký.
            </p>

            <div className="flex gap-2">
              <button
                type="button"
                onClick={resetFilters}
                className="inline-flex items-center gap-2 rounded-lg border border-slate-300 px-4 py-2.5 text-sm font-medium text-slate-700 hover:bg-slate-50"
              >
                <RefreshCw size={16} />
                Xóa bộ lọc
              </button>
              <button
                type="submit"
                className="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-4 py-2.5 text-sm font-medium text-white hover:bg-blue-700"
              >
                <Search size={16} />
                Tra cứu
              </button>
            </div>
          </div>
        </form>
      </Card>

      <Card>
        <div className="flex items-center justify-between border-b border-slate-200 px-5 py-4">
          <div>
            <h2 className="font-semibold text-slate-900">Danh sách nhật ký</h2>
            <p className="mt-1 text-xs text-slate-500">
              Nhật ký chỉ đọc. Giao diện không cung cấp chức năng sửa hoặc xóa.
            </p>
          </div>
        </div>

        {loading ? (
          <div className="flex min-h-64 items-center justify-center gap-3 text-sm text-slate-500">
            <span className="h-5 w-5 animate-spin rounded-full border-2 border-slate-300 border-t-blue-600" />
            Đang tải nhật ký...
          </div>
        ) : logs.length === 0 ? (
          <div className="flex min-h-64 flex-col items-center justify-center px-6 text-center">
            <Search size={34} className="text-slate-300" />
            <p className="mt-3 font-medium text-slate-700">Không tìm thấy nhật ký</p>
            <p className="mt-1 text-sm text-slate-500">Hãy thay đổi hoặc xóa các điều kiện lọc.</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="data-table w-full min-w-[1240px]">
              <thead>
                <tr className="border-b border-slate-200 bg-slate-50">
                  <TableHead>STT</TableHead>
                  <TableHead>Thời điểm</TableHead>
                  <TableHead>Người thực hiện</TableHead>
                  <TableHead>Hành động</TableHead>
                  <TableHead>Đối tượng tác động</TableHead>
                  <TableHead>Địa chỉ IP</TableHead>
                  <th className="px-5 py-3 text-right text-xs font-semibold uppercase text-slate-500">Chi tiết</th>
                </tr>
              </thead>
              <tbody>
                {logPagination.pageItems.map((item, index) => {
                  const style = getActionStyle(item.actionGroup)
                  const Icon = style.icon
                  return (
                    <tr key={item.id} className="border-b border-slate-100 transition hover:bg-slate-50">
                      <td className="px-5 py-4 font-semibold text-slate-500">
                        {logPagination.startIndex + index + 1}
                      </td>
                      <td className="whitespace-nowrap px-5 py-4 text-sm text-slate-600">
                        {formatDateTime(item.timestamp)}
                      </td>
                      <td className="px-5 py-4">
                        <p className="font-medium text-slate-900">{item.actor}</p>
                        <p className="mt-0.5 text-xs text-slate-500">{item.actorRole}</p>
                      </td>
                      <td className="px-5 py-4">
                        <span className={`inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-medium ${style.className}`}>
                          <Icon size={14} />
                          {auditActionLabel(item.action, item.actionLabel)}
                        </span>
                      </td>
                      <td className="px-5 py-4">
                        <p className="font-medium text-slate-800">{auditTargetLabel(item)}</p>
                        <p className="mt-0.5 text-xs text-slate-500">{auditEntityLabel(item.targetType)}</p>
                      </td>
                      <td className="min-w-[180px] px-5 py-4">
                        <IpAddress value={item.ipAddress} />
                      </td>
                      <td className="px-5 py-4">
                        <TableActionButton
                          icon={<Eye size={16} />}
                          tone="primary"
                          title="Xem chi tiết"
                          onClick={() => void openDetail(item)}
                        >
                          Xem chi tiết
                        </TableActionButton>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
            <TablePagination
              page={logPagination.page}
              totalItems={logPagination.totalItems}
              totalPages={logPagination.totalPages}
              pageSize={logPagination.pageSize}
              onPageChange={logPagination.goToPage}
            />
          </div>
        )}
      </Card>

      <div className="rounded-xl border border-blue-100 bg-blue-50 p-4">
        <div className="flex gap-3">
          <ShieldCheck size={20} className="mt-0.5 shrink-0 text-blue-600" />
          <div>
            <p className="text-sm font-semibold text-blue-900">Nhật ký chỉ đọc</p>
            <p className="mt-1 text-sm leading-6 text-blue-700">
              Chỉ Quản trị hệ thống được phép tra cứu. Backend từ chối mọi yêu cầu sửa hoặc xóa nhật ký.
            </p>
          </div>
        </div>
      </div>

      {selectedLog && (
        <AccessibleModal onClose={() => setSelectedLog(null)} label="Chi tiết nhật ký">
          <div className="w-full max-w-2xl rounded-2xl bg-white shadow-2xl">
            <div className="flex items-start justify-between border-b border-slate-200 px-6 py-5">
              <div>
                <h2 className="text-xl font-semibold text-slate-900">Chi tiết nhật ký</h2>
                <p className="mt-1 text-sm text-slate-500">Mã nhật ký #{selectedLog.id}</p>
              </div>
              <button aria-label="Đóng hộp thoại"
                type="button"
                onClick={() => setSelectedLog(null)}
                className="flex h-9 w-9 items-center justify-center rounded-lg text-slate-400 transition hover:bg-slate-100 hover:text-slate-700"
              >
                <X size={20} />
              </button>
            </div>

            <div className="space-y-5 p-6">
              {detailError && <FeedbackAlert message={detailError} tone="error" onDismiss={() => setDetailError('')} className="sm:col-span-2" />}
              <DetailItem label="Thời điểm" value={formatDateTime(selectedLog.timestamp)} />
              <DetailItem label="Người thực hiện" value={`${selectedLog.actor} — ${selectedLog.actorRole}`} />
              <DetailItem label="Hành động" value={auditActionLabel(selectedLog.action, selectedLog.actionLabel)} />
              <DetailItem label="Mã hành động" value={selectedLog.action} />
              <DetailItem label="Đối tượng tác động" value={`${auditTargetLabel(selectedLog)} — ${auditEntityLabel(selectedLog.targetType)}`} />
              <div className="grid min-w-0 gap-1 sm:grid-cols-[170px_1fr] sm:gap-4">
                <p className="text-sm font-medium text-slate-500">Địa chỉ IP</p>
                <IpAddress value={selectedLog.ipAddress} wrap />
              </div>
              <div>
                <p className="text-sm font-medium text-slate-500">Nội dung chi tiết</p>
                <div className="mt-2 rounded-xl bg-slate-50 p-4 text-sm leading-6 text-slate-700">
                  {auditDetailLabel(selectedLog)}
                </div>
              </div>
            </div>

            <div className="flex justify-end border-t border-slate-100 px-6 py-4">
              <button
                type="button"
                onClick={() => setSelectedLog(null)}
                className="rounded-lg bg-slate-900 px-4 py-2.5 text-sm font-medium text-white hover:bg-slate-800"
              >
                Đóng
              </button>
            </div>
          </div>
        </AccessibleModal>
      )}
    </div>
  )
}

function IpAddress({ value, wrap = false }: { value: string | null | undefined; wrap?: boolean }) {
  const address = value?.trim() || '-'
  const isLocalhost = ['::1', '0:0:0:0:0:0:0:1', '0000:0000:0000:0000:0000:0000:0000:0001', '127.0.0.1', '::ffff:127.0.0.1'].includes(address.toLowerCase())

  return (
    <div className="min-w-0">
      <code
        title={address}
        className={`inline-block rounded bg-slate-100 px-2 py-1 font-mono text-sm text-slate-700 ${wrap ? 'max-w-full whitespace-normal break-all' : 'whitespace-nowrap'}`}
      >
        {address === '-' ? 'Không ghi nhận' : address}
      </code>
      {isLocalhost && (
        <p className="mt-1 text-xs text-slate-500">
          Máy cục bộ (localhost)
          {address.includes(':') && (
            <span className="mt-0.5 block">Địa chỉ IPv6 hợp lệ</span>
          )}
        </p>
      )}
    </div>
  )
}

function StatCard({ label, value, valueClass }: { label: string; value: number; valueClass: string }) {
  return (
    <Card>
      <div className="p-5">
        <p className="text-sm text-slate-500">{label}</p>
        <p className={`mt-2 text-2xl font-semibold ${valueClass}`}>{value}</p>
      </div>
    </Card>
  )
}

function TableHead({ children }: { children: string }) {
  return (
    <th className="whitespace-nowrap px-5 py-3 text-center text-xs font-semibold uppercase text-slate-500">
      {children}
    </th>
  )
}

function DetailItem({ label, value }: { label: string; value: string }) {
  return (
    <div className="grid gap-1 sm:grid-cols-[170px_1fr] sm:gap-4">
      <p className="text-sm font-medium text-slate-500">{label}</p>
      <p className="break-words text-sm font-medium text-slate-800">{value}</p>
    </div>
  )
}

function AccessDenied() {
  return (
    <div className="flex min-h-[70vh] items-center justify-center">
      <div className="max-w-md text-center">
        <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-red-50 text-red-600">
          <ShieldCheck size={30} />
        </div>
        <h1 className="mt-5 text-xl font-semibold text-slate-900">Không có quyền truy cập</h1>
        <p className="mt-2 text-sm leading-6 text-slate-500">
          Chỉ Quản trị hệ thống được phép xem nhật ký hoạt động.
        </p>
      </div>
    </div>
  )
}
