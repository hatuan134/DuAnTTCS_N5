import { useState, useEffect } from 'react'
import { Send, Eye, RefreshCw, AlertCircle, CheckCircle2, Info } from 'lucide-react'
import Modal from '../../components/ui/Modal'
import Button from '../../components/ui/Button'
import StatusBadge from '../../components/ui/StatusBadge'
import LoadingState from '../../components/ui/LoadingState'
import EmptyState from '../../components/ui/EmptyState'
import EmailPreviewModal from './EmailPreviewModal'
import {
  loanReminderService,
  type LoanReminderPreviewResponse,
  type LoanReminderGroupDto,
  type LoanReminderExecutionResult,
} from './loanReminderService'

interface TriggerReminderModalProps {
  isOpen: boolean
  onClose: () => void
  onSuccess: () => void
}

export default function TriggerReminderModal({
  isOpen,
  onClose,
  onSuccess,
}: TriggerReminderModalProps) {
  const [targetDate, setTargetDate] = useState<string>(() => {
    const d = new Date()
    return d.toISOString().split('T')[0]
  })
  const [loading, setLoading] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [preview, setPreview] = useState<LoanReminderPreviewResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [result, setResult] = useState<LoanReminderExecutionResult | null>(null)
  const [simulateFailure, setSimulateFailure] = useState(false)

  const [previewItem, setPreviewItem] = useState<LoanReminderGroupDto | null>(null)

  const loadPreview = async (dateStr: string) => {
    setLoading(true)
    setError(null)
    setResult(null)
    try {
      const data = await loanReminderService.getPreview(dateStr)
      setPreview(data)
    } catch (err: any) {
      setError(err?.response?.data?.message || err?.message || 'Không thể tải danh sách xem trước')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    if (isOpen) {
      loadPreview(targetDate)
    }
  }, [isOpen, targetDate])

  const handleTrigger = async () => {
    setSubmitting(true)
    setError(null)
    try {
      const res = await loanReminderService.trigger({ targetDate, simulateFailure })
      setResult(res)
      onSuccess()
    } catch (err: any) {
      setError(err?.response?.data?.message || err?.message || 'Không thể thực hiện gửi email nhắc nhở')
    } finally {
      setSubmitting(false)
    }
  }

  if (!isOpen) return null

  return (
    <>
      <Modal onClose={onClose} label="Chạy thử và kích hoạt gửi email nhắc nhở">
        <div className="flex max-h-[90vh] w-full max-w-4xl flex-col rounded-xl bg-white shadow-2xl">
          {/* Header */}
          <div className="flex flex-wrap items-center justify-between gap-4 border-b border-slate-200 px-6 py-4">
            <div>
              <h2 className="text-lg font-bold text-slate-800">Kích hoạt gửi nhắc nhở mượn trả</h2>
              <p className="text-sm text-slate-500">
                Xem trước danh sách bạn đọc đủ điều kiện (trước hạn, đến hạn, quá hạn 1, 7, 30 ngày) và thực hiện gửi
              </p>
            </div>
            <div className="flex flex-wrap items-center gap-3">
              <div className="flex items-center gap-2">
                <label htmlFor="target-date" className="text-xs font-semibold text-slate-600">
                  Ngày tính mốc:
                </label>
                <input
                  id="target-date"
                  type="date"
                  value={targetDate}
                  onChange={(e) => setTargetDate(e.target.value)}
                  className="rounded-lg border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-700 shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                />
              </div>

              <div className="flex items-center gap-2">
                <label htmlFor="run-mode" className="text-xs font-semibold text-slate-600">
                  Chế độ:
                </label>
                <select
                  id="run-mode"
                  value={simulateFailure ? 'SIMULATE_FAILURE' : 'LIVE'}
                  onChange={(e) => setSimulateFailure(e.target.value === 'SIMULATE_FAILURE')}
                  className="rounded-lg border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-700 shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                >
                  <option value="LIVE">Gửi thực tế qua Email</option>
                  <option value="SIMULATE_FAILURE">Kiểm thử (Mô phỏng lỗi & Thử lại)</option>
                </select>
              </div>

              <Button
                variant="secondary"
                size="sm"
                onClick={() => loadPreview(targetDate)}
                disabled={loading || submitting}
                aria-label="Tải lại danh sách"
              >
                <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
              </Button>
            </div>
          </div>

          {/* Body */}
          <div className="flex-1 overflow-auto p-6 space-y-4">
            {simulateFailure && (
              <div className="flex items-start gap-2.5 rounded-lg border border-purple-200 bg-purple-50/80 p-3.5 text-xs text-purple-900 shadow-sm">
                <Info className="h-4 w-4 shrink-0 text-purple-600 mt-0.5" />
                <div>
                  <span className="font-semibold">Chế độ kiểm thử nghiệp vụ (Sandbox):</span> Hệ thống sẽ mô phỏng lần gửi đầu tiên bị lỗi mạng/từ chối để kiểm tra quy trình đưa vào <em>"Chờ gửi lại"</em>, tự động thử lại tối đa 3 lần và ghi nhật ký đối soát, mà không gửi thư spam tới email thật của bạn đọc.
                </div>
              </div>
            )}

            {error && (
              <div className="flex items-start gap-3 rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">
                <AlertCircle className="h-5 w-5 shrink-0 text-red-500" />
                <p>{error}</p>
              </div>
            )}

            {result && (
              <div className="flex items-start gap-3 rounded-lg border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-800">
                <CheckCircle2 className="h-5 w-5 shrink-0 text-emerald-600" />
                <div>
                  <p className="font-semibold">Đã hoàn thành lượt chạy gửi email nhắc nhở!</p>
                  <p className="mt-1">
                    Tổng số bạn đọc: <strong>{result.totalRecipients}</strong> | Gửi thành công:{' '}
                    <strong className="text-emerald-700">{result.sentCount}</strong> | Thất bại:{' '}
                    <strong className="text-red-700">{result.failedCount}</strong> | Bỏ qua (đã gửi hoặc email lỗi):{' '}
                    <strong className="text-amber-700">{result.skippedCount}</strong>
                    {typeof result.grandTotalEstimatedFeeVnd === 'number' && result.grandTotalEstimatedFeeVnd > 0 && (
                      <span>
                        {' '}| Tổng phí tạm tính: <strong>{result.grandTotalEstimatedFeeVnd.toLocaleString('vi-VN')} đ</strong>
                      </span>
                    )}
                  </p>
                </div>
              </div>
            )}

            {loading ? (
              <LoadingState />
            ) : !preview || preview.reminders.length === 0 ? (
              <EmptyState
                title="Không có phiếu mượn nào cần gửi nhắc"
                description={`Không tìm thấy phiếu mượn nào thuộc các mốc nhắc (còn 2 ngày, đến hạn hôm nay, hoặc quá hạn đúng 1, 7, 30 ngày) vào ngày ${targetDate}.`}
              />
            ) : (
              <div className="space-y-3">
                <div className="flex flex-wrap items-center justify-between gap-2 text-sm text-slate-600 bg-slate-50 p-3 rounded-lg border border-slate-200">
                  <span>
                    Tổng số email: <strong>{preview.totalRecipients}</strong> | Tổng số cuốn sách:{' '}
                    <strong>{preview.totalItems}</strong>
                    {typeof preview.grandTotalEstimatedFeeVnd === 'number' && preview.grandTotalEstimatedFeeVnd > 0 && (
                      <span className="ml-2 text-rose-700 font-semibold">
                        | Tổng phí tạm tính: {preview.grandTotalEstimatedFeeVnd.toLocaleString('vi-VN')} đ
                      </span>
                    )}
                  </span>
                  <span className="text-xs text-slate-500">
                    * Các email đã gửi thành công trong ngày sẽ tự động được bỏ qua
                  </span>
                </div>

                <div className="overflow-hidden rounded-lg border border-slate-200 shadow-sm">
                  <table className="min-w-full divide-y divide-slate-200 text-left text-sm">
                    <thead className="bg-slate-50 text-xs font-semibold text-slate-600">
                      <tr>
                        <th className="px-4 py-3">Bạn đọc</th>
                        <th className="px-4 py-3">Nhóm nhắc</th>
                        <th className="px-4 py-3">Danh sách sách mượn</th>
                        <th className="px-4 py-3 text-right">Phí tạm tính</th>
                        <th className="px-4 py-3 text-center">Trạng thái ngày</th>
                        <th className="px-4 py-3 text-right">Xem email</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-200 bg-white">
                      {preview.reminders.map((group, idx) => (
                        <tr key={`${group.readerId}-${group.reminderGroup}-${idx}`} className="hover:bg-slate-50/80">
                          <td className="px-4 py-3">
                            <div className="font-semibold text-slate-800">{group.readerName}</div>
                            <div className="text-xs text-slate-500">{group.recipientEmail || 'Không có email'}</div>
                          </td>
                          <td className="px-4 py-3 whitespace-nowrap">
                            <StatusBadge status={group.reminderGroup} />
                          </td>
                          <td className="px-4 py-3">
                            <div className="text-xs space-y-1">
                              {group.items.map((it) => (
                                <div key={it.itemId} className="text-slate-700">
                                  <span className="font-medium text-blue-600">{it.loanNumber}</span>: {it.bookTitle} (
                                  <span className="text-slate-500">{it.copyBarcode}</span>)
                                  {it.daysOverdue > 0 && (
                                    <span className="ml-1 text-rose-600 font-medium">
                                      - Quá hạn {it.daysOverdue} ngày ({it.estimatedFeeVnd.toLocaleString('vi-VN')} đ)
                                    </span>
                                  )}
                                </div>
                              ))}
                            </div>
                          </td>
                          <td className="px-4 py-3 text-right whitespace-nowrap">
                            {typeof group.totalEstimatedFeeVnd === 'number' && group.totalEstimatedFeeVnd > 0 ? (
                              <div className="font-bold text-rose-600">
                                {group.totalEstimatedFeeVnd.toLocaleString('vi-VN')} đ
                              </div>
                            ) : (
                              <div className="text-xs text-slate-400">0 đ</div>
                            )}
                          </td>
                          <td className="px-4 py-3 text-center whitespace-nowrap">
                            {group.alreadySentToday ? (
                              <span className="inline-flex items-center rounded-full bg-slate-100 px-2.5 py-0.5 text-xs font-medium text-slate-600">
                                Đã gửi hôm nay
                              </span>
                            ) : (
                              <span className="inline-flex items-center rounded-full bg-emerald-50 px-2.5 py-0.5 text-xs font-medium text-emerald-700">
                                Sẵn sàng gửi
                              </span>
                            )}
                          </td>
                          <td className="px-4 py-3 text-right whitespace-nowrap">
                            <Button
                              variant="secondary"
                              size="sm"
                              onClick={() => setPreviewItem(group)}
                              className="inline-flex items-center gap-1.5"
                            >
                              <Eye className="h-3.5 w-3.5" />
                              Xem mẫu
                            </Button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            )}
          </div>

          {/* Footer */}
          <div className="flex items-center justify-between border-t border-slate-200 px-6 py-4 bg-slate-50 rounded-b-xl">
            <Button variant="secondary" onClick={onClose} disabled={submitting}>
              Đóng
            </Button>
            <Button
              variant={simulateFailure ? 'secondary' : 'primary'}
              onClick={handleTrigger}
              disabled={loading || submitting || !preview || preview.reminders.length === 0}
              className={`inline-flex items-center gap-2 ${
                simulateFailure
                  ? 'bg-purple-600 text-white hover:bg-purple-700 border-transparent shadow-sm'
                  : ''
              }`}
            >
              <Send className="h-4 w-4" />
              {submitting
                ? 'Đang xử lý...'
                : simulateFailure
                ? 'Chạy kiểm thử kịch bản gửi lại'
                : 'Xác nhận gửi nhắc ngay'}
            </Button>
          </div>
        </div>
      </Modal>

      {/* Sub-modal for detailed email preview */}
      {previewItem && (
        <EmailPreviewModal
          isOpen={Boolean(previewItem)}
          onClose={() => setPreviewItem(null)}
          subject={previewItem.subject}
          recipientName={previewItem.readerName}
          recipientEmail={previewItem.recipientEmail}
          htmlContent={previewItem.emailContentHtml}
          reminderGroup={previewItem.reminderGroup}
          totalEstimatedFeeVnd={previewItem.totalEstimatedFeeVnd}
        />
      )}
    </>
  )
}
