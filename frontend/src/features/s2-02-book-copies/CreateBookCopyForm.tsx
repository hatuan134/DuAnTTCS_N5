import FeedbackAlert from '../../components/ui/FeedbackAlert'
import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import Button from '../../components/ui/Button'
import Card from '../../components/ui/Card'
import Input from '../../components/ui/Input'
import { librarySettingsService } from '../s1-09-library-config/librarySettingsService'
import type { ShelfItem, WarehouseItem } from '../s1-09-library-config/librarySettingsService'
import { bookCopyService, copyError, physicalConditions, todayInVietnam, validReceivedDate } from './bookCopyService'
import type { BarcodeMode, DuplicateCopy, PhysicalCondition } from './bookCopyService'

export default function CreateBookCopyForm({ bookId, bookTitle, onCancel }: {
  bookId: number; bookTitle: string; onCancel: () => void
}) {
  const navigate = useNavigate()
  const [warehouses, setWarehouses] = useState<WarehouseItem[]>([])
  const [shelves, setShelves] = useState<ShelfItem[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')
  const [reload, setReload] = useState(0)
  const [barcodeMode, setBarcodeMode] = useState<BarcodeMode>('AUTO')
  const [barcode, setBarcode] = useState('')
  const [warehouseId, setWarehouseId] = useState('')
  const [shelfId, setShelfId] = useState('')
  const [receivedDate, setReceivedDate] = useState(todayInVietnam)
  const [coverPrice, setCoverPrice] = useState('')
  const [condition, setCondition] = useState<PhysicalCondition | ''>('')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [duplicate, setDuplicate] = useState<DuplicateCopy | undefined>()
  const submitting = useRef(false)
  const mounted = useRef(true)
  useEffect(() => { mounted.current = true; return () => { mounted.current = false } }, [])
  useEffect(() => {
    let active = true
    setLoading(true)
    setLoadError('')
    Promise.all([librarySettingsService.getWarehouses(), librarySettingsService.getShelves()])
      .then(([ws, ss]) => { if (active) { setWarehouses(ws.filter(w => w.active)); setShelves(ss.filter(s => s.active)) } })
      .catch(e => { if (active) setLoadError(copyError(e).message) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [reload])
  const options = shelves.filter(s => s.warehouseId === Number(warehouseId))

  function changeBarcodeMode(mode: BarcodeMode) {
    setBarcodeMode(mode)
    setError('')
    setDuplicate(undefined)
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (submitting.current) return
    setError(''); setDuplicate(undefined)
    if (barcodeMode === 'MANUAL' && (!barcode.trim() || barcode.trim().length > 100)) {
      setError('Mã vạch bắt buộc và tối đa 100 ký tự khi chọn nhập mã thủ công.'); return
    }
    if (!warehouses.some(w => w.id === Number(warehouseId)) || !options.some(s => s.id === Number(shelfId))) {
      setError('Vui lòng chọn kho và kệ thuộc kho đó.'); return
    }
    if (!validReceivedDate(receivedDate)) { setError('Ngày nhập phải hợp lệ và không được sau hôm nay.'); return }
    if (!/^\d{1,10}(\.\d{1,2})?$/.test(coverPrice)) {
      setError('Giá bìa phải từ 0 đến 9.999.999.999,99; tối đa 2 số thập phân (dùng dấu chấm).'); return
    }
    if (!condition) { setError('Vui lòng chọn tình trạng vật lý.'); return }
    submitting.current = true; setSaving(true)
    try {
      const copy = await bookCopyService.create(bookId, {
        barcodeMode,
        ...(barcodeMode === 'MANUAL' ? { barcode: barcode.trim() } : {}),
        warehouseId: Number(warehouseId), shelfId: Number(shelfId),
        receivedDate, coverPrice, physicalCondition: condition,
      })
      if (mounted.current) {
        navigate(`/book-copies/${copy.id}`, {
          state: { created: true, generatedBarcode: barcodeMode === 'AUTO' ? copy.barcode : undefined },
        })
      }
    } catch (e) {
      if (mounted.current) { const failure = copyError(e); setError(failure.message); setDuplicate(failure.duplicate) }
    } finally {
      submitting.current = false
      if (mounted.current) setSaving(false)
    }
  }
  const selectClass = 'h-11 w-full rounded-lg border border-slate-300 bg-white px-3 text-sm text-slate-900 focus:border-blue-500 focus:outline-none focus:ring-4 focus:ring-blue-100'
  const modeClass = (active: boolean) => `flex cursor-pointer gap-3 rounded-xl border p-4 transition ${active ? 'border-blue-500 bg-blue-50 ring-2 ring-blue-100' : 'border-slate-200 bg-white hover:border-slate-300'}`
  return (
    <Card className="mt-6 p-6">
      <h3 className="text-xl font-semibold text-slate-900">Thêm bản sao</h3>
      <p className="mt-2 text-sm text-slate-600">Đầu sách: <strong>{bookTitle}</strong> (#{bookId}). Bản sao sẽ được gắn cố định với đầu sách này.</p>
      <p className="mt-1 text-sm text-slate-500">Các trường có dấu * là bắt buộc. Trạng thái khi tạo: Sẵn sàng.</p>
      {loading && <p role="status" className="mt-4">Đang tải kho và kệ…</p>}
      {loadError && <div role="alert" className="mt-4 rounded-lg bg-red-50 p-4 text-red-700">{loadError} <Button type="button" variant="secondary" onClick={() => setReload(v => v + 1)}>Tải lại</Button></div>}
      {!loading && !loadError && warehouses.length === 0 && <p className="mt-4 text-amber-700">Chưa có kho hoạt động. Vui lòng liên hệ quản lý thư viện.</p>}
      <form onSubmit={submit} className="mt-5" noValidate>
        <fieldset disabled={saving || loading || !!loadError}>
          <legend className="text-sm font-medium text-slate-700">Cách cấp mã vạch *</legend>
          <div className="mt-2 grid gap-3 sm:grid-cols-2">
            <label className={modeClass(barcodeMode === 'AUTO')}>
              <input type="radio" name="barcode-mode" value="AUTO" checked={barcodeMode === 'AUTO'}
                onChange={() => changeBarcodeMode('AUTO')} className="mt-1 h-4 w-4" />
              <span>
                <span className="block font-semibold text-slate-900">Hệ thống sinh mã</span>
                <span className="mt-1 block text-sm text-slate-600">Tự cấp mã duy nhất theo kho, kệ và số thứ tự, ví dụ TV-KHO-A-A01-000001.</span>
              </span>
            </label>
            <label className={modeClass(barcodeMode === 'MANUAL')}>
              <input type="radio" name="barcode-mode" value="MANUAL" checked={barcodeMode === 'MANUAL'}
                onChange={() => changeBarcodeMode('MANUAL')} className="mt-1 h-4 w-4" />
              <span>
                <span className="block font-semibold text-slate-900">Nhập mã thủ công</span>
                <span className="mt-1 block text-sm text-slate-600">Giữ nguyên cách nhập và kiểm tra trùng mã của lát trước.</span>
              </span>
            </label>
          </div>

          <div className="mt-5 grid gap-5 sm:grid-cols-2">
            {barcodeMode === 'MANUAL' && <Input id="copy-barcode" label="Mã vạch *" value={barcode} maxLength={100} required autoComplete="off"
              onChange={e => { setBarcode(e.target.value); setDuplicate(undefined); setError('') }} placeholder="Ví dụ: TV-000125" />}
            {barcodeMode === 'AUTO' && <div className="rounded-lg border border-blue-100 bg-blue-50 p-4 text-sm text-blue-800">
              Không cần nhập mã vạch. Sau khi chọn kho và kệ, hệ thống tự ghép mã kho, mã kệ và số thứ tự duy nhất; nếu mã đã tồn tại thì tự chuyển sang số tiếp theo.
            </div>}
            <div>
              <label htmlFor="copy-condition" className="mb-2 block text-sm font-medium text-slate-700">Tình trạng vật lý *</label>
              <select id="copy-condition" className={selectClass} value={condition} required onChange={e => setCondition(e.target.value as PhysicalCondition | '')}>
                <option value="">Chọn tình trạng</option>
                {Object.entries(physicalConditions).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
              </select>
            </div>
            <div>
              <label htmlFor="copy-warehouse" className="mb-2 block text-sm font-medium text-slate-700">Kho *</label>
              <select id="copy-warehouse" className={selectClass} value={warehouseId} required onChange={e => { setWarehouseId(e.target.value); setShelfId('') }}>
                <option value="">Chọn kho</option>
                {warehouses.map(w => <option key={w.id} value={w.id}>{w.code} — {w.name}</option>)}
              </select>
            </div>
            <div>
              <label htmlFor="copy-shelf" className="mb-2 block text-sm font-medium text-slate-700">Kệ *</label>
              <select id="copy-shelf" className={selectClass} value={shelfId} required disabled={!warehouseId || saving} onChange={e => setShelfId(e.target.value)}>
                <option value="">Chọn kệ thuộc kho</option>
                {options.map(s => <option key={s.id} value={s.id}>{s.code} — {s.name}</option>)}
              </select>
              {warehouseId && options.length === 0 && <p className="mt-1 text-sm text-amber-700">Kho chưa có kệ hoạt động.</p>}
            </div>
            <Input id="copy-date" type="date" label="Ngày nhập *" min="0001-01-01" max={todayInVietnam()} required value={receivedDate} onChange={e => setReceivedDate(e.target.value)} />
            <Input id="copy-price" label="Giá bìa (VNĐ) *" inputMode="decimal" required value={coverPrice} onChange={e => setCoverPrice(e.target.value)} placeholder="Ví dụ: 85000" />
          </div>
        </fieldset>
        {error && <FeedbackAlert message={error} tone="error" onDismiss={() => setError('')} className="mt-5" />}
        {duplicate && <div className="mt-2">
            <p>Mã {duplicate.barcode} đang thuộc bản sao #{duplicate.existingCopyId} — {duplicate.bookTitle} (đầu sách #{duplicate.bookId}).</p>
            <Link className="mt-2 inline-block font-semibold underline" to={`/book-copies/${duplicate.existingCopyId}`}>Mở bản sao đang giữ mã này</Link>
        </div>}
        <div className="mt-6 flex flex-wrap gap-3">
          <Button type="submit" loading={saving} disabled={loading || !!loadError || warehouses.length === 0}>{saving ? 'Đang lưu…' : 'Lưu bản sao'}</Button>
          <Button type="button" variant="secondary" disabled={saving} onClick={onCancel}>Hủy</Button>
        </div>
      </form>
    </Card>
  )
}
