import FeedbackAlert from '../../components/ui/FeedbackAlert'
import { useEffect, useRef, useState } from 'react'
import Button from '../../components/ui/Button'
import Card from '../../components/ui/Card'
import { getApiErrorMessage } from '../s1-02-user-management/accountService'
import { formatPickupDate, pickupService } from './pickupService'
import type { CancelReservationResult, ReservationCancellationAudit } from './pickupService'

interface Props {
  reservation: { id: number; readerName: string }
  onDismiss: () => void
  onSuccess: (result: CancelReservationResult) => void
}

export default function CancelReservationPanel({ reservation, onDismiss, onSuccess }: Props) {
  const [reason, setReason] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const pendingRef = useRef(false)
  const mountedRef = useRef(true)
  const normalized = reason.trim()
  const valid = normalized.length > 0 && normalized.length <= 500

  useEffect(() => {
    mountedRef.current = true
    return () => { mountedRef.current = false }
  }, [])

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (pendingRef.current) return
    if (!valid) {
      setError('Vui lòng nhập lý do huỷ từ 1 đến 500 ký tự, không chỉ gồm khoảng trắng.')
      return
    }
    pendingRef.current = true
    setBusy(true)
    setError('')
    try {
      const result = await pickupService.cancel(reservation.id, normalized)
      if (mountedRef.current) onSuccess(result)
    } catch (e) {
      if (mountedRef.current) setError(getApiErrorMessage(e, 'Không huỷ được đơn. Vui lòng thử lại hoặc tải lại danh sách.'))
    } finally {
      pendingRef.current = false
      if (mountedRef.current) setBusy(false)
    }
  }

  return <Card className="mb-5 border-red-200 p-5">
    <form onSubmit={(event) => { void submit(event) }} aria-labelledby="cancel-reservation-title">
      <h3 id="cancel-reservation-title" className="text-lg font-semibold text-slate-900">
        Huỷ đơn #{reservation.id} thay bạn đọc
      </h3>
      <p className="mt-2 text-sm text-slate-600">Bạn đọc: {reservation.readerName}.
        {' '}Nếu đơn đang giữ bản sao, hệ thống chuyển bản đó cho người kế tiếp hoặc đưa về Sẵn sàng.</p>
      <label htmlFor="cancel-reservation-reason" className="mb-2 mt-4 block text-sm font-medium text-slate-700">
        Lý do huỷ <span className="text-red-600">*</span>
      </label>
      <textarea id="cancel-reservation-reason" required maxLength={500} rows={3} value={reason}
        autoFocus disabled={busy} aria-describedby="cancel-reason-help"
        onChange={(event) => { setReason(event.target.value); setError('') }}
        className="w-full rounded-lg border border-slate-300 bg-white p-3 text-sm text-slate-900 outline-none focus:border-blue-500 focus:ring-4 focus:ring-blue-100" />
      <p id="cancel-reason-help" className="mt-1 text-xs text-slate-500">
        Bắt buộc có nội dung, tối đa 500 ký tự. {normalized.length}/500 ký tự.
      </p>
      {error && <FeedbackAlert message={error} tone="error" onDismiss={() => setError('')} />}
      <div className="mt-4 flex flex-wrap gap-3">
        <Button type="submit" variant="danger" loading={busy} disabled={!valid}>Xác nhận huỷ</Button>
        <Button type="button" variant="secondary" disabled={busy} onClick={onDismiss}>Đóng biểu mẫu</Button>
      </div>
    </form>
  </Card>
}

export function CancellationAudit({ audit }: { audit: ReservationCancellationAudit }) {
  return <div className="mt-2 space-y-1 text-sm text-slate-700">
    <p>Người huỷ: <strong>{audit.actorName}</strong>{audit.actorId == null ? '' : ` (#${audit.actorId})`}.</p>
    <p>Thời điểm: <time dateTime={audit.cancelledAt}>{formatPickupDate(audit.cancelledAt, true)}</time>.</p>
    <p className="whitespace-pre-wrap break-words">Lý do: {audit.reason}</p>
  </div>
}

export function CancellationNotice({ result }: { result: CancelReservationResult }) {
  return <div role="status" className="mb-5 rounded-lg border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-900">
    <p className="font-semibold">{result.message}</p>
    <CancellationAudit audit={result.cancellation} />
    {result.copyOutcome === 'TRANSFERRED' && <p className="mt-2">
      Bản sao {result.barcode} đang được giữ cho {result.nextReaderName}, đơn #{result.nextReservationId}.
      {' '}Hạn nhận mới: {formatPickupDate(result.pickupDeadline)}.
    </p>}
    {result.copyOutcome === 'AVAILABLE' && <p className="mt-2">Bản sao {result.barcode} đã về trạng thái Sẵn sàng.</p>}
    {result.copyOutcome === 'NO_COPY' && <p className="mt-2">Đơn chưa có bản sao được gán. Hàng đợi đã được cập nhật.</p>}
  </div>
}
