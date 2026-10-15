import TimedFieldMessage from '../../components/ui/TimedFieldMessage'
import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import Modal from '../../components/ui/Modal'
import Button from '../../components/ui/Button'
import FeedbackAlert from '../../components/ui/FeedbackAlert'
import StatusBadge from '../../components/ui/StatusBadge'
import LockLibraryCardDialog from '../s1-04-library-card/LockLibraryCardDialog'
import { getApiErrorMessage } from '../s1-02-user-management/accountService'
import { formatLoanTimestamp } from '../s3-01-loans/loanService'
import { cardLockSuggestionService, suggestionMoney, suggestionReason, suggestionStatus, type SuggestionDetail, type SuggestionDecision, type SuggestionEvidence } from './cardLockSuggestionService'
export default function CardLockSuggestionDetailsDialog({ id, onClose, onChanged }: {
  id: number; onClose: () => void; onChanged: (result: SuggestionDecision) => void
}) {
  const [data, setData] = useState<SuggestionDetail | null>(null)
  const [loading, setLoading] = useState(true), [revision, setRevision] = useState(0)
  const [error, setError] = useState(''), [fieldError, setFieldError] = useState('')
  const [locking, setLocking] = useState(false), [ignoring, setIgnoring] = useState(false)
  const [reason, setReason] = useState(''), [busy, setBusy] = useState(false)
  const pending = useRef(false), decision = useRef<SuggestionDecision | null>(null)
  useEffect(() => {
    let active = true
    setLoading(true); setData(null)
    cardLockSuggestionService.detail(id).then(value => { if (active) setData(value) })
      .catch(err => { if (active) setError(getApiErrorMessage(err, 'Không tải được chi tiết đề xuất.')) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [id, revision])
  async function ignore() {
    if (pending.current) return
    const trimmed = reason.trim()
    if (!trimmed || trimmed.length > 1000) {
      setFieldError('Bắt buộc nhập lý do bỏ qua, tối đa 1000 ký tự.'); setError('Bắt buộc nhập lý do bỏ qua, tối đa 1000 ký tự.'); return
    }
    pending.current = true; setBusy(true); setError(''); setFieldError('')
    try { onChanged(await cardLockSuggestionService.ignore(id, trimmed)) }
    catch (err) { setError(getApiErrorMessage(err, 'Không bỏ qua được đề xuất.')); setIgnoring(false); setRevision(v => v + 1) }
    finally { pending.current = false; setBusy(false) }
  }
  const actionable = data?.suggestion.status === 'PENDING'
  return <>
    <Modal onClose={onClose} busy={busy} label="Chi tiết đề xuất khóa thẻ">
      <div className="w-full max-w-3xl rounded-2xl bg-white shadow-xl">
        <div className="border-b border-slate-200 p-5"><h2 className="text-lg font-bold">Chi tiết đề xuất #{id}</h2>
          {data && <p className="break-words text-sm text-slate-600">{data.suggestion.readerName} · {data.suggestion.cardNumber}</p>}</div>
        <div className="space-y-5 p-5">
          {error && <FeedbackAlert tone="error" message={error} onDismiss={() => setError('')} />}
          {loading && <p role="status">Đang kiểm tra lại đề xuất...</p>}
          {!loading && !data && <Button onClick={() => setRevision(v => v + 1)}>Thử lại</Button>}
          {data && <>
            <div className="flex flex-wrap items-center gap-3"><span className="text-sm font-semibold">{suggestionStatus[data.suggestion.status]}</span><StatusBadge status={data.card.status} /></div>
            <p className="text-sm font-semibold">Lý do đề xuất: {suggestionReason(data.suggestion.reasonCode)}</p>
            <p className="text-sm leading-6 text-slate-600">Hiện tại: quá hạn lớn nhất {data.currentMaxOverdueDays} ngày mở cửa · Nợ {suggestionMoney(data.currentUnpaidFeesVnd)} · Ngưỡng {suggestionMoney(data.currentDebtThresholdVnd)}.<br />{suggestionReason(data.currentReasonCode)}.</p>
            <EvidenceView evidence={data.evidence} title={actionable ? 'Các phiếu quá hạn và khoản phí còn nợ' : 'Dữ liệu được lưu tại lần đánh giá trước quyết định'} />
            {!actionable && <details className="rounded-xl border border-slate-200 p-3"><summary className="cursor-pointer font-semibold">Xem các phiếu và khoản nợ hiện tại</summary><EvidenceView evidence={data.currentEvidence} title="Dữ liệu hiện tại" /></details>}
            {data.suggestion.decidedAt && <dl className="space-y-2 break-words text-sm">
              <div><dt className="text-slate-500">Người quyết định</dt><dd>{data.suggestion.deciderName} (#{data.suggestion.decidedBy ?? 'đã xóa tài khoản'})</dd></div>
              <div><dt className="text-slate-500">Thời điểm</dt><dd>{formatLoanTimestamp(data.suggestion.decidedAt)}</dd></div>
              <div><dt className="text-slate-500">Lý do quyết định</dt><dd className="whitespace-pre-wrap">{data.suggestion.decisionReason}</dd></div>
              <div><dt className="text-slate-500">Trạng thái thẻ ngay sau quyết định</dt><dd><StatusBadge status={data.suggestion.cardStatusAfterDecision ?? ''} /></dd></div>
              {data.suggestion.status === 'ACCEPTED' && <div><dt className="text-slate-500">Thời hạn khóa</dt><dd>{data.suggestion.lockEndsAt ? formatLoanTimestamp(data.suggestion.lockEndsAt) : 'Đến khi mở thủ công'}</dd></div>}
            </dl>}
            {data.suggestion.resolvedAt && <p className="text-sm text-slate-500">Đợt vi phạm kết thúc lúc {formatLoanTimestamp(data.suggestion.resolvedAt)}.</p>}
            {actionable && <p className="rounded-xl border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900">Chấp nhận sẽ khóa thẻ ngay, ngăn mượn mới, đặt giữ và gia hạn. Trả sách và thu phí vẫn được phép. Hệ thống kiểm tra lại trước khi ghi nhận.</p>}
            {actionable && ignoring && <form noValidate onSubmit={e => { e.preventDefault(); void ignore() }} className="space-y-3">
              <label htmlFor="ignore-suggestion-reason" className="block text-sm font-semibold">Lý do bỏ qua *</label>
              <textarea id="ignore-suggestion-reason" required maxLength={1000} rows={3} value={reason} disabled={busy}
                aria-invalid={!!fieldError} aria-describedby={fieldError ? 'ignore-suggestion-error' : undefined}
                onChange={e => { setReason(e.target.value); setFieldError('') }} className="w-full rounded-xl border border-slate-300 p-3 text-sm" />
              {fieldError && <TimedFieldMessage id="ignore-suggestion-error" className="text-sm text-red-700">{fieldError}</TimedFieldMessage>}
              <p className="text-sm text-slate-600">Đề xuất không sinh lại trong cùng đợt vi phạm; quyết định và lý do được lưu trong lịch sử.</p>
              <div className="flex flex-wrap gap-2"><Button type="submit" loading={busy}>Xác nhận bỏ qua</Button><Button type="button" variant="secondary" disabled={busy} onClick={() => setIgnoring(false)}>Hủy bỏ qua</Button></div>
            </form>}
          </>}
        </div>
        <div className="flex flex-wrap justify-end gap-3 border-t border-slate-200 p-5">
          <Button variant="secondary" disabled={busy || locking || loading} onClick={() => setRevision(v => v + 1)}>Làm mới chi tiết</Button>
          <Button variant="secondary" disabled={busy || locking} onClick={onClose}>Đóng</Button>
          {actionable && !ignoring && <><Button variant="secondary" disabled={busy} onClick={() => { setIgnoring(true); setError(''); setFieldError('') }}>Bỏ qua đề xuất</Button>
            <Button variant="danger" disabled={busy} onClick={() => { decision.current = null; setLocking(true) }}>Chấp nhận khóa</Button></>}
        </div>
      </div>
    </Modal>
    {locking && data && <LockLibraryCardDialog card={data.card} initialReason={suggestionReason(data.currentReasonCode)}
      onClose={() => setLocking(false)} onConfirmLock={async (reason, endsAt) => {
        const result = await cardLockSuggestionService.accept(id, reason, endsAt)
        decision.current = result; return result.card
      }} onLocked={() => { if (decision.current) onChanged(decision.current) }} />}
  </>
}
function EvidenceView({ evidence, title }: { evidence: SuggestionEvidence; title: string }) {
  return <section className="space-y-3"><h3 className="font-semibold text-slate-900">{title}</h3>
    {evidence.overdueItems.length === 0 ? <p className="text-sm text-slate-500">Không có bản sách quá hạn chưa trả.</p> : <ul className="space-y-2">{evidence.overdueItems.map(item => <li key={item.itemId} className="rounded-xl border border-slate-200 p-3 text-sm">
      <Link to={`/loans/${item.loanId}`} className="font-semibold text-blue-700 hover:underline">Phiếu {item.loanNumber}</Link>
      <p className="break-words">{item.bookTitle} · {item.barcode}</p><p className="text-slate-600">Hạn trả: {formatLoanTimestamp(item.dueAt)} · Quá hạn {item.overdueDays} ngày mở cửa</p>
    </li>)}</ul>}
    {evidence.fees.length === 0 ? <p className="text-sm text-slate-500">Không có khoản phí còn nợ.</p> : <ul className="space-y-2">{evidence.fees.map(fee => <li key={fee.id} className="rounded-xl border border-slate-200 p-3 text-sm">
      <p className="break-words font-semibold">Khoản phí #{fee.id} · {fee.description || 'Khoản phải thu'}</p>
      <p className="break-words text-slate-600">Gốc {suggestionMoney(fee.originalVnd)} · Đã thu {suggestionMoney(fee.paidVnd)} · Miễn giảm {suggestionMoney(fee.waivedVnd)}</p>
      <p className="font-semibold">Còn nợ: {suggestionMoney(fee.outstandingVnd)}</p>
    </li>)}</ul>}
  </section>
}
