import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ArrowLeft, Filter, RefreshCw, X } from 'lucide-react'
import Button from '../../components/ui/Button'
import Card from '../../components/ui/Card'
import EmptyState from '../../components/ui/EmptyState'
import FeedbackAlert from '../../components/ui/FeedbackAlert'
import Input from '../../components/ui/Input'
import LoadingState from '../../components/ui/LoadingState'
import PageHeader from '../../components/ui/PageHeader'
import StatusBadge from '../../components/ui/StatusBadge'
import TablePagination from '../../components/ui/TablePagination'
import useTablePagination from '../../hooks/useTablePagination'
import { getApiErrorMessage } from '../s1-02-user-management/accountService'
import { formatLoanTimestamp } from '../s3-01-loans/loanService'
import { readerService, validateReaderHistoryDates } from './readerService'
import type { ReaderHistoryDateError, ReaderHistoryFilters, ReaderLoanHistoryResponse } from './readerService'

export default function ReaderProfilePage() {
  const { readerId } = useParams()
  const id = Number(readerId)
  if (!Number.isSafeInteger(id) || id < 1) return <div className="space-y-4">
    <BackLink />
    <p role="alert" className="rounded-xl bg-red-50 p-4 text-sm text-red-700">Mã bạn đọc không hợp lệ.</p>
  </div>
  return <ReaderProfile key={id} id={id} />
}

function BackLink() {
  return <Link to="/readers" className="inline-flex items-center gap-2 text-sm font-medium text-blue-700 hover:underline">
    <ArrowLeft size={16} /> Danh sách Bạn đọc
  </Link>
}

