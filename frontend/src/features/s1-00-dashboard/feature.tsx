import { LayoutDashboard } from 'lucide-react'

import type { FeatureModule } from '../../types/feature'
import DashboardPage from './DashboardPage'

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
    },
  ],
}

export default feature