import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import Button from '../../components/ui/Button'
import Card from '../../components/ui/Card'
import Input from '../../components/ui/Input'
import FeedbackAlert from '../../components/ui/FeedbackAlert'
import Modal from '../../components/ui/Modal'
import PageHeader from '../../components/ui/PageHeader'
import { getCurrentUser } from '../../core/auth/authStorage'
import { getApiErrorMessage } from '../s1-02-user-management/accountService'
import { formatLoanTimestamp } from '../s3-01-loans/loanService'
import { formatVnd } from '../s3-07-returns/ReturnFeeDetails'
import IncidentRecordCard from './IncidentRecordCard'
import { incidentService, incidentTypeLabel, validateCompensationAdjustment, validateIncidentFiles, type IncidentItem, type IncidentPreview, type IncidentRecord, type IncidentType } from './incidentService'

export default function BookIncidentsPage() {
  if (getCurrentUser()?.role !== 'LIBRARIAN') return <p role="alert">Chỉ Thủ thư được ghi nhận và xem sự cố sách mất/hỏng nặng.</p>
  return <IncidentWorkspace />
}
function IncidentWorkspace() {
  const [query, setQuery] = useState(''), [items, setItems] = useState<IncidentItem[]>([])
  const [searched, setSearched] = useState(false), [busy, setBusy] = useState(false)
  const [selected, setSelected] = useState<IncidentItem | null>(null), [type, setType] = useState<IncidentType>('LOST')
  const [description, setDescription] = useState(''), [files, setFiles] = useState<File[]>([]), [urls, setUrls] = useState<string[]>([])
  const [preview, setPreview] = useState<IncidentPreview | null>(null), [saved, setSaved] = useState<IncidentRecord | null>(null)
  const [historyQuery, setHistoryQuery] = useState(''), [history, setHistory] = useState<IncidentRecord[]>([])
  const [historyLoaded, setHistoryLoaded] = useState(false)
  const [feedback, setFeedback] = useState<{ tone: 'success' | 'error' | 'warning'; message: string } | null>(null)
  const [descriptionError, setDescriptionError] = useState(''), [fileError, setFileError] = useState('')
  const [adjusting, setAdjusting] = useState(false), [amount, setAmount] = useState(''), [reason, setReason] = useState('')
  const [adjustmentError, setAdjustmentError] = useState('')
  function resetAdjustment() { setAdjusting(false); setAmount(''); setReason(''); setAdjustmentError('') }
  const inFlight = useRef(false)
  useEffect(() => {
    const allocated = files.map(file => URL.createObjectURL(file)); setUrls(allocated)
    return () => { for (const url of allocated) URL.revokeObjectURL(url) }
  }, [files])
  async function search(event: FormEvent) {
    event.preventDefault(); if (busy) return
    setBusy(true); setSelected(null); setFiles([]); setPreview(null); setItems([]); setSearched(false)
    try { setItems(await incidentService.search(query.trim())); setSearched(true) }
    catch (error) { setFeedback({ tone: 'error', message: getApiErrorMessage(error, 'Không tìm được phiếu mượn đang mở.') }) }
    finally { setBusy(false) }
  }
  function select(item: IncidentItem) {
    setSelected(item); setDescription(''); setFiles([]); setDescriptionError(''); setFileError(''); setPreview(null); setSaved(null)
  }
  async function review(event: FormEvent) {
    event.preventDefault(); if (!selected || busy) return
    const invalidDescription = !description.trim() || description.trim().length > 1900
    setDescriptionError(invalidDescription ? 'Nhập mô tả tình trạng sách, tối đa 1.900 ký tự.' : '')
    const invalidFiles = validateIncidentFiles(files); setFileError(invalidFiles)
    if (invalidDescription || invalidFiles) return
    setBusy(true); setFeedback(null)
    try { resetAdjustment(); setPreview(await incidentService.preview(selected.itemId, type)) }
    catch (error) { setFeedback({ tone: 'error', message: getApiErrorMessage(error, 'Chưa thể tính khoản bồi thường.') }) }
    finally { setBusy(false) }
  }
  async function confirm() {
    if (!preview || inFlight.current) return
    const invalid = adjusting ? validateCompensationAdjustment(preview.compensationVnd, amount, reason) : ''
    setAdjustmentError(invalid)
    if (invalid) return
    inFlight.current = true; setBusy(true); setFeedback(null)
    try {
      const result = await incidentService.confirm(preview, description, files, adjusting ? { amount, reason } : undefined)
      setSaved(result); setHistory(values => [result, ...values.filter(value => value.id !== result.id)])
      setItems(values => values.filter(value => value.itemId !== result.item.itemId))
      setSelected(null); setPreview(null); setFiles([])
      setFeedback({ tone: 'success', message: `Đã ghi nhận ${incidentTypeLabel(result.type)}, loại bản sao khỏi kho và lập bồi thường ${formatVnd(result.compensationVnd)}.` })
    } catch (error) {
      setPreview(null)
      setFeedback({ tone: 'error', message: getApiErrorMessage(error, 'Chưa xác định được kết quả. Tra cứu Nhật ký sự cố theo mã bản sao trước khi thử lại.') })
    } finally { inFlight.current = false; setBusy(false) }
  }
  async function loadHistory(event: FormEvent) {
    event.preventDefault(); if (busy) return
    setBusy(true)
    try { setHistory(await incidentService.history(historyQuery)); setHistoryLoaded(true) }
    catch (error) { setFeedback({ tone: 'error', message: getApiErrorMessage(error, 'Không tải được nhật ký sự cố.') }) }
    finally { setBusy(false) }
  }
  return <div className="space-y-5">
    <PageHeader title="Sách mất hoặc hỏng nặng" description="Ghi nhận từng bản sách, giữ nguyên các khoản phí đã thu/miễn và loại bản sao khỏi kho." />
    {feedback && <FeedbackAlert {...feedback} onDismiss={() => setFeedback(null)} />}
    <Card className="p-5"><form onSubmit={search} className="flex flex-col items-end gap-3 sm:flex-row">
      <Input label="Tìm phiếu mượn đang mở" value={query} maxLength={255} disabled={busy} onChange={e => setQuery(e.target.value)} placeholder="Mã phiếu, mã bản sao, thẻ hoặc tên bạn đọc" />
      <Button type="submit" disabled={busy} className="shrink-0">Tìm phiếu</Button>
    </form><p className="mt-2 text-xs text-slate-500">Hiển thị tối đa 100 bản đang mượn. Nhập mã cụ thể để thu hẹp kết quả.</p></Card>
    {busy && <p role="status">Đang xử lý...</p>}
    {searched && items.length === 0 && <p role="status" className="rounded-lg bg-slate-50 p-4">Không có bản sách đang mượn khớp tìm kiếm. Phiếu đã đóng không thể ghi nhận thêm.</p>}
    <div className="grid gap-3 lg:grid-cols-2">{items.map(item => <Card key={item.itemId} className="space-y-2 p-4">
      <h3 className="break-words font-semibold">{item.bookTitle}</h3><p className="break-words text-sm">{item.readerName} · {item.loanNumber}</p>
      <p className="break-all font-mono text-sm">{item.barcode}</p><p className="text-sm">Hạn trả: {formatLoanTimestamp(item.dueAt)} · Đang mượn</p>
      <p className="text-sm">Giá bìa: {item.coverPrice && item.coverPrice > 0 ? formatVnd(item.coverPrice) : 'Chưa có giá hợp lệ'}</p>
      <Button variant="secondary" disabled={busy} onClick={() => select(item)}>{selected?.itemId === item.itemId ? 'Đang chọn' : 'Chọn bản sách'}</Button>
    </Card>)}</div>
    {selected && <Card className="p-5"><form onSubmit={review} className="space-y-4">
      <h3 className="break-words font-semibold">Ghi nhận cho {selected.barcode} · {selected.bookTitle}</h3>
      <div><label htmlFor="incident-type" className="mb-2 block text-sm font-semibold">Loại sự cố *</label>
        <select id="incident-type" value={type} disabled={busy} onChange={e => setType(e.target.value as IncidentType)} className="min-h-11 w-full rounded-xl border border-slate-300 bg-white px-3 text-sm">
          <option value="LOST">Mất</option><option value="DAMAGED">Hỏng nặng</option>
        </select></div>
      <div><label htmlFor="incident-description" className="mb-2 block text-sm font-semibold">Mô tả tình trạng sách *</label>
        <textarea id="incident-description" value={description} maxLength={1900} disabled={busy} rows={4} aria-invalid={!!descriptionError} aria-describedby={descriptionError ? 'description-error' : undefined}
          onChange={e => { setDescription(e.target.value); setDescriptionError('') }} className="w-full rounded-xl border border-slate-300 p-3 text-sm" placeholder="Mô tả việc mất sách hoặc mức độ hư hỏng..." />
        {descriptionError && <p id="description-error" className="mt-1 text-sm text-red-700">{descriptionError}</p>}</div>
      <Input type="file" label="Ảnh tình trạng sách (không bắt buộc)" accept="image/jpeg,image/png,image/webp" multiple disabled={busy} onChange={e => {
        const next = [...files, ...Array.from(e.target.files ?? [])]; const error = validateIncidentFiles(next)
        setFileError(error); if (!error) setFiles(next); e.target.value = ''
      }} />
      <p className="text-xs text-slate-500">Tối đa 5 ảnh JPEG/PNG/WebP tĩnh, 5 MB/ảnh và 25 triệu điểm ảnh. Có thể bỏ ảnh trước khi xác nhận.</p>
      {fileError && <p role="alert" className="text-sm text-red-700">{fileError}</p>}
      <div className="grid gap-3 sm:grid-cols-3">{files.map((file, index) => <figure key={`${file.name}:${index}`} className="min-w-0 rounded-lg border p-2">
        {urls[index] && <img src={urls[index]} alt={`Ảnh xem trước ${index + 1}`} className="h-32 w-full object-contain" />}<figcaption className="break-all text-xs">{file.name}</figcaption>
        <Button variant="ghost" type="button" disabled={busy} onClick={() => setFiles(values => values.filter((_, i) => i !== index))}>Bỏ ảnh</Button>
      </figure>)}</div>
      <div className="flex flex-wrap gap-2"><Button type="submit" disabled={busy}>Xem khoản bồi thường</Button>
        <Button type="button" variant="secondary" disabled={busy} onClick={() => { setSelected(null); setFiles([]) }}>Hủy chọn</Button></div>
    </form></Card>}
    {preview && <Modal label="Xác nhận sự cố sách" alert busy={busy} onClose={() => { if (!busy) { resetAdjustment(); setPreview(null) } }}>
      <div className="w-full max-w-xl space-y-4 rounded-2xl bg-white p-5 shadow-xl">
        <h2 className="text-lg font-semibold">Xác nhận sách {incidentTypeLabel(preview.type)}</h2>
        <p className="break-words text-sm">{preview.item.readerName} · {preview.item.loanNumber} · {preview.item.barcode}</p>
        <dl className="grid gap-3 text-sm sm:grid-cols-2">
          <div><dt>Giá bìa</dt><dd className="font-semibold">{formatVnd(preview.item.coverPrice ?? 0)}</dd></div>
          <div><dt>Hệ số</dt><dd className="font-semibold">{preview.factor}</dd></div>
          <div><dt>Bồi thường tự tính</dt><dd className="font-semibold text-red-700">{formatVnd(preview.compensationVnd)}</dd></div>
          <div><dt>Phí trễ hạn</dt><dd>{formatVnd(preview.overdueFee.amountVnd ?? 0)} · {preview.overdueFee.overdueDays} ngày mở cửa</dd></div>
        </dl>
        <section className="space-y-3 rounded-xl border border-slate-200 p-3">
          {!adjusting ? <Button variant="secondary" disabled={busy} onClick={() => { setAdjusting(true); setAmount(String(preview.compensationVnd)); setReason(''); setAdjustmentError('') }}>Điều chỉnh số tiền bồi thường</Button> : <>
            <Input label="Số tiền bồi thường đề xuất (VND) *" inputMode="numeric" value={amount} disabled={busy} maxLength={16}
              onChange={e => { setAmount(e.target.value); setAdjustmentError('') }} />
            <p className="text-xs text-slate-500">Số nguyên từ 0 đến 10.000.000 đồng khi điều chỉnh. Không cần Quản lý phê duyệt.</p>
            <label htmlFor="adjustment-reason" className="block text-sm font-semibold">Lý do điều chỉnh (bắt buộc khi đổi tiền)</label>
            <textarea id="adjustment-reason" value={reason} rows={3} maxLength={1900} disabled={busy}
              onChange={e => { setReason(e.target.value); setAdjustmentError('') }} className="w-full rounded-xl border border-slate-300 p-3 text-sm" />
            {adjustmentError && <p role="alert" className="text-sm text-red-700">{adjustmentError}</p>}
            <p className="break-words text-sm">Trước điều chỉnh: <strong>{formatVnd(preview.compensationVnd)}</strong> · Sau điều chỉnh: <strong>{validateCompensationAdjustment(preview.compensationVnd, amount, reason) ? 'Chưa hợp lệ' : formatVnd(Number(amount))}</strong></p>
            {Number(amount) !== preview.compensationVnd && reason.trim() && <p className="whitespace-pre-wrap break-words text-sm">Lý do: {reason.trim()}</p>}
            <Button variant="secondary" disabled={busy} onClick={resetAdjustment}>Hủy điều chỉnh</Button>
          </>}
        </section>
        <p className="whitespace-pre-wrap break-words text-sm">{description}</p>
        <p className="text-sm">Đính kèm {files.length} ảnh. Bản sao sẽ chuyển sang Đã loại khỏi kho. Các bản khác chưa xử lý vẫn được mượn; phiếu chỉ đóng khi không còn bản đang mượn.</p>
        <p className="text-sm text-red-700">Sự cố đã xác nhận không được sửa hoặc xóa. Phí đã ghi nhận, đã thu và đã miễn giữ nguyên.</p>
        <div className="flex flex-wrap justify-end gap-2"><Button variant="secondary" disabled={busy} onClick={() => { resetAdjustment(); setPreview(null) }}>Quay lại</Button>
          <Button variant="danger" loading={busy} onClick={() => void confirm()}>Xác nhận ghi nhận sự cố</Button></div>
      </div>
    </Modal>}
    {saved && <section className="space-y-3"><h2 className="text-lg font-semibold">Kết quả ghi nhận</h2><IncidentRecordCard record={saved} /></section>}
    <section className="space-y-3">
      <h2 className="text-lg font-semibold">Nhật ký sự cố</h2>
      <Card className="p-5"><form onSubmit={loadHistory} className="flex flex-col items-end gap-3 sm:flex-row">
        <Input label="Tìm nhật ký" value={historyQuery} maxLength={255} disabled={busy} onChange={e => setHistoryQuery(e.target.value)} placeholder="Mã phiếu, mã bản sao hoặc tên bạn đọc" />
        <Button type="submit" variant="secondary" disabled={busy} className="shrink-0">Xem nhật ký</Button>
      </form><p className="mt-2 text-xs text-slate-500">Hiển thị tối đa 100 sự cố mới nhất khớp tìm kiếm.</p></Card>
      {historyLoaded && history.length === 0 && <p role="status">Không có sự cố khớp tìm kiếm.</p>}
      {history.filter(record => record.id !== saved?.id).map(record => <IncidentRecordCard key={record.id} record={record} />)}
    </section>
    <Link to="/fee-payments" className="inline-block text-sm text-blue-700 underline">Đi tới Thu phí và biên lai</Link>
  </div>
}
