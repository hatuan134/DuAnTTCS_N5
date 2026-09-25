import { Users } from 'lucide-react'

import FeaturePlaceholder
  from '../../components/common/FeaturePlaceholder'

import type {
  FeatureModule,
} from '../../types/feature'

const feature: FeatureModule = {
  id: 's1-02-user-management',

  order: 20,

  appRoutes: [
    {
      path: 'users',
      element: (
        <FeaturePlaceholder
          story="S1-02"
          title="Quản lý tài khoản"
          description="Tạo tài khoản, gán vai trò và quản lý trạng thái tài khoản."
        />
      ),
    },
  ],

  navItems: [
    {
      label: 'Tài khoản',
      to: '/users',
      icon: Users,
      order: 20,
    },
  ],
}

export default feature