import { useEffect, useRef, useState } from 'react'
import Button from '../../components/ui/Button'
import ConfirmActionDialog from '../../components/ui/ConfirmActionDialog'
import FeedbackAlert from '../../components/ui/FeedbackAlert'
import { getApiErrorMessage } from '../s1-02-user-management/accountService'
import ReadyPickupEmailPanel from './ReadyPickupEmailPanel'
import { formatPickupDate } from './pickupService'
import { readyEmailService, type PickupCancellationPreview } from './readyEmailService'

export function cancellationCheckTime(value: string): string | undefined {
  if (!value) return undefined
  if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2})?$/.test(value)) throw new Error('Thời điểm xem trước không hợp lệ.')
  const result = `${value.length === 16 ? `${value}:00` : value}+07:00`
  if (Number.isNaN(Date.parse(result))) throw new Error('Thời điểm xem trước không hợp lệ.')
  return result
}

export default function PickupCancellationTrialPanel({ onChanged }: { onChanged: () => void }) {
  const [id, setId] = useState('')
  const [checkTime, setCheckTime] = useState('')
  const [fieldError, setFieldError] = useState('')
  const [preview, setPreview] = useState<PickupCancellationPreview | null>(null)
  const [historyId, setHistoryId] = useState<number | null>(null)
  const [revision, setRevision] = useState(0)
  const [busy, setBusy] = useState(false)
  const [confirm, setConfirm] = useState(false)
  const [notice, setNotice] = useState<{ tone: 'success' | 'error' | 'warning' | 'info'; message: string } | null>(null)
  const pending = useRef(false)
  const active = useRef(true)
  useEffect(() => { active.current = true; return () => { active.current = false } }, [])
  const reset = () => { setPreview(null); setHistoryId(null); setFieldError(''); setNotice(null) }
  const load = async () => {
    if (pending.current) return
    const reservationId = Number(id)
    if (!Number.isSafeInteger(reservationId) || reservationId < 1) {
      const message = 'Vui lòng nhập mã đơn là số nguyên dương.'
      setFieldError(message); setNotice({ tone: 'warning', message }); return
    }
    pending.current = true; setBusy(true); setNotice(null); setFieldError(''); setPreview(null); setHistoryId(null)
    try {
      const data = await readyEmailService.previewCancellation(reservationId, cancellationCheckTime(checkTime))
      if (!active.current) return
      if (data.reservationId !== reservationId) throw new Error('Thông tin trả về không khớp mã đơn đã chọn.')
      setPreview(data); setHistoryId(reservationId)
      setNotice({ tone: 'info', message: 'Đã xem trước. Chưa hủy đơn, chưa gửi email và chưa ghi lịch sử.' })
    } catch (e) {
      if (active.current) setNotice({ tone: 'error', message: getApiErrorMessage(e, 'Không xem trước được email hủy.') })
    } finally { pending.current = false; if (active.current) setBusy(false) }
  }
  const run = async () => {
    if (pending.current || !preview?.canRunNow) return
    pending.current = true; setBusy(true); setNotice(null)
    try {
      const result = await readyEmailService.runCancellation(preview.reservationId, preview.evaluatedAt)
      if (!active.current) return
      setNotice({ tone: 'success', message: result.message }); setRevision(value => value + 1)
      setPreview(null); onChanged()
    } catch (e) {
      if (active.current) setNotice({ tone: 'error', message: getApiErrorMessage(e, 'Không hủy được đơn quá hạn.') })
    } finally { pending.current = false; if (active.current) { setBusy(false); setConfirm(false) } }
  }
  return <section className="min-w-0 rounded-2xl border border-slate-200 bg-white p-4 sm:p-6" aria-labelledby="cancellation-trial-title">
    <h2 id="cancellation-trial-title" className="text-lg font-semibold text-slate-900">Chạy thử email hủy do quá hạn nhận</h2>
    <p className="mt-2 text-sm leading-6 text-slate-600">Chọn một đơn để xem trước. Mốc tương lai chỉ xem trước; hủy thật chỉ áp dụng cho đơn đã quá hạn theo giờ thực tế. Thời gian nhập là giờ Việt Nam.</p>
    <form noValidate className="mt-4 flex flex-wrap items-start gap-3" onSubmit={event => { event.preventDefault(); void load() }}>
      <div className="w-full sm:w-40">
        <label htmlFor="cancellation-trial-id" className="mb-2 block text-sm font-medium">Mã đơn *</label>
        <input id="cancellation-trial-id" type="number" min="1" step="1" value={id} disabled={busy || confirm}
          aria-invalid={!!fieldError} aria-describedby={fieldError ? 'cancellation-trial-error' : undefined}
          onChange={event => { setId(event.target.value); reset() }} className="h-11 w-full rounded-lg border border-slate-300 px-3 text-sm" />
        {fieldError && <p id="cancellation-trial-error" className="mt-2 text-sm text-red-700">{fieldError}</p>}
      </div>
      <div className="w-full sm:w-64">
        <label htmlFor="cancellation-trial-time" className="mb-2 block text-sm font-medium">Mốc xem trước (UTC+7)</label>
        <input id="cancellation-trial-time" type="datetime-local" value={checkTime} disabled={busy || confirm}
          onChange={event => { setCheckTime(event.target.value); reset() }} className="h-11 w-full rounded-lg border border-slate-300 px-3 text-sm" />
        <p className="mt-1 text-xs text-slate-500">Để trống để dùng giờ thực tế.</p>
      </div>
      <Button type="submit" className="sm:mt-7" disabled={busy || confirm} loading={busy}>Xem trước</Button>
    </form>
    {notice && <FeedbackAlert message={notice.message} tone={notice.tone} onDismiss={() => setNotice(null)} className="mt-4" />}
    {preview && <div className="mt-4 min-w-0 rounded-lg bg-slate-50 p-4">
      <p className="break-words font-medium">Đơn #{preview.reservationId} — {preview.bookTitle}</p>
      <p className="mt-2 text-sm">Bạn đọc: {preview.readerName} · Email: <span className="break-all">{preview.recipientEmail || 'Chưa có email'}</span></p>
      <p className="mt-1 text-sm">Hạn nhận: {formatPickupDate(preview.pickupDeadline)} · Mốc xem trước: {formatPickupDate(preview.evaluatedAt, true)}</p>
      <p role="status" className="mt-3 text-sm font-medium text-slate-700">{preview.reason}</p>
      {preview.eligible && <details className="mt-3 text-sm">
        <summary className="cursor-pointer font-medium text-blue-700">Xem nội dung email dự kiến (chưa gửi)</summary>
        <p className="mt-2 text-xs text-slate-500">Thời điểm hủy trong bản xem trước là giả định. Email thật dùng thời điểm hủy đã ghi nhận.</p>
        <iframe title={`Xem trước email hủy đơn #${preview.reservationId}`} srcDoc={preview.emailContent} sandbox="" className="mt-2 h-80 w-full rounded border border-slate-200 bg-white" />
      </details>}
      {preview.canRunNow && <Button type="button" variant="danger" className="mt-4" disabled={busy} onClick={() => setConfirm(true)}>Hủy đơn quá hạn và gửi email</Button>}
    </div>}
    {historyId !== null && <ReadyPickupEmailPanel key={`${historyId}:${revision}`} reservationId={historyId} />}
    {confirm && preview && <ConfirmActionDialog busy={busy} title={`Hủy đơn #${preview.reservationId} do quá hạn nhận`}
      description="Thao tác hủy thật đơn được chọn, xử lý bản sao theo quy trình hiện có và tự động gửi email đến bạn đọc. Hệ thống kiểm tra lại trạng thái và hạn nhận trước khi hủy."
      confirmLabel="Xác nhận hủy và gửi email" onCancel={() => { if (!busy) setConfirm(false) }} onConfirm={() => void run()} />}
  </section>
}
