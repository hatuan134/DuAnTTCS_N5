import { useRef, useState } from 'react'
import Modal from '../../components/ui/Modal'
import Button from '../../components/ui/Button'
import FeedbackAlert from '../../components/ui/FeedbackAlert'
import { getApiErrorMessage } from '../s1-02-user-management/accountService'
import { libraryCardService } from './libraryCardService'
import type { LibraryCard } from './libraryCardService'

export function sevenDaysLater() {
  const d = new Date(Date.now() + 7 * 24 * 60 * 60 * 1000)
  // datetime-local is explicitly interpreted in the library's Vietnam timezone.
  return new Date(d.getTime() + 7 * 60 * 60 * 1000).toISOString().slice(0, 16)
}

export function validateLockForm(reason: string, indefinite: boolean, endsAt: string) {
  if (!reason.trim()) return 'Bắt buộc nhập lý do khóa thẻ.'
  if (reason.trim().length > 1000) return 'Lý do khóa thẻ không được vượt quá 1000 ký tự.'
  if (!indefinite) {
    const timestamp = Date.parse(`${endsAt}:00+07:00`)
    if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(endsAt) || !Number.isFinite(timestamp)
        || new Date(timestamp + 7 * 60 * 60 * 1000).toISOString().slice(0, 16) !== endsAt
        || endsAt.startsWith('0000') || timestamp <= Date.now()) {
      return 'Thời điểm hết hạn khóa phải hợp lệ và sau thời điểm hiện tại.'
    }
  }
  return ''
}

export default function LockLibraryCardDialog({ card, onClose, onLocked }: {
  card: LibraryCard; onClose: () => void; onLocked: (card: LibraryCard) => void
}) {
  const [reason, setReason] = useState('')
  const [indefinite, setIndefinite] = useState(false)
  const [endsAt, setEndsAt] = useState(sevenDaysLater)
  const [submitting, setSubmitting] = useState(false)
  const [notice, setNotice] = useState('')
  const pending = useRef(false)
  async function submit() {
    if (pending.current) return
    setNotice('')
    const invalid = validateLockForm(reason, indefinite, endsAt)
    if (invalid) { setNotice(invalid); return }
    pending.current = true; setSubmitting(true)
    try {
      const result = await libraryCardService.lockCard(card.id, reason.trim(),
        indefinite ? null : `${endsAt}:00+07:00`)
      onLocked(result)
    } catch (error) { setNotice(getApiErrorMessage(error, 'Không thể khóa thẻ. Vui lòng thử lại.')) }
    finally { pending.current = false; setSubmitting(false) }
  }
  return <Modal onClose={onClose} busy={submitting} label="Khóa thẻ thư viện">
    <form noValidate onSubmit={e => { e.preventDefault(); void submit() }}
      className="w-full max-w-lg rounded-2xl bg-white shadow-xl">
      <div className="border-b border-slate-200 p-5">
        <h2 className="text-lg font-bold text-slate-900">Khóa thẻ thư viện</h2>
        <p className="mt-1 break-words text-sm text-slate-600">{card.readerName} · {card.cardNumber}</p>
        <p className="mt-2 text-sm text-slate-600">Nợ phí: {Number(card.unpaidFees || 0).toLocaleString('vi-VN')} ₫</p>
      </div>
      <div className="space-y-4 p-5">
        {notice && <FeedbackAlert message={notice} tone="error" onDismiss={() => setNotice('')} />}
        <div>
          <label htmlFor="card-lock-reason" className="mb-1.5 block text-sm font-medium">Lý do khóa *</label>
          <textarea id="card-lock-reason" required maxLength={1000} rows={3} value={reason}
            disabled={submitting} onChange={e => setReason(e.target.value)}
            placeholder="Ví dụ: Vi phạm quy định thư viện"
            className="w-full resize-y rounded-xl border border-slate-300 px-3 py-2.5 text-sm" />
        </div>
        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" checked={indefinite} disabled={submitting}
            onChange={e => setIndefinite(e.target.checked)} /> Khóa đến khi mở thủ công
        </label>
        {!indefinite && <div>
          <label htmlFor="card-lock-end" className="mb-1.5 block text-sm font-medium">Thời điểm hết hạn khóa * (giờ Việt Nam)</label>
          <input id="card-lock-end" type="datetime-local" required value={endsAt} disabled={submitting}
            max="9999-12-31T23:59" onChange={e => setEndsAt(e.target.value)}
            className="w-full min-w-0 rounded-xl border border-slate-300 px-3 py-2.5 text-sm" />
          <p className="mt-1 text-xs text-slate-500">Mặc định 7 ngày. Khóa có hiệu lực ngay khi xác nhận và tự hết hiệu lực tại thời điểm này.</p>
        </div>}
        <p className="rounded-xl border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900">
          Bạn đọc sẽ không thể mượn mới, đặt giữ hoặc gia hạn. Phiếu mượn và đặt giữ cũ được giữ nguyên;
          trả sách và thu phí vẫn thực hiện được. Khóa vô thời hạn không tự hết hiệu lực. Hãy cân nhắc trước khi xác nhận.
        </p>
      </div>
      <div className="flex flex-wrap justify-end gap-3 border-t border-slate-200 p-5">
        <Button type="button" variant="secondary" disabled={submitting} onClick={onClose}>Hủy</Button>
        <Button type="submit" variant="danger" loading={submitting}>Xác nhận khóa thẻ</Button>
      </div>
    </form>
  </Modal>
}
