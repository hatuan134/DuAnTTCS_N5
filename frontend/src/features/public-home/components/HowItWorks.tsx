import { ArrowRight, BookMarked, BookOpen, Search } from 'lucide-react'
import { Link } from 'react-router-dom'

const steps = [
  { Icon: Search, index: '01', title: 'Tìm cuốn sách phù hợp', text: 'Tra cứu công khai theo tên sách, tác giả hoặc ISBN; kiểm tra số bản sẵn sàng và tình trạng đặt giữ.' },
  { Icon: BookMarked, index: '02', title: 'Đặt giữ và chờ xác nhận', text: 'Đăng nhập bằng tài khoản bạn đọc có thẻ hợp lệ, gửi đơn đặt giữ nếu đủ điều kiện và theo dõi trạng thái. Thủ thư xử lý tại quầy.' },
  { Icon: BookOpen, index: '03', title: 'Nhận sách và trả đúng hạn', text: 'Đến quầy để thủ thư lập phiếu mượn; nhận sách, xem hạn trả trong tài khoản và trả sách tại thư viện.' },
]
export default function HowItWorks() {
  return <section id="huong-dan-muon" className="libra-how" aria-labelledby="libra-how-heading" data-libra-reveal>
    <div className="public-container"><div className="section-heading"><div><p className="eyebrow">BA BƯỚC BẮT ĐẦU</p>
      <h2 id="libra-how-heading">Mượn sách đơn giản cùng LIBRA</h2>
      <p>Đúng quy trình thư viện: đặt giữ trực tuyến, làm thủ tục mượn tại quầy.</p></div></div>
      <div className="libra-how-grid">{steps.map(({ Icon, index, title, text }) => <article key={index} className="libra-how-card">
        <span className="libra-how-number">{index}</span><div className="libra-how-icon"><Icon size={26} aria-hidden="true" /></div>
        <h3>{title}</h3><p>{text}</p></article>)}</div>
      <Link to="/catalog" className="primary-button libra-how-action">Bắt đầu tìm sách <ArrowRight size={17} /></Link>
    </div>
  </section>
}
