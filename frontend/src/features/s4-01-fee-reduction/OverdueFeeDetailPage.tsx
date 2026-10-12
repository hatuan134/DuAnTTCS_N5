import { useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react'
import { Link, useParams } from 'react-router-dom'
import Button from '../../components/ui/Button'
import Card from '../../components/ui/Card'
import FeedbackAlert from '../../components/ui/FeedbackAlert'
import LoadingState from '../../components/ui/LoadingState'
import PageHeader from '../../components/ui/PageHeader'
import { getCurrentUser } from '../../core/auth/authStorage'
import { getApiErrorMessage } from '../s1-02-user-management/accountService'
import { formatLoanTimestamp, loanRoles } from '../s3-01-loans/loanService'
import { formatVnd } from '../s3-07-returns/ReturnFeeDetails'
import { feeReductionService, type OverdueFeeDetail } from './feeReductionService'

export default function OverdueFeeDetailPage() {
  const { feeId } = useParams()
  const id = Number(feeId)
  const user = getCurrentUser()
  if (!loanRoles.includes(user?.role ?? '')) return <p role="alert" className="rounded-xl bg-red-50 p-4 text-sm text-red-700">Bạn không có quyền xem khoản phí trễ hạn.</p>
  if (!Number.isSafeInteger(id) || id < 1) return <p role="alert" className="rounded-xl bg-red-50 p-4 text-sm text-red-700">Mã khoản phí không hợp lệ.</p>
  return <FeeDetails key={`${user?.id}:${id}`} id={id} librarian={user?.role === 'LIBRARIAN'} />
}

function FeeDetails({ id, librarian }: { id: number; librarian: boolean }) {
  const [fee, setFee] = useState<OverdueFeeDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [revision, setRevision] = useState(0)
  const [mode, setMode] = useState<'PARTIAL' | 'FULL'>('PARTIAL')
  const [amount, setAmount] = useState('')
  const [reason, setReason] = useState('')
  const [saving, setSaving] = useState(false)
  const submitLock = useRef(false)
  useEffect(() => {
    let active = true
    setLoading(true); setError(''); setFee(null)
    feeReductionService.detail(id)
      .then(data => { if (active) setFee(data) })
      .catch(err => { if (active) setError(getApiErrorMessage(err, 'Không tải được khoản phí trễ hạn.')) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [id, revision])

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (!fee || submitLock.current || fee.pendingRequest) return
    setError(''); setNotice('')
    const value = mode === 'FULL' ? fee.originalAmountVnd : Number(amount)
    if (!Number.isSafeInteger(value) || value <= 0 || value > fee.originalAmountVnd) {
      setError('Số tiền đề nghị phải là số nguyên VND, lớn hơn 0 và không vượt quá phí gốc.'); return
    }
    if (!reason.trim() || reason.length > 1000) {
      setError('Vui lòng nhập lý do có nội dung, tối đa 1000 ký tự.'); return
    }
    submitLock.current = true; setSaving(true)
    try {
      const request = await feeReductionService.create(id, value, reason.trim())
      setFee(current => current ? { ...current, pendingRequest: request, outstandingAmountVnd: request.outstandingAmountVnd } : current)
      setNotice('Đã gửi đề nghị miễn giảm. Trạng thái: Chờ duyệt. Khoản phải thu giữ nguyên.')
      setReason(''); setAmount('')
    } catch (err) {
      setError(getApiErrorMessage(err, 'Không gửi được đề nghị miễn giảm. Vui lòng thử lại.'))
      // Refresh payment/pending state after a conflict or a response lost after saving.
      try { setFee(await feeReductionService.detail(id)) } catch { /* Keep the visible error and allow retry. */ }
    } finally { submitLock.current = false; setSaving(false) }
  }

  return <div className="min-w-0 space-y-5">
    <Link to={fee ? `/loans/${fee.loanId}` : '/loans'} className="inline-block text-sm font-medium text-blue-700 hover:underline">← {fee ? 'Chi tiết phiếu mượn' : 'Danh sách phiếu mượn'}</Link>
    <PageHeader title="Chi tiết khoản phí trễ hạn" description="Xem khoản phải thu và đề nghị miễn giảm có lý do." />
    {notice && <FeedbackAlert tone="success" message={notice} onDismiss={() => setNotice('')} />}
    {error && <FeedbackAlert tone="error" message={error} onDismiss={() => setError('')} />}
    {loading && <div role="status"><LoadingState /></div>}
    {!loading && !fee && <Card className="p-5"><p className="text-sm text-slate-600">Chưa tải được thông tin khoản phí.</p><Button className="mt-3" variant="secondary" onClick={() => setRevision(value => value + 1)}>Thử lại</Button></Card>}
    {!loading && fee && <>
      <Card className="p-5 sm:p-6">
        <h2 className="text-lg font-semibold text-slate-900">Khoản phí #{fee.id}</h2>
        <dl className="mt-4 grid gap-4 text-sm sm:grid-cols-2 lg:grid-cols-3">
          <Field label="Phiếu mượn"><Link to={`/loans/${fee.loanId}`} className="text-blue-700 hover:underline">{fee.loanNumber}</Link></Field>
          <Field label="Bạn đọc">{fee.readerName}</Field>
          <Field label="Đầu sách">{fee.bookTitle}</Field>
          <Field label="Mã vạch">{fee.barcode}</Field>
          <Field label="Thời điểm nhận trả">{formatLoanTimestamp(fee.returnedAt)}</Field>
          <Field label="Số tiền phí gốc">{formatVnd(fee.originalAmountVnd)}</Field>
          <Field label="Đã thu">{formatVnd(fee.paidAmountVnd)}</Field>
          <Field label="Khoản phải thu hiện tại"><span className="text-red-700">{formatVnd(fee.outstandingAmountVnd)}</span></Field>
        </dl>
      </Card>
      {fee.pendingRequest ? <Card className="p-5 sm:p-6">
        <div className="flex flex-wrap items-center gap-3"><h2 className="text-lg font-semibold text-slate-900">Đề nghị miễn giảm #{fee.pendingRequest.id}</h2><span className="rounded-full bg-amber-100 px-3 py-1 text-sm font-semibold text-amber-800">Chờ duyệt</span></div>
        <dl className="mt-4 grid gap-4 text-sm sm:grid-cols-2">
          <Field label="Hình thức">{fee.pendingRequest.requestedAmountVnd === fee.pendingRequest.originalAmountVnd ? 'Miễn toàn bộ' : 'Giảm một phần'}</Field>
          <Field label="Số tiền đề nghị miễn giảm">{formatVnd(fee.pendingRequest.requestedAmountVnd)}</Field>
          <Field label="Người đề nghị">{fee.pendingRequest.requesterName}</Field>
          <Field label="Thời điểm gửi">{formatLoanTimestamp(fee.pendingRequest.submittedAt)}</Field>
        </dl>
        <dl className="mt-4 text-sm"><Field label="Lý do"><span className="whitespace-pre-wrap">{fee.pendingRequest.reason}</span></Field></dl>
        <p className="mt-4 rounded-xl bg-amber-50 p-3 text-sm leading-6 text-amber-900">Đề nghị đang chờ Quản lý thư viện xử lý. Khoản phải thu vẫn là {formatVnd(fee.outstandingAmountVnd)}. Chưa thể gửi thêm đề nghị cho khoản phí này.</p>
      </Card> : librarian && fee.originalAmountVnd > 0 && fee.paidAmountVnd === 0 ? <Card className="p-5 sm:p-6">
        <h2 className="text-lg font-semibold text-slate-900">Gửi đề nghị miễn giảm</h2>
        <p className="mt-2 text-sm leading-6 text-slate-600">Chọn miễn toàn bộ hoặc nhập số tiền giảm một phần. Gửi đề nghị sẽ giữ nguyên khoản phải thu trong khi chờ duyệt.</p>
        <form className="mt-5 space-y-5" onSubmit={submit}>
          <fieldset disabled={saving} className="space-y-5 disabled:opacity-60">
            <legend className="mb-2 text-sm font-semibold text-slate-800">Hình thức đề nghị</legend>
            <div className="flex flex-col gap-3 sm:flex-row sm:gap-6">
              <label className="flex min-h-10 items-center gap-2 text-sm"><input type="radio" name="reduction-mode" checked={mode === 'PARTIAL'} onChange={() => setMode('PARTIAL')} />Giảm một phần</label>
              <label className="flex min-h-10 items-center gap-2 text-sm"><input type="radio" name="reduction-mode" checked={mode === 'FULL'} onChange={() => setMode('FULL')} />Miễn toàn bộ</label>
            </div>
            <div className="max-w-sm"><label htmlFor="reduction-amount" className="mb-2 block text-sm font-semibold text-slate-800">Số tiền đề nghị miễn giảm (VND) <span className="text-red-700">*</span></label>
              <input id="reduction-amount" type="number" inputMode="numeric" min="1" max={fee.originalAmountVnd} step="1" required readOnly={mode === 'FULL'} value={mode === 'FULL' ? fee.originalAmountVnd : amount} onChange={event => setAmount(event.target.value)} aria-describedby="reduction-amount-help" className="w-full rounded-xl border border-slate-300 bg-white px-3 py-2.5 text-sm read-only:bg-slate-100 focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-100" placeholder="Ví dụ: 10000" />
              <p id="reduction-amount-help" className="mt-2 text-xs leading-5 text-slate-600">Lớn hơn 0 và tối đa {formatVnd(fee.originalAmountVnd)}. Nhập số nguyên, không làm tròn số tiền đề nghị.</p>
            </div>
            <div><label htmlFor="reduction-reason" className="mb-2 block text-sm font-semibold text-slate-800">Lý do đề nghị <span className="text-red-700">*</span></label>
              <textarea id="reduction-reason" required maxLength={1000} rows={4} value={reason} onChange={event => setReason(event.target.value)} className="w-full resize-y rounded-xl border border-slate-300 px-3 py-2.5 text-sm focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-100" placeholder="Ví dụ: Bạn đọc có hoàn cảnh khó khăn" />
              <p className="mt-1 text-right text-xs text-slate-500">{reason.length}/1000 ký tự</p>
            </div>
          </fieldset>
          <div className="flex flex-wrap gap-3"><Button type="submit" loading={saving}>{saving ? 'Đang gửi…' : 'Gửi đề nghị miễn giảm'}</Button><Button type="button" variant="secondary" disabled={saving} onClick={() => { setAmount(''); setReason(''); setMode('PARTIAL'); setError('') }}>Xóa nội dung nhập</Button></div>
        </form>
      </Card> : <p role="status" className="rounded-xl border border-blue-200 bg-blue-50 p-4 text-sm leading-6 text-blue-800">{librarian ? 'Khoản phí đã thu tiền nên không còn được gửi đề nghị miễn giảm.' : 'Chỉ Thủ thư được gửi đề nghị miễn giảm. Quản lý thư viện xem đề nghị trong danh sách chờ.'}</p>}
    </>}
  </div>
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return <div className="min-w-0"><dt className="text-slate-500">{label}</dt><dd className="mt-1 break-words font-semibold text-slate-900">{children}</dd></div>
}
