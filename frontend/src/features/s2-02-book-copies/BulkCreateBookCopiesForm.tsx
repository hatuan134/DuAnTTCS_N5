import { type FormEvent, useEffect, useRef, useState } from 'react'
import Button from '../../components/ui/Button'
import Card from '../../components/ui/Card'
import Input from '../../components/ui/Input'
import { librarySettingsService } from '../s1-09-library-config/librarySettingsService'
import type { ShelfItem, WarehouseItem } from '../s1-09-library-config/librarySettingsService'
import type { BulkBarcodePreview, BulkCreateBookCopiesResult } from './bookCopyService'
import { bookCopyService, copyError, todayInVietnam, validReceivedDate } from './bookCopyService'

export default function BulkCreateBookCopiesForm({
  bookId,
  bookTitle,
  onCancel,
  onCreated,
}: {
  bookId: number
  bookTitle: string
  onCancel: () => void
  onCreated: (result: BulkCreateBookCopiesResult) => void
}) {
  const [warehouses, setWarehouses] = useState<WarehouseItem[]>([])
  const [shelves, setShelves] = useState<ShelfItem[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')
  const [reload, setReload] = useState(0)
  const [quantity, setQuantity] = useState('10')
  const [warehouseId, setWarehouseId] = useState('')
  const [shelfId, setShelfId] = useState('')
  const [receivedDate, setReceivedDate] = useState(todayInVietnam)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [preview, setPreview] = useState<BulkBarcodePreview | null>(null)
  const [previewError, setPreviewError] = useState('')
  const [reviewing, setReviewing] = useState(false)
  const [refresh, setRefresh] = useState(0)
  const [previewKey, setPreviewKey] = useState('')
  const formKey = JSON.stringify([bookId, quantity, warehouseId, shelfId, receivedDate])
  const currentPreview = previewKey === formKey ? preview : null
  const submitting = useRef(false)
  const mounted = useRef(true)

  useEffect(() => {
    mounted.current = true
    return () => { mounted.current = false }
  }, [])

  useEffect(() => {
    let active = true
    setLoading(true)
    setLoadError('')
    Promise.all([
      librarySettingsService.getWarehouses(),
      librarySettingsService.getShelves(),
    ])
      .then(([warehouseData, shelfData]) => {
        if (!active) return
        setWarehouses(warehouseData.filter((warehouse) => warehouse.active))
        setShelves(shelfData.filter((shelf) => shelf.active))
      })
      .catch((failure) => {
        if (active) setLoadError(copyError(failure).message)
      })
      .finally(() => {
        if (active) setLoading(false)
      })

    return () => { active = false }
  }, [reload])

  useEffect(() => {
    let active = true
    let lastPreview: string | undefined
    let pending = false
    setPreview(null)
    setPreviewError('')
    setReviewing(false)
    const count = Number(quantity)
    if (!/^\d+$/.test(quantity) || !Number.isSafeInteger(count) || count < 1 || count > 50) return
    const selectedWarehouseId = Number(warehouseId)
    const selectedShelfId = Number(shelfId)
    if (!Number.isSafeInteger(selectedWarehouseId) || selectedWarehouseId < 1
      || !Number.isSafeInteger(selectedShelfId) || selectedShelfId < 1) return
    async function updatePreview() {
      if (submitting.current || pending) return
      pending = true
      try {
        const result = await bookCopyService.previewBulk(bookId, count, selectedWarehouseId, selectedShelfId)
        if (!active || submitting.current) return
        const signature = JSON.stringify(result)
        if (lastPreview !== undefined && lastPreview !== signature) setReviewing(false)
        lastPreview = signature
        setPreview(result)
        setPreviewKey(formKey)
        setPreviewError('')
      } catch (failure) {
        if (active && !submitting.current) {
          setPreview(null)
          setReviewing(false)
          setPreviewError(copyError(failure).message)
        }
      } finally {
        pending = false
      }
    }
    const timer = window.setTimeout(() => { void updatePreview() }, 250)
    const interval = window.setInterval(() => { void updatePreview() }, 5000)
    const focus = () => { void updatePreview() }
    window.addEventListener('focus', focus)
    return () => {
      active = false
      window.clearTimeout(timer)
      window.clearInterval(interval)
      window.removeEventListener('focus', focus)
    }
  }, [bookId, quantity, formKey, refresh])

  const shelfOptions = shelves.filter((shelf) => shelf.warehouseId === Number(warehouseId))
  const selectClass = 'h-11 w-full rounded-lg border border-slate-300 bg-white px-3 text-sm text-slate-900 focus:border-blue-500 focus:outline-none focus:ring-4 focus:ring-blue-100'

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (submitting.current) return
    setError('')

    if (!/^\d+$/.test(quantity)) {
      setError('Số lượng bản sao phải là số nguyên từ 1 đến 50.')
      return
    }
    const parsedQuantity = Number(quantity)
    if (!Number.isSafeInteger(parsedQuantity) || parsedQuantity < 1 || parsedQuantity > 50) {
      setError('Số lượng bản sao phải là số nguyên từ 1 đến 50.')
      return
    }
    if (!warehouses.some((warehouse) => warehouse.id === Number(warehouseId))) {
      setError('Vui lòng chọn kho hợp lệ.')
      return
    }
    if (!shelfOptions.some((shelf) => shelf.id === Number(shelfId))) {
      setError('Vui lòng chọn kệ thuộc kho đã chọn.')
      return
    }
    if (!validReceivedDate(receivedDate)) {
      setError('Ngày nhập phải hợp lệ và không được sau hôm nay.')
      return
    }

    if (!currentPreview) {
      setError('Vui lòng đợi khoảng mã dự kiến tải xong trước khi tiếp tục.')
      return
    }
    if (!reviewing) {
      setReviewing(true)
      return
    }
    submitting.current = true
    setSaving(true)
    try {
      const result = await bookCopyService.createBulk(bookId, {
        quantity: parsedQuantity,
        confirmed: true,
        expectedStartNumber: currentPreview.startNumber,
        expectedSkippedBarcodes: currentPreview.skippedBarcodes,
        warehouseId: Number(warehouseId),
        shelfId: Number(shelfId),
        receivedDate,
      })
      if (mounted.current) onCreated(result)
    } catch (failure) {
      if (mounted.current) {
        setError(copyError(failure).message)
        setReviewing(false)
        setPreview(null)
        setRefresh(value => value + 1)
      }
    } finally {
      submitting.current = false
      if (mounted.current) setSaving(false)
    }
  }

  return (
    <Card className="mt-6 p-6">
      <h3 className="text-xl font-semibold text-slate-900">Thêm nhiều bản sao</h3>
      <p className="mt-2 text-sm text-slate-600">
        Đầu sách: <strong>{bookTitle}</strong> (#{bookId}). Toàn bộ bản sao trong lô sẽ thuộc đầu sách này.
      </p>
      <p className="mt-1 text-sm text-slate-500">
        Tạo từ 1 đến 50 bản sao với kho, kệ và ngày nhập dùng chung. Trạng thái khi tạo: Sẵn sàng.
      </p>

      {loading && <p role="status" className="mt-4">Đang tải kho và kệ…</p>}
      {loadError && (
        <div role="alert" className="mt-4 rounded-lg bg-red-50 p-4 text-red-700">
          {loadError}{' '}
          <Button type="button" variant="secondary" onClick={() => setReload((value) => value + 1)}>
            Tải lại
          </Button>
        </div>
      )}
      {!loading && !loadError && warehouses.length === 0 && (
        <p className="mt-4 text-amber-700">Chưa có kho hoạt động. Vui lòng liên hệ quản lý thư viện.</p>
      )}

      <form onSubmit={submit} className="mt-5" noValidate>
        <fieldset disabled={saving || reviewing || loading || !!loadError}>
          <div className="grid gap-5 sm:grid-cols-2">
            <Input
              id="bulk-copy-quantity"
              type="number"
              min="1"
              max="50"
              step="1"
              inputMode="numeric"
              label="Số lượng bản sao *"
              required
              value={quantity}
              onChange={(event) => setQuantity(event.target.value)}
              placeholder="Ví dụ: 10"
            />

            <div>
              <label htmlFor="bulk-copy-warehouse" className="mb-2 block text-sm font-medium text-slate-700">
                Kho *
              </label>
              <select
                id="bulk-copy-warehouse"
                className={selectClass}
                value={warehouseId}
                required
                onChange={(event) => {
                  setWarehouseId(event.target.value)
                  setShelfId('')
                }}
              >
                <option value="">Chọn kho</option>
                {warehouses.map((warehouse) => (
                  <option key={warehouse.id} value={warehouse.id}>
                    {warehouse.code} — {warehouse.name}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label htmlFor="bulk-copy-shelf" className="mb-2 block text-sm font-medium text-slate-700">
                Kệ *
              </label>
              <select
                id="bulk-copy-shelf"
                className={selectClass}
                value={shelfId}
                required
                disabled={!warehouseId || saving}
                onChange={(event) => setShelfId(event.target.value)}
              >
                <option value="">Chọn kệ thuộc kho</option>
                {shelfOptions.map((shelf) => (
                  <option key={shelf.id} value={shelf.id}>
                    {shelf.code} — {shelf.name}
                  </option>
                ))}
              </select>
              {warehouseId && shelfOptions.length === 0 && (
                <p className="mt-1 text-sm text-amber-700">Kho chưa có kệ hoạt động.</p>
              )}
            </div>

            <Input
              id="bulk-copy-date"
              type="date"
              label="Ngày nhập *"
              min="0001-01-01"
              max={todayInVietnam()}
              required
              value={receivedDate}
              onChange={(event) => setReceivedDate(event.target.value)}
            />
          </div>
        </fieldset>

        <div aria-live="polite" className="mt-5 rounded-lg border border-blue-200 bg-blue-50 p-4 text-sm text-slate-800">
          <h4 className="font-semibold">Khoảng mã thực tế dự kiến sau khi bỏ qua mã trùng</h4>
          {currentPreview ? <>
            <p className="mt-2">Mã bắt đầu: <strong>{currentPreview.startBarcode}</strong></p>
            <p>Mã kết thúc: <strong>{currentPreview.endBarcode}</strong></p>
            <p>Số lượng mã: <strong>{currentPreview.quantity}</strong></p>
            <p className="mt-2 font-medium">Mã đã tồn tại sẽ bỏ qua ({currentPreview.skippedBarcodes.length}):</p>
            <p className="max-h-40 overflow-y-auto break-words">
              {currentPreview.skippedBarcodes.length ? currentPreview.skippedBarcodes.join(', ') : 'Không có mã trùng.'}
            </p>
            <p className="mt-2">Khoảng mã trên không bao gồm các mã bị bỏ qua; vẫn tạo đủ {currentPreview.quantity} bản sao.</p>
            <p className="mt-2">Khoảng mã chưa được giữ chỗ; hệ thống kiểm tra lại khi xác nhận.</p>
          </> : <p>{previewError || 'Nhập số lượng từ 1 đến 50 để xem khoảng mã dự kiến.'}</p>}
          <Button type="button" variant="secondary" disabled={saving} onClick={() => setRefresh(value => value + 1)}>Cập nhật khoảng mã</Button>
        </div>
        {reviewing && <div role="status" className="mt-4 rounded-lg bg-amber-50 p-4 text-sm text-amber-900">
          Kiểm tra khoảng mã, kho, kệ và ngày nhập phía trên. Chỉ khi bấm “Xác nhận tạo lô” hệ thống mới tạo bản sao.
        </div>}

        {error && (
          <div role="alert" className="mt-5 rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">
            {error}
          </div>
        )}

        <div className="mt-6 flex flex-wrap gap-3">
          <Button
            type="submit"
            loading={saving}
            disabled={saving || loading || !!loadError || warehouses.length === 0 || !currentPreview}
          >
            {saving ? 'Đang tạo lô…' : reviewing ? 'Xác nhận tạo lô' : 'Tiếp tục xác nhận'}
          </Button>
          {reviewing && <Button type="button" variant="secondary" disabled={saving} onClick={() => setReviewing(false)}>Quay lại chỉnh sửa</Button>}
          <Button type="button" variant="secondary" disabled={saving} onClick={onCancel}>
            Hủy
          </Button>
        </div>
      </form>
    </Card>
  )
}
