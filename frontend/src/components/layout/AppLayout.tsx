import AiAssistantMount from '../../features/ai-assistant/AiAssistantMount'
import { useState } from 'react'
import { Outlet } from 'react-router-dom'
import Header from './Header'
import Sidebar from './Sidebar'
import PageTransition from '../ui/PageTransition'
import ScrollToTopButton from '../ui/ScrollToTopButton'

export default function AppLayout() {
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const [collapsed, setCollapsed] = useState(false)
  return <div className={`app-shell ${collapsed ? 'sidebar-collapsed' : ''}`}>
    <a href="#main-content" className="skip-link">Đến nội dung chính</a>
    <AiAssistantMount />
    <Sidebar open={sidebarOpen} onClose={() => setSidebarOpen(false)} collapsed={collapsed} onCollapse={() => setCollapsed(value => !value)} />
    <div className="app-main">
      <Header onMenuToggle={() => setSidebarOpen(value => !value)} menuOpen={sidebarOpen} />
      <main className="app-content" id="main-content" tabIndex={-1}><PageTransition><Outlet /></PageTransition></main>
    </div>
    <ScrollToTopButton />
  </div>
}
