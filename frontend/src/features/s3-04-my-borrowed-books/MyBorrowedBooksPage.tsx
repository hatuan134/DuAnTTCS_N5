import { Fragment, useEffect, useRef, useState } from 'react'
import axios from 'axios'
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

// The list endpoint includes only unreturned items owned by the current JWT account.
// POST still rechecks ownership, returned_at and the due date in the database.
function canRequestRenewal(item: MyBorrowedBook): boolean {
  return item.dueAt !== null && item.remainingDays !== null && item.remainingDays >= 0
}

export default function MyBorrowedBooksPage() {
  const user = getCurrentUser()
  const allowed = user?.role === 'READER'
  const [dataReaderId, setDataReaderId] = useState<number | undefined>(undefined)
  const [tab, setTab] = useState<'borrowed' | 'returned'>('borrowed')
  const [items, setItems] = useState<MyBorrowedBook[]>([])
  const [loading, setLoading] = useState(true)
  const [failed, setFailed] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [actionError, setActionError] = useState('')
  const [queueRejection, setQueueRejection] = useState<{ itemId: number; message: string } | null>(null)
  const [confirming, setConfirming] = useState<MyBorrowedBook | null>(null)
  const [checking, setChecking] = useState(false)
  const checkingRef = useRef(false)
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

  async function confirmRenewal() {
    if (!confirming || checkingRef.current) return
    checkingRef.current = true
    setChecking(true)
    setActionError('')
    setNotice('')
    setQueueRejection(null)
    try {
      const result = await myBorrowedBooksService.checkRenewal(confirming.id)
      setNotice(result.message)
      // Refetch all copies: the renewal counter belongs to the loan, not only this item.
      setRevision((value) => value + 1)
    } catch (e: unknown) {
      const message = getApiErrorMessage(e, 'Không kiểm tra được điều kiện gia hạn. Vui lòng thử lại.')
      const status = axios.isAxiosError(e) ? e.response?.status : undefined
      const code = axios.isAxiosError(e) ? e.response?.data?.code : undefined
      if (status === 409 && (code === 'RENEWAL_BLOCKED_BY_RESERVATION' || code === 'RENEWAL_LIMIT_REACHED'
        || code === 'RENEWAL_POLICY_MISSING' || code === 'RENEWAL_BLOCKED_BY_VIOLATIONS')) {
        // Keep the reason next to this book. Do not refresh and accidentally hide it.
        setQueueRejection({ itemId: confirming.id, message })
      } else {
        setActionError(message)
        // A returned/overdue item may have disappeared or changed since loading.
        if (status === 404 || status === 409) setRevision((value) => value + 1)
      }
    } finally {
      checkingRef.current = false
      setChecking(false)
      setConfirming(null)
    }
  }

  if (!allowed) return <p role="alert" className="rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700">Chức năng Sách đang mượn dành cho Bạn đọc.</p>
  const currentItems = dataReaderId === user?.id ? items : []
  const remaining = (item: MyBorrowedBook) => item.remainingDays === null ? 'Chưa xác định' : `${item.remainingDays} ngày`
  const quotaReached = (item: MyBorrowedBook) => item.maxRenewals !== null
    && item.maxRenewals > 0 && item.renewalsUsed >= item.maxRenewals
  const renewButton = (item: MyBorrowedBook) => canRequestRenewal(item) ? (
    <Button type="button" size="sm" variant="secondary" disabled={checking || loading}
      onClick={() => { setNotice(''); setActionError(''); setQueueRejection(null); setConfirming(item) }}>
      Gia hạn
    </Button>
  ) : null
  const renewalUsage = (item: MyBorrowedBook) => (
    <span className={quotaReached(item) ? 'font-semibold text-amber-700' : 'text-slate-700'}>
      {item.renewalsUsed}/{item.maxRenewals !== null && item.maxRenewals > 0 ? item.maxRenewals : 'Chưa cấu hình'} lần
    </span>
  )
  const queueRejectionAlert = (item: MyBorrowedBook) => queueRejection?.itemId === item.id ? (
    <FeedbackAlert message={queueRejection.message} tone="error"
      onDismiss={() => setQueueRejection((current) => current?.itemId === item.id ? null : current)} />
  ) : null

  return <div className="space-y-4">
    <PageHeader title={tab === 'borrowed' ? 'Sách đang mượn' : 'Lịch sử đã trả'}
      description={tab === 'borrowed' ? 'Danh sách từng bản sao bạn đang mượn, ngày mượn và hạn trả theo giờ Việt Nam.' : 'Xem lại các giao dịch trả sách đã hoàn tất của bạn.'}
      action={tab === 'borrowed' ? <Button type="button" variant="secondary" loading={loading} onClick={() => setRevision((value) => value + 1)}>Làm mới</Button> : undefined} />
    <div className="flex flex-wrap gap-2 border-b border-slate-200 pb-3" role="tablist" aria-label="Sách của tôi">
      <button type="button" role="tab" id="borrowed-books-tab" aria-selected={tab === 'borrowed'} aria-controls="borrowed-books-panel"
        className={`rounded-xl px-4 py-2.5 text-sm font-semibold ${tab === 'borrowed' ? 'bg-blue-600 text-white' : 'bg-slate-100 text-slate-700 hover:bg-slate-200'}`}
        onClick={() => { if (!checkingRef.current) { setTab('borrowed'); setConfirming(null) } }}>Sách đang mượn</button>
      <button type="button" role="tab" id="returned-books-tab" aria-selected={tab === 'returned'} aria-controls="returned-books-panel"
        className={`rounded-xl px-4 py-2.5 text-sm font-semibold ${tab === 'returned' ? 'bg-blue-600 text-white' : 'bg-slate-100 text-slate-700 hover:bg-slate-200'}`}
        onClick={() => { if (!checkingRef.current) { setTab('returned'); setConfirming(null) } }}>Lịch sử đã trả</button>
    </div>
    {notice && <FeedbackAlert message={notice} tone="success" onDismiss={() => setNotice('')} />}
    {actionError && <FeedbackAlert message={actionError} tone="error" onDismiss={() => setActionError('')} />}
    {confirming && <Card className="border border-blue-200 p-5">
      <h2 className="font-semibold text-slate-900">Xác nhận lượt gia hạn</h2>
      <p className="my-3 text-sm leading-6 text-slate-700">
        Bạn muốn ghi nhận một lượt gia hạn cho sách <strong className="break-words">{confirming.bookTitle}</strong>
        {' '}({confirming.barcode})? Đã dùng {confirming.renewalsUsed}/{confirming.maxRenewals ?? 'chưa cấu hình'} lần.
        Hệ thống sẽ kiểm tra lại điều kiện khi bạn xác nhận.
      </p>
      <p className="mb-4 text-xs text-slate-600">Nếu được chấp nhận, số lần đã dùng sẽ tăng 1. Bước này chưa tính hoặc thay đổi hạn trả.</p>
      <div className="flex flex-wrap gap-3">
        <Button type="button" variant="secondary" disabled={checking} onClick={() => setConfirming(null)}>Đóng</Button>
        <Button type="button" loading={checking} onClick={() => void confirmRenewal()}>Xác nhận gia hạn</Button>
      </div>
    </Card>}
    {tab === 'returned' ? <section id="returned-books-panel" role="tabpanel" aria-labelledby="returned-books-tab"><MyReturnedBooksPanel /></section>
      : <section id="borrowed-books-panel" role="tabpanel" aria-labelledby="borrowed-books-tab" className="space-y-4">
    {error && <FeedbackAlert message={error} tone="error" onDismiss={() => setError('')} />}
    {loading && <LoadingState />}
    {!loading && failed && <EmptyState title="Chưa tải được danh sách" description="Nhấn Làm mới để thử lại." />}
    {!loading && !failed && currentItems.length === 0 && <EmptyState title="Bạn không có sách đang mượn" description="Sách sẽ xuất hiện ở đây sau khi Thủ thư xác nhận cho mượn." />}
    {!loading && !failed && currentItems.length > 0 && <>
      <p className="text-sm text-slate-600">Bạn đang mượn {currentItems.length} bản sách. Còn dưới 3 ngày được gắn nhãn Sắp đến hạn, kể cả hạn hôm nay (0 ngày). Sách đã quá hạn hiển thị số ngày trễ. Chỉ sách chưa quá hạn mới có nút Gia hạn. Khi hết lượt, hệ thống sẽ từ chối và nêu rõ số lần đã dùng.</p>
      <Card className="overflow-hidden">
        <div className="divide-y divide-slate-200 md:hidden">
          {currentItems.map((item) => <article key={item.id} className="space-y-3 p-4">
            <h3 className="break-words font-semibold text-slate-900">{item.bookTitle}</h3>
            <dl className="grid gap-3 text-sm sm:grid-cols-2">
              <div><dt className="text-slate-500">Mã vạch bản sao</dt><dd className="break-all font-mono text-slate-900">{item.barcode}</dd></div>
              <div><dt className="text-slate-500">Ngày mượn</dt><dd>{formatLoanTimestamp(item.borrowedAt, true)}</dd></div>
              <div><dt className="text-slate-500">Hạn trả</dt><dd>{item.dueAt ? formatLoanTimestamp(item.dueAt, true) : 'Chưa có hạn trả'}</dd></div>
              <div><dt className="text-slate-500">Số ngày còn lại</dt><dd className="space-y-2"><p className="font-semibold">{remaining(item)}</p><BorrowedBookDueWarning remainingDays={item.remainingDays} /></dd></div>
              <div><dt className="text-slate-500">Số lần gia hạn đã dùng / tối đa</dt><dd>{renewalUsage(item)}</dd></div>
            </dl>
            <div className="flex justify-end">{renewButton(item)}</div>
            {queueRejectionAlert(item)}
          </article>)}
        </div>
        <div className="hidden md:block">
          <table className="data-table w-full table-fixed text-sm">
            <caption className="sr-only">Danh sách đầy đủ các bản sách bạn đang mượn</caption>
            <thead className="bg-slate-50 text-left text-xs font-semibold uppercase text-slate-500"><tr>
              <th scope="col" className="w-1/4 px-4 py-3">Tên sách</th><th scope="col" className="px-4 py-3">Mã vạch bản sao</th>
              <th scope="col" className="px-4 py-3">Ngày mượn</th><th scope="col" className="px-4 py-3">Hạn trả</th><th scope="col" className="px-4 py-3">Số ngày còn lại</th>
              <th scope="col" className="w-28 px-3 py-3 text-right">Thao tác</th>
            </tr></thead>
            <tbody className="divide-y divide-slate-100">{currentItems.map((item) => <Fragment key={item.id}><tr className="hover:bg-slate-50">
              <td className="break-words px-4 py-4 font-medium">{item.bookTitle}</td>
              <td className="break-all px-4 py-4 font-mono">{item.barcode}</td>
              <td className="px-4 py-4">{formatLoanTimestamp(item.borrowedAt, true)}</td>
              <td className="px-4 py-4">{item.dueAt ? formatLoanTimestamp(item.dueAt, true) : 'Chưa có hạn trả'}</td>
              <td className="px-4 py-4"><div className="space-y-2"><p className="font-semibold">{remaining(item)}</p><BorrowedBookDueWarning remainingDays={item.remainingDays} /><p className="text-xs">Gia hạn: {renewalUsage(item)}</p></div></td>
              <td className="px-3 py-4 text-right">{renewButton(item)}</td>
            </tr>
            {queueRejection?.itemId === item.id && <tr><td colSpan={6} className="px-4 pb-4">{queueRejectionAlert(item)}</td></tr>}
            </Fragment>)}</tbody>
          </table>
        </div>
      </Card>
    </>}
    </section>}
  </div>
}
