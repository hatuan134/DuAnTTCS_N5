import type { CSSProperties } from 'react'
import { ArrowUpRight, BookOpen } from 'lucide-react'
import { Link } from 'react-router-dom'
import type { Book, FeaturedPublicBook } from '../../s1-08-catalog/catalogService'
import { publicBookImageUrl } from '../../s2-10-book-cover/bookCoverService'

function Cover({ book }: { book: Book }) {
  const source = book.coverImageUrl?.trim()
  return <div className="libra-feature-book">
    <div className="libra-feature-pages" aria-hidden="true" />
    {source ? <img src={publicBookImageUrl(book.id, source, false)} loading="lazy" decoding="async" alt={`Bìa sách ${book.title}`} />
      : <div className="libra-feature-no-cover"><BookOpen size={42} aria-hidden="true" /><span className="sr-only">Ảnh bìa đang cập nhật</span></div>}
  </div>
}

export default function FeaturedBooks({ books, loading, error }: { books: FeaturedPublicBook[]; loading: boolean; error: boolean }) {
  return <section className="libra-featured" id="sach-noi-bat" aria-labelledby="libra-featured-heading" data-libra-reveal>
    <div className="public-container">
      <div className="section-heading"><div><p className="eyebrow">ĐƯỢC BẠN ĐỌC YÊU THÍCH</p>
        <h2 id="libra-featured-heading">Sách nổi bật dành cho bạn</h2>
        <p>3 đầu sách được mượn nhiều nhất trong lịch sử LIBRA và hiện còn bản sẵn sàng.</p></div>
        <Link className="text-link" to="/catalog">Khám phá bộ sưu tập <ArrowUpRight size={17} /></Link>
      </div>
      <div className="libra-featured-grid">
        {loading && Array.from({ length: 3 }, (_, i) => <div className="libra-feature-skeleton" key={i} aria-hidden="true" />)}
        {!loading && error && <p role="status" className="libra-featured-empty">Chưa tải được sách nổi bật. Vui lòng thử tải lại trang.</p>}
        {!loading && !error && books.length === 0 && <p className="libra-featured-empty">Chưa có đầu sách nào vừa có lượt mượn thực tế vừa còn bản sẵn sàng.</p>}
        {books.map(({ book, borrowCount }, index) => <Link key={book.id} to={`/catalog/books/${book.id}`} className="libra-feature-card" style={{ '--card-index': index } as CSSProperties}>
          <div className="libra-feature-stage"><span className="libra-feature-halo" aria-hidden="true" /><Cover book={book} /></div>
          <div className="libra-feature-meta"><span>{book.categoryName}</span>
            <h3>{book.title}</h3><p>{book.authors?.map(a => a.name).join(', ') || book.authorName}</p>
            <p className="libra-feature-borrows">Đã được mượn {borrowCount.toLocaleString('vi-VN')} lượt</p>
            <div className="libra-feature-footer"><span>{book.availableCount > 0 ? `${book.availableCount} bản sẵn sàng` : 'Xem tình trạng sách'}</span><ArrowUpRight size={18} aria-hidden="true" /></div>
          </div>
        </Link>)}
      </div>
    </div>
  </section>
}
