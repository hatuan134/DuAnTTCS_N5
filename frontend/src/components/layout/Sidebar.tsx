import { useState } from 'react'
import { BookOpen, ChevronDown, PanelLeftClose, PanelLeftOpen, X, ArrowUpRight } from 'lucide-react'
import { Link, useLocation } from 'react-router-dom'
import { navItems } from '../../app/featureRegistry'
import { activeNavigation, groupedNavigation, visibleNavigation } from '../../app/navigation'
import { getCurrentUser } from '../../core/auth/authStorage'
import Modal from '../ui/Modal'

interface SidebarProps { open?: boolean; onClose?: () => void; collapsed?: boolean; onCollapse?: () => void }

export default function Sidebar({ open = false, onClose = () => {}, collapsed = false, onCollapse }: SidebarProps) {
  const { pathname } = useLocation()
  const [groupState, setGroupState] = useState<{ path: string; closed: string[] }>({ path: pathname, closed: [] })
  const visibleItems = visibleNavigation(navItems, getCurrentUser()?.role)
  const groups = groupedNavigation(visibleItems)
  const active = activeNavigation(visibleItems, pathname)
  const activeGroup = groups.find(group => group.items.some(item => item.to === active))?.id
  const closedGroups = groupState.path === pathname ? groupState.closed : groupState.closed.filter(id => id !== activeGroup)
  if (groupState.path !== pathname) setGroupState({ path: pathname, closed: closedGroups })
  const toggleGroup = (id: string) => setGroupState({ path: pathname, closed: closedGroups.includes(id) ? closedGroups.filter(value => value !== id) : [...closedGroups, id] })

  const content = (mobile: boolean) => <>
    <div className="sidebar-brand">
      <Link to="/" className="brand" aria-label="LIBRA – Trang chủ" onClick={onClose}><span className="brand-icon"><BookOpen size={22} /></span><span className="sidebar-label">LIBRA<small>Không gian làm việc</small></span></Link>
      {mobile && <button type="button" className="icon-button" onClick={onClose} aria-label="Đóng menu"><X size={18} /></button>}
    </div>
    <nav className="sidebar-nav" aria-label={mobile ? 'Điều hướng nghiệp vụ trên điện thoại' : 'Điều hướng nghiệp vụ'}>
      {groups.map(group => {
        const expanded = (!mobile && collapsed) || !closedGroups.includes(group.id)
        const id = `nav-${mobile ? 'mobile' : 'desktop'}-${group.id}`
        return <div className="nav-group" key={group.id}>
          <button type="button" className="nav-group-toggle" aria-expanded={expanded} aria-controls={id} onClick={() => toggleGroup(group.id)}>
            <span>{group.label}</span><ChevronDown size={14} className={expanded ? '' : 'group-chevron-closed'} />
          </button>
          <div id={id} className={`nav-group-content ${expanded ? 'expanded' : ''}`} {...(!expanded ? { inert: '' } : {})} aria-hidden={!expanded}>
            <div>{group.items.map(item => {
              const Icon = item.icon
              return <Link key={item.to} to={item.to} onClick={onClose} title={collapsed && !mobile ? item.label : undefined}
                aria-current={active === item.to ? 'page' : undefined} className={`sidebar-link ${active === item.to ? 'active' : ''}`}>
                <Icon size={19} aria-hidden="true" /><span className="sidebar-label">{item.label}</span>
              </Link>
            })}</div>
          </div>
        </div>
      })}
    </nav>
    <div className="sidebar-bottom"><Link to="/catalog" onClick={onClose} className="sidebar-link" title="Tra cứu sách công khai"><BookOpen size={19} /><span className="sidebar-label">Tra cứu sách <ArrowUpRight size={15} /></span></Link>
      {!mobile && <button type="button" className="sidebar-link collapse-button" onClick={onCollapse} aria-label={collapsed ? 'Mở rộng thanh điều hướng' : 'Thu gọn thanh điều hướng'}>{collapsed ? <PanelLeftOpen size={19} /> : <PanelLeftClose size={19} />}<span className="sidebar-label">Thu gọn</span></button>}
    </div>
  </>
  return <>
    <aside className={`desktop-sidebar ${collapsed ? 'is-collapsed' : ''}`}>{content(false)}</aside>
    {open && <Modal label="Điều hướng nghiệp vụ" onClose={onClose}><aside className="mobile-sidebar">{content(true)}</aside></Modal>}
  </>
}
