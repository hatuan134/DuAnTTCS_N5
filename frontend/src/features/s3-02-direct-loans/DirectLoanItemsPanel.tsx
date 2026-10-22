import { InlineDeposits } from '../deposits/DepositPage'
import TimedFieldMessage from '../../components/ui/TimedFieldMessage'
import FeedbackAlert from '../../components/ui/FeedbackAlert'
import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { Barcode, BookOpen, Search, User } from 'lucide-react'
import Button from '../../components/ui/Button'
import Card from '../../components/ui/Card'
import Input from '../../components/ui/Input'
import { getApiErrorMessage } from '../s1-02-user-management/accountService'
import { formatLoanTimestamp } from '../s3-01-loans/loanService'
import { directLoanService } from './directLoanService'
import type { BookCopyLocation, BookSearchItem, DirectLoanItem, DirectLoanResult, ReaderLoanEligibility } from './directLoanService'

interface DraftRow {
  id: number
  barcode: string
  item: DirectLoanItem | null
  error: string
  checking: boolean
}

type SearchMethod = 'barcode' | 'title' | 'author'

const ALLOWED_OVERRIDES = new Set(['LOAN_LIMIT_REACHED', 'LOAN_DRAFT_LIMIT_EXCEEDED',
  'LOAN_OVERDUE_UNRETURNED', 'LOAN_UNPAID_FEES'])

