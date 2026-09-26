import { LogOut } from 'lucide-react'
import { useNavigate } from 'react-router-dom'

import {
  clearAuthSession,
  getCurrentUser,
} from '../../core/auth/authStorage'

export default function Header() {
  const navigate = useNavigate()

  const currentUser =
    getCurrentUser()

  const handleLogout = () => {
    clearAuthSession()

    navigate('/login', {
      replace: true,
    })
  }

  return (
    <header className="flex h-16 items-center justify-between border-b border-slate-200 bg-white px-6">
      <div>
        <h1 className="text-lg font-semibold text-slate-900">
          Hệ thống Quản lý Thư viện
        </h1>

        <p className="text-xs text-slate-500">
          Quản lý mượn / trả sách
        </p>
      </div>

      <div className="flex items-center gap-4">
        <div className="text-right">
          <div className="text-sm font-medium text-slate-900">
            {currentUser?.fullName ??
              'Người dùng'}
          </div>

          <div className="text-xs text-slate-500">
            {currentUser?.role ?? '-'}
          </div>
        </div>

        <button
          type="button"
          onClick={handleLogout}
          className="flex items-center gap-2 rounded-lg border border-slate-200 px-3 py-2 text-sm font-medium text-slate-700 transition hover:border-red-200 hover:bg-red-50 hover:text-red-600"
        >
          <LogOut size={17} />
          Đăng xuất
        </button>
      </div>
    </header>
  )
}