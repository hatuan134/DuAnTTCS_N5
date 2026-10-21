import { useState } from 'react'
import { X, Mail, DollarSign, History, CheckCircle, XCircle, Info, CreditCard } from 'lucide-react'
import Modal from '../../components/ui/Modal'
import StatusBadge from '../../components/ui/StatusBadge'
import Button from '../../components/ui/Button'
import { reminderSendResult, type LoanReminderRetryAttempt } from './loanReminderService'

interface EmailPreviewModalProps {
  isOpen: boolean
  onClose: () => void
  subject: string
  recipientName: string
  recipientEmail: string
  recipientCardNumber?: string | null
  htmlContent: string
  reminderGroup: string
  totalEstimatedFeeVnd?: number
  status?: string
  errorMessage?: string | null
  retryCount?: number
  maxRetries?: number
  attempts?: LoanReminderRetryAttempt[]
}

export default function EmailPreviewModal({
  isOpen,
  onClose,
  subject,
  recipientName,
  recipientEmail,
  recipientCardNumber,
  htmlContent,
  reminderGroup,
  totalEstimatedFeeVnd,
  status,
  errorMessage,
  attempts,
}: EmailPreviewModalProps) {
  const [activeTab, setActiveTab] = useState<'content' | 'attempts'>('content')
  const result = reminderSendResult(status, errorMessage)

  if (!isOpen) return null

  return (
    <Modal onClose={onClose} label="Xem chi tiết email và nhật ký gửi">
      <div className="flex max-h-[90vh] w-full max-w-4xl flex-col rounded-xl bg-white shadow-2xl">
        {/* Header */}
        <div className="flex items-start justify-between border-b border-slate-200 px-6 py-4">
          <div className="space-y-1.5">
            <div className="flex flex-wrap items-center gap-2">
              <Mail className="h-5 w-5 text-blue-600" />
              <h2 className="text-lg font-bold text-slate-800">Chi tiết email thông báo</h2>
              <StatusBadge status={reminderGroup.startsWith('OVERDUE_') ? 'OVERDUE_DAILY' : reminderGroup}
                label={reminderGroup.startsWith('OVERDUE_') ? 'Quá hạn' : undefined} />
              {status && <StatusBadge status={result.status} label={result.label} />}
              {typeof totalEstimatedFeeVnd === 'number' && totalEstimatedFeeVnd > 0 && (
                <span className="inline-flex items-center gap-1 rounded-full bg-rose-50 px-2.5 py-0.5 text-xs font-bold text-rose-700 border border-rose-200">
                  <DollarSign className="h-3 w-3" />
                  Phí tạm tính: {totalEstimatedFeeVnd.toLocaleString('vi-VN')} đ
                </span>
              )}
            </div>
            <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-sm text-slate-500">
              <span>
                Người nhận: <strong className="text-slate-700">{recipientName}</strong> ({recipientEmail || 'Chưa có email'})
              </span>
              {recipientCardNumber && (
                <span className="inline-flex items-center gap-1 text-blue-700 font-semibold">
                  <CreditCard className="h-3.5 w-3.5" />
                  Mã thẻ: {recipientCardNumber}
                </span>
              )}

            </div>
            <p className="text-sm font-semibold text-slate-700">
              Tiêu đề: <span className="font-normal text-slate-600">{subject}</span>
            </p>
            {status && result.reason && <p className="text-sm text-red-600">{result.reason}</p>}
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

        {/* Tab switcher */}
        <div className="flex border-b border-slate-200 bg-slate-50 px-6">
          <button
            type="button"
            onClick={() => setActiveTab('content')}
            className={`border-b-2 px-4 py-2.5 text-sm font-semibold transition-colors ${
              activeTab === 'content'
                ? 'border-blue-600 text-blue-600'
                : 'border-transparent text-slate-600 hover:text-slate-900'
            }`}
          >
            Nội dung email
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('attempts')}
            className={`flex items-center gap-1.5 border-b-2 px-4 py-2.5 text-sm font-semibold transition-colors ${
              activeTab === 'attempts'
                ? 'border-blue-600 text-blue-600'
                : 'border-transparent text-slate-600 hover:text-slate-900'
            }`}
          >
            <History className="h-4 w-4" />
            Nhật ký các lần gửi ({attempts?.length ?? 1})
          </button>
        </div>

        {/* Audit Disclaimer Notice */}
        <div className="bg-amber-50 border-b border-amber-200 px-6 py-2 text-xs text-amber-800 flex items-center gap-2">
          <Info className="h-4 w-4 shrink-0 text-amber-600" />
          <span>
            <strong>Lưu ý đối soát:</strong> Trạng thái <em>"Đã gửi thành công"</em> ghi nhận hệ thống đã gửi thư thành công qua máy chủ bưu chính. Trạng thái này không phản ánh việc bạn đọc đã thực tế mở hoặc xem thư (do hộp thư rác hoặc lỗi người dùng).
          </span>
        </div>

        {/* Body */}
        <div className="flex-1 overflow-auto p-6 bg-slate-50">
          {activeTab === 'content' ? (
            <div className="rounded-lg border border-slate-200 bg-white p-2 shadow-sm">
              <iframe
                title="Xem trước email"
                srcDoc={htmlContent}
                className="min-h-[440px] w-full border-0"
                sandbox="allow-same-origin"
              />
            </div>
          ) : (
            <div className="space-y-4">
              <div className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
                <h3 className="font-semibold text-slate-800 mb-3 flex items-center gap-2">
                  <History className="h-4 w-4 text-purple-600" />
                  Nhật ký xử lý thông báo
                </h3>

                {attempts && attempts.length > 0 ? (
                  <div className="overflow-hidden rounded-lg border border-slate-200">
                    <table className="min-w-full divide-y divide-slate-200 text-left text-sm">
                      <thead className="bg-slate-50 text-xs font-semibold text-slate-600">
                        <tr>
                          <th className="px-4 py-2.5">Lần gửi</th>
                          <th className="px-4 py-2.5">Thời điểm thực hiện</th>
                          <th className="px-4 py-2.5">Kết quả</th>
                          <th className="px-4 py-2.5">Chi tiết lỗi nếu có</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-slate-200 bg-white">
                        {attempts.map((att) => (
                          <tr key={att.id} className="hover:bg-slate-50">
                            <td className="px-4 py-3 font-semibold text-slate-700 whitespace-nowrap">
                              {att.attemptNumber === 1
                                ? 'Lần gửi đầu tiên'
                                : `Lần thử lại #${att.attemptNumber - 1} (Lần ${att.attemptNumber})`}
                            </td>
                            <td className="px-4 py-3 text-slate-600 whitespace-nowrap">
                              {new Date(att.attemptedAt).toLocaleString('vi-VN')}
                            </td>
                            <td className="px-4 py-3 whitespace-nowrap">
                              {att.status === 'SUCCESS' ? (
                                <span className="inline-flex items-center gap-1 rounded-full bg-emerald-50 px-2.5 py-0.5 text-xs font-semibold text-emerald-700 border border-emerald-200">
                                  <CheckCircle className="h-3.5 w-3.5" />
                                  Gửi thành công
                                </span>
                              ) : (
                                <span className="inline-flex items-center gap-1 rounded-full bg-red-50 px-2.5 py-0.5 text-xs font-semibold text-red-700 border border-red-200">
                                  <XCircle className="h-3.5 w-3.5" />
                                  Gửi thất bại
                                </span>
                              )}
                            </td>
                            <td className="px-4 py-3 text-xs text-red-600 max-w-xs break-words">
                              {reminderSendResult(att.status, att.errorMessage).reason || <span className="text-slate-400 font-normal">Không có lỗi</span>}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                ) : (
                  <div className="space-y-2 text-sm text-slate-600">
                    <p>Kết quả lượt gửi: <strong>{result.label}</strong></p>
                    {result.reason && (
                      <div className="rounded-lg border border-red-200 bg-red-50 p-3 text-xs text-red-700">
                        <strong>Lý do:</strong> {result.reason}
                      </div>
                    )}
                  </div>
                )}


              </div>
            </div>
          )}
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
