import { useEffect, useState } from 'react'
import { Link, Navigate } from 'react-router-dom'
import { BookOpen, CalendarDays, ClipboardList, ClockAlert, RefreshCw, RotateCcw, TrendingUp } from 'lucide-react'
import Card from '../../components/ui/Card'
import Button from '../../components/ui/Button'
import EmptyState from '../../components/ui/EmptyState'
import FeedbackAlert from '../../components/ui/FeedbackAlert'
import Input from '../../components/ui/Input'
import PageHeader from '../../components/ui/PageHeader'
import { getCurrentUser } from '../../core/auth/authStorage'
import { getApiErrorMessage } from '../s1-02-user-management/accountService'
import { getLoanReport, type LoanReport } from './loanReportService'

const DATE_ZONE = 'Asia/Ho_Chi_Minh'

function todayInVietnam(): string {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: DATE_ZONE, year: 'numeric', month: '2-digit', day: '2-digit',
  }).formatToParts(new Date())
  const part = (type: string) => parts.find(item => item.type === type)?.value ?? ''
  return `${part('year')}-${part('month')}-${part('day')}`
}

function changeDays(date: string, days: number): string {
  const utc = new Date(`${date}T12:00:00.000Z`)
  utc.setUTCDate(utc.getUTCDate() + days)
  return utc.toISOString().slice(0, 10)
}

const formatNumber = (value: number) => value.toLocaleString('vi-VN')

function formatSnapshot(value: string): string {
  return new Intl.DateTimeFormat('vi-VN', {
    timeZone: DATE_ZONE, day: '2-digit', month: '2-digit', year: 'numeric',
    hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false,
  }).format(new Date(value))
}

