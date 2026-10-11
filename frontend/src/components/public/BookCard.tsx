import { ArrowUpRight } from 'lucide-react'
import { Link } from 'react-router-dom'
import type { Book } from '../../features/s1-08-catalog/catalogService'
import PublicBookCover from '../../features/s2-10-book-cover/PublicBookCover'
import StatusBadge from '../ui/StatusBadge'

export default function BookCard({ book }: { book: Book }) {
  const authors = book.authors?.length ? book.authors.map(author => author.name).join(', ') : book.authorName || 'Chưa có thông tin tác giả'
  return <article className="book-card">
    <Link to={`/catalog/books/${book.id}`} className="book-card-cover" aria-label={`Xem chi tiết ${book.title}`}>
      <PublicBookCover bookId={book.id} url={book.coverImageUrl} title={book.title} thumbnail />
      <span className="book-card-arrow"><ArrowUpRight size={18} /></span>
    </Link>
    <div className="book-card-body">
      <p className="book-category" title={book.categoryName}>{book.categoryName || 'Sách thư viện'}</p>
      <h3><Link to={`/catalog/books/${book.id}`}>{book.title}</Link></h3>
      <p className="book-author" title={authors}>{authors}</p>
      <div className="book-card-meta"><StatusBadge status={book.availableCount > 0 ? 'AVAILABLE' : 'NO_COPY'} label={book.availableCount > 0 ? `${book.availableCount} bản sẵn sàng` : 'Chưa có bản sẵn sàng'} /><span>{book.publicationYear ?? '—'}</span></div>
      <Link className="book-detail-link" to={`/catalog/books/${book.id}`}>Xem chi tiết <ArrowUpRight size={15} /></Link>
    </div>
  </article>
}
