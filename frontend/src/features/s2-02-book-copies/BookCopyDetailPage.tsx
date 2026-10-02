import { useEffect, useState } from 'react'
import { Link, useLocation, useParams } from 'react-router-dom'
import Card from '../../components/ui/Card'
import Button from '../../components/ui/Button'
import PageHeader from '../../components/ui/PageHeader'
import BookCopyStatusBadge from './BookCopyStatusBadge'
import RepairBookCopyPanel from './RepairBookCopyPanel'
import EditBookCopyForm from './EditBookCopyForm'
import { getCurrentUser } from '../../core/auth/authStorage'
import { bookCopyService, copyError } from './bookCopyService'
import type { BookCopy } from './bookCopyService'

interface CreatedCopyState {
  created?: boolean
  generatedBarcode?: string
}

export default function BookCopyDetailPage() {
  const { copyId } = useParams()
  const id = Number(copyId)
  const location = useLocation()
  const createdState = location.state as CreatedCopyState | null
  const allowed = ['LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN'].includes(getCurrentUser()?.role ?? '')
  const [copy, setCopy] = useState<BookCopy | null>(null)
  const [error, setError] = useState('')
  const [reload, setReload] = useState(0)
  const [editing, setEditing] = useState(false)
  const [saved, setSaved] = useState(false)
  useEffect(() => {
    let active = true
    setCopy(null); setError(''); setEditing(false); setSaved(false)
    if (!allowed) return
    if (!Number.isSafeInteger(id) || id < 1) { setError('Mã bản sao không hợp lệ.'); return }
    bookCopyService.getCopy(id).then(data => { if (active) setCopy(data) })
      .catch(e => { if (active) setError(copyError(e).message) })
    return () => { active = false }
  }, [id, allowed, reload])
  if (!allowed) return <p role="alert">Bạn không có quyền truy cập chức năng này.</p>
  return (
    <div>
      <PageHeader title="Chi tiết bản sao" description="Thông tin nhận diện và vị trí lưu trữ của bản sao cá biệt." />
      {createdState?.created && !saved && <p role="status" className="mb-5 rounded-lg border border-emerald-200 bg-emerald-50 p-4 text-emerald-800">
        Đã tạo bản sao thành công.
        {createdState.generatedBarcode && <> Mã vạch hệ thống cấp: <strong>{createdState.generatedBarcode}</strong>.</>}
      </p>}
      {saved && <p role="status" className="mb-5 rounded-lg border border-emerald-200 bg-emerald-50 p-4 text-emerald-800">Đã cập nhật thông tin và vị trí bản sao thành công. Mã vạch được giữ nguyên.</p>}
      {error && <div role="alert" className="rounded-lg bg-red-50 p-4 text-red-700">{error} <Button type="button" variant="secondary" onClick={() => setReload(v => v + 1)}>Thử lại</Button></div>}
      {!error && (!copy || copy.id !== id) && <p role="status">Đang tải bản sao…</p>}
      {copy && copy.id === id && <Card className="p-6">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h3 className="break-all text-xl font-semibold text-slate-900">{copy.barcode}</h3>
          <BookCopyStatusBadge status={copy.status} label={copy.statusLabel} />
        </div>
        <p className="mt-2 text-sm text-slate-500">Bản sao #{copy.id}</p>
        <div className="mt-5 rounded-lg bg-slate-50 p-4">
          <p className="text-sm text-slate-500">Thuộc đầu sách #{copy.bookId}</p>
          <Link to={`/books/${copy.bookId}`} className="mt-1 inline-block font-semibold text-blue-700 hover:underline">{copy.bookTitle}</Link>
          <p className="mt-1 text-sm text-slate-600">ISBN: {copy.isbn || 'Chưa ghi nhận'}</p>
        </div>
        <dl className="mt-5 grid gap-5 text-sm sm:grid-cols-2">
          {[
            ['Kho', `${copy.warehouseCode} — ${copy.warehouseName}`],
            ['Kệ', `${copy.shelfCode}${copy.shelfName ? ` — ${copy.shelfName}` : ''}`],
            ['Ngày nhập', copy.receivedDate ? copy.receivedDate.split('-').reverse().join('/') : 'Chưa ghi nhận'],
            ['Giá bìa', copy.coverPrice == null ? 'Chưa ghi nhận' : `${Number(copy.coverPrice).toLocaleString('vi-VN', { maximumFractionDigits: 2 })} VNĐ`],
            ['Tình trạng vật lý', copy.physicalConditionLabel], ['Trạng thái', copy.statusLabel],
          ].map(([label, value]) => <div key={label}><dt className="text-slate-500">{label}</dt><dd className="mt-1 font-medium text-slate-900">{value}</dd></div>)}
        </dl>
        <div className="mt-5 text-sm"><p className="text-slate-500">Ghi chú</p><p className="mt-1 whitespace-pre-wrap break-words text-slate-900">{copy.notes || 'Chưa ghi nhận'}</p></div>
        {!editing && <div className="mt-6"><Button type="button" onClick={() => { setEditing(true); setSaved(false) }}>Sửa thông tin và vị trí</Button></div>}
        {editing && <EditBookCopyForm key={copy.id} copy={copy} onCancel={() => setEditing(false)} onSaved={updated => {
          setCopy(updated); setEditing(false); setSaved(true)
        }} />}
        {!editing && <RepairBookCopyPanel key={copy.id} copy={copy} onSaved={updated => { setCopy(updated); setSaved(false) }} />}
        <p className="mt-6 text-sm text-slate-500">Bản sao được gắn cố định với đầu sách. Không hỗ trợ chuyển sang đầu sách khác.</p>
        <Link to={`/books/${copy.bookId}`} className="mt-5 inline-block font-medium text-blue-600 hover:underline">← Về chi tiết đầu sách</Link>
      </Card>}
    </div>
  )
}
