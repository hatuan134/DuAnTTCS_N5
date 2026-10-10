import { AlertTriangle, X } from 'lucide-react'

interface ConfirmActionDialogProps {
  title: string
  description: string
  confirmLabel: string
  onConfirm: () => void
  onCancel: () => void
  danger?: boolean
  busy?: boolean
}

/** Xác nhận các thao tác khóa/xóa theo một mẫu giao diện dùng chung. */
export default function ConfirmActionDialog({
  title, description, confirmLabel, onConfirm, onCancel, danger = true, busy = false,
}: ConfirmActionDialogProps) {
  return (
    <div className="fixed inset-0 z-[100] flex items-center justify-center bg-slate-950/50 p-4" role="presentation">
      <div role="alertdialog" aria-modal="true" aria-labelledby="confirm-action-title" aria-describedby="confirm-action-description"
        className="w-full max-w-md rounded-2xl border border-slate-200 bg-white shadow-2xl">
        <div className="flex items-start justify-between gap-3 border-b border-slate-100 px-5 py-4">
          <div className="flex items-center gap-3">
            <span className={`rounded-full p-2 ${danger ? 'bg-red-50 text-red-700' : 'bg-blue-50 text-blue-700'}`}><AlertTriangle size={20} /></span>
            <h2 id="confirm-action-title" className="text-lg font-semibold text-slate-900">{title}</h2>
          </div>
          <button type="button" onClick={onCancel} disabled={busy} aria-label="Đóng xác nhận"
            className="rounded-lg p-2 text-slate-500 hover:bg-slate-100 disabled:opacity-50"><X size={18} /></button>
        </div>
        <p id="confirm-action-description" className="px-5 py-5 text-sm leading-6 text-slate-700">{description}</p>
        <div className="flex justify-end gap-2 border-t border-slate-100 bg-slate-50 px-5 py-4">
          <button type="button" onClick={onCancel} disabled={busy} className="rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-100">Hủy</button>
          <button type="button" onClick={onConfirm} disabled={busy} autoFocus
            className={`rounded-lg px-4 py-2 text-sm font-semibold text-white disabled:opacity-50 ${danger ? 'bg-red-600 hover:bg-red-700' : 'bg-blue-600 hover:bg-blue-700'}`}>
            {busy ? 'Đang xử lý...' : confirmLabel}
          </button>
        </div>
      </div>
    </div>
  )
}
