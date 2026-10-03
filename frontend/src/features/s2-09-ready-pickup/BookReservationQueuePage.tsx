import { useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import Button from '../../components/ui/Button'
import Card from '../../components/ui/Card'
import EmptyState from '../../components/ui/EmptyState'
import LoadingState from '../../components/ui/LoadingState'
import PageHeader from '../../components/ui/PageHeader'
import { getCurrentUser } from '../../core/auth/authStorage'
import { getApiErrorMessage } from '../s1-02-user-management/accountService'
import { formatPickupDate, pickupRoles, pickupService, reservationFilterStatuses, reservationStatusLabel } from './pickupService'
import type { BookReservationQueue, ReservationStatus, ReservationStatusFilter } from './pickupService'

const statusClasses: Record<ReservationStatus, string> = {
  PENDING: 'border-blue-200 bg-blue-50 text-blue-800',
  READY_FOR_PICKUP: 'border-amber-200 bg-amber-50 text-amber-800',
  FULFILLED: 'border-emerald-200 bg-emerald-50 text-emerald-800',
  CANCELLED: 'border-slate-200 bg-slate-100 text-slate-600',
  EXPIRED: 'border-red-200 bg-red-50 text-red-800',
}

export default function BookReservationQueuePage() {
  const { bookId } = useParams()
  const id = Number(bookId)
  const allowed = pickupRoles.includes(getCurrentUser()?.role ?? '')

  if (!allowed) return <p role="alert">Bạn không có quyền xem hàng đợi đặt giữ đầu sách.</p>
  if (!Number.isSafeInteger(id) || id < 1) return <div>
    <Link to="/cataloging" className="text-blue-600 hover:underline">← Sách đã biên mục</Link>
    <p role="alert" className="mt-4 rounded-lg bg-red-50 p-4 text-red-700">Mã đầu sách không hợp lệ.</p>
  </div>
  // Changing titles resets the view and cancels the previous title's refresh.
  return <ReservationQueue key={id} bookId={id} />
}

function ReservationQueue({ bookId }: { bookId: number }) {
  const [queue, setQueue] = useState<BookReservationQueue | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [status, setStatus] = useState<ReservationStatusFilter>('')
  const refreshRef = useRef<() => void>(() => {})

  useEffect(() => {
    let active = true
    let pending = false

    async function refresh() {
      if (!active || pending) return
      pending = true
      setLoading(true)
      setError('')
      try {
        const data = await pickupService.queue(bookId, status)
        if (active) {
          if (data.bookId !== bookId) throw new Error('Wrong title in queue response')
          if (status && data.items.some((item) => item.status !== status)) {
            throw new Error('Wrong status in queue response')
          }
          // Replace the whole snapshot: rows and positions must update together.
          setQueue(data)
        }
      } catch (e) {
        if (active) {
          setQueue(null)
          setError(getApiErrorMessage(e, 'Không tải được hàng đợi đặt giữ. Vui lòng thử lại.'))
        }
      } finally {
        pending = false
        if (active) setLoading(false)
      }
    }

    const refreshWhenVisible = () => {
      if (document.visibilityState === 'visible') void refresh()
    }
    refreshRef.current = () => { void refresh() }
    void refresh()
    const timer = window.setInterval(refreshWhenVisible, 10000)
    window.addEventListener('focus', refreshWhenVisible)
    document.addEventListener('visibilitychange', refreshWhenVisible)
    return () => {
      active = false
      window.clearInterval(timer)
      window.removeEventListener('focus', refreshWhenVisible)
      document.removeEventListener('visibilitychange', refreshWhenVisible)
      refreshRef.current = () => {}
    }
  }, [bookId, status])

  function changeStatus(value: ReservationStatusFilter) {
    if (value === status) return
    // Hide the previous filter's rows immediately while fetching the new snapshot.
    setQueue(null)
    setError('')
    setLoading(true)
    setStatus(value)
  }

  const items = queue?.items ?? []
  const pendingCount = items.filter((item) => item.status === 'PENDING').length

  return (
    <div>
      <Link to={`/books/${bookId}`} className="mb-4 inline-block text-sm font-medium text-blue-600 hover:underline">
        ← Chi tiết đầu sách
      </Link>
      <PageHeader
        title="Hàng đợi đặt giữ"
        description={queue?.bookTitle ?? `Đầu sách #${bookId}`}
        action={<Button type="button" variant="secondary" loading={loading}
          onClick={() => refreshRef.current()}>Làm mới</Button>}
      />
      <p className="mb-4 text-sm text-slate-500">
        Các đơn của đầu sách theo thời điểm đặt từ sớm đến muộn trong từng trạng thái. Vị trí chỉ tính các đơn Đang xếp hàng;
        {' '}đơn đã cấp bản hoặc kết thúc không còn vị trí trong hàng đợi.
        {' '}Giờ hiển thị theo Việt Nam. Tự cập nhật mỗi 10 giây khi đang xem trang.
      </p>
      <div className="mb-5 flex flex-wrap items-end gap-3">
        <div className="w-full sm:w-80">
          <label htmlFor="reservation-status" className="mb-2 block text-sm font-medium text-slate-700">
            Trạng thái đơn đặt giữ
          </label>
          <select id="reservation-status" value={status}
            onChange={(event) => changeStatus(event.target.value as ReservationStatusFilter)}
            className="h-11 w-full rounded-lg border border-slate-300 bg-white px-3 text-sm text-slate-900 shadow-sm outline-none focus:border-blue-500 focus:ring-4 focus:ring-blue-100">
            <option value="">Tất cả trạng thái</option>
            {reservationFilterStatuses.map((value) => <option key={value} value={value}>
              {reservationStatusLabel(value)}
            </option>)}
          </select>
        </div>
        {status && <Button type="button" variant="secondary" onClick={() => changeStatus('')}>
          Bỏ bộ lọc
        </Button>}
      </div>
      {error && <div role="alert" className="mb-4 rounded-lg bg-red-50 p-4 text-red-700">
        {error} Nhấn “Làm mới” để thử lại.
      </div>}
      {loading && <div role="status"><LoadingState /></div>}
      {!loading && !error && queue && items.length === 0 && <EmptyState
        title={status ? 'Không có đơn ở trạng thái đã chọn' : 'Đầu sách chưa có đơn đặt giữ'}
        description={status
          ? `Không có đơn ${reservationStatusLabel(status)} cho đầu sách này. Chọn trạng thái khác hoặc bỏ bộ lọc.`
          : 'Chưa có bạn đọc nào đặt giữ đầu sách này.'}
      />}
      {!loading && !error && queue && items.length > 0 && <Card className="overflow-hidden">
        <div role="status" className="border-b border-slate-200 px-5 py-4 text-sm text-slate-700">
          <strong>{items.length}</strong> đơn hiển thị; <strong>{pendingCount}</strong> đơn đang xếp hàng trong kết quả.
        </div>
        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-slate-200 text-sm">
            <caption className="sr-only">Các đơn đặt giữ của {queue.bookTitle}, {status ? reservationStatusLabel(status) : 'tất cả trạng thái'}, từ sớm đến muộn</caption>
            <thead className="bg-slate-50 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
              <tr>
                <th scope="col" className="px-5 py-3">Đơn</th>
                <th scope="col" className="px-5 py-3">Vị trí hàng đợi</th>
                <th scope="col" className="px-5 py-3">Bạn đọc</th>
                <th scope="col" className="px-5 py-3">Thời điểm đặt</th>
                <th scope="col" className="px-5 py-3">Trạng thái</th>
                <th scope="col" className="px-5 py-3">Mã vạch bản sao</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 bg-white">
              {items.map((item) => <tr key={item.id} className="align-top hover:bg-slate-50">
                <td className="px-5 py-4 font-medium text-slate-700">#{item.id}</td>
                <td className="px-5 py-4 font-semibold text-blue-700">
                  {item.queuePosition == null ? <span className="text-slate-500">—</span> : `#${item.queuePosition}`}
                </td>
                <td className="px-5 py-4 text-slate-900">{item.readerName}</td>
                <td className="whitespace-nowrap px-5 py-4 text-slate-700">
                  <time dateTime={item.reservedAt}>{formatPickupDate(item.reservedAt, true)}</time>
                </td>
                <td className="px-5 py-4">
                  <span className={`inline-flex whitespace-nowrap rounded-full border px-3 py-1 text-xs font-semibold ${statusClasses[item.status] ?? statusClasses.CANCELLED}`}>
                    {reservationStatusLabel(item.status)}
                  </span>
                </td>
                <td className="break-all px-5 py-4 font-mono font-semibold text-slate-900">
                  {item.barcode || 'Chưa cấp bản'}
                </td>
              </tr>)}
            </tbody>
          </table>
        </div>
      </Card>}
    </div>
  )
}
