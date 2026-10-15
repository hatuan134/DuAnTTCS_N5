import { useRef, useState, type FormEvent } from 'react'
import { isAxiosError } from 'axios'
import Button from '../../components/ui/Button'
import Card from '../../components/ui/Card'
import ConfirmActionDialog from '../../components/ui/ConfirmActionDialog'
import FeedbackAlert from '../../components/ui/FeedbackAlert'
import Input from '../../components/ui/Input'
import LoadingState from '../../components/ui/LoadingState'
import PageHeader from '../../components/ui/PageHeader'
import { getCurrentUser } from '../../core/auth/authStorage'
import { getApiErrorMessage } from '../s1-02-user-management/accountService'
import { formatLoanTimestamp } from '../s3-01-loans/loanService'
import { formatVnd } from '../s3-07-returns/ReturnFeeDetails'
import { feePaymentService, feeTypeLabel, parsePartialAmount, previewFeeAllocation, type FeeCollection, type FeeReader, type FeeReceipt, type FullFeePaymentRequest, type PartialFeePaymentRequest } from './feePaymentService'
import FeeReceiptDialog from './FeeReceiptDialog'

export default function FeeCollectionPage() {
  if (!['LIBRARIAN', 'ADMIN'].includes(getCurrentUser()?.role ?? '')) return <p role="alert" className="rounded-xl bg-red-50 p-4 text-sm text-red-700">Chỉ Thủ thư được thu phí và xem, in lại biên lai.</p>
  return <FeeCollector />
}

