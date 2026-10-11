import { useEffect, useMemo, useRef, useState } from 'react'
import axios from 'axios'
import { Bookmark, LogIn, Minus, Plus } from 'lucide-react'
import { Link } from 'react-router-dom'
import FeedbackAlert from '../../components/ui/FeedbackAlert'
import { getAccessToken, getCurrentUser } from '../../core/auth/authStorage'
import { reservationService } from './reservationService'
import type { BookReservation, BookReservationBatch, MyBookReservation } from './reservationService'

interface Props {
  bookId: number
  availableCount: number
  onReserved: () => void
}

const MAX_ACTIVE_RESERVATIONS = 3

function activeReservationCount(items: MyBookReservation[]) {
  return items.filter((item) => item.status === 'PENDING' || item.status === 'READY_FOR_PICKUP').length
}

function formatDate(value: string | null) {
  if (!value) return 'Chưa xác định'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return 'Chưa xác định'
  return date.toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' })
}

export default function ReserveBookPanel({ bookId, availableCount, onReserved }: Props) {
  const user = getCurrentUser()
  const signedIn = Boolean(user && getAccessToken())
  const roleAllowed = signedIn && user?.role === 'READER'
  const [submitting, setSubmitting] = useState(false)
  const [loadingMine, setLoadingMine] = useState(roleAllowed)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [reservation, setReservation] = useState<BookReservation | null>(null)
  const [batch, setBatch] = useState<BookReservationBatch | null>(null)
  const [activeCount, setActiveCount] = useState(0)
  const [quantity, setQuantity] = useState(1)
  const pending = useRef(false)
  const remainingSlots = Math.max(0, MAX_ACTIVE_RESERVATIONS - activeCount)
  const maxQuantity = Math.max(0, Math.min(availableCount, remainingSlots))
  const canChooseQuantity = availableCount > 0
  const reason = !signedIn
    ? 'Bạn chưa đăng nhập. Vui lòng đăng nhập để đặt giữ đầu sách.'
    : !roleAllowed
      ? 'Chỉ tài khoản Bạn đọc mới được đặt giữ đầu sách.'
      : remainingSlots === 0
        ? 'Bạn đã có 3 đơn đặt giữ đang hiệu lực. Hãy hoàn tất hoặc huỷ một đơn trước khi đặt thêm.'
        : ''

  useEffect(() => {
    let active = true
    if (!roleAllowed) {
      setLoadingMine(false)
      return () => { active = false }
    }

    setLoadingMine(true)
    reservationService.listMine()
      .then((items) => {
        if (active) setActiveCount(activeReservationCount(items))
      })
      .catch(() => {
        if (active) setError('Không tải được số lượng đơn đặt giữ hiện tại. Vui lòng thử lại.')
      })
      .finally(() => {
        if (active) setLoadingMine(false)
      })

    return () => { active = false }
  }, [bookId, roleAllowed])

  useEffect(() => {
    if (maxQuantity > 0) {
      setQuantity((current) => Math.min(Math.max(1, current), maxQuantity))
    } else {
      setQuantity(1)
    }
  }, [maxQuantity])

  const quantityHint = useMemo(() => {
    if (!roleAllowed) return ''
    if (availableCount <= 0) return 'Hiện không có bản sẵn sàng. Bạn vẫn có thể tạo một đơn để xếp hàng chờ bản trả về.'
    return `Có ${availableCount} bản sẵn sàng. Bạn còn ${remainingSlots} lượt đặt giữ trong giới hạn tối đa 3 đơn đang hiệu lực.`
  }, [availableCount, remainingSlots, roleAllowed])

  const increaseHint = loadingMine
    ? 'Đang kiểm tra số đơn đặt giữ của bạn…'
    : submitting
      ? 'Đang gửi yêu cầu đặt giữ…'
      : remainingSlots === 0
        ? 'Bạn đã đạt giới hạn 3 đơn đặt giữ đang hiệu lực.'
        : quantity < maxQuantity
          ? `Bạn có thể chọn tối đa ${maxQuantity} bản.`
          : availableCount <= remainingSlots
            ? `Không thể tăng thêm: đầu sách hiện chỉ có ${availableCount} bản sẵn sàng.`
            : `Không thể tăng thêm: bạn chỉ còn ${remainingSlots} lượt trong giới hạn 3 đơn đặt giữ đang hiệu lực.`

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
    if (remainingSlots <= 0) {
      setError('Bạn đã có 3 đơn đặt giữ đang hiệu lực. Hãy hoàn tất hoặc huỷ một đơn trước khi đặt thêm.')
      return
    }
    if (canChooseQuantity && (quantity < 1 || quantity > maxQuantity)) {
      setError(`Số lượng đặt giữ phải từ 1 đến ${maxQuantity}.`)
      return
    }

    pending.current = true
    setSubmitting(true)
    setError('')
    setNotice('')
    setReservation(null)
    setBatch(null)
    try {
      if (canChooseQuantity) {
        const result = await reservationService.reserveMany(bookId, quantity)
        setBatch(result)
        setNotice(result.message)
        setActiveCount(result.activeReservationCount)
      } else {
        const result = await reservationService.reserve(bookId)
        setReservation(result)
        setNotice(result.message)
        setActiveCount((current) => Math.min(MAX_ACTIVE_RESERVATIONS, current + 1))
      }
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
        Khi còn bản sẵn sàng, bạn có thể đặt giữ nhiều bản cùng một đầu sách nhưng không vượt quá số bản sẵn sàng
        và tổng cộng không quá 3 đơn đang hiệu lực. Nếu hết bản, một yêu cầu đặt giữ sẽ được xếp vào hàng đợi.
        Thẻ thư viện cần còn hạn, không bị khóa và bạn không được đang mượn đầu sách này chưa trả.
      </p>
      {reason && <p role="alert" className="mt-3 text-sm font-medium text-red-700">{reason}</p>}
      {quantityHint && <p className="mt-3 text-sm font-medium text-slate-700">{quantityHint}</p>}

      {roleAllowed && canChooseQuantity && (
        <div className="mt-4 flex flex-wrap items-center gap-3">
          <span className="text-sm font-semibold text-slate-700">Số lượng đặt giữ:</span>
          <div className="inline-flex items-center rounded-lg border border-slate-300 bg-white" aria-label="Chọn số lượng đặt giữ">
            <button type="button" aria-label="Giảm số lượng" onClick={() => setQuantity((current) => Math.max(1, current - 1))}
              disabled={submitting || loadingMine || quantity <= 1}
              className="min-h-11 min-w-11 rounded-l-lg p-2 text-slate-600 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40">
              <Minus size={16} />
            </button>
            <output className="min-w-10 border-x border-slate-200 px-3 py-2 text-center text-sm font-bold text-slate-900">
              {quantity}
            </output>
            <button type="button" aria-label="Tăng số lượng" aria-describedby="reservation-quantity-hint" title={increaseHint} onClick={() => setQuantity((current) => Math.min(maxQuantity, current + 1))}
              disabled={submitting || loadingMine || quantity >= maxQuantity}
              className="min-h-11 min-w-11 rounded-r-lg p-2 text-slate-600 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40">
              <Plus size={16} />
            </button>
          </div>
          <p id="reservation-quantity-hint" role="status" className="max-w-lg text-sm leading-6 text-slate-600">{increaseHint}</p>
        </div>
      )}

      <div className="mt-4 flex flex-wrap items-center gap-3">
        <button type="button" onClick={() => void reserve()}
          disabled={!roleAllowed || submitting || loadingMine || remainingSlots <= 0 || (canChooseQuantity && maxQuantity <= 0)}
          className="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-4 py-2.5 text-sm font-semibold text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50">
          <Bookmark size={17} />
          {submitting
            ? 'Đang đặt giữ…'
            : canChooseQuantity
              ? `Đặt giữ ${quantity} bản`
              : 'Đặt giữ vào hàng đợi'}
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

      {error && <FeedbackAlert message={error} tone="error" onDismiss={() => setError('')} className="mt-4" />}

      {notice && <FeedbackAlert message={notice} tone="success" onDismiss={() => setNotice('')} className="mt-4" />}

      {batch && (
        <div role="status" className="mt-4 rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-800">
          <p className="font-semibold">Thông tin các đơn vừa tạo</p>
          <p className="mt-1">Đã tạo {batch.createdCount} đơn. Bạn còn {batch.remainingActiveSlots} lượt đặt giữ đang hiệu lực.</p>
          <div className="mt-3 grid gap-2 sm:grid-cols-2">
            {batch.reservations.map((item, index) => (
              <div key={item.id} className="rounded-lg border border-emerald-200 bg-white/80 p-3">
                <p className="font-semibold">Bản #{index + 1} · Đơn #{item.id}</p>
                <p className="mt-1">Đặt lúc: {formatDate(item.reservedAt)}</p>
                {item.reservedCopy && <p className="mt-1">Mã bản sao: <strong>{item.reservedCopy.barcode}</strong></p>}
                {item.pickupDeadline && <p className="mt-1">Hạn nhận: <strong>{formatDate(item.pickupDeadline)}</strong></p>}
              </div>
            ))}
          </div>
        </div>
      )}

      {reservation && (
        <div role="status" className="mt-4 rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-800">
          <p className="font-semibold">Thông tin đơn đặt giữ</p>
          <p className="mt-1">Mã đơn: #{reservation.id} · Trạng thái: {reservation.status === 'READY_FOR_PICKUP' ? 'Sẵn sàng đến nhận' : 'Đang chờ'}</p>
          <p className="mt-1">Thời điểm đặt giữ: {formatDate(reservation.reservedAt)}</p>
          {reservation.status === 'READY_FOR_PICKUP' && reservation.reservedCopy && reservation.pickupDeadline ? (
            <dl className="mt-3 grid gap-3 rounded-lg border border-emerald-200 bg-white/70 p-3 sm:grid-cols-2">
              <div><dt className="text-xs text-emerald-700">Mã nhận diện bản sách</dt><dd className="mt-1 font-semibold">{reservation.reservedCopy.barcode}</dd></div>
              <div><dt className="text-xs text-emerald-700">Kho lấy sách</dt><dd className="mt-1 font-semibold">{reservation.reservedCopy.warehouseName} ({reservation.reservedCopy.warehouseCode})</dd></div>
              <div><dt className="text-xs text-emerald-700">Kệ lấy sách</dt><dd className="mt-1 font-semibold">{reservation.reservedCopy.shelfName || 'Kệ'} ({reservation.reservedCopy.shelfCode})</dd></div>
              <div><dt className="text-xs text-emerald-700">Hạn đến nhận (giờ Việt Nam)</dt><dd className="mt-1 font-semibold">{formatDate(reservation.pickupDeadline)}</dd></div>
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