export function ReaderProfile({ id }: { id: number }) {
  const [data, setData] = useState<ReaderLoanHistoryResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [failed, setFailed] = useState(false)
  const [unavailable, setUnavailable] = useState(false)
  const [error, setError] = useState('')
  const [revision, setRevision] = useState(0)
  const [draftFilters, setDraftFilters] = useState<ReaderHistoryFilters>({ fromDate: '', toDate: '' })
  const [appliedFilters, setAppliedFilters] = useState<ReaderHistoryFilters>({ fromDate: '', toDate: '' })
  const [dateError, setDateError] = useState<ReaderHistoryDateError | null>(null)
  const [filterNotice, setFilterNotice] = useState('')
  const requestPending = useRef(true)
  const hasFilter = !!(appliedFilters.fromDate || appliedFilters.toDate)
  const pagination = useTablePagination(data?.loans ?? [], `${id}|${revision}`)

  useEffect(() => {
    let active = true
    requestPending.current = true
    setLoading(true); setData(null); setFailed(false); setUnavailable(false); setError('')
    readerService.getLoanHistory(id, appliedFilters)
      .then((result) => {
        if (!active) return
        if (result.profile.userId !== id) throw new Error('Hồ sơ trả về không khớp Bạn đọc đang xem.')
        setData(result)
      })
      .catch((e: unknown) => {
        if (!active) return
        const status = (e as { response?: { status?: number } })?.response?.status
        const denied = status === 403 || status === 404
        setFailed(true); setUnavailable(denied)
        setError(denied ? 'Hồ sơ không tồn tại hoặc bạn không có quyền truy cập.'
          : getApiErrorMessage(e, 'Không tải được hồ sơ Bạn đọc. Vui lòng thử lại.'))
      })
      .finally(() => { if (active) { requestPending.current = false; setLoading(false) } })
    return () => { active = false }
  }, [id, revision, appliedFilters.fromDate, appliedFilters.toDate])

  function applyFilters(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (loading || requestPending.current) return
    const invalid = validateReaderHistoryDates(draftFilters)
    setDateError(invalid)
    if (invalid) { setFilterNotice(invalid.message); return }
    requestPending.current = true
    setLoading(true); setFilterNotice('')
    setAppliedFilters({ fromDate: draftFilters.fromDate.trim(), toDate: draftFilters.toDate.trim() })
    setRevision((value) => value + 1)
  }

  function reloadHistory() {
    if (loading || requestPending.current) return
    requestPending.current = true
    setLoading(true)
    setRevision((value) => value + 1)
  }

  function clearFilters() {
    if (loading || requestPending.current) return
    requestPending.current = true
    setLoading(true); setDateError(null); setFilterNotice('')
    setDraftFilters({ fromDate: '', toDate: '' })
    setAppliedFilters({ fromDate: '', toDate: '' })
    setRevision((value) => value + 1)
  }

  function changeDate(field: keyof ReaderHistoryFilters, value: string) {
    setDraftFilters((current) => ({ ...current, [field]: value }))
    setDateError(null); setFilterNotice('')
  }

  return <div className="space-y-5">
    <BackLink />
    <PageHeader title="Hồ sơ tổng hợp Bạn đọc"
      description="Thông tin hồ sơ và toàn bộ lịch sử mượn trả. Thời gian hiển thị theo giờ Việt Nam."
      action={<Button type="button" variant="secondary" loading={loading}
        onClick={reloadHistory}><RefreshCw size={16} /> Làm mới</Button>} />
    {!unavailable && <Card className="p-5 sm:p-6">
      <h3 className="mb-4 font-semibold text-slate-900">Lọc lịch sử mượn trả</h3>
      <form noValidate onSubmit={applyFilters} className="grid gap-4 sm:grid-cols-2 xl:grid-cols-[minmax(0,1fr)_minmax(0,1fr)_auto]">
        <div className="min-w-0"><Input id="reader-history-from-date" label="Từ ngày mượn" type="date"
          min="0001-01-01" max="9999-12-31" value={draftFilters.fromDate} disabled={loading}
          error={dateError?.field === 'fromDate' ? dateError.message : undefined}
          onChange={(event) => changeDate('fromDate', event.target.value)} /></div>
        <div className="min-w-0"><Input id="reader-history-to-date" label="Đến ngày mượn" type="date"
          min="0001-01-01" max="9999-12-31" value={draftFilters.toDate} disabled={loading}
          error={dateError?.field === 'toDate' ? dateError.message : undefined}
          onChange={(event) => changeDate('toDate', event.target.value)} /></div>
        <div className="flex flex-wrap items-start gap-2 sm:col-span-2 xl:col-span-1 xl:pt-7">
          <Button type="submit" disabled={loading}><Filter size={16} /> Áp dụng bộ lọc</Button>
          <Button type="button" variant="secondary"
            disabled={loading || (!draftFilters.fromDate && !draftFilters.toDate && !hasFilter)} onClick={clearFilters}>
            <X size={16} /> Xóa bộ lọc
          </Button>
        </div>
      </form>
      <p className="mt-3 text-xs leading-5 text-slate-500">Có thể để trống một hoặc cả hai ngày. Khoảng lọc bao gồm cả ngày đầu và ngày cuối theo giờ Việt Nam.
        Ba chỉ số tổng hợp luôn tính trên toàn bộ lịch sử.</p>
      {filterNotice && <FeedbackAlert message={filterNotice} tone="warning" onDismiss={() => setFilterNotice('')} className="mt-3" />}
    </Card>}
    {error && <FeedbackAlert message={error} tone="error" onDismiss={() => setError('')} />}
    {loading && <div role="status"><LoadingState /></div>}
    {!loading && failed && <div role="status"><Card className="p-5">
      <p className="text-sm text-slate-700">{unavailable ? 'Không thể mở hồ sơ này.' : 'Chưa tải được hồ sơ và lịch sử mượn trả.'}</p>
      {!unavailable && <Button type="button" variant="secondary" className="mt-3"
        onClick={reloadHistory}>Thử lại</Button>}
    </Card></div>}
    {!loading && !failed && data && <>
      <Card className="p-5 sm:p-6">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h3 className="min-w-0 break-words text-lg font-semibold text-slate-900">{data.profile.fullName}</h3>
          <StatusBadge status={data.profile.userStatus} />
        </div>
        <dl className="mt-5 grid gap-4 text-sm sm:grid-cols-2 lg:grid-cols-3">
          <Field label="Mã Bạn đọc" value={data.profile.memberCode} />
          <Field label="Email" value={data.profile.email} />
          <Field label="Số điện thoại" value={data.profile.phone || 'Chưa có'} />
          <Field label="Ngày sinh" value={formatProfileDate(data.profile.dateOfBirth)} />
          <Field label="Địa chỉ" value={data.profile.address || 'Chưa có'} />
          <Field label="Loại thẻ" value={data.profile.cardTypeName || 'Chưa cấp thẻ'} />
          <div><dt className="text-slate-500">Trạng thái hồ sơ</dt><dd className="mt-1"><StatusBadge status={data.profile.registrationStatus} /></dd></div>
          <Field label="Ngày đăng ký" value={formatLoanTimestamp(data.profile.submittedAt, true)} />
          {data.profile.rejectionReason && <Field label="Lý do từ chối hồ sơ" value={data.profile.rejectionReason} />}
        </dl>
      </Card>
      <section aria-label="Tổng quan mượn trả" className="grid gap-4 sm:grid-cols-3">
        <Summary label="Phiếu đang mở" value={data.openLoanCount} />
        <Summary label="Tổng lượt đã mượn" value={data.totalBorrowCount} />
        <Summary label="Lượt từng trả trễ" value={data.lateReturnCount} />
      </section>
      <p className="text-xs text-slate-500">Số liệu tổng hợp của toàn bộ lịch sử, không thay đổi theo khoảng ngày đang lọc.</p>
      <section aria-labelledby="reader-history-heading" className="space-y-4">
        <div>
          <h3 id="reader-history-heading" className="text-lg font-semibold text-slate-900">Lịch sử mượn trả</h3>
          <p className="mt-1 text-sm leading-6 text-slate-500">Phiếu mới nhất trước. Mỗi phiếu tính một lượt mượn;
            phiếu có ít nhất một bản sao trả sau ngày đến hạn tính một lượt từng trả trễ.
            Phiếu còn bản sao chưa trả được tính là đang mở.</p>
        </div>
        <p role="status" aria-live="polite" className="rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm text-slate-700">
          {hasFilter ? `Tìm thấy ${data.loans.length} phiếu mượn` : `Toàn bộ lịch sử: ${data.loans.length} phiếu mượn`}
          {hasFilter && <span className="mt-1 block text-xs text-slate-500">
            {appliedFilters.fromDate ? `Từ ${formatProfileDate(appliedFilters.fromDate)}` : 'Không giới hạn ngày bắt đầu'}
            {' · '}{appliedFilters.toDate ? `Đến ${formatProfileDate(appliedFilters.toDate)}` : 'Không giới hạn ngày kết thúc'}
          </span>}
        </p>
        {data.loans.length === 0 ? <EmptyState
          title={hasFilter && data.totalBorrowCount > 0 ? 'Không có phiếu mượn trong khoảng ngày đã chọn' : 'Bạn đọc chưa từng mượn sách'}
          description={hasFilter && data.totalBorrowCount > 0
            ? 'Thử khoảng ngày khác hoặc xóa bộ lọc để xem toàn bộ lịch sử.'
            : 'Lịch sử sẽ xuất hiện khi Bạn đọc có phiếu mượn.'} /> : <>
          <ol className="space-y-4" aria-label="Danh sách phiếu mượn mới nhất trước">
            {pagination.pageItems.map((loan) => <li key={loan.id}>
              <Card className={`overflow-hidden ${loan.returnedLate ? 'border-amber-300' : ''}`}>
                <div className="flex flex-wrap items-start justify-between gap-3 border-b border-slate-200 bg-slate-50/60 p-4 sm:p-5">
                  <div className="min-w-0">
                    <Link to={`/loans/${loan.id}`} className="break-all font-mono font-semibold text-blue-700 hover:underline"
                      aria-label={`Xem phiếu ${loan.loanNumber}`}>{loan.loanNumber}</Link>
                    <p className="mt-1 text-sm text-slate-500">Ngày mượn: {formatLoanTimestamp(loan.borrowedAt)}</p>
                  </div>
                  <div className="flex flex-wrap gap-2">
                    <StatusBadge status={loan.status === 'PARTIALLY_RETURNED' ? 'BORROWED' : loan.status}
                      label={loan.status === 'PARTIALLY_RETURNED' ? 'Đã trả một phần'
                        : loan.status === 'EMPTY' ? 'Chưa có bản sao' : undefined} />
                    {loan.returnedLate && <StatusBadge status="OVERDUE" label="Từng trả trễ" />}
                  </div>
                </div>
                {loan.items.length === 0 ? <p className="p-5 text-sm text-slate-500">Phiếu cũ chưa có chi tiết bản sao.</p>
                  : <ul className="divide-y divide-slate-100">
                    {loan.items.map((item) => <li key={item.id} className="p-4 sm:p-5">
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <h4 className="min-w-0 break-words font-semibold text-slate-900">{item.bookTitle}</h4>
                        <div className="flex flex-wrap gap-2">
                          <StatusBadge status={item.status} />
                          {item.returnedLate && <StatusBadge status="OVERDUE" label="Trả trễ" />}
                        </div>
                      </div>
                      <dl className="mt-4 grid gap-4 text-sm sm:grid-cols-2 xl:grid-cols-4">
                        <Field label="Mã vạch" value={item.barcode} />
                        <Field label="Ngày mượn" value={formatLoanTimestamp(item.borrowedAt)} />
                        <Field label="Hạn trả" value={item.dueAt ? formatLoanTimestamp(item.dueAt, true) : 'Phiếu cũ chưa lưu hạn trả'} />
                        <Field label="Ngày trả thực tế" value={item.returnedAt ? formatLoanTimestamp(item.returnedAt) : 'Chưa trả'} />
                      </dl>
                    </li>)}
                  </ul>}
              </Card>
            </li>)}
          </ol>
          <TablePagination page={pagination.page} totalItems={pagination.totalItems} totalPages={pagination.totalPages}
            pageSize={pagination.pageSize} onPageChange={pagination.goToPage} />
        </>}
      </section>
    </>}
  </div>
}

function Field({ label, value }: { label: string; value: string }) {
  return <div className="min-w-0"><dt className="text-slate-500">{label}</dt><dd className="mt-1 break-words font-medium text-slate-900">{value}</dd></div>
}

function Summary({ label, value }: { label: string; value: number }) {
  return <Card className="p-5"><h3 className="text-sm font-medium text-slate-500">{label}</h3>
    <p className="mt-2 text-3xl font-bold text-slate-900">{value}</p></Card>
}

function formatProfileDate(value: string) {
  return new Date(`${value}T00:00:00+07:00`).toLocaleDateString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' })
}
