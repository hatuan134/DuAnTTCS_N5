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
import type { ReturnLookup, ReturnSessionEntry, ReturnSessionSuccess } from './returnService'

type Notice = { message: string; tone: 'success' | 'error' | 'warning' | 'info' }

export default function ReceiveReturnPage() {
  const allowed = loanRoles.includes(getCurrentUser()?.role ?? '')
  const [barcode, setBarcode] = useState('')
  const [fieldError, setFieldError] = useState('')
  const [busy, setBusy] = useState(false)
  const [result, setResult] = useState<ReturnLookup | null>(null)
  const [entries, setEntries] = useState<ReturnSessionEntry[]>([])
  const session = useRef<ReturnSessionEntry[]>([])
  const [showSummary, setShowSummary] = useState(false)
  const finished = useRef(false)
  const summaryHeading = useRef<HTMLHeadingElement | null>(null)
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
  useEffect(() => {
    if (showSummary) summaryHeading.current?.focus({ preventScroll: true })
    else if (!confirming) focusBarcode()
  }, [confirming, showSummary])

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
    if (confirmation.current || finished.current) return
    request.current?.abort()
    request.current = null
    setBarcode(value); setResult(null); setNotice(null); setFieldError(''); setBusy(false)
  }

  async function lookup(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!allowed || finished.current || request.current || confirmation.current) return
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
    if (!allowed || finished.current || confirmation.current || request.current || !result?.itemId) return
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

  function finishSession() {
    if (!allowed || finished.current || confirmation.current || request.current
      || !session.current.some(entry => entry.status === 'SUCCESS')) return
    // A looked-up copy is not a successful return until confirmation has completed.
    if (result?.itemId && !window.confirm(`Mã vạch ${result.barcode} chưa được xác nhận nhận trả. Kết thúc lượt và không tính cuốn này vào tổng số đã nhận?`)) return
    if (result?.itemId && !session.current.some(entry => entry.status === 'ERROR' && entry.barcode === result.barcode)) {
      recordFailure(result.barcode, 'Chưa xác nhận nhận trả khi kết thúc lượt.', result)
    }
    finished.current = true
    setNotice(null); setResult(null); setShowSummary(true)
  }

  function startNewSession() {
    if (!allowed || !finished.current || confirmation.current || request.current) return
    session.current = []
    setEntries([]); setBarcode(''); setFieldError(''); setResult(null); setNotice(null)
    finished.current = false
    setShowSummary(false)
  }

  if (!allowed) return <p role="alert" className="rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700">
    Bạn không có quyền tra cứu nhận trả sách.
  </p>
  const received = entries.filter((entry): entry is ReturnSessionSuccess => entry.status === 'SUCCESS')
  const successCount = received.length

  if (showSummary) return <div className="space-y-5">
    <PageHeader title="Nhận trả sách" description="Lượt nhận trả đã kết thúc. Kiểm tra tổng kết trước khi bắt đầu lượt tiếp theo." />
    <Card className="overflow-hidden">
      <div className="flex flex-col gap-4 border-b border-slate-200 p-5 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0">
          <h2 ref={summaryHeading} tabIndex={-1} className="text-lg font-semibold text-slate-900 outline-none">Tổng kết lượt nhận trả</h2>
          <p className="mt-2 text-sm font-semibold text-emerald-700">Tổng số cuốn đã nhận thành công: {successCount} cuốn</p>
          <p className="mt-1 text-xs leading-5 text-slate-500">Chỉ tính các cuốn được API xác nhận nhận trả thành công. Ngày giờ theo Việt Nam; trạng thái bản sao là kết quả tại lần nhận trả.</p>
        </div>
        <Button type="button" onClick={startNewSession} className="w-full sm:w-auto sm:shrink-0 sm:self-start">Đóng tổng kết và bắt đầu lượt mới</Button>
      </div>
      <div className="hidden lg:block">
        <table className="w-full table-fixed text-left text-sm">
          <caption className="sr-only">Các cuốn đã nhận thành công trong lượt</caption>
          <thead className="border-b border-slate-200 bg-slate-50 text-xs text-slate-600">
            <tr>
              <th scope="col" className="w-[14%] px-4 py-3">Mã vạch</th>
              <th scope="col" className="w-[18%] px-4 py-3">Tên sách</th>
              <th scope="col" className="w-[14%] px-4 py-3">Bạn đọc đã trả</th>
              <th scope="col" className="w-[16%] px-4 py-3">Ngày trả thực tế</th>
              <th scope="col" className="w-[9%] px-4 py-3">Số ngày trễ</th>
              <th scope="col" className="w-[13%] px-4 py-3">Trạng thái bản sao</th>
              <th scope="col" className="w-[16%] px-4 py-3">Đơn đặt giữ nhận bản sao</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-200">
            {received.map(entry => <tr key={entry.itemId} data-return-summary-row={entry.barcode} className="align-top">
              <th scope="row" className="break-all px-4 py-4 font-mono font-medium text-slate-700">{entry.barcode}</th>
              <td className="break-words px-4 py-4 font-semibold text-slate-900">{entry.bookTitle}</td>
              <td className="break-words px-4 py-4 text-slate-700">{entry.readerName ?? 'Chưa có thông tin'}</td>
              <td className="break-words px-4 py-4 text-slate-700"><time dateTime={entry.returnedAt}>{formatLoanTimestamp(entry.returnedAt)}</time></td>
              <td className="px-4 py-4"><ReturnDeadline entry={entry} /></td>
              <td className="px-4 py-4"><ReturnCopyStatus entry={entry} /></td>
              <td className="break-words px-4 py-4"><ReturnReservation entry={entry} /></td>
            </tr>)}
          </tbody>
        </table>
      </div>
      <ol className="divide-y divide-slate-200 lg:hidden" aria-label="Các cuốn đã nhận thành công trong lượt">
        {received.map((entry, index) => <li key={entry.itemId} data-return-summary-card={entry.barcode} className="p-5">
          <p className="break-words font-semibold text-slate-900">{index + 1}. {entry.bookTitle}</p>
          <dl className="mt-4 grid gap-4 sm:grid-cols-2">
            <Detail label="Mã vạch"><span className="break-all font-mono text-sm">{entry.barcode}</span></Detail>
            <Detail label="Bạn đọc đã trả">{entry.readerName ?? 'Chưa có thông tin'}</Detail>
            <Detail label="Ngày trả thực tế"><time dateTime={entry.returnedAt}>{formatLoanTimestamp(entry.returnedAt)}</time></Detail>
            <Detail label="Số ngày trễ"><ReturnDeadline entry={entry} /></Detail>
            <Detail label="Trạng thái bản sao"><ReturnCopyStatus entry={entry} /></Detail>
            <Detail label="Đơn đặt giữ nhận bản sao"><ReturnReservation entry={entry} /></Detail>
          </dl>
        </li>)}
      </ol>
    </Card>
    <Card className="p-5">
      <h2 className="text-base font-semibold text-slate-900">Mã vạch chưa ghi nhận thành công</h2>
      <p className="mt-1 text-xs leading-5 text-slate-500">Các mã dưới đây không nằm trong bảng sách đã nhận và không được cộng vào tổng số cuốn.</p>
      {entries.some(entry => entry.status === 'ERROR') ? <ul className="mt-4 divide-y divide-slate-200">
        {entries.filter(entry => entry.status === 'ERROR').map(entry => <li key={entry.barcode}
          data-return-summary-error={entry.barcode} className="py-3 first:pt-0 last:pb-0">
          <p className="break-all font-mono text-sm font-semibold text-red-700">{entry.barcode}</p>
          {entry.bookTitle && <p className="mt-1 break-words text-sm font-medium text-slate-900">{entry.bookTitle}</p>}
          <p className="mt-1 break-words text-sm leading-6 text-slate-600">{entry.message}</p>
        </li>)}
      </ul> : <p className="mt-3 text-sm text-slate-600">Không có mã vạch lỗi hoặc chưa ghi nhận thành công trong lượt.</p>}
    </Card>
  </div>

  return <div className="space-y-5">
    <PageHeader title="Nhận trả sách" description="Tra cứu và xác nhận từng cuốn. Chọn Kết thúc lượt để xem bảng tổng kết sau khi nhận đủ sách."
      action={successCount > 0 && <Button type="button" disabled={busy || confirming} onClick={finishSession}>Kết thúc lượt</Button>} />
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
      <p className="px-5 pt-4 text-xs leading-5 text-slate-500">Danh sách được giữ khi nhập mã khác. Chọn Kết thúc lượt để xem tổng kết. Rời hoặc tải lại trang sẽ bắt đầu lượt mới; các cuốn đã nhận vẫn được lưu trong phiếu mượn.</p>
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

