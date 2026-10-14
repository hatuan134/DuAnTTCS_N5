import { useState, useEffect, useCallback } from 'react'
import { Send, RefreshCw, Eye, Filter, AlertCircle } from 'lucide-react'
import PageHeader from '../../components/ui/PageHeader'
import Button from '../../components/ui/Button'
import StatusBadge from '../../components/ui/StatusBadge'
import LoadingState from '../../components/ui/LoadingState'
import EmptyState from '../../components/ui/EmptyState'
import TablePagination from '../../components/ui/TablePagination'
import TriggerReminderModal from './TriggerReminderModal'
import EmailPreviewModal from './EmailPreviewModal'
import {
  loanReminderService,
  type LoanReminderLog,
} from './loanReminderService'

export default function LoanRemindersPage() {
  const [logs, setLogs] = useState<LoanReminderLog[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  // Filters
  const [filterDate, setFilterDate] = useState<string>('')
  const [filterGroup, setFilterGroup] = useState<string>('')
  const [filterStatus, setFilterStatus] = useState<string>('')
  const [searchTerm, setSearchTerm] = useState<string>('')
  const [filterCardNumber, setFilterCardNumber] = useState<string>('')

  // Pagination
  const [page, setPage] = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const pageSize = 15

  // Modals & Action states
  const [showTriggerModal, setShowTriggerModal] = useState(false)
  const [selectedLogForPreview, setSelectedLogForPreview] = useState<LoanReminderLog | null>(null)
  const [retrying, setRetrying] = useState(false)
  const [retryResultMsg, setRetryResultMsg] = useState<string | null>(null)

  const fetchLogs = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const res = await loanReminderService.getHistory({
        date: filterDate || undefined,
        reminderGroup: filterGroup || undefined,
        status: filterStatus || undefined,
        search: searchTerm.trim() || undefined,
        cardNumber: filterCardNumber.trim() || undefined,
        page,
        size: pageSize,
      })
      setLogs(res.content)
      setTotalElements(res.totalElements)
    } catch (err: any) {
      setError(err?.response?.data?.message || err?.message || 'Không thể tải lịch sử gửi email nhắc nhở')
    } finally {
      setLoading(false)
    }
  }, [filterDate, filterGroup, filterStatus, searchTerm, filterCardNumber, page])

  useEffect(() => {
    fetchLogs()
  }, [fetchLogs])

  const handleRetryPendingBatch = async () => {
    setRetrying(true)
    setRetryResultMsg(null)
    try {
      const res = await loanReminderService.retryPending()
      setRetryResultMsg(res.executionSummary)
      fetchLogs()
    } catch (err: any) {
      setError(err?.response?.data?.message || err?.message || 'Lỗi khi thực hiện thử lại các email chờ')
    } finally {
      setRetrying(false)
    }
  }

  const handleRetrySingle = async (logId: number) => {
    setRetrying(true)
    setRetryResultMsg(null)
    try {
      const res = await loanReminderService.retrySingle(logId)
      setRetryResultMsg(res.executionSummary)
      fetchLogs()
    } catch (err: any) {
      setError(err?.response?.data?.message || err?.message || 'Lỗi khi thử lại email này')
    } finally {
      setRetrying(false)
    }
  }

  const formatDate = (isoString?: string) => {
    if (!isoString) return '-'
    const d = new Date(isoString)
    return d.toLocaleString('vi-VN', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    })
  }

  return (
    <div className="space-y-6 p-6">
      <PageHeader
        title="Quản lý gửi email nhắc nhở & Đối soát thử lại"
        description="Theo dõi lịch sử gửi email nhắc sách, tự động gửi lại email thất bại tối đa 3 lần và đối soát nhật ký khi bạn đọc phản ánh chưa nhận được thư."
        action={
          <div className="flex flex-wrap items-center gap-3">
            <Button
              variant="secondary"
              onClick={fetchLogs}
              disabled={loading || retrying}
              className="inline-flex items-center gap-2"
            >
              <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
              Làm mới
            </Button>
            <Button
              variant="secondary"
              onClick={handleRetryPendingBatch}
              disabled={loading || retrying}
              className="inline-flex items-center gap-2 text-purple-700 border-purple-300 hover:bg-purple-50"
            >
              <RefreshCw className={`h-4 w-4 ${retrying ? 'animate-spin' : ''}`} />
              {retrying ? 'Đang gửi lại...' : 'Thử lại email chờ'}
            </Button>
            <Button
              variant="primary"
              onClick={() => setShowTriggerModal(true)}
              className="inline-flex items-center gap-2"
            >
              <Send className="h-4 w-4" />
              Kích hoạt gửi / Chạy thử
            </Button>
          </div>
        }
      />

      {/* Retry Result Banner if any */}
      {retryResultMsg && (
        <div className="flex items-center justify-between rounded-lg border border-purple-200 bg-purple-50 p-4 text-sm text-purple-800">
          <div className="flex items-center gap-2 font-medium">
            <RefreshCw className="h-4 w-4 text-purple-600" />
            <span>{retryResultMsg}</span>
          </div>
          <button
            type="button"
            onClick={() => setRetryResultMsg(null)}
            className="text-xs text-purple-600 underline font-semibold"
          >
            Đóng
          </button>
        </div>
      )}

      {/* Filter Toolbar */}
      <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm space-y-3">
        <div className="flex flex-wrap items-center gap-3 text-sm">
          <div className="flex items-center gap-1.5 font-medium text-slate-700">
            <Filter className="h-4 w-4 text-blue-600" />
            <span>Tìm kiếm:</span>
          </div>

          <div className="flex items-center gap-2">
            <input
              type="text"
              placeholder="Tên hoặc email bạn đọc..."
              value={searchTerm}
              onChange={(e) => {
                setSearchTerm(e.target.value)
                setPage(0)
              }}
              className="w-48 rounded-lg border border-slate-300 px-3 py-1.5 text-sm text-slate-700 shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
            />
          </div>

          <div className="flex items-center gap-2">
            <input
              type="text"
              placeholder="Mã thẻ (VD: CARD-2026...)"
              value={filterCardNumber}
              onChange={(e) => {
                setFilterCardNumber(e.target.value)
                setPage(0)
              }}
              className="w-44 rounded-lg border border-slate-300 px-3 py-1.5 text-sm text-slate-700 shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
            />
          </div>

          <div className="flex items-center gap-2">
            <label htmlFor="filter-date" className="text-xs text-slate-500">
              Ngày:
            </label>
            <input
              id="filter-date"
              type="date"
              value={filterDate}
              onChange={(e) => {
                setFilterDate(e.target.value)
                setPage(0)
              }}
              className="rounded-lg border border-slate-300 px-3 py-1.5 text-sm text-slate-700 shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
            />
          </div>

          <div className="flex items-center gap-2">
            <label htmlFor="filter-group" className="text-xs text-slate-500">
              Nhóm:
            </label>
            <select
              id="filter-group"
              value={filterGroup}
              onChange={(e) => {
                setFilterGroup(e.target.value)
                setPage(0)
              }}
              className="rounded-lg border border-slate-300 px-3 py-1.5 text-sm text-slate-700 shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
            >
              <option value="">Tất cả các nhóm</option>
              <option value="DUE_IN_2_DAYS">Còn 2 ngày tới hạn</option>
              <option value="DUE_TODAY">Đến hạn hôm nay</option>
              <option value="OVERDUE_1_DAY">Quá hạn 1 ngày</option>
              <option value="OVERDUE_7_DAYS">Quá hạn 7 ngày</option>
              <option value="OVERDUE_30_DAYS">Quá hạn 30 ngày</option>
            </select>
          </div>

          <div className="flex items-center gap-2">
            <label htmlFor="filter-status" className="text-xs text-slate-500">
              Trạng thái:
            </label>
            <select
              id="filter-status"
              value={filterStatus}
              onChange={(e) => {
                setFilterStatus(e.target.value)
                setPage(0)
              }}
              className="rounded-lg border border-slate-300 px-3 py-1.5 text-sm text-slate-700 shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
            >
              <option value="">Tất cả trạng thái</option>
              <option value="SENT">Gửi thành công</option>
              <option value="PENDING_RETRY">Chờ gửi lại</option>
              <option value="FAILED_MAX_RETRIES">Thất bại sau 3 lần thử</option>
              <option value="FAILED">Thất bại (Gốc)</option>
              <option value="SKIPPED">Đã bỏ qua</option>
            </select>
          </div>

          {(filterDate || filterGroup || filterStatus || searchTerm || filterCardNumber) && (
            <button
              type="button"
              onClick={() => {
                setFilterDate('')
                setFilterGroup('')
                setFilterStatus('')
                setSearchTerm('')
                setFilterCardNumber('')
                setPage(0)
              }}
              className="text-xs font-semibold text-blue-600 hover:underline"
            >
              Xóa bộ lọc
            </button>
          )}
        </div>
      </div>

      {/* Main Table */}
      {error && (
        <div className="flex items-center gap-3 rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">
          <AlertCircle className="h-5 w-5 shrink-0 text-red-500" />
          <p>{error}</p>
        </div>
      )}

      <div className="rounded-xl border border-slate-200 bg-white shadow-sm overflow-hidden">
        {loading ? (
          <div className="p-8">
            <LoadingState />
          </div>
        ) : logs.length === 0 ? (
          <div className="p-8">
            <EmptyState
              title="Chưa có bản ghi nhắc nhở nào"
              description="Các email nhắc nhở được hệ thống tự động gửi lúc 7:00 sáng hoặc khi bạn kích hoạt chạy thử nghiệm."
            />
          </div>
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="min-w-full divide-y divide-slate-200 text-left text-sm">
                <thead className="bg-slate-50 text-xs font-semibold text-slate-600">
                  <tr>
                    <th className="px-5 py-3.5">Thời gian gửi</th>
                    <th className="px-5 py-3.5">Bạn đọc</th>
                    <th className="px-5 py-3.5">Nhóm nhắc</th>
                    <th className="px-5 py-3.5">Số sách / Phiếu mượn</th>
                    <th className="px-5 py-3.5 text-right">Phí tạm tính</th>
                    <th className="px-5 py-3.5 text-center">Trạng thái</th>
                    <th className="px-5 py-3.5">Kích hoạt bởi</th>
                    <th className="px-5 py-3.5 text-right">Thao tác</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-200 bg-white">
                  {logs.map((log) => (
                    <tr key={log.id} className="hover:bg-slate-50/80 transition-colors">
                      <td className="px-5 py-3.5 whitespace-nowrap text-slate-600">
                        <div className="font-medium text-slate-800">{formatDate(log.sentAt)}</div>
                        <div className="text-xs text-slate-500">Ngày tính: {log.reminderDate}</div>
                      </td>
                      <td className="px-5 py-3.5">
                        <div className="font-semibold text-slate-800">{log.readerName}</div>
                        <div className="text-xs text-slate-500">{log.recipientEmail || 'Không có email'}</div>
                        {log.readerCardNumber && (
                          <div className="text-xs font-medium text-blue-600 mt-0.5">
                            Mã thẻ: {log.readerCardNumber}
                          </div>
                        )}
                      </td>
                      <td className="px-5 py-3.5 whitespace-nowrap">
                        <StatusBadge status={log.reminderGroup} />
                      </td>
                      <td className="px-5 py-3.5">
                        <div className="font-medium text-slate-800">
                          {log.loanItemCount} cuốn
                        </div>
                        <div className="text-xs text-slate-500 line-clamp-1 max-w-xs" title={log.bookTitles}>
                          {log.bookTitles}
                        </div>
                      </td>
                      <td className="px-5 py-3.5 text-right whitespace-nowrap">
                        {typeof log.totalEstimatedFeeVnd === 'number' && log.totalEstimatedFeeVnd > 0 ? (
                          <div className="font-bold text-rose-600">
                            {log.totalEstimatedFeeVnd.toLocaleString('vi-VN')} đ
                          </div>
                        ) : (
                          <span className="text-slate-400 text-xs">0 đ</span>
                        )}
                      </td>
                      <td className="px-5 py-3.5 text-center whitespace-nowrap">
                        <StatusBadge status={log.status} />
                        {log.status === 'PENDING_RETRY' && (
                          <div className="text-xs text-purple-700 font-medium mt-1">
                            Đã thử: {log.retryCount}/3 lần
                          </div>
                        )}
                        {log.status === 'FAILED_MAX_RETRIES' && (
                          <div className="text-xs text-rose-700 font-bold mt-1">
                            Đã đủ 3 lần thử
                          </div>
                        )}
                        {log.errorMessage && (
                          <div className="text-xs text-red-600 mt-1 max-w-[200px] truncate" title={log.errorMessage}>
                            {log.errorMessage}
                          </div>
                        )}
                      </td>
                      <td className="px-5 py-3.5 whitespace-nowrap text-xs text-slate-600">
                        {log.triggeredBy === 'SYSTEM_SCHEDULED' ? (
                          <span className="font-medium text-blue-700">Tự động (7h sáng)</span>
                        ) : (
                          <span>Thủ công ({log.triggeredByUserName || 'Thủ thư'})</span>
                        )}
                      </td>
                      <td className="px-5 py-3.5 text-right whitespace-nowrap">
                        <div className="flex items-center justify-end gap-2">
                          {log.status === 'PENDING_RETRY' && (
                            <Button
                              variant="secondary"
                              size="sm"
                              onClick={() => handleRetrySingle(log.id)}
                              disabled={retrying}
                              className="inline-flex items-center gap-1 text-xs text-purple-700 border-purple-200 hover:bg-purple-50"
                            >
                              <RefreshCw className={`h-3 w-3 ${retrying ? 'animate-spin' : ''}`} />
                              Gửi lại
                            </Button>
                          )}
                          <Button
                            variant="secondary"
                            size="sm"
                            onClick={() => setSelectedLogForPreview(log)}
                            className="inline-flex items-center gap-1.5"
                          >
                            <Eye className="h-3.5 w-3.5" />
                            Xem chi tiết
                          </Button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <TablePagination
              page={page + 1}
              totalPages={Math.ceil(totalElements / pageSize)}
              totalItems={totalElements}
              pageSize={pageSize}
              onPageChange={(p) => setPage(p - 1)}
            />
          </>
        )}
      </div>

      {/* Trigger Modal */}
      <TriggerReminderModal
        isOpen={showTriggerModal}
        onClose={() => setShowTriggerModal(false)}
        onSuccess={() => {
          fetchLogs()
        }}
      />

      {/* Email View Modal */}
      {selectedLogForPreview && (
        <EmailPreviewModal
          isOpen={Boolean(selectedLogForPreview)}
          onClose={() => setSelectedLogForPreview(null)}
          subject={selectedLogForPreview.subject}
          recipientName={selectedLogForPreview.readerName}
          recipientEmail={selectedLogForPreview.recipientEmail}
          recipientCardNumber={selectedLogForPreview.readerCardNumber}
          htmlContent={selectedLogForPreview.emailContent}
          reminderGroup={selectedLogForPreview.reminderGroup}
          totalEstimatedFeeVnd={selectedLogForPreview.totalEstimatedFeeVnd}
          status={selectedLogForPreview.status}
          errorMessage={selectedLogForPreview.errorMessage}
          retryCount={selectedLogForPreview.retryCount}
          maxRetries={selectedLogForPreview.maxRetries}
          attempts={selectedLogForPreview.attempts}
        />
      )}
    </div>
  )
}