export default function LoanReportPage() {
  const [endDate, setEndDate] = useState(todayInVietnam)
  const [startDate, setStartDate] = useState(() => changeDays(todayInVietnam(), -29))
  const [refreshKey, setRefreshKey] = useState(0)
  const [report, setReport] = useState<LoanReport | null>(null)
  const [loading, setLoading] = useState(true)
  const [loadFailed, setLoadFailed] = useState(false)
  const [notice, setNotice] = useState('')
  const allowed = getCurrentUser()?.role === 'LIBRARY_MANAGER'
  const dateError = !startDate || !endDate
    ? 'Vui lòng chọn đầy đủ ngày bắt đầu và ngày kết thúc.'
    : startDate > endDate
      ? 'Ngày bắt đầu không được lớn hơn ngày kết thúc.'
      : ''

  useEffect(() => {
    if (!allowed || dateError) {
      setLoading(false)
      setReport(null)
      setLoadFailed(false)
      setNotice('')
      return
    }

    const controller = new AbortController()
    setLoading(true)
    setLoadFailed(false)
    setNotice('')
    setReport(null)
    getLoanReport(startDate, endDate, controller.signal)
      .then(data => {
        if (controller.signal.aborted) return
        setReport(data)
        setLoadFailed(false)
      })
      .catch(error => {
        if (controller.signal.aborted) return
        setLoadFailed(true)
        setNotice(getApiErrorMessage(error, 'Không tải được báo cáo mượn trả. Vui lòng thử lại.'))
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })

    return () => controller.abort()
  }, [allowed, dateError, startDate, endDate, refreshKey])

  if (!allowed) return <Navigate to="/dashboard" replace />

  const stats = [
    { title: 'Tổng lượt mượn', value: report?.totalBorrowed, icon: BookOpen, note: 'Số bản sách được mượn trong kỳ', color: 'bg-blue-50 text-blue-700' },
    { title: 'Tổng lượt trả', value: report?.totalReturned, icon: RotateCcw, note: 'Số bản sách đã trả trong kỳ', color: 'bg-emerald-50 text-emerald-700' },
    { title: 'Phiếu đang mở', value: report?.openLoans, icon: ClipboardList, note: 'Phiếu còn ít nhất một bản chưa trả', color: 'bg-indigo-50 text-indigo-700' },
    { title: 'Phiếu quá hạn', value: report?.overdueLoans, icon: ClockAlert, note: 'Phiếu đang mở có bản quá hạn', color: 'bg-rose-50 text-rose-700' },
    { title: 'Tỷ lệ quá hạn', value: report?.overdueRate, icon: TrendingUp, note: 'Phiếu quá hạn / Phiếu đang mở', color: 'bg-amber-50 text-amber-700', percent: true },
  ]

  return <div className="min-w-0 space-y-5">
    <PageHeader
      title="Báo cáo mượn trả"
      description="Theo dõi tình hình mượn, trả và các đầu sách được quan tâm nhất để hỗ trợ bổ sung sách."
      action={<Button variant="secondary" type="button" disabled={!!dateError || loading} onClick={() => setRefreshKey(key => key + 1)}>
        <RefreshCw size={16} className={loading ? 'animate-spin' : ''} /> Làm mới
      </Button>}
    />

    <Card className="p-4 sm:p-5">
      <div className="mb-4 flex flex-wrap items-center gap-2">
        <CalendarDays size={18} className="text-blue-600" aria-hidden="true" />
        <h2 className="font-semibold text-slate-900">Khoảng thời gian báo cáo</h2>
        <span className="text-xs text-slate-500">(Asia/Ho_Chi_Minh)</span>
      </div>
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)_auto] lg:items-end">
        <Input id="loan-report-start" type="date" label="Ngày bắt đầu" required value={startDate} onChange={event => setStartDate(event.target.value)} />
        <Input id="loan-report-end" type="date" label="Ngày kết thúc" required value={endDate} onChange={event => setEndDate(event.target.value)} />
        <Button type="button" variant="secondary" onClick={() => {
          const today = todayInVietnam()
          setStartDate(changeDays(today, -29))
          setEndDate(today)
          setRefreshKey(key => key + 1)
        }}>
          30 ngày gần nhất
        </Button>
      </div>
      {dateError
        ? <p role="alert" className="mt-3 text-sm font-medium text-red-700">{dateError}</p>
        : <p className="mt-3 text-xs text-slate-500">Đổi khoảng ngày để cập nhật chỉ số và bảng xếp hạng lượt mượn. Bảng hàng đợi luôn phản ánh dữ liệu đặt giữ tại thời điểm tải, không lọc theo khoảng ngày.</p>}
    </Card>

    {notice && <FeedbackAlert tone="error" message={notice} onDismiss={() => setNotice('')} />}
    {loadFailed && <p className="rounded-xl bg-slate-50 p-4 text-sm text-slate-700">Chưa lấy được báo cáo. Kiểm tra kết nối rồi bấm “Làm mới”.</p>}

    <section aria-label="Chỉ số mượn trả" className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-5">
      {stats.map(item => <Card key={item.title} className="min-w-0 p-4 sm:p-5">
        <div className={`mb-3 flex h-10 w-10 items-center justify-center rounded-xl ${item.color}`}>
          <item.icon size={19} aria-hidden="true" />
        </div>
        <p className="text-sm font-medium text-slate-600">{item.title}</p>
        <p className="mt-1 text-2xl font-bold tabular-nums text-slate-950" data-testid={item.title}>
          {loading ? <span className="text-slate-400">...</span>
            : report ? `${formatNumber(item.value ?? 0)}${item.percent ? '%' : ''}` : '—'}
        </p>
        <p className="mt-2 text-xs leading-5 text-slate-500">{item.note}</p>
      </Card>)}
    </section>

    <Card className="min-w-0 overflow-hidden">
      <div className="border-b border-slate-200 px-4 py-5 sm:px-6">
        <h2 className="text-lg font-bold text-slate-900">Top 10 đầu sách được mượn nhiều nhất</h2>
        <p className="mt-1 text-sm text-slate-500">Xếp hạng theo số lượt mượn giảm dần. Khi bằng lượt mượn, ưu tiên mã đầu sách tăng dần.</p>
      </div>
      {!dateError && loading
        ? <p role="status" className="px-6 py-12 text-center text-sm text-slate-500">Đang tổng hợp báo cáo...</p>
        : report?.topBooks.length
          ? <>
            <div className="space-y-3 p-4 sm:hidden">
              {report.topBooks.map((book, index) => <div key={book.bookId} className="rounded-xl border border-slate-200 p-3">
                <div className="flex items-start gap-3">
                  <span className="rounded-lg bg-slate-100 px-2 py-1 text-sm font-semibold tabular-nums text-slate-600">{index + 1}</span>
                  <div className="min-w-0 flex-1">
                    <Link className="break-words font-semibold text-blue-700 hover:underline" to={`/books/${book.bookId}`}>{book.bookTitle}</Link>
                    <p className="mt-1 text-xs text-slate-500">Mã đầu sách: #{book.bookId}</p>
                  </div>
                </div>
                <div className="mt-3 flex justify-between gap-3 border-t border-slate-100 pt-3 text-sm">
                  <span>Lượt mượn: <strong>{formatNumber(book.borrowCount)}</strong></span>
                  <span>Bản hiện có: <strong>{formatNumber(book.stockCount)}</strong></span>
                </div>
              </div>)}
            </div>
            <table className="hidden w-full table-fixed border-collapse text-left text-sm sm:table">
              <thead className="bg-slate-50 text-slate-600">
                <tr>
                  <th scope="col" className="w-14 px-4 py-3 font-semibold">STT</th>
                  <th scope="col" className="px-4 py-3 font-semibold">Tên sách</th>
                  <th scope="col" className="w-28 px-3 py-3 font-semibold">Mã đầu sách</th>
                  <th scope="col" className="w-28 px-3 py-3 text-right font-semibold">Lượt mượn</th>
                  <th scope="col" className="w-32 px-4 py-3 text-right font-semibold">Bản hiện có</th>
                </tr>
              </thead>
              <tbody>
                {report.topBooks.map((book, index) => <tr key={book.bookId} className="border-t border-slate-100 hover:bg-slate-50/70">
                  <td className="px-4 py-4 tabular-nums text-slate-500">{index + 1}</td>
                  <td className="break-words px-4 py-4"><Link to={`/books/${book.bookId}`} className="font-semibold text-blue-700 hover:underline">{book.bookTitle}</Link></td>
                  <td className="break-words px-3 py-4 text-slate-600">#{book.bookId}</td>
                  <td className="px-3 py-4 text-right font-semibold tabular-nums text-slate-900">{formatNumber(book.borrowCount)}</td>
                  <td className="px-4 py-4 text-right tabular-nums text-slate-700">{formatNumber(book.stockCount)}</td>
                </tr>)}
              </tbody>
            </table>
          </>
          : !dateError && report
            ? <div className="p-4"><EmptyState title="Không có lượt mượn trong khoảng ngày này" description="Hãy chọn khoảng ngày khác để xem các đầu sách được mượn nhiều nhất." /></div>
            : null}
    </Card>
    <Card className="min-w-0 overflow-hidden">
      <div className="border-b border-slate-200 px-4 py-5 sm:px-6">
        <h2 className="text-lg font-bold text-slate-900">Top 10 đầu sách có hàng đợi đặt giữ dài nhất</h2>
        <p className="mt-1 text-sm text-slate-500">Đếm đơn đang xếp hàng (PENDING), không tính đơn đã chờ nhận, hoàn tất, hủy hoặc hết hiệu lực. Khi bằng số đơn, xếp mã đầu sách tăng dần.</p>
        {report?.queueSnapshotAt && <p className="mt-2 text-xs text-slate-500">Dữ liệu hàng đợi tại: <time dateTime={report.queueSnapshotAt}>{formatSnapshot(report.queueSnapshotAt)}</time> (giờ Việt Nam). Bấm “Làm mới” để xem đơn vừa tạo hoặc vừa hủy.</p>}
      </div>
      {!dateError && loading
        ? <p role="status" className="px-6 py-12 text-center text-sm text-slate-500">Đang tổng hợp hàng đợi đặt giữ...</p>
        : report?.topQueuedBooks.length
          ? <>
            <div className="space-y-3 p-4 sm:hidden">
              {report.topQueuedBooks.map((book, index) => <div key={book.bookId} className="rounded-xl border border-slate-200 p-3">
                <div className="flex items-start gap-3">
                  <span className="rounded-lg bg-slate-100 px-2 py-1 text-sm font-semibold tabular-nums text-slate-600">{index + 1}</span>
                  <div className="min-w-0 flex-1">
                    <Link className="break-words font-semibold text-blue-700 hover:underline" to={`/books/${book.bookId}`}>{book.bookTitle}</Link>
                    <p className="mt-1 text-xs text-slate-500">Mã đầu sách: #{book.bookId}</p>
                  </div>
                </div>
                <div className="mt-3 flex justify-between gap-3 border-t border-slate-100 pt-3 text-sm">
                  <span>Đang chờ: <strong>{formatNumber(book.waitCount)}</strong></span>
                  <span>Bản hiện có: <strong>{formatNumber(book.stockCount)}</strong></span>
                </div>
              </div>)}
            </div>
            <table className="hidden w-full table-fixed border-collapse text-left text-sm sm:table">
              <thead className="bg-slate-50 text-slate-600">
                <tr>
                  <th scope="col" className="w-14 px-4 py-3 font-semibold">STT</th>
                  <th scope="col" className="px-4 py-3 font-semibold">Tên sách</th>
                  <th scope="col" className="w-28 px-3 py-3 font-semibold">Mã đầu sách</th>
                  <th scope="col" className="w-28 px-3 py-3 text-right font-semibold">Đang chờ</th>
                  <th scope="col" className="w-32 px-4 py-3 text-right font-semibold">Bản hiện có</th>
                </tr>
              </thead>
              <tbody>
                {report.topQueuedBooks.map((book, index) => <tr key={book.bookId} className="border-t border-slate-100 hover:bg-slate-50/70">
                  <td className="px-4 py-4 tabular-nums text-slate-500">{index + 1}</td>
                  <td className="break-words px-4 py-4"><Link to={`/books/${book.bookId}`} className="font-semibold text-blue-700 hover:underline">{book.bookTitle}</Link></td>
                  <td className="break-words px-3 py-4 text-slate-600">#{book.bookId}</td>
                  <td className="px-3 py-4 text-right font-semibold tabular-nums text-slate-900">{formatNumber(book.waitCount)}</td>
                  <td className="px-4 py-4 text-right tabular-nums text-slate-700">{formatNumber(book.stockCount)}</td>
                </tr>)}
              </tbody>
            </table>
          </>
          : !dateError && report
            ? <div className="p-4"><EmptyState title="Chưa có đầu sách nào đang được đặt giữ" description="Không có đơn PENDING tại thời điểm tải. Hãy bấm Làm mới khi có đơn mới." /></div>
            : null}
    </Card>
    {report && <p className="text-xs leading-5 text-slate-500">Bảng mượn tính lượt phát sinh trong khoảng ngày đã chọn; bảng đặt giữ đếm số đơn PENDING tại thời điểm tải, không đếm người duy nhất và không tái tạo lịch sử tại ngày kết thúc. Hai bảng dùng cùng quy tắc số bản hiện có: không tính REMOVED, vẫn tính bản đang sửa chữa. Phiếu quá hạn được xác định tại ngày kết thúc báo cáo theo ngày hẹn trả.</p>}
  </div>
}
