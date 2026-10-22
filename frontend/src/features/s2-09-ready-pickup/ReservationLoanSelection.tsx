import { useEffect, useRef, useState } from 'react'
import Button from '../../components/ui/Button'
import FeedbackAlert from '../../components/ui/FeedbackAlert'
import { getApiErrorMessage } from '../s1-02-user-management/accountService'
import { formatPickupDate, pickupService } from './pickupService'
import type { ReadyPickupReservation } from './pickupService'

interface Props {
  reservation: ReadyPickupReservation
  disabled: boolean
  selectedIds: number[]
  onChange: (ids: number[]) => void
  onReady: (ready: boolean) => void
  onBooks?: (rows: ReadyPickupReservation[]) => void
}

export default function ReservationLoanSelection({ reservation, disabled, selectedIds, onChange, onReady, onBooks }: Props) {
  const [items, setItems] = useState<ReadyPickupReservation[]>([])
  const [loading, setLoading] = useState(true)
  const [failed, setFailed] = useState(false)
  const [error, setError] = useState('')
  const [revision, setRevision] = useState(0)
  const callbacks = useRef({ onChange, onReady })
  callbacks.current = { onChange, onReady }
  const booksCallback = useRef(onBooks)
  booksCallback.current = onBooks
  useEffect(() => { booksCallback.current?.(items.filter(row => selectedIds.includes(row.id))) }, [items, selectedIds])

  useEffect(() => {
    let active = true
    setLoading(true); setFailed(false); setError('')
    callbacks.current.onReady(false)
    pickupService.list().then(rows => {
      if (!active) return
      const sameReader = rows.filter(row => row.readerId === reservation.readerId)
        .sort((a, b) => a.id - b.id)
      setItems(sameReader)
      const eligible = sameReader.filter(row => row.copyId && row.barcode && row.pickupDeadline
        && (!reservation.checkedAt || Date.parse(row.pickupDeadline) >= Date.parse(reservation.checkedAt)))
      if (!eligible.some(row => row.id === reservation.id) || eligible.length > 3) {
        setFailed(true)
        setError('Danh sách đơn đã thay đổi hoặc không hợp lệ. Vui lòng cập nhật lại trước khi nhận sách.')
        return
      }
      // All ready books of this reader are included by default; staff can exclude uncollected books.
      callbacks.current.onChange(eligible.map(row => row.id))
      callbacks.current.onReady(true)
    }).catch((e: unknown) => {
      if (!active) return
      setFailed(true)
      setError(getApiErrorMessage(e, 'Không tải được các sách đang chờ nhận của bạn đọc.'))
    }).finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [reservation.id, reservation.readerId, revision])

  return <section className="min-w-0 space-y-3 rounded-xl border border-slate-200 bg-slate-50 p-4" aria-labelledby="reservation-selection-heading">
    <div className="flex flex-wrap items-start justify-between gap-2">
      <h5 id="reservation-selection-heading" className="min-w-0 flex-1 font-semibold text-slate-900">Các sách nhận trong cùng một phiếu mượn</h5>
      <span className="shrink-0 rounded-full bg-blue-50 px-2.5 py-1 text-xs font-semibold text-blue-700">{selectedIds.length} cuốn</span>
    </div>
    <p className="text-sm leading-6 text-slate-600">Chỉ gồm đơn Chờ nhận của {reservation.readerName}. Bỏ chọn sách bạn đọc chưa đến nhận; đơn đang xếp hàng sẽ giữ nguyên.</p>
    {loading && <p role="status" className="text-sm text-slate-600">Đang tải các sách chờ nhận…</p>}
    {error && <FeedbackAlert tone="error" message={error} onDismiss={() => setError('')} />}
    {failed && <div className="space-y-2 text-sm text-slate-600">
      <p>Chưa xác định được danh sách nhận sách. Cần cập nhật trước khi lập phiếu.</p>
    </div>}
    {!loading && !failed && <ul className="space-y-2">
      {items.map(row => {
        const selectable = !!row.copyId && !!row.barcode && !!row.pickupDeadline
          && (!reservation.checkedAt || Date.parse(row.pickupDeadline) >= Date.parse(reservation.checkedAt))
        return <li key={row.id} className="rounded-lg border border-slate-200 bg-white p-3">
          <label className="flex min-w-0 items-start gap-3 text-sm">
            <input type="checkbox" className="mt-1 shrink-0" checked={selectedIds.includes(row.id)}
              disabled={disabled || !selectable || row.id === reservation.id}
              aria-label={`Nhận đơn #${row.id}: ${row.bookTitle}`}
              onChange={event => callbacks.current.onChange(event.target.checked
                ? [...selectedIds, row.id].sort((a, b) => a - b) : selectedIds.filter(id => id !== row.id))} />
            <span className="min-w-0 break-words leading-6">
              <span className="block font-semibold text-slate-900">{row.bookTitle} · Đơn #{row.id}</span>
              <span className="block break-all text-slate-700">Mã vạch: {row.barcode || 'Chưa được gán'}</span>
              <span className="block text-slate-600">Hạn nhận: {formatPickupDate(row.pickupDeadline)}</span>
              {!selectable && <span className="block text-red-700">Không đủ dữ liệu hoặc đã quá hạn nhận.</span>}
            </span>
          </label>
        </li>
      })}
    </ul>}
    <Button type="button" variant="secondary" size="sm" disabled={disabled || loading} onClick={() => setRevision(v => v + 1)}>Cập nhật danh sách</Button>
    <p className="text-sm font-semibold text-slate-900">Một phiếu mượn · {selectedIds.length} cuốn được chọn</p>
  </section>
}
