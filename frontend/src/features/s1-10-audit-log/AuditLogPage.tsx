import {
  useMemo,
  useState,
} from 'react'

import {
  Activity,
  Eye,
  Filter,
  LogIn,
  Search,
  ShieldCheck,
  UserCog,
  UserPlus,
  X,
} from 'lucide-react'

import Card from '../../components/ui/Card'
import PageHeader from '../../components/ui/PageHeader'

type AuditAction =
  | 'LOGIN'
  | 'CREATE_ACCOUNT'
  | 'UPDATE_ACCOUNT'
  | 'ISSUE_CARD'
  | 'UPDATE_POLICY'

type AuditLog = {
  id: number
  timestamp: string
  actor: string
  actorRole: string
  action: AuditAction
  target: string
  targetType: string
  ipAddress: string
  detail: string
}

const CURRENT_ROLE =
  'ADMIN'

const auditLogs: AuditLog[] = [
  {
    id: 1,
    timestamp:
      '2026-09-26T08:15:22',
    actor: 'Nguyễn Văn Admin',
    actorRole:
      'Quản trị hệ thống',
    action: 'LOGIN',
    target:
      'Hệ thống quản lý thư viện',
    targetType: 'Phiên đăng nhập',
    ipAddress: '192.168.1.15',
    detail:
      'Đăng nhập thành công bằng email admin@libra.edu.vn.',
  },
  {
    id: 2,
    timestamp:
      '2026-09-26T08:22:10',
    actor: 'Nguyễn Văn Admin',
    actorRole:
      'Quản trị hệ thống',
    action: 'CREATE_ACCOUNT',
    target: 'Trần Thị Lan',
    targetType:
      'Tài khoản nhân viên',
    ipAddress: '192.168.1.15',
    detail:
      'Tạo tài khoản mới và gán vai trò Thủ thư.',
  },
  {
    id: 3,
    timestamp:
      '2026-09-26T08:35:44',
    actor: 'Nguyễn Văn Admin',
    actorRole:
      'Quản trị hệ thống',
    action: 'UPDATE_ACCOUNT',
    target: 'Lê Văn Minh',
    targetType:
      'Tài khoản nhân viên',
    ipAddress: '192.168.1.15',
    detail:
      'Cập nhật trạng thái tài khoản từ Hoạt động sang Khóa.',
  },
  {
    id: 4,
    timestamp:
      '2026-09-26T09:02:31',
    actor: 'Trần Thị Lan',
    actorRole: 'Thủ thư',
    action: 'ISSUE_CARD',
    target: 'TV20260001',
    targetType:
      'Thẻ thư viện',
    ipAddress: '192.168.1.22',
    detail:
      'Cấp thẻ sinh viên cho bạn đọc Nguyễn Văn An, hạn thẻ 12 tháng.',
  },
  {
    id: 5,
    timestamp:
      '2026-09-26T09:18:08',
    actor: 'Phạm Văn Quản lý',
    actorRole:
      'Quản lý thư viện',
    action: 'UPDATE_POLICY',
    target: 'Thẻ sinh viên',
    targetType:
      'Chính sách mượn',
    ipAddress: '192.168.1.30',
    detail:
      'Thay đổi số ngày mượn từ 14 ngày thành 20 ngày.',
  },
  {
    id: 6,
    timestamp:
      '2026-09-26T10:04:55',
    actor: 'Nguyễn Văn Admin',
    actorRole:
      'Quản trị hệ thống',
    action: 'LOGIN',
    target:
      'Hệ thống quản lý thư viện',
    targetType: 'Phiên đăng nhập',
    ipAddress: '10.10.20.15',
    detail:
      'Đăng nhập thành công bằng tài khoản quản trị.',
  },
  {
    id: 7,
    timestamp:
      '2026-09-25T14:11:35',
    actor: 'Trần Thị Lan',
    actorRole: 'Thủ thư',
    action: 'ISSUE_CARD',
    target: 'TV20260002',
    targetType:
      'Thẻ thư viện',
    ipAddress: '192.168.1.22',
    detail:
      'Cấp thẻ cán bộ cho bạn đọc Trần Văn Bình.',
  },
  {
    id: 8,
    timestamp:
      '2026-09-25T15:24:12',
    actor: 'Phạm Văn Quản lý',
    actorRole:
      'Quản lý thư viện',
    action: 'UPDATE_POLICY',
    target: 'Thẻ cán bộ',
    targetType:
      'Chính sách mượn',
    ipAddress: '192.168.1.30',
    detail:
      'Thay đổi số lần gia hạn tối đa từ 2 thành 3 lần.',
  },
]

