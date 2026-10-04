import { useEffect, useRef, useState } from 'react'
import { Link, useLocation, useParams } from 'react-router-dom'
import BookCoverImage from '../s2-10-book-cover/BookCoverImage'
import Button from '../../components/ui/Button'
import Card from '../../components/ui/Card'
import PageHeader from '../../components/ui/PageHeader'
import { getCurrentUser } from '../../core/auth/authStorage'
import type { Book } from '../s1-08-catalog/catalogService'
import BookCopyStatusBadge from './BookCopyStatusBadge'
import BulkCreateBookCopiesForm from './BulkCreateBookCopiesForm'
import BulkCreateBookCopiesResultPage from './BulkCreateBookCopiesResult'
import CreateBookCopyForm from './CreateBookCopyForm'
import { bookCopyService, copyError } from './bookCopyService'
import type { BookCopy, BookCopySummary, BulkCreateBookCopiesResult } from './bookCopyService'

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
  const location = useLocation()
  const successMessage = (location.state as { successMessage?: string } | null)?.successMessage
  const id = Number(bookId)
  const allowed = ['LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN'].includes(getCurrentUser()?.role ?? '')
  const [book, setBook] = useState<Book | null>(null)
  const [summary, setSummary] = useState<BookCopySummary | null>(null)
  const [refreshError, setRefreshError] = useState('')
  const [refreshing, setRefreshing] = useState(false)
  const refreshRef = useRef<() => void>(() => {})
  const copies = summary?.copies ?? []
  const [error, setError] = useState('')
  const [showForm, setShowForm] = useState(false)
  const [showBulkForm, setShowBulkForm] = useState(false)
  const [bulkSuccess, setBulkSuccess] = useState<BulkCreateBookCopiesResult | null>(null)
  const [reload, setReload] = useState(0)

  useEffect(() => {
    let active = true
    let pending = false
    let loadedBook: Book | null = null
    setBook(null)
    setSummary(null)
    setError('')
    setRefreshError('')
    setRefreshing(false)
    setShowForm(false)
    setShowBulkForm(false)
    refreshRef.current = () => {}

    if (!allowed) return
    if (!Number.isSafeInteger(id) || id < 1) {
      setError('Mã đầu sách không hợp lệ.')
      return
    }

    async function refresh() {
      if (!active || pending) return
      pending = true
      setRefreshing(true)
      try {
        const [bookData, copySummary] = await Promise.all([
          loadedBook ? Promise.resolve(loadedBook) : bookCopyService.getBook(id),
          bookCopyService.getCopySummary(id),
        ])
        if (!active) return
        loadedBook = bookData
        setBook(bookData)
        // One state update keeps the count and table on the same server snapshot.
        setSummary(copySummary)
        setError('')
        setRefreshError('')
      } catch (e) {
        if (!active) return
        if (loadedBook) setRefreshError(copyError(e).message)
        else setError(copyError(e).message)
      } finally {
        pending = false
        if (active) setRefreshing(false)
      }
    }

    const refreshWhenVisible = () => {
      if (document.visibilityState === 'visible') void refresh()
    }
    refreshRef.current = () => { void refresh() }
    void refresh()
    const timer = window.setInterval(refreshWhenVisible, 10000)
    window.addEventListener('focus', refreshWhenVisible)
    document.addEventListener('visibilitychange', refreshWhenVisible)

    return () => {
      active = false
      window.clearInterval(timer)
      window.removeEventListener('focus', refreshWhenVisible)
      document.removeEventListener('visibilitychange', refreshWhenVisible)
      refreshRef.current = () => {}
    }
  }, [id, allowed, reload])

  useEffect(() => { setBulkSuccess(null) }, [id])

  if (!allowed) return <p role="alert">Bạn không có quyền truy cập chức năng này.</p>

  if (bulkSuccess && book && book.id === id) {
    return (
      <BulkCreateBookCopiesResultPage
        bookTitle={book.title}
        result={bulkSuccess}
        onBack={() => {
          setBulkSuccess(null)
          setReload((value) => value + 1)
        }}
      />
    )
  }

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
        description="Xem đầy đủ thông tin thư mục của đầu sách và các bản sao cá biệt hiện có."
      />

      {successMessage && (
        <div
          role="status"
          className="mb-5 rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm font-medium text-emerald-800"
        >
          {successMessage}
        </div>
      )}

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

      {book && book.id === id && summary && (
        <>
          <Card className="p-6">
            <div className="flex flex-wrap items-center gap-3">
              <h3 className="text-xl font-semibold text-slate-900">{book.title}</h3>
              {copies.length === 0 && (
                <span className="inline-flex rounded-full border border-amber-200 bg-amber-50 px-2.5 py-1 text-xs font-semibold text-amber-800">
                  Chưa có bản sao
                </span>
              )}
            </div>
            {book.subtitle && (
              <p className="mt-1 text-sm font-medium text-slate-500">{book.subtitle}</p>
            )}
            {book.coverImageUrl && <BookCoverImage bookId={book.id} url={book.coverImageUrl} title={book.title} />}
            {allowed && <Link to={`/books/${book.id}/cover/edit`}
              className="mt-4 inline-block rounded-lg border border-slate-300 px-4 py-2 text-sm font-medium text-blue-700 hover:bg-slate-50">
              Chỉnh sửa ảnh bìa
            </Link>}
            <dl className="mt-5 grid gap-4 text-sm sm:grid-cols-2">
              {[
                ['Mã đầu sách', `#${book.id}`],
                ['ISBN', book.isbn || 'Chưa ghi nhận'],
                [
                  'Tác giả',
                  (book.authors?.length
                    ? book.authors.map((author) => author.name).join(', ')
                    : book.authorName) || 'Chưa ghi nhận',
                ],
                ['Thể loại', book.categoryName],
                ['Nhà xuất bản', book.publisher || 'Chưa ghi nhận'],
                ['Năm xuất bản', book.publicationYear ?? 'Chưa ghi nhận'],
                ['Số trang', book.pageCount ?? 'Chưa ghi nhận'],
              ].map(([label, value]) => (
                <div key={label}>
                  <dt className="text-slate-500">{label}</dt>
                  <dd className="mt-1 font-medium text-slate-900">{value}</dd>
                </div>
              ))}
            </dl>
            <div className="mt-5">
              <p className="text-sm font-medium text-slate-500">Tóm tắt nội dung</p>
              <p className="mt-1 whitespace-pre-wrap text-sm text-slate-700">
                {book.description || 'Chưa ghi nhận'}
              </p>
            </div>
            <Link to={`/books/${book.id}/reservations`}
              className="mt-6 inline-flex items-center rounded-lg border border-blue-200 bg-blue-50 px-4 py-2.5 text-sm font-semibold text-blue-700 hover:bg-blue-100">
              Xem hàng đợi đặt giữ
            </Link>
            {!showForm && !showBulkForm && (
              <div className="mt-6 flex flex-wrap gap-3">
                <Button
                  type="button"
                  onClick={() => { setBulkSuccess(null); setShowBulkForm(false); setShowForm(true) }}
                >
                  Thêm bản sao
                </Button>
                <Button
                  type="button"
                  variant="secondary"
                  onClick={() => { setBulkSuccess(null); setShowForm(false); setShowBulkForm(true) }}
                >
                  Thêm nhiều bản sao
                </Button>
              </div>
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

          {showBulkForm && (
            <BulkCreateBookCopiesForm
              key={`bulk-${book.id}`}
              bookId={book.id}
              bookTitle={book.title}
              onCancel={() => setShowBulkForm(false)}
              onCreated={(result) => {
                setBulkSuccess(result)
                setShowBulkForm(false)
              }}
            />
          )}

          <Card className="mt-6 overflow-hidden">
            <div className="border-b border-slate-200 px-6 py-5">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <h3 className="text-lg font-semibold text-slate-900">Danh sách bản sao</h3>
                <Button
                  type="button"
                  variant="secondary"
                  size="sm"
                  loading={refreshing}
                  onClick={() => refreshRef.current()}
                >
                  {refreshing ? 'Đang cập nhật…' : 'Làm mới'}
                </Button>
              </div>
              <p className="mt-1 text-sm text-slate-500">
                Các bản sao thuộc riêng đầu sách này, kèm vị trí và trạng thái hiện tại.
              </p>
              <div
                role="status"
                aria-live="polite"
                aria-atomic="true"
                className="mt-4 flex flex-wrap items-center gap-3 rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-emerald-900"
              >
                <span className="font-medium">Tổng số bản đang Sẵn sàng:</span>
                <strong className="text-3xl">{summary.availableCount}</strong>
                <span className="text-sm">bản có thể cho mượn</span>
              </div>
              <p className="mt-2 text-xs text-slate-500">
                Tự cập nhật mỗi 10 giây khi đang xem trang và khi quay lại cửa sổ.
              </p>
              {refreshError && (
                <div role="alert" className="mt-3 rounded-lg bg-amber-50 p-3 text-sm text-amber-800">
                  Chưa cập nhật được dữ liệu. Bảng và tổng đang hiển thị lần tải thành công gần nhất.
                  {' '}{refreshError} Nhấn “Làm mới” để thử lại.
                </div>
              )}
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
