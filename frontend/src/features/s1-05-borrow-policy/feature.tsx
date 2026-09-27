import { Settings } from 'lucide-react'

import CardTypesPage from './CardTypesPage'

import type {
  FeatureModule,
} from '../../types/feature'

const readerRoles = ['LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN']

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
      roles: readerRoles,
    },
  ],
}

export default feature