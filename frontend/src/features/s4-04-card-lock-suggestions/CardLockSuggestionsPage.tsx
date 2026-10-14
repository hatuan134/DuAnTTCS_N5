import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import Button from '../../components/ui/Button'
import Card from '../../components/ui/Card'
import FeedbackAlert from '../../components/ui/FeedbackAlert'
import PageHeader from '../../components/ui/PageHeader'
import TablePagination from '../../components/ui/TablePagination'
import StatusBadge from '../../components/ui/StatusBadge'
import { getCurrentUser } from '../../core/auth/authStorage'
import { getApiErrorMessage } from '../s1-02-user-management/accountService'
import { formatLoanTimestamp } from '../s3-01-loans/loanService'
import { cardLockSuggestionService, suggestionMoney, suggestionReason, suggestionStatus, type SuggestionPage, type SuggestionDecision } from './cardLockSuggestionService'
import CardLockSuggestionDetailsDialog from './CardLockSuggestionDetailsDialog'
export default function CardLockSuggestionsPage() {
  if (getCurrentUser()?.role !== 'LIBRARY_MANAGER') return <p role="alert">Chỉ Quản lý thư viện được xem và quyết định đề xuất khóa thẻ.</p>
  return <Suggestions />
}
function Suggestions() {
  const [data, setData] = useState<SuggestionPage | null>(null)
  const [history, setHistory] = useState(false), [page, setPage] = useState(0)
  const [loading, setLoading] = useState(true), [revision, setRevision] = useState(0)
  const [selected, setSelected] = useState<number | null>(null)
  const [feedback, setFeedback] = useState<{ tone: 'success' | 'error' | 'warning'; message: string } | null>(null)
  useEffect(() => {
    let active = true
    setLoading(true); setData(null)
    cardLockSuggestionService.list(history, page).then(value => {
      if (!active) return
      if (page > 0 && value.totalPages <= page) { setPage(Math.max(0, value.totalPages - 1)); return }
      setData(value)
    }).catch(error => { if (active) setFeedback({ tone: 'error', message: getApiErrorMessage(error, 'Không tải được đề xuất khóa thẻ.') }) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [history, page, revision])
  function changed(result: SuggestionDecision) {
    setSelected(null)
    setFeedback({ tone: result.suggestion.status === 'ACCEPTED' || result.suggestion.status === 'IGNORED' ? 'success' : 'warning', message: result.message })
    setRevision(v => v + 1)
  }
  return <div className="min-w-0 space-y-5">
    <PageHeader title="Đề xuất khóa thẻ" description="Quá hạn trên 30 ngày mở cửa hoặc nợ phí vượt ngưỡng. Quản lý xem xét trước khi khóa." />
    {feedback && <FeedbackAlert {...feedback} onDismiss={() => setFeedback(null)} />}
    <Card className="space-y-3 p-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-sm text-slate-600">Ngưỡng nợ hiện tại: <strong>{data ? suggestionMoney(data.settings.debtThresholdVnd) : 'Đang tải...'}</strong></p>
        <Link to="/card-lock-suggestion-settings" className="font-semibold text-blue-700 hover:underline">Cấu hình ngưỡng nợ phí</Link>
      </div>
      <p className="text-sm leading-6 text-slate-600">Tự kiểm tra mỗi 5 phút. Mở hoặc làm mới danh sách sẽ kiểm tra ngay. Đề xuất bị bỏ qua không sinh lại trong cùng đợt vi phạm.</p>
      {data?.settings.lastCheckedAt && <p className="text-xs text-slate-500">Kiểm tra gần nhất: {formatLoanTimestamp(data.settings.lastCheckedAt)}</p>}
    </Card>
    <div className="flex flex-wrap items-center justify-between gap-3">
      <div className="flex flex-wrap gap-2" aria-label="Loại danh sách">
        <Button variant={history ? 'secondary' : 'primary'} aria-pressed={!history} disabled={loading} onClick={() => { setHistory(false); setPage(0) }}>Chờ xử lý</Button>
        <Button variant={history ? 'primary' : 'secondary'} aria-pressed={history} disabled={loading} onClick={() => { setHistory(true); setPage(0) }}>Lịch sử đề xuất</Button>
      </div>
      <Button variant="secondary" disabled={loading} onClick={() => setRevision(v => v + 1)}>Làm mới</Button>
    </div>
    {loading ? <p role="status">Đang kiểm tra vi phạm và tải danh sách...</p> : !data ? <Card className="p-5"><p>Chưa tải được danh sách đề xuất.</p><Button onClick={() => setRevision(v => v + 1)}>Thử lại</Button></Card> : <>
      <p className="text-sm text-slate-500">{data.totalItems} đề xuất {history ? 'đã xử lý hoặc kết thúc' : 'đang chờ xử lý'}</p>
      {data.items.length === 0 ? <Card className="p-6"><p role="status">{history ? 'Chưa có lịch sử đề xuất.' : 'Không có đề xuất đang chờ xử lý. Hệ thống chỉ đề xuất khi bạn đọc đang vi phạm và thẻ chưa có lệnh khóa.'}</p></Card> :
        <ol className="space-y-4">{data.items.map(item => <li key={item.id}><Card className="space-y-4 p-5 sm:p-6">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div className="min-w-0"><h2 className="break-words font-semibold text-slate-900">{item.readerName}</h2><p className="break-words text-sm text-slate-500">Thẻ {item.cardNumber} · Đề xuất #{item.id}</p></div>
            <span className={`rounded-full px-3 py-1 text-sm font-semibold ${item.status === 'PENDING' ? 'bg-amber-100 text-amber-800' : 'bg-slate-100 text-slate-700'}`}>{suggestionStatus[item.status]}</span>
          </div>
          <dl className="grid gap-3 text-sm sm:grid-cols-2 lg:grid-cols-4">
            <Field label="Quá hạn lớn nhất" value={`${item.maxOverdueDays} ngày mở cửa`} />
            <Field label="Tổng nợ tại lần đánh giá" value={suggestionMoney(item.unpaidFeesVnd)} />
            <Field label="Ngưỡng tại lần đánh giá" value={suggestionMoney(item.debtThresholdVnd)} />
            <div><dt className="text-slate-500">Trạng thái thẻ hiện tại</dt><dd className="mt-1"><StatusBadge status={item.currentCardStatus} /></dd></div>
          </dl>
          <p className="break-words text-sm font-medium text-slate-800">{suggestionReason(item.reasonCode)}</p>
          {item.decidedAt && <p className="whitespace-pre-wrap break-words text-sm text-slate-600">{item.deciderName} · {formatLoanTimestamp(item.decidedAt)} · {item.decisionReason}<br />Trạng thái thẻ sau quyết định: {item.cardStatusAfterDecision === 'LOCKED' ? 'Đã khóa' : item.cardStatusAfterDecision}</p>}
          <Button variant="secondary" onClick={() => setSelected(item.id)}>Xem chi tiết{item.status === 'PENDING' ? ' và xử lý' : ''}</Button>
        </Card></li>)}</ol>}
      <TablePagination page={data.page + 1} pageSize={data.size} totalItems={data.totalItems} totalPages={data.totalPages} onPageChange={next => setPage(next - 1)} />
    </>}
    {selected !== null && <CardLockSuggestionDetailsDialog key={selected} id={selected} onClose={() => setSelected(null)} onChanged={changed} />}
  </div>
}
function Field({ label, value }: { label: string; value: string }) {
  return <div className="min-w-0"><dt className="text-slate-500">{label}</dt><dd className="mt-1 break-words font-semibold text-slate-900">{value}</dd></div>
}
