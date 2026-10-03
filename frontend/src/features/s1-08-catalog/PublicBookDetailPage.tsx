import { publicCoverUrl } from '../s2-10-book-cover/bookCoverService'
import { useEffect, useState } from 'react'
import {
  AlertCircle,
  ArrowLeft,
  BookOpen,
  Bookmark,
  Calendar,
  CheckCircle2,
  Clock,
  FileText,
  ImageOff,
  Info,
  Layers,
  Library,
  LogIn,
  MapPin,
  Tag,
  User,
  Users,
  Warehouse,
} from 'lucide-react'
import { Link, useParams } from 'react-router-dom'

import { catalogService } from './catalogService'
import type { Book } from './catalogService'

function authorNames(book: Book) {
  if (book.authors?.length) {
    return book.authors.map((author) => author.name).join(', ')
  }
  return book.authorName || 'Không rõ'
}

function formatDate(dateString?: string | null) {
  if (!dateString) return ''
  try {
    const parts = dateString.split('-')
    if (parts.length === 3) {
      return `${parts[2]}/${parts[1]}/${parts[0]}`
    }
    const d = new Date(dateString)
    if (isNaN(d.getTime())) return dateString
    const day = String(d.getDate()).padStart(2, '0')
    const month = String(d.getMonth() + 1).padStart(2, '0')
    const year = d.getFullYear()
    return `${day}/${month}/${year}`
  } catch {
    return dateString
  }
}

