import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { AlertTriangle, ChevronDown, ChevronUp, RefreshCcw, Search } from 'lucide-react'
import Button from '../../components/ui/Button'
import Card from '../../components/ui/Card'
import FeedbackAlert from '../../components/ui/FeedbackAlert'
import PageHeader from '../../components/ui/PageHeader'
import { getCurrentUser } from '../../core/auth/authStorage'
import { getApiErrorMessage } from '../s1-02-user-management/accountService'
import { loanRoles } from '../s3-01-loans/loanService'
import { loanRejectionService } from './loanRejectionService'
import type { LoanRejection, LoanRejectionPage } from './loanRejectionService'

function dateTime(value: string) {
  return new Date(value).toLocaleString('vi-VN', {
    timeZone: 'Asia/Ho_Chi_Minh', day: '2-digit', month: '2-digit', year: 'numeric',
    hour: '2-digit', minute: '2-digit', second: '2-digit',
  })
}

const sources: Record<LoanRejection['source'], string> = {
  CARD_CHECK: 'Kiểm tra thẻ',
  DIRECT_CONFIRM: 'Xác nhận tại quầy',
  RESERVATION_CONFIRM: 'Xác nhận đặt giữ',
}

export default function LoanRejectionsPage() {
  const allowed = loanRoles.includes(getCurrentUser()?.role ?? '')
  const [draft, setDraft] = useState('')
  const [filter, setFilter] = useState('')
  const [page, setPage] = useState(0)
  const [reload, setReload] = useState(0)
  const [data, setData] = useState<LoanRejectionPage | null>(null)
  const [loading, setLoading] = useState(false)
  const [loadError, setLoadErrorState] = useState('')
  const [loadErrorNotice, setLoadErrorNotice] = useState('')
  const setLoadError = (message: string) => { setLoadErrorState(message); setLoadErrorNotice(message) }
  const [detail, setDetail] = useState<LoanRejection | null>(null)
  const [detailId, setDetailId] = useState<number | null>(null)
  const [detailError, setDetailError] = useState('')
  const [detailLoading, setDetailLoading] = useState(false)

  useEffect(() => {
    if (!allowed) return
    let valid = true
    setLoading(true)
    setLoadError('')
    loanRejectionService.page(page, filter)
      .then((response) => { if (valid) setData(response) })
      .catch((error: unknown) => {
        if (valid) { setData(null); setLoadError(getApiErrorMessage(error, 'Không tải được nhật ký.')) }
      })
      .finally(() => { if (valid) setLoading(false) })
    return () => { valid = false }
  }, [allowed, filter, page, reload])

  const openDetail = useCallback(async (id: number) => {
    if (id === detailId) {
      setDetailId(null); setDetail(null); setDetailError('')
      return
    }
    setDetailId(id); setDetail(null); setDetailError(''); setDetailLoading(true)
    try {
      setDetail(await loanRejectionService.detail(id))
    } catch (error: unknown) {
      setDetailError(getApiErrorMessage(error, 'Không thể tải chi tiết nhật ký.'))
    } finally {
      setDetailLoading(false)
    }
  }, [detailId])

  function search(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (draft.trim().length > 100) return
    setFilter(draft.trim()); setPage(0); setDetailId(null); setDetail(null)
  }

  if (!allowed) return <p role="alert" className="rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700">Bạn không có quyền xem nhật ký từ chối cho mượn.</p>

  return <div className="space-y-5">
    <PageHeader title="Nhật ký chặn và bỏ qua cho mượn" description="Theo dõi các lượt bị chặn và lượt được Quản lý cho phép bỏ qua, kèm nguyên nhân và người thực hiện." />
    <Card className="p-4 sm:p-6">
      <div className="flex items-start gap-3 rounded-xl border border-amber-200 bg-amber-50 p-3 text-sm leading-6 text-amber-900">
        <AlertTriangle size={18} className="mt-1 shrink-0" />
        <p>Quyền xem hiện áp dụng tạm thời cho Thủ thư, Quản lý thư viện và Quản trị hệ thống. Thời hạn lưu nhật ký và phạm vi quyền xem cuối cùng cần được thống nhất với Product Owner.</p>
      </div>
      <form onSubmit={search} className="mt-4 flex flex-col gap-3 sm:flex-row sm:items-end">
        <label className="flex min-w-0 flex-1 flex-col gap-1.5 text-sm font-semibold text-slate-700">
          Tìm theo mã thẻ
          <input className="min-h-11 rounded-xl border border-slate-300 bg-white px-3 text-sm font-normal outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
            value={draft} maxLength={100} onChange={(event) => setDraft(event.target.value)} placeholder="Nhập mã thẻ cần tra cứu" />
        </label>
        <div className="flex flex-wrap gap-2">
          <Button type="submit"><Search size={16} className="mr-2 inline" />Tìm kiếm</Button>
          <Button type="button" variant="secondary" onClick={() => { setDraft(''); setFilter(''); setPage(0); setReload((v) => v + 1) }}>
            <RefreshCcw size={16} className="mr-2 inline" />Xóa lọc / Làm mới
          </Button>
        </div>
      </form>
      {filter && <p className="mt-3 text-xs text-slate-600">Đang lọc mã thẻ chứa: <strong>{filter}</strong></p>}
    </Card>

    <Card className="overflow-hidden p-0">
      <div className="flex flex-wrap items-center justify-between gap-2 border-b border-slate-200 p-4">
        <h2 className="font-semibold text-slate-900">Lịch sử kiểm tra và quyết định</h2>
        <span className="text-sm text-slate-600">{data ? `${data.total} lần` : '—'}</span>
      </div>
      {loading && <p role="status" className="p-5 text-sm text-slate-600">Đang tải nhật ký…</p>}
      {loadErrorNotice && <FeedbackAlert message={loadErrorNotice} tone="error" onDismiss={() => setLoadErrorNotice('')} />}
      {!loading && loadError && <div className="p-4"><p role="status" className="text-sm text-slate-700">Chưa tải được nhật ký từ chối cho mượn.</p><Button variant="secondary" type="button" onClick={() => setReload((v) => v + 1)}>Thử lại</Button></div>}
      {!loading && data?.items.length === 0 && <p className="p-6 text-sm text-slate-600">Chưa có nhật ký nào phù hợp với bộ lọc hiện tại.</p>}
      {!loading && data && data.items.length > 0 && <div className="divide-y divide-slate-200">
        {data.items.map((item) => <div key={item.id} className="px-4 py-4 sm:px-6">
          <div className="grid gap-3 sm:grid-cols-[minmax(0,1.2fr)_minmax(0,1fr)_minmax(0,1fr)_auto] sm:items-center">
            <div className="min-w-0"><p className="text-xs text-slate-500">Thời điểm</p><p className="font-medium text-slate-900">{dateTime(item.occurredAt)}</p><p className="text-xs text-slate-500">{sources[item.source]}</p>
              <span className={`mt-1 inline-block rounded-lg px-2 py-1 text-xs font-semibold ${item.eventType === 'OVERRIDDEN' ? 'bg-amber-100 text-amber-900' : 'bg-red-100 text-red-800'}`}>
                {item.eventType === 'OVERRIDDEN' ? 'Quản lý đã cho phép bỏ qua' : 'Giao dịch bị chặn'}
              </span></div>
            <div className="min-w-0"><p className="text-xs text-slate-500">Bạn đọc / mã thẻ</p><p className="break-words font-medium text-slate-900">{item.readerName}</p><p className="break-all font-mono text-xs text-slate-600">{item.cardNumber}</p></div>
            <div className="min-w-0"><p className="text-xs text-slate-500">Nhân viên thao tác</p><p className="break-words font-medium text-slate-900">{item.actorName}</p><p className="text-xs text-amber-800">{item.reasons.length} lý do</p></div>
            <Button type="button" variant="secondary" onClick={() => { void openDetail(item.id) }}>
              {detailId === item.id ? <ChevronUp size={16} className="mr-1 inline" /> : <ChevronDown size={16} className="mr-1 inline" />}
              {detailId === item.id ? 'Thu gọn' : 'Chi tiết'}
            </Button>
          </div>
          {detailId === item.id && <section aria-label={`Chi tiết nhật ký ${item.id}` } className="mt-4 rounded-xl border border-slate-200 bg-slate-50 p-4">
            {detailLoading && <p role="status" className="text-sm text-slate-600">Đang tải lý do…</p>}
            {detailError && <FeedbackAlert tone="error" message={detailError} onDismiss={() => setDetailError('')} />}
            {detail?.id === item.id && <>
              <p className="mb-2 font-semibold text-slate-900">{detail.eventType === 'OVERRIDDEN' ? 'Các vi phạm đã được bỏ qua cho giao dịch này' : 'Các lý do chặn giao dịch'}</p>
              {detail.eventType === 'OVERRIDDEN' && <div className="mb-3 rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-950">
                <p><strong>Quản lý xác nhận:</strong> {detail.actorName} (ID #{detail.actorId})</p>
                <p className="mt-1 break-words"><strong>Lý do:</strong> {detail.overrideReason}</p>
                <p className="mt-1"><strong>Phiếu mượn:</strong> {detail.loanId ? <a className="font-semibold underline" href={`/loans/${detail.loanId}`}>#{detail.loanId}</a> : '—'}</p>
              </div>}
              <ul className="list-disc space-y-2 pl-5 text-sm text-amber-900">{detail.reasons.map((reason) => <li key={reason.code}><strong>{reason.code}</strong>: {reason.message}</li>)}</ul>
              <dl className="mt-4 grid gap-3 border-t border-slate-200 pt-4 text-sm sm:grid-cols-2 lg:grid-cols-4">
                <div><dt className="text-slate-500">Sách chưa trả / hạn mức</dt><dd className="font-semibold">{detail.borrowedBooks} / {detail.maxBooks}</dd></div>
                <div><dt className="text-slate-500">Phiếu quá hạn</dt><dd className="font-semibold">{detail.overdueLoans}</dd></div>
                <div><dt className="text-slate-500">Phí chưa thanh toán</dt><dd className="font-semibold">{Number(detail.unpaidAmountVnd).toLocaleString('vi-VN')} ₫</dd></div>
                <div><dt className="text-slate-500">Mã nhật ký</dt><dd className="font-semibold">#{detail.id}</dd></div>
              </dl>
            </>}
          </section>}
        </div>)}
      </div>}
      {data && data.total > data.size && <div className="flex flex-wrap items-center justify-between gap-3 border-t border-slate-200 p-4 text-sm">
        <span>Trang {data.page + 1} / {Math.ceil(data.total / data.size)}</span>
        <div className="flex gap-2"><Button type="button" variant="secondary" disabled={loading || page === 0} onClick={() => { setPage((v) => Math.max(0, v - 1)); setDetailId(null) }}>Trước</Button>
          <Button type="button" variant="secondary" disabled={loading || (page + 1) * data.size >= data.total} onClick={() => { setPage((v) => v + 1); setDetailId(null) }}>Sau</Button></div>
      </div>}
    </Card>
  </div>
}
