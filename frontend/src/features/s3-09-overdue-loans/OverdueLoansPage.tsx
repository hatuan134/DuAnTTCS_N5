import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { ClockAlert, Filter, History, Phone, PhoneCall, RefreshCw, X } from 'lucide-react'
import { Link } from 'react-router-dom'
import Button from '../../components/ui/Button'
import Card from '../../components/ui/Card'
import EmptyState from '../../components/ui/EmptyState'
import FeedbackAlert from '../../components/ui/FeedbackAlert'
import Input from '../../components/ui/Input'
import LoadingState from '../../components/ui/LoadingState'
import PageHeader from '../../components/ui/PageHeader'
import { tableActionClassName } from '../../components/ui/TableActionButton'
import { getCurrentUser } from '../../core/auth/authStorage'
import { getApiErrorMessage } from '../s1-02-user-management/accountService'
import { formatLoanTimestamp, loanRoles } from '../s3-01-loans/loanService'
import { overdueLoanService } from './overdueLoanService'
import OverdueContactDialog from './OverdueContactDialog'
import type { OverdueContact, OverdueLoanItem } from './overdueLoanService'
import { countOverdueLoanVouchers, describeOverdueFilter, filterOverdueLoans, validateOverdueFilter } from './overdueFilter'
import type { OverdueDaysFilter } from './overdueFilter'

