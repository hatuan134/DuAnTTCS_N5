import { useEffect, useRef, useState } from 'react'
import Button from '../../components/ui/Button'
import { bookCopyService, copyError } from './bookCopyService'
import type { BookCopy, CopyStatusHistory } from './bookCopyService'

const labels: Record<string, string> = { AVAILABLE: 'Sẵn sàng', REPAIR: 'Đang sửa chữa', BORROWED: 'Đang mượn', HELD: 'Đang giữ chỗ', LOST: 'Mất', DAMAGED: 'Hỏng', REMOVED: 'Loại khỏi kho' }
export default function RepairBookCopyPanel({ copy, onSaved }: { copy: BookCopy; onSaved: (copy: BookCopy) => void }) {
  const [reason, setReason] = useState('')
  const [busy, setBusy] = useState(false)
  const submitting = useRef(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [history, setHistory] = useState<CopyStatusHistory[]>([])
  const [historyError, setHistoryError] = useState('')
  const [loading, setLoading] = useState(true)
  const [reload, setReload] = useState(0)
  useEffect(() => {
    let active = true
    setLoading(true); setHistoryError('')
    bookCopyService.history(copy.id).then(data => { if (active) setHistory(data) })
      .catch(e => { if (active) setHistoryError(copyError(e).message) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [copy.id, reload])
  const submit = async (event: React.FormEvent) => {
    event.preventDefault()
    if (submitting.current) return
    setError(''); setSuccess('')
    const value = reason.trim()
    if (!value || value.length > 2000) { setError('Vui lòng nhập lý do từ 1 đến 2000 ký tự.'); return }
    submitting.current = true; setBusy(true)
    try {
      const updated = await bookCopyService.repair(copy.id, value)
      onSaved(updated); setReason(''); setReload(v => v + 1)
      setSuccess('Đã chuyển sang Đang sửa chữa, cập nhật số bản sẵn sàng và lưu lịch sử.')
    } catch (e) { setError(copyError(e).message) }
    finally { submitting.current = false; setBusy(false) }
  }
  return <section className="mt-6 border-t border-slate-200 pt-6">
    <h3 className="text-lg font-semibold">Chuyển sang Đang sửa chữa</h3>
    <p className="mt-2 text-sm text-slate-600">Chỉ chuyển bản Sẵn sàng và không thuộc phiếu mượn chưa trả. Lý do bắt buộc; lịch sử không được sửa hoặc xóa.</p>
    {success && <p role="status" className="mt-3 rounded-lg bg-emerald-50 p-3 text-emerald-800">{success}</p>}
    {error && <p role="alert" className="mt-3 rounded-lg bg-red-50 p-3 text-red-700">{error}</p>}
    {['AVAILABLE', 'BORROWED'].includes(copy.status) ? <form onSubmit={submit} className="mt-4 space-y-3">
      <label htmlFor="repair-reason" className="block text-sm font-medium">Lý do sửa chữa <span className="text-red-600">*</span></label>
      <textarea id="repair-reason" required maxLength={2000} rows={3} value={reason} disabled={busy}
        onChange={e => setReason(e.target.value)} placeholder="Ví dụ: Bong gáy sách, cần đóng lại trước khi cho mượn."
        className="w-full rounded-lg border border-slate-300 p-3 text-sm" />
      <Button type="submit" disabled={busy}>{busy ? 'Đang xử lý…' : 'Xác nhận chuyển sang sửa chữa'}</Button>
    </form> : <p className="mt-3 text-sm text-slate-500">Trạng thái hiện tại không cho phép chuyển sang sửa chữa.</p>}
    <h3 className="mt-6 text-lg font-semibold">Lịch sử thay đổi trạng thái</h3>
    {loading && <p role="status" className="mt-3">Đang tải lịch sử…</p>}
    {historyError && <div role="alert" className="mt-3 text-red-700">{historyError} <Button type="button" variant="secondary" onClick={() => setReload(v => v + 1)}>Tải lại lịch sử</Button></div>}
    {!loading && !historyError && history.length === 0 && <p className="mt-3 text-sm text-slate-500">Chưa có lịch sử thay đổi trạng thái.</p>}
    {!loading && !historyError && <ol className="mt-3 space-y-3">{history.map(item => <li key={item.id} className="rounded-lg border border-slate-200 p-4 text-sm">
      <p className="font-semibold">{labels[item.previousStatus] || item.previousStatus} → {labels[item.newStatus] || item.newStatus}</p>
      <p className="mt-1 text-slate-600">{item.actorName} (#{item.actorUserId}) · {new Date(item.changedAt).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' })}</p>
      <p className="mt-2 whitespace-pre-wrap break-words">Lý do: {item.reason}</p>
    </li>)}</ol>}
  </section>
}
