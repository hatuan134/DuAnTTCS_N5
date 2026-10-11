import type { NavItem } from '../types/feature'

const groups = [
  { id: 'overview', label: 'Tổng quan', paths: ['/dashboard'] },
  { id: 'people', label: 'Người dùng', paths: ['/users', '/readers', '/library-cards', '/change-password'] },
  { id: 'books', label: 'Quản lý sách', paths: ['/cataloging', '/authors', '/categories'] },
  { id: 'warehouse', label: 'Kho', paths: ['/warehouse-shelves'] },
  { id: 'loans', label: 'Mượn trả', paths: ['/loans', '/loans/direct', '/loans/search', '/loans/rejections', '/loans/receive-return', '/loans/overdue', '/my-borrowed-books'] },
  { id: 'reservations', label: 'Đặt giữ', paths: ['/reservations/ready-for-pickup', '/my-reservations', '/manager/auto-cancellations'] },
  { id: 'settings', label: 'Quản trị', paths: ['/borrow-policy', '/library-calendar', '/audit-log'] },
]

export function visibleNavigation(items: NavItem[], role?: string) {
  return items.filter(item => !item.roles || Boolean(role && item.roles.includes(role)))
}

export function groupedNavigation(items: NavItem[]) {
  const result = groups.map(group => ({ ...group, items: items.filter(item => group.paths.includes(item.to)) })).filter(group => group.items.length)
  const extra = items.filter(item => !groups.some(group => group.paths.includes(item.to)))
  if (extra.length) result.push({ id: 'other', label: 'Chức năng khác', paths: [], items: extra })
  return result
}

export function activeNavigation(items: NavItem[], pathname: string) {
  // Prefer the most specific match, so /loans does not highlight together with /loans/search.
  const match = items.filter(item => pathname === item.to || pathname.startsWith(`${item.to}/`)).sort((a, b) => b.to.length - a.to.length)[0]
  if (match) return match.to
  if (/^\/books\//.test(pathname) || /^\/book-copies\//.test(pathname)) return '/cataloging'
  return ''
}
