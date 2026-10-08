import { useEffect, useState } from 'react'
import { Link, useLocation, useParams } from 'react-router-dom'
import Button from '../../components/ui/Button'
import Card from '../../components/ui/Card'
import FeedbackAlert from '../../components/ui/FeedbackAlert'
import LoadingState from '../../components/ui/LoadingState'
import PageHeader from '../../components/ui/PageHeader'
import { getCurrentUser } from '../../core/auth/authStorage'
import { getApiErrorMessage } from '../s1-02-user-management/accountService'
import { formatLoanTimestamp, loanRoles, loanService } from './loanService'
import type { LoanDetail } from './loanService'

export default function LoanDetailPage() {
  const { loanId } = useParams()
  const id = Number(loanId)
  const location = useLocation()
  const user = getCurrentUser()
  const reader = user?.role === 'READER'
  const allowed = reader || loanRoles.includes(user?.role ?? '')
  if (!allowed) return <p role="alert" className="rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700">
    Bạn không có quyền xem phiếu mượn.
  </p>
  if (!Number.isSafeInteger(id) || id < 1) return <div>
    <Link to={reader ? "/my-borrowed-books" : "/loans"} className="text-blue-700 hover:underline">← {reader ? 'Sách của tôi' : 'Danh sách phiếu mượn'}</Link>
    <p role="alert" className="mt-4 rounded-lg bg-red-50 p-4 text-red-700">Mã phiếu mượn không hợp lệ.</p>
  </div>
  return <LoanDetails key={`${user?.id}:${id}`} id={id} viewerId={user?.id} reader={reader} justCreated={!reader && location.state?.loanCreated === true} />
}

