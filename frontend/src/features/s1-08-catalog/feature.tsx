import {
  Tags,
  UserRound,
} from 'lucide-react'

import CatalogManagementPage
  from './CatalogManagementPage'

import type {
  FeatureModule,
} from '../../types/feature'

const feature: FeatureModule = {
  id: 's1-08-catalog',

  order: 80,

  appRoutes: [
    {
      path: 'authors',
      element: (
        <CatalogManagementPage
          mode="authors"
        />
      ),
    },
    {
      path: 'categories',
      element: (
        <CatalogManagementPage
          mode="categories"
        />
      ),
    },
  ],

  navItems: [
    {
      label: 'Tác giả',
      to: '/authors',
      icon: UserRound,
      order: 80,
    },
    {
      label: 'Thể loại',
      to: '/categories',
      icon: Tags,
      order: 81,
    },
  ],
}

export default feature