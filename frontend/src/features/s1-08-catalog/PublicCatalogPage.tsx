import { useEffect, useMemo, useState } from 'react'
import { BookOpen, LogIn, RefreshCw, Search } from 'lucide-react'
import { Link } from 'react-router-dom'

import { catalogService } from './catalogService'
import type { Book } from './catalogService'

function authorNames(book: Book) {
  if (book.authors?.length) {
    return book.authors.map((author) => author.name).join(', ')
  }
  return book.authorName || 'Không rõ'
}

export default function PublicCatalogPage() {
  const [books, setBooks] = useState<Book[]>([])
  const [search, setSearch] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const loadBooks = async () => {
    setLoading(true)
    setError('')
    try {
      setBooks(await catalogService.getPublicBooks())
    } catch {
      setError('Không thể tải dữ liệu tra cứu đầu sách. Vui lòng thử lại.')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void loadBooks()
  }, [])

  const filteredBooks = useMemo(() => {
    const keyword = search.trim().toLowerCase()
    if (!keyword) return books

    return books.filter((book) => {
      const authors = authorNames(book).toLowerCase()
      return (
        book.title.toLowerCase().includes(keyword) ||
        (book.subtitle?.toLowerCase().includes(keyword) ?? false) ||
        authors.includes(keyword) ||
        book.categoryName.toLowerCase().includes(keyword) ||
        (book.publisher?.toLowerCase().includes(keyword) ?? false) ||
        (book.isbn?.toLowerCase().includes(keyword) ?? false)
      )
    })
  }, [books, search])

  return (
    <div className="min-h-screen bg-slate-50">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-7xl flex-wrap items-center justify-between gap-4 px-4 py-4 sm:px-6 lg:px-8">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-blue-600 text-white">
              <BookOpen size={22} />
            </div>
            <div>
              <p className="font-semibold text-slate-900">LIBRA</p>
              <p className="text-xs text-slate-500">Tra cứu đầu sách công khai</p>
            </div>
          </div>

          <Link
            to="/login"
            className="inline-flex items-center gap-2 rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-semibold text-slate-700 transition hover:bg-slate-50"
          >
            <LogIn size={17} />
            Đăng nhập
          </Link>
        </div>
      </header>

      <main className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
        <section className="rounded-2xl bg-slate-900 px-6 py-8 text-white shadow-sm sm:px-8">
          <p className="text-sm font-semibold uppercase tracking-[0.18em] text-blue-300">Thư viện</p>
          <h1 className="mt-2 text-3xl font-bold tracking-tight sm:text-4xl">Tra cứu đầu sách</h1>
          <p className="mt-3 max-w-2xl text-sm leading-6 text-slate-300">
            Tìm theo nhan đề, tác giả, ISBN, thể loại hoặc nhà xuất bản. Chỉ các đầu sách đã có ít nhất một bản sao mới xuất hiện tại đây.
          </p>

          <div className="relative mt-6 max-w-3xl">
            <Search
              size={19}
              className="pointer-events-none absolute left-4 top-1/2 -translate-y-1/2 text-slate-400"
            />
            <input
              type="search"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Ví dụ: Nguyễn Nhật Ánh, Văn học, 978604..."
              className="h-12 w-full rounded-xl border border-slate-700 bg-white pl-11 pr-4 text-sm text-slate-900 outline-none transition focus:border-blue-500 focus:ring-4 focus:ring-blue-500/20"
              aria-label="Tìm đầu sách"
            />
          </div>
        </section>

        <section className="mt-6">
          <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
            <div>
              <h2 className="text-xl font-semibold text-slate-900">Kết quả tra cứu</h2>
              <p className="mt-1 text-sm text-slate-500">
                {loading ? 'Đang tải dữ liệu…' : `${filteredBooks.length} đầu sách phù hợp`}
              </p>
            </div>

            <button
              type="button"
              onClick={() => void loadBooks()}
              disabled={loading}
              className="inline-flex items-center gap-2 rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm font-medium text-slate-700 shadow-sm transition hover:bg-slate-50 disabled:opacity-50"
            >
              <RefreshCw size={16} className={loading ? 'animate-spin' : ''} />
              Làm mới
            </button>
          </div>

          {error && (
            <div role="alert" className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
              {error}
            </div>
          )}

          {!error && !loading && filteredBooks.length === 0 && (
            <div className="rounded-2xl border border-dashed border-slate-300 bg-white px-6 py-12 text-center">
              <BookOpen size={38} className="mx-auto text-slate-300" />
              <p className="mt-3 font-medium text-slate-700">Không tìm thấy đầu sách phù hợp.</p>
              <p className="mt-1 text-sm text-slate-500">
                Đầu sách chưa có bản sao sẽ không xuất hiện trong tra cứu công khai.
              </p>
            </div>
          )}

          {!error && filteredBooks.length > 0 && (
            <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
              {filteredBooks.map((book) => (
                <article
                  key={book.id}
                  className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm"
                >
                  <div className="flex items-start justify-between gap-3">
                    <div className="min-w-0">
                      <h3 className="text-lg font-semibold text-slate-900">{book.title}</h3>
                      {book.subtitle && (
                        <p className="mt-1 text-sm font-medium text-slate-500">{book.subtitle}</p>
                      )}
                    </div>
                    <span className="shrink-0 rounded-full bg-emerald-50 px-2.5 py-1 text-xs font-semibold text-emerald-700">
                      Có bản sao
                    </span>
                  </div>

                  <dl className="mt-5 space-y-3 text-sm">
                    <div>
                      <dt className="text-slate-500">Tác giả</dt>
                      <dd className="mt-0.5 font-medium text-slate-800">{authorNames(book)}</dd>
                    </div>
                    <div className="grid grid-cols-2 gap-3">
                      <div>
                        <dt className="text-slate-500">Thể loại</dt>
                        <dd className="mt-0.5 font-medium text-slate-800">{book.categoryName}</dd>
                      </div>
                      <div>
                        <dt className="text-slate-500">Năm XB</dt>
                        <dd className="mt-0.5 font-medium text-slate-800">{book.publicationYear ?? '—'}</dd>
                      </div>
                    </div>
                    <div>
                      <dt className="text-slate-500">Nhà xuất bản</dt>
                      <dd className="mt-0.5 font-medium text-slate-800">{book.publisher || '—'}</dd>
                    </div>
                    <div className="flex flex-wrap items-center justify-between gap-3 border-t border-slate-100 pt-3">
                      <span className="text-xs text-slate-500">
                        {book.isbn ? `ISBN: ${book.isbn}` : 'Chưa có ISBN'}
                      </span>
                      <span className="text-xs font-semibold text-emerald-700">
                        {book.copyCount} bản sao
                      </span>
                    </div>
                  </dl>
                </article>
              ))}
            </div>
          )}
        </section>
      </main>
    </div>
  )
}
