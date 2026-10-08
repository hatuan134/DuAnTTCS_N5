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
import MyReturnedBooksPanel from './MyReturnedBooksPanel'

export default function MyBorrowedBooksPage() {
  const user = getCurrentUser()
  const allowed = user?.role === 'READER'
  const [dataReaderId, setDataReaderId] = useState<number | undefined>(undefined)
  const [tab, setTab] = useState<'borrowed' | 'returned'>('borrowed')
  const [items, setItems] = useState<MyBorrowedBook[]>([])
  const [loading, setLoading] = useState(true)
  const [failed, setFailed] = useState(false)
  const [error, setError] = useState('')
  const [revision, setRevision] = useState(0)

  useEffect(() => {
    if (!allowed || tab !== 'borrowed') return
    let active = true
    setLoading(true); setFailed(false); setError(''); setItems([])
    myBorrowedBooksService.list()
      .then((data) => { if (active) { setItems(data); setDataReaderId(user?.id) } })
      .catch((e: unknown) => {
        if (active) {
          setFailed(true)
          setError(getApiErrorMessage(e, 'Không tải được sách đang mượn. Vui lòng thử lại.'))
        }
      })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [allowed, user?.id, revision, tab])

  if (!allowed) return <p role="alert" className="rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700">Chức năng Sách đang mượn dành cho Bạn đọc.</p>
  const currentItems = dataReaderId === user?.id ? items : []
  const remaining = (item: MyBorrowedBook) => item.remainingDays === null ? 'Chưa xác định' : `${item.remainingDays} ngày`
  return <div className="space-y-4">
    <PageHeader title={tab === 'borrowed' ? 'Sách đang mượn' : 'Lịch sử đã trả'}
      description={tab === 'borrowed' ? 'Danh sách từng bản sao bạn đang mượn, ngày mượn và hạn trả theo giờ Việt Nam.' : 'Xem lại các giao dịch trả sách đã hoàn tất của bạn.'}
      action={tab === 'borrowed' ? <Button type="button" variant="secondary" loading={loading} onClick={() => setRevision((value) => value + 1)}>Làm mới</Button> : undefined} />
    <div className="flex flex-wrap gap-2 border-b border-slate-200 pb-3" role="tablist" aria-label="Sách của tôi">
      <button type="button" role="tab" id="borrowed-books-tab" aria-selected={tab === 'borrowed'} aria-controls="borrowed-books-panel"
        className={`rounded-xl px-4 py-2.5 text-sm font-semibold ${tab === 'borrowed' ? 'bg-blue-600 text-white' : 'bg-slate-100 text-slate-700 hover:bg-slate-200'}`}
        onClick={() => setTab('borrowed')}>Sách đang mượn</button>
      <button type="button" role="tab" id="returned-books-tab" aria-selected={tab === 'returned'} aria-controls="returned-books-panel"
        className={`rounded-xl px-4 py-2.5 text-sm font-semibold ${tab === 'returned' ? 'bg-blue-600 text-white' : 'bg-slate-100 text-slate-700 hover:bg-slate-200'}`}
        onClick={() => setTab('returned')}>Lịch sử đã trả</button>
    </div>
    {tab === 'returned' ? <section id="returned-books-panel" role="tabpanel" aria-labelledby="returned-books-tab"><MyReturnedBooksPanel /></section>
      : <section id="borrowed-books-panel" role="tabpanel" aria-labelledby="borrowed-books-tab" className="space-y-4">
    {error && <FeedbackAlert message={error} tone="error" onDismiss={() => setError('')} />}
    {loading && <LoadingState />}
    {!loading && failed && <EmptyState title="Chưa tải được danh sách" description="Nhấn Làm mới để thử lại." />}
    {!loading && !failed && currentItems.length === 0 && <EmptyState title="Bạn không có sách đang mượn" description="Sách sẽ xuất hiện ở đây sau khi Thủ thư xác nhận cho mượn." />}
    {!loading && !failed && currentItems.length > 0 && <>
      <p className="text-sm text-slate-600">Bạn đang mượn {currentItems.length} bản sách. Còn dưới 3 ngày được gắn nhãn Sắp đến hạn, kể cả hạn hôm nay (0 ngày). Sách đã quá hạn hiển thị số ngày trễ.</p>
      <Card className="overflow-hidden">
        <div className="divide-y divide-slate-200 md:hidden">
          {currentItems.map((item) => <article key={item.id} className="space-y-3 p-4">
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
            <tbody className="divide-y divide-slate-100">{currentItems.map((item) => <tr key={item.id} className="hover:bg-slate-50">
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
    </section>}
  </div>
}
