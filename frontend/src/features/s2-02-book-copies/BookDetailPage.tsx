import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import Button from '../../components/ui/Button'
import Card from '../../components/ui/Card'
import PageHeader from '../../components/ui/PageHeader'
import { getCurrentUser } from '../../core/auth/authStorage'
import type { Book } from '../s1-08-catalog/catalogService'
import BookCopyStatusBadge from './BookCopyStatusBadge'
import CreateBookCopyForm from './CreateBookCopyForm'
import { bookCopyService, copyError } from './bookCopyService'
import type { BookCopy } from './bookCopyService'

function formatDate(value: string | null) {
  if (!value) return 'Chưa ghi nhận'
  return value.split('-').reverse().join('/')
}

function locationLabel(copy: BookCopy) {
  const warehouse = `${copy.warehouseCode} — ${copy.warehouseName}`
  const shelf = copy.shelfName
    ? `${copy.shelfCode} — ${copy.shelfName}`
    : copy.shelfCode
  return { warehouse, shelf }
}

export default function BookDetailPage() {
  const { bookId } = useParams()
  const id = Number(bookId)
  const allowed = ['LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN'].includes(getCurrentUser()?.role ?? '')
  const [book, setBook] = useState<Book | null>(null)
  const [copies, setCopies] = useState<BookCopy[]>([])
  const [error, setError] = useState('')
  const [showForm, setShowForm] = useState(false)
  const [reload, setReload] = useState(0)

  useEffect(() => {
    let active = true
    setBook(null)
    setCopies([])
    setError('')
    setShowForm(false)

    if (!allowed) return
    if (!Number.isSafeInteger(id) || id < 1) {
      setError('Mã đầu sách không hợp lệ.')
      return
    }

    Promise.all([
      bookCopyService.getBook(id),
      bookCopyService.getCopiesByBook(id),
    ])
      .then(([bookData, copyData]) => {
        if (!active) return
        setBook(bookData)
        setCopies(copyData)
      })
      .catch((e) => {
        if (active) setError(copyError(e).message)
      })

    return () => {
      active = false
    }
  }, [id, allowed, reload])

  if (!allowed) return <p role="alert">Bạn không có quyền truy cập chức năng này.</p>

  return (
    <div>
      <Link
        to="/cataloging"
        className="mb-4 inline-block text-sm font-medium text-blue-600 hover:underline"
      >
        ← Sách đã biên mục
      </Link>

      <PageHeader
        title="Chi tiết đầu sách"
        description="Xem thông tin đầu sách, các bản sao cá biệt và vị trí hiện tại."
      />

      {error && (
        <div role="alert" className="rounded-lg bg-red-50 p-4 text-red-700">
          {error}{' '}
          <Button
            type="button"
            variant="secondary"
            onClick={() => setReload((value) => value + 1)}
          >
            Thử lại
          </Button>
        </div>
      )}

      {!error && (!book || book.id !== id) && <p role="status">Đang tải đầu sách…</p>}

      {book && book.id === id && (
        <>
          <Card className="p-6">
            <h3 className="text-xl font-semibold text-slate-900">{book.title}</h3>
            <dl className="mt-5 grid gap-4 text-sm sm:grid-cols-2">
              {[
                ['Mã đầu sách', `#${book.id}`],
                ['ISBN', book.isbn || 'Chưa ghi nhận'],
                ['Tác giả', book.authorName],
                ['Thể loại', book.categoryName],
                ['Nhà xuất bản', book.publisher || 'Chưa ghi nhận'],
                ['Năm xuất bản', book.publicationYear ?? 'Chưa ghi nhận'],
              ].map(([label, value]) => (
                <div key={label}>
                  <dt className="text-slate-500">{label}</dt>
                  <dd className="mt-1 font-medium text-slate-900">{value}</dd>
                </div>
              ))}
            </dl>
            {book.description && (
              <p className="mt-5 whitespace-pre-wrap text-sm text-slate-600">{book.description}</p>
            )}
            {!showForm && (
              <Button className="mt-6" type="button" onClick={() => setShowForm(true)}>
                Thêm bản sao
              </Button>
            )}
          </Card>

          {showForm && (
            <CreateBookCopyForm
              key={book.id}
              bookId={book.id}
              bookTitle={book.title}
              onCancel={() => setShowForm(false)}
            />
          )}

          <Card className="mt-6 overflow-hidden">
            <div className="border-b border-slate-200 px-6 py-5">
              <h3 className="text-lg font-semibold text-slate-900">Danh sách bản sao</h3>
              <p className="mt-1 text-sm text-slate-500">
                Các bản sao thuộc riêng đầu sách này, kèm vị trí và trạng thái hiện tại.
              </p>
            </div>

            {copies.length === 0 ? (
              <div className="px-6 py-10 text-center">
                <p className="font-medium text-slate-700">Đầu sách này chưa có bản sao.</p>
                <p className="mt-1 text-sm text-slate-500">
                  Chọn “Thêm bản sao” nếu cần tạo bản sao cá biệt đầu tiên.
                </p>
              </div>
            ) : (
              <div className="overflow-x-auto">
                <table className="min-w-full divide-y divide-slate-200 text-sm">
                  <thead className="bg-slate-50 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
                    <tr>
                      <th className="px-5 py-3">Mã vạch</th>
                      <th className="px-5 py-3">Kho</th>
                      <th className="px-5 py-3">Kệ</th>
                      <th className="px-5 py-3">Ngày nhập</th>
                      <th className="px-5 py-3">Tình trạng vật lý</th>
                      <th className="px-5 py-3">Trạng thái</th>
                      <th className="px-5 py-3 text-right">Thao tác</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100 bg-white">
                    {copies.map((copy) => {
                      const location = locationLabel(copy)
                      return (
                        <tr key={copy.id} className="align-top hover:bg-slate-50">
                          <td className="px-5 py-4">
                            <Link
                              to={`/book-copies/${copy.id}`}
                              className="break-all font-semibold text-blue-700 hover:underline"
                            >
                              {copy.barcode}
                            </Link>
                            <div className="mt-1 text-xs text-slate-400">Bản sao #{copy.id}</div>
                          </td>
                          <td className="px-5 py-4 text-slate-700">{location.warehouse}</td>
                          <td className="px-5 py-4 text-slate-700">{location.shelf}</td>
                          <td className="whitespace-nowrap px-5 py-4 text-slate-700">
                            {formatDate(copy.receivedDate)}
                          </td>
                          <td className="px-5 py-4 text-slate-700">{copy.physicalConditionLabel}</td>
                          <td className="px-5 py-4">
                            <BookCopyStatusBadge status={copy.status} label={copy.statusLabel} />
                          </td>
                          <td className="px-5 py-4 text-right">
                            <Link
                              to={`/book-copies/${copy.id}`}
                              className="whitespace-nowrap font-semibold text-blue-600 hover:underline"
                            >
                              Xem chi tiết
                            </Link>
                          </td>
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </Card>
        </>
      )}
    </div>
  )
}
