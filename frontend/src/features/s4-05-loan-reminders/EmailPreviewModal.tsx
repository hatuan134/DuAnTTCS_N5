import { X, Mail, DollarSign } from 'lucide-react'
import Modal from '../../components/ui/Modal'
import StatusBadge from '../../components/ui/StatusBadge'
import Button from '../../components/ui/Button'

interface EmailPreviewModalProps {
  isOpen: boolean
  onClose: () => void
  subject: string
  recipientName: string
  recipientEmail: string
  htmlContent: string
  reminderGroup: string
  totalEstimatedFeeVnd?: number
}

export default function EmailPreviewModal({
  isOpen,
  onClose,
  subject,
  recipientName,
  recipientEmail,
  htmlContent,
  reminderGroup,
  totalEstimatedFeeVnd,
}: EmailPreviewModalProps) {
  if (!isOpen) return null

  return (
    <Modal onClose={onClose} label="Xem trước nội dung email">
      <div className="flex max-h-[90vh] w-full max-w-3xl flex-col rounded-xl bg-white shadow-2xl">
        {/* Header */}
        <div className="flex items-start justify-between border-b border-slate-200 px-6 py-4">
          <div className="space-y-1.5">
            <div className="flex flex-wrap items-center gap-2">
              <Mail className="h-5 w-5 text-blue-600" />
              <h2 className="text-lg font-bold text-slate-800">Nội dung email thông báo</h2>
              <StatusBadge status={reminderGroup} />
              {typeof totalEstimatedFeeVnd === 'number' && totalEstimatedFeeVnd > 0 && (
                <span className="inline-flex items-center gap-1 rounded-full bg-rose-50 px-2.5 py-0.5 text-xs font-bold text-rose-700 border border-rose-200">
                  <DollarSign className="h-3 w-3" />
                  Phí tạm tính: {totalEstimatedFeeVnd.toLocaleString('vi-VN')} đ
                </span>
              )}
            </div>
            <p className="text-sm text-slate-500">
              Gửi tới: <span className="font-medium text-slate-700">{recipientName}</span> ({recipientEmail || 'Chưa có email'})
            </p>
            <p className="text-sm font-semibold text-slate-700">
              Tiêu đề: <span className="font-normal text-slate-600">{subject}</span>
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-600 transition"
            aria-label="Đóng"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        {/* Email Body Preview via iframe */}
        <div className="flex-1 overflow-auto p-6 bg-slate-50">
          <div className="rounded-lg border border-slate-200 bg-white p-2 shadow-sm">
            <iframe
              title="Xem trước email"
              srcDoc={htmlContent}
              className="min-h-[460px] w-full border-0"
              sandbox="allow-same-origin"
            />
          </div>
        </div>

        {/* Footer */}
        <div className="flex justify-end border-t border-slate-200 px-6 py-3 bg-white rounded-b-xl">
          <Button variant="secondary" onClick={onClose}>
            Đóng
          </Button>
        </div>
      </div>
    </Modal>
  )
}
