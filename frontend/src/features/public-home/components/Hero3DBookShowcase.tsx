import { useEffect, useRef, useState } from 'react'
import { ArrowUpRight, BookOpen, RefreshCw, Sparkles } from 'lucide-react'
import { Link, useNavigate } from 'react-router-dom'
import type { Book } from '../../s1-08-catalog/catalogService'
import { publicBookImageUrl } from '../../s2-10-book-cover/bookCoverService'
import { isbnCoverUrl, lookupTitleCover } from './coverLookup'

// The original, locally bundled ThreeUI HTML is deliberately left untouched.
// The script-free derivative keeps its authored HTML/CSS; React owns interactions.
const sceneUrl = `${import.meta.env.BASE_URL}threeui/libra-book-showcase.html`
const slots = ['codex', 'claude', 'cursor'] as const

// Keep ThreeUI's authored page-turning, hover, perspective and parallax CSS.
// Hide the demo's unrelated navigation/documentation; its original HTML is not rewritten.
const libraSceneStyle = `
  html, body, .stage { background: transparent !important; }
  html, body { width: 100%; height: 100%; min-height: 0; overflow: hidden !important; }
  .stage { width: 100%; height: 100%; min-height: 0; overflow: hidden; }
  .stage::after { opacity: .055; }
  .topbar, .hero-word, .detail-panel, .bottom-dock, .close-button,
  .blossom-field, .menu-layer, .toast, .cover-copy, .cover-motion,
  .open-badge { display: none !important; }
  .gallery { z-index: 1; pointer-events: none; }
  .book-card { pointer-events: auto; outline: none;
    transition: transform 520ms var(--ease), filter 500ms ease, opacity 500ms ease !important; }
  .book-card:focus-visible .front-cover { outline: 3px solid #3b82f6; outline-offset: 4px; }
  .book-card[data-book="codex"] { --x: 24%; --y: 25%; --w: min(33%, 205px); --r: -13deg; --yaw: -14deg; }
  .book-card[data-book="claude"] { --x: 51%; --y: 12%; --w: min(39%, 250px); --r: -1deg; --yaw: -3deg; }
  /* Keep the right-hand cover within the clipped, rounded ThreeUI stage during hover/parallax. */
  .book-card[data-book="cursor"] { --x: 74%; --y: 27%; --w: min(33%, 205px); --r: 10deg; --yaw: 10deg; }
  /* Filtering the entire 3D book forces compositing of its cover at reduced detail.
     Apply the hover glow to the existing shadow layer, never the image itself. */
  .book-card:hover, .book-card:focus-visible { --hover-y: -34px; z-index: 12; filter: none; }
  .book-card:hover .book-shadow, .book-card:focus-visible .book-shadow {
    box-shadow: 0 0 34px 12px rgba(90,161,255,.24);
  }
  /* The source demo uses sample covers from ThreeUI. Never display those assets. */
  .front-cover { overflow: hidden; background: #243247 !important; background-image: none !important; }
  .libra-cover-image { display: block; width: 100%; height: 100%; object-fit: cover;
    opacity: 0; transition: opacity 320ms ease; position: absolute; inset: 0; z-index: 2;
    /* Preserve the browser's high-quality image resampling under CSS 3D. */
    image-rendering: auto; filter: none !important; backface-visibility: hidden;
  }
  .libra-cover-image[data-ready="true"] { opacity: 1; }
  /* No fabricated cover / no 'Chưa có ảnh bìa' label while looking up real art. */
  .front-cover::before { z-index: 4; }
  .front-cover::after { z-index: 5; }
  .libra-cover-image { transition: opacity 480ms ease, filter 480ms ease; }
  .book-card[data-libra-loading="true"] .front-cover {
    background: linear-gradient(125deg, #1b3153, #263f68 43%, #101e37 100%) !important;
  }

  .book-card[data-libra-opening="true"] { z-index: 20 !important; --hover-y: -28px; }
  .book-card[data-libra-opening="true"] .front-cover {
    transform: translate3d(0,0,24px) rotateY(-40deg);
  }
  .book-card[data-libra-opening="true"] .page-fan i { opacity: 1; }
  .book-card[data-libra-opening="true"] .page-fan i:nth-child(1) { transform: translate3d(0,0,10px) rotateY(-5deg); }
  .book-card[data-libra-opening="true"] .page-fan i:nth-child(2) { transform: translate3d(0,0,11px) rotateY(-10deg); }
  .book-card[data-libra-opening="true"] .page-fan i:nth-child(3) { transform: translate3d(0,0,12px) rotateY(-15deg); }
  .book-card[data-libra-opening="true"] .page-fan i:nth-child(4) { transform: translate3d(0,0,13px) rotateY(-20deg); }
  body[data-libra-paused="true"] *, body[data-libra-paused="true"] *::before,
  body[data-libra-paused="true"] *::after { animation-play-state: paused !important; }
  @media (max-width: 540px) {
    .book-card[data-book="codex"] { --x: 25%; --y: 33%; --w: 34%; }
    .book-card[data-book="claude"] { --x: 50%; --y: 23%; --w: 43%; }
    .book-card[data-book="cursor"] { --x: 72%; --y: 33%; --w: 32%; --r: 9deg; --yaw: 9deg; }
    .book-card:hover, .book-card:focus-visible { --hover-y: -12px; }
  }
  @media (prefers-reduced-motion: reduce) {
    .book-card { transition: none !important; }
    .book-card:hover { --hover-y: 0px; }
  }
`

