import { useEffect, useState, type FormEvent } from 'react'
import { Receipt, Search, Wallet } from 'lucide-react'
import Button from '../../components/ui/Button'
import Card from '../../components/ui/Card'
import EmptyState from '../../components/ui/EmptyState'
import LoadingState from '../../components/ui/LoadingState'
import TablePagination from '../../components/ui/TablePagination'
import { feePaymentService, feeTypeLabel, type FeeFilters, type FeeList } from './feePaymentService'
import { getApiErrorMessage } from '../s1-02-user-management/accountService'
import { formatVnd } from '../s3-07-returns/ReturnFeeDetails'
import { formatLoanTimestamp } from '../s3-01-loans/loanService'

const empty: FeeFilters = { query: '', status: '', type: '', from: '', to: '' }
const field = 'mt-1 min-h-11 w-full min-w-0 rounded-xl border border-slate-300 bg-white px-3 text-sm'
export default function FeePaymentList({ disabled, revision, onSelect, onReceipt }: {
  disabled: boolean; revision: number; onSelect: (readerId: number) => void; onReceipt: (id: number) => void
}) {
  const [draft, setDraft] = useState(empty), [filters, setFilters] = useState(empty)
  const [page, setPage] = useState(0), [data, setData] = useState<FeeList | null>(null)
  const [loading, setLoading] = useState(true), [error, setError] = useState('')
  useEffect(() => {
    const controller = new AbortController()
    setLoading(true); setError('')
    feePaymentService.list(filters, page, controller.signal).then(result => {
      if (controller.signal.aborted) return
      if (page > 0 && page >= Math.max(1, Math.ceil(result.totalItems / 10))) { setPage(Math.max(0, Math.ceil(result.totalItems / 10) - 1)); return }
      setData(result)
    }).catch(e => { if (!controller.signal.aborted) { setData(null); setError(getApiErrorMessage(e, 'Không tải được danh sách khoản phí.')) } })
      .finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [filters, page, revision])
  function apply(event: FormEvent) { event.preventDefault(); if (disabled) return; setFilters({ ...draft, query: draft.query.trim() }); setPage(0) }
  function filter(name: keyof FeeFilters, value: string) {
    const next = { ...draft, [name]: value }; setDraft(next)
    if (name !== 'query') { setFilters({ ...next, query: next.query.trim() }); setPage(0) }
  }
  return <section className="min-w-0 space-y-4" aria-label="Danh sách khoản phí">
    <Card className="p-4 sm:p-5">
      <form onSubmit={apply} className="space-y-4">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-end">
          <label className="min-w-0 flex-1 text-sm font-medium text-slate-700">Tìm khoản phí và bạn đọc
            <input className={field} value={draft.query} maxLength={255} disabled={disabled} onChange={e => filter('query', e.target.value)} placeholder="Mã thẻ, mã bạn đọc, tên, email, điện thoại, phiếu mượn" /></label>
          <Button type="submit" disabled={disabled || loading}><Search size={16} />Tìm kiếm</Button>
          <Button type="button" variant="secondary" disabled={disabled || loading} onClick={() => { setDraft(empty); setFilters({ ...empty }); setPage(0) }}>Tất cả</Button>
        </div>
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <label className="text-sm text-slate-700">Thanh toán<select className={field} disabled={disabled} value={draft.status} onChange={e => filter('status', e.target.value)}><option value="">Tất cả</option><option value="UNPAID">Chưa thanh toán</option><option value="PARTIAL">Thanh toán một phần</option><option value="PAID">Đã thanh toán đủ / miễn giảm đủ</option></select></label>
          <label className="text-sm text-slate-700">Loại phí<select className={field} disabled={disabled} value={draft.type} onChange={e => filter('type', e.target.value)}><option value="">Tất cả loại phí</option><option value="OVERDUE">Phí trễ hạn</option><option value="COMPENSATION">Bồi thường sách</option><option value="RECORDED">Khoản phí đã ghi nhận</option></select></label>
          <label className="text-sm text-slate-700">Phát sinh từ ngày<input type="date" className={field} disabled={disabled} value={draft.from} max={draft.to || undefined} onChange={e => filter('from', e.target.value)} /></label>
          <label className="text-sm text-slate-700">Đến ngày<input type="date" className={field} disabled={disabled} value={draft.to} min={draft.from || undefined} onChange={e => filter('to', e.target.value)} /></label>
        </div>
      </form>
    </Card>
    {loading ? <LoadingState /> : error ? <Card className="p-5"><p role="alert" className="text-sm text-red-700">{error}</p><Button variant="secondary" className="mt-3" onClick={() => setFilters({ ...filters })}>Thử lại</Button></Card>
      : !data?.items.length ? <Card className="p-5"><EmptyState title="Không có khoản phí phù hợp" description="Thử thay đổi bộ lọc hoặc chọn Tất cả. Bạn đọc không có phí sẽ không phát sinh khoản nợ mới." /></Card>
        : <div className="grid gap-4 md:grid-cols-2">{data.items.map(item => <Card key={item.id} className="min-w-0 space-y-4 p-4 sm:p-5">
          <div className="flex flex-wrap items-start justify-between gap-2"><h3 className="min-w-0 break-words font-semibold text-slate-900">{item.reader.fullName}</h3>
            <span className={'rounded-lg px-2 py-1 text-xs font-semibold ' + (item.paymentStatus === 'PAID' ? 'bg-emerald-50 text-emerald-800' : item.paymentStatus === 'PARTIAL' ? 'bg-blue-50 text-blue-800' : 'bg-amber-50 text-amber-900')}>{item.paymentStatus === 'PAID' ? 'Đã hoàn tất' : item.paymentStatus === 'PARTIAL' ? 'Thu một phần' : 'Chưa thanh toán'}</span></div>
          <div className="space-y-1 break-words text-sm text-slate-600"><p>Thẻ: {item.reader.cardNumber ?? 'Chưa cấp'} · {item.reader.memberCode ?? `Bạn đọc #${item.reader.id}`}</p><p className="break-all">{item.reader.email} {item.reader.phone ? `· ${item.reader.phone}` : ''}</p><p>KP-{item.id} · {feeTypeLabel(item.feeType)} {item.loanNumber ? `· ${item.loanNumber}` : ''}</p><p>{item.description}</p></div>
          <dl className="grid grid-cols-1 gap-3 rounded-xl bg-slate-50 p-3 text-sm sm:grid-cols-3">
            <div><dt className="text-slate-500">Phải thu</dt><dd className="font-semibold">{formatVnd(item.amountVnd - item.waivedAmountVnd)}</dd></div>
            <div><dt className="text-slate-500">Đã thu</dt><dd className="font-semibold text-emerald-700">{formatVnd(item.paidAmountVnd)}</dd></div>
            <div><dt className="text-slate-500">Còn nợ</dt><dd className="font-semibold text-amber-800">{formatVnd(item.outstandingAmountVnd)}</dd></div>
          </dl>
          {item.waivedAmountVnd > 0 && <p className="text-xs text-slate-500">Miễn giảm: {formatVnd(item.waivedAmountVnd)}</p>}
          <p className="text-xs text-slate-500">Phát sinh: {formatLoanTimestamp(item.createdAt)}</p>
          <div className="flex flex-wrap gap-2"><Button variant="secondary" disabled={disabled} onClick={() => onSelect(item.reader.id)}>Chi tiết</Button>
            {item.outstandingAmountVnd > 0 && <Button disabled={disabled} onClick={() => onSelect(item.reader.id)}><Wallet size={16} />Thu phí</Button>}
            {item.latestPaymentId && <Button variant="secondary" disabled={disabled} onClick={() => onReceipt(item.latestPaymentId!)}><Receipt size={16} />Biên lai gần nhất</Button>}
          </div>
        </Card>)}</div>}
    {!loading && data && <TablePagination page={page + 1} totalItems={data.totalItems} totalPages={Math.max(1, Math.ceil(data.totalItems / 10))} pageSize={10} onPageChange={value => { if (!disabled) setPage(value - 1) }} />}
  </section>
}
