import { loadablePage } from '../../app/loadablePage'
import { LayoutDashboard } from 'lucide-react'

import type { FeatureModule } from '../../types/feature'
const DashboardPage = loadablePage(() => import('./DashboardPage'))

const feature: FeatureModule = {
  id: 's1-00-dashboard',

  order: 0,

  appRoutes: [
    {
      path: 'dashboard',
      element: <DashboardPage />,
    },
  ],

  navItems: [
    {
      label: 'Tổng quan',
      to: '/dashboard',
      icon: LayoutDashboard,
      order: 0,
      roles: ['LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN'],
    },
  ],
}

export default feature