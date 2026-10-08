import { useEffect, useState, useMemo } from 'react'
import {
  CalendarX,
  RefreshCw,
  Search,
  CheckCircle2,
  AlertCircle,
  Play,
  ArrowRight,
  BookOpen,
  User as UserIcon,
  RotateCcw,
  Clock,
  Sparkles,
} from 'lucide-react'
import {
  autoCancellationService,
  type AutoCancelledReservation,
  type AutoCancellationRun,
} from './autoCancellationService'

function formatDateTime(isoString: string | null | undefined): string {
  if (!isoString) return '-'
  try {
    const d = new Date(isoString)
    if (isNaN(d.getTime())) return isoString
    return new Intl.DateTimeFormat('vi-VN', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    }).format(d)
  } catch {
    return isoString
  }
}

export default function AutoCancelledReservationsPage() {
  const [items, setItems] = useState<AutoCancelledReservation[]>([])
  const [latestRun, setLatestRun] = useState<AutoCancellationRun | null>(null)
  const [loading, setLoading] = useState<boolean>(true)
  const [triggering, setTriggering] = useState<boolean>(false)
  const [error, setError] = useState<string | null>(null)
  const [searchQuery, setSearchQuery] = useState<string>('')
  const [outcomeFilter, setOutcomeFilter] = useState<string>('ALL')
  const [triggerSuccessMsg, setTriggerSuccessMsg] = useState<string | null>(null)

  const fetchData = async () => {
    setLoading(true)
    setError(null)
    try {
      const [listData, runData] = await Promise.all([
        autoCancellationService.getLast30Days(),
        autoCancellationService.getLatestRun().catch(() => null),
      ])
      setItems(listData)
      setLatestRun(runData)
    } catch (err: any) {
      console.error('Lỗi khi tải danh sách đơn huỷ tự động:', err)
      setError(
        err?.response?.data?.message ||
          'Không thể tải dữ liệu đơn huỷ tự động. Vui lòng kiểm tra quyền hạn của bạn.',
      )
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchData()
  }, [])

  const handleTriggerRun = async () => {
    if (!window.confirm('Bạn có chắc chắn muốn kích hoạt quét và huỷ đơn quá hạn ngay bây giờ không?')) {
      return
    }

    setTriggering(true)
    setTriggerSuccessMsg(null)
    setError(null)

    try {
      const runResult = await autoCancellationService.triggerRun()
      setTriggerSuccessMsg(
        `Quét hoàn tất: ${runResult.totalIdentified} đơn quá hạn được xử lý (${runResult.totalCancelled} huỷ, ${runResult.totalTransferred} chuyển bản sao, ${runResult.totalReleased} về sẵn sàng).`,
      )
      await fetchData()
    } catch (err: any) {
      console.error('Lỗi khi kích hoạt quét tự động:', err)
      setError(err?.response?.data?.message || 'Kích hoạt quét tự động thất bại.')
    } finally {
      setTriggering(false)
    }
  }

  // Thống kê nhanh
  const stats = useMemo(() => {
    const totalCancelled = items.length
    const transferred = items.filter((i) => i.copyOutcome === 'TRANSFERRED').length
    const available = items.filter((i) => i.copyOutcome === 'AVAILABLE').length
    return { totalCancelled, transferred, available }
  }, [items])

  // Lọc danh sách
  const filteredItems = useMemo(() => {
    return items.filter((item) => {
      // Lọc theo kết quả bản sao
      if (outcomeFilter !== 'ALL' && item.copyOutcome !== outcomeFilter) {
        return false
      }

      // Lọc theo từ khóa tìm kiếm
      if (!searchQuery.trim()) return true
      const q = searchQuery.toLowerCase().trim()
      const matchId = String(item.id).includes(q)
      const matchReader = item.readerName?.toLowerCase().includes(q)
      const matchBook = item.bookTitle?.toLowerCase().includes(q)
      const matchBarcode = item.barcode?.toLowerCase().includes(q)
      const matchNextReader = item.nextReaderName?.toLowerCase().includes(q)

      return matchId || matchReader || matchBook || matchBarcode || matchNextReader
    })
  }, [items, searchQuery, outcomeFilter])

  return (
    <div className="space-y-6 p-6">
      {/* Tiêu đề & Action Buttons */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <div className="flex items-center gap-2">
            <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-rose-100 text-rose-600 dark:bg-rose-900/40 dark:text-rose-400">
              <CalendarX className="h-5 w-5" />
            </div>
            <div>
              <h1 className="text-2xl font-bold tracking-tight text-slate-900 dark:text-slate-100">
                Đơn Huỷ Tự Động (30 Ngày)
              </h1>
              <p className="text-sm text-slate-500 dark:text-slate-400">
                Tra cứu các đơn đặt giữ quá hạn nhận được hệ thống tự động huỷ trong 30 ngày gần nhất
              </p>
            </div>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <button
            onClick={fetchData}
            disabled={loading}
            className="inline-flex items-center gap-2 rounded-lg border border-slate-300 bg-white px-3.5 py-2 text-sm font-medium text-slate-700 shadow-sm transition hover:bg-slate-50 disabled:opacity-50 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-300 dark:hover:bg-slate-700"
            title="Tải lại danh sách"
          >
            <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
            Làm mới
          </button>

          <button
            onClick={handleTriggerRun}
            disabled={triggering || loading}
            className="inline-flex items-center gap-2 rounded-lg bg-rose-600 px-4 py-2 text-sm font-medium text-white shadow-sm transition hover:bg-rose-700 focus:outline-none focus:ring-2 focus:ring-rose-500 focus:ring-offset-2 disabled:opacity-50 dark:bg-rose-600 dark:hover:bg-rose-700"
          >
            <Play className={`h-4 w-4 ${triggering ? 'animate-pulse' : ''}`} />
            {triggering ? 'Đang quét...' : 'Quét thủ công ngay'}
          </button>
        </div>
      </div>

      {/* Thông báo kết quả quét nếu có */}
      {triggerSuccessMsg && (
        <div className="flex items-center justify-between rounded-lg border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-800 dark:border-emerald-800/60 dark:bg-emerald-950/40 dark:text-emerald-300">
          <div className="flex items-center gap-2">
            <CheckCircle2 className="h-5 w-5 text-emerald-600 dark:text-emerald-400" />
            <span>{triggerSuccessMsg}</span>
          </div>
          <button
            onClick={() => setTriggerSuccessMsg(null)}
            className="text-emerald-700 hover:underline dark:text-emerald-400"
          >
            Đóng
          </button>
        </div>
      )}

      {error && (
        <div className="flex items-center gap-2 rounded-lg border border-rose-200 bg-rose-50 p-4 text-sm text-rose-800 dark:border-rose-800/60 dark:bg-rose-950/40 dark:text-rose-300">
          <AlertCircle className="h-5 w-5 text-rose-600 dark:text-rose-400" />
          <span>{error}</span>
        </div>
      )}

      {/* Thẻ số liệu thống kê (Stats Cards) */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {/* Card 1: Tổng đơn huỷ */}
        <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-800 dark:bg-slate-900">
          <div className="flex items-center justify-between">
            <span className="text-sm font-medium text-slate-500 dark:text-slate-400">
              Tổng đơn đã huỷ (30 ngày)
            </span>
            <span className="rounded-full bg-rose-100 p-2 text-rose-600 dark:bg-rose-900/50 dark:text-rose-400">
              <CalendarX className="h-4 w-4" />
            </span>
          </div>
          <div className="mt-2 flex items-baseline gap-2">
            <span className="text-2xl font-bold text-slate-900 dark:text-slate-100">
              {stats.totalCancelled}
            </span>
            <span className="text-xs text-slate-500">đơn</span>
          </div>
        </div>

        {/* Card 2: Bản sao đã chuyển tiếp */}
        <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-800 dark:bg-slate-900">
          <div className="flex items-center justify-between">
            <span className="text-sm font-medium text-slate-500 dark:text-slate-400">
              Bản sao đã chuyển tiếp
            </span>
            <span className="rounded-full bg-emerald-100 p-2 text-emerald-600 dark:bg-emerald-900/50 dark:text-emerald-400">
              <ArrowRight className="h-4 w-4" />
            </span>
          </div>
          <div className="mt-2 flex items-baseline gap-2">
            <span className="text-2xl font-bold text-emerald-600 dark:text-emerald-400">
              {stats.transferred}
            </span>
            <span className="text-xs text-slate-500">cho người chờ</span>
          </div>
        </div>

        {/* Card 3: Bản sao trả về Sẵn sàng */}
        <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-800 dark:bg-slate-900">
          <div className="flex items-center justify-between">
            <span className="text-sm font-medium text-slate-500 dark:text-slate-400">
              Bản sao về Sẵn sàng
            </span>
            <span className="rounded-full bg-blue-100 p-2 text-blue-600 dark:bg-blue-900/50 dark:text-blue-400">
              <RotateCcw className="h-4 w-4" />
            </span>
          </div>
          <div className="mt-2 flex items-baseline gap-2">
            <span className="text-2xl font-bold text-blue-600 dark:text-blue-400">
              {stats.available}
            </span>
            <span className="text-xs text-slate-500">khi hết hàng đợi</span>
          </div>
        </div>

        {/* Card 4: Lần chạy gần nhất */}
        <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-800 dark:bg-slate-900">
          <div className="flex items-center justify-between">
            <span className="text-sm font-medium text-slate-500 dark:text-slate-400">
              Lần quét gần nhất
            </span>
            <span className="rounded-full bg-violet-100 p-2 text-violet-600 dark:bg-violet-900/50 dark:text-violet-400">
              <Clock className="h-4 w-4" />
            </span>
          </div>
          <div className="mt-2">
            {latestRun ? (
              <div>
                <div className="flex items-center gap-1.5">
                  <span className="inline-block h-2 w-2 rounded-full bg-emerald-500" />
                  <span className="text-sm font-semibold text-slate-800 dark:text-slate-200">
                    {latestRun.status}
                  </span>
                  <span className="text-xs text-slate-400">
                    ({latestRun.triggeredBy})
                  </span>
                </div>
                <div className="mt-1 text-xs text-slate-500">
                  {formatDateTime(latestRun.startedAt)}
                </div>
              </div>
            ) : (
              <span className="text-sm text-slate-400">Chưa có dữ liệu chạy</span>
            )}
          </div>
        </div>
      </div>

      {/* Bộ lọc & Tìm kiếm */}
      <div className="flex flex-col gap-3 rounded-xl border border-slate-200 bg-white p-4 shadow-sm sm:flex-row sm:items-center sm:justify-between dark:border-slate-800 dark:bg-slate-900">
        <div className="relative flex-1">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
          <input
            type="text"
            placeholder="Tìm theo tên bạn đọc, tên sách, mã đơn (#), mã vạch (BC-)..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="w-full rounded-lg border border-slate-300 bg-white py-2 pl-9 pr-3 text-sm text-slate-900 placeholder-slate-400 transition focus:border-rose-500 focus:outline-none focus:ring-1 focus:ring-rose-500 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-100 dark:placeholder-slate-500"
          />
        </div>

        <div className="flex items-center gap-2">
          <span className="text-xs font-medium text-slate-500 dark:text-slate-400 whitespace-nowrap">
            Kết quả bản sao:
          </span>
          <select
            value={outcomeFilter}
            onChange={(e) => setOutcomeFilter(e.target.value)}
            className="rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 transition focus:border-rose-500 focus:outline-none focus:ring-1 focus:ring-rose-500 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-100"
          >
            <option value="ALL">Tất cả ({items.length})</option>
            <option value="TRANSFERRED">Đã chuyển cho người kế tiếp ({stats.transferred})</option>
            <option value="AVAILABLE">Trả về Sẵn sàng ({stats.available})</option>
          </select>
        </div>
      </div>

      {/* Bảng danh sách đơn huỷ tự động */}
      <div className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm dark:border-slate-800 dark:bg-slate-900">
        {loading ? (
          <div className="flex flex-col items-center justify-center p-12 text-slate-400">
            <RefreshCw className="h-8 w-8 animate-spin" />
            <p className="mt-3 text-sm font-medium">Đang tải danh sách đơn huỷ tự động...</p>
          </div>
        ) : filteredItems.length === 0 ? (
          <div className="flex flex-col items-center justify-center p-12 text-center">
            <div className="flex h-12 w-12 items-center justify-center rounded-full bg-slate-100 text-slate-400 dark:bg-slate-800">
              <CheckCircle2 className="h-6 w-6 text-emerald-500" />
            </div>
            <h3 className="mt-3 text-base font-semibold text-slate-800 dark:text-slate-200">
              Không có đơn nào
            </h3>
            <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
              {searchQuery || outcomeFilter !== 'ALL'
                ? 'Không tìm thấy đơn huỷ tự động phù hợp với bộ lọc hiện tại.'
                : 'Không có đơn đặt giữ nào bị hệ thống huỷ do quá hạn nhận trong 30 ngày qua.'}
            </p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm text-slate-600 dark:text-slate-300">
              <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase text-slate-500 dark:border-slate-800 dark:bg-slate-800/60 dark:text-slate-400">
                <tr>
                  <th className="px-4 py-3.5 font-semibold">Mã đơn</th>
                  <th className="px-4 py-3.5 font-semibold">Bạn đọc</th>
                  <th className="px-4 py-3.5 font-semibold">Đầu sách & Bản sao</th>
                  <th className="px-4 py-3.5 font-semibold">Chờ nhận & Hạn</th>
                  <th className="px-4 py-3.5 font-semibold">Thời điểm huỷ</th>
                  <th className="px-4 py-3.5 font-semibold">Kết quả xử lý bản sao</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
                {filteredItems.map((row) => (
                  <tr
                    key={row.id}
                    className="transition hover:bg-slate-50/80 dark:hover:bg-slate-800/40"
                  >
                    {/* Cột 1: Mã đơn */}
                    <td className="px-4 py-3.5 font-mono text-xs font-semibold text-slate-900 dark:text-slate-100 whitespace-nowrap">
                      #{row.id}
                    </td>

                    {/* Cột 2: Bạn đọc */}
                    <td className="px-4 py-3.5">
                      <div className="flex items-center gap-2">
                        <div className="flex h-7 w-7 items-center justify-center rounded-full bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-300">
                          <UserIcon className="h-3.5 w-3.5" />
                        </div>
                        <div>
                          <div className="font-medium text-slate-900 dark:text-slate-100">
                            {row.readerName || 'Không rõ'}
                          </div>
                          <div className="text-xs text-slate-400">
                            ID: {row.readerId}
                          </div>
                        </div>
                      </div>
                    </td>

                    {/* Cột 3: Tên sách & Mã vạch */}
                    <td className="px-4 py-3.5 max-w-xs">
                      <div className="flex items-start gap-2">
                        <BookOpen className="mt-0.5 h-4 w-4 shrink-0 text-slate-400" />
                        <div>
                          <div className="font-medium text-slate-900 line-clamp-2 dark:text-slate-100">
                            {row.bookTitle}
                          </div>
                          <div className="mt-0.5 inline-flex items-center gap-1 font-mono text-xs text-slate-500">
                            Barcode: <span className="font-semibold text-slate-700 dark:text-slate-300">{row.barcode || 'N/A'}</span>
                          </div>
                        </div>
                      </div>
                    </td>

                    {/* Cột 4: Thời điểm bắt đầu chờ nhận & Hạn nhận */}
                    <td className="px-4 py-3.5 text-xs whitespace-nowrap">
                      <div>
                        <span className="text-slate-400">Bắt đầu: </span>
                        <span className="text-slate-700 dark:text-slate-300">{formatDateTime(row.reservedAt)}</span>
                      </div>
                      <div className="mt-0.5">
                        <span className="text-slate-400">Hạn nhận: </span>
                        <span className="font-medium text-rose-600 dark:text-rose-400">{formatDateTime(row.pickupDeadline)}</span>
                      </div>
                    </td>

                    {/* Cột 5: Thời điểm bị huỷ tự động */}
                    <td className="px-4 py-3.5 text-xs whitespace-nowrap">
                      <div className="font-medium text-slate-900 dark:text-slate-100">
                        {formatDateTime(row.cancelledAt)}
                      </div>
                      <div className="mt-0.5 inline-flex items-center gap-1 text-[11px] text-slate-400">
                        <span>Bởi: {row.cancelledByName}</span>
                      </div>
                    </td>

                    {/* Cột 6: Kết quả xử lý bản sao */}
                    <td className="px-4 py-3.5">
                      {row.copyOutcome === 'TRANSFERRED' ? (
                        <div className="space-y-1">
                          <span className="inline-flex items-center gap-1 rounded-full bg-emerald-50 px-2.5 py-1 text-xs font-medium text-emerald-700 ring-1 ring-inset ring-emerald-600/20 dark:bg-emerald-950/50 dark:text-emerald-400">
                            <Sparkles className="h-3 w-3" />
                            Đã chuyển người kế tiếp
                          </span>
                          {row.nextReaderName && (
                            <div className="text-xs text-slate-600 dark:text-slate-300">
                              <span className="text-slate-400">Nhận tiếp: </span>
                              <span className="font-semibold text-emerald-600 dark:text-emerald-400">
                                {row.nextReaderName}
                              </span>
                              {row.nextPickupDeadline && (
                                <div className="text-[11px] text-slate-400">
                                  Hạn mới: {formatDateTime(row.nextPickupDeadline)}
                                </div>
                              )}
                            </div>
                          )}
                        </div>
                      ) : row.copyOutcome === 'AVAILABLE' ? (
                        <span className="inline-flex items-center gap-1 rounded-full bg-blue-50 px-2.5 py-1 text-xs font-medium text-blue-700 ring-1 ring-inset ring-blue-600/20 dark:bg-blue-950/50 dark:text-blue-400">
                          <RotateCcw className="h-3 w-3" />
                          Trả về Sẵn sàng
                        </span>
                      ) : (
                        <span className="inline-flex items-center rounded-full bg-slate-100 px-2.5 py-1 text-xs font-medium text-slate-600 dark:bg-slate-800 dark:text-slate-400">
                          Không có bản sao
                        </span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {/* Footer bảng */}
        <div className="border-t border-slate-200 bg-slate-50 px-4 py-3 text-xs text-slate-500 sm:flex sm:items-center sm:justify-between dark:border-slate-800 dark:bg-slate-800/40 dark:text-slate-400">
          <span>
            Hiển thị <span className="font-semibold text-slate-800 dark:text-slate-200">{filteredItems.length}</span> / {items.length} đơn bị huỷ trong 30 ngày gần nhất
          </span>
          <span className="mt-1 block sm:mt-0">
            Lịch chạy ngầm tự động lúc 00:30 hằng ngày (giờ Asia/Ho_Chi_Minh)
          </span>
        </div>
      </div>
    </div>
  )
}
