import FeedbackAlert from '../../components/ui/FeedbackAlert'
import PublicBookCover from '../s2-10-book-cover/PublicBookCover'
import { type FormEvent, useEffect, useRef, useState } from 'react'
import { isAxiosError } from 'axios'
import { BookOpen, ChevronDown, Filter, LogIn, RefreshCw, Search, X } from 'lucide-react'
import { Link } from 'react-router-dom'

import PublicSiteFooter from './PublicSiteFooter'
import { catalogService } from './catalogService'
import type { Book, PublicCatalogFilterOptions, PublicCatalogFilters, PublicCatalogPage, PublicCatalogSearch, PublicCatalogSort } from './catalogService'

function authorNames(book: Book) {
  if (book.authors?.length) {
    return book.authors.map((author) => author.name).join(', ')
  }
  return book.authorName || 'Không rõ'
}

const initialQuery: PublicCatalogSearch = { keyword: '', page: 0, sort: 'relevance' }

function apiErrorMessage(error: unknown, fallback: string) {
  if (isAxiosError<{ message?: string }>(error) && error.response?.data?.message) {
    return error.response.data.message
  }
  return fallback
}

export default function PublicCatalogPage() {
  const keywordInputRef = useRef<HTMLInputElement>(null)
  const loadedPageRef = useRef(0)
  const [books, setBooks] = useState<Book[]>([])
  const [result, setResult] = useState<PublicCatalogPage | null>(null)
  const [keyword, setKeyword] = useState('')
  const [filters, setFilters] = useState<PublicCatalogFilters>({})
  const [submittedQuery, setSubmittedQuery] = useState<PublicCatalogSearch>(initialQuery)
  const [options, setOptions] = useState<PublicCatalogFilterOptions>({ categories: [], publicationYears: [] })
  const [optionsLoading, setOptionsLoading] = useState(true)
  const [optionsError, setOptionsError] = useState('')
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [error, setError] = useState('')
  const [dismissedError, setDismissedError] = useState('')
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
    setLoadingMore(false)
    setError(''); setDismissedError('')
    loadedPageRef.current = 0

    const loadBooks = async (background = false) => {
      if (running || (background && document.hidden)) return
      running = true
      try {
        if (!background) {
          const data = await catalogService.searchPublicBooks({ ...submittedQuery, page: 0 }, controller.signal)
          if (active) {
            loadedPageRef.current = 0
            setBooks(data.content)
            setResult(data)
            setError('')
          }
          return
        }

        const pages: PublicCatalogPage[] = []
        const maxRequestedPage = loadedPageRef.current
        for (let page = 0; page <= maxRequestedPage; page += 1) {
          const data = await catalogService.searchPublicBooks({ ...submittedQuery, page }, controller.signal)
          pages.push(data)
          if (data.last || data.page < page) break
        }
        if (active && pages.length > 0) {
          const unique = new Map<number, Book>()
          pages.flatMap((page) => page.content).forEach((book) => unique.set(book.id, book))
          const latest = pages[pages.length - 1]
          loadedPageRef.current = latest.page
          setBooks(Array.from(unique.values()))
          setResult(latest)
          setError('')
        }
      } catch (error) {
        if (active && !background) {
          setError(apiErrorMessage(error, 'Không thể tải dữ liệu tra cứu đầu sách. Vui lòng thử lại.'))
        }
      } finally {
        running = false
        if (active && !background) setLoading(false)
      }
    }

    void loadBooks()
    const refresh = () => void loadBooks(true)
    const channel = 'BroadcastChannel' in window
      ? new BroadcastChannel('catalog-availability')
      : null
    if (channel) channel.onmessage = refresh
    const timer = window.setInterval(refresh, 10000)
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

  const loadMore = async () => {
    if (!result || result.last || loadingMore || loading) return
    const nextPage = result.page + 1
    setLoadingMore(true)
    setError(''); setDismissedError('')
    try {
      const data = await catalogService.searchPublicBooks({ ...submittedQuery, page: nextPage })
      setBooks((current) => {
        const unique = new Map(current.map((book) => [book.id, book]))
        data.content.forEach((book) => unique.set(book.id, book))
        return Array.from(unique.values())
      })
      loadedPageRef.current = data.page
      setResult(data)
    } catch (error) {
      setError(apiErrorMessage(error, 'Không thể hiển thị thêm đầu sách. Vui lòng thử lại.'))
    } finally {
      setLoadingMore(false)
    }
  }

  const handleSearch = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (loading) return
    setSubmittedQuery({ ...filters, keyword: keyword.trim(), page: 0, sort: submittedQuery.sort })
  }

  const removeFilter = (key: keyof PublicCatalogFilters) => {
    if (loading) return
    const next = { ...submittedQuery, page: 0 }
    delete next[key]
    setFilters({ categoryId: next.categoryId, publicationYear: next.publicationYear, availableOnly: next.availableOnly })
    setSubmittedQuery(next)
  }

  const clearFilters = () => {
    if (loading) return
    setFilters({})
    setSubmittedQuery({ keyword: submittedQuery.keyword, page: 0, sort: submittedQuery.sort })
  }

  const editKeyword = () => {
    keywordInputRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' })
    keywordInputRef.current?.focus({ preventScroll: true })
  }

  const hasFilters = Boolean(submittedQuery.categoryId || submittedQuery.publicationYear || submittedQuery.availableOnly)
  const selectedCategory = options.categories.find((category) => category.id === submittedQuery.categoryId)
  const submittedKeyword = submittedQuery.keyword
  const hasNoResults = !error && !loading && result?.totalElements === 0

  // Dùng các bộ lọc đã áp dụng, không dùng lựa chọn chưa bấm Tra cứu trong form.
  const filterActions = (
    <div className="flex flex-wrap items-center justify-center gap-2 text-sm" aria-label="Bộ lọc đang áp dụng">
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
          aria-label="Xóa bộ lọc còn bản sẵn sàng"
          className="inline-flex items-center gap-2 rounded-full bg-blue-50 px-3 py-1.5 text-blue-700 disabled:opacity-50">
          Còn bản sẵn sàng <X size={14} />
        </button>
      )}
      <button type="button" disabled={loading} onClick={clearFilters}
        className="px-2 py-1.5 font-medium text-slate-600 underline hover:text-blue-600 disabled:opacity-50">
        Xóa toàn bộ bộ lọc
      </button>
    </div>
  )

  return (
    <div className="public-page min-h-screen bg-slate-50">
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
                  ref={keywordInputRef}
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
                Chỉ hiện sách còn bản sẵn sàng
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
                  : `${result?.totalElements ?? 0} đầu sách phù hợp${submittedKeyword ? ` với “${submittedKeyword}”` : ''}`}
              </p>
            </div>

            <div className="flex flex-wrap items-center gap-3">
              <label htmlFor="catalog-sort" className="text-sm font-medium text-slate-700">Sắp xếp theo</label>
              <select
                id="catalog-sort"
                value={submittedQuery.sort}
                disabled={loading}
                onChange={(event) => setSubmittedQuery({
                  ...filters, keyword: keyword.trim(), page: 0, sort: event.target.value as PublicCatalogSort,
                })}
                className="h-10 rounded-lg border border-slate-300 bg-white px-3 text-sm text-slate-800 focus:ring-4 focus:ring-blue-500/20 disabled:opacity-50"
              >
                <option value="relevance">Mức phù hợp</option>
                <option value="publicationYear">Năm xuất bản (mới nhất trước)</option>
              </select>
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
          </div>

          {hasFilters && !hasNoResults && (
            <div className="mb-4">{filterActions}</div>
          )}

          {error && error !== dismissedError && (
            <FeedbackAlert message={error} tone="error" onDismiss={() => setDismissedError(error)} />
          )}
          {error && error === dismissedError && (
            <p role="status" className="text-sm text-slate-600">Chưa tải được kết quả mới. Bấm Làm mới để thử lại.</p>
          )}

          {hasNoResults && (
            <div className="rounded-2xl border border-dashed border-slate-300 bg-white px-6 py-10 text-center sm:px-8">
              <div role="status" aria-live="polite" aria-atomic="true">
                <BookOpen size={38} className="mx-auto text-slate-300" aria-hidden="true" />
                <h3 className="mt-3 text-lg font-semibold text-slate-800">Không tìm thấy đầu sách phù hợp.</h3>
                <p className="mt-2 text-sm leading-6 text-slate-600">
                  {submittedKeyword
                    ? `Chưa có đầu sách khớp với từ khóa “${submittedKeyword}”${hasFilters ? ' và các bộ lọc đang áp dụng' : ''}.`
                    : 'Chưa có đầu sách phù hợp với điều kiện tra cứu hiện tại.'}
                </p>
              </div>

              {hasFilters && (
                <div className="mx-auto mt-6 max-w-2xl rounded-xl bg-slate-50 p-4">
                  <p className="mb-3 text-sm leading-6 text-slate-600">
                    Hãy bỏ bớt bộ lọc để mở rộng kết quả. Bấm vào từng bộ lọc bên dưới để xóa,
                    hoặc xóa toàn bộ bộ lọc và tìm lại với cùng từ khóa.
                  </p>
                  {filterActions}
                </div>
              )}

              <p className="mx-auto mt-5 max-w-2xl text-sm leading-6 text-slate-600">
                {submittedKeyword
                  ? 'Thử từ khóa ngắn hơn, chỉ giữ một vài từ trong nhan đề hoặc tên tác giả. Bạn cũng có thể kiểm tra lại cách viết hoặc ISBN.'
                  : 'Bạn có thể tìm bằng một vài từ trong nhan đề, tên tác giả hoặc ISBN. Nếu đang dùng từ khóa dài, hãy thử từ khóa ngắn hơn.'}
              </p>
              <button
                type="button"
                onClick={editKeyword}
                className="mt-4 inline-flex items-center justify-center gap-2 rounded-xl bg-blue-600 px-4 py-2.5 text-sm font-semibold text-white transition hover:bg-blue-700 focus:ring-4 focus:ring-blue-500/20"
              >
                <Search size={16} aria-hidden="true" />
                Chỉnh sửa từ khóa
              </button>
              <p className="mt-3 text-xs leading-5 text-slate-500">
                Từ khóa trong ô tìm kiếm được giữ nguyên. Sửa từ khóa rồi bấm Tra cứu để tìm lại.
              </p>
            </div>
          )}

          {!error && !loading && books.length > 0 && (
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5">
              {books.map((book) => (
                <article
                  key={book.id}
                  className="flex min-w-0 flex-col rounded-xl border border-slate-200 bg-white p-3 shadow-sm transition hover:-translate-y-0.5 hover:shadow-md"
                >
                  <Link to={`/catalog/books/${book.id}`} aria-label={`Xem chi tiết ${book.title}`}>
                    <PublicBookCover bookId={book.id} url={book.coverImageUrl} title={book.title} thumbnail />
                  </Link>

                  <div className="flex flex-1 flex-col">
                    <Link to={`/catalog/books/${book.id}`} className="group">
                      <h3 className="line-clamp-2 min-h-10 text-sm font-bold leading-5 text-slate-900 group-hover:text-blue-700">
                        {book.title}
                      </h3>
                    </Link>
                    <p className="mt-1 line-clamp-1 text-xs text-slate-500" title={authorNames(book)}>
                      {authorNames(book)}
                    </p>

                    <div className="mt-3 flex items-center justify-between gap-2">
                      <span className="min-w-0 truncate rounded-md bg-slate-100 px-2 py-1 text-[11px] font-medium text-slate-600" title={book.categoryName}>
                        {book.categoryName}
                      </span>
                      <span className={`shrink-0 rounded-full px-2 py-1 text-[11px] font-semibold ${
                        (book.availableCount ?? 0) > 0
                          ? 'bg-emerald-50 text-emerald-700'
                          : 'bg-slate-100 text-slate-500'
                      }`}>
                        {book.availableCount ?? 0} sẵn sàng
                      </span>
                    </div>

                    <div className="mt-3 flex items-center justify-between border-t border-slate-100 pt-3">
                      <span className="text-[11px] text-slate-500">{book.publicationYear ?? 'Chưa rõ năm'}</span>
                      <Link
                        to={`/catalog/books/${book.id}`}
                        className="text-xs font-semibold text-blue-600 transition hover:text-blue-700"
                      >
                        Xem chi tiết
                      </Link>
                    </div>
                  </div>
                </article>
              ))}
            </div>
          )}

          {!error && !loading && result && result.totalElements > 0 && (
            <div className="mt-6 flex flex-col items-center gap-3 rounded-xl border border-slate-200 bg-white p-4 text-center">
              <p className="text-sm text-slate-600" aria-live="polite">
                Đang hiển thị <strong>{books.length}</strong> / {result.totalElements} đầu sách phù hợp.
              </p>
              {!result.last && (
                <button
                  type="button"
                  disabled={loadingMore}
                  onClick={() => void loadMore()}
                  className="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-5 py-2.5 text-sm font-semibold text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-60"
                >
                  <ChevronDown size={17} className={loadingMore ? 'animate-bounce' : ''} />
                  {loadingMore ? 'Đang tải thêm…' : 'Hiển thị thêm'}
                </button>
              )}
              {result.last && books.length > 0 && (
                <p className="text-xs text-slate-500">Bạn đã xem toàn bộ kết quả tra cứu.</p>
              )}
            </div>
          )}
        </section>
      </main>
      <PublicSiteFooter />
    </div>
  )
}
