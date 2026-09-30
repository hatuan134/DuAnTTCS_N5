import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { getCurrentUser } from '../../core/auth/authStorage'
import Card from '../../components/ui/Card'
import Button from '../../components/ui/Button'
import PageHeader from '../../components/ui/PageHeader'
import type { Book } from '../s1-08-catalog/catalogService'
import { bookCopyService, copyError } from './bookCopyService'
import CreateBookCopyForm from './CreateBookCopyForm'

export default function BookDetailPage() {
  const { bookId } = useParams()
  const id = Number(bookId)
  const allowed = ['LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN'].includes(getCurrentUser()?.role ?? '')
  const [book, setBook] = useState<Book | null>(null)
  const [error, setError] = useState('')
  const [showForm, setShowForm] = useState(false)
  const [reload, setReload] = useState(0)
  useEffect(() => {
    let active = true
    setBook(null); setError(''); setShowForm(false)
    if (!allowed) return
    if (!Number.isSafeInteger(id) || id < 1) { setError('Mã đầu sách không hợp lệ.'); return }
    bookCopyService.getBook(id).then(data => { if (active) setBook(data) })
      .catch(e => { if (active) setError(copyError(e).message) })
    return () => { active = false }
  }, [id, allowed, reload])
  if (!allowed) return <p role="alert">Bạn không có quyền truy cập chức năng này.</p>
  return (
    <div>
      <Link to="/cataloging" className="mb-4 inline-block text-sm font-medium text-blue-600 hover:underline">← Sách đã biên mục</Link>
      <PageHeader title="Chi tiết đầu sách" description="Xem thông tin đầu sách và thêm bản sao cá biệt." />
      {error && <div role="alert" className="rounded-lg bg-red-50 p-4 text-red-700">{error} <Button type="button" variant="secondary" onClick={() => setReload(v => v + 1)}>Thử lại</Button></div>}
      {!error && (!book || book.id !== id) && <p role="status">Đang tải đầu sách…</p>}
      {book && book.id === id && <>
        <Card className="p-6">
          <h3 className="text-xl font-semibold text-slate-900">{book.title}</h3>
          <dl className="mt-5 grid gap-4 text-sm sm:grid-cols-2">
            {[
              ['Mã đầu sách', `#${book.id}`], ['ISBN', book.isbn || 'Chưa ghi nhận'],
              ['Tác giả', book.authorName], ['Thể loại', book.categoryName],
              ['Nhà xuất bản', book.publisher || 'Chưa ghi nhận'], ['Năm xuất bản', book.publicationYear ?? 'Chưa ghi nhận'],
            ].map(([label, value]) => <div key={label}><dt className="text-slate-500">{label}</dt><dd className="mt-1 font-medium text-slate-900">{value}</dd></div>)}
          </dl>
          {book.description && <p className="mt-5 whitespace-pre-wrap text-sm text-slate-600">{book.description}</p>}
          {!showForm && <Button className="mt-6" type="button" onClick={() => setShowForm(true)}>Thêm bản sao</Button>}
        </Card>
        {showForm && <CreateBookCopyForm key={book.id} bookId={book.id} bookTitle={book.title} onCancel={() => setShowForm(false)} />}
      </>}
    </div>
  )
}
