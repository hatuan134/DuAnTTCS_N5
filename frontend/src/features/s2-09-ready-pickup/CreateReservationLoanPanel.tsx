import FeedbackAlert from '../../components/ui/FeedbackAlert'
import type { ReaderLoanEligibility } from '../s3-02-direct-loans/directLoanService'
import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import Button from '../../components/ui/Button'
import Input from '../../components/ui/Input'
import StatusBadge from '../../components/ui/StatusBadge'
import { getApiErrorMessage } from '../s1-02-user-management/accountService'
import { formatLoanDate, formatPickupDate, isPickupExpired, pickupExpiredMessage, pickupService } from './pickupService'
import type { LoanDatePreview, ReadyPickupReservation, ReservationLoanResult, ReservationLoanContext } from './pickupService'

interface Props {
  reservation: ReadyPickupReservation
  isManager?: boolean
  disabled: boolean
  onBusyChange: (busy: boolean) => void
  onSuccess: (result: ReservationLoanResult) => void
  onAlreadyConverted: (context?: ReservationLoanContext) => void
  onExpired?: (context: ReservationLoanContext) => void
}

const ALLOWED_OVERRIDES = new Set(['LOAN_LIMIT_REACHED', 'LOAN_DRAFT_LIMIT_EXCEEDED',
  'LOAN_OVERDUE_UNRETURNED', 'LOAN_UNPAID_FEES'])