export default function DirectLoanItemsPanel({ reader, onCreated, onLockChange, onNewLoan, isManager = false, requireOverride = false }: {
  reader: ReaderLoanEligibility
  isManager?: boolean
  requireOverride?: boolean
  onCreated?: (result: DirectLoanResult) => void
  onLockChange?: (locked: boolean) => void
  onNewLoan?: () => void
}) {
  const [barcode, setBarcode] = useState('')
  const [searchMethod, setSearchMethod] = useState<SearchMethod>('barcode')
  const [searchQuery, setSearchQuery] = useState('')
  const [searchResults, setSearchResults] = useState<BookSearchItem[] | null>(null)
  const [searchingBooks, setSearchingBooks] = useState(false)
  const [searchError, setSearchError] = useState('')
  const [bookCopiesMap, setBookCopiesMap] = useState<Record<number, BookCopyLocation[]>>({})
  const [loadingCopiesBookId, setLoadingCopiesBookId] = useState<number | null>(null)

  const [items, setItems] = useState<DraftRow[]>([])
  const [limit, setLimit] = useState(reader.remainingBooks)
  const allowedByPolicy = reader.eligible || (reader.blockReasons?.length ?? 0) > 0
    && (reader.blockReasons ?? []).every((r) => ALLOWED_OVERRIDES.has(r.code))
  const [overrideRequested, setOverrideRequested] = useState(false)
  const [overrideReason, setOverrideReason] = useState('')
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [actionError, setActionError] = useState('')
  const [loading, setLoading] = useState(false)
  const [confirmationError, setConfirmationError] = useState('')
  const [awaitingResult, setAwaitingResult] = useState(false)
  const [completed, setCompleted] = useState<DirectLoanResult | null>(null)
  const submitted = useRef<{ id: string; barcodes: string[] } | null>(null)
  const [editingId, setEditingId] = useState<number | null>(null)
  const active = useRef(true)
  const inFlight = useRef(false)
  const nextId = useRef(0)
  const form = useRef<HTMLFormElement>(null)
  const validItems = items.filter((row) => row.item !== null)
  const atLimit = validItems.length >= limit
  const frozen = loading || awaitingResult || completed !== null
  const bypassDraft = isManager && allowedByPolicy && overrideRequested
  const blocked = frozen || (!reader.eligible && !bypassDraft) || (atLimit && !bypassDraft)
    || validItems.length >= 10
  const unresolved = items.filter((row) => row.item === null).length
  const hasViolation = !reader.eligible || validItems.length > limit
  const canConfirm = (requireOverride ? overrideRequested && bypassDraft && hasViolation :
    (overrideRequested ? bypassDraft && hasViolation : reader.eligible && validItems.length <= limit))
    && (!bypassDraft || !!overrideReason.trim())
    && validItems.length > 0 && unresolved === 0 && !loading
    && editingId === null && !barcode.trim() && !completed
  const editingIndex = items.findIndex((row) => row.id === editingId)

  function focusBarcode() {
    form.current?.querySelector<HTMLInputElement>('input')?.focus()
  }

  function switchSearchMethod(method: SearchMethod) {
    setSearchMethod(method)
    setError('')
    setNotice('')
    setSearchError('')
    if (method === 'barcode') {
      window.setTimeout(() => focusBarcode(), 0)
    }
  }

  useEffect(() => {
    active.current = true
    focusBarcode()
    return () => { active.current = false }
  }, [])

  async function processAddBarcode(codeToAdd: string) {
    if (inFlight.current || awaitingResult || completed) return
    setError(''); setNotice(''); setActionError('')
    const code = codeToAdd.trim()
    if (!code || code.length > 100) {
      setError('Vui lòng nhập mã vạch từ 1 đến 100 ký tự.')
      return
    }
    if (items.some((row) => row.id !== editingId && row.barcode === code)) {
      setError('Mã vạch này đã có trong lượt mượn. Mỗi bản sao chỉ được thêm một lần.')
      return
    }
    if (blocked) {
      setError(reader.eligible
        ? `Không thể thêm sách: lượt mượn sẽ vượt giới hạn ${limit} sách. Vui lòng xóa bớt dòng.`
        : reader.message)
      return
    }
    const rowId = editingId ?? ++nextId.current
    submitted.current = null
    setConfirmationError('')
    const pending: DraftRow = { id: rowId, barcode: code, item: null, error: '', checking: true }
    setItems((rows) => editingId === null ? [...rows, pending] : rows.map((row) => row.id === rowId ? pending : row))
    setBarcode('')
    setEditingId(null)
    inFlight.current = true
    setLoading(true)
    try {
      // Failed lines never consume quota or enter the server's valid draft.
      const item = await directLoanService.previewItem(reader.cardNumber, code,
        validItems.map((row) => row.barcode), bypassDraft)
      if (!active.current) return
      setLimit(item.remainingBooks)
      setItems((rows) => rows.map((row) => row.id === rowId ? { ...row, item, checking: false } : row))
      setNotice(`Đã kiểm tra sách ${item.bookTitle} (${code}).`)
    } catch (e: unknown) {
      if (active.current) {
        const message = getApiErrorMessage(e, 'Không kiểm tra được sách. Vui lòng sửa hoặc kiểm tra lại dòng này.')
        setItems((rows) => rows.map((row) => row.id === rowId ? { ...row, error: message, checking: false } : row))
        setActionError(`Không thêm được mã vạch ${code}. Vui lòng xem lỗi tại dòng sách để sửa hoặc thử lại.`)
      }
    } finally {
      inFlight.current = false
      if (active.current) {
        setLoading(false)
        if (searchMethod === 'barcode') focusBarcode()
      }
    }
  }

  async function handleBookSearch() {
    const query = searchQuery.trim()
    if (!query) {
      setSearchError(searchMethod === 'title' ? 'Vui lòng nhập tên sách để tìm kiếm.' : 'Vui lòng nhập tên tác giả để tìm kiếm.')
      return
    }
    setSearchError('')
    setSearchingBooks(true)
    try {
      const data = (await directLoanService.searchBooks?.(query)) ?? []
      let sorted = [...data]
      const lower = query.toLowerCase()
      if (searchMethod === 'title') {
        sorted.sort((a, b) => {
          const aMatch = a.title.toLowerCase().includes(lower) ? 1 : 0
          const bMatch = b.title.toLowerCase().includes(lower) ? 1 : 0
          return bMatch - aMatch
        })
      } else if (searchMethod === 'author') {
        sorted.sort((a, b) => {
          const aAuthor = (a.authors?.map(x => x.name).join(' ') || a.authorName || '').toLowerCase()
          const bAuthor = (b.authors?.map(x => x.name).join(' ') || b.authorName || '').toLowerCase()
          const aMatch = aAuthor.includes(lower) ? 1 : 0
          const bMatch = bAuthor.includes(lower) ? 1 : 0
          return bMatch - aMatch
        })
      }
      setSearchResults(sorted)
      const firstAvailable = sorted.find((b) => b.availableCount > 0)
      if (firstAvailable) {
        void loadCopiesForBook(firstAvailable.id)
      }
    } catch (e: unknown) {
      setSearchError(getApiErrorMessage(e, 'Không thể tìm kiếm sách. Vui lòng thử lại.'))
      setSearchResults([])
    } finally {
      setSearchingBooks(false)
    }
  }

  async function loadCopiesForBook(bookId: number) {
    if (bookCopiesMap[bookId]) return
    setLoadingCopiesBookId(bookId)
    try {
      const copies = (await directLoanService.getAvailableCopies?.(bookId)) ?? []
      setBookCopiesMap((prev) => ({ ...prev, [bookId]: copies }))
    } catch {
      setBookCopiesMap((prev) => ({ ...prev, [bookId]: [] }))
    } finally {
      setLoadingCopiesBookId(null)
    }
  }

  async function add(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (searchMethod === 'barcode') {
      await processAddBarcode(barcode)
    } else {
      await handleBookSearch()
    }
  }

  function remove(row: DraftRow) {
    if (inFlight.current || awaitingResult || completed) return
    submitted.current = null
    setConfirmationError('')
    setItems((rows) => rows.filter((item) => item.id !== row.id))
    if (editingId === row.id) { setEditingId(null); setBarcode('') }
    setError(''); setActionError('')
    setNotice(`Đã xóa mã vạch ${row.barcode} khỏi lượt mượn.`)
    focusBarcode()
  }

  function edit(row: DraftRow) {
    if (inFlight.current || awaitingResult || completed || row.item !== null) return
    setSearchMethod('barcode')
    setEditingId(row.id); setBarcode(row.barcode); setError(''); setNotice('')
    focusBarcode()
  }

  function cancelEdit() {
    if (inFlight.current) return
    setEditingId(null); setBarcode(''); setError(''); setNotice('')
    focusBarcode()
  }

  async function confirmDraft() {
    if (!canConfirm || inFlight.current) return
    inFlight.current = true
    setLoading(true); setConfirmationError(''); setNotice(''); setActionError('')
    onLockChange?.(true)
    let keepLocked = false
    try {
      // Preserve the exact key/payload on retry after a timeout or lost response.
      if (!submitted.current) submitted.current = {
        id: crypto.randomUUID(), barcodes: validItems.map((row) => row.barcode),
      }
      const result = await directLoanService.confirm(reader.cardNumber, submitted.current.barcodes,
        submitted.current.id, bypassDraft, overrideReason)
      if (!active.current) return
      setCompleted(result); setAwaitingResult(false)
      setNotice(`Đã ghi toàn bộ ${result.loan.items.length} sách vào phiếu mượn. ${result.message}`)
      onCreated?.(result)
    } catch (e: unknown) {
      if (!active.current) return
      const response = (e as { response?: { status?: number; data?: { code?: string } } } | null)?.response
      keepLocked = !response || ((response.status ?? 500) >= 500 && response.data?.code !== 'DIRECT_LOAN_SAVE_FAILED')
      setAwaitingResult(keepLocked)
      setConfirmationError(keepLocked
        ? 'Chưa xác định được kết quả xác nhận. Giữ nguyên lượt và nhấn Xác nhận lượt mượn để kiểm tra lại; hệ thống sẽ không tạo phiếu trùng.'
        : `Không thể ghi trọn vẹn lượt mượn. ${getApiErrorMessage(e, 'Vui lòng kiểm tra lại thẻ và sách trước khi thử lại.')}`)
    } finally {
      inFlight.current = false
      if (active.current) { setLoading(false); onLockChange?.(keepLocked) }
    }
  }

  if (completed) return <Card className="p-4 sm:p-6">
    {notice && <FeedbackAlert message={notice} tone="success" onDismiss={() => setNotice('')} />}
    <h3 className="font-semibold text-slate-900">Thông tin phiếu mượn · {completed.loan.items.length} sách</h3>
    <dl className="mt-5 grid gap-4 sm:grid-cols-2">
      <div className="min-w-0"><dt className="text-sm text-slate-500">Số phiếu mượn</dt><dd className="mt-1 break-all font-mono text-sm font-semibold text-slate-900">{completed.loan.loanNumber}</dd></div>
      <div className="min-w-0"><dt className="text-sm text-slate-500">Người lập phiếu</dt><dd className="mt-1 break-words font-semibold text-slate-900">{completed.loan.createdByName}</dd></div>
    </dl>
    <ul className="mt-5 divide-y divide-slate-200 rounded-xl border border-slate-200 px-4">
      {completed.loan.items.map((item) => <li key={item.id} className="flex flex-col gap-2 py-4 sm:flex-row sm:justify-between">
        <div className="min-w-0"><p className="break-words font-medium text-slate-900">{item.bookTitle}</p>
          <p className="mt-1 break-all font-mono text-xs text-slate-500">{item.barcode}</p>
          <p className="mt-1 text-sm text-slate-600">Ngày mượn: {formatLoanTimestamp(item.borrowedAt, true)} · Hạn trả: {formatLoanTimestamp(item.dueAt, true)}</p>
        </div>
        <span className="self-start rounded-lg bg-blue-50 px-2 py-1 text-xs font-semibold text-blue-800">Đang mượn</span>
      </li>)}
    </ul>
    <div className="mt-5 flex flex-wrap items-center gap-3">
      {onNewLoan && <Button type="button" onClick={onNewLoan}>Bắt đầu lượt mới</Button>}
      <a className="rounded-xl border border-slate-300 px-4 py-3 text-sm font-semibold text-slate-700 hover:bg-slate-50" href={`/loans/${completed.loan.id}`}>Xem chi tiết phiếu</a>
    </div>
  </Card>

  return <Card className="p-4 sm:p-6">
    <InlineDeposits mode="collect" card={reader.cardNumber} barcodes={validItems.map(row => row.barcode)} disabled={frozen} />
    <div className="mb-4 flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
      <div className="min-w-0">
        <h3 className="text-lg font-semibold text-slate-900">Sách trong lượt mượn</h3>
        <p className="mt-1 text-sm leading-6 text-slate-500">Tìm kiếm bản sao bằng mã vạch, tên sách hoặc tác giả để thêm vào lượt mượn.</p>
      </div>
      <p role="status" aria-live="polite" className="shrink-0 self-start rounded-xl bg-blue-50 px-3 py-2 text-sm font-semibold text-blue-800">
        Dự kiến mượn: {validItems.length} / {limit} sách
      </p>
    </div>

    <div className="mb-3 flex flex-wrap items-center gap-2">
      <span className="text-xs font-semibold text-slate-600">Tìm kiếm theo:</span>
      <div className="inline-flex rounded-xl bg-slate-100 p-1" role="tablist" aria-label="Phương thức tìm kiếm sách">
        <button
          type="button"
          role="tab"
          aria-selected={searchMethod === 'barcode'}
          className={`flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-xs font-medium transition ${searchMethod === 'barcode' ? 'bg-white font-semibold text-blue-700 shadow-xs' : 'text-slate-600 hover:text-slate-900'}`}
          onClick={() => switchSearchMethod('barcode')}
        >
          <Barcode size={14} />
          Mã vạch
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={searchMethod === 'title'}
          className={`flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-xs font-medium transition ${searchMethod === 'title' ? 'bg-white font-semibold text-blue-700 shadow-xs' : 'text-slate-600 hover:text-slate-900'}`}
          onClick={() => switchSearchMethod('title')}
        >
          <BookOpen size={14} />
          Tên sách
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={searchMethod === 'author'}
          className={`flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-xs font-medium transition ${searchMethod === 'author' ? 'bg-white font-semibold text-blue-700 shadow-xs' : 'text-slate-600 hover:text-slate-900'}`}
          onClick={() => switchSearchMethod('author')}
        >
          <User size={14} />
          Tác giả
        </button>
      </div>
    </div>

    <form ref={form} onSubmit={add} noValidate className="space-y-3">
      {editingId !== null && <div className="rounded-xl border border-blue-200 bg-blue-50 p-3 text-sm text-blue-800">
        Đang sửa mã vạch ở dòng {editingIndex + 1}. Nhập mã đúng rồi nhấn Kiểm tra lại.
      </div>}
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start">
        <div className="min-w-0 flex-1">
          {searchMethod === 'barcode' ? (
            <Input id="direct-loan-barcode" label="Mã vạch sách" required autoComplete="off" maxLength={100}
              placeholder="Nhập mã vạch của bản sao" value={barcode} error={error}
              disabled={(!reader.eligible && !bypassDraft) || frozen}
              onChange={(event) => { setBarcode(event.target.value); setError(''); setNotice('') }} />
          ) : (
            <Input id="direct-loan-barcode"
              label={searchMethod === 'title' ? 'Tên sách' : 'Tên tác giả'}
              required autoComplete="off" maxLength={100}
              placeholder={searchMethod === 'title' ? 'Nhập tên sách để tìm kiếm…' : 'Nhập tên tác giả để tìm kiếm…'}
              value={searchQuery} error={searchError}
              disabled={(!reader.eligible && !bypassDraft) || frozen}
              onChange={(event) => { setSearchQuery(event.target.value); setSearchError('') }} />
          )}
        </div>
        <div className="flex flex-wrap gap-2 sm:mt-7">
          {searchMethod === 'barcode' ? (
            <>
              <Button type="submit" loading={loading && submitted.current === null} disabled={blocked}>{editingId === null ? 'Thêm sách' : 'Kiểm tra lại'}</Button>
              {editingId !== null && <Button type="button" variant="secondary" disabled={loading} onClick={cancelEdit}>Hủy sửa</Button>}
            </>
          ) : (
            <Button type="submit" loading={searchingBooks} disabled={(!reader.eligible && !bypassDraft) || frozen || !searchQuery.trim()}>
              <Search size={16} className="mr-1.5" />
              Tìm sách
            </Button>
          )}
        </div>
      </div>
      <div aria-live="polite" aria-atomic="true">
        {loading && submitted.current === null && <p role="status" className="text-sm text-blue-700">Đang tìm sách theo mã vạch…</p>}
        {searchingBooks && <p role="status" className="text-sm text-blue-700">Đang tìm kiếm đầu sách…</p>}
        {actionError && <FeedbackAlert message={actionError} tone="error" onDismiss={() => setActionError('')} />}
        {notice && <FeedbackAlert message={notice} tone="success" onDismiss={() => setNotice('')} />}
      </div>
    </form>

    {/* Kết quả tìm kiếm sách theo Tên sách hoặc Tác giả */}
    {searchMethod !== 'barcode' && searchResults !== null && (
      <div className="mt-4 space-y-3 rounded-xl border border-slate-200 bg-slate-50/80 p-4">
        <div className="flex items-center justify-between gap-2 border-b border-slate-200 pb-2">
          <span className="text-xs font-semibold text-slate-700">
            Kết quả tìm kiếm ({searchResults.length} đầu sách phù hợp):
          </span>
          <button
            type="button"
            className="text-xs text-slate-500 hover:text-slate-800"
            onClick={() => setSearchResults(null)}
          >
            Đóng kết quả
          </button>
        </div>
        {searchResults.length === 0 ? (
          <p className="py-3 text-center text-sm text-slate-500">
            Không tìm thấy đầu sách nào phù hợp với từ khóa &ldquo;{searchQuery.trim()}&rdquo;.
          </p>
        ) : (
          <div className="max-h-80 space-y-2.5 overflow-y-auto pr-1">
            {searchResults.map((book) => {
              const authorStr = book.authors?.length
                ? book.authors.map((a) => a.name).join(', ')
                : (book.authorName || 'Không rõ tác giả')
              const isLoaded = Boolean(bookCopiesMap[book.id])
              const isLoadingCopies = loadingCopiesBookId === book.id
              const copies = bookCopiesMap[book.id] || []
              const hasAvailable = book.availableCount > 0

              return (
                <div key={book.id} className="rounded-xl border border-slate-200 bg-white p-3 shadow-xs">
                  <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
                    <div className="min-w-0 flex-1">
                      <p className="font-semibold text-slate-900 break-words">{book.title}</p>
                      <p className="mt-0.5 text-xs text-slate-600 break-words">
                        Tác giả: <span className="font-medium text-slate-800">{authorStr}</span>
                        {book.categoryName && ` · Thể loại: ${book.categoryName}`}
                        {book.publicationYear && ` · Năm: ${book.publicationYear}`}
                      </p>
                      <div className="mt-1 flex items-center gap-2">
                        <span className={`inline-block rounded-md px-2 py-0.5 text-xs font-semibold ${hasAvailable ? 'border border-emerald-200 bg-emerald-50 text-emerald-700' : 'border border-red-200 bg-red-50 text-red-600'}`}>
                          {hasAvailable ? `${book.availableCount} bản khả dụng` : 'Hết bản sẵn sàng'}
                        </span>
                        {book.isbn && <span className="font-mono text-xs text-slate-400">ISBN: {book.isbn}</span>}
                      </div>
                    </div>
                    {hasAvailable && (
                      <div className="shrink-0 self-start">
                        {!isLoaded && !isLoadingCopies && (
                          <Button
                            type="button"
                            variant="secondary"
                            size="sm"
                            onClick={() => void loadCopiesForBook(book.id)}
                          >
                            Xem bản sao ({book.availableCount})
                          </Button>
                        )}
                        {isLoadingCopies && (
                          <span className="text-xs text-blue-600">Đang tải bản sao…</span>
                        )}
                      </div>
                    )}
                  </div>

                  {/* Danh sách các bản sao khả dụng */}
                  {isLoaded && (
                    <div className="mt-3 border-t border-slate-100 pt-2.5">
                      <p className="mb-2 text-xs font-medium text-slate-500">Bản sao sẵn sàng mượn:</p>
                      {copies.length === 0 ? (
                        <p className="text-xs text-slate-400">Không có bản sao khả dụng tại thời điểm này.</p>
                      ) : (
                        <div className="grid gap-2 sm:grid-cols-2">
                          {copies.map((c) => {
                            const isAdded = items.some((row) => row.barcode === c.barcode)
                            return (
                              <div
                                key={c.copyId}
                                className={`flex items-center justify-between gap-2 rounded-lg border p-2 text-xs transition ${isAdded ? 'border-slate-200 bg-slate-50 opacity-60' : 'border-blue-100 bg-blue-50/40 hover:bg-blue-50'}`}
                              >
                                <div className="min-w-0">
                                  <span className="break-all font-mono font-bold text-slate-800">{c.barcode}</span>
                                  <span className="block truncate text-slate-500">
                                    {c.warehouseName} {c.shelfName ? `· ${c.shelfName}` : `· Kệ ${c.shelfCode}`}
                                  </span>
                                </div>
                                {isAdded ? (
                                  <span className="shrink-0 font-medium text-emerald-700">✓ Đã thêm</span>
                                ) : (
                                  <Button
                                    type="button"
                                    size="sm"
                                    disabled={blocked || !c.barcode || frozen}
                                    onClick={() => { if (c.barcode) void processAddBarcode(c.barcode) }}
                                  >
                                    Chọn mượn
                                  </Button>
                                )}
                              </div>
                            )
                          })}
                        </div>
                      )}
                    </div>
                  )}
                </div>
              )
            })}
          </div>
        )}
      </div>
    )}

    {(!reader.eligible || atLimit) && <p role="alert" className="mt-4 rounded-xl border border-red-200 bg-red-50 p-4 text-sm leading-6 text-red-700">
      {reader.eligible
        ? `Đã đạt giới hạn ${limit} sách hợp lệ trong lượt này. Không thể thêm sách vì sẽ vượt số sách bạn đọc còn được mượn. ${isManager ? 'Quản lý có thể xem xét bỏ qua một lần kèm lý do.' : 'Xóa một dòng hợp lệ để nhập sách khác.'}`
        : reader.message}
    </p>}
    {items.length === 0
      ? <p className="mt-5 rounded-xl border border-dashed border-slate-300 p-5 text-sm leading-6 text-slate-500">Chưa có sách trong lượt mượn. {reader.eligible ? 'Nhập mã vạch để thêm sách đầu tiên.' : 'Bạn đọc cần đủ điều kiện mượn trước khi thêm sách.'}</p>
      : <div className="mt-5 overflow-hidden rounded-xl border border-slate-200">
        <table className="data-table w-full table-fixed text-sm">
          <thead className="bg-slate-50 text-slate-600"><tr>
            <th scope="col" className="w-10 px-2 py-3 text-center sm:w-14">STT</th>
            <th scope="col" className="w-[27%] px-2 py-3 sm:px-3">Mã vạch</th>
            <th scope="col" className="px-2 py-3 sm:px-3">Sách / Kết quả kiểm tra</th>
            <th scope="col" className="w-20 px-2 py-3 text-center sm:w-28">Thao tác</th>
          </tr></thead>
          <tbody className="divide-y divide-slate-200" aria-live="polite">
            {items.map((row, index) => <tr key={row.id} className={`align-top ${row.error ? 'bg-red-50/60' : ''}`}>
              <td className="px-2 py-4 text-center text-slate-500">{index + 1}</td>
              <td className="break-all px-2 py-4 font-mono text-xs text-slate-700 sm:px-3 sm:text-sm">{row.barcode}</td>
              <td className="px-2 py-4 [overflow-wrap:anywhere] sm:px-3">
                {row.item && <p className="mb-2 break-words font-medium text-slate-900">{row.item.bookTitle}</p>}
                <span className={`inline-block max-w-full rounded-lg px-2 py-1 text-xs font-semibold ${row.item ? 'bg-emerald-100 text-emerald-800' : row.checking ? 'bg-blue-100 text-blue-800' : 'bg-red-100 text-red-800'}`}>
                  {row.item ? 'Hợp lệ · Sẵn sàng' : row.checking ? 'Đang kiểm tra' : editingId === row.id ? 'Đang sửa · Chưa hợp lệ' : 'Chưa đủ điều kiện'}
                </span>
                {row.error && <TimedFieldMessage className="mt-2 break-words text-xs leading-5 text-red-700">{row.error}</TimedFieldMessage>}
              </td>
              <td className="px-1 py-3 text-center sm:px-3">
                <div className="flex flex-col items-center gap-1">
                  {!row.item && <Button type="button" variant="secondary" size="sm"
                    aria-label={`Sửa mã vạch ${row.barcode}`} disabled={frozen} onClick={() => edit(row)}>Sửa</Button>}
                  <Button type="button" variant="ghost" size="sm" className="text-red-700 hover:bg-red-50 hover:text-red-800"
                    aria-label={`Xóa sách có mã vạch ${row.barcode}`} disabled={frozen} onClick={() => remove(row)}>Xóa</Button>
                </div>
              </td>
            </tr>)}
          </tbody>
        </table>
      </div>}
    {isManager && (!reader.eligible || atLimit || overrideRequested) && <section
      className="mt-5 space-y-3 rounded-xl border border-amber-300 bg-amber-50 p-4" aria-label="Bỏ qua chặn một lần">
      <p className="font-semibold text-amber-950">Quản lý thư viện — xem xét bỏ qua lần chặn</p>
      <p className="text-sm text-amber-950">Chỉ áp dụng cho một lượt mượn. Vi phạm gốc vẫn còn và lần mượn sau phải kiểm tra lại.</p>
      {!allowedByPolicy && <p role="alert" className="text-sm text-red-800">Có điều kiện bắt buộc không được bỏ qua; cần xử lý vi phạm trước.</p>}
      <label className="flex items-start gap-2 text-sm font-semibold text-slate-900">
        <input type="checkbox" checked={overrideRequested} disabled={frozen || !allowedByPolicy}
          onChange={(e) => { setOverrideRequested(e.target.checked); setConfirmationError(''); submitted.current = null }} />
        Xác nhận xem xét bỏ qua các vi phạm để hoàn thành đúng lượt này
      </label>
      {overrideRequested && <label className="block text-sm font-medium text-slate-800">Lý do bắt buộc (tối đa 500 ký tự)
        <textarea className="mt-2 block min-h-24 w-full rounded-xl border border-slate-300 bg-white p-3"
          value={overrideReason} maxLength={500} disabled={frozen} required
          onChange={(e) => { setOverrideReason(e.target.value); submitted.current = null }}
          placeholder="Ghi lý do cụ thể và căn cứ quyết định…" />
        {!overrideReason.trim() && <span className="mt-1 block text-xs text-red-700">Phải nhập lý do trước khi xác nhận bỏ qua.</span>}
      </label>}
      {bypassDraft && validItems.length > limit && <p className="text-sm text-amber-900">
        Lượt này đang vượt hạn mức {limit} sách; hệ thống sẽ lưu cả vi phạm vượt hạn mức vào nhật ký.
      </p>}
    </section>}
    <div className="mt-4 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
      <p role="status" className={`min-w-0 text-sm leading-6 ${unresolved ? 'text-red-700' : 'text-slate-600'}`}>
        {unresolved ? `Còn ${unresolved} dòng chưa hợp lệ. Sửa hoặc xóa các dòng này trước khi xác nhận; vẫn có thể nhập sách khác.`
          : barcode.trim() ? 'Còn mã vạch chưa được kiểm tra. Thêm sách hoặc xóa nội dung ô nhập trước khi xác nhận.'
          : validItems.length ? 'Tất cả các dòng đã nhập đều hợp lệ.' : 'Danh sách cần có ít nhất một sách hợp lệ.'}
      </p>
      <Button type="button" className="shrink-0 self-start" loading={loading && submitted.current !== null}
        disabled={!canConfirm} onClick={confirmDraft}>Xác nhận lượt mượn</Button>
    </div>
    {confirmationError && <FeedbackAlert message={confirmationError} tone="error" onDismiss={() => setConfirmationError('')} className="mt-4" />}
    <p role="status" className="mt-4 text-xs leading-5 text-slate-500">{loading && submitted.current
      ? 'Đang kiểm tra lại thẻ, giới hạn và toàn bộ sách để ghi lượt mượn…'
      : awaitingResult ? 'Giữ nguyên mã thẻ và danh sách cho đến khi xác định được kết quả.'
      : 'Khi xác nhận, hệ thống kiểm tra lại toàn bộ điều kiện và ghi tất cả sách trong một phiếu. Nếu có lỗi khi ghi, toàn bộ lượt sẽ được hủy.'}</p>
  </Card>
}
