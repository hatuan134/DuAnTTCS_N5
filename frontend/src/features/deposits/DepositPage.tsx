import { useEffect, useRef, useState } from 'react'
import { isAxiosError } from 'axios'
import { getCurrentUser } from '../../core/auth/authStorage'
import Button from '../../components/ui/Button'
import Card from '../../components/ui/Card'
import Input from '../../components/ui/Input'
import PageHeader from '../../components/ui/PageHeader'
import TablePagination from '../../components/ui/TablePagination'
import FeedbackAlert from '../../components/ui/FeedbackAlert'
import { getApiErrorMessage } from '../s1-02-user-management/accountService'
import { formatVnd } from '../s3-07-returns/ReturnFeeDetails'
import { formatLoanTimestamp } from '../s3-01-loans/loanService'
import { depositService, depositActionAmount, depositKindLabel, type DepositAccount, type DepositKind, type DepositTransaction } from './depositService'
import { readPendingDeposit, savePendingDeposit, clearPendingDeposit, type PendingDeposit } from './depositRecovery'
import DepositPolicyPanel from './DepositPolicyPanel'

export default function DepositPage() {
  const role = getCurrentUser()?.role ?? ''
  if (!['ADMIN', 'LIBRARY_MANAGER', 'LIBRARIAN', 'READER'].includes(role)) return <p role="alert">Vui lòng đăng nhập để xem tiền cọc.</p>
  if (role !== 'READER') return <div className="space-y-5"><PageHeader title="Tiền đặt cọc" description="Cấu hình chính sách cọc cho các lượt bàn giao mới." /><DepositPolicyPanel readOnly={!['ADMIN', 'LIBRARY_MANAGER'].includes(role)} /></div>
  return <Deposits role={role} />
}
type DepositContext = { mode: 'collect' | 'return'; card?: string; barcodes?: string[]; readerId?: number; loanNumber?: string; disabled?: boolean }
export function InlineDeposits(props: DepositContext) {
  const role = getCurrentUser()?.role ?? ''
  if (!['ADMIN', 'LIBRARIAN'].includes(role)) return null
  return <Deposits role={role} context={props} />
}
function Deposits({ role, context }: { role: string; context?: DepositContext }) {
  const actor = getCurrentUser()!.id
  const [recovery] = useState(() => {
    try { return { pending: readPendingDeposit(sessionStorage, actor), error: '' } }
    catch { return { pending: null, error: 'Không đọc được giao dịch đang chờ đối chiếu. Kiểm tra lịch sử giao dịch trước khi tiếp tục thu hoặc hoàn tiền.' } }
  })
  const canCollect = ['ADMIN', 'LIBRARIAN'].includes(role) && !recovery.error
  const [rows, setRows] = useState<DepositAccount[]>([]), [total, setTotal] = useState(0)
  const [query, setQuery] = useState(''), [search, setSearch] = useState(''), [page, setPage] = useState(0), [revision, setRevision] = useState(0)
  const [loading, setLoading] = useState(true), [busy, setBusy] = useState(false), [error, setError] = useState(''), [notice, setNotice] = useState('')
  const [prepared, setPrepared] = useState<DepositAccount[]>([])
  const [preparedSelection, setPreparedSelection] = useState('')
  const [policyNotice, setPolicyNotice] = useState('')
  const confirmedSelection = useRef<string | null>(null)
  const [confirmation, setConfirmation] = useState<PendingDeposit | null>(recovery.pending)
  const [uncertain, setUncertain] = useState(!!recovery.pending), pending = useRef(false)
  const [receipt, setReceipt] = useState<DepositTransaction | null>(null)
  const [history, setHistory] = useState<DepositTransaction[]>([]), [historyReader, setHistoryReader] = useState<number | null>(null), [historyPage, setHistoryPage] = useState(0), [historyTotal, setHistoryTotal] = useState(0)
  useEffect(() => {
    if (context?.mode === 'collect') return
    let active = true; setLoading(true)
    depositService.list(context?.loanNumber ?? search, page, context?.readerId).then(data => { if (active) { setRows(data.items); setTotal(data.totalItems); setError('') } })
      .catch(e => { if (active) setError(getApiErrorMessage(e, 'Không tải được cọc.')) }).finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [search, page, revision, context?.mode, context?.loanNumber, context?.readerId])
  const selection = JSON.stringify([context?.card, context?.barcodes])
  useEffect(() => {
    if (confirmation && !uncertain && !busy && confirmedSelection.current !== selection) setConfirmation(null)
  }, [selection, confirmation, uncertain, busy])
  useEffect(() => {
    if (context?.mode !== 'collect') return
    const [card, barcodes] = JSON.parse(selection) as [string | undefined, string[] | undefined]
    let active = true
    setPrepared([]); setPreparedSelection(''); setError(''); setPolicyNotice('')
    if (!card?.trim() || !barcodes?.length) { setLoading(false); return }
    setLoading(true)
    depositService.prepare(card.trim(), barcodes)
      .then(accounts => { if (active) { setPrepared(accounts); setPreparedSelection(selection) } })
      .catch(e => { if (active) {
        if (isAxiosError(e) && e.response?.data?.code === 'DEPOSIT_NOT_ACTIVE') setPolicyNotice('Chưa có chính sách cọc có hiệu lực. Phiếu này chưa yêu cầu cọc theo chính sách mới.')
        else setError(getApiErrorMessage(e, 'Không tính được tiền cọc.'))
      } })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [selection, context?.mode, revision])
  useEffect(() => {
    if (historyReader === null) return
    let active = true
    depositService.history(historyReader, historyPage).then(data => { if (active) { setHistory(data.items); setHistoryTotal(data.totalItems) } }).catch(e => { if (active) setError(getApiErrorMessage(e, 'Không tải được lịch sử.')) })
    return () => { active = false }
  }, [historyReader, historyPage, revision])
  function confirm(accounts: DepositAccount[], kind: DepositKind) {
    if (confirmation || busy || loading || context?.disabled || !accounts.length || (kind === 'COLLECT' && preparedSelection !== selection)) return
    const selected = accounts.filter(a => depositActionAmount(a, kind) > 0)
    if (!selected.length || selected.some(a => a.readerId !== selected[0].readerId)) return
    setUncertain(false); setError(''); confirmedSelection.current = selection
    setConfirmation({ reader: selected[0].readerId, kind, request: { requestKey: crypto.randomUUID(), accountIds: selected.map(a => a.id), expectedAmountVnd: selected.reduce((sum, a) => sum + depositActionAmount(a, kind), 0), confirmation: kind } })
  }
  async function submit() {
    if (!confirmation || pending.current) return
    if (!uncertain && (context?.disabled || confirmedSelection.current !== selection)) {
      setConfirmation(null); setError('Thông tin phiếu đã thay đổi. Kiểm tra lại sách và thẻ trước khi xác nhận thu tiền.'); return
    }
    pending.current = true; setBusy(true)
    try {
      savePendingDeposit(sessionStorage, actor, confirmation)
      const saved = await depositService.transact(confirmation.reader, confirmation.kind, confirmation.request)
      clearPendingDeposit(sessionStorage, actor)
      setReceipt(saved); setNotice(`Đã ghi nhận ${depositKindLabel[saved.kind].toLowerCase()} ${formatVnd(saved.totalVnd)} · ${saved.transactionNumber}`)
      setConfirmation(null); setUncertain(false); setPrepared([]); setRevision(v => v + 1)
    } catch (e) {
      const status = isAxiosError(e) ? e.response?.status : undefined
      const ambiguous = !status || status >= 500
      setUncertain(ambiguous || uncertain); setError(getApiErrorMessage(e, 'Chưa xác định được kết quả. Thử lại cùng mã giao dịch để đối chiếu.'))
      if (!ambiguous) {
        // Authentication failures do not resolve an earlier ambiguous transaction.
        if (uncertain) return
        clearPendingDeposit(sessionStorage, actor); setConfirmation(null); setPrepared([]); setRevision(v => v + 1)
      }
    } finally { pending.current = false; setBusy(false) }
  }
  return <div className="space-y-5">
    {context ? <h3 className="font-semibold">{context.mode === 'collect' ? 'Thu tiền cọc trước bàn giao' : 'Đối soát và hoàn cọc'}</h3> : <PageHeader title="Tiền đặt cọc" description="Mượn sách miễn phí. Cọc là tiền giữ tạm thời theo từng cuốn, được đối soát khi trả sách." />}
    {recovery.error && <p role="alert" className="rounded-xl bg-red-50 p-4 text-red-800">{recovery.error}</p>}
    {error && <FeedbackAlert tone="error" message={error} onDismiss={() => setError('')} />}
    {notice && <FeedbackAlert tone="success" message={notice} onDismiss={() => setNotice('')} />}
    {policyNotice && <p role="status" className="rounded-xl bg-blue-50 p-3 text-sm text-blue-900">{policyNotice}</p>}
    {canCollect && context?.mode === 'collect' && <Card className="space-y-3 p-4">
      <p className="text-sm text-slate-600">Tính cọc cho {context.barcodes?.length ?? 0} cuốn đã chọn. Chỉ xác nhận thu sau khi nhận đủ tiền tại quầy.</p>
      {loading && <p role="status" className="text-sm text-slate-600">Đang tính tiền cọc…</p>}
      {error && <Button type="button" variant="secondary" disabled={loading || busy} onClick={() => setRevision(v => v + 1)}>Thử lại</Button>}
      {preparedSelection === selection && prepared.length > 0 && <div className="space-y-2"><p>Tổng cọc chưa thu: <strong>{formatVnd(prepared.reduce((sum, a) => sum + depositActionAmount(a, 'COLLECT'), 0))}</strong></p>
        {prepared.map(a => <p key={a.id} className="break-words text-sm">{a.bookTitle} · {a.barcode} · {a.percentage}% × {formatVnd(a.coverPrice)} = {formatVnd(a.requiredVnd)} · Đã thu {formatVnd(a.collectedVnd)}</p>)}
        <div className="flex flex-wrap gap-2"><Button type="button" disabled={loading || busy || context.disabled || !!confirmation || !prepared.some(a => depositActionAmount(a, 'COLLECT') > 0)} onClick={() => confirm(prepared, 'COLLECT')}>Thu tổng tiền cọc tại quầy</Button>
          {prepared.some(a => depositActionAmount(a, 'REFUND') > 0) && <Button type="button" variant="secondary" disabled={loading || busy || context.disabled || !!confirmation} onClick={() => confirm(prepared, 'REFUND')}>Hoàn cọc nếu không bàn giao</Button>}
        </div></div>}
    </Card>}
    {confirmation && <Card className="space-y-3 border-amber-300 p-4"><h2 className="font-semibold">Xác nhận {depositKindLabel[confirmation.kind].toLowerCase()}: {formatVnd(confirmation.request.expectedAmountVnd)}</h2>
      <p className="text-xs text-slate-600">Bạn đọc #{confirmation.reader} · Mã yêu cầu {confirmation.request.requestKey}</p>
      <p className="text-sm">{confirmation.kind === 'COLLECT' ? 'Chỉ xác nhận sau khi đã nhận đủ tiền tại quầy.' : confirmation.kind === 'REFUND' ? 'Chỉ xác nhận sau khi đã giao lại tiền cho bạn đọc. Hệ thống không chuyển tiền ngân hàng.' : 'Xác nhận sử dụng tiền cọc đang giữ để thanh toán nghĩa vụ đã chốt.'}</p>
      {uncertain && <p role="alert">Chưa xác định kết quả lần trước. Giữ nguyên nội dung và mã giao dịch khi thử lại; không thu/hoàn tiền lần thứ hai.</p>}
      <div className="flex flex-wrap gap-2"><Button type="button" loading={busy} onClick={() => void submit()}>{uncertain ? 'Đối chiếu / thử lại cùng giao dịch' : 'Xác nhận đã thực hiện'}</Button><Button type="button" variant="secondary" disabled={busy || uncertain} onClick={() => setConfirmation(null)}>Hủy</Button></div>
    </Card>}
    {receipt && <Card className="space-y-2 p-4"><h2 className="font-semibold">Chứng từ {receipt.transactionNumber}</h2><p>{depositKindLabel[receipt.kind]} · {formatVnd(receipt.totalVnd)}</p><p className="text-sm">{receipt.actorName} · {formatLoanTimestamp(receipt.recordedAt)}</p>{receipt.entries.map(e => <p key={e.accountId} className="text-sm">Phân bổ cọc #{e.accountId}: {formatVnd(e.amountVnd)}</p>)}{receipt.feePaymentId && <p className="text-sm">Biên lai phí #{receipt.feePaymentId} đã ghi nhận từ khấu trừ cọc, xem trong Thu phí và biên lai.</p>}</Card>}
    {!context && <form className="flex flex-wrap items-end gap-2" onSubmit={e => { e.preventDefault(); setPage(0); setSearch(query.trim()); setRevision(v => v + 1) }}><div className="min-w-0 flex-1"><Input label="Tìm tên bạn đọc, mã thẻ, mã vạch, sách hoặc phiếu mượn" value={query} maxLength={255} onChange={e => setQuery(e.target.value)} /></div><Button type="submit">Tìm kiếm</Button><Button type="button" variant="secondary" onClick={() => { setQuery(''); setSearch(''); setPage(0); setRevision(v => v + 1) }}>Tất cả</Button></form>}
    {context?.mode === 'return' && <p className="text-sm text-slate-600">Cọc được theo dõi riêng từng cuốn trong phiếu {context.loanNumber}. Chỉ hoàn sau khi đã nhận trả và thanh toán hoặc khấu trừ hết nghĩa vụ của cuốn đó. Xác nhận hoàn tiền sau khi đã giao tiền cho bạn đọc.</p>}
    {context?.mode !== 'collect' && <>{loading ? <p role="status">Đang tải tiền cọc…</p> : error ? <Button type="button" variant="secondary" onClick={() => setRevision(v => v + 1)}>Tải lại tiền cọc</Button> : rows.length === 0 ? <Card className="p-5">{context?.mode === 'return' ? 'Phiếu này chưa có khoản cọc được ghi nhận để hoàn. Phiếu cũ không bị áp thêm nghĩa vụ cọc.' : 'Chưa có khoản cọc phù hợp.'}</Card> : <div className="grid gap-4 lg:grid-cols-2">{rows.map(a => <Card key={a.id} className="min-w-0 space-y-3 p-4"><div><h2 className="break-words font-semibold">{a.bookTitle}</h2><p className="break-words text-sm text-slate-600">{a.readerName} · {a.cardNumber ?? 'Chưa có mã thẻ'} · {a.barcode}</p></div>
      <p className="text-sm">{a.loanNumber ?? 'Chưa bàn giao'} · {a.itemId ? a.returnedAt ? 'Đã trả / xử lý' : 'Đang mượn' : 'Chuẩn bị nhận sách'}</p>
      <dl className="grid grid-cols-2 gap-2 text-sm">{[['Cọc cần nộp', a.requiredVnd], ['Đã thu', a.collectedVnd], ['Đang giữ', a.heldVnd], ['Đã hoàn', a.refundedVnd], ['Đã khấu trừ', a.deductedVnd], ['Nghĩa vụ còn thiếu', a.outstandingVnd]].map(([label, amount]) => <div key={label}><dt className="text-slate-500">{label}</dt><dd className="font-semibold">{formatVnd(Number(amount))}</dd></div>)}</dl>
      <p className="text-xs text-slate-500">Giá bìa {formatVnd(a.coverPrice)} · Cọc {a.percentage}% · Chính sách #{a.policyId}</p>
      {context?.mode === 'return' && a.itemId && !a.returnedAt && <p className="rounded-lg bg-amber-50 p-2 text-sm text-amber-800">Chưa nhận trả cuốn này — tiền cọc tiếp tục được giữ, chưa thể hoàn hoặc khấu trừ.</p>}
      <div className="flex flex-wrap gap-2">{canCollect && (['COLLECT', 'DEDUCT', 'REFUND'] as const).filter(kind => context?.mode !== 'return' || kind !== 'COLLECT').filter(kind => depositActionAmount(a, kind) > 0).map(kind => <Button type="button" key={kind} size="sm" disabled={busy || !!confirmation} onClick={() => confirm([a], kind)}>{depositKindLabel[kind]} {formatVnd(depositActionAmount(a, kind))}</Button>)}<Button type="button" size="sm" variant="secondary" onClick={() => { setHistory([]); setHistoryPage(0); setHistoryReader(a.readerId) }}>Lịch sử giao dịch</Button></div>
    </Card>)}</div>}
    <TablePagination page={page + 1} pageSize={10} totalItems={total} totalPages={Math.max(1, Math.ceil(total / 10))} onPageChange={p => setPage(p - 1)} /></>}
    {historyReader !== null && <Card className="space-y-3 p-4"><h2 className="font-semibold">Lịch sử cọc bạn đọc #{historyReader}</h2>{history.map(t => <div key={t.id} className="border-b border-slate-100 py-2 text-sm"><p>{t.transactionNumber} · {depositKindLabel[t.kind]} · {formatVnd(t.totalVnd)}</p><p>{t.actorName} · {formatLoanTimestamp(t.recordedAt)}</p><Button type="button" size="sm" variant="secondary" onClick={() => setReceipt(t)}>Xem chứng từ</Button></div>)}{!history.length && <p>Chưa có giao dịch.</p>}<TablePagination page={historyPage + 1} pageSize={10} totalItems={historyTotal} totalPages={Math.max(1, Math.ceil(historyTotal / 10))} onPageChange={p => setHistoryPage(p - 1)} /></Card>}
  </div>
}
