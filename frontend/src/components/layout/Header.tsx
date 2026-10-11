import { LogOut, Menu, UserRound } from 'lucide-react'
import { useNavigate } from 'react-router-dom'

import {
  clearAuthSession,
  getCurrentUser,
} from '../../core/auth/authStorage'

interface HeaderProps {
  onMenuToggle?: () => void
  menuOpen?: boolean
}

export default function Header({ onMenuToggle, menuOpen }: HeaderProps) {
  const navigate = useNavigate()
  const currentUser = getCurrentUser()

  const roleLabel = (() => {
    switch (currentUser?.role) {
      case 'ADMIN': return 'Quản trị hệ thống'
      case 'LIBRARY_MANAGER': return 'Quản lý thư viện'
      case 'LIBRARIAN': return 'Thủ thư'
      case 'READER': return 'Bạn đọc'
      default: return '-'
    }
  })()

  const handleLogout = () => {
    clearAuthSession()
    navigate('/', { replace: true })
  }

  return (
    <header className="sticky top-0 z-30 flex min-h-16 items-center justify-between border-b border-slate-200/90 bg-white/95 px-4 shadow-[0_1px_2px_rgba(15,23,42,0.02)] backdrop-blur sm:px-6">
      <div className="flex min-w-0 items-center gap-3">
        <button
          type="button"
          onClick={onMenuToggle}
          className="inline-flex h-10 w-10 shrink-0 items-center justify-center rounded-xl border border-slate-200 bg-white text-slate-600 transition hover:bg-slate-50 hover:text-slate-900 lg:hidden"
          aria-label="Mở menu điều hướng"
          aria-expanded={menuOpen}
        >
          <Menu size={20} />
        </button>

        <div className="min-w-0">
          <p className="truncate text-base font-bold tracking-tight text-slate-950 sm:text-lg">
            Hệ thống Quản lý Thư viện
          </p>
          <p className="hidden text-xs text-slate-500 sm:block">
            Quản lý mượn / trả sách tập trung
          </p>
        </div>
      </div>

      <div className="ml-3 flex items-center gap-2 sm:gap-3">
        <div className="hidden items-center gap-3 rounded-xl border border-slate-200 bg-slate-50/70 px-3 py-2 md:flex">
          <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-blue-50 text-blue-600">
            <UserRound size={17} />
          </div>
          <div className="max-w-48 text-right">
            <div className="truncate text-sm font-semibold text-slate-900">
              {currentUser?.fullName ?? 'Người dùng'}
            </div>
            <div className="text-xs text-slate-500">
              {roleLabel}
            </div>
          </div>
        </div>

        <button
          type="button"
          onClick={handleLogout}
          className="inline-flex min-h-10 items-center gap-2 rounded-xl border border-slate-200 bg-white px-3 text-sm font-semibold text-slate-700 transition hover:border-red-200 hover:bg-red-50 hover:text-red-600"
          aria-label="Đăng xuất khỏi hệ thống"
          title="Đăng xuất khỏi hệ thống"
        >
          <LogOut size={17} />
          <span className="hidden sm:inline">Đăng xuất</span>
        </button>
      </div>
    </header>
  )
}
