import AccessibleModal from '../../components/ui/Modal'
import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { History, PhoneCall, X } from 'lucide-react'
import Button from '../../components/ui/Button'
import FeedbackAlert from '../../components/ui/FeedbackAlert'
import { getApiErrorMessage } from '../s1-02-user-management/accountService'
import { formatLoanTimestamp } from '../s3-01-loans/loanService'
import { overdueLoanService } from './overdueLoanService'
import type { OverdueContact } from './overdueLoanService'

type Props = {
  loanId: number
  loanNumber: string
  canRecord: boolean
  onClose: () => void
  onRecorded: (contact: OverdueContact) => void
}

export default function OverdueContactDialog({ loanId, loanNumber, canRecord, onClose, onRecorded }: Props) {
  const [history, setHistory] = useState<OverdueContact[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')
  const [note, setNote] = useState('')
  const [fieldError, setFieldError] = useState('')
  const [notice, setNotice] = useState<{ message: string; tone: 'success' | 'error' } | null>(null)
  const [saving, setSaving] = useState(false)
  const busyRef = useRef(false)
  const mountedRef = useRef(true)
  const closeRef = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    mountedRef.current = true
    closeRef.current?.focus()
    let active = true
    overdueLoanService.history(loanId)
      .then((rows) => { if (active) setHistory(rows) })
      .catch((error: unknown) => {
        if (active) setLoadError(getApiErrorMessage(error, 'Không tải được lịch sử liên hệ.'))
      })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false; mountedRef.current = false }
  }, [loanId])

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!canRecord || busyRef.current) return
    const trimmed = note.trim()
    if (!trimmed || trimmed.length > 1000) {
      setFieldError('Vui lòng nhập ghi chú từ 1 đến 1000 ký tự.')
      return
    }
    busyRef.current = true
    setSaving(true)
    setFieldError('')
    setNotice(null)
    try {
      const contact = await overdueLoanService.record(loanId, trimmed)
      if (!mountedRef.current) return
      setHistory((previous) => [contact, ...previous].sort(
        (a, b) => b.contactedAt.localeCompare(a.contactedAt) || b.id - a.id,
      ))
      setNote('')
      onRecorded(contact)
      setNotice({ message: 'Đã ghi nhận lần liên hệ và cập nhật lịch sử.', tone: 'success' })
    } catch (error: unknown) {
      if (mountedRef.current) setNotice({
        message: getApiErrorMessage(error, 'Không ghi nhận được liên hệ. Vui lòng thử lại.'),
        tone: 'error',
      })
    } finally {
      busyRef.current = false
      if (mountedRef.current) setSaving(false)
    }
  }

  return (
    <AccessibleModal onClose={onClose} busy={saving} labelledBy="overdue-contact-heading">
      <section
        className="flex max-h-[92vh] w-full max-w-2xl flex-col overflow-hidden rounded-2xl bg-white shadow-2xl">
        <div className="flex items-start justify-between gap-3 border-b border-slate-200 px-5 py-4">
          <div className="min-w-0">
            <h2 id="overdue-contact-heading" className="text-lg font-bold text-slate-900">Liên hệ nhắc trả sách</h2>
            <p className="mt-1 break-all text-sm text-slate-600">Phiếu mượn: <strong>{loanNumber}</strong></p>
          </div>
          <button ref={closeRef} type="button" aria-label="Đóng cửa sổ liên hệ" disabled={saving}
            onClick={onClose} className="rounded-lg p-2 text-slate-600 hover:bg-slate-100 disabled:opacity-50"><X size={20} /></button>
        </div>
        <div className="space-y-5 overflow-y-auto px-5 py-5">
          {canRecord && (
            <form onSubmit={(event) => { void submit(event) }} noValidate className="space-y-3 rounded-xl border border-blue-100 bg-blue-50/40 p-4">
              <h3 className="flex items-center gap-2 font-semibold text-slate-900"><PhoneCall size={17} /> Đánh dấu đã liên hệ</h3>
              <p className="text-xs leading-5 text-slate-600">Thời điểm liên hệ được hệ thống ghi tự động khi lưu, theo giờ Việt Nam. Tên Thủ thư lấy từ tài khoản đang đăng nhập.</p>
              <div>
                <label htmlFor="overdue-contact-note" className="mb-1.5 block text-sm font-semibold text-slate-800">Ghi chú lần liên hệ <span className="text-red-600">*</span></label>
                <textarea id="overdue-contact-note" rows={3} maxLength={1000} required value={note} disabled={saving}
                  placeholder="Ví dụ: Đã gọi điện, bạn đọc hẹn trả vào ngày mai."
                  onChange={(event) => { setNote(event.target.value); setFieldError('') }}
                  aria-invalid={Boolean(fieldError)} aria-describedby={fieldError ? 'overdue-contact-error' : undefined}
                  className="w-full rounded-xl border border-slate-300 bg-white px-3 py-2.5 text-sm text-slate-900 outline-none focus:border-blue-500 focus:ring-4 focus:ring-blue-100" />
                <p className="mt-1 text-xs text-slate-500">{note.length}/1000 ký tự</p>
                {fieldError && <p id="overdue-contact-error" role="alert" className="mt-1 text-sm text-red-700">{fieldError}</p>}
              </div>
              <Button type="submit" size="sm" loading={saving} disabled={!note.trim()}><PhoneCall size={15} /> Lưu lần liên hệ</Button>
            </form>
          )}
          {notice && <FeedbackAlert message={notice.message} tone={notice.tone} onDismiss={() => setNotice(null)} />}
          <div>
            <h3 className="flex items-center gap-2 text-base font-semibold text-slate-900"><History size={18} /> Lịch sử liên hệ ({history.length})</h3>
            {loading && <p role="status" className="mt-3 text-sm text-slate-600">Đang tải lịch sử liên hệ…</p>}
            {loadError && <div className="mt-3"><FeedbackAlert message={loadError} tone="error" onDismiss={() => setLoadError('')} />
              <Button type="button" variant="secondary" size="sm" className="mt-2" onClick={() => {
                setLoading(true)
                overdueLoanService.history(loanId).then((rows) => { if (mountedRef.current) { setHistory(rows); setLoadError('') } })
                  .catch((error: unknown) => { if (mountedRef.current) setLoadError(getApiErrorMessage(error, 'Không tải được lịch sử.')) })
                  .finally(() => { if (mountedRef.current) setLoading(false) })
              }}>Tải lại lịch sử</Button>
            </div>}
            {!loading && !loadError && history.length === 0 && (
              <p className="mt-3 rounded-xl border border-dashed border-slate-300 px-4 py-6 text-center text-sm text-slate-600">Chưa liên hệ. Phiếu này chưa có lịch sử nhắc trả sách.</p>
            )}
            {history.length > 0 && <ol className="mt-3 space-y-3" aria-label="Các lần liên hệ theo thứ tự mới nhất trước">
              {history.map((contact, index) => (
                <li key={contact.id} className="rounded-xl border border-slate-200 p-4 text-sm">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <strong className="text-slate-900">{index === 0 ? 'Lần gần nhất' : `Lần trước ${index}`}</strong>
                    <time dateTime={contact.contactedAt} className="font-medium text-blue-700">{formatLoanTimestamp(contact.contactedAt)}</time>
                  </div>
                  <p className="mt-2 text-slate-600">Thủ thư: <strong className="text-slate-800">{contact.staffName}</strong></p>
                  <p className="mt-2 whitespace-pre-wrap break-words text-slate-800">{contact.note}</p>
                </li>
              ))}
            </ol>}
          </div>
        </div>
        <div className="flex justify-end border-t border-slate-200 px-5 py-3">
          <Button type="button" variant="secondary" onClick={onClose} disabled={saving}>Đóng</Button>
        </div>
      </section>
    </AccessibleModal>
  )
}