function getActionLabel(
  action: AuditAction,
) {
  switch (action) {
    case 'LOGIN':
      return 'Đăng nhập'

    case 'CREATE_ACCOUNT':
      return 'Tạo tài khoản'

    case 'UPDATE_ACCOUNT':
      return 'Sửa tài khoản'

    case 'ISSUE_CARD':
      return 'Cấp thẻ'

    case 'UPDATE_POLICY':
      return 'Sửa chính sách'

    default:
      return action
  }
}

function formatDateTime(
  value: string,
) {
  return new Date(
    value,
  ).toLocaleString(
    'vi-VN',
  )
}

function getActionStyle(
  action: AuditAction,
) {
  switch (action) {
    case 'LOGIN':
      return {
        className:
          'bg-blue-50 text-blue-700',
        icon: LogIn,
      }

    case 'CREATE_ACCOUNT':
      return {
        className:
          'bg-emerald-50 text-emerald-700',
        icon: UserPlus,
      }

    case 'UPDATE_ACCOUNT':
      return {
        className:
          'bg-amber-50 text-amber-700',
        icon: UserCog,
      }

    case 'ISSUE_CARD':
      return {
        className:
          'bg-violet-50 text-violet-700',
        icon: ShieldCheck,
      }

    case 'UPDATE_POLICY':
      return {
        className:
          'bg-cyan-50 text-cyan-700',
        icon: Activity,
      }

    default:
      return {
        className:
          'bg-slate-100 text-slate-700',
        icon: Activity,
      }
  }
}