export default function CreateReservationLoanPanel({
  reservation, disabled, onBusyChange, onSuccess, onAlreadyConverted, onExpired, isManager = false,
}: Props) {
  const [cardNumber, setCardNumber] = useState('')
  const [readerCheck, setReaderCheck] = useState<ReaderLoanEligibility | null>(null)
  const [readerCheckError, setReaderCheckError] = useState('')
  const [overrideRequested, setOverrideRequested] = useState(false)
  const [overrideReason, setOverrideReason] = useState('')
  const canOverride = isManager && !!readerCheck && (readerCheck.blockReasons?.length ?? 0) > 0
    && (readerCheck.blockReasons ?? []).every((r) => ALLOWED_OVERRIDES.has(r.code))
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [dismissedError, setDismissedError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [result, setResult] = useState<ReservationLoanResult | null>(null)
  const pendingRef = useRef(false)
  const requestIdRef = useRef<string | null>(null)
  const [preview, setPreview] = useState<LoanDatePreview | null>(reservation.dates ?? null)
  const [dateError, setDateError] = useState(reservation.dateError ?? '')
  const [currentContext, setCurrentContext] = useState<ReservationLoanContext | null>(null)
  const [elapsedMs, setElapsedMs] = useState(0)
  const expiryAnchor = useRef(performance.now())
  const expiryCheckAttempted = useRef(false)
  const effective = currentContext ? { ...reservation, ...currentContext } : reservation
  const expired = isPickupExpired(effective, elapsedMs)

  useEffect(() => {
    if (!isManager || !cardNumber.trim() || cardNumber.trim().length > 100) {
      setReaderCheck(null); setReaderCheckError(''); return
    }
    let valid = true
    setReaderCheck(null); setReaderCheckError('')
    const timer = window.setTimeout(() => {
      pickupService.checkReader(cardNumber).then((data) => {
        if (valid) setReaderCheck(data)
      }).catch((e: unknown) => {
        if (valid) setReaderCheckError(getApiErrorMessage(e, 'Không kiểm tra được điều kiện mượn.'))
      })
    }, 350)
    return () => { valid = false; window.clearTimeout(timer) }
  }, [cardNumber, isManager])

  function acceptContext(context: ReservationLoanContext) {
    expiryAnchor.current = performance.now()
    setElapsedMs(0)
    setCurrentContext(context)
    if (!context.expired) expiryCheckAttempted.current = false
    setPreview(context.dates ?? null)
    setDateError(context.dateError ?? '')
    if (context.converted) onAlreadyConverted(context)
    if (context.expired) onExpired?.(context)
  }

  useEffect(() => {
    if (!effective.checkedAt || effective.expired || effective.converted || result) return
    const timer = window.setInterval(() => setElapsedMs(performance.now() - expiryAnchor.current), 1000)
    return () => window.clearInterval(timer)
  }, [effective.checkedAt, effective.expired, effective.converted, result])

  useEffect(() => {
    // If the view stays open past the deadline, persist expiry/release through the explicit check.
    if (expired && !effective.expired && !disabled && !submitting && !result && !expiryCheckAttempted.current) {
      expiryCheckAttempted.current = true
      void refreshPreview()
    }
  }, [expired, effective.expired, disabled, submitting, result])

  async function refreshPreview() {
    if (pendingRef.current || disabled || effective.converted || result) return
    pendingRef.current = true
    setSubmitting(true)
    onBusyChange(true)
    setError(''); setDismissedError('')
    try {
      const context = await pickupService.loanContext(reservation.id)
      acceptContext(context)
    } catch (e: unknown) {
      setPreview(null)
      setError(getApiErrorMessage(e, 'Không tải được hạn trả. Vui lòng thử lại.'))
    } finally {
      pendingRef.current = false
      setSubmitting(false)
      onBusyChange(false)
    }
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (pendingRef.current || disabled || effective.converted || result) return
    if (isPickupExpired(effective, performance.now() - expiryAnchor.current)) {
      setElapsedMs(performance.now() - expiryAnchor.current)
      setError(pickupExpiredMessage)
      return
    }
    const confirmed = cardNumber.trim()
    if (!confirmed || confirmed.length > 100) {
      setError('Vui lòng nhập mã thẻ hợp lệ, tối đa 100 ký tự.')
      return
    }
    if (overrideRequested && (!canOverride || !overrideReason.trim())) {
      setError('Chỉ được bỏ qua vi phạm chính sách được phép và phải nhập lý do.'); return
    }
    if (!preview || dateError) {
      setError('Chưa xác định được hạn trả. Vui lòng kiểm tra cấu hình và cập nhật hạn trả trước khi xác nhận.')
      return
    }
    pendingRef.current = true
    setSubmitting(true)
    onBusyChange(true)
    setError(''); setDismissedError('')
    try {
      if (!requestIdRef.current) requestIdRef.current = crypto.randomUUID()
      const created = await pickupService.createLoan(reservation.id, confirmed, preview,
        requestIdRef.current, overrideRequested, overrideReason)
      setResult(created)
      setNotice(created.message)
      onSuccess(created)
    } catch (e: unknown) {
      setError(getApiErrorMessage(e, 'Không lập được phiếu mượn. Vui lòng thử lại.'))
      // A lost response/repeated request must not offer another conversion.
      try {
        const current = await pickupService.loanContext(reservation.id)
        acceptContext(current)
      } catch {
        // Preserve the original error. The server still guards subsequent retries.
      }
    } finally {
      pendingRef.current = false
      setSubmitting(false)
      onBusyChange(false)
    }
  }

  return <section className="mt-6 border-t border-slate-200 pt-6" aria-labelledby="loan-heading">
    <h4 id="loan-heading" className="text-lg font-semibold text-slate-900">Đối chiếu thẻ và lập phiếu mượn</h4>
    <p className="mt-2 text-sm text-slate-600">Kiểm tra thẻ của người đến nhận và mã vạch bản sách phía trên trước khi xác nhận.</p>
    <p className="mt-3 text-sm text-slate-600">Mã thẻ của bạn đọc: <strong className="break-all font-mono text-slate-900">
      {reservation.cardNumber || 'Chưa có thẻ thư viện'}
    </strong></p>
    {error && error !== dismissedError && <FeedbackAlert message={error} tone="error" onDismiss={() => setDismissedError(error)} className="mt-3" />}
    {notice && <FeedbackAlert message={notice} tone="success" onDismiss={() => setNotice('')} className="mt-3" />}
    {result ? <div role="status" className="mt-4 rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-900">
      <p className="font-semibold">Thông tin phiếu mượn đã lập</p>
      <div className="mt-3 flex flex-wrap items-center gap-3">
        <div>Đơn đặt giữ: <StatusBadge status="FULFILLED" label="Đã chuyển thành phiếu mượn" /></div>
        <div>Bản sao: <StatusBadge status="BORROWED" label="Đang mượn" /></div>
      </div>
      <dl className="mt-3 grid gap-3 sm:grid-cols-2">
        <div><dt>Mã phiếu mượn</dt><dd className="break-all font-mono font-semibold">{result.loanNumber}</dd></div>
        <div><dt>Bạn đọc</dt><dd className="font-semibold">{result.readerName}</dd></div>
        <div><dt>Mã thẻ</dt><dd className="font-mono font-semibold">{result.cardNumber}</dd></div>
        <div><dt>Đầu sách</dt><dd className="font-semibold">{result.bookTitle}</dd></div>
        <div><dt>Mã vạch bản sao</dt><dd className="break-all font-mono font-semibold">{result.barcode}</dd></div>
        <div><dt>Thời điểm mượn</dt><dd>{formatPickupDate(result.borrowedAt, true)}</dd></div>
        <div><dt>Ngày mượn</dt><dd>{formatLoanDate(result.dates.borrowDate)}</dd></div>
        <div><dt>Hạn trả đã lưu (giờ Việt Nam)</dt><dd className="font-semibold">{formatPickupDate(result.dates.dueAt)}</dd></div>
      </dl>
    </div> : effective.converted ? <p role="status" className="mt-4 rounded-lg bg-emerald-50 p-3 text-sm text-emerald-900">
      Đơn này đã chuyển thành phiếu mượn{effective.loanNumber ? <strong className="break-all"> {effective.loanNumber}</strong> : ''}. Không thể lập thêm phiếu.
    </p> : expired ? <div className="mt-4 space-y-3">
      <p role="alert" className="rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">
        {effective.pickupMessage || pickupExpiredMessage}
      </p>
      {effective.copyStatus === 'AVAILABLE' && <p className="text-sm text-slate-600">
        Bản sao đã được giải phóng về Sẵn sàng. Bạn đọc cần tạo đơn đặt giữ mới.
      </p>}
      <div className="flex flex-wrap gap-3">
        <Button type="button" disabled>Xác nhận và lập phiếu mượn</Button>
        {error && !effective.expired && <Button type="button" variant="secondary" disabled={disabled || submitting}
          onClick={() => { void refreshPreview() }}>Kiểm tra lại trạng thái</Button>}
      </div>
    </div> : <form className="mt-4 max-w-lg space-y-4" onSubmit={(event) => { void submit(event) }} noValidate>
      <div className="rounded-xl border border-blue-100 bg-blue-50 p-4 text-sm text-slate-700">
        <h5 className="font-semibold text-slate-900">Ngày mượn và hạn trả dự kiến</h5>
        {preview && <dl className="mt-3 grid gap-3 sm:grid-cols-2">
          <div><dt>Loại thẻ</dt><dd className="font-semibold">{preview.cardTypeName}</dd></div>
          <div><dt>Số ngày được mượn</dt><dd className="font-semibold">{preview.loanDays} ngày</dd></div>
          <div><dt>Ngày mượn (giờ Việt Nam)</dt><dd className="font-semibold">{formatLoanDate(preview.borrowDate)}</dd></div>
          <div><dt>Hạn trả trước điều chỉnh</dt><dd>{formatLoanDate(preview.originalDueDate)}</dd></div>
          <div className="sm:col-span-2"><dt>Hạn trả dự kiến (giờ Việt Nam)</dt><dd className="font-semibold text-blue-900">{formatPickupDate(preview.dueAt)}</dd></div>
        </dl>}
        {preview?.adjusted && <p role="status" className="mt-3 text-amber-900">
          Hạn trả đã được dời tới ngày mở cửa kế tiếp, sau {preview.skippedClosedDates.length} ngày đóng cửa:
          {' '}{preview.skippedClosedDates.map(formatLoanDate).join(', ')}.
        </p>}
        {dateError && <p role="alert" className="mt-3 text-red-700">{dateError}</p>}
        {!preview && !dateError && <p className="mt-3">Chưa có thông tin hạn trả. Nhấn cập nhật để kiểm tra.</p>}
        <Button type="button" variant="secondary" className="mt-3" disabled={disabled || submitting}
          onClick={() => { void refreshPreview() }}>Cập nhật hạn trả</Button>
      </div>
      <Input id="confirmed-card-number" label="Mã thẻ của người đến nhận" required maxLength={100}
        autoComplete="off" placeholder="Nhập hoặc quét mã thẻ thực tế"
        value={cardNumber} disabled={disabled || submitting}
        onChange={(event) => { setCardNumber(event.target.value); setError(''); setDismissedError('');
          setOverrideRequested(false); setOverrideReason(''); requestIdRef.current = null }} />
      {isManager && <section className="space-y-3 rounded-xl border border-amber-300 bg-amber-50 p-4 text-sm">
        <p className="font-semibold text-amber-950">Quản lý thư viện — bỏ qua chặn đúng một lượt</p>
        {readerCheckError && <FeedbackAlert message={readerCheckError} tone="error"
          onDismiss={() => setReaderCheckError('')} />}
        {readerCheck && !readerCheck.eligible && <>
          <p className="font-semibold text-amber-900">Các lý do đang chặn:</p>
          <ul className="list-disc space-y-1 pl-5 text-amber-900">
            {(readerCheck.blockReasons ?? []).map((r) => <li key={r.code}>{r.message}</li>)}
          </ul>
          {!canOverride && <p className="text-red-800">Có vi phạm không được phép bỏ qua. Cần xử lý trước khi mượn.</p>}
        </>}
        {readerCheck?.eligible && <p className="text-slate-600">Bạn đọc hiện đủ điều kiện; không cần sử dụng quyền bỏ qua.</p>}
        <label className="flex items-start gap-2 font-medium text-slate-900">
          <input type="checkbox" checked={overrideRequested} disabled={!canOverride || submitting || disabled}
            onChange={(e) => { setOverrideRequested(e.target.checked); requestIdRef.current = null }} />
          Tôi xác nhận bỏ qua các lý do trên chỉ cho đơn đang chuyển thành phiếu mượn
        </label>
        {overrideRequested && <label className="block font-medium text-slate-900">Lý do bắt buộc
          <textarea maxLength={500} required value={overrideReason} disabled={submitting || disabled}
            onChange={(e) => { setOverrideReason(e.target.value); requestIdRef.current = null }}
            className="mt-2 block min-h-24 w-full rounded-xl border border-slate-300 bg-white p-3"
            placeholder="Nêu rõ lý do quyết định bỏ qua…" />
          {!overrideReason.trim() && <span className="mt-1 block text-xs text-red-700">Vui lòng nhập lý do.</span>}
        </label>}
        <p className="text-xs text-amber-900">Vi phạm của bạn đọc không bị xóa. Lượt sau sẽ kiểm tra lại toàn bộ chính sách.</p>
      </section>}
      <Button type="submit" loading={submitting}
        disabled={disabled || !reservation.cardNumber || !reservation.copyId || !reservation.barcode || !preview || !!dateError || !!effective.pickupMessage
          || (overrideRequested && (!canOverride || !overrideReason.trim()))}>
        Xác nhận và lập phiếu mượn
      </Button>
    </form>}
  </section>
}
