import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import Button from '../../components/ui/Button'
import Card from '../../components/ui/Card'
import EmptyState from '../../components/ui/EmptyState'
import LoadingState from '../../components/ui/LoadingState'
import PageHeader from '../../components/ui/PageHeader'
import StatusBadge from '../../components/ui/StatusBadge'
import { getCurrentUser } from '../../core/auth/authStorage'
import { getApiErrorMessage } from '../s1-02-user-management/accountService'
import { reservationService } from '../s2-07-reservations/reservationService'
import type { MyBookReservation } from '../s2-07-reservations/reservationService'

const statusLabels: Record<string, string> = {
  PENDING: 'Đang xếp hàng',
  READY_FOR_PICKUP: 'Đang chờ nhận',
  FULFILLED: 'Đã chuyển thành phiếu mượn',
  CANCELLED: 'Đã huỷ',
  EXPIRED: 'Hết hạn',
}

const dateFormatter = new Intl.DateTimeFormat('vi-VN', {
  timeZone: 'Asia/Ho_Chi_Minh', day: '2-digit', month: '2-digit', year: 'numeric',
  hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23',
})

function formatDate(value: string) {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? 'Không xác định' : dateFormatter.format(date)
}

export default function MyReservationsPage() {
  const allowed = getCurrentUser()?.role === 'READER'
  const [items, setItems] = useState<MyBookReservation[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [confirming, setConfirming] = useState<MyBookReservation | null>(null)
  const [cancelling, setCancelling] = useState(false)
  const [actionError, setActionError] = useState('')
  const [success, setSuccess] = useState('')
  const cancellingRef = useRef(false)
  const refreshRef = useRef<() => void>(() => {})

  useEffect(() => {
    let active = true
    let pending = false
    if (!allowed) return

    async function refresh() {
      if (!active || pending || cancellingRef.current) return
      pending = true
      setLoading(true)
      setError('')
      try {
        const data = await reservationService.listMine()
        if (active) setItems(data)
      } catch (e) {
        if (active) {
          setItems([])
          setError(getApiErrorMessage(e, 'Không tải được đơn đặt giữ. Vui lòng thử lại.'))
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
    window.addEventListener('focus', refreshWhenVisible)
    document.addEventListener('visibilitychange', refreshWhenVisible)
    return () => {
      active = false
      window.removeEventListener('focus', refreshWhenVisible)
      document.removeEventListener('visibilitychange', refreshWhenVisible)
      refreshRef.current = () => {}
    }
  }, [allowed])

  async function cancelConfirmed() {
    if (!confirming || cancellingRef.current || loading) return
    cancellingRef.current = true
    setCancelling(true)
    setActionError('')
    setSuccess('')
    try {
      await reservationService.cancelMine(confirming.id)
      setItems((current) => current.map((item) => item.id === confirming.id
        ? { ...item, status: 'CANCELLED', queuePosition: null, pickupDeadline: null } : item))
      setSuccess(confirming.status === 'READY_FOR_PICKUP'
        ? `Đã huỷ đơn #${confirming.id}. Bản sao đang giữ đã được giải phóng và hệ thống đã xử lý người kế tiếp trong hàng đợi.`
        : `Đã huỷ đơn #${confirming.id}. Đơn không còn giữ vị trí trong hàng đợi.`)
    } catch (e) {
      setActionError(getApiErrorMessage(e, 'Không xác nhận được kết quả huỷ. Hãy kiểm tra danh sách trước khi thử lại.'))
    } finally {
      cancellingRef.current = false
      setCancelling(false)
      setConfirming(null)
      // Re-fetch positions for all of this reader's orders, also after a stale-status conflict.
      refreshRef.current()
    }
  }

  if (!allowed) return <p role="alert">Chỉ Bạn đọc mới được xem danh sách đơn đặt giữ cá nhân.</p>

  const activeCount = items.filter((item) => ['PENDING', 'READY_FOR_PICKUP'].includes(item.status)).length

  return (
    <div>
      <PageHeader title="Đơn đặt giữ của tôi"
        description="Đơn chờ nhận hiển thị trước, tiếp đến đơn đang xếp hàng và lịch sử đặt giữ."
        action={<Button type="button" variant="secondary" loading={loading} disabled={cancelling}
          onClick={() => refreshRef.current()}>Làm mới</Button>} />
      <p className="mb-4 text-sm text-slate-500">
        Ngày giờ theo Việt Nam (UTC+7). Vị trí được tính trong hàng đợi của từng đầu sách.
        {' '}Nhấn Làm mới hoặc quay lại cửa sổ để cập nhật.
      </p>
      {success && <div role="status" className="mb-4 rounded-lg bg-green-50 p-4 text-green-800">{success}</div>}
      {actionError && <div role="alert" className="mb-4 rounded-lg bg-red-50 p-4 text-red-700">{actionError}</div>}
      {confirming && <Card className="mb-4 border border-red-200 p-5">
        <h2 className="font-semibold text-slate-900">Xác nhận huỷ đặt giữ</h2>
        <p className="my-3 text-sm text-slate-700">
          Bạn muốn huỷ đơn #{confirming.id} — {confirming.bookTitle}?
          {confirming.status === 'READY_FOR_PICKUP'
            ? ' Bản sao đang giữ sẽ được chuyển cho người tiếp theo nếu còn hàng đợi; nếu không, sách sẽ trở về trạng thái Sẵn sàng.'
            : ' Đơn sẽ mất vị trí trong hàng đợi. Nếu đặt lại, bạn sẽ xếp hàng lại từ đầu.'}
        </p>
        <div className="flex flex-wrap gap-3">
          <Button type="button" variant="secondary" disabled={cancelling}
            onClick={() => setConfirming(null)}>Giữ lại đơn</Button>
          <Button type="button" variant="danger" loading={cancelling} disabled={loading}
            onClick={() => void cancelConfirmed()}>Xác nhận huỷ</Button>
        </div>
      </Card>}
      {error && <div role="alert" className="mb-4 rounded-lg bg-red-50 p-4 text-red-700">
        {error} Nhấn “Làm mới” để thử lại.
      </div>}
      {loading && <div role="status"><LoadingState /></div>}
      {!loading && !error && items.length === 0 && <>
        <EmptyState title="Bạn chưa có đơn đặt giữ nào"
          description="Hãy tra cứu đầu sách và đặt giữ sách bạn muốn mượn." />
        <Link to="/catalog" className="mt-4 inline-block font-medium text-blue-700 hover:underline">Tra cứu sách</Link>
      </>}
      {!loading && !error && items.length > 0 && <Card className="overflow-hidden">
        <p className="border-b border-slate-200 px-5 py-4 text-sm text-slate-700" role="status">
          Có <strong>{activeCount}</strong> đơn đang hiệu lực / {items.length} đơn đặt giữ.
        </p>
        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-slate-200 text-sm">
            <caption className="sr-only">Danh sách đơn đặt giữ của bạn, ưu tiên đơn đang hiệu lực</caption>
            <thead className="bg-slate-50 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
              <tr>
                {['Đầu sách', 'Thời điểm đặt', 'Trạng thái', 'Vị trí hàng đợi', 'Hạn cuối đến nhận', 'Thao tác'].map((label) =>
                  <th key={label} scope="col" className="px-5 py-3">{label}</th>)}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 bg-white">
              {items.map((item) => {
                const statusLabel = statusLabels[item.status] ?? item.status
                return <tr key={item.id} className="align-top hover:bg-slate-50">
                  <td className="px-5 py-4">
                    <Link to={`/catalog/books/${item.bookId}`} className="font-semibold text-blue-700 hover:underline">
                      {item.bookTitle}
                    </Link>
                    <p className="mt-1 text-xs text-slate-500">Đơn #{item.id}</p>
                  </td>
                  <td className="whitespace-nowrap px-5 py-4 text-slate-700">
                    <time dateTime={item.reservedAt}>{formatDate(item.reservedAt)}</time>
                  </td>
                  <td className="px-5 py-4">
                    <StatusBadge status={item.status} label={statusLabel} />
                  </td>
                  <td className="px-5 py-4 font-semibold text-slate-900">
                    {item.status === 'PENDING' ? (item.queuePosition ?? 'Chưa xác định') : '—'}
                  </td>
                  <td className="whitespace-nowrap px-5 py-4 font-medium text-slate-900">
                    {item.status === 'READY_FOR_PICKUP' && item.pickupDeadline
                      ? <time dateTime={item.pickupDeadline}>{formatDate(item.pickupDeadline)}</time>
                      : '—'}
                  </td>
                  <td className="px-5 py-4">
                    {item.status === 'FULFILLED'
                      ? <span className="text-xs font-medium text-slate-500">Không thể huỷ: sách đã được nhận.</span>
                      : ['PENDING', 'READY_FOR_PICKUP'].includes(item.status) ? <Button type="button" variant="danger" size="sm"
                        disabled={cancelling || confirming !== null}
                        onClick={() => { setConfirming(item); setActionError(''); setSuccess('') }}>
                        Huỷ đặt giữ
                      </Button> : '—'}
                  </td>
                </tr>
              })}
            </tbody>
          </table>
        </div>
      </Card>}
    </div>
  )
}
