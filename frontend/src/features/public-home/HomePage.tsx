import { useEffect, useRef, useState, type FormEvent } from 'react'
import { ArrowRight, BookOpen, Bookmark, Search, Sparkles, MoveDownRight, CheckCircle2 } from 'lucide-react'
import { Link, useNavigate } from 'react-router-dom'
import { catalogService, type Book } from '../s1-08-catalog/catalogService'
import BookCard from '../../components/public/BookCard'
import EmptyState from '../../components/ui/EmptyState'
import FeedbackAlert from '../../components/ui/FeedbackAlert'
import Hero3DBookShowcase from './components/Hero3DBookShowcase'
import LiquidParticleBackground from './components/LiquidParticleBackground'
import { useReducedMotion, useSceneMotion } from './hooks/useSceneMotion'
import LibraryStatistics from './components/LibraryStatistics'
import FeaturedBooks from './components/FeaturedBooks'
import HowItWorks from './components/HowItWorks'
import { usePublicOverview } from './usePublicOverview'
import type { FeaturedPublicBook } from '../s1-08-catalog/catalogService'
import './immersive.css'

export default function HomePage() {
  const [keyword, setKeyword] = useState('')
  const [books, setBooks] = useState<Book[]>([])
  const [featured, setFeatured] = useState<FeaturedPublicBook[]>([])
  const [featuredLoading, setFeaturedLoading] = useState(true)
  const [featuredError, setFeaturedError] = useState(false)
  const { overview, error: overviewError } = usePublicOverview()
  const [status, setStatus] = useState<'loading' | 'success' | 'error'>('loading')
  const [retry, setRetry] = useState(0)
  const [retryNotice, setRetryNotice] = useState('')
  const navigate = useNavigate()
  const heroRef = useRef<HTMLElement>(null)
  const reducedMotion = useReducedMotion()
  const active = useSceneMotion(heroRef, !reducedMotion)
  const retryBooks = () => { setRetryNotice(''); setStatus('loading'); setRetry(value => value + 1) }
  useEffect(() => {
    const hero = heroRef.current
    if (!hero || reducedMotion) return
    let frame = 0
    let nextX = 64
    let nextY = 42
    const render = () => {
      hero.style.setProperty('--spot-x', `${nextX}%`)
      hero.style.setProperty('--spot-y', `${nextY}%`)
      hero.style.setProperty('--light-x', `${(nextX - 50) * .18}px`)
      hero.style.setProperty('--light-y', `${(nextY - 50) * .12}px`)
      frame = 0
    }
    const move = (event: PointerEvent) => {
      if (event.pointerType === 'touch') return
      const rect = hero.getBoundingClientRect()
      nextX = Math.max(0, Math.min(100, ((event.clientX - rect.left) / rect.width) * 100))
      nextY = Math.max(0, Math.min(100, ((event.clientY - rect.top) / rect.height) * 100))
      if (!frame) frame = requestAnimationFrame(render)
    }
    const reset = () => { nextX = 64; nextY = 42; if (!frame) frame = requestAnimationFrame(render) }
    hero.addEventListener('pointermove', move, { passive: true })
    hero.addEventListener('pointerleave', reset)
    return () => {
      hero.removeEventListener('pointermove', move)
      hero.removeEventListener('pointerleave', reset)
      cancelAnimationFrame(frame)
    }
  }, [reducedMotion])
  useEffect(() => {
    if (reducedMotion) return
    const targets = document.querySelectorAll<HTMLElement>('[data-libra-reveal]')
    if (!('IntersectionObserver' in window)) return
    const observer = new IntersectionObserver(entries => {
      entries.forEach(entry => {
        if (entry.isIntersecting) {
          (entry.target as HTMLElement).dataset.libraVisible = 'true'
          observer.unobserve(entry.target)
        }
      })
    }, { threshold: .08, rootMargin: '0px 0px 40px 0px' })
    targets.forEach(target => { target.dataset.libraAnimate = 'true'; observer.observe(target) })
    return () => { observer.disconnect(); targets.forEach(target => { delete target.dataset.libraAnimate }) }
  }, [reducedMotion])
  useEffect(() => {
    const controller = new AbortController()
    catalogService.searchPublicBooks({ keyword: '', page: 0, size: 18, sort: 'createdAt' }, controller.signal)
      .then(data => { if (!controller.signal.aborted) {
        setBooks(data.content.slice(0, 5))
        setStatus('success')
      } })
      .catch(() => { if (!controller.signal.aborted) {
        setStatus('error')
        if (retry > 0) setRetryNotice('Chưa tải được tủ sách. Vui lòng kiểm tra kết nối và thử lại.')
      } })
    return () => controller.abort()
  }, [retry])
  useEffect(() => {
    const controller = new AbortController()
    setFeaturedLoading(true)
    setFeaturedError(false)
    catalogService.getMostBorrowedAvailableBooks(controller.signal)
      .then(data => {
        if (!controller.signal.aborted) setFeatured(data.filter(item => item.book.availableCount > 0).slice(0, 3))
      })
      .catch(() => {
        if (!controller.signal.aborted) { setFeatured([]); setFeaturedError(true) }
      })
      .finally(() => { if (!controller.signal.aborted) setFeaturedLoading(false) })
    return () => controller.abort()
  }, [])
  const search = (event: FormEvent) => {
    event.preventDefault()
    navigate(keyword.trim() ? `/catalog?keyword=${encodeURIComponent(keyword.trim())}` : '/catalog')
  }
  return <>
    <section ref={heroRef} className="home-hero immersive-hero" data-motion={active ? 'active' : 'paused'} data-reduced-motion={reducedMotion}>
      <div className="hero-sky" aria-hidden="true"><span className="hero-sky-orb hero-sky-orb-a" /><span className="hero-sky-orb hero-sky-orb-b" /><span className="hero-sky-rays" /></div>
      {!reducedMotion && <LiquidParticleBackground hostRef={heroRef} active={active} />}
      <div className="public-container hero-grid">
        <div className="hero-copy">
          <p className="eyebrow"><span /> THƯ VIỆN SỐ · KHÔNG GIỚI HẠN KHÁM PHÁ</p>
          <span className="hero-edition"><Sparkles size={15} aria-hidden="true" /> LIBRA / KHÔNG GIAN TRI THỨC</span>
          <h1>Một cuốn sách hay.<br /><em>Một góc nhìn mới.</em></h1>
          <p className="hero-description">Đằng sau mỗi bìa sách là một thế giới đang chờ được khám phá. Chỉ cần một chút tò mò, bạn có thể tìm thấy những điều khiến mình nhìn cuộc sống theo cách khác.</p>
          <form className="hero-search" onSubmit={search} role="search">
            <Search size={21} aria-hidden="true" /><label htmlFor="home-keyword" className="sr-only">Tên sách, tác giả hoặc ISBN</label>
            <input id="home-keyword" type="search" value={keyword} onChange={event => setKeyword(event.target.value)} placeholder="Tên sách, tác giả hoặc ISBN…" />
            <button type="submit" className="primary-button">Khám phá sách <ArrowRight size={17} /></button>
          </form>
          <div className="hero-links"><a href="#sach-moi" className="hero-jump">Xem sách mới bổ sung <MoveDownRight size={17} aria-hidden="true" /></a><span className="hero-link-divider" aria-hidden="true" /><span className="hero-trust"><CheckCircle2 size={16} /> Tra cứu miễn phí · Đặt giữ trực tuyến</span></div>
        </div>
        <Hero3DBookShowcase books={books} status={status} retry={retryBooks} active={active} />
      </div>
      <div className="public-container immersive-hero-bottom"><span><span className="hero-bottom-pulse" aria-hidden="true" /> THƯ VIỆN ĐANG CHỜ BẠN KHÁM PHÁ</span>
        <Link to="/catalog" className="immersive-hero-discover">DUYỆT TOÀN BỘ KHO SÁCH <ArrowRight size={14} aria-hidden="true" /></Link>
      </div>
    </section>

    <div className="public-container home-benefits" data-libra-reveal>
      {[{ Icon: Search, title: 'Tìm sách dễ dàng', text: 'Tra cứu nhan đề, tác giả và ISBN' }, { Icon: Bookmark, title: 'Chủ động đặt giữ', text: 'Theo dõi đơn và thời hạn đến nhận' }, { Icon: BookOpen, title: 'Đọc sách an tâm', text: 'Xem sách đang mượn và hạn trả' }].map(({ Icon, title, text }) => <div key={title}><span><Icon size={23} /></span><div><h2>{title}</h2><p>{text}</p></div></div>)}
    </div>

    <LibraryStatistics overview={overview} error={overviewError} />

    <FeaturedBooks books={featured} loading={featuredLoading} error={featuredError} />

    <section id="sach-moi" className="public-container home-discover" aria-labelledby="discover-title" data-libra-reveal>
      <div className="section-heading"><div><p className="eyebrow">MỞ THÊM MỘT TRANG MỚI</p><h2 id="discover-title">Khám phá tủ sách</h2><p>Những đầu sách vừa được bổ sung vào thư viện, chờ bạn khám phá.</p></div><Link className="text-link" to="/catalog">Xem tất cả sách <ArrowRight size={17} /></Link></div>
      {status === 'loading' && <div className="book-grid" role="status" aria-label="Đang tải đầu sách"><span className="sr-only">Đang tải đầu sách…</span>{Array.from({ length: 5 }, (_, i) => <div className="book-skeleton" key={i}><div /><span /><span /></div>)}</div>}
      {retryNotice && <FeedbackAlert tone="error" message={retryNotice} onDismiss={() => setRetryNotice('')} className="mb-4" />}
      {status === 'error' && <div className="catalog-unavailable" role="status"><BookOpen size={30} /><h3>Chưa tải được tủ sách</h3><p>Vui lòng thử lại để xem dữ liệu hiện tại của thư viện.</p><button className="secondary-button" onClick={retryBooks}>Thử lại</button></div>}
      {status === 'success' && (books.length ? <div className="book-grid">{books.map(book => <BookCard key={book.id} book={book} />)}</div> : <EmptyState title="Tủ sách đang được cập nhật" description="Các đầu sách sẽ xuất hiện tại đây khi thư viện bổ sung vào danh mục." />)}
    </section>

    <HowItWorks />

    <section id="gioi-thieu" className="home-about" aria-labelledby="about-title" data-libra-reveal>
      <div className="public-container libra-about-grid">
        <div className="libra-about-photo">
          <img src="https://images.unsplash.com/photo-1704098623946-2f273eeeead2?auto=format&fit=crop&w=1100&q=82"
            loading="lazy" decoding="async" alt="Không gian thư viện hiện đại với các dãy kệ sách và khu vực đọc" />
        </div>
        <div className="libra-about-copy"><p className="eyebrow">VỀ KHÔNG GIAN TRI THỨC LIBRA</p>
          <h2 id="about-title">Mỗi cuốn sách đều xứng đáng được tìm thấy.</h2>
          <p>LIBRA kết nối bạn đọc với tri thức: tra cứu đầu sách, xem tình trạng sẵn sàng, đặt giữ và theo dõi quá trình mượn – trả ngay trong tài khoản.</p>
          <p className="libra-about-quote">“Đọc sách là cách ta gặp gỡ những góc nhìn chưa từng biết đến.”</p>
          <div className="libra-about-info"><div className="libra-hours-panel"><h3>Giờ phục vụ</h3>
            {overview?.weeklySchedule?.length ? <ul className="libra-opening-hours">{overview.weeklySchedule.map(day => <li key={day.dayOfWeek}>
              <span>{['', 'Thứ Hai','Thứ Ba','Thứ Tư','Thứ Năm','Thứ Sáu','Thứ Bảy','Chủ Nhật'][day.dayOfWeek]}</span>
              <span>{day.open && day.openTime && day.closeTime ? `${day.openTime.slice(0,5)} – ${day.closeTime.slice(0,5)}` : 'Đóng cửa'}</span>
            </li>)}</ul> : <p>Đang cập nhật lịch phục vụ từ hệ thống.</p>}
          </div></div>
          <Link to="/catalog" className="primary-button libra-about-cta">Khám phá bộ sưu tập <ArrowRight size={17} /></Link>
        </div>
      </div>
    </section>
  </>
}
