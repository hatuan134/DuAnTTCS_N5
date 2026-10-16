import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { RefreshCcw, Search } from 'lucide-react'
import Button from '../../components/ui/Button'
import Card from '../../components/ui/Card'
import FeedbackAlert from '../../components/ui/FeedbackAlert'
import Input from '../../components/ui/Input'
import TablePagination from '../../components/ui/TablePagination'
import { getApiErrorMessage } from '../s1-02-user-management/accountService'
import { formatLoanTimestamp, loanService, type LoanSearchFilters, type LoanSearchPage, type ReaderLoanLookupPage } from './loanService'

interface Props {
  showFilters?: boolean
  disabled?: boolean
  revision?: number
  externalKeyword?: string
  onSelectReader?: (cardNumber: string) => void
  onSelectCopy?: (barcode: string) => void
}
const emptyFilters: LoanSearchFilters = { fromDate: '', toDate: '', status: '' }
const statusLabel: Record<string, string> = {
  BORROWED: 'Đang mượn', PARTIALLY_RETURNED: 'Đã xử lý một phần', RETURNED: 'Đã trả',
  LOST: 'Đã đóng · Mất', DAMAGED: 'Đã đóng · Hỏng', MIXED_INCIDENT: 'Đã đóng · Mất và hỏng', EMPTY: 'Chưa có bản sao',
}

