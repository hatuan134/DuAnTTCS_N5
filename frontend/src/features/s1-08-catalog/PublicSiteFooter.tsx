import { useState } from 'react'
import FeedbackAlert from '../../components/ui/FeedbackAlert'
import { ArrowUpRight, Globe, Mail, MessageCircle } from 'lucide-react'
import { Link } from 'react-router-dom'
import LibraLogo from '../../components/brand/LibraLogo'

type Channel = { label: string; href: string | null; Icon: typeof Globe }

function configuredHttpLink(value: string | undefined): string | null {
  if (!value) return null
  try {
    const url = new URL(value.trim())
    return url.protocol === 'https:' || url.protocol === 'http:' ? url.href : null
  } catch { return null }
}

function configuredEmail(value: string | undefined): string | null {
  const email = value?.trim() ?? ''
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) ? `mailto:${email}` : null
}

export default function PublicSiteFooter() {
  const [pendingNotice, setPendingNotice] = useState<{ message: string; id: number } | null>(null)

  // No fabricated library accounts or email addresses. Set these three Vite
  // variables when the library's official contact details are available.
  const channels: Channel[] = [
    { label: 'Facebook', href: configuredHttpLink(import.meta.env.VITE_PUBLIC_FACEBOOK_URL), Icon: Globe },
    { label: 'Zalo', href: configuredHttpLink(import.meta.env.VITE_PUBLIC_ZALO_URL), Icon: MessageCircle },
    { label: 'Email', href: configuredEmail(import.meta.env.VITE_PUBLIC_CONTACT_EMAIL), Icon: Mail },
  ]

  return <footer className="public-footer libra-footer-compact" id="chan-trang">
    <div className="public-container footer-grid libra-footer-grid">
      <div className="libra-footer-intro">
        <Link to="/" className="brand" aria-label="LIBRA - Trang chủ"><span className="brand-icon"><LibraLogo size={34} /></span><span>LIBR<span className="libra-wordmark-accent">A</span><small>Không gian tri thức</small></span></Link>
        <p>Kết nối sách và bạn đọc, nuôi dưỡng niềm yêu thích khám phá tri thức.</p>
      </div>
      <nav aria-label="Liên kết nhanh"><h2>Khám phá</h2>
        <Link to="/">Trang chủ</Link><Link to="/catalog">Tra cứu sách <ArrowUpRight size={14} /></Link>
        <Link to="/#sach-noi-bat">Sách nổi bật</Link><Link to="/#thong-ke">Thống kê thư viện</Link><Link to="/#gioi-thieu">Giới thiệu LIBRA</Link>
      </nav>
      <nav aria-label="Mượn và trả sách"><h2>Dành cho bạn đọc</h2>
        <Link to="/#huong-dan-muon">Hướng dẫn mượn trả</Link><Link to="/catalog">Tìm đầu sách và đặt giữ</Link>
        <Link to="/my-reservations">Đặt giữ của tôi</Link><Link to="/my-borrowed-books">Sách đang mượn</Link>
      </nav>
      <div className="libra-footer-contact" id="lien-he"><h2>Liên hệ & hỗ trợ</h2>
        <div className="libra-contact-links" aria-label="Kênh liên hệ LIBRA">
          {channels.map(({ label, href, Icon }) => href
            ? <a key={label} href={href} target={href.startsWith('mailto:') ? undefined : '_blank'} rel={href.startsWith('mailto:') ? undefined : 'noopener noreferrer'}><Icon size={17} aria-hidden="true" />{label}</a>
            : <button key={label} type="button" title={`${label}: thông tin liên hệ đang cập nhật`} onClick={() => setPendingNotice({ message: `Kênh ${label} của LIBRA đang được cập nhật.`, id: performance.now() })}><Icon size={17} aria-hidden="true" />{label}</button>)}
        </div>
        {pendingNotice && <FeedbackAlert key={pendingNotice.id} message={pendingNotice.message} tone="info"
          onDismiss={() => setPendingNotice(null)} className="mt-2" />}
      </div>
    </div>
    <div className="public-container footer-bottom"><span>© {new Date().getFullYear()} LIBRA · Bản quyền thuộc đơn vị vận hành thư viện</span><span>Hệ thống quản lý mượn / trả sách thư viện</span></div>
  </footer>
}
