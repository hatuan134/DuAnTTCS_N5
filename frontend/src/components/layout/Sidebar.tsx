import { BookOpen, X } from 'lucide-react'
import { NavLink } from 'react-router-dom'

import { navItems } from '../../app/featureRegistry'
import { getCurrentUser } from '../../core/auth/authStorage'

interface SidebarProps {
  open?: boolean
  onClose?: () => void
}

export default function Sidebar({
  open = false,
  onClose,
}: SidebarProps) {
  const currentUser = getCurrentUser()

  const visibleNavItems = navItems.filter(
    (item) =>
      !item.roles ||
      (currentUser?.role && item.roles.includes(currentUser.role)),
  )

  return (
    <>
      <button
        type="button"
        aria-label="Đóng menu điều hướng"
        onClick={onClose}
        className={[
          'fixed inset-0 z-40 bg-slate-950/40 backdrop-blur-[1px] transition-opacity lg:hidden',
          open ? 'pointer-events-auto opacity-100' : 'pointer-events-none opacity-0',
        ].join(' ')}
      />

      <aside
        className={[
          'fixed left-0 top-0 z-50 flex h-screen w-64 flex-col border-r border-slate-200/90 bg-white shadow-xl shadow-slate-950/5 transition-transform duration-200 lg:z-40 lg:translate-x-0 lg:shadow-none',
          open ? 'translate-x-0' : '-translate-x-full',
        ].join(' ')}
      >
        <div className="flex h-16 shrink-0 items-center gap-3 border-b border-slate-200 px-4">
          <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-blue-600 text-white shadow-sm shadow-blue-600/20">
            <BookOpen size={20} />
          </div>

          <div className="min-w-0 flex-1">
            <div className="font-bold tracking-wide text-slate-950">
              LIBRA
            </div>
            <div className="truncate text-xs text-slate-500">
              Quản lý thư viện
            </div>
          </div>

          <button
            type="button"
            onClick={onClose}
            className="inline-flex h-9 w-9 items-center justify-center rounded-lg text-slate-500 hover:bg-slate-100 hover:text-slate-800 lg:hidden"
            aria-label="Đóng menu"
          >
            <X size={19} />
          </button>
        </div>

        <nav
          className="flex-1 space-y-1 overflow-y-auto px-3 py-4"
          aria-label="Điều hướng chính"
        >
          {visibleNavItems.map((item) => {
            const Icon = item.icon

            return (
              <NavLink
                key={item.to}
                to={item.to}
                onClick={onClose}
                className={({ isActive }) => [
                  'group flex min-h-10 items-center gap-3 rounded-xl px-3 py-2.5',
                  'text-sm font-semibold transition-colors',
                  isActive
                    ? 'bg-blue-50 text-blue-700 shadow-[inset_3px_0_0_#2563eb]'
                    : 'text-slate-600 hover:bg-slate-50 hover:text-slate-950',
                ].join(' ')}
              >
                <Icon size={18} className="shrink-0" />
                <span className="min-w-0 truncate">{item.label}</span>
              </NavLink>
            )
          })}
        </nav>

      </aside>
    </>
  )
}
