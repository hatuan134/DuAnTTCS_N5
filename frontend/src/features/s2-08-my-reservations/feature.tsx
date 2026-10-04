import { BookMarked } from 'lucide-react'
import type { FeatureModule } from '../../types/feature'
import MyReservationsPage from './MyReservationsPage'

const feature: FeatureModule = {
  id: 's2-08-my-reservations',
  order: 130,
  appRoutes: [{ path: 'my-reservations', element: <MyReservationsPage /> }],
  navItems: [{
    label: 'Đơn đặt giữ của tôi',
    to: '/my-reservations',
    icon: BookMarked,
    order: 130,
    roles: ['READER'],
  }],
}

export default feature
