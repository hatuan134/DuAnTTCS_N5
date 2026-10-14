import { useRef, useState } from 'react'
import Button from '../../components/ui/Button'
import Modal from '../../components/ui/Modal'
import FeedbackAlert from '../../components/ui/FeedbackAlert'
import { getCurrentUser } from '../../core/auth/authStorage'
import { getApiErrorMessage } from '../s1-02-user-management/accountService'
import { formatLoanTimestamp } from '../s3-01-loans/loanService'
import { formatVnd } from '../s3-07-returns/ReturnFeeDetails'
import { adjustmentStatusLabel, closureLabel, incidentService, validateReversalReason, type IncidentRecord, type IncidentAdjustment, type AdjustmentPreview } from './incidentService'

const cases: Record<string, string> = { MISTAKEN: 'Ghi nhận nhầm', FOUND: 'Tìm lại sách đã báo mất', REASSESSED: 'Xác định lại tình trạng sách' }
export default function IncidentAdjustmentPanel({ record, onUpdated }: { record: IncidentRecord; onUpdated: (record: IncidentRecord) => void }) {
  const [expanded, setExpanded] = useState(false), [loaded, setLoaded] = useState(false)
  const [entries, setEntries] = useState<IncidentAdjustment[]>([]), [busy, setBusy] = useState(false)
  const [reason, setReason] = useState(''), [caseType, setCaseType] = useState('MISTAKEN'), [target, setTarget] = useState('AVAILABLE')
  const [formError, setFormError] = useState(''), [preview, setPreview] = useState<AdjustmentPreview | null>(null)
  const [reviewing, setReviewing] = useState<'REQUEST' | 'CONFIRM' | null>(null)
  const [feedback, setFeedback] = useState<{ message: string; tone: 'success' | 'error' } | null>(null)
  const flight = useRef(false)
  const role = getCurrentUser()?.role
  const canConfirm = role === 'LIBRARY_MANAGER' || role === 'ADMIN'
  const entry = entries[0]
  const fail = (error: unknown) => setFeedback({ tone: 'error', message: getApiErrorMessage(error, 'Không thể xử lý bút toán điều chỉnh.') })
  async function load() {
    if (flight.current) return
    flight.current = true; setBusy(true); setExpanded(true); setFeedback(null)
    try { setEntries(await incidentService.adjustments(record.id)); setLoaded(true) }
    catch (error) { setLoaded(false); fail(error) }
    finally { flight.current = false; setBusy(false) }
  }
  async function review(mode: 'REQUEST' | 'CONFIRM') {
    if (flight.current) return
    if (mode === 'REQUEST') {
      const invalid = validateReversalReason(reason); setFormError(invalid)
      if (invalid) return
    }
    flight.current = true; setBusy(true); setFeedback(null)
    try {
      setPreview(await incidentService.adjustmentPreview(record.id, mode === 'CONFIRM' && entry ? entry.targetStatus : target))
      setReviewing(mode)
    } catch (error) { fail(error) }
    finally { flight.current = false; setBusy(false) }
  }
  async function submit() {
    if (!reviewing || flight.current) return
    const mode = reviewing
    flight.current = true; setBusy(true); setFeedback(null)
    try {
      const result = mode === 'CONFIRM' && entry
        ? await incidentService.confirmAdjustment(record.id, entry.id)
        : await incidentService.requestAdjustment(record.id, reason, caseType, target)
      setEntries([result]); setReviewing(null); setPreview(null)
      setFeedback({ tone: 'success', message: mode === 'CONFIRM' ? 'Đã xác nhận bút toán, cập nhật khoản phải thu và trạng thái sách.' : 'Đã lập bút toán. Chờ Quản lý hoặc Admin xác nhận.' })
      // A failed refresh must not turn a committed transaction into a failure notice.
      try { onUpdated(await incidentService.detail(record.id)) }
      catch { setFeedback({ tone: 'error', message: 'Bút toán đã lưu. Chưa tải được trạng thái mới; bấm Làm mới lịch sử để thử lại.' }) }
    } catch (error) { fail(error) }
    finally { flight.current = false; setBusy(false) }
  }
  async function refresh() {
    await load()
    try { onUpdated(await incidentService.detail(record.id)) } catch (error) { fail(error) }
  }
  return <section className="space-y-3 border-t border-slate-200 pt-3">
    <Button variant="secondary" disabled={busy} onClick={() => { if (expanded) setExpanded(false); else void load() }}>
      {busy ? 'Đang xử lý...' : expanded ? 'Ẩn bút toán điều chỉnh' : 'Lập bút toán điều chỉnh / Xem lịch sử'}
    </Button>
    {!reviewing && feedback && <FeedbackAlert {...feedback} onDismiss={() => setFeedback(null)} />}
    {expanded && <div className="space-y-3">
      <h4 className="font-semibold">Bút toán điều chỉnh liên quan</h4>
      <Button variant="ghost" disabled={busy} onClick={() => void refresh()}>Làm mới lịch sử</Button>
      {!loaded && !busy && <p className="text-sm">Chưa tải được lịch sử. Vui lòng thử lại.</p>}
      {loaded && entry && <article className="space-y-2 rounded-xl border border-slate-200 p-3 text-sm">
        <p className="font-semibold">{entry.number} · {entry.status === 'CONFIRMED' ? 'Đã xác nhận' : 'Chờ xác nhận'}</p>
        <p>{cases[entry.caseType]} · Kết quả đề nghị: {adjustmentStatusLabel(entry.targetStatus)}</p>
        <p className="whitespace-pre-wrap break-words">Lý do: {entry.reason}</p>
        <p>Người lập: {entry.requesterName} · {formatLoanTimestamp(entry.requestedAt)}</p>
        {entry.status === 'CONFIRMED' ? <>
          <p>Người xác nhận: {entry.confirmerName} · {formatLoanTimestamp(entry.confirmedAt!)}</p>
          <p>Đảo bồi thường: <strong>{formatVnd(entry.reversedVnd ?? 0)}</strong></p>
          <p>Khoản nợ được hủy: {formatVnd(entry.cancelledDebtVnd ?? 0)} · Cần hoàn: {formatVnd(entry.refundDueVnd ?? 0)}</p>
          <p>Bồi thường còn phải thu: <strong>0 đồng</strong>. Chưa thực hiện chi hoàn tiền.</p>
          <p>Bản sao hiện tại: {adjustmentStatusLabel(record.item.copyStatus)} · Phiếu: {record.loanStatus === 'BORROWED' ? 'Đang mượn' : `Đã đóng · ${closureLabel(record.loanStatus)}`}</p>
          <details><summary className="cursor-pointer text-blue-700">Dữ liệu đối soát trước và sau</summary>
            <Snapshot label="Trước" value={entry.beforeData} /><Snapshot label="Sau" value={entry.afterData} />
          </details>
        </> : <>
          <p>Chưa thay đổi khoản phải thu, bản sao hoặc phiếu mượn.</p>
          {canConfirm ? <Button disabled={busy} onClick={() => void review('CONFIRM')}>Xem trước và xác nhận bút toán</Button>
            : <p>Chỉ Quản lý hoặc Admin được xác nhận.</p>}
        </>}
      </article>}
      {loaded && !entry && record.item.copyStatus === 'REMOVED' && <form className="space-y-3" onSubmit={event => { event.preventDefault(); void review('REQUEST') }}>
        <p className="text-sm">Đảo toàn bộ bồi thường bằng bút toán riêng. Phí trễ hạn và biên lai gốc giữ nguyên.</p>
        <label className="block space-y-1 text-sm"><span>Trường hợp điều chỉnh *</span>
          <select value={caseType} disabled={busy} onChange={event => { setCaseType(event.target.value) }} className="w-full rounded-xl border border-slate-300 p-3">
            {Object.entries(cases).filter(([key]) => key !== 'FOUND' || record.type === 'LOST').map(([key, label]) => <option key={key} value={key}>{label}</option>)}
          </select>
        </label>
        <label className="block space-y-1 text-sm"><span>Trạng thái bản sao sau điều chỉnh *</span>
          <select value={target} disabled={busy} onChange={event => setTarget(event.target.value)} className="w-full rounded-xl border border-slate-300 p-3">
            <option value="BORROWED">Đang mượn — bạn đọc vẫn giữ sách</option>
            <option value="AVAILABLE">Đã nhận lại — đủ điều kiện cho mượn</option><option value="REPAIR">Đã nhận lại — cần sửa chữa</option>
          </select>
        </label>
        <label className="block space-y-1 text-sm"><span>Lý do điều chỉnh *</span>
          <textarea value={reason} rows={3} maxLength={1900} disabled={busy} aria-invalid={!!formError} onChange={event => { setReason(event.target.value); setFormError('') }} className="w-full rounded-xl border border-slate-300 p-3" />
        </label>
        {formError && <p role="alert" className="text-sm text-red-700">{formError}</p>}
        <Button type="submit" disabled={busy}>Xem trước bút toán</Button>
      </form>}
      {loaded && !entry && record.item.copyStatus !== 'REMOVED' && <p>Không có bút toán. Trạng thái sách không phù hợp để lập điều chỉnh.</p>}
    </div>}
    {reviewing && preview && <Modal label="Xác nhận bút toán điều chỉnh" busy={busy} onClose={() => { if (!busy) setReviewing(null) }}>
      <div className="w-full max-w-xl space-y-4 rounded-2xl bg-white p-5">
        <h3 className="font-semibold">{reviewing === 'CONFIRM' ? 'Xác nhận bút toán điều chỉnh' : 'Lập bút toán chờ xác nhận'}</h3>
        <p className="break-words text-sm">Sự cố #{record.id} · {record.item.loanNumber} · {record.item.barcode} · Người ghi nhận: {record.actorName}</p>
        <p className="whitespace-pre-wrap break-words text-sm">Lý do: {reviewing === 'CONFIRM' ? entry?.reason : reason.trim()}</p>
        <dl className="grid gap-3 text-sm sm:grid-cols-2">
          <div><dt>Bồi thường gốc</dt><dd>{formatVnd(record.compensationVnd)}</dd></div>
          <div><dt>Khoản đảo</dt><dd>{formatVnd(preview.reversedVnd)}</dd></div>
          <div><dt>Nợ được hủy</dt><dd>{formatVnd(preview.cancelledDebtVnd)}</dd></div>
          <div><dt>Số tiền cần hoàn</dt><dd>{formatVnd(preview.refundDueVnd)}</dd></div>
          <div><dt>Bản sao dự kiến</dt><dd>{adjustmentStatusLabel(preview.copyStatus)}</dd></div>
          <div><dt>Phiếu dự kiến</dt><dd>{preview.loanStatus === 'BORROWED' ? 'Đang mượn' : `Đã đóng · ${closureLabel(preview.loanStatus)}`}</dd></div>
        </dl>
        <p className="text-sm">Phí trễ hạn {formatVnd(record.overdueFee?.amountVnd ?? 0)} giữ nguyên. Sau xác nhận bồi thường còn phải thu là 0 đồng; số tiền cần hoàn được ghi nhận riêng, chưa chi hoàn.</p>
        {['AVAILABLE', 'HELD'].includes(preview.copyStatus) && <p className="text-sm">Sách được ưu tiên cho hàng đợi đặt trước hợp lệ; nếu có người chờ nhận, trạng thái thực tế là Đang giữ và chưa tăng bản rảnh.</p>}
        <p className="text-sm">Số tiền sẽ được tính lại khi xác nhận theo khoản đã thu thực tế. Dữ liệu gốc và bút toán đã xác nhận không được sửa hoặc xóa.</p>
        {feedback && <FeedbackAlert {...feedback} onDismiss={() => setFeedback(null)} />}
        <div className="flex flex-wrap justify-end gap-2"><Button variant="secondary" disabled={busy} onClick={() => setReviewing(null)}>Quay lại</Button>
          <Button loading={busy} onClick={() => void submit()}>{reviewing === 'CONFIRM' ? 'Xác nhận đảo giao dịch' : 'Lập bút toán chờ duyệt'}</Button>
        </div>
      </div>
    </Modal>}
  </section>
}
function Snapshot({ label, value }: { label: string; value: string | null }) {
  if (!value) return null
  try {
    const data = JSON.parse(value)
    return <dl className="mt-2 grid gap-2 rounded-lg bg-slate-50 p-3 sm:grid-cols-2">
      <div><dt>{label}: trạng thái sách</dt><dd>{adjustmentStatusLabel(data.copy?.status ?? '')}</dd></div>
      <div><dt>Trạng thái phiếu</dt><dd>{data.loanStatus === 'BORROWED' ? 'Đang mượn' : closureLabel(data.loanStatus)}</dd></div>
      <div><dt>Bồi thường gốc / Đã thu</dt><dd>{formatVnd(data.fee?.amount_vnd ?? 0)} / {formatVnd(data.fee?.paid_amount_vnd ?? 0)}</dd></div>
      <div><dt>Thời điểm đóng lượt mượn</dt><dd>{data.item?.returned_at ? formatLoanTimestamp(data.item.returned_at) : 'Chưa đóng'}</dd></div>
    </dl>
  } catch { return <p>Không đọc được ảnh chụp dữ liệu đối soát.</p> }
}