function LoanDetails({ id, viewerId, reader, justCreated }: { id: number; viewerId: number | undefined; reader: boolean; justCreated: boolean }) {
  const [loan, setLoan] = useState<LoanDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [failed, setFailed] = useState(false)
  const [unavailable, setUnavailable] = useState(false)
  const [revision, setRevision] = useState(0)
  const [showCreatedNotice, setShowCreatedNotice] = useState(justCreated)
  useEffect(() => {
    let active = true
    setLoan(null)
    setLoading(true)
    setError(''); setFailed(false); setUnavailable(false)
    loanService.detail(id)
      .then((data) => { if (active) setLoan(data) })
      .catch((e: unknown) => {
        if (!active) return
        const status = (e as { response?: { status?: number } })?.response?.status
        const denied = status === 403 || status === 404
        setFailed(true); setUnavailable(denied)
        setError(denied ? 'Phiếu không tồn tại hoặc bạn không có quyền truy cập.'
          : getApiErrorMessage(e, 'Không tải được phiếu mượn. Vui lòng thử lại.'))
      })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [id, viewerId, reader, revision])
  return <div>
    <Link to={reader ? "/my-borrowed-books" : "/loans"} className="mb-4 inline-block text-sm font-medium text-blue-700 hover:underline">← {reader ? 'Sách của tôi' : 'Danh sách phiếu mượn'}</Link>
    <PageHeader title="Chi tiết phiếu mượn" description="Thông tin phiếu và hạn trả đã được lưu. Thời gian hiển thị theo giờ Việt Nam." />
    {showCreatedNotice && <FeedbackAlert className="mb-4" tone="success"
      message="Đã lập phiếu mượn thành công. Kiểm tra thông tin phiếu bên dưới."
      onDismiss={() => setShowCreatedNotice(false)} />}
    {loading && <div role="status"><LoadingState /></div>}
    {error && <FeedbackAlert className="mb-4" tone="error" message={error} onDismiss={() => setError('')} />}
    {!loading && failed && <div role="status" className="rounded-lg bg-slate-50 p-4 text-sm text-slate-700">
      <p>{unavailable ? 'Không thể mở phiếu mượn này.' : 'Chưa tải được phiếu mượn.'}</p>
      {!unavailable && <Button type="button" variant="secondary" className="mt-3" onClick={() => setRevision((value) => value + 1)}>Thử lại</Button>}
    </div>}
    {!loading && !failed && loan && loan.id === id && (!reader || loan.readerId === viewerId) && <div className="space-y-5">
      <Card className="p-5 sm:p-6">
        <h3 className="text-lg font-semibold text-slate-900">Thông tin phiếu</h3>
        <dl className="mt-4 grid gap-5 text-sm sm:grid-cols-2 lg:grid-cols-3">
          <div className="min-w-0"><dt className="text-slate-500">Mã phiếu mượn</dt><dd className="mt-1 break-all font-mono font-semibold text-slate-900">{loan.loanNumber}</dd></div>
          <div className="min-w-0"><dt className="text-slate-500">Bạn đọc</dt><dd className="mt-1 break-words font-semibold text-slate-900">{loan.readerName}</dd></div>
          <div className="min-w-0"><dt className="text-slate-500">Người lập phiếu</dt><dd className="mt-1 break-words font-semibold text-slate-900">{loan.createdByName}</dd></div>
          <div><dt className="text-slate-500">Ngày mượn</dt><dd className="mt-1 font-semibold text-slate-900">{formatLoanTimestamp(loan.borrowedAt, true)}</dd></div>
          <div><dt className="text-slate-500">Thời điểm mượn</dt><dd className="mt-1 text-slate-900"><time dateTime={loan.borrowedAt}>{formatLoanTimestamp(loan.borrowedAt)}</time></dd></div>
          {!reader && loan.reservationId && <div><dt className="text-slate-500">Đơn đặt giữ</dt><dd className="mt-1"><Link to={`/reservations/ready-for-pickup/${loan.reservationId}`} className="font-medium text-blue-700 hover:underline">Xem đơn #{loan.reservationId}</Link></dd></div>}
        </dl>
      </Card>
      <Card className="p-5 sm:p-6">
        <h3 className="text-lg font-semibold text-slate-900">Sách trong phiếu mượn</h3>
        {loan.items.length === 0 ? <p role="status" className="mt-4 rounded-lg bg-amber-50 p-3 text-sm text-amber-900">Phiếu này chưa có chi tiết bản sao được mượn.</p>
          : <ol className="mt-4 space-y-4">
            {loan.items.map((item, index) => <li key={item.id} className="rounded-xl border border-slate-200 p-4">
              <h4 className="break-words font-semibold text-slate-900">{index + 1}. <Link to={reader ? `/catalog/books/${item.bookId}` : `/books/${item.bookId}`} className="text-blue-700 hover:underline">{item.bookTitle}</Link></h4>
              <dl className="mt-4 grid gap-4 text-sm sm:grid-cols-2">
                <div className="min-w-0"><dt className="text-slate-500">Mã vạch bản sao</dt><dd className="mt-1 break-all font-mono font-semibold">{reader ? item.barcode : <Link to={`/book-copies/${item.copyId}`} className="text-blue-700 hover:underline">{item.barcode}</Link>}</dd></div>
                <div><dt className="text-slate-500">Tên đầu sách</dt><dd className="mt-1 break-words font-medium text-slate-900">{item.bookTitle}</dd></div>
                <div><dt className="text-slate-500">Ngày mượn bản sao</dt><dd className="mt-1 font-medium text-slate-900">{formatLoanTimestamp(item.borrowedAt, true)}</dd></div>
                <div><dt className="text-slate-500">Hạn trả đã lưu</dt><dd className="mt-1 font-semibold text-blue-900">{item.dueAt ? <time dateTime={item.dueAt}>{formatLoanTimestamp(item.dueAt)}</time> : 'Phiếu cũ chưa có hạn trả được lưu.'}</dd></div>
              </dl>
            </li>)}
          </ol>}
      </Card>
    </div>}
  </div>
}
