import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import Button from '../../components/ui/Button'
import Card from '../../components/ui/Card'
import FeedbackAlert from '../../components/ui/FeedbackAlert'
import LoadingState from '../../components/ui/LoadingState'
import PageHeader from '../../components/ui/PageHeader'
import TablePagination from '../../components/ui/TablePagination'
import { getCurrentUser } from '../../core/auth/authStorage'
import { getApiErrorMessage } from '../s1-02-user-management/accountService'
import { formatLoanTimestamp } from '../s3-01-loans/loanService'
import { formatVnd } from '../s3-07-returns/ReturnFeeDetails'
import { feeReductionService, type FeeReductionRequestPage } from './feeReductionService'

export default function PendingFeeReductionsPage() {
  const user = getCurrentUser()
  if (user?.role !== 'LIBRARY_MANAGER') return <p role="alert" className="rounded-xl bg-red-50 p-4 text-sm text-red-700">Chỉ Quản lý thư viện được xem danh sách đề nghị miễn giảm đang chờ duyệt.</p>
  return <PendingRequests key={user.id} />
}

function PendingRequests() {
  const [data, setData] = useState<FeeReductionRequestPage | null>(null)
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [revision, setRevision] = useState(0)
  useEffect(() => {
    let active = true
    setLoading(true); setError(''); setData(null)
    feeReductionService.pending(page)
      .then(result => { if (active) setData(result) })
      .catch(err => { if (active) setError(getApiErrorMessage(err, 'Không tải được danh sách đề nghị chờ duyệt.')) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [page, revision])
  return <div className="min-w-0 space-y-5">
    <PageHeader title="Đề nghị miễn giảm phí" description="Danh sách đề nghị Chờ duyệt do Thủ thư gửi, mới nhất ở đầu danh sách." />
    <div className="flex flex-wrap items-center justify-between gap-3"><p className="text-sm text-slate-600">{data ? `${data.totalItems} đề nghị đang chờ duyệt` : loading ? 'Đang tải danh sách đề nghị' : 'Chưa tải được danh sách đề nghị'}</p><Button variant="secondary" disabled={loading} onClick={() => setRevision(value => value + 1)}>Làm mới</Button></div>
    <p role="status" className="rounded-xl border border-blue-200 bg-blue-50 p-4 text-sm leading-6 text-blue-800">Khoản phải thu giữ nguyên trong khi đề nghị chờ duyệt.</p>
    {loading && <div role="status"><LoadingState /></div>}
    {error && <FeedbackAlert tone="error" message={error} onDismiss={() => setError('')} />}
    {!loading && !data && <Button variant="secondary" onClick={() => setRevision(value => value + 1)}>Thử lại</Button>}
    {!loading && data && <>
      {data.items.length === 0 ? <Card className="p-6"><p role="status" className="text-sm text-slate-600">Chưa có đề nghị miễn giảm đang chờ duyệt. Đề nghị mới từ Thủ thư sẽ xuất hiện tại đây.</p></Card>
        : <ol className="space-y-4">{data.items.map(request => <li key={request.id}><Card className="p-5 sm:p-6">
          <div className="flex flex-wrap items-center justify-between gap-3"><h2 className="break-words font-semibold text-slate-900">Đề nghị #{request.id} · {request.readerName}</h2><span className="rounded-full bg-amber-100 px-3 py-1 text-sm font-semibold text-amber-800">Chờ duyệt</span></div>
          <p className="mt-2 break-words text-sm text-slate-600">{request.bookTitle} · Mã vạch: {request.barcode}</p>
          <dl className="mt-4 grid gap-4 text-sm sm:grid-cols-2 lg:grid-cols-3">
            <Field label="Phí gốc" value={formatVnd(request.originalAmountVnd)} />
            <Field label="Đề nghị miễn giảm" value={`${formatVnd(request.requestedAmountVnd)} (${request.requestedAmountVnd === request.originalAmountVnd ? 'Miễn toàn bộ' : 'Giảm một phần'})`} />
            <Field label="Khoản phải thu hiện tại" value={formatVnd(request.outstandingAmountVnd)} />
            <Field label="Người đề nghị" value={request.requesterName} />
            <Field label="Thời điểm gửi" value={formatLoanTimestamp(request.submittedAt)} />
            <Field label="Phiếu mượn" value={request.loanNumber} />
          </dl>
          <dl className="mt-4 text-sm"><Field label="Lý do" value={request.reason} /></dl>
          <Link to={`/overdue-fees/${request.feeId}`} className="mt-4 inline-block font-semibold text-blue-700 hover:underline">Xem chi tiết khoản phí #{request.feeId} →</Link>
        </Card></li>)}</ol>}
      <TablePagination page={data.page + 1} pageSize={data.size} totalItems={data.totalItems} totalPages={data.totalPages} onPageChange={next => setPage(next - 1)} />
    </>}
  </div>
}

function Field({ label, value }: { label: string; value: string }) {
  return <div className="min-w-0"><dt className="text-slate-500">{label}</dt><dd className="mt-1 whitespace-pre-wrap break-words font-semibold text-slate-900">{value}</dd></div>
}
