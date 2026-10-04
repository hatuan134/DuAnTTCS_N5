import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import Button from '../../components/ui/Button'
import Card from '../../components/ui/Card'
import PageHeader from '../../components/ui/PageHeader'
import { getCurrentUser } from '../../core/auth/authStorage'
import type { Book } from '../s1-08-catalog/catalogService'
import { bookCopyService, copyError } from '../s2-02-book-copies/bookCopyService'
import BookCoverImage from './BookCoverImage'
import { COVER_RULE, MAX_COVER_BYTES, uploadBookCover } from './bookCoverService'

export default function EditBookCoverPage() {
  const { bookId } = useParams()
  return <CoverEditor key={bookId} id={Number(bookId)} />
}

function CoverEditor({ id }: { id: number }) {
  const navigate = useNavigate()
  const allowed = ['LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN'].includes(getCurrentUser()?.role ?? '')
  const [book, setBook] = useState<Book | null>(null)
  const [file, setFile] = useState<File | null>(null)
  const [error, setError] = useState('')
  const [loadError, setLoadError] = useState('')
  const [saving, setSaving] = useState(false)
  const pending = useRef(false)
  const active = useRef(true)
  useEffect(() => {
    active.current = true
    if (allowed && Number.isSafeInteger(id) && id > 0) {
      void bookCopyService.getBook(id).then((data) => {
        if (active.current) setBook(data)
      }).catch((e: unknown) => { if (active.current) setLoadError(copyError(e).message) })
    } else if (allowed) setLoadError('Mã đầu sách không hợp lệ.')
    return () => { active.current = false }
  }, [id, allowed])

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!file || !book || pending.current) return
    pending.current = true
    setSaving(true)
    setError('')
    try {
      await uploadBookCover(id, file)
      if (active.current) navigate(`/books/${id}`, { replace: true,
        state: { successMessage: book.coverImageUrl ? 'Thay thế ảnh bìa thành công.' : 'Tải ảnh bìa thành công.' } })
    } catch (e) {
      if (active.current) setError(copyError(e).message)
    } finally {
      pending.current = false
      if (active.current) setSaving(false)
    }
  }

  if (!allowed) return <p role="alert">Bạn không có quyền truy cập chức năng này.</p>
  return <div>
    {!saving && <Link to={`/books/${id}`} className="mb-4 inline-block text-sm font-medium text-blue-600 hover:underline">← Chi tiết đầu sách</Link>}
    <PageHeader title="Chỉnh sửa ảnh bìa" description="Tải ảnh mới hoặc thay thế ảnh bìa hiện tại của đầu sách." />
    {loadError && <p role="alert" className="rounded-lg bg-red-50 p-4 text-red-700">{loadError}</p>}
    {!book && !loadError && <p role="status">Đang tải đầu sách…</p>}
    {book && <Card className="max-w-2xl p-6">
      <h3 className="text-lg font-semibold text-slate-900">{book.title}</h3>
      <p className="mt-1 text-sm text-slate-500">Mã đầu sách: #{book.id} · ISBN: {book.isbn || 'Chưa ghi nhận'}</p>
      {book.coverImageUrl && <div className="mt-4">
        <p className="text-sm font-medium text-slate-700">Ảnh bìa hiện tại</p>
        <BookCoverImage bookId={book.id} url={book.coverImageUrl} title={book.title} />
      </div>}
      <form onSubmit={submit} className="mt-5 space-y-4">
        <div>
          <label htmlFor="book-cover-file" className="block text-sm font-medium text-slate-700">Ảnh bìa <span className="text-red-600">*</span></label>
          <p id="cover-help" className="mt-1 text-sm text-slate-500">{COVER_RULE}</p>
          {book.coverImageUrl && <p className="mt-1 text-sm text-slate-500">Ảnh cũ chỉ được thay sau khi ảnh mới được lưu thành công.</p>}
          <input id="book-cover-file" type="file" accept=".jpg,.jpeg,.png,image/jpeg,image/png"
            aria-describedby="cover-help" required disabled={saving}
            className="mt-3 block w-full rounded-lg border border-slate-300 p-3 text-sm file:mr-4 file:rounded-md file:border-0 file:bg-blue-50 file:px-3 file:py-2 file:text-blue-700"
            onChange={(event) => {
              setError('')
              const selected = event.target.files?.[0] ?? null
              setFile(null)
              if (!selected) return
              if (!/\.(jpe?g|png)$/i.test(selected.name) || selected.size === 0 || selected.size > MAX_COVER_BYTES) {
                setError(COVER_RULE)
                event.target.value = ''
                return
              }
              setFile(selected)
            }} />
        </div>
        {file && <p className="break-all text-sm text-slate-600">Đã chọn: {file.name} ({file.size.toLocaleString('vi-VN')} byte)</p>}
        {error && <p role="alert" className="rounded-lg bg-red-50 p-3 text-sm text-red-700">{error}</p>}
        <div className="flex flex-wrap gap-3">
          <Button type="submit" disabled={!file} loading={saving}>{saving ? 'Đang tải ảnh…' : book.coverImageUrl ? 'Thay thế ảnh bìa' : 'Lưu ảnh bìa'}</Button>
          <Button type="button" variant="secondary" disabled={saving} onClick={() => navigate(`/books/${id}`)}>Hủy</Button>
        </div>
      </form>
    </Card>}
  </div>
}
