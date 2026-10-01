import {
  BookOpen,
  Tags,
  UserRound,
} from 'lucide-react'

import CatalogManagementPage from './CatalogManagementPage'

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
    {
      path: 'cataloging',
      element: (
        <CatalogManagementPage
          mode="books"
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
      roles: ['LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN'],
    },
    {
      label: 'Thể loại',
      to: '/categories',
      icon: Tags,
      order: 81,
      roles: ['LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN'],
    },
    {
      label: 'Đầu sách',
      to: '/cataloging',
      icon: BookOpen,
      order: 82,
      roles: ['LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN'],
    },
  ],
}

export default feature
