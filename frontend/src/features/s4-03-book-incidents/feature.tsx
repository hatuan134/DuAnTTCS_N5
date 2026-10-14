import { AlertTriangle, Settings2 } from 'lucide-react'
import { loadablePage } from '../../app/loadablePage'
import type { FeatureModule } from '../../types/feature'
const BookIncidentsPage = loadablePage(() => import('./BookIncidentsPage'))
const CompensationSettingsPage = loadablePage(() => import('./CompensationSettingsPage'))
const feature: FeatureModule = {
  id: 's4-03-book-incidents', order: 190,
  appRoutes: [
    { path: 'book-incidents', element: <BookIncidentsPage /> },
    { path: 'compensation-settings', element: <CompensationSettingsPage /> },
  ],
  navItems: [
    { label: 'Sách mất hoặc hỏng nặng', to: '/book-incidents', icon: AlertTriangle, order: 190, roles: ['LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN'] },
    { label: 'Hệ số bồi thường', to: '/compensation-settings', icon: Settings2, order: 191, roles: ['LIBRARY_MANAGER', 'ADMIN'] },
  ],
}
export default feature
