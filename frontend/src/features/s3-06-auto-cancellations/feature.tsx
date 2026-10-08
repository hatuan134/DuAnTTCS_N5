import { CalendarX } from 'lucide-react'
import AutoCancelledReservationsPage from './AutoCancelledReservationsPage'
import type { FeatureModule } from '../../types/feature'

const feature: FeatureModule = {
  id: 's3-06-auto-cancellations',
  order: 306,

  appRoutes: [
    {
      path: 'manager/auto-cancellations',
      element: <AutoCancelledReservationsPage />,
    },
  ],

  navItems: [
    {
      label: 'Đơn huỷ tự động',
      to: '/manager/auto-cancellations',
      icon: CalendarX,
      order: 306,
      roles: ['LIBRARY_MANAGER', 'ADMIN'],
    },
  ],
}

export default feature