type SourceCard = HTMLButtonElement & { dataset: DOMStringMap }

function authorsOf(book: Book) {
  return book.authors?.length ? book.authors.map(author => author.name).join(', ') : book.authorName || 'Chưa có tác giả'
}

/**
 * In the authored scene the middle book is most prominent. Put the newest LIBRA
 * book there; the second newest on the left and the third on the right.
 */
function booksBySlot(books: Book[]) {
  return [books[1], books[0], books[2]]
}

export default function Hero3DBookShowcase({ books, status, retry, active }: {
  books: Book[]
  status: 'loading' | 'success' | 'error'
  retry: () => void
  active: boolean
}) {
  const navigate = useNavigate()
  const frameRef = useRef<HTMLIFrameElement>(null)
  // The source iframe persists while visibility changes; do not capture stale motion state.
  const activeRef = useRef(active)
  useEffect(() => { activeRef.current = active }, [active])
  const navigationTimer = useRef<ReturnType<typeof setTimeout> | null>(null)
  const [sceneLoaded, setSceneLoaded] = useState(false)
  const [scenePrepared, setScenePrepared] = useState(false)
  const [sceneFailed, setSceneFailed] = useState(false)
  const [activeBookId, setActiveBookId] = useState<number | null>(null)
  const topThree = books.slice(0, 3)

  useEffect(() => {
    if (!sceneLoaded || !frameRef.current || status !== 'success') return
    const doc = frameRef.current.contentDocument
    if (!doc?.querySelector('.gallery')) return

    let style = doc.getElementById('libra-scene-style') as HTMLStyleElement | null
    if (!style) {
      style = doc.createElement('style')
      style.id = 'libra-scene-style'
      style.textContent = libraSceneStyle
      doc.head.appendChild(style)
    }

    // Never show the original sample books, even while new data is loading.
    const slotBooks = booksBySlot(topThree)
    const liveCards = slots.map((slot, index) => {
      const card = doc.querySelector<SourceCard>(`.book-card[data-book="${slot}"]`)
      const book = slotBooks[index]
      if (!card) return null
      card.hidden = !book
      card.style.display = book ? '' : 'none'
      if (!book) return null

      card.setAttribute('aria-label', `Mở chi tiết ${book.title}, ${authorsOf(book)}`)
      card.dataset.libraBookId = String(book.id)
      const cover = card.querySelector<HTMLElement>('.front-cover')
      const originalMotion = card.querySelector('video')
      if (originalMotion) {
        (originalMotion as HTMLVideoElement).pause()
        originalMotion.remove() // Demo media is unrelated to the library's covers.
      }
      card.querySelector('.cover-copy')?.remove()
      if (cover) {
        // ThreeUI contains hard-coded demo covers. Only display the real book image.
        cover.style.backgroundImage = 'none'
        // Remove any outdated fallback label from older patches if the frame was reused.
        cover.querySelector('.libra-cover-unavailable')?.remove()
        let img = cover.querySelector<HTMLImageElement>('.libra-cover-image')
        if (!img) {
          img = doc.createElement('img')
          img.className = 'libra-cover-image'
          img.alt = '' // The button already has the accessible title.
          img.decoding = 'async'
          img.loading = 'eager'
          img.setAttribute('fetchpriority', 'high')
          cover.appendChild(img)
        }
        const image = img
        const bookId = String(book.id)
        const source = book.coverImageUrl?.trim() ?? ''
        const previouslyApplied = card.dataset.libraAppliedCoverKey
        const coverKey = `${bookId}|${source}|${book.isbn || ''}`
        // Visibility/scroll state changes must not restart loaded image requests.
        if (previouslyApplied !== coverKey) {
          card.dataset.libraAppliedCoverKey = coverKey
          card.dataset.libraLoading = 'true'
          image.dataset.ready = 'false'
          image.removeAttribute('src')
          const thumbnail = source ? publicBookImageUrl(book.id, source, true) : ''
          const original = source ? publicBookImageUrl(book.id, source, false) : ''
          const isbn = isbnCoverUrl(book)
          // The Hero displays large, rotated covers. Load the full-resolution original
          // first; the thumbnail is a fallback only if the original fails.
          // Never upscale the backend thumbnail when a sharper source exists.
          const candidates = [...new Set([original, thumbnail, isbn].filter(Boolean))] as string[]
          let index = 0
          let lookupStarted = false
          const current = () => card.dataset.libraAppliedCoverKey === coverKey
          const tryNext = () => {
            if (!current()) return
            if (index < candidates.length) {
              image.src = candidates[index++]
            } else if (!lookupStarted) {
              lookupStarted = true
              void lookupTitleCover(book).then(url => {
                if (!current()) return
                if (url && !candidates.includes(url)) image.src = url
                else card.dataset.libraLoading = 'false'
              })
            } else {
              // No genuine cover could be found; leave the book unlabelled,
              // rather than falsely representing a generated illustration as real.
              card.dataset.libraLoading = 'false'
            }
          }
          image.onload = () => {
            if (!current()) return
            if (image.naturalWidth > 1 && image.naturalHeight > 1) {
              image.dataset.ready = 'true'
              card.dataset.libraLoading = 'false'
            } else tryNext()
          }
          image.onerror = tryNext
          tryNext()
        }

      }
      return card
    }).filter((card): card is SourceCard => card !== null)

    // Play the authored book-opening layers, then route into LIBRA's real catalog.
    // The iframe itself has no scripts or demo click handlers.
    let navigating = false
    const openBook = (event: MouseEvent) => {
      // Elements from an iframe belong to a different JS realm: avoid instanceof Element.
      const target = event.target as Element | null
      if (!target || target.nodeType !== 1) return
      const card = target.closest<SourceCard>('.book-card')
      if (!card || !liveCards.includes(card)) return
      event.stopPropagation()
      event.preventDefault()
      if (navigating) return
      const bookId = Number(card.dataset.libraBookId)
      if (!Number.isSafeInteger(bookId)) return
      navigating = true
      card.dataset.libraOpening = 'true'
      if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
        navigate(`/catalog/books/${bookId}`)
      } else {
        navigationTimer.current = setTimeout(() => navigate(`/catalog/books/${bookId}`), 600)
      }
    }
    doc.addEventListener('click', openBook, true)
    // Highlight the corresponding live book information when a 3D book is hovered.
    const hoverCleanups = liveCards.map(card => {
      const enter = () => { card.dataset.hovered = 'true'; setActiveBookId(Number(card.dataset.libraBookId)) }
      const leave = () => { card.dataset.hovered = 'false'; setActiveBookId(current => current === Number(card.dataset.libraBookId) ? null : current) }
      card.addEventListener('pointerenter', enter)
      card.addEventListener('pointerleave', leave)
      card.addEventListener('focus', enter)
      card.addEventListener('blur', leave)
      return () => {
        card.removeEventListener('pointerenter', enter)
        card.removeEventListener('pointerleave', leave)
        card.removeEventListener('focus', enter)
        card.removeEventListener('blur', leave)
      }
    })
    // Reveal only after all three demo covers have been removed/replaced.
    if (frameRef.current) frameRef.current.style.visibility = 'visible'
    setScenePrepared(true)

    // Pointer events do not bubble across iframe boundaries. Forward a lightweight
    // pointer signal to the existing liquid-shader host to preserve the interactive
    // background while the user moves across the ThreeUI books.
    const frameElement = frameRef.current
    const liquidHost = frameElement?.closest<HTMLElement>('.immersive-hero')
    // Same-origin DOM access is enough: the child cannot run scripts. Preserve
    // the original gallery parallax here, with at most one update per frame.
    const motion = window.matchMedia('(prefers-reduced-motion: reduce)')
    let motionFrame = 0
    let pointerX = 0
    let pointerY = 0
    const updateParallax = () => {
      motionFrame = 0
      const x = motion.matches || !activeRef.current ? 0 : pointerX
      const y = motion.matches || !activeRef.current ? 0 : pointerY
      doc.documentElement.style.setProperty('--mx', `${x * 11}px`)
      doc.documentElement.style.setProperty('--my', `${y * 8}px`)
      liveCards.forEach(card => {
        const depth = card.dataset.book === 'claude' ? 1 : .58
        card.style.setProperty('--local-x', `${x * 15 * depth}px`)
        card.style.setProperty('--local-y', `${y * 9 * depth}px`)
      })
    }
    const scheduleParallax = () => {
      if (!motionFrame) motionFrame = window.requestAnimationFrame(updateParallax)
    }
    const resetParallax = () => { pointerX = 0; pointerY = 0; scheduleParallax() }
    motion.addEventListener('change', resetParallax)
    const forwardPointer = (event: PointerEvent) => {
      if (!liquidHost || !frameElement || !activeRef.current || event.pointerType === 'touch') return
      pointerX = event.clientX / Math.max(doc.documentElement.clientWidth, 1) - .5
      pointerY = event.clientY / Math.max(doc.documentElement.clientHeight, 1) - .5
      scheduleParallax()
      const bounds = frameElement.getBoundingClientRect()
      liquidHost.dispatchEvent(new PointerEvent('pointermove', {
        clientX: bounds.left + event.clientX,
        clientY: bounds.top + event.clientY,
        pointerType: event.pointerType || 'mouse',
        bubbles: false,
      }))
    }
    const clearPointer = () => {
      resetParallax()
      liquidHost?.dispatchEvent(new PointerEvent('pointerleave', { bubbles: false }))
    }
    doc.addEventListener('pointermove', forwardPointer, { passive: true })
    doc.addEventListener('pointerleave', clearPointer)

    return () => {
      if (motionFrame) window.cancelAnimationFrame(motionFrame)
      motion.removeEventListener('change', resetParallax)
      doc.removeEventListener('pointermove', forwardPointer)
      doc.removeEventListener('pointerleave', clearPointer)
      doc.removeEventListener('click', openBook, true)
      hoverCleanups.forEach(cleanup => cleanup())
      if (navigationTimer.current) clearTimeout(navigationTimer.current)
      liveCards.forEach(card => { delete card.dataset.libraOpening })
    }
  }, [books, navigate, sceneLoaded, status])

  useEffect(() => {
    const doc = frameRef.current?.contentDocument
    const body = doc?.body
    if (body) body.dataset.libraPaused = String(!active)
    if (doc && !active) {
      doc.documentElement.style.setProperty('--mx', '0px')
      doc.documentElement.style.setProperty('--my', '0px')
      doc.querySelectorAll<HTMLElement>('.book-card').forEach(card => {
        card.style.setProperty('--local-x', '0px')
        card.style.setProperty('--local-y', '0px')
      })
    }
  }, [active, sceneLoaded, scenePrepared])

  useEffect(() => () => {
    if (navigationTimer.current) clearTimeout(navigationTimer.current)
  }, [])

  const onFrameLoad = () => {
    const doc = frameRef.current?.contentDocument
    if (!doc?.querySelector('.gallery')) { setSceneFailed(true); return }
    if (frameRef.current) frameRef.current.style.visibility = 'hidden'
    setScenePrepared(false)
    setSceneLoaded(true)
  }

  return <div className="hero-showcase" aria-label="Ba đầu sách mới nhất">
    <div className="threeui-showcase-stage">
      <div className="threeui-stage-label" aria-hidden="true"><span /> SÁCH MỚI TRÊN KỆ</div>
      <span className="threeui-glow threeui-glow-one" aria-hidden="true" />
      <span className="threeui-glow threeui-glow-two" aria-hidden="true" />
      <span className="threeui-orbit threeui-orbit-one" aria-hidden="true" />
      <span className="threeui-orbit threeui-orbit-two" aria-hidden="true" />
      <span className="threeui-stage-halo" aria-hidden="true" />
      <span className="threeui-stage-floor" aria-hidden="true" />
      <span className="threeui-stage-spark threeui-spark-a" aria-hidden="true" />
      <span className="threeui-stage-spark threeui-spark-b" aria-hidden="true" />
      <span className="threeui-stage-spark threeui-spark-c" aria-hidden="true" />
      <span className="threeui-scene-indicator" aria-hidden="true"><Sparkles size={13} /> CHẠM VÀO THẾ GIỚI SÁCH</span>
      {status === 'loading' && <div className="showcase-message" role="status"><div className="showcase-loading" aria-hidden="true"><span /><span /><span /></div><p>Đang tìm những cuốn sách mới…</p></div>}
      {status === 'error' && <div className="showcase-message" role="status"><BookOpen size={36} aria-hidden="true" /><h3>Chưa thể tải tủ sách</h3><p>Hãy thử lại, hoặc truy cập danh mục sách.</p><button type="button" className="secondary-button" onClick={retry}><RefreshCw size={16} /> Thử lại</button></div>}
      {status === 'success' && (topThree.length === 0 ? <div className="showcase-message" role="status"><BookOpen size={36} /><h3>Thư viện đang bổ sung đầu sách</h3><p>Các quyển sách mới sẽ xuất hiện tại đây.</p></div> : <>
        {!sceneFailed && <iframe
          ref={frameRef}
          src={sceneUrl}
          title="Mô hình ba quyển sách 3D có thể tương tác"
          className="threeui-book-frame"
          style={{ visibility: 'hidden' }}
          data-ready={scenePrepared}
          onLoad={onFrameLoad}
          sandbox="allow-same-origin"
          loading="eager"
        />}
        {sceneFailed && <div className="showcase-message" role="status"><BookOpen size={36} /><p>Hiệu ứng 3D không khả dụng. Bạn vẫn có thể mở từng đầu sách bên dưới.</p></div>}
      </>)}
    </div>
    {status === 'success' && topThree.length > 0 && <div className="threeui-book-titles">
      {booksBySlot(topThree).map((book, index) => book && <Link to={`/catalog/books/${book.id}`} key={book.id} className={`threeui-book-title threeui-book-title-${index}`} data-active={activeBookId === book.id} aria-label={`Xem chi tiết ${book.title}`} onMouseEnter={() => {
        setActiveBookId(book.id)
        const card = frameRef.current?.contentDocument?.querySelector<HTMLElement>(`.book-card[data-libra-book-id="${book.id}"]`)
        if (card) card.dataset.hovered = 'true'
      }} onMouseLeave={() => {
        setActiveBookId(null)
        const card = frameRef.current?.contentDocument?.querySelector<HTMLElement>(`.book-card[data-libra-book-id="${book.id}"]`)
        if (card) card.dataset.hovered = 'false'
      }} onFocus={() => setActiveBookId(book.id)} onBlur={() => setActiveBookId(null)}>
        {index === 1 && <span className="threeui-newest">MỚI NHẤT</span>}
        <strong title={book.title}>{book.title}</strong>
        <span className="threeui-author" title={authorsOf(book)}>{authorsOf(book)}</span>
        <span className="threeui-availability" data-available={book.availableCount > 0}>{book.availableCount > 0 ? `${book.availableCount} bản sẵn sàng` : 'Xem tình trạng sách'}</span>
        <span className="threeui-see-more">Xem & đặt giữ <ArrowUpRight size={13} aria-hidden="true" /></span>
      </Link>)}
    </div>}
  </div>
}
