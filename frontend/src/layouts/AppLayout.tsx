import { Outlet } from 'react-router-dom'

import Header from '../components/layout/Header'
import Sidebar from '../components/layout/Sidebar'

export default function AppLayout() {
  return (
    <div className="min-h-screen bg-slate-50">
      <Sidebar />

      <div className="ml-64 min-h-screen">
        <Header />

        <main className="p-6">
          <Outlet />
        </main>
      </div>
    </div>
  )
}