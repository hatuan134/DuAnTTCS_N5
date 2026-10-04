import { useRef, useState } from 'react'
import axios from 'axios'
import { Bookmark, LogIn } from 'lucide-react'
import { Link } from 'react-router-dom'
import { getAccessToken, getCurrentUser } from '../../core/auth/authStorage'
import { reservationService } from './reservationService'
import type { BookReservation } from './reservationService'

interface Props {
  bookId: number
  onReserved: () => void
}

export default function ReserveBookPanel({ bookId, onReserved }: Props) {
  const user = getCurrentUser()
  const signedIn = Boolean(user && getAccessToken())
  const roleAllowed = signedIn && user?.role === 'READER'
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')
  const [reservation, setReservation] = useState<BookReservation | null>(null)
  const pending = useRef(false)
  const reason = !signedIn
    ? 'Bạn chưa đăng nhập. Vui lòng đăng nhập để đặt giữ đầu sách.'
    : !roleAllowed
      ? 'Chỉ tài khoản Bạn đọc mới được đặt giữ đầu sách.'
      : ''

  async function reserve() {
    if (pending.current) return
    const currentUser = getCurrentUser()
    if (!currentUser || !getAccessToken()) {
      setError('Bạn chưa đăng nhập. Vui lòng đăng nhập để đặt giữ đầu sách.')
      return
    }
    if (currentUser.role !== 'READER') {
      setError('Chỉ tài khoản Bạn đọc mới được đặt giữ đầu sách.')
      return
    }
    pending.current = true
    setSubmitting(true)
    setError('')
    setReservation(null)
    try {
      const result = await reservationService.reserve(bookId)
      setReservation(result)
      onReserved()
    } catch (requestError) {
      setError(axios.isAxiosError<{ message?: string }>(requestError)
        ? requestError.response?.data?.message ?? 'Không thể kết nối tới hệ thống. Vui lòng thử lại.'
        : 'Không thể đặt giữ đầu sách. Vui lòng thử lại.')
    } finally {
      pending.current = false
      setSubmitting(false)
    }
  }

  return (
    <section className="border-t border-slate-200 bg-blue-50/40 p-6 sm:p-8" aria-labelledby="reserve-book-title">
      <h2 id="reserve-book-title" className="flex items-center gap-2 text-base font-bold text-slate-900">
        <Bookmark size={18} className="text-blue-600" /> Đặt giữ đầu sách
      </h2>
      <p className="mt-2 text-sm text-slate-600">
        Nếu còn bản Sẵn sàng, thư viện sẽ tự dành một bản cho bạn; nếu hết bản, yêu cầu được xếp hàng.
        Thẻ thư viện cần còn hạn và không bị khóa. Bạn được có tối đa 3 đơn đang chờ hoặc chờ đến nhận;
        mỗi đầu sách chỉ được có một đơn trong các trạng thái này.
      </p>
      {reason && <p role="alert" className="mt-3 text-sm text-amber-800">{reason}</p>}
      <div className="mt-4 flex flex-wrap items-center gap-3">
        <button type="button" onClick={() => void reserve()} disabled={!roleAllowed || submitting}
          className="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-4 py-2.5 text-sm font-semibold text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50">
          <Bookmark size={17} /> {submitting ? 'Đang đặt giữ…' : 'Đặt giữ'}
        </button>
        {roleAllowed && (
          <Link to="/my-reservations" className="text-sm font-semibold text-blue-700 hover:underline">
            Xem đơn đặt giữ của tôi
          </Link>
        )}
        {!signedIn && (
          <Link to="/login" state={{ from: `/catalog/books/${bookId}` }}
            className="inline-flex items-center gap-2 text-sm font-semibold text-blue-700 hover:underline">
            <LogIn size={17} /> Đăng nhập để đặt giữ
          </Link>
        )}
      </div>
      {error && <p role="alert" className="mt-4 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-700">{error}</p>}
      {reservation && (
        <div role="status" className="mt-4 rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-800">
          <p className="font-semibold">{reservation.message}</p>
          <p className="mt-1">Mã đơn: #{reservation.id} · Trạng thái: {reservation.status === 'READY_FOR_PICKUP' ? 'Sẵn sàng đến nhận' : 'Đang chờ'}</p>
          <p className="mt-1">Thời điểm đặt giữ: {new Date(reservation.reservedAt).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' })}</p>
          {reservation.status === 'READY_FOR_PICKUP' && reservation.reservedCopy && reservation.pickupDeadline ? (
            <dl className="mt-3 grid gap-3 rounded-lg border border-emerald-200 bg-white/70 p-3 sm:grid-cols-2">
              <div><dt className="text-xs text-emerald-700">Mã nhận diện bản sách</dt><dd className="mt-1 font-semibold">{reservation.reservedCopy.barcode}</dd></div>
              <div><dt className="text-xs text-emerald-700">Kho lấy sách</dt><dd className="mt-1 font-semibold">{reservation.reservedCopy.warehouseName} ({reservation.reservedCopy.warehouseCode})</dd></div>
              <div><dt className="text-xs text-emerald-700">Kệ lấy sách</dt><dd className="mt-1 font-semibold">{reservation.reservedCopy.shelfName || 'Kệ'} ({reservation.reservedCopy.shelfCode})</dd></div>
              <div><dt className="text-xs text-emerald-700">Hạn đến nhận (giờ Việt Nam)</dt><dd className="mt-1 font-semibold">{new Date(reservation.pickupDeadline).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' })}</dd></div>
              <p className="text-xs text-emerald-700 sm:col-span-2">Hạn nhận là giờ đóng cửa của ngày mở cửa thứ ba sau ngày đặt giữ, bỏ qua ngày thư viện đóng cửa.</p>
            </dl>
          ) : reservation.queuePosition !== null && (
            <p className="mt-1">Vị trí tại thời điểm đặt giữ: <strong>{reservation.queuePosition}</strong></p>
          )}
        </div>
      )}
    </section>
  )
}