export function FeeCollector() {
  const [query, setQuery] = useState('')
  const [readers, setReaders] = useState<FeeReader[]>([])
  const [searched, setSearched] = useState(false)
  const [appliedQuery, setAppliedQuery] = useState('')
  const [searching, setSearching] = useState(false)
  const [loading, setLoading] = useState(false)
  const [collection, setCollection] = useState<FeeCollection | null>(null)
  const [confirming, setConfirming] = useState(false)
  const [collecting, setCollecting] = useState(false)
  const [mode, setMode] = useState<'FULL' | 'PARTIAL'>('FULL')
  const [amountText, setAmountText] = useState('')
  const [uncertain, setUncertain] = useState(false)
  const [needsRefresh, setNeedsRefresh] = useState(false)
  const [receipt, setReceipt] = useState<FeeReceipt | null>(null)
  const [receiptLoading, setReceiptLoading] = useState(false)
  const [feedback, setFeedback] = useState<{ tone: 'success' | 'error'; message: string } | null>(null)
  const [loadError, setLoadErrorState] = useState('')
  function setLoadError(message: string) {
    setLoadErrorState(message)
    if (message) setFeedback({ tone: 'error', message })
  }
  const inFlight = useRef(false)
  const request = useRef<FullFeePaymentRequest | PartialFeePaymentRequest | null>(null)
  const version = useRef(0)
  const busy = searching || loading || collecting || receiptLoading
  const locked = busy || uncertain
  const amountInput = mode === 'PARTIAL' && collection ? parsePartialAmount(amountText, collection.totalOutstandingVnd) : null
  const plannedAmount = mode === 'FULL' ? collection?.totalOutstandingVnd ?? 0 : amountInput?.amount ?? 0
  const allocation = collection && mode === 'PARTIAL' ? previewFeeAllocation(collection.fees, plannedAmount) : []

  async function search(event: FormEvent) {
    event.preventDefault()
    const value = query.trim()
    if (locked || !value) return
    setSearching(true); setFeedback(null); setCollection(null); setReaders([]); setSearched(false); setLoadError(''); request.current = null
    const generation = ++version.current
    try {
      const results = await feePaymentService.search(value)
      if (generation === version.current) { setReaders(results); setSearched(true); setAppliedQuery(value) }
    } catch (error) { setFeedback({ tone: 'error', message: getApiErrorMessage(error, 'Không tìm được bạn đọc. Vui lòng thử lại.') }) }
    finally { setSearching(false) }
  }
  async function select(readerId: number) {
    if (inFlight.current || uncertain) return
    const generation = ++version.current
    setLoading(true); setCollection(null); setLoadError(''); setConfirming(false); setMode('FULL'); setAmountText(''); setNeedsRefresh(false); request.current = null
    try { const value = await feePaymentService.detail(readerId); if (generation === version.current) setCollection(value) }
    catch (error) { if (generation === version.current) setLoadError(getApiErrorMessage(error, 'Không tải được khoản phí. Vui lòng chọn lại bạn đọc.')) }
    finally { if (generation === version.current) setLoading(false) }
  }
  async function collect() {
    if (inFlight.current || !collection || collection.totalOutstandingVnd <= 0 || needsRefresh) return
    if (!request.current && (plannedAmount < 1 || amountInput?.error)) return
    const current = collection
    inFlight.current = true; setCollecting(true); setFeedback(null)
    try {
      // Keep both the payload and key on ambiguous failures; a retry cannot collect different cash.
      request.current ??= { requestKey: crypto.randomUUID(), expectedTotalVnd: current.totalOutstandingVnd,
        debtToken: current.debtToken, ...(mode === 'PARTIAL' ? { amountVnd: plannedAmount } : {}) }
      const saved = 'amountVnd' in request.current
        ? await feePaymentService.collectPartial(current.reader.id, request.current)
        : await feePaymentService.collect(current.reader.id, request.current)
      setConfirming(false); setReceipt(saved); request.current = null; setUncertain(false); setAmountText('')
      const after = saved.debtAfterVnd ?? 0
      const updatedFees = current.fees.map(fee => {
        const item = saved.items.find(value => value.feeId === fee.id)
        if (!item) return fee
        const paid = item.previouslyPaidVnd + item.collectedAmountVnd
        return { ...fee, paidAmountVnd: paid, outstandingAmountVnd: fee.originalAmountVnd - paid - fee.waivedAmountVnd }
      }).filter(fee => fee.outstandingAmountVnd > 0)
      setCollection({ ...current, fees: updatedFees, totalOutstandingVnd: after, hasDebt: after > 0, debtToken: '',
        payments: [saved, ...current.payments.filter(p => p.id !== saved.id)] })
      setFeedback({ tone: 'success', message: `Đã thu ${formatVnd(saved.totalAmountVnd)}. ${after > 0 ? `Còn nợ ${formatVnd(after)}; giữ trạng thái Nợ phí.` : 'Bạn đọc không còn nợ phí tại thời điểm thu.'}` })
      setNeedsRefresh(true)
      try { setCollection(await feePaymentService.detail(current.reader.id)); setNeedsRefresh(false) }
      catch { setFeedback({ tone: 'success', message: `Đã lưu biên lai ${saved.receiptNumber}, thu ${formatVnd(saved.totalAmountVnd)}, còn nợ ${formatVnd(after)} tại thời điểm thu. Bấm Tải lại để cập nhật khoản nợ trước lần thu tiếp theo.` }) }
    } catch (error) {
      setConfirming(false); setUncertain(true)
      const code = isAxiosError(error) ? error.response?.data?.code : null
      setFeedback({ tone: 'error', message: getApiErrorMessage(error, 'Chưa xác nhận được kết quả thu tiền. Tải lại khoản nợ hoặc thử xác nhận lại để kiểm tra.') })
      if (['FEE_DEBT_CHANGED', 'NO_OUTSTANDING_FEES', 'PAYMENT_REQUEST_REUSED', 'INVALID_PAYMENT_AMOUNT', 'PAYMENT_AMOUNT_EXCEEDS_DEBT', 'INVALID_PAYMENT_REQUEST', 'VALIDATION_ERROR'].includes(code)) {
        request.current = null; setUncertain(false)
        await selectAfterConflict(current.reader.id)
      }
    } finally { inFlight.current = false; setCollecting(false) }
  }
  async function selectAfterConflict(readerId: number) {
    try { setCollection(await feePaymentService.detail(readerId)) }
    catch { setCollection(null); setLoadError('Không tải được số dư mới. Vui lòng chọn lại bạn đọc trước khi thu phí.') }
  }
  async function preview(id: number) {
    if (busy) return
    setReceiptLoading(true)
    try { setReceipt(await feePaymentService.receipt(id)) }
    catch (error) { setFeedback({ tone: 'error', message: getApiErrorMessage(error, 'Không tải được biên lai. Giao dịch đã thu vẫn được giữ nguyên.') }) }
    finally { setReceiptLoading(false) }
  }

  return <div className="min-w-0 space-y-5">
    <PageHeader title="Thu phí thư viện" description="Tìm bạn đọc, kiểm tra khoản nợ, thu đủ hoặc thu một phần và in biên lai cho từng lần thu." />
    {feedback && <FeedbackAlert {...feedback} onDismiss={() => setFeedback(null)} />}
    <Card className="p-5">
      <form onSubmit={event => void search(event)} className="flex flex-col items-end gap-3 sm:flex-row">
        <div className="min-w-0 flex-1 w-full"><Input label="Mã thẻ hoặc thông tin định danh" required maxLength={255} value={query}
          placeholder="Mã thẻ, mã bạn đọc, họ tên, email, số điện thoại" disabled={locked} onChange={event => setQuery(event.target.value)} /></div>
        <Button type="submit" loading={searching} disabled={locked || !query.trim()}>Tìm bạn đọc</Button>
      </form>
      {searched && <p className="mt-4 break-words text-sm text-slate-500">Kết quả cho “{appliedQuery}”: {readers.length > 20 ? 'hơn 20 bạn đọc; hãy nhập thông tin cụ thể hơn' : `${readers.length} bạn đọc`}.</p>}
      {searched && readers.length === 0 && <p className="mt-3 text-sm text-slate-600">Không tìm thấy bạn đọc. Kiểm tra mã thẻ hoặc thử email, số điện thoại.</p>}
      <div className="mt-4 grid gap-3 md:grid-cols-2">
        {readers.slice(0, 20).map(reader => <button key={reader.id} type="button" disabled={locked} onClick={() => void select(reader.id)}
          className={`min-w-0 rounded-xl border p-4 text-left transition hover:border-blue-400 disabled:opacity-50 ${collection?.reader.id === reader.id ? 'border-blue-500 bg-blue-50' : 'border-slate-200 bg-white'}`}>
          <span className="block break-words font-semibold text-slate-900">{reader.fullName}</span>
          <span className="mt-1 block break-all text-sm text-slate-600">Thẻ: {reader.cardNumber ?? 'Chưa cấp'} · Mã bạn đọc: {reader.memberCode ?? 'Chưa có'}</span>
          <span className="mt-1 block break-all text-xs text-slate-500">{reader.email}{reader.phone ? ` · ${reader.phone}` : ''}</span>
        </button>)}
      </div>
    </Card>
    {loading && <LoadingState />}
    {loadError && <p role="status" className="rounded-xl bg-red-50 p-4 text-sm text-red-700">Chưa tải được khoản phí. Chọn lại bạn đọc hoặc bấm Tải lại để thử lại.</p>}
    {collection && <>
      <Card className="p-5">
        <div className="flex flex-col justify-between gap-4 sm:flex-row">
          <div className="min-w-0"><h2 className="break-words text-lg font-bold text-slate-900">{collection.reader.fullName}</h2>
            <p className="mt-1 break-all text-sm text-slate-500">Mã thẻ: {collection.reader.cardNumber ?? 'Chưa cấp thẻ'}</p>
            <span className={`mt-3 inline-flex rounded-full px-3 py-1 text-sm font-semibold ${collection.hasDebt ? 'bg-red-50 text-red-700' : 'bg-emerald-50 text-emerald-700'}`}>
              {collection.hasDebt ? 'Nợ phí' : 'Không nợ phí'}
            </span>
            <p className="mt-2 text-xs text-slate-500">Gỡ nợ phí không thay đổi hạn thẻ hoặc các điều kiện mượn khác.</p>
          </div>
          <div className="sm:text-right"><p className="text-sm text-slate-500">Tổng số tiền đang nợ</p><p className={`mt-1 text-2xl font-bold ${collection.hasDebt ? 'text-red-700' : 'text-emerald-700'}`}>{formatVnd(collection.totalOutstandingVnd)}</p>
            <div className="mt-3 flex flex-wrap gap-2 sm:justify-end"><Button variant="secondary" disabled={locked} onClick={() => void select(collection.reader.id)}>Tải lại</Button>
              {collection.totalOutstandingVnd > 0 && <Button disabled={locked || needsRefresh || plannedAmount < 1 || Boolean(amountInput?.error)} loading={collecting} onClick={() => setConfirming(true)}>{mode === 'FULL' ? 'Thu đủ' : 'Thu một phần'}</Button>}
            </div>
          </div>
        </div>
        {uncertain && <div role="alert" className="mt-5 rounded-xl border border-amber-200 bg-amber-50 p-4">
          <p className="text-sm text-amber-900">Chưa xác nhận được kết quả lần thu vừa gửi. Xác nhận lại cùng giao dịch để kiểm tra và lấy biên lai; không nhận thêm tiền từ bạn đọc trong lúc này.</p>
          <Button className="mt-3" disabled={busy} onClick={() => void collect()}>Xác nhận lại giao dịch</Button>
        </div>}
        {collection.hasDebt && <section className="mt-5 rounded-xl border border-slate-200 bg-slate-50 p-4">
          <fieldset disabled={locked || needsRefresh} className="flex flex-wrap gap-x-6 gap-y-3">
            <legend className="mb-3 text-sm font-semibold text-slate-900">Cách thu tiền</legend>
            {(['FULL', 'PARTIAL'] as const).map(value => <label key={value} className="inline-flex items-center gap-2 text-sm text-slate-700">
              <input type="radio" name="fee-payment-mode" value={value} checked={mode === value}
                onChange={() => { setMode(value); setAmountText(''); request.current = null }} />
              {value === 'FULL' ? 'Thu đủ' : 'Thu một phần'}
            </label>)}
          </fieldset>
          {mode === 'PARTIAL' && <div className="mt-4 max-w-md">
            <Input label="Số tiền bạn đọc thực tế nộp (VND)" required inputMode="numeric" type="text" maxLength={16}
              value={amountText} placeholder="Ví dụ: 10000" disabled={locked || needsRefresh}
              error={amountText ? amountInput?.error : undefined} onChange={event => { setAmountText(event.target.value); request.current = null }} />
            <p className="mt-2 text-xs leading-5 text-slate-500">Tối thiểu 1 VND, chỉ nhập số nguyên. Trả theo mã khoản phí tăng dần, hết khoản trước mới chuyển khoản sau.</p>
          </div>}
          <dl className="mt-4 grid gap-3 text-sm sm:grid-cols-3" aria-live="polite">
            <div className="min-w-0"><dt className="text-slate-500">Nợ trước khi thu</dt><dd className="mt-1 break-words font-semibold">{formatVnd(collection.totalOutstandingVnd)}</dd></div>
            <div className="min-w-0"><dt className="text-slate-500">Dự kiến thu lần này</dt><dd className="mt-1 break-words font-semibold text-blue-700">{amountInput?.error || plannedAmount < 1 ? 'Chưa có số tiền hợp lệ' : formatVnd(plannedAmount)}</dd></div>
            <div className="min-w-0"><dt className="text-slate-500">Nợ sau khi thu</dt><dd className="mt-1 break-words font-semibold">{amountInput?.error || plannedAmount < 1 ? 'Chưa xác định' : formatVnd(collection.totalOutstandingVnd - plannedAmount)}</dd></div>
          </dl>
          {mode === 'PARTIAL' && allocation.length > 0 && <div className="mt-4 border-t border-slate-200 pt-3">
            <h3 className="text-sm font-semibold text-slate-900">Phân bổ dự kiến</h3>
            <ul className="mt-2 space-y-2 text-sm">{allocation.map(item => <li key={item.fee.id} className="flex flex-wrap justify-between gap-2">
              <span className="min-w-0 break-words">KP-{item.fee.id} · {feeTypeLabel(item.fee.feeType)}</span><strong>{formatVnd(item.collectedAmountVnd)}</strong>
            </li>)}</ul>
          </div>}
        </section>}
        <h3 className="mb-3 mt-6 font-semibold text-slate-900">Khoản phí chưa thanh toán hết</h3>
        {collection.fees.length === 0 ? <p className="rounded-xl bg-emerald-50 p-4 text-sm text-emerald-800">Số dư nợ bằng 0 VND. Không có khoản phí cần thu.</p>
          : <div className="grid gap-3">{collection.fees.map(fee => <section key={fee.id} className="min-w-0 rounded-xl border border-slate-200 p-4">
            <h4 className="font-semibold text-slate-900">KP-{fee.id} · {feeTypeLabel(fee.feeType)}</h4><p className="mt-1 break-words text-sm text-slate-500">{fee.description || 'Khoản phí thư viện'}</p>
            <dl className="mt-3 grid grid-cols-2 gap-3 text-sm lg:grid-cols-4">
              {[['Phí ban đầu', fee.originalAmountVnd], ['Đã thanh toán', fee.paidAmountVnd], ['Đã miễn giảm', fee.waivedAmountVnd], ['Còn nợ', fee.outstandingAmountVnd]].map(([label, amount]) =>
                <div key={label} className="min-w-0"><dt className="text-slate-500">{label}</dt><dd className={`mt-1 break-words font-semibold ${label === 'Còn nợ' ? 'text-red-700' : 'text-slate-900'}`}>{formatVnd(Number(amount))}</dd></div>)}
            </dl>
          </section>)}</div>}
      </Card>
      <Card className="p-5"><h2 className="font-semibold text-slate-900">Giao dịch đã thu và biên lai</h2>
        {receiptLoading && <p role="status" className="mt-3 text-sm text-slate-500">Đang tải biên lai...</p>}
        {collection.payments.length === 0 && <p className="mt-3 text-sm text-slate-500">Bạn đọc chưa có giao dịch thu phí được ghi nhận.</p>}
        <div className="mt-3 divide-y divide-slate-100">{collection.payments.map(payment => <div key={payment.id} className="flex flex-col justify-between gap-3 py-3 sm:flex-row sm:items-center">
          <div className="min-w-0"><p className="break-all text-sm font-semibold text-slate-900">{payment.receiptNumber} · {payment.transactionNumber}</p>
            <p className="mt-1 break-words text-sm text-slate-500">{formatLoanTimestamp(payment.collectedAt)} · {payment.collectorName}</p><p className="mt-1 text-sm font-semibold">{formatVnd(payment.totalAmountVnd)}</p><p className="mt-1 text-xs text-slate-500">Số dư sau lần thu: {formatVnd(payment.debtAfterVnd ?? 0)}</p></div>
          <Button variant="secondary" disabled={locked} onClick={() => void preview(payment.id)}>Xem / in lại biên lai</Button>
        </div>)}</div>
      </Card>
    </>}
    {confirming && collection && <ConfirmActionDialog title={mode === 'FULL' ? 'Xác nhận đã nhận đủ tiền' : 'Xác nhận đã nhận tiền thu một phần'}
      description={`Bạn đã nhận ${formatVnd(plannedAmount)} từ ${collection.reader.fullName}? Nợ trước: ${formatVnd(collection.totalOutstandingVnd)}; còn nợ sau thu: ${formatVnd(collection.totalOutstandingVnd - plannedAmount)}. Hệ thống cấp một biên lai cho số tiền thực thu lần này.`}
      confirmLabel={mode === 'FULL' ? 'Xác nhận thu đủ' : 'Xác nhận thu một phần'} danger={false} busy={collecting}
      onCancel={() => setConfirming(false)} onConfirm={() => void collect()} />}
    {receipt && <FeeReceiptDialog receipt={receipt} onClose={() => setReceipt(null)} />}
  </div>
}
