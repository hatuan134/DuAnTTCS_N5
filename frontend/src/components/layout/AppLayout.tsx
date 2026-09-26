import { Outlet } from 'react-router-dom'

import Header from './Header'
import Sidebar from './Sidebar'

export default function AppLayout() {
  return (
    <div className="min-h-screen bg-slate-50">
      <Sidebar />

      {/* Sidebar rộng w-64 = 16rem */}
      <div className="min-h-screen lg:pl-64">
        <Header />

        <main className="min-w-0 p-6">
          <Outlet />
        </main>
      </div>
    </div>
  )
}