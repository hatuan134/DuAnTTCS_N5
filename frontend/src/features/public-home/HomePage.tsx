import { useEffect, useState, type FormEvent } from 'react'
import { ArrowRight, BookOpen, Bookmark, Search, Layers3 } from 'lucide-react'
import { Link, useNavigate } from 'react-router-dom'
import { catalogService, type Book } from '../s1-08-catalog/catalogService'
import BookCard from '../../components/public/BookCard'
import EmptyState from '../../components/ui/EmptyState'

export default function HomePage() {
  const [keyword, setKeyword] = useState('')
  const [books, setBooks] = useState<Book[]>([])
  const [status, setStatus] = useState<'loading' | 'success' | 'error'>('loading')
  const [retry, setRetry] = useState(0)
  const navigate = useNavigate()
  useEffect(() => {
    const controller = new AbortController()
    catalogService.searchPublicBooks({ keyword: '', page: 0, sort: 'publicationYear' }, controller.signal)
      .then(data => { if (!controller.signal.aborted) { setBooks(data.content.slice(0, 5)); setStatus('success') } })
      .catch(() => { if (!controller.signal.aborted) setStatus('error') })
    return () => controller.abort()
  }, [retry])
  const search = (event: FormEvent) => {
    event.preventDefault()
    navigate(keyword.trim() ? `/catalog?keyword=${encodeURIComponent(keyword.trim())}` : '/catalog')
  }
  return <>
    <section className="home-hero">
      <div className="public-container hero-grid">
        <div className="hero-copy">
          <p className="eyebrow"><span /> THƯ VIỆN TRONG TẦM TAY</p>
          <h1><span className="hero-brand">LIBRA – </span>Khám phá tri thức,<br /><em>kết nối tương lai.</em></h1>
          <p className="hero-description">Một cuốn sách, một góc nhìn mới. Tìm cuốn sách bạn cần, xem tình trạng sẵn sàng và bắt đầu hành trình đọc của riêng mình.</p>
          <form className="hero-search" onSubmit={search} role="search">
            <Search size={21} aria-hidden="true" /><label htmlFor="home-keyword" className="sr-only">Tên sách, tác giả hoặc ISBN</label>
            <input id="home-keyword" type="search" value={keyword} onChange={event => setKeyword(event.target.value)} placeholder="Tên sách, tác giả hoặc ISBN…" />
            <button type="submit" className="primary-button">Khám phá sách <ArrowRight size={17} /></button>
          </form>
          <div className="hero-footnote"><BookOpen size={16} /><span>Tra cứu tự do. Đặt giữ bằng tài khoản bạn đọc.</span></div>
        </div>
        <div className="hero-art"><img src={`${import.meta.env.BASE_URL}images/library-illustration.svg`} width="580" height="510" alt="Minh họa những cuốn sách và không gian đọc LIBRA" loading="eager" /></div>
      </div>
    </section>

    <div className="public-container home-benefits">
      {[{ Icon: Search, title: 'Tìm sách dễ dàng', text: 'Tra cứu nhan đề, tác giả và ISBN' }, { Icon: Bookmark, title: 'Chủ động đặt giữ', text: 'Theo dõi đơn và thời hạn đến nhận' }, { Icon: BookOpen, title: 'Đọc sách an tâm', text: 'Xem sách đang mượn và hạn trả' }].map(({ Icon, title, text }) => <div key={title}><span><Icon size={23} /></span><div><h2>{title}</h2><p>{text}</p></div></div>)}
    </div>

    <section className="public-container home-discover" aria-labelledby="discover-title">
      <div className="section-heading"><div><p className="eyebrow">MỞ THÊM MỘT TRANG MỚI</p><h2 id="discover-title">Khám phá tủ sách</h2><p>Các đầu sách theo năm xuất bản mới nhất trong danh mục.</p></div><Link className="text-link" to="/catalog">Xem tất cả sách <ArrowRight size={17} /></Link></div>
      {status === 'loading' && <div className="book-grid" role="status" aria-label="Đang tải đầu sách"><span className="sr-only">Đang tải đầu sách…</span>{Array.from({ length: 5 }, (_, i) => <div className="book-skeleton" key={i}><div /><span /><span /></div>)}</div>}
      {status === 'error' && <div className="catalog-unavailable" role="status"><BookOpen size={30} /><h3>Chưa tải được tủ sách</h3><p>Vui lòng thử lại để xem dữ liệu hiện tại của thư viện.</p><button className="secondary-button" onClick={() => { setStatus('loading'); setRetry(value => value + 1) }}>Thử lại</button></div>}
      {status === 'success' && (books.length ? <div className="book-grid">{books.map(book => <BookCard key={book.id} book={book} />)}</div> : <EmptyState title="Tủ sách đang được cập nhật" description="Các đầu sách sẽ xuất hiện tại đây khi thư viện bổ sung vào danh mục." />)}
    </section>

    <section id="gioi-thieu" className="home-about" aria-labelledby="about-title">
      <div className="public-container about-grid">
        <div className="about-mark" aria-hidden="true"><BookOpen size={72} strokeWidth={1} /><span>LIBRA</span><p>Mỗi trang sách<br />mở một chân trời.</p></div>
        <div><p className="eyebrow">VỀ LIBRA</p><h2 id="about-title">Kết nối bạn đọc<br />với không gian tri thức.</h2><p>LIBRA giúp bạn tiếp cận tủ sách của thư viện từ bất cứ đâu. Tra cứu trước khi đến, đặt giữ sách phù hợp và theo dõi hành trình mượn trả ngay trong tài khoản.</p>
          <ul><li><Search size={18} /> Tra cứu công khai, lọc theo thể loại và năm xuất bản.</li><li><Layers3 size={18} /> Xem số bản sẵn sàng và thông tin đầu sách.</li><li><Bookmark size={18} /> Quản lý đơn đặt giữ, sách đang mượn và hồ sơ cá nhân.</li></ul>
          <Link to="/catalog" className="primary-button">Bắt đầu khám phá <ArrowRight size={17} /></Link>
        </div>
      </div>
    </section>
  </>
}
