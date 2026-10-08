import { useEffect, useState } from 'react'
import Button from '../../components/ui/Button'
import Card from '../../components/ui/Card'
import EmptyState from '../../components/ui/EmptyState'
import FeedbackAlert from '../../components/ui/FeedbackAlert'
import LoadingState from '../../components/ui/LoadingState'
import TablePagination from '../../components/ui/TablePagination'
import { getCurrentUser } from '../../core/auth/authStorage'
import { getApiErrorMessage } from '../s1-02-user-management/accountService'
import { formatLoanTimestamp } from '../s3-01-loans/loanService'
import { myBorrowedBooksService } from './myBorrowedBooksService'
import type { MyReturnedBooksPage } from './myBorrowedBooksService'

export default function MyReturnedBooksPanel() {
  const user = getCurrentUser()
  const allowed = user?.role === 'READER'
  const [page, setPage] = useState(0)
  const [revision, setRevision] = useState(0)
  const [data, setData] = useState<MyReturnedBooksPage | null>(null)
  const [dataReaderId, setDataReaderId] = useState<number | undefined>(undefined)
  const [loading, setLoading] = useState(true)
  const [failed, setFailed] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!allowed) return
    let active = true
    setLoading(true); setFailed(false); setError(''); setData(null)
    myBorrowedBooksService.history(page)
      .then((response) => {
        if (!active) return
        // If the dataset shrank while viewing a later page, load its last valid page.
        const lastPage = Math.max(0, Math.ceil(response.total / response.size) - 1)
        if (page > lastPage) { setPage(lastPage); return }
        setDataReaderId(user?.id); setData(response)
      })
      .catch((e: unknown) => {
        if (active) {
          setFailed(true)
          setError(getApiErrorMessage(e, 'Không tải được lịch sử đã trả. Vui lòng thử lại.'))
        }
      })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [allowed, user?.id, page, revision])

  if (!allowed) return <p role="alert" className="rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700">Lịch sử đã trả dành cho Bạn đọc.</p>
  const current = dataReaderId === user?.id && data?.page === page ? data : null
  const totalPages = current ? Math.ceil(current.total / current.size) : 0
  return <div className="space-y-4">
    <div className="flex flex-wrap items-center justify-between gap-3">
      <div>
        <h3 className="text-lg font-semibold text-slate-900">Lịch sử đã trả</h3>
        <p className="text-sm text-slate-500">Sắp xếp theo thời điểm trả gần nhất. Mỗi trang tối đa 20 dòng, ngày giờ theo Việt Nam.</p>
      </div>
      <Button type="button" variant="secondary" loading={loading} onClick={() => setRevision((value) => value + 1)}>Làm mới lịch sử</Button>
    </div>
    {error && <FeedbackAlert message={error} tone="error" onDismiss={() => setError('')} />}
    {loading && <LoadingState />}
    {!loading && failed && <EmptyState title="Chưa tải được lịch sử" description="Nhấn Làm mới lịch sử để thử lại." />}
    {!loading && !failed && current?.total === 0 && <EmptyState title="Bạn chưa có lịch sử trả sách" description="Những bản sách đã được ghi nhận trả sẽ xuất hiện ở đây." />}
    {!loading && !failed && current && current.items.length > 0 && <Card className="overflow-hidden">
      <div className="divide-y divide-slate-200 md:hidden">
        {current.items.map((item) => <article key={item.id} className="space-y-3 p-4">
          <h4 className="break-words font-semibold text-slate-900">{item.bookTitle}</h4>
          <dl className="grid gap-3 text-sm sm:grid-cols-2">
            <div><dt className="text-slate-500">Mã vạch bản sao</dt><dd className="break-all font-mono">{item.barcode}</dd></div>
            <div><dt className="text-slate-500">Mã phiếu mượn</dt><dd className="break-all font-mono">{item.loanNumber}</dd></div>
            <div><dt className="text-slate-500">Ngày mượn</dt><dd>{formatLoanTimestamp(item.borrowedAt, true)}</dd></div>
            <div><dt className="text-slate-500">Thời điểm trả</dt><dd>{formatLoanTimestamp(item.returnedAt)}</dd></div>
          </dl>
        </article>)}
      </div>
      <div className="hidden md:block">
        <table className="data-table w-full table-fixed text-sm">
          <caption className="sr-only">Các giao dịch trả sách của bạn, thời điểm trả gần nhất trước</caption>
          <thead className="bg-slate-50 text-left text-xs font-semibold uppercase text-slate-500"><tr>
            <th scope="col" className="w-1/4 px-4 py-3">Tên sách</th><th scope="col" className="px-4 py-3">Mã vạch bản sao</th>
            <th scope="col" className="px-4 py-3">Mã phiếu mượn</th><th scope="col" className="px-4 py-3">Ngày mượn</th><th scope="col" className="px-4 py-3">Thời điểm trả</th>
          </tr></thead>
          <tbody className="divide-y divide-slate-100">{current.items.map((item) => <tr key={item.id} className="hover:bg-slate-50">
            <td className="break-words px-4 py-4 font-medium">{item.bookTitle}</td>
            <td className="break-all px-4 py-4 font-mono">{item.barcode}</td>
            <td className="break-all px-4 py-4 font-mono">{item.loanNumber}</td>
            <td className="px-4 py-4">{formatLoanTimestamp(item.borrowedAt, true)}</td>
            <td className="px-4 py-4">{formatLoanTimestamp(item.returnedAt)}</td>
          </tr>)}</tbody>
        </table>
      </div>
      {current.total > 20 && <TablePagination page={page + 1} pageSize={20} totalItems={current.total} totalPages={totalPages}
        onPageChange={(next) => {
          if (!loading && next >= 1 && next <= totalPages) setPage(next - 1)
        }} />}
    </Card>}
  </div>
}