/** Shared staff search. Selecting a row only opens a preview; it never confirms a loan or return. */
export default function LoanLookupPanel({ showFilters = false, disabled = false, revision = 0,
  externalKeyword, onSelectReader, onSelectCopy }: Props) {
  const [draft, setDraft] = useState('')
  const [keyword, setKeyword] = useState('')
  const [mode, setMode] = useState<'loans' | 'readers'>('loans')
  const [page, setPage] = useState(0)
  const [reload, setReload] = useState(0)
  const [collapsed, setCollapsed] = useState(false)
  const [filters, setFilters] = useState<LoanSearchFilters>(emptyFilters)
  const [appliedFilters, setAppliedFilters] = useState<LoanSearchFilters>(emptyFilters)
  const [loans, setLoans] = useState<LoanSearchPage | null>(null)
  const [readers, setReaders] = useState<ReaderLoanLookupPage | null>(null)
  const [loading, setLoading] = useState(true)
  const [failed, setFailed] = useState(false)
  const [error, setError] = useState('')
  const generation = useRef(0)
  const value = externalKeyword ?? keyword

  useEffect(() => { setPage(0) }, [externalKeyword, revision])
  useEffect(() => {
    const request = ++generation.current
    setLoading(true); setFailed(false); setError(''); setLoans(null); setReaders(null)
    const load = mode === 'readers' ? loanService.searchReaders(value, page) : loanService.search(value, page, appliedFilters)
    load.then(data => {
      if (request !== generation.current) return
      if (mode === 'readers') setReaders(data as ReaderLoanLookupPage)
      else setLoans(data as LoanSearchPage)
    }).catch((cause: unknown) => {
      if (request !== generation.current) return
      setFailed(true); setError(getApiErrorMessage(cause, 'Không tải được kết quả. Vui lòng thử lại.'))
    }).finally(() => { if (request === generation.current) setLoading(false) })
    return () => { ++generation.current }
  }, [value, page, mode, appliedFilters, reload, revision])

  function search(event: FormEvent) {
    event.preventDefault()
    if (disabled) return
    if (draft.trim().length > 100) { setError('Từ khóa tìm kiếm tối đa 100 ký tự.'); return }
    if (filters.fromDate && filters.toDate && filters.fromDate > filters.toDate) {
      setError('Từ ngày không được lớn hơn Đến ngày.'); return
    }
    setCollapsed(false); setKeyword(draft.trim()); setAppliedFilters({ ...filters }); setPage(0); setReload(n => n + 1)
  }
  function selectReader(number: string) { setCollapsed(true); onSelectReader?.(number) }
  const data = mode === 'readers' ? readers : loans
  return <section aria-label="Tìm nhanh phiếu mượn" className="space-y-4">
    {externalKeyword === undefined && <Card className="p-4 sm:p-5">
      <form noValidate onSubmit={search} className="space-y-4">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-end">
          <div className="min-w-0 flex-1"><Input label="Tìm phiếu mượn hoặc bạn đọc" value={draft} disabled={disabled}
            maxLength={101} startIcon={<Search size={18} />} placeholder="Mã phiếu, mã thẻ, mã vạch, tên bạn đọc hoặc số điện thoại"
            onChange={event => setDraft(event.target.value)} /></div>
          <div className="flex shrink-0 gap-2"><Button type="submit" disabled={disabled} loading={loading}><Search size={16} />Tìm kiếm</Button>
            <Button type="button" variant="secondary" disabled={disabled || loading} onClick={() => {
              setCollapsed(false); setDraft(''); setKeyword(''); setFilters(emptyFilters); setAppliedFilters(emptyFilters); setPage(0); setReload(n => n + 1)
            }}><RefreshCcw size={16} /><span>Tất cả</span></Button></div>
        </div>
        {showFilters && <div className="grid gap-3 sm:grid-cols-3">
          <Input label="Từ ngày mượn" type="date" value={filters.fromDate} onChange={event => setFilters({ ...filters, fromDate: event.target.value })} />
          <Input label="Đến ngày mượn" type="date" value={filters.toDate} onChange={event => setFilters({ ...filters, toDate: event.target.value })} />
          <label className="text-sm font-semibold text-slate-700">Trạng thái phiếu<select className="mt-2 min-h-11 w-full rounded-xl border border-slate-300 bg-white px-3 font-normal" value={filters.status}
            onChange={event => setFilters({ ...filters, status: event.target.value as LoanSearchFilters['status'] })}>
            <option value="">Tất cả trạng thái</option>{Object.entries(statusLabel).map(([key, label]) => <option key={key} value={key}>{label}</option>)}
          </select></label>
        </div>}
        <p className="text-xs leading-5 text-slate-500">Để trống từ khóa để xem tất cả phiếu. Có thể nhập một phần tên, số điện thoại hoặc mã; không phân biệt chữ hoa và chữ thường.</p>
      </form>
    </Card>}
    {onSelectReader && externalKeyword === undefined && <div className="flex flex-wrap gap-2" aria-label="Loại kết quả">
      {(['loans', 'readers'] as const).map(tab => <Button key={tab} type="button" variant={mode === tab ? 'primary' : 'secondary'} disabled={disabled || loading}
        aria-pressed={mode === tab} onClick={() => { setMode(tab); setPage(0) }}>{tab === 'loans' ? 'Phiếu mượn hiện có' : 'Tìm bạn đọc kể cả chưa mượn'}</Button>)}
    </div>}
    <div className="flex flex-wrap items-center justify-between gap-2">
      <h2 className="font-semibold text-slate-900">{mode === 'loans' ? 'Phiếu mượn hiện có' : 'Bạn đọc phù hợp'}</h2>
      <p className="min-w-0 break-words text-sm text-slate-600">{data ? `${data.total} ${mode === 'loans' ? 'phiếu' : 'bạn đọc'}` : '—'}{value && <> · Từ khóa: <strong>{value}</strong></>}</p>
    </div>
    {(onSelectReader || onSelectCopy) && <Button variant="secondary" size="sm" disabled={disabled} onClick={() => setCollapsed(value => !value)}>{collapsed ? 'Hiện danh sách để chọn tiếp' : 'Thu gọn danh sách'}</Button>}
    {!collapsed && <>
    {(appliedFilters.fromDate || appliedFilters.toDate || appliedFilters.status) && mode === 'loans' && <p className="text-sm text-slate-600">
      Đang lọc: {appliedFilters.fromDate || 'Từ đầu'} → {appliedFilters.toDate || 'Hiện tại'} · {statusLabel[appliedFilters.status] || 'Tất cả trạng thái'}
    </p>}
    {error && <FeedbackAlert tone="error" message={error} onDismiss={() => setError('')} />}
    {loading && <p role="status" className="rounded-xl bg-slate-50 p-4 text-sm text-slate-600">Đang tải danh sách…</p>}
    {!loading && failed && <Card className="p-4"><p className="mb-3 text-sm text-slate-600">Chưa tải được danh sách.</p><Button variant="secondary" onClick={() => setReload(n => n + 1)}>Thử lại</Button></Card>}
    {!loading && !failed && data?.total === 0 && <Card className="p-5"><p className="font-semibold text-slate-900">{value ? 'Không tìm thấy kết quả phù hợp' : 'Chưa có dữ liệu'}</p>
      <p className="mt-2 text-sm leading-6 text-slate-600">{mode === 'readers' ? 'Kiểm tra tên, số điện thoại hoặc mã thẻ của bạn đọc.' : 'Thử tìm bằng tên, số điện thoại hoặc một phần mã; xóa bộ lọc để xem tất cả phiếu.'}</p>
      {onSelectReader && mode === 'loans' && <Button className="mt-3" variant="secondary" onClick={() => { setMode('readers'); setPage(0) }}>Tìm bạn đọc chưa có phiếu</Button>}
    </Card>}
    {!loading && loans && mode === 'loans' && loans.items.map((loan, index) => <Card key={loan.id} className="overflow-hidden">
      <div className="flex flex-col gap-3 bg-slate-50 p-4 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0"><p className="text-xs text-slate-500">Phiếu {loans.page * loans.size + index + 1}</p>
          <Link to={`/loans/${loan.id}`} className="mt-1 inline-block break-all font-mono font-bold text-blue-700 hover:underline">{loan.loanNumber}</Link>
          <p className="mt-2 break-words font-semibold text-slate-900">{loan.readerName}</p>
          <p className="mt-1 break-words text-sm text-slate-600">{loan.cardNumber || 'Chưa có thẻ'} · {loan.readerPhone || 'Chưa có số điện thoại'}</p>
          <p className="mt-1 text-xs text-slate-500">Mượn: {formatLoanTimestamp(loan.borrowedAt, true)} · {statusLabel[loan.status] || loan.status}</p>
        </div>
        <div className="flex shrink-0 flex-wrap gap-2"><Link to={`/loans/${loan.id}`} className="inline-flex min-h-10 items-center rounded-xl border border-slate-300 bg-white px-3 text-sm font-semibold text-blue-700 hover:bg-blue-50">Chi tiết phiếu</Link>
          {onSelectReader && <Button type="button" variant="secondary" disabled={disabled || !loan.cardNumber} onClick={() => { if (loan.cardNumber) selectReader(loan.cardNumber) }}>Chọn bạn đọc</Button>}</div>
      </div>
      <ul className="divide-y divide-slate-100 px-4">{loan.items.map((item, itemIndex) => <li key={`${item.barcode}-${itemIndex}`} className="flex flex-col gap-2 py-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="min-w-0"><p className="break-words text-sm font-medium text-slate-900">{item.bookTitle}</p><p className="mt-1 break-words text-xs text-slate-500">{item.barcode} · Hạn trả: {formatLoanTimestamp(item.dueAt, true)} · {statusLabel[item.status]}</p></div>
        {onSelectCopy && item.status === 'BORROWED' && <Button type="button" variant="secondary" size="sm" disabled={disabled} className="shrink-0 self-start" onClick={() => { setCollapsed(true); onSelectCopy(item.barcode) }}>Chọn để nhận trả</Button>}
      </li>)}</ul>
      {loan.items.length === 0 && <p className="p-4 text-sm text-slate-500">Phiếu chưa có thông tin bản sao.</p>}
    </Card>)}
    {!loading && readers && mode === 'readers' && <div className="space-y-3">{readers.items.map(reader => <Card key={reader.readerId} className="flex flex-col justify-between gap-3 p-4 sm:flex-row sm:items-center">
      <div className="min-w-0"><p className="break-words font-semibold text-slate-900">{reader.readerName}</p><p className="mt-1 break-words text-sm text-slate-600">{reader.cardNumber || 'Chưa được cấp thẻ'} · {reader.readerPhone || 'Chưa có số điện thoại'}</p></div>
      <Button type="button" variant="secondary" disabled={disabled || !reader.cardNumber} className="shrink-0 self-start" onClick={() => { if (reader.cardNumber) selectReader(reader.cardNumber) }}>Chọn bạn đọc</Button>
    </Card>)}</div>}
    {!loading && data && data.total > data.size && <Card className="overflow-hidden"><TablePagination page={data.page + 1} pageSize={data.size} totalItems={data.total} totalPages={Math.ceil(data.total / data.size)} onPageChange={next => { if (!disabled) setPage(next - 1) }} /></Card>}
    </>}
  </section>
}
