import { Settings } from 'lucide-react'

import CardTypesPage from './CardTypesPage'

import type {
  FeatureModule,
} from '../../types/feature'

const feature: FeatureModule = {
  id: 's1-05-borrow-policy',

  order: 50,

  appRoutes: [
    {
      path: 'borrow-policy',
      element: <CardTypesPage />,
    },
  ],

  navItems: [
    {
      label: 'Chính sách mượn',
      to: '/borrow-policy',
      icon: Settings,
      order: 50,
    },
  ],
}

export default feature