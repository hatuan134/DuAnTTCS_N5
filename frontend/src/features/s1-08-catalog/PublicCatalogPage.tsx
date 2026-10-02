import { type FormEvent, useEffect, useState } from 'react'
import { isAxiosError } from 'axios'
import { BookOpen, Filter, LogIn, RefreshCw, Search, X } from 'lucide-react'
import { Link } from 'react-router-dom'

import { catalogService } from './catalogService'
import type { Book, PublicCatalogFilterOptions, PublicCatalogFilters } from './catalogService'

function authorNames(book: Book) {
  if (book.authors?.length) {
    return book.authors.map((author) => author.name).join(', ')
  }
  return book.authorName || 'Không rõ'
}

type SearchQuery = PublicCatalogFilters & { keyword: string }

function apiErrorMessage(error: unknown, fallback: string) {
  if (isAxiosError<{ message?: string }>(error) && error.response?.data?.message) {
    return error.response.data.message
  }
  return fallback
}

export default function PublicCatalogPage() {
  const [books, setBooks] = useState<Book[]>([])
  const [keyword, setKeyword] = useState('')
  const [filters, setFilters] = useState<PublicCatalogFilters>({})
  const [submittedQuery, setSubmittedQuery] = useState<SearchQuery>({ keyword: '' })
  const [options, setOptions] = useState<PublicCatalogFilterOptions>({ categories: [], publicationYears: [] })
  const [optionsLoading, setOptionsLoading] = useState(true)
  const [optionsError, setOptionsError] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    let active = true
    setOptionsLoading(true)
    setOptionsError('')

    const loadOptions = async () => {
      try {
        const data = await catalogService.getPublicFilterOptions(controller.signal)
        if (active) setOptions(data)
      } catch (error) {
        if (active) setOptionsError(apiErrorMessage(error, 'Không thể tải bộ lọc. Vui lòng bấm Làm mới để thử lại.'))
      } finally {
        if (active) setOptionsLoading(false)
      }
    }
    void loadOptions()
    return () => {
      active = false
      controller.abort()
    }
  }, [reloadKey])

  useEffect(() => {
    const controller = new AbortController()
    let active = true
    let running = false
    setLoading(true)
    setError('')

    const loadBooks = async (background = false) => {
      if (running || (background && document.hidden)) return
      running = true
      try {
        const data = await catalogService.getPublicBooks(
          submittedQuery.keyword, submittedQuery, controller.signal,
        )
        if (active) {
          setBooks(data)
          setError('')
        }
      } catch (error) {
        if (active) {
          setError(apiErrorMessage(error, 'Không thể tải dữ liệu tra cứu đầu sách. Vui lòng thử lại.'))
        }
      } finally {
        running = false
        if (active) setLoading(false)
      }
    }

    void loadBooks()
    const refresh = () => void loadBooks(true)
    const channel = 'BroadcastChannel' in window
      ? new BroadcastChannel('catalog-availability')
      : null
    if (channel) channel.onmessage = refresh
    const timer = window.setInterval(refresh, 3000)
    window.addEventListener('focus', refresh)
    document.addEventListener('visibilitychange', refresh)

    return () => {
      active = false
      controller.abort()
      window.clearInterval(timer)
      channel?.close()
      window.removeEventListener('focus', refresh)
      document.removeEventListener('visibilitychange', refresh)
    }
  }, [submittedQuery, reloadKey])

  const handleSearch = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (loading) return
    setSubmittedQuery({ ...filters, keyword: keyword.trim() })
  }

  const removeFilter = (key: keyof PublicCatalogFilters) => {
    const next = { ...submittedQuery }
    delete next[key]
    setKeyword(next.keyword)
    setFilters({ categoryId: next.categoryId, publicationYear: next.publicationYear, availableOnly: next.availableOnly })
    setSubmittedQuery(next)
  }

  const clearFilters = () => {
    setFilters({})
    setKeyword(submittedQuery.keyword)
    setSubmittedQuery({ keyword: submittedQuery.keyword })
  }

  const hasFilters = Boolean(submittedQuery.categoryId || submittedQuery.publicationYear || submittedQuery.availableOnly)
  const selectedCategory = options.categories.find((category) => category.id === submittedQuery.categoryId)
  const submittedKeyword = submittedQuery.keyword

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

          <form onSubmit={handleSearch} className="mt-6">
            <div className="flex max-w-3xl flex-col gap-3 sm:flex-row">
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
            </div>

            <fieldset disabled={loading} className="mt-5 grid max-w-4xl gap-4 sm:grid-cols-2 lg:grid-cols-3">
              <legend className="mb-3 flex items-center gap-2 text-sm font-semibold text-slate-200">
                <Filter size={16} /> Bộ lọc tra cứu
              </legend>
              <div>
                <label htmlFor="catalog-category" className="mb-1.5 block text-sm text-slate-300">Thể loại</label>
                <select
                  id="catalog-category"
                  value={filters.categoryId ?? ''}
                  onChange={(event) => setFilters((current) => ({ ...current, categoryId: event.target.value ? Number(event.target.value) : undefined }))}
                  disabled={optionsLoading || Boolean(optionsError)}
                  className="h-11 w-full rounded-xl border border-slate-700 bg-white px-3 text-sm text-slate-900 outline-none focus:ring-4 focus:ring-blue-500/20 disabled:opacity-60"
                >
                  <option value="">Tất cả thể loại</option>
                  {options.categories.map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}
                  {filters.categoryId && !options.categories.some((category) => category.id === filters.categoryId) && (
                    <option value={filters.categoryId}>Thể loại #{filters.categoryId}</option>
                  )}
                </select>
              </div>
              <div>
                <label htmlFor="catalog-year" className="mb-1.5 block text-sm text-slate-300">Năm xuất bản</label>
                <select
                  id="catalog-year"
                  value={filters.publicationYear ?? ''}
                  onChange={(event) => setFilters((current) => ({ ...current, publicationYear: event.target.value ? Number(event.target.value) : undefined }))}
                  disabled={optionsLoading || Boolean(optionsError)}
                  className="h-11 w-full rounded-xl border border-slate-700 bg-white px-3 text-sm text-slate-900 outline-none focus:ring-4 focus:ring-blue-500/20 disabled:opacity-60"
                >
                  <option value="">Tất cả năm xuất bản</option>
                  {options.publicationYears.map((year) => <option key={year} value={year}>{year}</option>)}
                  {filters.publicationYear && !options.publicationYears.includes(filters.publicationYear) && (
                    <option value={filters.publicationYear}>{filters.publicationYear}</option>
                  )}
                </select>
              </div>
              <label className="flex min-h-11 cursor-pointer items-center gap-3 self-end rounded-xl border border-slate-700 bg-slate-800 px-3 py-3 text-sm text-slate-200">
                <input
                  type="checkbox"
                  checked={filters.availableOnly ?? false}
                  onChange={(event) => setFilters((current) => ({ ...current, availableOnly: event.target.checked }))}
                  className="h-4 w-4 accent-blue-600"
                />
                Chỉ hiện sách còn bản rảnh
              </label>
            </fieldset>
            <p className="mt-3 text-xs text-slate-400">Chọn bộ lọc rồi bấm Tra cứu để áp dụng cùng từ khóa.</p>
          </form>
          {optionsError && <p role="alert" className="mt-3 text-sm text-red-300">{optionsError}</p>}
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
              onClick={() => setReloadKey((current) => current + 1)}
              disabled={loading}
              className="inline-flex items-center gap-2 rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm font-medium text-slate-700 shadow-sm transition hover:bg-slate-50 disabled:opacity-50"
            >
              <RefreshCw size={16} className={loading ? 'animate-spin' : ''} />
              Làm mới
            </button>
          </div>

          {hasFilters && (
            <div className="mb-4 flex flex-wrap items-center gap-2 text-sm" aria-label="Bộ lọc đang áp dụng">
              <span className="text-slate-500">Đang lọc:</span>
              {submittedQuery.categoryId && (
                <button type="button" disabled={loading} onClick={() => removeFilter('categoryId')}
                  aria-label="Xóa bộ lọc thể loại"
                  className="inline-flex items-center gap-2 rounded-full bg-blue-50 px-3 py-1.5 text-blue-700 disabled:opacity-50">
                  {selectedCategory?.name ?? `Thể loại #${submittedQuery.categoryId}`} <X size={14} />
                </button>
              )}
              {submittedQuery.publicationYear && (
                <button type="button" disabled={loading} onClick={() => removeFilter('publicationYear')}
                  aria-label="Xóa bộ lọc năm xuất bản"
                  className="inline-flex items-center gap-2 rounded-full bg-blue-50 px-3 py-1.5 text-blue-700 disabled:opacity-50">
                  Năm {submittedQuery.publicationYear} <X size={14} />
                </button>
              )}
              {submittedQuery.availableOnly && (
                <button type="button" disabled={loading} onClick={() => removeFilter('availableOnly')}
                  aria-label="Xóa bộ lọc còn bản rảnh"
                  className="inline-flex items-center gap-2 rounded-full bg-blue-50 px-3 py-1.5 text-blue-700 disabled:opacity-50">
                  Còn bản rảnh <X size={14} />
                </button>
              )}
              <button type="button" disabled={loading} onClick={clearFilters}
                className="px-2 py-1.5 font-medium text-slate-600 underline hover:text-blue-600 disabled:opacity-50">
                Xóa toàn bộ bộ lọc
              </button>
            </div>
          )}

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

          {!error && !loading && books.length > 0 && (
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
