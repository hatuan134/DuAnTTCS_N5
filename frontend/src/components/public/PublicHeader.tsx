import { useState } from 'react'
import { BookOpen, LogOut, Menu, X, UserRound } from 'lucide-react'
import { Link, NavLink, useNavigate } from 'react-router-dom'
import { clearAuthSession } from '../../core/auth/authStorage'
import usePublicSession from '../../core/auth/usePublicSession'
import Modal from '../ui/Modal'

export default function PublicHeader() {
  const user = usePublicSession()
  const [open, setOpen] = useState(false)
  const navigate = useNavigate()
  const close = () => setOpen(false)
  const logout = () => { clearAuthSession(); close(); navigate('/', { replace: true }) }
  const links = <>
    <NavLink to="/" end onClick={close}>Trang chủ</NavLink>
    <NavLink to="/catalog" onClick={close}>Tra cứu sách</NavLink>
    <Link to="/#gioi-thieu" onClick={close}>Giới thiệu</Link>
  </>
  const account = user ? <>
    <Link to={user.role === 'READER' ? '/my-borrowed-books' : '/dashboard'} onClick={close} className="account-link">
      <UserRound size={17} /><span>{user.fullName}</span>
    </Link>
    {user.role === 'READER' && <Link to="/my-reservations" onClick={close} className="reader-reservations">Đặt giữ của tôi</Link>}
    <button type="button" className="icon-button" onClick={logout} aria-label="Đăng xuất" title="Đăng xuất"><LogOut size={18} /></button>
  </> : <>
    <Link to="/login" onClick={close} className="public-login">Đăng nhập</Link>
    <Link to="/register" onClick={close} className="primary-button">Đăng ký</Link>
  </>

  return <header className="public-header">
    <a href="#main-content" className="skip-link">Đến nội dung chính</a>
    <div className="public-container public-header-inner">
      <Link to="/" className="brand" aria-label="LIBRA – Trang chủ"><span className="brand-icon"><BookOpen size={24} /></span><span>LIBRA<small>Không gian tri thức</small></span></Link>
      <nav className="public-nav" aria-label="Điều hướng công khai">{links}</nav>
      <div className="public-account">{account}</div>
      <button className="icon-button public-menu-button" type="button" aria-expanded={open} aria-label="Mở menu" onClick={() => setOpen(true)}><Menu size={21} /></button>
    </div>
    {open && <Modal label="Menu điều hướng" onClose={close}>
      <div className="public-mobile-menu">
        <div className="flex items-center justify-between"><strong className="text-xl">LIBRA</strong><button className="icon-button" onClick={close} aria-label="Đóng menu"><X size={20} /></button></div>
        <nav aria-label="Điều hướng công khai trên điện thoại">{links}</nav>
        <div className="mobile-account">{account}</div>
      </div>
    </Modal>}
  </header>
}
