import { BookOpen, ArrowUpRight } from 'lucide-react'
import { Link } from 'react-router-dom'

export default function PublicSiteFooter() {
  return <footer className="public-footer">
    <div className="public-container footer-grid">
      <div><Link to="/" className="brand"><span className="brand-icon"><BookOpen size={24} /></span><span>LIBRA<small>Không gian tri thức</small></span></Link><p>Từ một trang sách đến những chân trời mới.<br />Cùng LIBRA kết nối với thư viện mỗi ngày.</p></div>
      <nav aria-label="Khám phá thư viện"><h2>Khám phá</h2><Link to="/">Trang chủ</Link><Link to="/catalog">Tra cứu sách <ArrowUpRight size={14} /></Link><Link to="/#gioi-thieu">Giới thiệu LIBRA</Link></nav>
      <div><h2>Dành cho bạn đọc</h2><p>Đăng ký tài khoản trực tuyến. Sau khi đăng ký, đến quầy xuất trình giấy tờ để thủ thư duyệt hồ sơ và cấp thẻ.</p><Link className="text-link" to="/my-borrowed-books">Không gian bạn đọc <ArrowUpRight size={15} /></Link></div>
    </div>
    <div className="public-container footer-bottom"><span>© {new Date().getFullYear()} LIBRA</span><span>Hệ thống quản lý mượn / trả sách thư viện</span></div>
  </footer>
}