function ReturnDeadline({ entry }: { entry: ReturnSessionSuccess }) {
  return <span className={entry.overdueDays !== null && entry.overdueDays > 0 ? 'font-semibold text-red-700' : 'text-slate-700'}>
    {entry.overdueDays === null ? 'Chưa có hạn trả' : entry.overdueDays > 0 ? `${entry.overdueDays} ngày` : '0 ngày · Đúng hạn'}
  </span>
}

function ReturnCopyStatus({ entry }: { entry: ReturnSessionSuccess }) {
  return <span className={`inline-block rounded-lg px-2 py-1 text-xs font-semibold ${entry.copyStatus === 'HELD'
    ? 'bg-amber-50 text-amber-800' : 'bg-emerald-50 text-emerald-700'}`}>
    {entry.copyStatus === 'HELD' ? 'Đang giữ' : 'Sẵn sàng'}
  </span>
}

function ReturnReservation({ entry }: { entry: ReturnSessionSuccess }) {
  return entry.copyStatus === 'HELD' && entry.nextReservationId !== null ? <div className="space-y-1">
    <Link to={`/reservations/ready-for-pickup/${entry.nextReservationId}`} className="font-semibold text-blue-700 hover:underline">
      Đơn #{entry.nextReservationId} · Chờ nhận
    </Link>
    <p className="text-sm font-normal text-slate-600">{entry.nextReaderName}</p>
  </div> : <span className="text-sm font-normal text-slate-500">Không chuyển hàng đợi</span>
}