export default function AuditLogPage() {
  const [keyword, setKeyword] =
    useState('')

  const [actorFilter, setActorFilter] =
    useState('ALL')

  const [actionFilter, setActionFilter] =
    useState('ALL')

  const [fromDate, setFromDate] =
    useState('')

  const [toDate, setToDate] =
    useState('')

  const [
    selectedLog,
    setSelectedLog,
  ] = useState<AuditLog | null>(
    null,
  )

  const actors =
    useMemo(() => {
      return Array.from(
        new Set(
          auditLogs.map(
            (item) =>
              item.actor,
          ),
        ),
      )
    }, [])

  const filteredLogs =
    useMemo(() => {
      const normalizedKeyword =
        keyword
          .trim()
          .toLowerCase()

      return auditLogs.filter(
        (item) => {
          const date =
            item.timestamp.slice(
              0,
              10,
            )

          const matchesKeyword =
            !normalizedKeyword ||
            item.actor
              .toLowerCase()
              .includes(
                normalizedKeyword,
              ) ||
            item.target
              .toLowerCase()
              .includes(
                normalizedKeyword,
              ) ||
            item.ipAddress
              .toLowerCase()
              .includes(
                normalizedKeyword,
              ) ||
            item.detail
              .toLowerCase()
              .includes(
                normalizedKeyword,
              )

          const matchesActor =
            actorFilter ===
              'ALL' ||
            item.actor ===
              actorFilter

          const matchesAction =
            actionFilter ===
              'ALL' ||
            item.action ===
              actionFilter

          const matchesFrom =
            !fromDate ||
            date >= fromDate

          const matchesTo =
            !toDate ||
            date <= toDate

          return (
            matchesKeyword &&
            matchesActor &&
            matchesAction &&
            matchesFrom &&
            matchesTo
          )
        },
      )
    }, [
      keyword,
      actorFilter,
      actionFilter,
      fromDate,
      toDate,
    ])

  const resetFilters = () => {
    setKeyword('')
    setActorFilter('ALL')
    setActionFilter('ALL')
    setFromDate('')
    setToDate('')
  }

  /*
   * Mock frontend:
   * S1-10 chỉ cho Admin truy cập.
   *
   * Khi nối backend, quyền này phải
   * được kiểm tra lại ở API.
   */
  if (
    CURRENT_ROLE !== 'ADMIN'
  ) {
    return (
      <div className="flex min-h-[70vh] items-center justify-center">
        <div className="max-w-md text-center">
          <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-red-50 text-red-600">
            <ShieldCheck
              size={30}
            />
          </div>

          <h1 className="mt-5 text-xl font-semibold text-slate-900">
            Không có quyền truy cập
          </h1>

          <p className="mt-2 text-sm leading-6 text-slate-500">
            Chỉ Quản trị hệ thống
            được phép xem nhật ký
            hoạt động.
          </p>
        </div>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Nhật ký hoạt động"
        description="Theo dõi các thao tác quan trọng được thực hiện trong hệ thống."
      />

      {/* THỐNG KÊ */}
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <Card>
          <div className="p-5">
            <p className="text-sm text-slate-500">
              Tổng nhật ký
            </p>

            <p className="mt-2 text-2xl font-semibold text-slate-900">
              {auditLogs.length}
            </p>
          </div>
        </Card>

        <Card>
          <div className="p-5">
            <p className="text-sm text-slate-500">
              Đăng nhập
            </p>

            <p className="mt-2 text-2xl font-semibold text-blue-700">
              {
                auditLogs.filter(
                  (item) =>
                    item.action ===
                    'LOGIN',
                ).length
              }
            </p>
          </div>
        </Card>

        <Card>
          <div className="p-5">
            <p className="text-sm text-slate-500">
              Tài khoản
            </p>

            <p className="mt-2 text-2xl font-semibold text-amber-700">
              {
                auditLogs.filter(
                  (item) =>
                    item.action ===
                      'CREATE_ACCOUNT' ||
                    item.action ===
                      'UPDATE_ACCOUNT',
                ).length
              }
            </p>
          </div>
        </Card>

        <Card>
          <div className="p-5">
            <p className="text-sm text-slate-500">
              Nghiệp vụ thư viện
            </p>

            <p className="mt-2 text-2xl font-semibold text-violet-700">
              {
                auditLogs.filter(
                  (item) =>
                    item.action ===
                      'ISSUE_CARD' ||
                    item.action ===
                      'UPDATE_POLICY',
                ).length
              }
            </p>
          </div>
        </Card>
      </div>

      {/* BỘ LỌC */}
      <Card>
        <div className="border-b border-slate-200 p-5">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-slate-100 text-slate-600">
              <Filter size={19} />
            </div>

            <div>
              <h2 className="text-lg font-semibold text-slate-900">
                Bộ lọc nhật ký
              </h2>

              <p className="mt-1 text-sm text-slate-500">
                Lọc theo khoảng ngày,
                người thực hiện và loại
                hành động.
              </p>
            </div>
          </div>
        </div>

        <div className="grid gap-4 p-5 md:grid-cols-2 xl:grid-cols-5">
          <div className="relative">
            <Search
              size={17}
              className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400"
            />

            <input
              value={keyword}
              onChange={(event) =>
                setKeyword(
                  event.target.value,
                )
              }
              placeholder="Tìm đối tượng, IP..."
              className="w-full rounded-lg border border-slate-300 py-2.5 pl-9 pr-3 text-sm outline-none focus:border-blue-500"
            />
          </div>

          <select
            value={actorFilter}
            onChange={(event) =>
              setActorFilter(
                event.target.value,
              )
            }
            className="rounded-lg border border-slate-300 px-3 py-2.5 text-sm outline-none focus:border-blue-500"
          >
            <option value="ALL">
              Tất cả người thực hiện
            </option>

            {actors.map(
              (actor) => (
                <option
                  key={actor}
                  value={actor}
                >
                  {actor}
                </option>
              ),
            )}
          </select>

          <select
            value={actionFilter}
            onChange={(event) =>
              setActionFilter(
                event.target.value,
              )
            }
            className="rounded-lg border border-slate-300 px-3 py-2.5 text-sm outline-none focus:border-blue-500"
          >
            <option value="ALL">
              Tất cả hành động
            </option>

            <option value="LOGIN">
              Đăng nhập
            </option>

            <option value="CREATE_ACCOUNT">
              Tạo tài khoản
            </option>

            <option value="UPDATE_ACCOUNT">
              Sửa tài khoản
            </option>

            <option value="ISSUE_CARD">
              Cấp thẻ
            </option>

            <option value="UPDATE_POLICY">
              Sửa chính sách
            </option>
          </select>

          <div>
            <input
              type="date"
              value={fromDate}
              onChange={(event) =>
                setFromDate(
                  event.target.value,
                )
              }
              title="Từ ngày"
              className="w-full rounded-lg border border-slate-300 px-3 py-2.5 text-sm outline-none focus:border-blue-500"
            />
          </div>

          <div>
            <input
              type="date"
              value={toDate}
              onChange={(event) =>
                setToDate(
                  event.target.value,
                )
              }
              title="Đến ngày"
              className="w-full rounded-lg border border-slate-300 px-3 py-2.5 text-sm outline-none focus:border-blue-500"
            />
          </div>
        </div>

        <div className="flex items-center justify-between border-t border-slate-100 px-5 py-3">
          <p className="text-sm text-slate-500">
            Tìm thấy{' '}
            <strong className="text-slate-800">
              {filteredLogs.length}
            </strong>{' '}
            nhật ký
          </p>

          <button
            type="button"
            onClick={resetFilters}
            className="text-sm font-medium text-blue-600 hover:text-blue-700"
          >
            Xóa bộ lọc
          </button>
        </div>
      </Card>

      {/* DANH SÁCH LOG */}
      <Card>
        <div className="border-b border-slate-200 p-5">
          <h2 className="text-lg font-semibold text-slate-900">
            Danh sách nhật ký
          </h2>

          <p className="mt-1 text-sm text-slate-500">
            Nhật ký chỉ được xem,
            không thể sửa hoặc xóa.
          </p>
        </div>

        {filteredLogs.length ===
        0 ? (
          <div className="px-6 py-14 text-center">
            <Activity
              size={38}
              className="mx-auto text-slate-300"
            />

            <p className="mt-3 font-medium text-slate-700">
              Không tìm thấy nhật ký
            </p>

            <p className="mt-1 text-sm text-slate-500">
              Hãy thay đổi điều kiện
              lọc để thử lại.
            </p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[1200px]">
              <thead>
                <tr className="border-b border-slate-200 bg-slate-50">
                  <th className="px-5 py-3 text-left text-xs font-semibold uppercase text-slate-500">
                    Thời điểm
                  </th>

                  <th className="px-5 py-3 text-left text-xs font-semibold uppercase text-slate-500">
                    Người thực hiện
                  </th>

                  <th className="px-5 py-3 text-left text-xs font-semibold uppercase text-slate-500">
                    Hành động
                  </th>

                  <th className="px-5 py-3 text-left text-xs font-semibold uppercase text-slate-500">
                    Đối tượng
                  </th>

                  <th className="px-5 py-3 text-left text-xs font-semibold uppercase text-slate-500">
                    Địa chỉ IP
                  </th>

                  <th className="px-5 py-3 text-right text-xs font-semibold uppercase text-slate-500">
                    Chi tiết
                  </th>
                </tr>
              </thead>

              <tbody>
                {filteredLogs.map(
                  (item) => {
                    const style =
                      getActionStyle(
                        item.action,
                      )

                    const Icon =
                      style.icon

                    return (
                      <tr
                        key={item.id}
                        className="border-b border-slate-100 transition hover:bg-slate-50"
                      >
                        <td className="whitespace-nowrap px-5 py-4 text-sm text-slate-600">
                          {formatDateTime(
                            item.timestamp,
                          )}
                        </td>

                        <td className="px-5 py-4">
                          <p className="font-medium text-slate-900">
                            {
                              item.actor
                            }
                          </p>

                          <p className="mt-0.5 text-xs text-slate-500">
                            {
                              item.actorRole
                            }
                          </p>
                        </td>

                        <td className="px-5 py-4">
                          <span
                            className={`inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-medium ${style.className}`}
                          >
                            <Icon
                              size={14}
                            />

                            {getActionLabel(
                              item.action,
                            )}
                          </span>
                        </td>

                        <td className="px-5 py-4">
                          <p className="font-medium text-slate-800">
                            {
                              item.target
                            }
                          </p>

                          <p className="mt-0.5 text-xs text-slate-500">
                            {
                              item.targetType
                            }
                          </p>
                        </td>

                        <td className="px-5 py-4">
                          <code className="rounded bg-slate-100 px-2 py-1 text-xs text-slate-700">
                            {
                              item.ipAddress
                            }
                          </code>
                        </td>

                        <td className="px-5 py-4 text-right">
                          <button
                            type="button"
                            title="Xem chi tiết"
                            onClick={() =>
                              setSelectedLog(
                                item,
                              )
                            }
                            className="inline-flex h-9 w-9 items-center justify-center rounded-lg border border-slate-200 text-slate-600 transition hover:border-blue-200 hover:bg-blue-50 hover:text-blue-600"
                          >
                            <Eye
                              size={16}
                            />
                          </button>
                        </td>
                      </tr>
                    )
                  },
                )}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      {/* GHI CHÚ */}
      <div className="rounded-xl border border-blue-100 bg-blue-50 p-4">
        <div className="flex gap-3">
          <ShieldCheck
            size={20}
            className="mt-0.5 shrink-0 text-blue-600"
          />

          <div>
            <p className="text-sm font-semibold text-blue-900">
              Nhật ký hệ thống
            </p>

            <p className="mt-1 text-sm leading-6 text-blue-700">
              Chỉ Quản trị hệ thống
              được phép xem nhật ký.
              Nhật ký không có chức năng
              sửa hoặc xóa trên giao diện.
            </p>
          </div>
        </div>
      </div>

      {/* MODAL CHI TIẾT */}
      {selectedLog && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4">
          <div className="w-full max-w-xl rounded-2xl bg-white shadow-2xl">
            <div className="flex items-start justify-between border-b border-slate-200 px-6 py-5">
              <div>
                <h2 className="text-xl font-semibold text-slate-900">
                  Chi tiết nhật ký
                </h2>

                <p className="mt-1 text-sm text-slate-500">
                  Mã nhật ký #
                  {selectedLog.id}
                </p>
              </div>

              <button
                type="button"
                onClick={() =>
                  setSelectedLog(
                    null,
                  )
                }
                className="flex h-9 w-9 items-center justify-center rounded-lg text-slate-400 transition hover:bg-slate-100 hover:text-slate-700"
              >
                <X size={20} />
              </button>
            </div>

            <div className="space-y-5 p-6">
              <DetailItem
                label="Thời điểm"
                value={formatDateTime(
                  selectedLog.timestamp,
                )}
              />

              <DetailItem
                label="Người thực hiện"
                value={`${selectedLog.actor} — ${selectedLog.actorRole}`}
              />

              <DetailItem
                label="Hành động"
                value={getActionLabel(
                  selectedLog.action,
                )}
              />

              <DetailItem
                label="Đối tượng tác động"
                value={`${selectedLog.target} — ${selectedLog.targetType}`}
              />

              <DetailItem
                label="Địa chỉ IP"
                value={
                  selectedLog.ipAddress
                }
              />

              <div>
                <p className="text-sm font-medium text-slate-500">
                  Nội dung chi tiết
                </p>

                <div className="mt-2 rounded-xl bg-slate-50 p-4 text-sm leading-6 text-slate-700">
                  {
                    selectedLog.detail
                  }
                </div>
              </div>
            </div>

            <div className="flex justify-end border-t border-slate-100 px-6 py-4">
              <button
                type="button"
                onClick={() =>
                  setSelectedLog(
                    null,
                  )
                }
                className="rounded-lg bg-slate-900 px-4 py-2.5 text-sm font-medium text-white hover:bg-slate-800"
              >
                Đóng
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

type DetailItemProps = {
  label: string
  value: string
}

function DetailItem({
  label,
  value,
}: DetailItemProps) {
  return (
    <div className="grid gap-1 sm:grid-cols-[160px_1fr] sm:gap-4">
      <p className="text-sm font-medium text-slate-500">
        {label}
      </p>

      <p className="text-sm font-medium text-slate-800">
        {value}
      </p>
    </div>
  )
}