export default function OverdueLoansPage() {
  const allowed = loanRoles.includes(getCurrentUser()?.role ?? '')
  const [items, setItems] = useState<OverdueLoanItem[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setErrorState] = useState('')
  const [errorNotice, setErrorNotice] = useState('')
  const setError = (message: string) => { setErrorState(message); setErrorNotice(message) }
  const [revision, setRevision] = useState(0)
  const [minimumInput, setMinimumInput] = useState('')
  const [maximumInput, setMaximumInput] = useState('')
  const [exclusiveMinimum, setExclusiveMinimum] = useState(false)
  const [appliedFilter, setAppliedFilter] = useState<OverdueDaysFilter | null>(null)
  const [filterNotice, setFilterNotice] = useState('')
  const [selectedLoan, setSelectedLoan] = useState<{ id: number; number: string } | null>(null)
  const canRecord = getCurrentUser()?.role === 'LIBRARIAN'

  function onRecorded(contact: OverdueContact) {
    setItems((previous) => previous.map((item) => item.loanId === contact.loanId ? {
      ...item,
      lastContactedAt: contact.contactedAt,
      lastContactNote: contact.note,
      lastContactStaffName: contact.staffName,
    } : item))
  }

  function contactStatus(item: OverdueLoanItem) {
    if (!item.lastContactedAt) return <span className="text-slate-500">Chưa liên hệ</span>
    return <div className="min-w-0 space-y-1">
      <time className="block font-semibold text-blue-700" dateTime={item.lastContactedAt}>
        {formatLoanTimestamp(item.lastContactedAt)}
      </time>
      <p className="max-w-64 break-words text-xs text-slate-600" title={item.lastContactNote ?? ''}>
        {item.lastContactNote}
      </p>
    </div>
  }

  const filteredItems = filterOverdueLoans(items, appliedFilter)
  const matchingLoans = countOverdueLoanVouchers(filteredItems)
  const totalLoans = countOverdueLoanVouchers(items)
  const hasFilter = appliedFilter !== null && (appliedFilter.minimum !== null || appliedFilter.maximum !== null)

  function applyFilter(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const result = validateOverdueFilter(minimumInput, maximumInput, exclusiveMinimum)
    if (result.ok === false) {
      setFilterNotice(result.message)
      return
    }
    setFilterNotice('')
    setAppliedFilter(result.filter)
  }

  function applyQuickFilter(days: number) {
    setMinimumInput(String(days))
    setMaximumInput('')
    setExclusiveMinimum(true)
    setAppliedFilter({ minimum: days, maximum: null, exclusiveMinimum: true })
    setFilterNotice('')
  }

  function clearFilter() {
    setMinimumInput('')
    setMaximumInput('')
    setExclusiveMinimum(false)
    setAppliedFilter(null)
    setFilterNotice('')
  }

  useEffect(() => {
    if (!allowed) return

    let active = true
    setLoading(true)
    setError('')

    overdueLoanService.list()
      .then((data) => {
        if (active) setItems(data)
      })
      .catch((requestError: unknown) => {
        if (active) {
          setItems([])
          setError(getApiErrorMessage(
            requestError,
            'Không tải được danh sách phiếu mượn quá hạn. Vui lòng thử lại.',
          ))
        }
      })
      .finally(() => {
        if (active) setLoading(false)
      })

    return () => {
      active = false
    }
  }, [allowed, revision])

  if (!allowed) {
    return (
      <p role="alert" className="rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700">
        Bạn không có quyền xem danh sách phiếu mượn quá hạn.
      </p>
    )
  }

  return (
    <div className="space-y-5">
      <PageHeader
        title="Phiếu mượn quá hạn"
        description="Chỉ tính các ngày thư viện mở cửa từ sau hạn trả đến hôm nay, kể cả ngày hôm nay nếu mở cửa. Số ngày trễ nhiều nhất được ưu tiên trước."
        action={(
          <Button
            type="button"
            variant="secondary"
            loading={loading}
            onClick={() => setRevision((value) => value + 1)}
          >
            <RefreshCw size={16} />
            Làm mới
          </Button>
        )}
      />

      <Card className="p-4 sm:p-5">
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-2 text-slate-900">
            <Filter size={18} className="text-blue-600" aria-hidden="true" />
            <h3 className="font-semibold">Lọc theo số ngày trễ</h3>
          </div>
          <div className="flex flex-wrap gap-2" aria-label="Các mức lọc nhanh">
            {[7, 30].map((days) => (
              <Button
                key={days}
                type="button"
                size="sm"
                variant={appliedFilter?.minimum === days && appliedFilter.exclusiveMinimum && appliedFilter.maximum === null ? 'primary' : 'secondary'}
                aria-pressed={appliedFilter?.minimum === days && appliedFilter.exclusiveMinimum && appliedFilter.maximum === null}
                onClick={() => applyQuickFilter(days)}
              >
                Trên {days} ngày
              </Button>
            ))}
          </div>
        </div>

        <form onSubmit={applyFilter} noValidate className="space-y-3">
          <div className="grid gap-3 sm:grid-cols-2">
            <Input
              id="overdue-minimum" type="number" min="0" step="1" inputMode="numeric"
              label="Số ngày trễ tối thiểu" placeholder="Ví dụ: 7"
              value={minimumInput} onChange={(event) => { setMinimumInput(event.target.value); setFilterNotice('') }}
            />
            <Input
              id="overdue-maximum" type="number" min="0" step="1" inputMode="numeric"
              label="Số ngày trễ tối đa" placeholder="Ví dụ: 30"
              value={maximumInput} onChange={(event) => { setMaximumInput(event.target.value); setFilterNotice('') }}
            />
          </div>
          <label className="flex cursor-pointer items-start gap-2.5 text-sm text-slate-600">
            <input
              type="checkbox" checked={exclusiveMinimum}
              onChange={(event) => { setExclusiveMinimum(event.target.checked); setFilterNotice('') }}
              className="mt-1 h-4 w-4 shrink-0 accent-blue-600"
            />
            Chỉ lấy phiếu trễ <strong className="text-slate-800">trên</strong> số ngày tối thiểu (không bao gồm ngày đó).
          </label>
          <div className="flex flex-wrap items-center gap-2">
            <Button type="submit" size="sm"><Filter size={15} />Áp dụng bộ lọc</Button>
            <Button type="button" variant="secondary" size="sm" onClick={clearFilter}>
              <X size={15} /> Xóa bộ lọc
            </Button>
          </div>
          <p className="text-xs leading-5 text-slate-500">
            Khoảng từ 7 đến 30 bao gồm cả ngày 7 và 30. “Trên 7 ngày” bắt đầu từ ngày trễ thứ 8.
            Số ngày trễ được tính theo ngày thư viện mở cửa.
          </p>
        </form>
        {filterNotice && (
          <FeedbackAlert
            className="mt-3" tone="error" message={filterNotice}
            onDismiss={() => setFilterNotice('')}
          />
        )}
      </Card>

      {errorNotice && <FeedbackAlert message={errorNotice} tone="error" onDismiss={() => setErrorNotice('')} />}
      {error && <p role="status" className="rounded-xl bg-slate-50 p-4 text-sm text-slate-700">Chưa tải được danh sách quá hạn. Nhấn “Làm mới” để thử lại.</p>}

      {loading && <LoadingState />}

      {!loading && !error && items.length === 0 && (
        <EmptyState
          title="Hôm nay không có phiếu quá hạn"
          description="Không có sách chưa trả nào có hạn trả trước ngày hôm nay."
        />
      )}

      {!loading && !error && items.length > 0 && (
        <div role="status" aria-live="polite" className="flex flex-wrap items-center justify-between gap-2 rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm">
          <span className="font-semibold text-slate-900">
            Tìm thấy {matchingLoans} phiếu phù hợp
            {filteredItems.length !== matchingLoans && ` (${filteredItems.length} bản sách quá hạn)`}
          </span>
          <span className="text-slate-600">
            {describeOverdueFilter(hasFilter ? appliedFilter : null)} · Tổng cộng {totalLoans} phiếu quá hạn
          </span>
        </div>
      )}

      {!loading && !error && items.length > 0 && filteredItems.length === 0 && (
        <EmptyState
          title="Không có phiếu phù hợp"
          description="Không có phiếu quá hạn nào trong khoảng ngày trễ đang chọn. Hãy thay đổi hoặc xóa bộ lọc để xem toàn bộ danh sách."
        />
      )}

      {!loading && !error && filteredItems.length > 0 && (
        <>
          <Card className="overflow-hidden xl:hidden">
            <div className="divide-y divide-slate-100">
              {filteredItems.map((item, index) => (
                <article key={item.itemId} className="space-y-4 p-4 sm:p-5">
                  <div className="flex flex-wrap items-start justify-between gap-3">
                    <div className="min-w-0">
                      <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                        STT {index + 1} · {item.loanNumber}
                      </p>
                      <p className="mt-1 break-words text-base font-semibold text-slate-950">
                        {item.readerName}
                      </p>
                    </div>
                    <span className="inline-flex shrink-0 items-center gap-1.5 rounded-full border border-red-200 bg-red-50 px-3 py-1 text-sm font-semibold text-red-700">
                      <ClockAlert size={15} />
                      Trễ {item.overdueDays} ngày mở cửa
                    </span>
                  </div>

                  <dl className="grid gap-3 text-sm sm:grid-cols-2">
                    <div className="min-w-0">
                      <dt className="text-slate-500">Số điện thoại</dt>
                      <dd className="mt-1 break-words font-medium text-slate-900">
                        {item.readerPhone ? (
                          <a className="inline-flex items-center gap-1.5 text-blue-700 hover:underline" href={`tel:${item.readerPhone}`}>
                            <Phone size={14} />
                            {item.readerPhone}
                          </a>
                        ) : 'Chưa cập nhật'}
                      </dd>
                    </div>
                    <div className="min-w-0">
                      <dt className="text-slate-500">Tên sách</dt>
                      <dd className="mt-1 break-words font-medium text-slate-900">{item.bookTitle}</dd>
                    </div>
                    <div>
                      <dt className="text-slate-500">Hạn trả</dt>
                      <dd className="mt-1 font-medium text-slate-900">{formatLoanTimestamp(item.dueAt, true)}</dd>
                    </div>
                  </dl>

                  <div className="min-w-0 rounded-lg bg-slate-50 p-3 text-sm">
                    <p className="mb-1 text-xs font-semibold text-slate-500">Liên hệ gần nhất</p>
                    {contactStatus(item)}
                  </div>
                  <div className="flex flex-wrap gap-2">
                    <Button type="button" size="sm" variant="secondary"
                      onClick={() => setSelectedLoan({ id: item.loanId, number: item.loanNumber })}>
                      {canRecord ? <PhoneCall size={15} /> : <History size={15} />}
                      {canRecord ? 'Đánh dấu đã liên hệ' : 'Lịch sử liên hệ'}
                    </Button>
                    <Link to={`/loans/${item.loanId}`} className={tableActionClassName('primary')}>Xem phiếu</Link>
                  </div>
                </article>
              ))}
            </div>
          </Card>

          <Card className="hidden overflow-hidden xl:block">
            <div className="overflow-x-auto">
              <table className="data-table min-w-full divide-y divide-slate-200 text-sm">
                <caption className="sr-only">
                  Danh sách sách quá hạn, sắp xếp theo số ngày trễ giảm dần
                </caption>
                <thead className="bg-slate-50 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
                  <tr>
                    <th scope="col" className="px-4 py-3">STT</th>
                    <th scope="col" className="px-4 py-3">Mã phiếu</th>
                    <th scope="col" className="px-4 py-3">Bạn đọc</th>
                    <th scope="col" className="px-4 py-3">Số điện thoại</th>
                    <th scope="col" className="px-4 py-3">Tên sách</th>
                    <th scope="col" className="px-4 py-3">Hạn trả</th>
                    <th scope="col" className="px-4 py-3 text-center">Ngày trễ (mở cửa)</th>
                    <th scope="col" className="px-4 py-3">Liên hệ gần nhất</th>
                    <th scope="col" className="px-4 py-3">Thao tác</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 bg-white">
                  {filteredItems.map((item, index) => (
                    <tr key={item.itemId} className="hover:bg-slate-50/70">
                      <td className="px-4 py-4 font-semibold text-slate-500">{index + 1}</td>
                      <td className="max-w-48 break-all px-4 py-4 font-mono font-semibold text-slate-900">
                        {item.loanNumber}
                      </td>
                      <td className="max-w-56 break-words px-4 py-4 font-medium text-slate-900">
                        {item.readerName}
                      </td>
                      <td className="whitespace-nowrap px-4 py-4 text-slate-700">
                        {item.readerPhone ? (
                          <a className="text-blue-700 hover:underline" href={`tel:${item.readerPhone}`}>
                            {item.readerPhone}
                          </a>
                        ) : (
                          <span className="text-slate-400">Chưa cập nhật</span>
                        )}
                      </td>
                      <td className="max-w-72 break-words px-4 py-4 text-slate-700">{item.bookTitle}</td>
                      <td className="whitespace-nowrap px-4 py-4 text-slate-700">
                        {formatLoanTimestamp(item.dueAt, true)}
                      </td>
                      <td className="px-4 py-4 text-center">
                        <span className="inline-flex min-w-20 justify-center rounded-full border border-red-200 bg-red-50 px-3 py-1 font-semibold text-red-700">
                          {item.overdueDays} ngày mở cửa
                        </span>
                      </td>
                      <td className="min-w-44 px-4 py-4 align-top">{contactStatus(item)}</td>
                      <td className="px-4 py-4">
                        <div className="flex flex-col items-start gap-2">
                          <Button type="button" size="sm" variant="secondary" className="whitespace-normal text-left"
                            onClick={() => setSelectedLoan({ id: item.loanId, number: item.loanNumber })}>
                            {canRecord ? <PhoneCall size={15} /> : <History size={15} />}
                            {canRecord ? 'Đánh dấu đã liên hệ' : 'Lịch sử liên hệ'}
                          </Button>
                        <Link
                          to={`/loans/${item.loanId}`}
                          className={tableActionClassName('primary')}
                          aria-label={`Xem phiếu ${item.loanNumber}`}
                        >
                          Xem phiếu
                        </Link>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Card>
        </>
      )}
      {selectedLoan && (
        <OverdueContactDialog key={selectedLoan.id} loanId={selectedLoan.id} loanNumber={selectedLoan.number}
          canRecord={canRecord} onClose={() => setSelectedLoan(null)} onRecorded={onRecorded} />
      )}
    </div>
  )
}
