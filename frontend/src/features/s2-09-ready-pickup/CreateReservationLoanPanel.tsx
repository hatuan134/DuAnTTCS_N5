import { useRef, useState } from 'react'
import type { FormEvent } from 'react'
import Button from '../../components/ui/Button'
import Input from '../../components/ui/Input'
import { getApiErrorMessage } from '../s1-02-user-management/accountService'
import { formatPickupDate, pickupService } from './pickupService'
import type { ReadyPickupReservation, ReservationLoanResult } from './pickupService'

interface Props {
  reservation: ReadyPickupReservation
  disabled: boolean
  onBusyChange: (busy: boolean) => void
  onSuccess: (result: ReservationLoanResult) => void
  onAlreadyConverted: () => void
}

export default function CreateReservationLoanPanel({
  reservation, disabled, onBusyChange, onSuccess, onAlreadyConverted,
}: Props) {
  const [cardNumber, setCardNumber] = useState('')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [result, setResult] = useState<ReservationLoanResult | null>(null)
  const pendingRef = useRef(false)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (pendingRef.current || disabled || reservation.converted || result) return
    const confirmed = cardNumber.trim()
    if (!confirmed || confirmed.length > 100) {
      setError('Vui lòng nhập mã thẻ hợp lệ, tối đa 100 ký tự.')
      return
    }
    pendingRef.current = true
    setSubmitting(true)
    onBusyChange(true)
    setError('')
    try {
      const created = await pickupService.createLoan(reservation.id, confirmed)
      setResult(created)
      onSuccess(created)
    } catch (e: unknown) {
      setError(getApiErrorMessage(e, 'Không lập được phiếu mượn. Vui lòng thử lại.'))
      // A lost response/repeated request must not offer another conversion.
      try {
        const current = await pickupService.loanContext(reservation.id)
        if (current.converted) onAlreadyConverted()
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
    {error && <p role="alert" className="mt-3 rounded-lg bg-red-50 p-3 text-sm text-red-700">{error}</p>}
    {result ? <div role="status" className="mt-4 rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-900">
      <p className="font-semibold">{result.message}</p>
      <dl className="mt-3 grid gap-3 sm:grid-cols-2">
        <div><dt>Mã phiếu mượn</dt><dd className="break-all font-mono font-semibold">{result.loanNumber}</dd></div>
        <div><dt>Bạn đọc</dt><dd className="font-semibold">{result.readerName}</dd></div>
        <div><dt>Mã thẻ</dt><dd className="font-mono font-semibold">{result.cardNumber}</dd></div>
        <div><dt>Đầu sách</dt><dd className="font-semibold">{result.bookTitle}</dd></div>
        <div><dt>Mã vạch bản sao</dt><dd className="break-all font-mono font-semibold">{result.barcode}</dd></div>
        <div><dt>Thời điểm mượn</dt><dd>{formatPickupDate(result.borrowedAt, true)}</dd></div>
      </dl>
    </div> : reservation.converted ? <p role="status" className="mt-4 rounded-lg bg-emerald-50 p-3 text-sm text-emerald-900">
      Đơn này đã chuyển thành phiếu mượn{reservation.loanNumber ? <strong className="break-all"> {reservation.loanNumber}</strong> : ''}. Không thể lập thêm phiếu.
    </p> : <form className="mt-4 max-w-lg space-y-4" onSubmit={(event) => { void submit(event) }} noValidate>
      <Input id="confirmed-card-number" label="Mã thẻ của người đến nhận" required maxLength={100}
        autoComplete="off" placeholder="Nhập hoặc quét mã thẻ thực tế"
        value={cardNumber} disabled={disabled || submitting}
        onChange={(event) => { setCardNumber(event.target.value); setError('') }} />
      <Button type="submit" loading={submitting}
        disabled={disabled || !reservation.cardNumber || !reservation.copyId || !reservation.barcode}>
        Xác nhận và lập phiếu mượn
      </Button>
    </form>}
  </section>
}
