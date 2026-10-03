import { BookMarked } from 'lucide-react'
import type { FeatureModule } from '../../types/feature'
import ReadyPickupPage from './ReadyPickupPage'
import ReadyPickupDetailPage from './ReadyPickupDetailPage'
import { pickupRoles } from './pickupService'

const feature: FeatureModule = {
  id: 's2-09-ready-pickup',
  order: 140,
  appRoutes: [
    { path: 'reservations/ready-for-pickup', element: <ReadyPickupPage /> },
    { path: 'reservations/ready-for-pickup/:reservationId', element: <ReadyPickupDetailPage /> },
  ],
  navItems: [{
    label: 'Sách đang chờ nhận',
    to: '/reservations/ready-for-pickup',
    icon: BookMarked,
    order: 140,
    roles: pickupRoles,
  }],
}

export default feature
