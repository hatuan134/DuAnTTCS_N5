import { useEffect, useRef, useState } from 'react'
import type { FormEvent, ReactNode } from 'react'
import { Barcode } from 'lucide-react'
import { Link } from 'react-router-dom'
import Button from '../../components/ui/Button'
import Card from '../../components/ui/Card'
import EmptyState from '../../components/ui/EmptyState'
import FeedbackAlert from '../../components/ui/FeedbackAlert'
import Input from '../../components/ui/Input'
import PageHeader from '../../components/ui/PageHeader'
import { getCurrentUser } from '../../core/auth/authStorage'
import { getApiErrorMessage } from '../s1-02-user-management/accountService'
import { formatLoanTimestamp, loanRoles } from '../s3-01-loans/loanService'
import { hasReceivedCopy, recordReturnResult, returnService, returnSessionSuccess, validateReturnBarcode } from './returnService'
import type { ReturnLookup, ReturnSessionEntry } from './returnService'

type Notice = { message: string; tone: 'success' | 'error' | 'warning' | 'info' }

export default function ReceiveReturnPage() {
  const allowed = loanRoles.includes(getCurrentUser()?.role ?? '')
  const [barcode, setBarcode] = useState('')
  const [fieldError, setFieldError] = useState('')
  const [busy, setBusy] = useState(false)
  const [result, setResult] = useState<ReturnLookup | null>(null)
  const [entries, setEntries] = useState<ReturnSessionEntry[]>([])
  const session = useRef<ReturnSessionEntry[]>([])
  const [confirming, setConfirming] = useState(false)
  const confirmation = useRef(false)
  const mounted = useRef(false)
  const form = useRef<HTMLFormElement | null>(null)
  const [notice, setNotice] = useState<Notice | null>(null)
  const request = useRef<AbortController | null>(null)
  useEffect(() => {
    mounted.current = true
    return () => { mounted.current = false; request.current?.abort(); request.current = null }
  }, [])

  function focusBarcode(select = false) {
    const input = form.current?.querySelector<HTMLInputElement>('#return-barcode')
    input?.focus({ preventScroll: true })
    if (select) input?.select()
  }
  // Focus after React has enabled the input again following confirmation.
  useEffect(() => { if (!confirming) focusBarcode() }, [confirming])

  function record(entry: ReturnSessionEntry) {
    session.current = recordReturnResult(session.current, entry)
    setEntries(session.current)
  }
  function recordFailure(code: string, message: string, preview: ReturnLookup | null = null) {
    record({ status: 'ERROR', barcode: code.trim(), message,
      bookTitle: preview?.bookTitle ?? null, readerName: preview?.readerName ?? null,
      dueAt: preview?.dueAt ?? null })
  }
  function duplicate(code: string, copyId?: number): boolean {
    if (!hasReceivedCopy(session.current, code, copyId)) return false
    setNotice({ message: `Mã vạch ${code.trim()} đã được nhận trả trong lượt này.`, tone: 'warning' })
    setResult(null); focusBarcode(true)
    return true
  }
  function changeBarcode(value: string) {
    if (confirmation.current) return
    request.current?.abort()
    request.current = null
    setBarcode(value); setResult(null); setNotice(null); setFieldError(''); setBusy(false)
  }

  async function lookup(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!allowed || request.current || confirmation.current) return
    setResult(null); setNotice(null)
    const code = barcode.trim()
    const error = validateReturnBarcode(code)
    setFieldError(error)
    if (error) { setNotice({ message: error, tone: 'warning' }); focusBarcode(true); return }
    if (duplicate(code)) return
    const pending = new AbortController()
    request.current = pending
    setBusy(true)
    try {
      const data = await returnService.lookup(code, pending.signal)
      if (!mounted.current || request.current !== pending || pending.signal.aborted) return
      if (duplicate(data.barcode, data.copyId)) return
      setResult(data)
      if (data.status === 'NOT_BORROWED') recordFailure(data.barcode, data.message, data)
      setNotice({
        message: data.status === 'NOT_BORROWED' || data.status === 'MISSING_DUE_DATE'
          ? data.message : `Đã tìm thấy phiếu mượn ${data.loanNumber}.`,
        tone: data.status === 'NOT_BORROWED' ? 'info' : data.status === 'MISSING_DUE_DATE' ? 'warning' : 'success',
      })
    } catch (error) {
      if (mounted.current && request.current === pending && !pending.signal.aborted) {
        const message = getApiErrorMessage(error, 'Không tra cứu được mã vạch. Vui lòng thử lại.')
        recordFailure(code, message)
        setNotice({ message, tone: 'error' })
      }
    } finally {
      if (mounted.current && request.current === pending) {
        request.current = null; setBusy(false); focusBarcode(true)
      }
    }
  }

  async function confirmReturn() {
    if (!allowed || confirmation.current || request.current || !result?.itemId) return
    const preview = result
    if (duplicate(preview.barcode, preview.copyId)) return
    if (!window.confirm(`Xác nhận đã nhận cuốn “${preview.bookTitle}” có mã vạch ${preview.barcode}?`)) return
    confirmation.current = true
    setConfirming(true); setNotice(null)
    try {
      const data = await returnService.confirm(preview.barcode, preview.itemId!)
      if (!mounted.current) return
      record(returnSessionSuccess(preview, data))
      setResult(null); setBarcode(''); setFieldError('')
      setNotice({ message: data.message, tone: 'success' })
    } catch (error) {
      if (!mounted.current) return
      const response = (error as { response?: { status?: number } })?.response
      // A timeout may follow a committed return. Require a fresh lookup before another write.
      if (!response || response.status === 409) setResult(null)
      const message = !response
        ? 'Chưa xác định được kết quả nhận trả. Vui lòng tìm lại phiếu hoặc xem chi tiết phiếu trước khi xác nhận tiếp.'
        : getApiErrorMessage(error, 'Không nhận trả được sách. Vui lòng thử lại.')
      recordFailure(preview.barcode, message, preview)
      setNotice({ message, tone: 'error' })
    } finally {
      confirmation.current = false
      if (mounted.current) setConfirming(false)
    }
  }

  if (!allowed) return <p role="alert" className="rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700">
    Bạn không có quyền tra cứu nhận trả sách.
  </p>
  const successCount = entries.filter(entry => entry.status === 'SUCCESS').length

  return <div className="space-y-5">
    <PageHeader title="Nhận trả sách" description="Tra cứu và xác nhận từng cuốn. Sau khi nhận trả, nhập tiếp mã vạch ngay trên màn hình này." />
    <Card className="p-5">
      <form ref={form} noValidate onSubmit={(event) => { void lookup(event) }} className="flex flex-col gap-4 sm:flex-row sm:items-start">
        <div className="min-w-0 flex-1">
          <Input id="return-barcode" label="Mã vạch bản sao" required autoFocus autoComplete="off"
            maxLength={100} disabled={confirming} value={barcode} error={fieldError} startIcon={<Barcode size={18} />}
            placeholder="Nhập hoặc quét mã vạch rồi nhấn Enter"
            onChange={(event) => changeBarcode(event.target.value)} />
        </div>
        <Button type="submit" loading={busy} disabled={confirming} className="shrink-0 sm:mt-7">Tìm phiếu mượn</Button>
      </form>
      <p className="mt-3 text-xs leading-5 text-slate-500">Ngày giờ theo Việt Nam. Mỗi cuốn được xác nhận riêng; mã sai không làm mất các kết quả trước.</p>
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
            <span className={`inline-block rounded-lg px-3 py-1 text-sm ${result.status === 'OVERDUE'
              ? 'bg-red-50 text-red-700' : result.status === 'ON_TIME'
                ? 'bg-emerald-50 text-emerald-700' : 'bg-amber-50 text-amber-800'}`}>
              {result.status === 'OVERDUE' ? `Quá hạn ${result.overdueDays} ngày`
                : result.status === 'ON_TIME' ? 'Đúng hạn' : 'Chưa có hạn trả'}
            </span>
          </Detail>
          {result.status === 'OVERDUE' && <Detail label="Số ngày trễ">{result.overdueDays} ngày</Detail>}
        </> : <Detail label="Phiếu mượn đang mở">Không có phiếu đang mở cho bản sao này.</Detail>}
      </dl>
      {result.itemId !== null && <div className="mt-5">
        <Button type="button" loading={confirming} onClick={() => { void confirmReturn() }}>Xác nhận nhận trả</Button>
        <p className="mt-2 text-xs leading-5 text-slate-500">Chỉ xác nhận khi đã nhận sách. Ngày trả được ghi theo thời điểm xác nhận.</p>
      </div>}
      {result.itemId !== null && <p className="mt-5 text-xs leading-5 text-slate-500">
        Số ngày trễ tính theo ngày lịch, gồm ngày thư viện đóng cửa, từ hạn trả đã lưu đến ngày tra cứu theo giờ Việt Nam.
      </p>}
    </Card>}
    {!busy && !result && <EmptyState title="Sẵn sàng nhận cuốn tiếp theo" description="Nhập mã vạch bản sao và chọn Tìm phiếu mượn để tiếp tục." />}
    <Card className="overflow-hidden">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-200 px-5 py-4">
        <h2 className="text-lg font-semibold text-slate-900">Kết quả trong lượt hiện tại</h2>
        <p role="status" aria-live="polite" className="text-sm font-semibold text-emerald-700">Đã nhận thành công: {successCount} cuốn</p>
      </div>
      <p className="px-5 pt-4 text-xs leading-5 text-slate-500">Danh sách được giữ khi nhập mã khác. Rời hoặc tải lại trang sẽ bắt đầu lượt mới; các cuốn đã nhận vẫn được lưu trong phiếu mượn.</p>
      {entries.length === 0 ? <div className="p-5"><EmptyState title="Chưa xử lý mã vạch nào" description="Kết quả từng mã vạch sẽ xuất hiện ở đây sau khi xử lý." /></div>
        : <ol className="divide-y divide-slate-200">
          {entries.map((entry, index) => <li key={entry.barcode} data-return-result={entry.status} className="p-5">
            <dl className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
              <div className="min-w-0">
                <dt className="text-sm text-slate-500">{index + 1}. Tên sách · Mã vạch</dt>
                <dd className="mt-1 break-words font-semibold text-slate-900">{entry.bookTitle ?? 'Chưa xác định tên sách'}</dd>
                <dd className="mt-1 break-all font-mono text-sm text-slate-600">{entry.barcode}</dd>
              </div>
              <Detail label={entry.status === 'SUCCESS' ? 'Bạn đọc đã trả sách' : 'Bạn đọc đang mượn'}>{entry.readerName ?? 'Chưa có thông tin'}</Detail>
              <div className="space-y-3">
                <Detail label="Hạn trả">{formatLoanTimestamp(entry.dueAt)}</Detail>
                {entry.status === 'SUCCESS' && <Detail label="Ngày trả thực tế"><time dateTime={entry.returnedAt}>{formatLoanTimestamp(entry.returnedAt)}</time></Detail>}
              </div>
              {entry.status === 'SUCCESS' ? <div className="space-y-3">
                <Detail label="Kết quả nhận trả"><span className="text-emerald-700">Đã trả</span></Detail>
                <Detail label="Trạng thái bản sao"><span className={entry.copyStatus === 'HELD' ? 'text-amber-800' : 'text-emerald-700'}>
                  {entry.copyStatus === 'HELD' ? 'Đang giữ cho đặt trước' : 'Sẵn sàng'}
                </span></Detail>
                <Detail label={entry.overdueDays !== null && entry.overdueDays > 0 ? 'Số ngày trễ' : 'Tình trạng hạn trả'}>
                  <span className={entry.overdueDays !== null && entry.overdueDays > 0 ? 'text-red-700' : ''}>
                    {entry.overdueDays === null ? 'Chưa có hạn trả' : entry.overdueDays > 0 ? `${entry.overdueDays} ngày` : 'Đúng hạn'}
                  </span>
                </Detail>
              </div> : <Detail label="Kết quả xử lý"><span className="text-red-700">Chưa ghi nhận thành công</span>
                <p className="mt-1 text-sm font-normal leading-6 text-slate-600">{entry.message}</p>
              </Detail>}
            </dl>
            {entry.status === 'SUCCESS' && <details className="mt-4 text-sm">
              <summary className="cursor-pointer font-semibold text-blue-700">Chi tiết phiếu và xử lý bản sao</summary>
              <dl className="mt-3 grid gap-4 rounded-xl bg-slate-50 p-4 sm:grid-cols-2">
                <Detail label="Mã phiếu mượn"><Link to={`/loans/${entry.loanId}`} className="text-blue-700 hover:underline">{entry.loanNumber} · Xem chi tiết phiếu mượn</Link></Detail>
                <Detail label="Nhân viên nhận trả">{entry.returnedByName}</Detail>
                <Detail label="Trạng thái phiếu tại lần nhận trả">{entry.loanStatus === 'RETURNED' ? 'Đã trả' : 'Đang mượn · Còn cuốn chưa trả'}</Detail>
                {entry.copyStatus === 'HELD' && <>
                  <Detail label="Bạn đọc được giữ sách">{entry.nextReaderName}</Detail>
                  <Detail label="Đơn đặt giữ"><Link to={`/reservations/ready-for-pickup/${entry.nextReservationId}`} className="text-blue-700 hover:underline">Đơn #{entry.nextReservationId} · Chờ nhận</Link></Detail>
                  <Detail label="Bắt đầu giữ bản sao">{formatLoanTimestamp(entry.holdStartedAt)}</Detail>
                  <Detail label="Hạn cuối đến nhận">{formatLoanTimestamp(entry.pickupDeadline)}</Detail>
                </>}
              </dl>
            </details>}
          </li>)}
        </ol>}
    </Card>
  </div>
}

function Detail({ label, children }: { label: string; children: ReactNode }) {
  return <div className="min-w-0"><dt className="text-sm text-slate-500">{label}</dt>
    <dd className="mt-1 break-words font-semibold text-slate-900">{children}</dd></div>
}