export default function PublicBookDetailPage() {
  const { bookId } = useParams<{ bookId: string }>()
  const [book, setBook] = useState<Book | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [imageError, setImageError] = useState(false)

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
      setImageError(false)
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
    <div className="min-h-screen bg-slate-50 text-slate-800">
      {/* Header */}
      <header className="border-b border-slate-200 bg-white sticky top-0 z-10">
        <div className="mx-auto flex max-w-5xl flex-wrap items-center justify-between gap-4 px-4 py-3.5 sm:px-6 lg:px-8">
          <Link to="/catalog" className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-blue-600 text-white shadow-sm shadow-blue-200">
              <BookOpen size={22} />
            </div>
            <div>
              <p className="font-bold tracking-tight text-slate-900">LIBRA</p>
              <p className="text-xs text-slate-500">Chi tiết đầu sách công khai</p>
            </div>
          </Link>

          <Link
            to="/login"
            className="inline-flex items-center gap-2 rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-semibold text-slate-700 shadow-sm transition hover:bg-slate-50 hover:border-slate-400"
          >
            <LogIn size={17} />
            Đăng nhập
          </Link>
        </div>
      </header>

      {/* Main Container */}
      <main className="mx-auto max-w-5xl px-4 py-8 sm:px-6 lg:px-8">
        <Link
          to="/catalog"
          className="inline-flex items-center gap-2 text-sm font-semibold text-blue-600 transition hover:text-blue-700"
        >
          <ArrowLeft size={17} />
          Quay lại tra cứu
        </Link>

        {loading && (
          <div className="mt-6 rounded-2xl border border-slate-200 bg-white p-12 text-center text-sm text-slate-500 shadow-sm">
            <div className="mx-auto mb-3 h-8 w-8 animate-spin rounded-full border-2 border-blue-600 border-t-transparent" />
            Đang tải thông tin đầu sách…
          </div>
        )}

        {!loading && error && (
          <div
            role="alert"
            className="mt-6 flex items-start gap-3 rounded-2xl border border-red-200 bg-red-50 p-6 text-sm text-red-700 shadow-sm"
          >
            <AlertCircle size={20} className="shrink-0 text-red-500 mt-0.5" />
            <div>
              <p className="font-semibold text-red-800">Thông báo</p>
              <p className="mt-1">{error}</p>
            </div>
          </div>
        )}

        {!loading && !error && book && (
          <article className="mt-6 overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
            {/* Top Overview: Cover Image + Key Metadata */}
            <div className="p-6 sm:p-8 border-b border-slate-100">
              <div className="flex flex-col md:flex-row gap-8 items-start">
                {/* Book Cover Image / Fallback Placeholder */}
                <div className="w-full md:w-56 shrink-0 flex flex-col items-center">
                  {book.coverImageUrl && !imageError ? (
                    <div className="relative aspect-[3/4] w-48 sm:w-56 overflow-hidden rounded-xl border border-slate-200 bg-slate-100 shadow-md">
                      <img
                        src={publicCoverUrl(book.coverImageUrl)}
                        alt={`Bìa sách ${book.title}`}
                        onError={() => setImageError(true)}
                        className="h-full w-full object-cover"
                      />
                    </div>
                  ) : (
                    /* Fallback when book has no cover image or image failed to load */
                    <div
                      aria-label="Hình thay thế bìa sách"
                      className="flex aspect-[3/4] w-48 sm:w-56 flex-col items-center justify-center rounded-xl border-2 border-dashed border-slate-300 bg-gradient-to-b from-slate-100 to-slate-200/70 p-4 text-center text-slate-400 shadow-inner"
                    >
                      <div className="flex h-14 w-14 items-center justify-center rounded-full bg-slate-200 text-slate-500 mb-3 shadow-sm">
                        <ImageOff size={28} />
                      </div>
                      <p className="text-xs font-semibold uppercase tracking-wider text-slate-500">
                        Chưa có ảnh bìa
                      </p>
                      <p className="mt-1.5 line-clamp-2 text-xs text-slate-400 italic">
                        {book.title}
                      </p>
                    </div>
                  )}
                </div>

                {/* Main Information */}
                <div className="flex-1 w-full">
                  {/* Category Chip */}
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="inline-flex items-center gap-1.5 rounded-md bg-blue-50 px-2.5 py-1 text-xs font-semibold text-blue-700 border border-blue-200">
                      <Tag size={12} />
                      {book.categoryName}
                    </span>

                    {/* Availability Status Badge */}
                    {book.availableCount > 0 ? (
                      <span className="inline-flex items-center gap-1.5 rounded-md bg-emerald-50 px-2.5 py-1 text-xs font-semibold text-emerald-700 border border-emerald-200">
                        <CheckCircle2 size={13} className="text-emerald-600" />
                        Sẵn sàng cho mượn
                      </span>
                    ) : book.copyCount > 0 ? (
                      <span className="inline-flex items-center gap-1.5 rounded-md bg-amber-50 px-2.5 py-1 text-xs font-semibold text-amber-700 border border-amber-200">
                        <AlertCircle size={13} className="text-amber-600" />
                        Tạm hết bản sẵn sàng
                      </span>
                    ) : (
                      <span className="inline-flex items-center gap-1.5 rounded-md bg-slate-100 px-2.5 py-1 text-xs font-semibold text-slate-600 border border-slate-200">
                        <AlertCircle size={13} className="text-slate-500" />
                        Chưa có bản sao
                      </span>
                    )}
                  </div>

                  {/* Title and Subtitle */}
                  <h1 className="mt-3 text-2xl sm:text-3xl font-bold tracking-tight text-slate-900 leading-snug">
                    {book.title}
                  </h1>
                  {book.subtitle && (
                    <p className="mt-1.5 text-base sm:text-lg text-slate-600 font-medium">
                      {book.subtitle}
                    </p>
                  )}

                  {/* Inventory Availability Summary Cards */}
                  <div className="mt-6 grid grid-cols-1 sm:grid-cols-2 gap-4">
                    <div className="rounded-xl border border-emerald-200 bg-emerald-50/70 p-4 transition">
                      <p className="text-xs font-semibold uppercase tracking-wider text-emerald-700">
                        Số bản Sẵn sàng
                      </p>
                      <div className="mt-2 flex items-baseline gap-2">
                        <span className="text-3xl font-extrabold text-emerald-800">
                          {book.availableCount ?? 0}
                        </span>
                        <span className="text-xs text-emerald-600">bản khả dụng</span>
                      </div>
                      <p className="mt-1 text-xs text-emerald-600/80">
                        {book.availableCount > 0
                          ? 'Có thể làm thủ tục mượn ngay tại thư viện'
                          : 'Hiện không có bản sao nào sẵn sàng'}
                      </p>
                    </div>

                    <div className="rounded-xl border border-slate-200 bg-slate-50 p-4 transition">
                      <p className="text-xs font-semibold uppercase tracking-wider text-slate-500">
                        Tổng số bản
                      </p>
                      <div className="mt-2 flex items-baseline gap-2">
                        <span className="text-3xl font-extrabold text-slate-800">
                          {book.copyCount}
                        </span>
                        <span className="text-xs text-slate-500">bản sao</span>
                      </div>
                      <p className="mt-1 text-xs text-slate-500">
                        Tổng số bản sao đã ghi nhận thuộc đầu sách
                      </p>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            {/* Bibliographic Details Table / Grid */}
            <div className="p-6 sm:p-8 bg-slate-50/50">
              <h2 className="text-base font-bold text-slate-900 mb-4 flex items-center gap-2">
                <FileText size={18} className="text-slate-500" />
                Thông tin thư mục
              </h2>

              <dl className="grid grid-cols-1 gap-x-6 gap-y-4 sm:grid-cols-2 lg:grid-cols-3">
                <InfoItem
                  icon={<User size={16} className="text-slate-400" />}
                  label="Tác giả"
                  value={authorNames(book)}
                />
                <InfoItem
                  icon={<Bookmark size={16} className="text-slate-400" />}
                  label="Mã ISBN"
                  value={book.isbn || '—'}
                />
                <InfoItem
                  icon={<Tag size={16} className="text-slate-400" />}
                  label="Thể loại"
                  value={book.categoryName}
                />
                <InfoItem
                  icon={<Library size={16} className="text-slate-400" />}
                  label="Nhà xuất bản"
                  value={book.publisher || '—'}
                />
                <InfoItem
                  icon={<Calendar size={16} className="text-slate-400" />}
                  label="Năm xuất bản"
                  value={book.publicationYear?.toString() ?? '—'}
                />
                <InfoItem
                  icon={<BookOpen size={16} className="text-slate-400" />}
                  label="Số trang"
                  value={book.pageCount ? `${book.pageCount} trang` : '—'}
                />
              </dl>
            </div>

            {/* Vị trí các bản Sẵn sàng cho mượn (S2-06.2) */}
            {book.availableCount > 0 && book.availableCopies && book.availableCopies.length > 0 && (
              <div className="border-t border-slate-100 p-6 sm:p-8 bg-emerald-50/20">
                <div className="flex flex-wrap items-center justify-between gap-2 mb-3">
                  <div className="flex items-center gap-2">
                    <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-emerald-100 text-emerald-700">
                      <MapPin size={18} />
                    </div>
                    <div>
                      <h2 className="text-base font-bold text-slate-900">
                        Vị trí các bản đang Sẵn sàng
                      </h2>
                      <p className="text-xs text-slate-500">
                        Bạn đọc có thể đến trực tiếp các kho và kệ sau để lấy sách làm thủ tục mượn:
                      </p>
                    </div>
                  </div>
                  <span className="inline-flex items-center rounded-full bg-emerald-100 px-3 py-1 text-xs font-bold text-emerald-800 border border-emerald-200">
                    {book.availableCopies.length} vị trí khả dụng
                  </span>
                </div>

                <div className="mt-4 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
                  {book.availableCopies.map((copy, index) => (
                    <div
                      key={copy.copyId || index}
                      className="rounded-xl border border-emerald-200 bg-white p-4 shadow-xs hover:border-emerald-300 transition"
                    >
                      <div className="flex items-center justify-between gap-2 border-b border-slate-100 pb-2 mb-2.5">
                        <span className="text-xs font-bold uppercase tracking-wider text-emerald-700 bg-emerald-50 px-2 py-0.5 rounded border border-emerald-100">
                          Bản #{index + 1}
                        </span>
                        {copy.barcode && (
                          <span className="text-xs font-mono text-slate-500 bg-slate-50 px-1.5 py-0.5 rounded border border-slate-200">
                            {copy.barcode}
                          </span>
                        )}
                      </div>

                      <div className="space-y-2 text-sm">
                        <div className="flex items-start gap-2">
                          <Warehouse size={16} className="text-emerald-600 mt-0.5 shrink-0" />
                          <div>
                            <span className="text-xs text-slate-500 block">Kho:</span>
                            <span className="font-semibold text-slate-900">
                              {copy.warehouseName}
                              {copy.warehouseCode && (
                                <span className="text-xs text-slate-500 font-normal ml-1">
                                  ({copy.warehouseCode})
                                </span>
                              )}
                            </span>
                          </div>
                        </div>

                        <div className="flex items-start gap-2">
                          <Layers size={16} className="text-emerald-600 mt-0.5 shrink-0" />
                          <div>
                            <span className="text-xs text-slate-500 block">Kệ:</span>
                            <span className="font-semibold text-slate-900">
                              {copy.shelfName ? `${copy.shelfName} (${copy.shelfCode})` : `Kệ ${copy.shelfCode}`}
                            </span>
                          </div>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* S2-06.3: Queue and Expected Return Date Section - ONLY visible when availableCount === 0 */}
            {book.availableCount === 0 && (
              <div className="border-t border-amber-200/70 bg-gradient-to-br from-amber-50/60 via-white to-amber-50/30 p-6 sm:p-8">
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-4 border-b border-amber-200/50">
                  <div className="flex items-center gap-3">
                    <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-amber-100 text-amber-800 shadow-xs border border-amber-200">
                      <Clock size={20} />
                    </div>
                    <div>
                      <h2 className="text-base font-bold text-slate-900">
                        Hàng đợi đặt giữ & Dự kiến có sách
                      </h2>
                      <p className="text-xs text-slate-500">
                        Đầu sách hiện không còn bản Sẵn sàng. Bạn đọc có thể theo dõi hàng đợi chờ có bản trả về:
                      </p>
                    </div>
                  </div>
                  <span className="inline-flex items-center gap-1.5 rounded-full bg-amber-100 px-3 py-1 text-xs font-bold text-amber-900 border border-amber-300 w-fit">
                    <Users size={13} />
                    {book.queueCount && book.queueCount > 0
                      ? `${book.queueCount} người đang xếp hàng`
                      : 'Chưa có ai xếp hàng'}
                  </span>
                </div>

                <div className="mt-5 grid grid-cols-1 md:grid-cols-2 gap-4">
                  {/* Card 1: Số người đang xếp hàng */}
                  <div className="rounded-xl border border-amber-200 bg-white p-5 shadow-xs">
                    <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wider text-amber-800 mb-2">
                      <Users size={15} className="text-amber-600" />
                      Số người đang xếp hàng đặt giữ
                    </div>
                    <div className="flex items-baseline gap-2">
                      <span className="text-3xl font-extrabold text-amber-900">
                        {book.queueCount ?? 0}
                      </span>
                      <span className="text-xs font-medium text-amber-700">bạn đọc đang chờ</span>
                    </div>
                    <p className="mt-2 text-xs text-slate-600 leading-relaxed">
                      {book.queueCount && book.queueCount > 0
                        ? `Đầu sách hiện có ${book.queueCount} yêu cầu đặt giữ đang trong hàng đợi chờ có bản sao trả về.`
                        : 'Hiện chưa có ai xếp hàng đặt giữ đầu sách này. Bạn có thể là người đầu tiên nhận sách khi có bản trả về.'}
                    </p>
                  </div>

                  {/* Card 2: Ngày dự kiến có bản trả về sớm nhất */}
                  <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-xs">
                    <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wider text-slate-600 mb-2">
                      <Calendar size={15} className="text-blue-600" />
                      Ngày dự kiến có bản trả về sớm nhất
                    </div>

                    {book.earliestExpectedReturnDate ? (
                      <div>
                        <div className="flex items-baseline gap-2">
                          <span className="text-2xl sm:text-3xl font-extrabold text-blue-900">
                            {formatDate(book.earliestExpectedReturnDate)}
                          </span>
                          <span className="text-xs font-medium text-blue-600">dự kiến sớm nhất</span>
                        </div>
                        {book.expectedReturnNotice && (
                          <p className="mt-2 text-xs text-slate-600 leading-relaxed">
                            {book.expectedReturnNotice}
                          </p>
                        )}
                      </div>
                    ) : (
                      <div>
                        <div className="flex items-baseline gap-2">
                          <span className="text-base font-bold text-slate-700">
                            Chưa xác định được ngày
                          </span>
                        </div>
                        <p className="mt-2 text-xs text-slate-500 leading-relaxed">
                          {book.expectedReturnNotice ||
                            'Chưa có thông tin ngày hẹn trả cụ thể cho các bản sao thuộc đầu sách này.'}
                        </p>
                      </div>
                    )}
                  </div>
                </div>

                <div className="mt-4 flex items-start gap-2 rounded-lg bg-amber-50/80 border border-amber-200/70 p-3 text-xs text-amber-900">
                  <Info size={15} className="text-amber-700 shrink-0 mt-0.5" />
                  <span>
                    Lưu ý: Thời điểm có sách thực tế phụ thuộc vào việc bạn đọc đang mượn hoàn trả sách đúng hạn hoặc gia hạn phiếu mượn.
                  </span>
                </div>
              </div>
            )}

            {/* Summary / Description */}
            <div className="border-t border-slate-100 p-6 sm:p-8">
              <h2 className="text-base font-bold text-slate-900">Tóm tắt nội dung</h2>
              {book.description ? (
                <p className="mt-3 whitespace-pre-line text-sm leading-relaxed text-slate-600">
                  {book.description}
                </p>
              ) : (
                <p className="mt-3 text-sm italic text-slate-400">
                  Chưa có thông tin tóm tắt nội dung cho đầu sách này.
                </p>
              )}
            </div>
          </article>
        )}
      </main>
    </div>
  )
}

function InfoItem({
  icon,
  label,
  value,
}: {
  icon?: React.ReactNode
  label: string
  value: string
}) {
  return (
    <div className="rounded-xl border border-slate-200/80 bg-white p-3.5 shadow-xs">
      <div className="flex items-center gap-1.5 text-xs font-medium text-slate-500">
        {icon}
        <span>{label}</span>
      </div>
      <p className="mt-1 font-semibold text-slate-900 break-words text-sm">{value}</p>
    </div>
  )
}
