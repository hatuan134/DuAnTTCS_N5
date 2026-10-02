import { useEffect, useState } from 'react'
import { ArrowLeft, BookOpen, LogIn } from 'lucide-react'
import { Link, useParams } from 'react-router-dom'

import { catalogService } from './catalogService'
import type { Book } from './catalogService'

function authorNames(book: Book) {
  if (book.authors?.length) {
    return book.authors.map((author) => author.name).join(', ')
  }
  return book.authorName || 'Không rõ'
}

export default function PublicBookDetailPage() {
  const { bookId } = useParams<{ bookId: string }>()
  const [book, setBook] = useState<Book | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    const id = Number(bookId)
    if (!Number.isInteger(id) || id <= 0) {
      setError('Mã đầu sách không hợp lệ.')
      setLoading(false)
      return
    }

    let active = true

    const loadBook = async () => {
      setLoading(true)
      setError('')
      try {
        const data = await catalogService.getPublicBookById(id)
        if (active) {
          setBook(data)
        }
      } catch {
        if (active) {
          setBook(null)
          setError('Không tìm thấy đầu sách trên trang tra cứu công khai.')
        }
      } finally {
        if (active) {
          setLoading(false)
        }
      }
    }

    void loadBook()

    return () => {
      active = false
    }
  }, [bookId])

  return (
    <div className="min-h-screen bg-slate-50">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-5xl flex-wrap items-center justify-between gap-4 px-4 py-4 sm:px-6 lg:px-8">
          <Link to="/catalog" className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-blue-600 text-white">
              <BookOpen size={22} />
            </div>
            <div>
              <p className="font-semibold text-slate-900">LIBRA</p>
              <p className="text-xs text-slate-500">Chi tiết đầu sách công khai</p>
            </div>
          </Link>

          <Link
            to="/login"
            className="inline-flex items-center gap-2 rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-semibold text-slate-700 transition hover:bg-slate-50"
          >
            <LogIn size={17} />
            Đăng nhập
          </Link>
        </div>
      </header>

      <main className="mx-auto max-w-5xl px-4 py-8 sm:px-6 lg:px-8">
        <Link
          to="/catalog"
          className="inline-flex items-center gap-2 text-sm font-semibold text-blue-600 transition hover:text-blue-700"
        >
          <ArrowLeft size={17} />
          Quay lại tra cứu
        </Link>

        {loading && (
          <div className="mt-6 rounded-2xl border border-slate-200 bg-white p-8 text-sm text-slate-500 shadow-sm">
            Đang tải thông tin đầu sách…
          </div>
        )}

        {!loading && error && (
          <div role="alert" className="mt-6 rounded-2xl border border-red-200 bg-red-50 p-6 text-sm text-red-700">
            {error}
          </div>
        )}

        {!loading && !error && book && (
          <article className="mt-6 overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
            <div className="border-b border-slate-100 bg-slate-900 px-6 py-7 text-white sm:px-8">
              <p className="text-sm font-semibold uppercase tracking-[0.16em] text-blue-300">Đầu sách</p>
              <h1 className="mt-2 text-3xl font-bold tracking-tight">{book.title}</h1>
              {book.subtitle && (
                <p className="mt-2 text-sm text-slate-300">{book.subtitle}</p>
              )}
            </div>

            <div className="grid gap-6 p-6 sm:grid-cols-2 sm:p-8">
              <Info label="Tác giả" value={authorNames(book)} />
              <Info label="ISBN" value={book.isbn || '—'} />
              <Info label="Thể loại" value={book.categoryName} />
              <Info label="Năm xuất bản" value={book.publicationYear?.toString() ?? '—'} />
              <Info label="Nhà xuất bản" value={book.publisher || '—'} />
              <Info label="Số trang" value={book.pageCount?.toString() ?? '—'} />
              <div className="rounded-xl border border-emerald-200 bg-emerald-50 p-4">
                <p className="text-sm text-emerald-700">Số bản rảnh</p>
                <p className="mt-1 text-2xl font-bold text-emerald-800">{book.availableCount ?? 0}</p>
              </div>
              <div className="rounded-xl border border-slate-200 bg-slate-50 p-4">
                <p className="text-sm text-slate-500">Tổng số bản sao</p>
                <p className="mt-1 text-2xl font-bold text-slate-800">{book.copyCount}</p>
              </div>
            </div>

            {book.description && (
              <div className="border-t border-slate-100 px-6 py-6 sm:px-8">
                <h2 className="font-semibold text-slate-900">Mô tả</h2>
                <p className="mt-2 whitespace-pre-line text-sm leading-6 text-slate-600">
                  {book.description}
                </p>
              </div>
            )}
          </article>
        )}
      </main>
    </div>
  )
}

function Info({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <p className="text-sm text-slate-500">{label}</p>
      <p className="mt-1 font-semibold text-slate-900">{value}</p>
    </div>
  )
}
