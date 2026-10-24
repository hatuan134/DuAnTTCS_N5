import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import Card from '../../components/ui/Card'
import { getApiErrorMessage } from '../s1-02-user-management/accountService'
import { formatVnd } from '../s3-07-returns/ReturnFeeDetails'
import { depositService, type DepositAccount } from './depositService'

type Props = { readerId: number; loanId?: number; loanNumber?: string; revision?: number }

/** Read only. Fees and deposits have separate ledgers; never infer a payment from held collateral. */
export default function DepositReconciliationSummary({ readerId, loanId, loanNumber, revision }: Props) {
  const [accounts, setAccounts] = useState<DepositAccount[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  useEffect(() => {
    let active = true
    setAccounts([]); setLoading(true); setError('')
    async function load() {
      const all: DepositAccount[] = []
      // Page through server-side results; do not assume one reader has at most 10 deposits.
      for (let page = 0; page < 1000; page++) {
        const result = await depositService.list(loanNumber ?? '', page, readerId)
        if (result.items.length === 0 && result.totalItems > all.length) throw new Error('Danh sách tiền cọc phân trang không nhất quán.')
        all.push(...result.items)
        if (all.length >= result.totalItems || result.items.length === 0) return all
      }
      throw new Error('Danh sách tiền cọc quá dài; cần lọc theo phiếu mượn.')
    }
    void load().then(all => {
      if (active) setAccounts(loanId === undefined ? all : all.filter(a => a.loanId === loanId && a.loanNumber === loanNumber))
    }).catch(e => { if (active) setError(getApiErrorMessage(e, 'Không tải được dữ liệu tiền cọc; không thể kết luận số dư.')) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [readerId, loanId, loanNumber, revision])

  const total = (key: 'requiredVnd' | 'collectedVnd' | 'heldVnd' | 'refundedVnd' | 'deductedVnd') =>
    accounts.reduce((sum, account) => sum + account[key], 0)
  return <Card className="p-5 sm:p-6" aria-label="Đối soát tiền đặt cọc">
    <div className="flex flex-wrap items-start justify-between gap-3">
      <div><h3 className="text-lg font-semibold text-slate-900">Đối soát tiền đặt cọc</h3>
        <p className="mt-1 text-xs text-slate-600">Tiền cọc là khoản giữ riêng, không tự động trừ vào nợ phí.</p></div>
      <Link to="/deposits" className="rounded-xl border border-slate-300 bg-white px-3 py-2 text-sm font-medium text-blue-700 hover:bg-slate-50">Xem giao dịch cọc</Link>
    </div>
    {loading ? <p role="status" className="mt-4 text-sm text-slate-600">Đang đối chiếu dữ liệu cọc…</p>
      : error ? <p role="alert" className="mt-4 rounded-lg bg-red-50 p-3 text-sm text-red-700">{error}</p>
      : accounts.length === 0 ? <p className="mt-4 rounded-lg bg-slate-50 p-3 text-sm text-slate-700">
        Không ghi nhận khoản cọc liên kết {loanNumber ? `với phiếu ${loanNumber}` : 'với bạn đọc này'}. Phiếu mượn trước thời điểm áp dụng cọc không bị truy thu hồi tố.
      </p> : <>
        <dl className="mt-4 grid grid-cols-2 gap-4 rounded-xl bg-slate-50 p-4 text-sm sm:grid-cols-3">
          {([['Cọc yêu cầu', total('requiredVnd')], ['Đã thu', total('collectedVnd')], ['Đang giữ', total('heldVnd')],
            ['Đã hoàn', total('refundedVnd')], ['Đã khấu trừ', total('deductedVnd')]] as const).map(([label, amount]) =>
            <div key={label}><dt className="text-slate-600">{label}</dt><dd className="mt-1 font-semibold text-slate-900">{formatVnd(amount)}</dd></div>)}
        </dl>
        <div className="mt-4 space-y-3">{accounts.map(a => <div key={a.id} className="rounded-xl border border-slate-200 p-3 text-sm">
          <p className="break-words font-semibold">{a.bookTitle} · {a.barcode}</p>
          {!loanNumber && <p className="mt-1 text-xs text-slate-600">{a.loanNumber ?? 'Chưa bàn giao'}</p>}
          <p className="mt-2 text-slate-700">Đã thu {formatVnd(a.collectedVnd)} · Còn giữ <strong>{formatVnd(a.heldVnd)}</strong> · Đã hoàn {formatVnd(a.refundedVnd)} · Đã khấu trừ {formatVnd(a.deductedVnd)}</p>
          <p className="mt-1 text-xs text-slate-600">Giá bìa {formatVnd(a.coverPrice)} · Tỷ lệ {a.percentage}% · Cọc yêu cầu {formatVnd(a.requiredVnd)}</p>
          {a.itemId !== null && a.returnedAt && a.heldVnd > 0 && <p className="mt-2 rounded-lg bg-amber-50 p-2 text-sm text-amber-900">
            {a.outstandingVnd > 0 ? `Đã trả sách, còn nghĩa vụ ${formatVnd(a.outstandingVnd)}. Cần đối soát phí trước khi hoàn cọc.`
              : `Đã trả sách, còn ${formatVnd(a.heldVnd)} chờ đối soát hoàn cọc. Chỉ xác nhận hoàn khi đã thực sự giao tiền.`}
          </p>}
        </div>)}</div>
      </>}
  </Card>
}
