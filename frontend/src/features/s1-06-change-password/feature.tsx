import { loadablePage } from '../../app/loadablePage'
import { UserRound } from 'lucide-react'

const ChangePasswordPage = loadablePage(() => import('./ChangePasswordPage'))

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
      roles: ['READER'],
    },
  ],
}

export default feature