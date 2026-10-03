import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import Button from '../../components/ui/Button'
import CancelReservationPanel, { CancellationNotice } from './CancelReservationPanel'
import Card from '../../components/ui/Card'
import EmptyState from '../../components/ui/EmptyState'
import LoadingState from '../../components/ui/LoadingState'
import PageHeader from '../../components/ui/PageHeader'
import { getCurrentUser } from '../../core/auth/authStorage'
import { getApiErrorMessage } from '../s1-02-user-management/accountService'
import { formatPickupDate, pickupRoles, pickupService } from './pickupService'
import type { ReadyPickupReservation, CancelReservationResult } from './pickupService'

export default function ReadyPickupPage() {
  const allowed = pickupRoles.includes(getCurrentUser()?.role ?? '')
  const [items, setItems] = useState<ReadyPickupReservation[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [cancelTarget, setCancelTarget] = useState<ReadyPickupReservation | null>(null)
  const [cancellation, setCancellation] = useState<CancelReservationResult | null>(null)
  const [revision, setRevision] = useState(0)
  const panelOpenRef = useRef(false)
  const refreshRef = useRef<() => void>(() => {})

  useEffect(() => {
    let active = true
    let pending = false
    if (!allowed) return

    async function refresh() {
      if (!active || pending || panelOpenRef.current) return
      pending = true
      setLoading(true)
      setError('')
      try {
        const data = await pickupService.list()
        if (active) setItems(data)
      } catch (e) {
        if (active) {
          // Do not present a stale table as a current list after a failed refresh.
          setItems([])
          setError(getApiErrorMessage(e, 'Không tải được danh sách sách đang chờ nhận. Vui lòng thử lại.'))
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
  }, [allowed, revision])

  function closeCancellation(result?: CancelReservationResult) {
    panelOpenRef.current = false
    setCancelTarget(null)
    if (result) setCancellation(result)
    setItems([])
    setError('')
    setLoading(true)
    setRevision((value) => value + 1)
  }

  if (!allowed) return <p role="alert">Bạn không có quyền xem danh sách sách đang chờ nhận.</p>

  return (
    <div>
      <PageHeader
        title="Sách đang chờ nhận"
        description="Các đơn Đang chờ nhận, sắp xếp theo hạn nhận gần nhất trước."
        action={<Button type="button" variant="secondary" loading={loading} disabled={!!cancelTarget}
          onClick={() => refreshRef.current()}>Làm mới</Button>}
      />
      <p className="mb-4 text-sm text-slate-500">
        Đối chiếu mã vạch và tên bạn đọc để đưa đúng bản sách lên giá chờ nhận.
        {' '}Giờ hiển thị theo Việt Nam. Tự cập nhật mỗi 10 giây khi đang xem trang.
      </p>
      {cancellation && <CancellationNotice result={cancellation} />}
      {cancelTarget && <CancelReservationPanel key={cancelTarget.id} reservation={cancelTarget}
        onDismiss={() => closeCancellation()} onSuccess={(result) => closeCancellation(result)} />}
      {error && <div role="alert" className="mb-4 rounded-lg bg-red-50 p-4 text-red-700">
        {error} Nhấn “Làm mới” để thử lại.
      </div>}
      {loading && <div role="status"><LoadingState /></div>}
      {!loading && !error && items.length === 0 && <EmptyState
        title="Không có sách đang chờ nhận"
        description="Hiện chưa có đơn đặt giữ nào ở trạng thái Đang chờ nhận."
      />}
      {!loading && !error && items.length > 0 && <Card className="overflow-hidden">
        <div className="border-b border-slate-200 px-5 py-4 text-sm text-slate-700" role="status">
          Có <strong>{items.length}</strong> đơn đang chờ nhận.
        </div>
        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-slate-200 text-sm">
            <caption className="sr-only">Sách đang chờ nhận theo hạn nhận tăng dần</caption>
            <thead className="bg-slate-50 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
              <tr>
                <th scope="col" className="px-5 py-3">Đầu sách</th>
                <th scope="col" className="px-5 py-3">Mã vạch bản sao</th>
                <th scope="col" className="px-5 py-3">Bạn đọc</th>
                <th scope="col" className="px-5 py-3">Hạn cuối đến nhận</th>
                <th scope="col" className="px-5 py-3">Đơn đặt giữ</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 bg-white">
              {items.map((item) => <tr key={item.id} className="align-top hover:bg-slate-50">
                <td className="px-5 py-4">
                  <Link to={`/books/${item.bookId}`} className="font-semibold text-blue-700 hover:underline">
                    {item.bookTitle}
                  </Link>
                </td>
                <td className="break-all px-5 py-4 font-mono font-semibold text-slate-900">
                  {item.barcode || 'Chưa có bản sao được gán'}
                </td>
                <td className="px-5 py-4 text-slate-700">{item.readerName}</td>
                <td className="whitespace-nowrap px-5 py-4 font-medium text-slate-900">
                  {formatPickupDate(item.pickupDeadline)}
                </td>
                <td className="px-5 py-4">
                  <Link to={`/reservations/ready-for-pickup/${item.id}`}
                    className="whitespace-nowrap font-semibold text-blue-600 hover:underline">
                    Chi tiết đơn #{item.id}
                  </Link>
                  <Button type="button" variant="danger" size="sm" className="mt-3" disabled={!!cancelTarget}
                    aria-label={`Huỷ đơn #${item.id}`}
                    onClick={() => { panelOpenRef.current = true; setCancelTarget(item); setCancellation(null) }}>
                    Huỷ đơn
                  </Button>
                </td>
              </tr>)}
            </tbody>
          </table>
        </div>
      </Card>}
    </div>
  )
}
