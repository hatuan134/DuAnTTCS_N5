import { useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import axios from 'axios'
import {
  CalendarDays,
  CreditCard,
  FilterX,
  RefreshCw,
  Search,
  ShieldCheck,
  UserCheck,
} from 'lucide-react'
import { Navigate } from 'react-router-dom'

import Button from '../../components/ui/Button'
import FeedbackAlert from '../../components/ui/FeedbackAlert'
import PageHeader from '../../components/ui/PageHeader'
import StatusBadge from '../../components/ui/StatusBadge'
import TableActionButton, { TableActions } from '../../components/ui/TableActionButton'
import TablePagination from '../../components/ui/TablePagination'
import useTablePagination from '../../hooks/useTablePagination'

import { getCurrentUser } from '../../core/auth/authStorage'
import { cardTypeService } from '../s1-05-borrow-policy/cardTypeService'
import type { CardType } from '../s1-05-borrow-policy/cardTypeService'
import {
  libraryCardService,
} from './libraryCardService'
import type {
  LibraryCard,
  PendingReaderApplication,
} from './libraryCardService'

type ModalState =
  | { type: 'approve'; reader: PendingReaderApplication }
  | { type: 'reject'; reader: PendingReaderApplication }
  | null

function todayPlus12Months() {
  const date = new Date()
  date.setFullYear(date.getFullYear() + 1)
  return date.toISOString().slice(0, 10)
}

function formatDate(value?: string | null) {
  if (!value) return '-'
  return new Date(`${value}T00:00:00`).toLocaleDateString('vi-VN')
}

function formatDateTime(value: string) {
  return new Date(value).toLocaleString('vi-VN')
}

function getApiMessage(error: unknown) {
  if (axios.isAxiosError(error)) {
    return error.response?.data?.message ?? 'Không thể thực hiện yêu cầu. Vui lòng thử lại.'
  }
  return 'Không thể thực hiện yêu cầu. Vui lòng thử lại.'
}

export default function LibraryCardsPage() {
  const currentUser = getCurrentUser()
  const allowed = currentUser?.role === 'LIBRARIAN' || currentUser?.role === 'ADMIN'

  const [pending, setPending] = useState<PendingReaderApplication[]>([])
  const [issued, setIssued] = useState<LibraryCard[]>([])
  const [cardTypes, setCardTypes] = useState<CardType[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [search, setSearch] = useState('')
  const [fromDate, setFromDate] = useState('')
  const [toDate, setToDate] = useState('')
  const [modal, setModal] = useState<ModalState>(null)
  const [cardTypeId, setCardTypeId] = useState('')
  const [expiresAt, setExpiresAt] = useState(todayPlus12Months())
  const [rejectReason, setRejectReason] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [activeTab, setActiveTab] = useState<'pending' | 'issued'>('pending')

  const activeCardTypes = useMemo(
    () => cardTypes.filter((item) => item.active),
    [cardTypes],
  )

  const pendingPagination = useTablePagination(
    pending,
    `${search}|${fromDate}|${toDate}`,
  )
  const issuedPagination = useTablePagination(issued)

  const load = async () => {
    setLoading(true)
    setError('')
    try {
      const [pendingData, issuedData, cardTypeData] = await Promise.all([
        libraryCardService.getPending({
          search: search.trim() || undefined,
          fromDate: fromDate || undefined,
          toDate: toDate || undefined,
        }),
        libraryCardService.getIssued(),
        cardTypeService.getActiveCardTypes(),
      ])
      setPending(pendingData)
      setIssued(issuedData)
      setCardTypes(cardTypeData)
    } catch (err) {
      setError(getApiMessage(err))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    if (allowed) void load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [allowed])

  if (!allowed) {
    return <Navigate to="/dashboard" replace />
  }

  const openApprove = (reader: PendingReaderApplication) => {
    setSuccess('')
    setError('')
    setCardTypeId(activeCardTypes[0]?.id ? String(activeCardTypes[0].id) : '')
    setExpiresAt(todayPlus12Months())
    setModal({ type: 'approve', reader })
  }

  const openReject = (reader: PendingReaderApplication) => {
    setSuccess('')
    setError('')
    setRejectReason('')
    setModal({ type: 'reject', reader })
  }

  const approve = async () => {
    if (!modal || modal.type !== 'approve') return
    if (!cardTypeId || !expiresAt) {
      setError('Vui lòng chọn loại thẻ và ngày hết hạn.')
      return
    }

    setSubmitting(true)
    setError('')
    try {
      const card = await libraryCardService.approve(
        modal.reader.userId,
        Number(cardTypeId),
        expiresAt,
      )
      setSuccess(`Đã cấp thẻ ${card.cardNumber} cho ${modal.reader.fullName}.`)
      setModal(null)
      await load()
      setActiveTab('issued')
    } catch (err) {
      setError(getApiMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  const reject = async () => {
    if (!modal || modal.type !== 'reject') return
    if (!rejectReason.trim()) {
      setError('Bắt buộc nhập lý do từ chối hồ sơ.')
      return
    }

    setSubmitting(true)
    setError('')
    try {
      await libraryCardService.reject(modal.reader.userId, rejectReason.trim())
      setSuccess(`Đã từ chối hồ sơ của ${modal.reader.fullName}.`)
      setModal(null)
      await load()
    } catch (err) {
      setError(getApiMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Duyệt hồ sơ & cấp thẻ thư viện"
        description="Duyệt hồ sơ chờ, chọn loại thẻ, hạn thẻ và cấp mã thẻ duy nhất cho bạn đọc."
        action={(
          <Button type="button" variant="secondary" onClick={() => void load()} loading={loading}>
            <RefreshCw size={16} />
            Làm mới
          </Button>
        )}
      />

      <div className="grid gap-4 md:grid-cols-3">
        <Stat title="Hồ sơ chờ duyệt" value={pending.length} icon={<UserCheck size={19} />} />
        <Stat title="Thẻ đã cấp" value={issued.length} icon={<CreditCard size={19} />} />
        <Stat title="Loại thẻ đang áp dụng" value={activeCardTypes.length} icon={<ShieldCheck size={19} />} />
      </div>

      {success && (
        <FeedbackAlert message={success} tone="success" onDismiss={() => setSuccess('')} />
      )}
      {error && (
        <FeedbackAlert message={error} tone="error" onDismiss={() => setError('')} />
      )}

      <div className="flex gap-2 rounded-xl border border-slate-200 bg-white p-2">
        <button
          onClick={() => setActiveTab('pending')}
          className={`rounded-lg px-4 py-2 text-sm font-semibold ${activeTab === 'pending' ? 'bg-blue-600 text-white' : 'text-slate-600 hover:bg-slate-100'}`}
        >
          Hồ sơ chờ duyệt ({pending.length})
        </button>
        <button
          onClick={() => setActiveTab('issued')}
          className={`rounded-lg px-4 py-2 text-sm font-semibold ${activeTab === 'issued' ? 'bg-blue-600 text-white' : 'text-slate-600 hover:bg-slate-100'}`}
        >
          Thẻ đã cấp ({issued.length})
        </button>
      </div>

      {activeTab === 'pending' ? (
        <>
          <div className="grid gap-3 rounded-2xl border border-slate-200 bg-white p-4 shadow-sm lg:grid-cols-[1fr_180px_180px_auto_auto]">
            <div className="relative">
              <Search size={18} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
              <input
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                placeholder="Tìm theo họ tên, mã sinh viên / cán bộ..."
                className="w-full rounded-xl border border-slate-300 py-2.5 pl-10 pr-3 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
              />
            </div>
            <input type="date" value={fromDate} onChange={(e) => setFromDate(e.target.value)} className="rounded-xl border border-slate-300 px-3 py-2.5 text-sm" />
            <input type="date" value={toDate} onChange={(e) => setToDate(e.target.value)} className="rounded-xl border border-slate-300 px-3 py-2.5 text-sm" />
            <button onClick={() => void load()} className="rounded-xl bg-blue-600 px-4 py-2.5 text-sm font-semibold text-white hover:bg-blue-700">Lọc</button>
            <button
              onClick={async () => {
                setSearch('')
                setFromDate('')
                setToDate('')
                setLoading(true)
                setError('')
                try {
                  const data = await libraryCardService.getPending()
                  setPending(data)
                } catch (err) {
                  setError(getApiMessage(err))
                } finally {
                  setLoading(false)
                }
              }}
              className="inline-flex items-center justify-center gap-2 rounded-xl border border-slate-300 bg-white px-4 py-2.5 text-sm font-semibold text-slate-700 hover:bg-slate-50"
            >
              <FilterX size={16} /> Xóa lọc
            </button>
          </div>

          <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
            {loading ? (
              <div className="p-10 text-center text-sm text-slate-500">Đang tải hồ sơ...</div>
            ) : pending.length === 0 ? (
              <div className="p-10 text-center text-sm text-slate-500">Không có hồ sơ chờ duyệt phù hợp.</div>
            ) : (
              <>
                <div className="overflow-x-auto">
                <table className="data-table w-full text-sm">
                  <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase text-slate-500">
                    <tr>
                      <th className="px-4 py-3">STT</th>
                      <th className="px-4 py-3">Mã định danh</th>
                      <th className="px-4 py-3">Bạn đọc</th>
                      <th className="px-4 py-3">Liên hệ</th>
                      <th className="px-4 py-3">Ngày gửi</th>
                      <th className="px-4 py-3 text-right">Thao tác</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {pendingPagination.pageItems.map((reader, index) => (
                      <tr key={reader.userId} className="hover:bg-slate-50">
                        <td className="px-4 py-4 font-semibold text-slate-500">
                          {pendingPagination.startIndex + index + 1}
                        </td>
                        <td className="px-4 py-4 font-mono font-semibold text-blue-700">{reader.memberCode}</td>
                        <td className="px-4 py-4">
                          <div className="font-semibold text-slate-900">{reader.fullName}</div>
                          <div className="mt-1 text-xs text-slate-500">Sinh ngày {formatDate(reader.dateOfBirth)}</div>
                        </td>
                        <td className="px-4 py-4 text-slate-600">
                          <div>{reader.email}</div>
                          <div className="mt-1 text-xs">{reader.phone || 'Chưa có số điện thoại'}</div>
                        </td>
                        <td className="px-4 py-4 text-slate-600">{formatDateTime(reader.submittedAt)}</td>
                        <td className="px-4 py-4">
                          <TableActions>
                            <TableActionButton tone="success" onClick={() => openApprove(reader)}>
                              Duyệt và cấp thẻ
                            </TableActionButton>
                            <TableActionButton tone="danger" onClick={() => openReject(reader)}>
                              Từ chối
                            </TableActionButton>
                          </TableActions>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
                </div>
                <TablePagination
                  page={pendingPagination.page}
                  totalItems={pendingPagination.totalItems}
                  totalPages={pendingPagination.totalPages}
                  pageSize={pendingPagination.pageSize}
                  onPageChange={pendingPagination.goToPage}
                />
              </>
            )}
          </div>
        </>
      ) : (
        <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
          {issued.length === 0 ? (
            <div className="p-10 text-center text-sm text-slate-500">Chưa có thẻ thư viện nào được cấp.</div>
          ) : (
            <>
              <div className="overflow-x-auto">
              <table className="data-table w-full text-sm">
                <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase text-slate-500">
                  <tr>
                    <th className="px-4 py-3">STT</th>
                    <th className="px-4 py-3">Mã thẻ</th>
                    <th className="px-4 py-3">Bạn đọc</th>
                    <th className="px-4 py-3">Loại thẻ</th>
                    <th className="px-4 py-3">Ngày cấp</th>
                    <th className="px-4 py-3">Hết hạn</th>
                    <th className="px-4 py-3">Trạng thái</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {issuedPagination.pageItems.map((card, index) => (
                    <tr key={card.id}>
                      <td className="px-4 py-4 font-semibold text-slate-500">
                        {issuedPagination.startIndex + index + 1}
                      </td>
                      <td className="px-4 py-4 font-mono font-semibold text-blue-700">{card.cardNumber}</td>
                      <td className="px-4 py-4"><div className="font-semibold">{card.readerName}</div><div className="text-xs text-slate-500">{card.memberCode}</div></td>
                      <td className="px-4 py-4">{card.cardTypeName}</td>
                      <td className="px-4 py-4">{formatDate(card.issuedAt)}</td>
                      <td className="px-4 py-4">{formatDate(card.expiresAt)}</td>
                      <td className="px-4 py-4"><StatusBadge status={card.status} /></td>
                    </tr>
                  ))}
                </tbody>
              </table>
              </div>
              <TablePagination
                page={issuedPagination.page}
                totalItems={issuedPagination.totalItems}
                totalPages={issuedPagination.totalPages}
                pageSize={issuedPagination.pageSize}
                onPageChange={issuedPagination.goToPage}
              />
            </>
          )}
        </div>
      )}

      {modal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4">
          <div className="w-full max-w-lg rounded-2xl bg-white shadow-xl">
            <div className="border-b border-slate-200 p-5">
              <h2 className="text-lg font-bold text-slate-900">
                {modal.type === 'approve' ? 'Duyệt hồ sơ & cấp thẻ' : 'Từ chối hồ sơ'}
              </h2>
              <p className="mt-1 text-sm text-slate-500">{modal.reader.fullName} • {modal.reader.memberCode}</p>
            </div>

            <div className="space-y-4 p-5">
              {modal.type === 'approve' ? (
                <>
                  <div>
                    <label className="mb-1.5 block text-sm font-medium text-slate-700">Loại thẻ *</label>
                    <select value={cardTypeId} onChange={(e) => setCardTypeId(e.target.value)} className="w-full rounded-xl border border-slate-300 px-3 py-2.5 text-sm">
                      <option value="">Chọn loại thẻ</option>
                      {activeCardTypes.map((type) => <option key={type.id} value={type.id}>{type.name}</option>)}
                    </select>
                  </div>
                  <div>
                    <label className="mb-1.5 block text-sm font-medium text-slate-700">Ngày hết hạn *</label>
                    <div className="relative">
                      <CalendarDays size={17} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
                      <input type="date" value={expiresAt} onChange={(e) => setExpiresAt(e.target.value)} className="w-full rounded-xl border border-slate-300 py-2.5 pl-10 pr-3 text-sm" />
                    </div>
                    <p className="mt-1 text-xs text-slate-500">Mặc định 12 tháng kể từ ngày cấp. Bạn có thể điều chỉnh trước khi xác nhận.</p>
                  </div>
                </>
              ) : (
                <div>
                  <label className="mb-1.5 block text-sm font-medium text-slate-700">Lý do từ chối *</label>
                  <textarea rows={4} value={rejectReason} onChange={(e) => setRejectReason(e.target.value)} placeholder="Nhập lý do để bạn đọc xem tại trang cá nhân..." className="w-full resize-none rounded-xl border border-slate-300 px-3 py-2.5 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100" />
                </div>
              )}
            </div>

            <div className="flex justify-end gap-3 border-t border-slate-200 p-5">
              <button disabled={submitting} onClick={() => setModal(null)} className="rounded-xl border border-slate-300 bg-white px-4 py-2.5 text-sm font-semibold text-slate-700 hover:bg-slate-50">Hủy</button>
              {modal.type === 'approve' ? (
                <button disabled={submitting} onClick={() => void approve()} className="rounded-xl bg-emerald-600 px-4 py-2.5 text-sm font-semibold text-white hover:bg-emerald-700 disabled:opacity-60">
                  {submitting ? 'Đang cấp...' : 'Xác nhận cấp thẻ'}
                </button>
              ) : (
                <button disabled={submitting} onClick={() => void reject()} className="rounded-xl bg-red-600 px-4 py-2.5 text-sm font-semibold text-white hover:bg-red-700 disabled:opacity-60">
                  {submitting ? 'Đang xử lý...' : 'Xác nhận từ chối'}
                </button>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

function Stat({ title, value, icon }: { title: string; value: number; icon: ReactNode }) {
  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex items-center justify-between text-slate-500"><span className="text-sm font-medium">{title}</span><span className="text-blue-600">{icon}</span></div>
      <div className="mt-2 text-2xl font-bold text-slate-900">{value}</div>
    </div>
  )
}
