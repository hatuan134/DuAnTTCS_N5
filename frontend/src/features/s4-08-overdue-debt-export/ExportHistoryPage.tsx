import { useEffect, useState } from 'react'
import { Link, Navigate } from 'react-router-dom'
import { RefreshCw } from 'lucide-react'
import Button from '../../components/ui/Button'
import Card from '../../components/ui/Card'
import EmptyState from '../../components/ui/EmptyState'
import FeedbackAlert from '../../components/ui/FeedbackAlert'
import PageHeader from '../../components/ui/PageHeader'
import TablePagination from '../../components/ui/TablePagination'
import { getCurrentUser } from '../../core/auth/authStorage'
import { getApiErrorMessage } from '../s1-02-user-management/accountService'
import { formatTime, getHistory, type ExportHistory } from './reportService'

const statuses = { SUCCESS: 'Tạo tệp thành công', FAILED: 'Xuất thất bại', PROCESSING: 'Đang xử lý / chưa hoàn tất' }
export default function ExportHistoryPage() {
  const allowed = getCurrentUser()?.role === 'LIBRARY_MANAGER'
  const [data, setData] = useState<ExportHistory | null>(null), [page, setPage] = useState(0)
  const [loading, setLoading] = useState(true), [revision, setRevision] = useState(0), [notice, setNotice] = useState('')
  useEffect(() => {
    if (!allowed) return
    const controller = new AbortController()
    setLoading(true); setData(null); setNotice('')
    getHistory(page, controller.signal).then(value => { if (!controller.signal.aborted) setData(value) })
      .catch(error => { if (!controller.signal.aborted) setNotice(getApiErrorMessage(error, 'Không tải được lịch sử xuất dữ liệu.')) })
      .finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [allowed, page, revision])
  if (!allowed) return <Navigate to="/dashboard" replace />
  return <div className="min-w-0 space-y-5">
    <PageHeader title="Lịch sử xuất dữ liệu" description="Theo dõi các yêu cầu xuất danh sách bạn đọc quá hạn và nợ phí."
      action={<Button type="button" variant="secondary" disabled={loading} onClick={() => setRevision(v => v + 1)}><RefreshCw size={16} />Làm mới</Button>} />
    <Link to="/reports/overdue-debts" className="inline-flex min-h-11 items-center text-sm font-semibold text-blue-700 hover:underline">Quay lại danh sách xuất</Link>
    <p className="text-sm leading-6 text-slate-600">“Tạo tệp thành công” xác nhận hệ thống đã tạo tệp và lưu nhật ký, không xác nhận thiết bị đã lưu xong. Bản ghi đang xử lý có thể là yêu cầu chưa hoàn tất nếu máy chủ bị dừng đột ngột. Tất cả thời gian hiển thị theo giờ Việt Nam.</p>
    {notice && <FeedbackAlert tone="error" message={notice} onDismiss={() => setNotice('')} />}
    <Card className="min-w-0 overflow-hidden">
      {loading ? <p role="status" className="p-8 text-center text-sm text-slate-500">Đang tải lịch sử...</p> : !data ? <p className="p-5 text-sm text-slate-600">Chưa lấy được lịch sử. Bấm “Làm mới” để thử lại.</p>
        : data.items.length ? <><div className="divide-y divide-slate-200">{data.items.map(log => <article key={log.id} className="space-y-3 p-4 sm:p-5">
          <div className="flex flex-wrap items-start justify-between gap-2"><h2 className="break-words font-semibold text-slate-900">{log.actorName}</h2>
            <span className={`rounded-lg px-3 py-1 text-xs font-semibold ${log.status === 'SUCCESS' ? 'bg-emerald-50 text-emerald-800' : log.status === 'FAILED' ? 'bg-red-50 text-red-700' : 'bg-amber-50 text-amber-800'}`}>{statuses[log.status]}</span></div>
          <dl className="grid gap-3 text-sm sm:grid-cols-3"><div><dt className="text-slate-500">Yêu cầu xuất</dt><dd className="mt-1">{formatTime(log.requestedAt)}</dd></div><div><dt className="text-slate-500">Thời điểm chốt</dt><dd className="mt-1">{log.snapshotAt ? formatTime(log.snapshotAt) : 'Chưa lấy được dữ liệu'}</dd></div><div><dt className="text-slate-500">Số lượng bạn đọc</dt><dd className="mt-1 font-semibold">{log.recordCount.toLocaleString('vi-VN')}</dd></div></dl>
          <dl className="grid gap-3 text-sm sm:grid-cols-2">
            <div><dt className="text-slate-500">Số ngày trễ tối thiểu</dt><dd className="mt-1 font-medium">{log.minOverdueDays ?? 'Không giới hạn'}</dd></div>
            <div><dt className="text-slate-500">Loại thẻ</dt><dd className="mt-1 break-words font-medium">{log.cardTypeName ?? 'Tất cả'}</dd></div>
          </dl>
          <p className="break-words text-sm leading-6 text-slate-600"><span className="font-medium">Điều kiện lọc: </span>{log.defaultFilter}</p>
        </article>)}</div><TablePagination page={page + 1} totalItems={data.totalItems} totalPages={Math.max(1, Math.ceil(data.totalItems / data.size))} pageSize={data.size} onPageChange={next => setPage(next - 1)} /></>
          : <div className="p-4"><EmptyState title="Chưa có lịch sử xuất" description="Mỗi lần nhấn Xuất Excel sẽ tạo một bản ghi tại đây." /></div>}
    </Card>
  </div>
}
