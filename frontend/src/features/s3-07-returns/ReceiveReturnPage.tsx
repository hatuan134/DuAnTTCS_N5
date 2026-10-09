import { useEffect, useRef, useState } from 'react'
import type { FormEvent, ReactNode } from 'react'
import { Barcode } from 'lucide-react'
import Button from '../../components/ui/Button'
import Card from '../../components/ui/Card'
import EmptyState from '../../components/ui/EmptyState'
import FeedbackAlert from '../../components/ui/FeedbackAlert'
import Input from '../../components/ui/Input'
import PageHeader from '../../components/ui/PageHeader'
import { getCurrentUser } from '../../core/auth/authStorage'
import { getApiErrorMessage } from '../s1-02-user-management/accountService'
import { formatLoanTimestamp, loanRoles } from '../s3-01-loans/loanService'
import { returnService, validateReturnBarcode } from './returnService'
import type { ConfirmReturnResult, ReturnLookup } from './returnService'

type Notice = { message: string; tone: 'success' | 'error' | 'warning' | 'info' }

export default function ReceiveReturnPage() {
  const allowed = loanRoles.includes(getCurrentUser()?.role ?? '')
  const [barcode, setBarcode] = useState('')
  const [fieldError, setFieldError] = useState('')
  const [busy, setBusy] = useState(false)
  const [result, setResult] = useState<ReturnLookup | null>(null)
  const [returned, setReturned] = useState<ConfirmReturnResult | null>(null)
  const [confirming, setConfirming] = useState(false)
  const confirmation = useRef(false)
  const mounted = useRef(false)
  const [notice, setNotice] = useState<Notice | null>(null)
  const request = useRef<AbortController | null>(null)
  useEffect(() => {
    mounted.current = true
    return () => { mounted.current = false; request.current?.abort(); request.current = null }
  }, [])

  function changeBarcode(value: string) {
    if (confirmation.current) return
    setReturned(null)
    request.current?.abort()
    request.current = null
    setBarcode(value); setResult(null); setNotice(null); setFieldError(''); setBusy(false)
  }

  async function lookup(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!allowed || request.current || confirmation.current) return
    setReturned(null)
    setResult(null); setNotice(null)
    const error = validateReturnBarcode(barcode)
    setFieldError(error)
    if (error) { setNotice({ message: error, tone: 'warning' }); return }
    const pending = new AbortController()
    request.current = pending
    setBusy(true)
    try {
      const data = await returnService.lookup(barcode, pending.signal)
      if (request.current !== pending || pending.signal.aborted) return
      setResult(data)
      setNotice({
        message: data.status === 'NOT_BORROWED' || data.status === 'MISSING_DUE_DATE'
          ? data.message : `Đã tìm thấy phiếu mượn ${data.loanNumber}.`,
        tone: data.status === 'NOT_BORROWED' ? 'info' : data.status === 'MISSING_DUE_DATE' ? 'warning' : 'success',
      })
    } catch (error) {
      if (request.current === pending && !pending.signal.aborted) {
        setNotice({ message: getApiErrorMessage(error, 'Không tra cứu được mã vạch. Vui lòng thử lại.'), tone: 'error' })
      }
    } finally {
      if (request.current === pending) { request.current = null; setBusy(false) }
    }
  }

  async function confirmReturn() {
    if (!allowed || confirmation.current || request.current || !result?.itemId || returned) return
    if (!window.confirm(`Xác nhận đã nhận cuốn “${result.bookTitle}” có mã vạch ${result.barcode}?`)) return
    confirmation.current = true
    setConfirming(true); setNotice(null)
    try {
      const data = await returnService.confirm(result.barcode, result.itemId)
      if (!mounted.current) return
      setReturned(data)
      setNotice({ message: data.message, tone: 'success' })
    } catch (error) {
      if (!mounted.current) return
      const response = (error as { response?: { status?: number } })?.response
      // A timeout may follow a committed return. Require a fresh lookup before another write.
      if (!response || response.status === 409) setResult(null)
      setNotice({ message: !response
        ? 'Chưa xác định được kết quả nhận trả. Vui lòng tìm lại phiếu hoặc xem chi tiết phiếu trước khi xác nhận tiếp.'
        : getApiErrorMessage(error, 'Không nhận trả được sách. Vui lòng thử lại.'), tone: 'error' })
    } finally {
      confirmation.current = false
      if (mounted.current) setConfirming(false)
    }
  }

  if (!allowed) return <p role="alert" className="rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700">
    Bạn không có quyền tra cứu nhận trả sách.
  </p>

  return <div className="space-y-5">
    <PageHeader title="Nhận trả sách" description="Nhập mã vạch một bản sao để kiểm tra phiếu mượn đang mở trước khi nhận trả." />
    <Card className="p-5">
      <form noValidate onSubmit={(event) => { void lookup(event) }} className="flex flex-col gap-4 sm:flex-row sm:items-start">
        <div className="min-w-0 flex-1">
          <Input id="return-barcode" label="Mã vạch bản sao" required autoFocus autoComplete="off"
            maxLength={100} disabled={confirming} value={barcode} error={fieldError} startIcon={<Barcode size={18} />}
            placeholder="Nhập hoặc quét mã vạch rồi nhấn Enter"
            onChange={(event) => changeBarcode(event.target.value)} />
        </div>
        <Button type="submit" loading={busy} disabled={confirming} className="shrink-0 sm:mt-7">Tìm phiếu mượn</Button>
      </form>
      <p className="mt-3 text-xs leading-5 text-slate-500">Ngày giờ theo Việt Nam. Mỗi lần xác nhận chỉ nhận trả một cuốn sách.</p>
    </Card>
    {notice && <FeedbackAlert message={notice.message} tone={notice.tone} onDismiss={() => setNotice(null)} />}
    {busy && <p role="status" className="text-sm text-blue-700">Đang tìm phiếu mượn theo mã vạch…</p>}
    {!busy && result && <Card className="p-5">
      <h2 className="text-lg font-semibold text-slate-900">Kết quả tra cứu</h2>
      <dl className="mt-4 grid gap-4 sm:grid-cols-2">
        <Detail label="Tên sách">{result.bookTitle}</Detail>
        <Detail label="Mã vạch">{result.barcode}</Detail>
        {result.itemId !== null ? <>
          <Detail label="Bạn đọc đang mượn">{result.readerName}</Detail>
          <Detail label="Mã phiếu mượn">{result.loanNumber}</Detail>
          <Detail label="Ngày mượn">{formatLoanTimestamp(result.borrowedAt)}</Detail>
          <Detail label="Hạn trả">{formatLoanTimestamp(result.dueAt)}</Detail>
          <Detail label="Trạng thái">
            <span className={`inline-block rounded-lg px-3 py-1 text-sm ${returned ? 'bg-emerald-50 text-emerald-700' : result.status === 'OVERDUE'
              ? 'bg-red-50 text-red-700' : result.status === 'ON_TIME'
                ? 'bg-emerald-50 text-emerald-700' : 'bg-amber-50 text-amber-800'}`}>
              {returned ? 'Đã trả' : result.status === 'OVERDUE' ? `Quá hạn ${result.overdueDays} ngày`
                : result.status === 'ON_TIME' ? 'Đúng hạn' : 'Chưa có hạn trả'}
            </span>
          </Detail>
          {!returned && result.status === 'OVERDUE' && <Detail label="Số ngày trễ">{result.overdueDays} ngày</Detail>}
        </> : <Detail label="Phiếu mượn đang mở">Không có phiếu đang mở cho bản sao này.</Detail>}
      </dl>
      {returned && <dl className="mt-4 grid gap-4 border-t border-slate-200 pt-4 sm:grid-cols-2">
        <Detail label="Ngày trả thực tế"><time dateTime={returned.returnedAt}>{formatLoanTimestamp(returned.returnedAt)}</time></Detail>
        <Detail label="Nhân viên nhận trả">{returned.returnedByName}</Detail>
        <Detail label="Trạng thái bản sao"><span className={returned.copyStatus === 'HELD' ? 'text-amber-800' : 'text-emerald-700'}>
          {returned.copyStatus === 'HELD' ? 'Đang giữ cho đặt trước' : 'Sẵn sàng'}
        </span></Detail>
        <Detail label="Trạng thái phiếu">{returned.loanStatus === 'RETURNED' ? 'Đã trả' : 'Đang mượn · Còn cuốn chưa trả'}</Detail>
        {returned.copyStatus === 'HELD' && <>
          <Detail label="Bạn đọc được giữ sách">{returned.nextReaderName}</Detail>
          <Detail label="Đơn đặt giữ">
            <a href={`/reservations/ready-for-pickup/${returned.nextReservationId}`}
              className="text-blue-700 hover:underline">Đơn #{returned.nextReservationId} · Chờ nhận</a>
          </Detail>
          <Detail label="Bắt đầu giữ bản sao">{formatLoanTimestamp(returned.holdStartedAt)}</Detail>
          <Detail label="Hạn cuối đến nhận">{formatLoanTimestamp(returned.pickupDeadline)}</Detail>
        </>}
      </dl>}
      {result.itemId !== null && !returned && <div className="mt-5">
        <Button type="button" loading={confirming} onClick={() => { void confirmReturn() }}>Xác nhận nhận trả</Button>
        <p className="mt-2 text-xs leading-5 text-slate-500">Chỉ xác nhận khi đã nhận sách. Ngày trả được ghi theo thời điểm xác nhận.</p>
      </div>}
      {returned && <a href={`/loans/${returned.loanId}`} className="mt-5 inline-block text-sm font-semibold text-blue-700 hover:underline">Xem chi tiết phiếu mượn</a>}
      {result.itemId !== null && !returned && <p className="mt-5 text-xs leading-5 text-slate-500">
        Số ngày trễ tính theo ngày lịch, gồm ngày thư viện đóng cửa, từ hạn trả đã lưu đến ngày tra cứu theo giờ Việt Nam.
      </p>}
    </Card>}
    {!busy && !result && <EmptyState title="Chưa có thông tin phiếu mượn" description="Nhập mã vạch bản sao và chọn Tìm phiếu mượn để tra cứu." />}
  </div>
}

function Detail({ label, children }: { label: string; children: ReactNode }) {
  return <div className="min-w-0"><dt className="text-sm text-slate-500">{label}</dt>
    <dd className="mt-1 break-words font-semibold text-slate-900">{children}</dd></div>
}
