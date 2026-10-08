import { useEffect, useState, useMemo } from 'react'
import {
  RefreshCw,
  Search,
  CheckCircle2,
  AlertCircle,
  Play,
  RotateCcw,
  Sparkles,
  BookOpen,
  User as UserIcon,
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
      if (outcomeFilter !== 'ALL' && item.copyOutcome !== outcomeFilter) {
        return false
      }

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
    <div className="space-y-6">
      {/* Tiêu đề trang đồng bộ 100% với Kho & Kệ */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">
            Đơn huỷ tự động
          </h1>
          <p className="mt-1 text-sm text-slate-500">
            Khai báo và tra cứu các đơn đặt giữ quá hạn nhận được hệ thống tự động huỷ trong 30 ngày gần nhất.
          </p>
        </div>

        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={fetchData}
            disabled={loading}
            className="inline-flex items-center gap-2 rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm font-semibold text-slate-700 shadow-sm transition hover:border-slate-300 hover:bg-slate-50 disabled:opacity-50"
          >
            <RefreshCw className={`h-4 w-4 text-slate-500 ${loading ? 'animate-spin' : ''}`} />
            Làm mới
          </button>

          <button
            type="button"
            onClick={handleTriggerRun}
            disabled={triggering || loading}
            className="inline-flex items-center gap-2 rounded-xl bg-blue-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition hover:bg-blue-700 disabled:opacity-50"
          >
            <Play className={`h-4 w-4 ${triggering ? 'animate-pulse' : ''}`} />
            {triggering ? 'Đang quét...' : 'Quét thủ công ngay'}
          </button>
        </div>
      </div>

      {/* Thông báo thông tin kết quả quét / lỗi */}
      {triggerSuccessMsg && (
        <div className="flex items-center justify-between rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-800">
          <div className="flex items-center gap-2">
            <CheckCircle2 className="h-5 w-5 text-emerald-600" />
            <span>{triggerSuccessMsg}</span>
          </div>
          <button
            type="button"
            onClick={() => setTriggerSuccessMsg(null)}
            className="text-emerald-700 hover:underline text-xs font-semibold"
          >
            Đóng
          </button>
        </div>
      )}

      {error && (
        <div className="flex items-center gap-2 rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700">
          <AlertCircle className="h-5 w-5 text-red-600" />
          <span>{error}</span>
        </div>
      )}

      {/* 4 Thẻ thống kê chuẩn giao diện dự án: Nền trắng, viền mỏng, chữ đen to rõ nét */}
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {/* Card 1: Tổng đơn huỷ */}
        <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
          <p className="text-sm font-medium text-slate-500">Tổng đơn huỷ (30 ngày)</p>
          <p className="mt-2 text-2xl font-bold text-slate-900">{stats.totalCancelled}</p>
        </div>

        {/* Card 2: Bản sao đã chuyển tiếp */}
        <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
          <p className="text-sm font-medium text-slate-500">Bản sao đã chuyển tiếp</p>
          <p className="mt-2 text-2xl font-bold text-emerald-600">{stats.transferred}</p>
        </div>

        {/* Card 3: Bản sao về Sẵn sàng */}
        <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
          <p className="text-sm font-medium text-slate-500">Bản sao về Sẵn sàng</p>
          <p className="mt-2 text-2xl font-bold text-blue-600">{stats.available}</p>
        </div>

        {/* Card 4: Lần quét gần nhất */}
        <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
          <p className="text-sm font-medium text-slate-500">Lần quét gần nhất</p>
          {latestRun ? (
            <div className="mt-2">
              <div className="flex items-center gap-1.5 font-bold text-slate-900">
                <span className="inline-block h-2 w-2 rounded-full bg-emerald-500" />
                <span>{latestRun.status}</span>
                <span className="text-xs font-normal text-slate-400">({latestRun.triggeredBy})</span>
              </div>
              <p className="mt-0.5 text-xs text-slate-400">{formatDateTime(latestRun.startedAt)}</p>
            </div>
          ) : (
            <p className="mt-2 text-base font-semibold text-slate-400">Chưa có dữ liệu</p>
          )}
        </div>
      </div>

      {/* Card Danh sách đơn huỷ tự động */}
      <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
        {/* Section Header */}
        <div className="border-b border-slate-200 p-5">
          <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <h2 className="text-lg font-semibold text-slate-900">
                Danh sách đơn huỷ tự động
              </h2>
              <p className="mt-1 text-sm text-slate-500">
                Hiển thị các đơn đặt giữ quá hạn nhận bị hệ thống tự động huỷ trong 30 ngày qua.
              </p>
            </div>

            {/* Bộ lọc theo kết quả bản sao */}
            <div className="flex items-center gap-2">
              <span className="text-xs font-semibold text-slate-500 whitespace-nowrap">
                Kết quả bản sao:
              </span>
              <select
                value={outcomeFilter}
                onChange={(e) => setOutcomeFilter(e.target.value)}
                className="rounded-xl border border-slate-200 bg-white px-3.5 py-2 text-sm text-slate-700 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
              >
                <option value="ALL">Tất cả ({items.length})</option>
                <option value="TRANSFERRED">Đã chuyển người kế tiếp ({stats.transferred})</option>
                <option value="AVAILABLE">Trả về Sẵn sàng ({stats.available})</option>
              </select>
            </div>
          </div>

          {/* Ô tìm kiếm nhanh */}
          <div className="relative mt-4">
            <Search className="absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
            <input
              type="text"
              placeholder="Tìm theo tên bạn đọc, tên sách, mã đơn (#), mã vạch (BC-)..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full rounded-xl border border-slate-200 bg-slate-50 py-2.5 pl-10 pr-4 text-sm text-slate-900 placeholder:text-slate-400 focus:border-blue-500 focus:bg-white focus:outline-none focus:ring-1 focus:ring-blue-500"
            />
          </div>
        </div>

        {/* Nội dung bảng */}
        {loading ? (
          <div className="flex items-center justify-center gap-3 p-12 text-sm text-slate-500">
            <span className="h-5 w-5 animate-spin rounded-full border-2 border-slate-300 border-t-blue-600" />
            Đang tải dữ liệu...
          </div>
        ) : filteredItems.length === 0 ? (
          <div className="p-12 text-center">
            <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-slate-100 text-slate-400">
              <CheckCircle2 className="h-6 w-6 text-emerald-500" />
            </div>
            <h3 className="mt-3 text-base font-semibold text-slate-900">
              Không có đơn nào
            </h3>
            <p className="mt-1 text-sm text-slate-500">
              {searchQuery || outcomeFilter !== 'ALL'
                ? 'Không tìm thấy đơn huỷ tự động phù hợp với bộ lọc hiện tại.'
                : 'Không có đơn đặt giữ nào bị hệ thống huỷ do quá hạn nhận trong 30 ngày qua.'}
            </p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm text-slate-700">
              <thead className="border-b border-slate-200 bg-slate-50 text-xs font-semibold uppercase text-slate-500">
                <tr>
                  <th className="px-5 py-3.5">Mã đơn</th>
                  <th className="px-5 py-3.5">Bạn đọc</th>
                  <th className="px-5 py-3.5">Đầu sách & Bản sao</th>
                  <th className="px-5 py-3.5">Chờ nhận & Hạn</th>
                  <th className="px-5 py-3.5">Thời điểm huỷ</th>
                  <th className="px-5 py-3.5">Kết quả xử lý bản sao</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {filteredItems.map((row) => (
                  <tr key={row.id} className="transition hover:bg-slate-50/70">
                    {/* Cột 1: Mã đơn */}
                    <td className="px-5 py-4 font-mono text-xs font-bold text-slate-900 whitespace-nowrap">
                      #{row.id}
                    </td>

                    {/* Cột 2: Bạn đọc */}
                    <td className="px-5 py-4">
                      <div className="flex items-center gap-2.5">
                        <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-blue-50 text-blue-600 font-semibold text-xs">
                          <UserIcon className="h-4 w-4" />
                        </div>
                        <div>
                          <div className="font-semibold text-slate-900">
                            {row.readerName || 'Không rõ'}
                          </div>
                          <div className="text-xs text-slate-400">
                            Mã bạn đọc: #{row.readerId}
                          </div>
                        </div>
                      </div>
                    </td>

                    {/* Cột 3: Tên sách & Bản sao */}
                    <td className="px-5 py-4 max-w-xs">
                      <div className="flex items-start gap-2.5">
                        <BookOpen className="mt-0.5 h-4 w-4 shrink-0 text-slate-400" />
                        <div>
                          <div className="font-medium text-slate-900 line-clamp-2">
                            {row.bookTitle}
                          </div>
                          <div className="mt-0.5 inline-flex items-center gap-1 font-mono text-xs text-slate-500">
                            Barcode: <span className="font-semibold text-slate-700">{row.barcode || 'N/A'}</span>
                          </div>
                        </div>
                      </div>
                    </td>

                    {/* Cột 4: Chờ nhận & Hạn */}
                    <td className="px-5 py-4 text-xs whitespace-nowrap">
                      <div>
                        <span className="text-slate-400">Bắt đầu: </span>
                        <span className="font-medium text-slate-700">{formatDateTime(row.reservedAt)}</span>
                      </div>
                      <div className="mt-1">
                        <span className="text-slate-400">Hạn nhận: </span>
                        <span className="font-semibold text-rose-600">{formatDateTime(row.pickupDeadline)}</span>
                      </div>
                    </td>

                    {/* Cột 5: Thời điểm huỷ */}
                    <td className="px-5 py-4 text-xs whitespace-nowrap">
                      <div className="font-semibold text-slate-900">
                        {formatDateTime(row.cancelledAt)}
                      </div>
                      <div className="mt-0.5 text-[11px] text-slate-400">
                        Bởi: {row.cancelledByName}
                      </div>
                    </td>

                    {/* Cột 6: Kết quả bản sao */}
                    <td className="px-5 py-4">
                      {row.copyOutcome === 'TRANSFERRED' ? (
                        <div className="space-y-1">
                          <span className="inline-flex items-center gap-1 rounded-md bg-emerald-50 px-2 py-1 text-xs font-medium text-emerald-700">
                            <Sparkles className="h-3 w-3" />
                            Đã chuyển người kế tiếp
                          </span>
                          {row.nextReaderName && (
                            <div className="text-xs text-slate-600">
                              <span className="text-slate-400">Người nhận: </span>
                              <span className="font-semibold text-emerald-600">
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
                        <span className="inline-flex items-center gap-1 rounded-md bg-blue-50 px-2 py-1 text-xs font-medium text-blue-700">
                          <RotateCcw className="h-3 w-3" />
                          Trả về Sẵn sàng
                        </span>
                      ) : (
                        <span className="inline-flex items-center rounded-md bg-slate-100 px-2 py-1 text-xs font-medium text-slate-600">
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
        <div className="border-t border-slate-200 bg-slate-50 px-5 py-3.5 text-xs text-slate-500 sm:flex sm:items-center sm:justify-between">
          <span>
            Hiển thị <span className="font-bold text-slate-800">{filteredItems.length}</span> / {items.length} đơn bị huỷ trong 30 ngày gần nhất
          </span>
          <span className="mt-1 block sm:mt-0">
            Lịch chạy ngầm tự động lúc 00:30 hằng ngày (giờ Asia/Ho_Chi_Minh)
          </span>
        </div>
      </div>
    </div>
  )
}
