import { useEffect, useRef, useState } from 'react'
import Modal from '../../components/ui/Modal'
import Button from '../../components/ui/Button'
import FeedbackAlert from '../../components/ui/FeedbackAlert'
import StatusBadge from '../../components/ui/StatusBadge'
import { getCurrentUser } from '../../core/auth/authStorage'
import { getApiErrorMessage } from '../s1-02-user-management/accountService'
import ManualCardLockDetails, { formatLockTime } from './ManualCardLockDetails'
import { libraryCardService, type LibraryCard, type LibraryCardDetails } from './libraryCardService'

export function validateUnlockReason(reason: string) {
  if (!reason.trim()) return 'Bắt buộc nhập lý do mở khóa thẻ.'
  if (reason.trim().length > 1000) return 'Lý do mở khóa không được vượt quá 1000 ký tự.'
  return ''
}

const statuses: Record<string, string> = {
  ACTIVE: 'Hoạt động', LOCKED: 'Bị khóa', DISABLED: 'Ngừng hoạt động', EXPIRED: 'Hết hạn', UNKNOWN: 'Không ghi nhận',
}
export function cardStatusLabel(status: string) { return statuses[status] ?? status }

export default function LibraryCardDetailsDialog({ cardId, onClose, onUpdated }: {
  cardId: number; onClose: () => void; onUpdated: (card: LibraryCard) => void
}) {
  const [details, setDetails] = useState<LibraryCardDetails | null>(null)
  const [loading, setLoading] = useState(true), [submitting, setSubmitting] = useState(false)
  const [revision, setRevision] = useState(0), [confirming, setConfirming] = useState(false)
  const [reason, setReason] = useState(''), [fieldError, setFieldError] = useState('')
  const [feedback, setFeedback] = useState<{ message: string; tone: 'success' | 'error' } | null>(null)
  const [loadFailed, setLoadFailed] = useState(false)
  const flight = useRef(false)
  const canUnlock = ['LIBRARY_MANAGER', 'ADMIN'].includes(getCurrentUser()?.role ?? '')
  useEffect(() => {
    let active = true
    setLoading(true); setLoadFailed(false)
    libraryCardService.getDetails(cardId).then(result => {
      if (!active) return
      setDetails(result)
    }).catch(error => {
      if (!active) return
      setLoadFailed(true)
      setFeedback({ tone: 'error', message: getApiErrorMessage(error, 'Không tải được chi tiết thẻ.') })
    }).finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [cardId, revision])

  async function unlock() {
    if (flight.current || !canUnlock || loading || loadFailed || !details?.card.manualLock?.active) return
    const invalid = validateUnlockReason(reason)
    setFieldError(invalid)
    if (invalid) { setFeedback({ tone: 'error', message: invalid }); return }
    flight.current = true; setSubmitting(true); setFeedback(null)
    try {
      const card = await libraryCardService.unlockCard(cardId, reason.trim(), details.card.manualLock.startedAt)
      setDetails(current => current ? { ...current, card } : current)
      onUpdated(card); setConfirming(false); setReason('')
      setFeedback({ tone: 'success', message: `Đã kết thúc khóa thủ công. Trạng thái thẻ: ${cardStatusLabel(card.status)}.${card.unpaidFees > 0 ? ' Bạn đọc vẫn còn nợ phí; điều kiện giao dịch vẫn áp dụng.' : ''}` })
      setRevision(value => value + 1)
    } catch (error) {
      setFeedback({ tone: 'error', message: getApiErrorMessage(error, 'Không thể mở khóa thẻ. Vui lòng thử lại.') })
      // Recheck authoritative status, including an expired/replaced lock or another manager's release.
      setConfirming(false); setRevision(value => value + 1)
    } finally { flight.current = false; setSubmitting(false) }
  }

  return <Modal onClose={onClose} busy={submitting} label="Chi tiết thẻ thư viện">
    <div className="w-full max-w-3xl rounded-2xl bg-white shadow-xl">
      <div className="border-b border-slate-200 p-5">
        <h2 className="text-lg font-bold">Chi tiết thẻ thư viện</h2>
        {details && <p className="mt-1 break-words text-sm text-slate-600">{details.card.readerName} · {details.card.cardNumber}</p>}
      </div>
      <div className="space-y-4 p-5">
        {feedback && <FeedbackAlert {...feedback} onDismiss={() => setFeedback(null)} />}
        {loading && <p role="status">Đang tải chi tiết và lịch sử thẻ...</p>}
        {loadFailed && <div className="space-y-2"><p>Chưa tải được chi tiết thẻ.</p>
          <Button variant="secondary" onClick={() => setRevision(v => v + 1)}>Thử lại</Button></div>}
        {details && !loading && !loadFailed && <>
          <div className="flex flex-wrap items-center justify-between gap-3">
            <StatusBadge status={details.card.status} />
            <Button variant="secondary" disabled={submitting} onClick={() => { setConfirming(false); setFieldError(''); setRevision(v => v + 1) }}>Làm mới chi tiết</Button>
          </div>
          <div className="grid gap-2 text-sm sm:grid-cols-2">
            <p>Mã bạn đọc: {details.card.memberCode}</p><p>Loại thẻ: {details.card.cardTypeName}</p>
            <p>Hết hạn thẻ: {new Date(`${details.card.expiresAt}T00:00:00`).toLocaleDateString('vi-VN')}</p>
            <p>Nợ phí: {Number(details.card.unpaidFees || 0).toLocaleString('vi-VN')} ₫</p>
          </div>
          {details.card.manualLock?.active ? <div className="space-y-2 rounded-xl border border-red-200 bg-red-50 p-4 text-red-800">
            <ManualCardLockDetails lock={details.card.manualLock} />
            <p className="break-words text-sm"><strong>Người khóa:</strong> {details.lockActorName}
              {details.card.manualLock.actorUserId != null && ` (ID ${details.card.manualLock.actorUserId})`}</p>
          </div> : <p className="text-sm text-slate-600">Thẻ không còn khóa thủ công có hiệu lực. Các khóa khác và điều kiện giao dịch vẫn áp dụng.</p>}
          {canUnlock && details.card.manualLock?.active && !confirming && <Button disabled={submitting}
            onClick={() => { setFeedback(null); setFieldError(''); setConfirming(true) }}>Mở khóa thẻ</Button>}
          {canUnlock && confirming && details.card.manualLock?.active && <form noValidate
            onSubmit={e => { e.preventDefault(); void unlock() }} className="space-y-3 rounded-xl border border-slate-200 p-4">
            <label htmlFor="card-unlock-reason" className="block text-sm font-medium">Lý do mở khóa *</label>
            <textarea id="card-unlock-reason" required rows={3} maxLength={1000} value={reason} disabled={submitting}
              aria-invalid={Boolean(fieldError)} aria-describedby={fieldError ? 'card-unlock-error' : undefined}
              onChange={e => { setReason(e.target.value); setFieldError('') }}
              placeholder="Ví dụ: Bạn đọc đã hoàn thành xử lý vi phạm"
              className="w-full resize-y rounded-xl border border-slate-300 px-3 py-2.5 text-sm" />
            {fieldError && <p id="card-unlock-error" className="text-sm text-red-700">{fieldError}</p>}
            <p className="text-sm text-slate-600">Chỉ kết thúc lần khóa thủ công đang hiển thị. Nợ phí, quá hạn, hạn thẻ và khóa tài khoản giữ nguyên; bạn đọc chỉ giao dịch khi đủ điều kiện.</p>
            <div className="flex flex-wrap justify-end gap-3">
              <Button type="button" variant="secondary" disabled={submitting} onClick={() => { setConfirming(false); setFieldError('') }}>Hủy mở khóa</Button>
              <Button type="submit" loading={submitting}>Xác nhận mở khóa</Button>
            </div>
          </form>}
          <section className="space-y-3" aria-label="Lịch sử khóa và mở khóa">
            <h3 className="font-semibold">Lịch sử khóa và mở khóa</h3>
            {!details.history.length && <p className="text-sm text-slate-500">Chưa có lịch sử khóa hoặc mở khóa.</p>}
            {details.history.map(entry => <article key={entry.id} className="min-w-0 space-y-2 break-words rounded-xl border border-slate-200 p-4 text-sm">
              <div className="flex flex-wrap justify-between gap-2"><strong>{entry.action === 'LOCK' ? 'Khóa thẻ' : 'Mở khóa thẻ'}</strong>
                <span className="text-slate-500">{formatLockTime(entry.performedAt)} (giờ Việt Nam)</span></div>
              <p>Người thực hiện: {entry.actorName}{entry.actorUserId != null && ` (ID ${entry.actorUserId})`}</p>
              <p>Lý do: {entry.reason}</p>
              <p>Trạng thái: {cardStatusLabel(entry.previousStatus)} → {cardStatusLabel(entry.resultingStatus)}</p>
              <p>Thời điểm khóa: {formatLockTime(entry.lockStartedAt)} · {entry.lockEndsAt ? `Hạn khóa: ${formatLockTime(entry.lockEndsAt)}` : 'Khóa vô thời hạn'}</p>
              {entry.action === 'UNLOCK' && <p>Người khóa: {entry.lockActorName} · Lý do khóa: {entry.lockReason}</p>}
            </article>)}
          </section>
        </>}
      </div>
      <div className="flex justify-end border-t border-slate-200 p-5"><Button variant="secondary" disabled={submitting} onClick={onClose}>Đóng</Button></div>
    </div>
  </Modal>
}
