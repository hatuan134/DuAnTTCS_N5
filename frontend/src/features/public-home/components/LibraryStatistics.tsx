import { useEffect, useRef, useState } from 'react'
import { BookCopy, BookOpen, LibraryBig, UsersRound } from 'lucide-react'
import type { PublicOverview } from '../usePublicOverview'

function CountUp({ value, ready }: { value: number | undefined; ready: boolean }) {
  const [shown, setShown] = useState(0)
  useEffect(() => {
    if (!ready || value === undefined) return
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) { setShown(value); return }
    let frame = 0
    const started = performance.now()
    const tick = (now: number) => {
      const t = Math.min(1, (now - started) / 1250)
      setShown(Math.round(value * (1 - Math.pow(1 - t, 3))))
      if (t < 1) frame = requestAnimationFrame(tick)
    }
    frame = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(frame)
  }, [ready, value])
  return <span>{value === undefined ? '—' : shown.toLocaleString('vi-VN')}</span>
}

export default function LibraryStatistics({ overview, error }: { overview: PublicOverview | null; error: boolean }) {
  const ref = useRef<HTMLElement>(null)
  const [inView, setInView] = useState(false)
  useEffect(() => {
    const root = ref.current
    if (!root) return
    const observer = new IntersectionObserver(([entry]) => {
      if (entry.isIntersecting) { setInView(true); observer.disconnect() }
    }, { threshold: .25 })
    observer.observe(root)
    return () => observer.disconnect()
  }, [])
  const stats = [
    { Icon: BookOpen, title: 'Tổng đầu sách', value: overview?.totalTitles },
    { Icon: BookCopy, title: 'Số bản sách', value: overview?.totalCopies },
    { Icon: UsersRound, title: 'Bạn đọc đã duyệt', value: overview?.totalReaders },
    { Icon: LibraryBig, title: 'Lượt lập phiếu mượn', value: overview?.totalLoans },
  ]
  return <section id="thong-ke" className="home-statistics" ref={ref} aria-labelledby="home-stats-heading" data-libra-reveal>
    <div className="public-container">
      <header className="section-heading"><div><p className="eyebrow">LIBRA QUA NHỮNG CON SỐ</p>
      <h2 id="home-stats-heading">Thư viện lớn lên cùng mỗi lượt đọc.</h2>
      <p>Số liệu tổng hợp từ hệ thống thư viện, được cập nhật khi tải trang.</p></div></header>
      <div className="libra-stats-grid" aria-busy={!overview && !error}>
        {stats.map(({ Icon, title, value }) => <article className="libra-stat" key={title}>
          <Icon size={25} aria-hidden="true" /><strong><CountUp value={value} ready={inView} /></strong><span>{title}</span>
        </article>)}
      </div>
      {error && <p role="status" className="libra-stats-note">Chưa kết nối được số liệu thống kê. Vui lòng thử lại sau.</p>}
    </div>
  </section>
}
