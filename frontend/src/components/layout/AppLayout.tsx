import { useState } from 'react'
import { Outlet } from 'react-router-dom'

import Header from './Header'
import Sidebar from './Sidebar'

export default function AppLayout() {
  const [sidebarOpen, setSidebarOpen] = useState(false)

  return (
    <div className="app-shell">
      <Sidebar
        open={sidebarOpen}
        onClose={() => setSidebarOpen(false)}
      />

      <div className="app-main lg:pl-64">
        <Header onMenuToggle={() => setSidebarOpen((open) => !open)} />

        <main className="app-content">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
