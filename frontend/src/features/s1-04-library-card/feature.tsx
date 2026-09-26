import { CreditCard } from 'lucide-react'

import FeaturePlaceholder
  from '../../components/common/FeaturePlaceholder'

import type {
  FeatureModule,
} from '../../types/feature'

const feature: FeatureModule = {
  id: 's1-04-library-card',

  order: 40,

  appRoutes: [
    {
      path: 'library-cards',
      element: (
        <FeaturePlaceholder
          story="S1-04"
          title="Thẻ thư viện"
          description="Duyệt và quản lý thẻ thư viện của bạn đọc."
        />
      ),
    },
  ],

  navItems: [
    {
      label: 'Thẻ thư viện',
      to: '/library-cards',
      icon: CreditCard,
      order: 40,
    },
  ],
}

export default feature