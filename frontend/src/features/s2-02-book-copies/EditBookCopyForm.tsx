import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import Button from '../../components/ui/Button'
import Input from '../../components/ui/Input'
import { librarySettingsService } from '../s1-09-library-config/librarySettingsService'
import type { ShelfItem, WarehouseItem } from '../s1-09-library-config/librarySettingsService'
import { bookCopyService, copyError, physicalConditions } from './bookCopyService'
import type { BookCopy, PhysicalCondition } from './bookCopyService'

export default function EditBookCopyForm({ copy, onSaved, onCancel }: {
  copy: BookCopy; onSaved: (copy: BookCopy) => void; onCancel: () => void
}) {
  const [warehouses, setWarehouses] = useState<WarehouseItem[]>([])
  const [shelves, setShelves] = useState<ShelfItem[]>([])
  const [warehouseId, setWarehouseId] = useState(String(copy.warehouseId))
  const [shelfId, setShelfId] = useState(String(copy.shelfId))
  const [condition, setCondition] = useState<PhysicalCondition | ''>(copy.physicalCondition ?? '')
  const [notes, setNotes] = useState(copy.notes ?? '')
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')
  const [reload, setReload] = useState(0)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const submitting = useRef(false)
  const mounted = useRef(true)
  useEffect(() => { mounted.current = true; return () => { mounted.current = false } }, [])
  useEffect(() => {
    let active = true
    setLoading(true); setLoadError('')
    Promise.all([librarySettingsService.getWarehouses(), librarySettingsService.getShelves()])
      .then(([ws, ss]) => {
        if (active) { setWarehouses(ws.filter(w => w.active)); setShelves(ss.filter(s => s.active)) }
      })
      .catch(e => { if (active) setLoadError(copyError(e).message) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [reload])
  const options = shelves.filter(s => s.warehouseId === Number(warehouseId))
  const selectedWarehouse = warehouses.some(w => w.id === Number(warehouseId))
  const selectedShelf = options.some(s => s.id === Number(shelfId))

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (submitting.current || loading || loadError) return
    setError('')
    if (!selectedWarehouse || !selectedShelf) {
      setError('Vui lòng chọn kho đang hoạt động và kệ thuộc kho đó.'); return
    }
    if (!condition || !(condition in physicalConditions)) {
      setError('Vui lòng chọn tình trạng vật lý.'); return
    }
    if (notes.length > 2000) { setError('Ghi chú không được dài quá 2000 ký tự.'); return }
    submitting.current = true; setSaving(true)
    try {
      const updated = await bookCopyService.update(copy.id, {
        warehouseId: Number(warehouseId), shelfId: Number(shelfId), physicalCondition: condition, notes,
      })
      if (mounted.current) onSaved(updated)
    } catch (e) {
      if (mounted.current) setError(copyError(e).message)
    } finally {
      submitting.current = false
      if (mounted.current) setSaving(false)
    }
  }
  const selectClass = 'h-11 w-full rounded-lg border border-slate-300 bg-white px-3 text-sm text-slate-900 focus:border-blue-500 focus:outline-none focus:ring-4 focus:ring-blue-100'
  return (
    <section className="mt-6 border-t border-slate-200 pt-6" aria-labelledby="edit-copy-title">
      <h3 id="edit-copy-title" className="text-xl font-semibold text-slate-900">Sửa thông tin và vị trí</h3>
      <p className="mt-2 text-sm text-slate-600">Các trường có dấu * là bắt buộc. Khi đổi kho, hãy chọn lại kệ trong kho mới.</p>
      {loading && <p role="status" className="mt-4">Đang tải kho và kệ…</p>}
      {loadError && <div role="alert" className="mt-4 rounded-lg bg-red-50 p-4 text-red-700">
        {loadError} <Button type="button" variant="secondary" onClick={() => setReload(v => v + 1)}>Tải lại</Button>
      </div>}
      {!loading && !loadError && (!selectedWarehouse || !selectedShelf) &&
        <p className="mt-4 text-sm text-amber-700">Vui lòng chọn kho và kệ đang hoạt động trước khi lưu.</p>}
      <form onSubmit={submit} className="mt-5" noValidate>
        <fieldset disabled={saving || loading || !!loadError} className="grid min-w-0 gap-5 sm:grid-cols-2">
          <Input id="edit-copy-barcode" label="Mã vạch (chỉ đọc)" value={copy.barcode} readOnly className="bg-slate-100" />
          <div>
            <label htmlFor="edit-copy-condition" className="mb-2 block text-sm font-medium text-slate-700">Tình trạng vật lý *</label>
            <select id="edit-copy-condition" className={selectClass} value={condition} required onChange={e => setCondition(e.target.value as PhysicalCondition | '')}>
              <option value="">Chọn tình trạng</option>
              {Object.entries(physicalConditions).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
            </select>
          </div>
          <div>
            <label htmlFor="edit-copy-warehouse" className="mb-2 block text-sm font-medium text-slate-700">Kho *</label>
            <select id="edit-copy-warehouse" className={selectClass} value={selectedWarehouse ? warehouseId : ''} required
              onChange={e => { setWarehouseId(e.target.value); setShelfId(''); setError('') }}>
              <option value="">Chọn kho</option>
              {warehouses.map(w => <option key={w.id} value={w.id}>{w.code} — {w.name}</option>)}
            </select>
          </div>
          <div>
            <label htmlFor="edit-copy-shelf" className="mb-2 block text-sm font-medium text-slate-700">Kệ *</label>
            <select id="edit-copy-shelf" className={selectClass} value={selectedShelf ? shelfId : ''} required disabled={!selectedWarehouse || saving}
              onChange={e => setShelfId(e.target.value)}>
              <option value="">Chọn kệ thuộc kho</option>
              {options.map(s => <option key={s.id} value={s.id}>{s.code} — {s.name}</option>)}
            </select>
            {!loading && selectedWarehouse && options.length === 0 && <p className="mt-1 text-sm text-amber-700">Kho chưa có kệ hoạt động.</p>}
          </div>
          <div className="sm:col-span-2">
            <label htmlFor="edit-copy-notes" className="mb-2 block text-sm font-medium text-slate-700">Ghi chú</label>
            <textarea id="edit-copy-notes" rows={4} maxLength={2000} value={notes} onChange={e => setNotes(e.target.value)}
              className="w-full rounded-lg border border-slate-300 bg-white p-3 text-sm text-slate-900 focus:border-blue-500 focus:outline-none focus:ring-4 focus:ring-blue-100"
              placeholder="Ví dụ: Bìa hơi xước, đã chuyển sang kệ gần bàn thủ thư." />
            <p className="mt-1 text-sm text-slate-500">{notes.length}/2000 ký tự. Để trống để xóa ghi chú.</p>
          </div>
        </fieldset>
        {error && <p role="alert" className="mt-5 rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">{error}</p>}
        <div className="mt-6 flex flex-wrap gap-3">
          <Button type="submit" loading={saving} disabled={loading || !!loadError || warehouses.length === 0}>{saving ? 'Đang lưu…' : 'Lưu thay đổi'}</Button>
          <Button type="button" variant="secondary" disabled={saving} onClick={onCancel}>Hủy</Button>
        </div>
      </form>
    </section>
  )
}
