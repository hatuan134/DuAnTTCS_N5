import { type FormEvent, useCallback, useEffect, useState } from 'react'
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
  const [keyword, setKeyword] = useState('')
  const [submittedKeyword, setSubmittedKeyword] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const loadBooks = useCallback(async (searchKeyword: string) => {
    setLoading(true)
    setError('')
    try {
      setBooks(await catalogService.getPublicBooks(searchKeyword))
    } catch {
      setError('Không thể tải dữ liệu tra cứu đầu sách. Vui lòng thử lại.')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void loadBooks('')
  }, [loadBooks])

  useEffect(() => {
    let active = true
    let running = false

    const refresh = async () => {
      if (running || document.hidden) return
      running = true
      try {
        const data = await catalogService.getPublicBooks(submittedKeyword)
        if (active) {
          setBooks(data)
          setError('')
        }
      } catch {
        if (active) {
          setError('Không thể cập nhật số bản sẵn sàng. Vui lòng thử lại.')
        }
      } finally {
        running = false
      }
    }

    const channel = 'BroadcastChannel' in window
      ? new BroadcastChannel('catalog-availability')
      : null

    if (channel) {
      channel.onmessage = () => void refresh()
    }

    const timer = window.setInterval(() => void refresh(), 3000)
    window.addEventListener('focus', refresh)
    document.addEventListener('visibilitychange', refresh)

    return () => {
      active = false
      window.clearInterval(timer)
      channel?.close()
      window.removeEventListener('focus', refresh)
      document.removeEventListener('visibilitychange', refresh)
    }
  }, [submittedKeyword])

  const handleSearch = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const nextKeyword = keyword.trim()
    setSubmittedKeyword(nextKeyword)
    void loadBooks(nextKeyword)
  }

  const handleRefresh = () => {
    void loadBooks(submittedKeyword)
  }

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
            Tìm theo nhan đề, tác giả hoặc ISBN. Hệ thống không phân biệt chữ hoa, chữ thường và hỗ trợ tìm tiếng Việt không dấu.
          </p>

          <form onSubmit={handleSearch} className="mt-6 flex max-w-3xl flex-col gap-3 sm:flex-row">
            <div className="relative flex-1">
              <Search
                size={19}
                className="pointer-events-none absolute left-4 top-1/2 -translate-y-1/2 text-slate-400"
              />
              <input
                type="search"
                value={keyword}
                onChange={(event) => setKeyword(event.target.value)}
                placeholder="Ví dụ: Nguyễn Nhật Ánh, Mắt biếc, 978604..."
                className="h-12 w-full rounded-xl border border-slate-700 bg-white pl-11 pr-4 text-sm text-slate-900 outline-none transition focus:border-blue-500 focus:ring-4 focus:ring-blue-500/20"
                aria-label="Từ khóa tra cứu đầu sách"
              />
            </div>
            <button
              type="submit"
              disabled={loading}
              className="inline-flex h-12 items-center justify-center gap-2 rounded-xl bg-blue-600 px-5 text-sm font-semibold text-white transition hover:bg-blue-500 disabled:cursor-not-allowed disabled:opacity-60"
            >
              <Search size={18} />
              Tra cứu
            </button>
          </form>
        </section>

        <section className="mt-6">
          <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
            <div>
              <h2 className="text-xl font-semibold text-slate-900">Kết quả tra cứu</h2>
              <p className="mt-1 text-sm text-slate-500">
                {loading
                  ? 'Đang tải dữ liệu…'
                  : `${books.length} đầu sách phù hợp${submittedKeyword ? ` với “${submittedKeyword}”` : ''}`}
              </p>
            </div>

            <button
              type="button"
              onClick={handleRefresh}
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

          {!error && !loading && books.length === 0 && (
            <div className="rounded-2xl border border-dashed border-slate-300 bg-white px-6 py-12 text-center">
              <BookOpen size={38} className="mx-auto text-slate-300" />
              <p className="mt-3 font-medium text-slate-700">Không tìm thấy đầu sách phù hợp.</p>
            </div>
          )}

          {!error && books.length > 0 && (
            <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
              {books.map((book) => (
                <article
                  key={book.id}
                  className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm"
                >
                  <div className="flex items-start justify-between gap-3">
                    <div className="min-w-0">
                      <h3 className="text-lg font-semibold text-slate-900">{book.title}</h3>
                    </div>
                    <span className="shrink-0 rounded-full bg-emerald-50 px-2.5 py-1 text-xs font-semibold text-emerald-700">
                      {book.availableCount ?? 0} bản rảnh
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
                        <dt className="text-slate-500">Năm xuất bản</dt>
                        <dd className="mt-0.5 font-medium text-slate-800">{book.publicationYear ?? '—'}</dd>
                      </div>
                    </div>
                    <div>
                      <dt className="text-slate-500">ISBN</dt>
                      <dd className="mt-0.5 font-medium text-slate-800">{book.isbn || '—'}</dd>
                    </div>
                  </dl>

                  <div className="mt-5 border-t border-slate-100 pt-4">
                    <Link
                      to={`/catalog/books/${book.id}`}
                      className="inline-flex items-center text-sm font-semibold text-blue-600 transition hover:text-blue-700"
                    >
                      Xem chi tiết đầu sách
                    </Link>
                  </div>
                </article>
              ))}
            </div>
          )}
        </section>
      </main>
    </div>
  )
}
