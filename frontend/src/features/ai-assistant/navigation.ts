/** Defense in depth. URLs are server-generated; model text never becomes a clickable link. */
export function allowedAiPath(href: string, role: string): boolean {
  if (/^\/catalog(?:\/books\/[1-9]\d*)?$/.test(href)) return true
  if (href.startsWith('/catalog?')) {
    try {
      const url = new URL(href, 'https://libra.local')
      const keys: string[] = []
      url.searchParams.forEach((_value, key) => keys.push(key))
      const keyword = url.searchParams.get('keyword') ?? ''
      const publicationYearText = url.searchParams.get('publicationYear')
      const publicationYear = publicationYearText === null ? null : Number(publicationYearText)
      const maxYear = new Date().getFullYear()
      return url.origin === 'https://libra.local' && url.pathname === '/catalog' &&
        keys.length <= 3 && new Set(keys).size === keys.length &&
        keys.every(key => key === 'keyword' || key === 'availableOnly' || key === 'publicationYear') &&
        (publicationYearText === null || (/^[0-9]{1,4}$/.test(publicationYearText) && publicationYear !== null &&
          Number.isInteger(publicationYear) && publicationYear >= 1 && publicationYear <= maxYear)) &&
        keyword.length <= 100 && !/[\x00-\x1f]/.test(keyword) &&
        (url.searchParams.get('availableOnly') === null || url.searchParams.get('availableOnly') === 'true')
    } catch { return false }
  }
  if (role === 'PUBLIC') return ['/login', '/register'].includes(href)
  if (role === 'READER') return ['/my-library-card', '/my-borrowed-books', '/my-reservations', '/change-password'].includes(href) || /^\/loans\/[1-9]\d*$/.test(href)
  if (!['LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN'].includes(role)) return false
  if (['/dashboard', '/authors', '/categories', '/library-cards', '/borrow-policy',
    '/readers', '/cataloging', '/reservations/ready-for-pickup', '/loans', '/loans/search',
    '/loans/overdue', '/loans/rejections', '/users', '/audit-log', '/library-calendar',
    '/book-incidents', '/loans/reminders'].includes(href)) return true
  if (/^\/loans\/overdue\?minDays=(?:[0-9]|[1-9][0-9]{1,2})&exclusive=1$/.test(href) && Number(new URLSearchParams(href.split('?')[1]).get('minDays')) <= 365) return true
  if (/^\/(?:loans|books|book-copies)\/[1-9]\d*$/.test(href)) return true
  if (['LIBRARIAN', 'ADMIN'].includes(role) && ['/loans/direct', '/loans/receive-return', '/fee-payments'].includes(href)) return true
  if (['LIBRARY_MANAGER', 'ADMIN'].includes(role) && /^\/reports\/loans\?startDate=\d{4}-\d{2}-\d{2}&endDate=\d{4}-\d{2}-\d{2}$/.test(href)) {
    const params = new URLSearchParams(href.split('?')[1])
    const start = params.get('startDate') || ''
    const end = params.get('endDate') || ''
    const a = new Date(`${start}T00:00:00Z`).getTime()
    const b = new Date(`${end}T00:00:00Z`).getTime()
    return Number.isFinite(a) && Number.isFinite(b) && start === new Date(a).toISOString().slice(0, 10) &&
      end === new Date(b).toISOString().slice(0, 10) && a <= b && b - a <= 92 * 86400000
  }
  if (['LIBRARY_MANAGER', 'ADMIN'].includes(role) && [
    '/warehouse-shelves', '/manager/auto-cancellations',
    '/overdue-fees/reduction-requests/pending', '/compensation-settings',
    '/card-lock-suggestions', '/card-lock-suggestion-settings',
    '/reports/overdue-debts', '/reports/overdue-debts/history',
    '/reports/loans',
  ].includes(href)) return true
  return role === 'ADMIN' && href === '/permission-review'
}
