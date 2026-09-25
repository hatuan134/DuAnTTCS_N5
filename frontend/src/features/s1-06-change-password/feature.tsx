import { UserRound } from 'lucide-react'

import ChangePasswordPage from './ChangePasswordPage'

import type {
  FeatureModule,
} from '../../types/feature'

const feature: FeatureModule = {
  id: 's1-06-change-password',

  order: 60,

  appRoutes: [
    {
      path: 'change-password',
      element: <ChangePasswordPage />,
    },
  ],

  navItems: [
    {
      label: 'Hồ sơ cá nhân',
      to: '/change-password',
      icon: UserRound,
      order: 60,
    },
  ],
}

export default feature