import type { CSSProperties } from 'react'
import { ArrowUpRight } from 'lucide-react'
import { Link } from 'react-router-dom'
import type { Book } from '../../s1-08-catalog/catalogService'
import PublicBookCover from '../../s2-10-book-cover/PublicBookCover'
import usePointerTilt from '../hooks/usePointerTilt'

export default function FloatingBook3D({ book, index, active }: { book: Book; index: number; active: boolean }) {
  const ref = usePointerTilt(active)
  const authors = book.authors?.length ? book.authors.map(author => author.name).join(', ') : book.authorName || 'Chưa có thông tin tác giả'
  return <li className={`floating-slot floating-slot-${index}`} style={{ '--book-delay': `${-index * 1.7}s`, '--book-duration': `${6.8 + index * 0.65}s` } as CSSProperties}>
    <Link ref={ref} className="floating-book-link" to={`/catalog/books/${book.id}`} aria-label={`Xem chi tiết ${book.title} — ${authors}`}>
      <span className="book-levitation">
        <span className="book-lift">
          <span className="book-volume">
            <span className="volume-back" aria-hidden="true" />
            <span className="volume-pages" aria-hidden="true" />
            <span className="volume-top" aria-hidden="true" />
            <span className="volume-bottom" aria-hidden="true" />
            <span className="volume-spine" aria-hidden="true"><span>LIBRA</span><span>{book.title}</span></span>
            <span className="volume-front"><PublicBookCover bookId={book.id} url={book.coverImageUrl} title={book.title} thumbnail /></span>
          </span>
        </span>
      </span>
      <span className="floating-book-info">
        {index === 0 && <span className="newest-label">MỚI NHẤT</span>}
        <strong title={book.title}>{book.title}</strong>
        <span className="floating-author" title={authors}>{authors}</span>
        <span className="floating-detail">Xem chi tiết <ArrowUpRight size={13} aria-hidden="true" /></span>
      </span>
    </Link>
  </li>
}
