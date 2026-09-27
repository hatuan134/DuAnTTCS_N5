import { CreditCard } from 'lucide-react'

import LibraryCardsPage from './LibraryCardsPage'
import type { FeatureModule } from '../../types/feature'

const feature: FeatureModule = {
  id: 's1-04-library-card',
  order: 40,

  appRoutes: [
    {
      path: 'library-cards',
      element: <LibraryCardsPage />,
    },
  ],

  navItems: [
    {
      label: 'Thẻ thư viện',
      to: '/library-cards',
      icon: CreditCard,
      order: 40,
      roles: ['LIBRARIAN', 'ADMIN'],
    },
  ],
}

export default feature
