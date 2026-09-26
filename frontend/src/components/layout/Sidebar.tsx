import { BookOpen } from 'lucide-react'
import { NavLink } from 'react-router-dom'

import { navItems } from '../../app/featureRegistry'
import { getCurrentUser } from '../../core/auth/authStorage'

export default function Sidebar() {
  const currentUser = getCurrentUser()

  const visibleNavItems = navItems.filter(
    (item) =>
      !item.roles ||
      (currentUser?.role && item.roles.includes(currentUser.role)),
  )

  return (
    <aside className="fixed left-0 top-0 h-screen w-64 border-r border-slate-200 bg-white">
      <div className="flex h-16 items-center gap-3 border-b border-slate-200 px-5">
        <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-blue-600 text-white">
          <BookOpen size={19} />
        </div>

        <div>
          <div className="font-semibold text-slate-900">
            LIBRA
          </div>

          <div className="text-xs text-slate-500">
            Library Management
          </div>
        </div>
      </div>

      <nav className="space-y-1 p-3">
        {visibleNavItems.map((item) => {
          const Icon = item.icon

          return (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) =>
                [
                  'flex items-center gap-3 rounded-md px-3 py-2.5',
                  'text-sm font-medium transition-colors',
                  isActive
                    ? 'bg-blue-50 text-blue-700'
                    : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900',
                ].join(' ')
              }
            >
              <Icon size={18} />

              <span>
                {item.label}
              </span>
            </NavLink>
          )
        })}
      </nav>
    </aside>
  )
}