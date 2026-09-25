import { UserRoundCheck } from 'lucide-react'

import FeaturePlaceholder
  from '../../components/common/FeaturePlaceholder'

import type {
  FeatureModule,
} from '../../types/feature'

const feature: FeatureModule = {
  id: 's1-03-reader-registration',

  order: 30,

  appRoutes: [
    {
      path: 'readers',
      element: (
        <FeaturePlaceholder
          story="S1-03"
          title="Quản lý bạn đọc"
          description="Tiếp nhận và quản lý hồ sơ đăng ký bạn đọc."
        />
      ),
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