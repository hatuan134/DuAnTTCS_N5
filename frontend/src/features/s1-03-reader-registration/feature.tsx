import { UserRoundCheck } from 'lucide-react'

import RegisterPage from './RegisterPage'
import ReadersPage from './ReadersPage'

import type {
  FeatureModule,
} from '../../types/feature'

const feature: FeatureModule = {
  id: 's1-03-reader-registration',

  order: 30,

  publicRoutes: [
    {
      path: '/register',
      element: <RegisterPage />,
    },
  ],

  appRoutes: [
    {
      path: 'readers',
      element: <ReadersPage />,
    },
  ],

  navItems: [
    {
      label: 'Bạn đọc',
      to: '/readers',
      icon: UserRoundCheck,
      order: 30,
    },
  ],
}

export default feature