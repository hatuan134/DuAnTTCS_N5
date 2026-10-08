import { useEffect, useState } from 'react'
import Button from '../../components/ui/Button'
import Card from '../../components/ui/Card'
import EmptyState from '../../components/ui/EmptyState'
import FeedbackAlert from '../../components/ui/FeedbackAlert'
import LoadingState from '../../components/ui/LoadingState'
import PageHeader from '../../components/ui/PageHeader'
import { getCurrentUser } from '../../core/auth/authStorage'
import { getApiErrorMessage } from '../s1-02-user-management/accountService'
import { formatLoanTimestamp } from '../s3-01-loans/loanService'
import { myBorrowedBooksService } from './myBorrowedBooksService'
import type { MyBorrowedBook } from './myBorrowedBooksService'
import BorrowedBookDueWarning from './BorrowedBookDueWarning'

export default function MyBorrowedBooksPage() {
  const allowed = getCurrentUser()?.role === 'READER'
  const [items, setItems] = useState<MyBorrowedBook[]>([])
  const [loading, setLoading] = useState(true)
  const [failed, setFailed] = useState(false)
  const [error, setError] = useState('')
  const [revision, setRevision] = useState(0)

  useEffect(() => {
    if (!allowed) return
    let active = true
    setLoading(true); setFailed(false); setError(''); setItems([])
    myBorrowedBooksService.list()
      .then((data) => { if (active) setItems(data) })
      .catch((e: unknown) => {
        if (active) {
          setFailed(true)
          setError(getApiErrorMessage(e, 'Không tải được sách đang mượn. Vui lòng thử lại.'))
        }
      })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [allowed, revision])

  if (!allowed) return <p role="alert" className="rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700">Chức năng Sách đang mượn dành cho Bạn đọc.</p>
  const remaining = (item: MyBorrowedBook) => item.remainingDays === null ? 'Chưa xác định' : `${item.remainingDays} ngày`
  return <div className="space-y-4">
    <PageHeader title="Sách đang mượn" description="Danh sách từng bản sao bạn đang mượn, ngày mượn và hạn trả theo giờ Việt Nam."
      action={<Button type="button" variant="secondary" loading={loading} onClick={() => setRevision((value) => value + 1)}>Làm mới</Button>} />
    {error && <FeedbackAlert message={error} tone="error" onDismiss={() => setError('')} />}
    {loading && <LoadingState />}
    {!loading && failed && <EmptyState title="Chưa tải được danh sách" description="Nhấn Làm mới để thử lại." />}
    {!loading && !failed && items.length === 0 && <EmptyState title="Bạn không có sách đang mượn" description="Sách sẽ xuất hiện ở đây sau khi Thủ thư xác nhận cho mượn." />}
    {!loading && !failed && items.length > 0 && <>
      <p className="text-sm text-slate-600">Bạn đang mượn {items.length} bản sách. Còn dưới 3 ngày được gắn nhãn Sắp đến hạn, kể cả hạn hôm nay (0 ngày). Sách đã quá hạn hiển thị số ngày trễ.</p>
      <Card className="overflow-hidden">
        <div className="divide-y divide-slate-200 md:hidden">
          {items.map((item) => <article key={item.id} className="space-y-3 p-4">
            <h3 className="break-words font-semibold text-slate-900">{item.bookTitle}</h3>
            <dl className="grid gap-3 text-sm sm:grid-cols-2">
              <div><dt className="text-slate-500">Mã vạch bản sao</dt><dd className="break-all font-mono text-slate-900">{item.barcode}</dd></div>
              <div><dt className="text-slate-500">Ngày mượn</dt><dd>{formatLoanTimestamp(item.borrowedAt, true)}</dd></div>
              <div><dt className="text-slate-500">Hạn trả</dt><dd>{item.dueAt ? formatLoanTimestamp(item.dueAt, true) : 'Chưa có hạn trả'}</dd></div>
              <div><dt className="text-slate-500">Số ngày còn lại</dt><dd className="space-y-2"><p className="font-semibold">{remaining(item)}</p><BorrowedBookDueWarning remainingDays={item.remainingDays} /></dd></div>
            </dl>
          </article>)}
        </div>
        <div className="hidden md:block">
          <table className="data-table w-full table-fixed text-sm">
            <caption className="sr-only">Danh sách đầy đủ các bản sách bạn đang mượn</caption>
            <thead className="bg-slate-50 text-left text-xs font-semibold uppercase text-slate-500"><tr>
              <th scope="col" className="w-1/3 px-4 py-3">Tên sách</th><th scope="col" className="px-4 py-3">Mã vạch bản sao</th>
              <th scope="col" className="px-4 py-3">Ngày mượn</th><th scope="col" className="px-4 py-3">Hạn trả</th><th scope="col" className="px-4 py-3">Số ngày còn lại</th>
            </tr></thead>
            <tbody className="divide-y divide-slate-100">{items.map((item) => <tr key={item.id} className="hover:bg-slate-50">
              <td className="break-words px-4 py-4 font-medium">{item.bookTitle}</td>
              <td className="break-all px-4 py-4 font-mono">{item.barcode}</td>
              <td className="px-4 py-4">{formatLoanTimestamp(item.borrowedAt, true)}</td>
              <td className="px-4 py-4">{item.dueAt ? formatLoanTimestamp(item.dueAt, true) : 'Chưa có hạn trả'}</td>
              <td className="px-4 py-4"><div className="space-y-2"><p className="font-semibold">{remaining(item)}</p><BorrowedBookDueWarning remainingDays={item.remainingDays} /></div></td>
            </tr>)}</tbody>
          </table>
        </div>
      </Card>
    </>}
  </div>
}
