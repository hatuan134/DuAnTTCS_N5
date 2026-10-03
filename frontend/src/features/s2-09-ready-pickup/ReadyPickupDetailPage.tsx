import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import Button from '../../components/ui/Button'
import Card from '../../components/ui/Card'
import LoadingState from '../../components/ui/LoadingState'
import PageHeader from '../../components/ui/PageHeader'
import { getCurrentUser } from '../../core/auth/authStorage'
import { getApiErrorMessage } from '../s1-02-user-management/accountService'
import { formatPickupDate, pickupRoles, pickupService } from './pickupService'
import type { ReadyPickupReservation } from './pickupService'

export default function ReadyPickupDetailPage() {
  const { reservationId } = useParams()
  const id = Number(reservationId)
  const allowed = pickupRoles.includes(getCurrentUser()?.role ?? '')

  if (!allowed) return <p role="alert">Bạn không có quyền xem đơn đặt giữ đang chờ nhận.</p>
  if (!Number.isSafeInteger(id) || id < 1) return <div>
    <Link to="/reservations/ready-for-pickup" className="text-blue-600 hover:underline">← Sách đang chờ nhận</Link>
    <p role="alert" className="mt-4 rounded-lg bg-red-50 p-4 text-red-700">Mã đơn đặt giữ không hợp lệ.</p>
  </div>
  return <ReadyPickupDetail key={id} id={id} />
}

// A new id mounts a fresh detail view, so the previous order never flashes on screen.
function ReadyPickupDetail({ id }: { id: number }) {
  const [item, setItem] = useState<ReadyPickupReservation | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [reload, setReload] = useState(0)

  useEffect(() => {
    let active = true
    pickupService.detail(id)
      .then((data) => { if (active) setItem(data) })
      .catch((e: unknown) => {
        if (active) setError(getApiErrorMessage(e, 'Không tải được chi tiết đơn đặt giữ. Vui lòng thử lại.'))
      })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [id, reload])

  return (
    <div>
      <Link to="/reservations/ready-for-pickup"
        className="mb-4 inline-block text-sm font-medium text-blue-600 hover:underline">
        ← Sách đang chờ nhận
      </Link>
      <PageHeader title="Chi tiết đơn đặt giữ" description="Thông tin đơn đặt giữ đang chờ bạn đọc đến nhận sách." />
      {loading && <div role="status"><LoadingState /></div>}
      {error && <div role="alert" className="rounded-lg bg-red-50 p-4 text-red-700">
        <p>{error}</p>
        <Button type="button" variant="secondary" className="mt-3"
          onClick={() => {
            setItem(null)
            setError('')
            setLoading(true)
            setReload((value) => value + 1)
          }}>Thử lại</Button>
      </div>}
      {!loading && !error && item && item.id === id && <Card className="p-6">
        <div className="flex flex-wrap items-center gap-3">
          <h3 className="text-xl font-semibold text-slate-900">Đơn #{item.id}</h3>
          <span className="rounded-full border border-amber-200 bg-amber-50 px-3 py-1 text-xs font-semibold text-amber-800">
            Đang chờ nhận
          </span>
        </div>
        <dl className="mt-6 grid gap-5 text-sm sm:grid-cols-2">
          <div>
            <dt className="text-slate-500">Tên đầu sách</dt>
            <dd className="mt-1 font-medium"><Link to={`/books/${item.bookId}`}
              className="text-blue-700 hover:underline">{item.bookTitle}</Link></dd>
          </div>
          <div>
            <dt className="text-slate-500">Mã vạch bản sao đang được giữ</dt>
            <dd className="mt-1 break-all font-mono font-semibold text-slate-900">
              {item.barcode || 'Chưa có bản sao được gán'}
            </dd>
          </div>
          <div>
            <dt className="text-slate-500">Bạn đọc được giữ sách</dt>
            <dd className="mt-1 font-medium text-slate-900">{item.readerName}</dd>
          </div>
          <div>
            <dt className="text-slate-500">Hạn cuối đến nhận (giờ Việt Nam)</dt>
            <dd className="mt-1 font-semibold text-slate-900">{formatPickupDate(item.pickupDeadline)}</dd>
          </div>
          <div>
            <dt className="text-slate-500">Thời điểm đặt giữ (giờ Việt Nam)</dt>
            <dd className="mt-1 font-medium text-slate-900">{formatPickupDate(item.reservedAt)}</dd>
          </div>
        </dl>
        {(!item.barcode || !item.pickupDeadline) && <p role="status"
          className="mt-5 rounded-lg bg-amber-50 p-3 text-sm text-amber-800">
          Đơn cũ chưa có đủ thông tin bản sao hoặc hạn nhận. Cần đối chiếu dữ liệu trước khi đưa sách lên giá chờ nhận.
        </p>}
      </Card>}
    </div>
  )
}
