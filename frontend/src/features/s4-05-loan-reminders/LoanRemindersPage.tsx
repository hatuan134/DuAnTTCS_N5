import { getApiErrorMessage } from '../s1-02-user-management/accountService'
import { useState, useEffect, useCallback } from 'react'
import FeedbackAlert from '../../components/ui/FeedbackAlert'
import { RefreshCw, Eye, Filter } from 'lucide-react'
import PageHeader from '../../components/ui/PageHeader'
import Button from '../../components/ui/Button'
import StatusBadge from '../../components/ui/StatusBadge'
import LoadingState from '../../components/ui/LoadingState'
import EmptyState from '../../components/ui/EmptyState'
import TablePagination from '../../components/ui/TablePagination'
import EmailPreviewModal from './EmailPreviewModal'
import {
  loanReminderService,
  reminderSendResult,
  type LoanReminderLog,
} from './loanReminderService'

export default function LoanRemindersPage() {
  const [logs, setLogs] = useState<LoanReminderLog[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [loadFailed, setLoadFailed] = useState(false)

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
  const [selectedLogForPreview, setSelectedLogForPreview] = useState<LoanReminderLog | null>(null)

  const fetchLogs = useCallback(async () => {
    setLoading(true)
    setError(null)
    setLoadFailed(false)
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
      setLoadFailed(true)
      setError(getApiErrorMessage(err, 'Không thể tải lịch sử gửi email nhắc nhở'))
    } finally {
      setLoading(false)
    }
  }, [filterDate, filterGroup, filterStatus, searchTerm, filterCardNumber, page])

  useEffect(() => {
    fetchLogs()
  }, [fetchLogs])

  const formatDate = (isoString?: string) => {
    if (!isoString) return '-'
    const d = new Date(isoString)
    return d.toLocaleString('vi-VN', {
      timeZone: 'Asia/Ho_Chi_Minh',
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    })
  }

  return (
    <div className="loan-reminders-page min-w-0 space-y-6 p-3 sm:p-6">
      <PageHeader
        title="Theo dõi email nhắc trả sách tự động"
        description="Hệ thống gửi nhắc lúc 07:00 hằng ngày. Sách quá hạn tiếp tục được nhắc mỗi ngày đến khi được trả hoặc xử lý hoàn tất."
        action={
          <div className="flex flex-wrap items-center gap-3">
            <Button
              variant="secondary"
              onClick={fetchLogs}
              disabled={loading}
              className="inline-flex items-center gap-2"
            >
              <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
              Làm mới
            </Button>
          </div>
        }
      />

      {/* Filter Toolbar */}
      <div className="reminder-filters rounded-xl border border-slate-200 bg-white p-4 shadow-sm space-y-3">
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
              Ngày nhắc hạn:
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
              Loại nhắc hạn:
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
              <option value="">Tất cả loại nhắc hạn</option>
              <option value="DUE_IN_2_DAYS">Còn 2 ngày tới hạn</option>
              <option value="DUE_TODAY">Đến hạn hôm nay</option>
              <option value="OVERDUE_DAILY">Quá hạn</option>
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
              <option value="">Tất cả kết quả gửi</option>
              <option value="SENT">Gửi thành công</option>
              <option value="FAILED">Gửi thất bại</option>
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
      {error && <FeedbackAlert tone="error" message={error} onDismiss={() => setError(null)} />}
      {loadFailed && <p role="status" className="text-sm text-slate-600">Chưa tải được lịch sử gửi email. Nhấn “Làm mới” để thử lại.</p>}

      <div className="rounded-xl border border-slate-200 bg-white shadow-sm overflow-hidden">
        {loading ? (
          <div className="p-8">
            <LoadingState />
          </div>
        ) : loadFailed ? <div className="p-8 text-sm text-slate-600">Lịch sử hiện chưa có sẵn.</div> : logs.length === 0 ? (
          <div className="p-8">
            <EmptyState
              title="Chưa có bản ghi nhắc nhở nào"
              description="Lịch sử sẽ xuất hiện khi hệ thống tự động xử lý nhắc hạn lúc 07:00 hằng ngày."
            />
          </div>
        ) : (
          <>
            <div className="overflow-x-auto" role="region" aria-label="Lịch sử nhắc nhở đến hạn" tabIndex={0}>
              <table className="reminder-history-table min-w-full divide-y divide-slate-200 text-center text-sm">
                <thead className="bg-slate-50 text-xs font-semibold text-slate-600">
                  <tr>
                    <th className="px-5 py-3.5 text-center">Cập nhật lần cuối</th>
                    <th className="px-5 py-3.5 text-center">Bạn đọc</th>
                    <th className="px-5 py-3.5 text-center">Nhóm nhắc</th>
                    <th className="px-5 py-3.5 text-center">Số sách / Phiếu mượn</th>
                    <th className="px-5 py-3.5 text-center">Phí tạm tính</th>
                    <th className="px-5 py-3.5 text-center">Trạng thái</th>
                    <th className="px-5 py-3.5 text-center">Kích hoạt bởi</th>
                    <th className="px-5 py-3.5 text-center">Thao tác</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-200 bg-white">
                  {logs.map((log) => {
                    const result = reminderSendResult(log.status, log.errorMessage)
                    return (
                    <tr key={log.id} className="hover:bg-slate-50/80 transition-colors">
                      <td data-label="Cập nhật lần cuối" className="px-5 py-3.5 whitespace-nowrap text-slate-600 text-center">
                        <div className="font-medium text-slate-800">{formatDate(log.sentAt)}</div>
                        <div className="text-xs text-slate-500" title="Ngày dùng để xét hạn trả và lập thông báo nhắc hạn">Ngày nhắc hạn: {log.reminderDate.split('-').reverse().join('/')}</div>
                      </td>
                      <td data-label="Bạn đọc" className="px-5 py-3.5 text-center">
                        <div className="font-semibold text-slate-800">{log.readerName}</div>
                        <div className="text-xs text-slate-500">{log.recipientEmail || 'Không có email'}</div>
                        {log.readerCardNumber && (
                          <div className="text-xs font-medium text-blue-600 mt-0.5">
                            Mã thẻ: {log.readerCardNumber}
                          </div>
                        )}
                      </td>
                      <td data-label="Nhóm nhắc" className="px-5 py-3.5 whitespace-nowrap text-center">
                        <StatusBadge status={log.reminderGroup.startsWith('OVERDUE_') ? 'OVERDUE_DAILY' : log.reminderGroup}
                          label={log.reminderGroup.startsWith('OVERDUE_') ? 'Quá hạn' : undefined} />
                      </td>
                      <td data-label="Số sách / Phiếu mượn" className="px-5 py-3.5 text-center">
                        <div className="font-medium text-slate-800">
                          {log.loanItemCount} cuốn
                        </div>
                        <div className="text-xs text-slate-500 line-clamp-1 max-w-xs mx-auto" title={log.bookTitles}>
                          {log.bookTitles}
                        </div>
                      </td>
                      <td data-label="Phí tạm tính" className="px-5 py-3.5 text-center whitespace-nowrap">
                        {typeof log.totalEstimatedFeeVnd === 'number' && log.totalEstimatedFeeVnd > 0 ? (
                          <div className="font-bold text-rose-600">
                            {log.totalEstimatedFeeVnd.toLocaleString('vi-VN')} đ
                          </div>
                        ) : (
                          <span className="text-slate-400 text-xs">0 đ</span>
                        )}
                      </td>
                      <td data-label="Trạng thái" className="px-5 py-3.5 text-center whitespace-nowrap">
                        <StatusBadge status={result.status} label={result.label} />
                      </td>
                      <td data-label="Kích hoạt bởi" className="px-5 py-3.5 whitespace-nowrap text-xs text-slate-600 text-center">
                        {log.triggeredBy === 'SYSTEM_SCHEDULED' ? (
                          <span className="font-medium text-blue-700">Tự động (7h sáng)</span>
                        ) : (
                          <span>Thủ công ({log.triggeredByUserName || 'Thủ thư'})</span>
                        )}
                      </td>
                      <td data-label="Thao tác" className="px-5 py-3.5 text-center whitespace-nowrap">
                        <div className="flex items-center justify-center gap-2">
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
                  )})}
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
