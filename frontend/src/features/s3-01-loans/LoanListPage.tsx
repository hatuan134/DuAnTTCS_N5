import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import Button from '../../components/ui/Button'
import Card from '../../components/ui/Card'
import EmptyState from '../../components/ui/EmptyState'
import LoadingState from '../../components/ui/LoadingState'
import PageHeader from '../../components/ui/PageHeader'
import TablePagination from '../../components/ui/TablePagination'
import { tableActionClassName } from '../../components/ui/TableActionButton'
import useTablePagination from '../../hooks/useTablePagination'
import { getCurrentUser } from '../../core/auth/authStorage'
import { getApiErrorMessage } from '../s1-02-user-management/accountService'
import { formatLoanTimestamp, loanRoles, loanService } from './loanService'
import type { LoanSummary } from './loanService'

export default function LoanListPage() {
  const allowed = loanRoles.includes(getCurrentUser()?.role ?? '')
  const [items, setItems] = useState<LoanSummary[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [revision, setRevision] = useState(0)
  useEffect(() => {
    if (!allowed) return
    let active = true
    setLoading(true); setItems([]); setError('')
    loanService.list()
      .then((data) => { if (active) setItems(data) })
      .catch((e: unknown) => { if (active) setError(getApiErrorMessage(e, 'Không tải được danh sách phiếu mượn. Vui lòng thử lại.')) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [allowed, revision])
  const pagination = useTablePagination(items, items.map((item) => item.id).join(','))
  if (!allowed) return <p role="alert" className="rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700">Bạn không có quyền xem danh sách phiếu mượn.</p>
  return <div>
    <PageHeader title="Phiếu mượn" description="Mở lại phiếu để kiểm tra bản sao, hạn trả và người lập. Phiếu mới nhất hiển thị trước."
      action={<div className="flex flex-wrap items-center gap-2">
        <Link to="/loans/search" className="inline-flex min-h-11 items-center justify-center rounded-xl border border-blue-600 bg-blue-600 px-4 text-sm font-semibold text-white hover:bg-blue-700">Tra cứu phiếu mượn</Link>
        <Button type="button" variant="secondary" loading={loading} onClick={() => setRevision((value) => value + 1)}>Làm mới</Button>
      </div>} />
    {error && <p role="alert" className="mb-4 rounded-lg bg-red-50 p-4 text-red-700">{error} Nhấn “Làm mới” để thử lại.</p>}
    {loading && <div role="status"><LoadingState /></div>}
    {!loading && !error && items.length === 0 && <EmptyState title="Chưa có phiếu mượn" description="Phiếu sẽ xuất hiện sau khi Thủ thư xác nhận nhận sách thành công." />}
    {!loading && !error && items.length > 0 && <Card className="overflow-hidden">
      <div className="divide-y divide-slate-200 md:hidden">
        {pagination.pageItems.map((item, index) => <article key={item.id} className="space-y-3 p-4">
          <p className="text-xs font-semibold text-slate-500">Phiếu {pagination.startIndex + index + 1}</p>
          <Link to={`/loans/${item.id}`} className="block break-all font-mono font-semibold text-blue-700 hover:underline">{item.loanNumber}</Link>
          <dl className="grid gap-3 text-sm sm:grid-cols-2">
            <div className="min-w-0"><dt className="text-slate-500">Bạn đọc</dt><dd className="break-words font-medium text-slate-900">{item.readerName}</dd></div>
            <div><dt className="text-slate-500">Ngày mượn</dt><dd className="font-medium text-slate-900">{formatLoanTimestamp(item.borrowedAt, true)}</dd></div>
            <div className="min-w-0"><dt className="text-slate-500">Người lập phiếu</dt><dd className="break-words font-medium text-slate-900">{item.createdByName}</dd></div>
            <div><dt className="text-slate-500">Số bản sao</dt><dd className="font-medium text-slate-900">{item.itemCount}</dd></div>
          </dl>
          <Link to={`/loans/${item.id}`} className={tableActionClassName('primary')}>Xem phiếu</Link>
        </article>)}
      </div>
      <div className="hidden overflow-x-auto md:block">
        <table className="data-table min-w-full divide-y divide-slate-200 text-sm">
          <caption className="sr-only">Danh sách phiếu mượn theo thời điểm mượn giảm dần</caption>
          <thead className="bg-slate-50 text-left text-xs font-semibold uppercase tracking-wide text-slate-500"><tr>
            <th scope="col" className="px-4 py-3">STT</th><th scope="col" className="px-4 py-3">Mã phiếu mượn</th>
            <th scope="col" className="px-4 py-3">Bạn đọc</th><th scope="col" className="px-4 py-3">Ngày mượn</th>
            <th scope="col" className="px-4 py-3">Người lập phiếu</th><th scope="col" className="px-4 py-3">Số bản sao</th>
            <th scope="col" className="px-4 py-3">Thao tác</th>
          </tr></thead>
          <tbody className="divide-y divide-slate-100 bg-white">{pagination.pageItems.map((item, index) => <tr key={item.id} className="hover:bg-slate-50">
            <td className="px-4 py-4 font-semibold text-slate-500">{pagination.startIndex + index + 1}</td>
            <td className="max-w-64 break-all px-4 py-4 font-mono font-semibold"><Link to={`/loans/${item.id}`} className="text-blue-700 hover:underline">{item.loanNumber}</Link></td>
            <td className="px-4 py-4 text-slate-700">{item.readerName}</td>
            <td className="whitespace-nowrap px-4 py-4 text-slate-700">{formatLoanTimestamp(item.borrowedAt, true)}</td>
            <td className="px-4 py-4 text-slate-700">{item.createdByName}</td>
            <td className="px-4 py-4 text-slate-700">{item.itemCount}</td>
            <td className="px-4 py-4"><Link to={`/loans/${item.id}`} className={tableActionClassName('primary')} aria-label={`Xem phiếu ${item.loanNumber}`}>Xem phiếu</Link></td>
          </tr>)}</tbody>
        </table>
      </div>
      <TablePagination page={pagination.page} totalItems={pagination.totalItems} totalPages={pagination.totalPages}
        pageSize={pagination.pageSize} onPageChange={pagination.goToPage} />
    </Card>}
  </div>
}
