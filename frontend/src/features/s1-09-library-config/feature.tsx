import {
  CalendarDays,
  Warehouse,
} from 'lucide-react'

import LibrarySettingsPage
  from './LibrarySettingsPage'

import type {
  FeatureModule,
} from '../../types/feature'

const feature: FeatureModule = {
  id: 's1-09-library-settings',

  order: 90,

  appRoutes: [
    {
      path: 'warehouse-shelves',
      element: (
        <LibrarySettingsPage
          mode="warehouse"
        />
      ),
    },
    {
      path: 'library-calendar',
      element: (
        <LibrarySettingsPage
          mode="calendar"
        />
      ),
    },
  ],

  navItems: [
    {
      label: 'Kho & kệ',
      to: '/warehouse-shelves',
      icon: Warehouse,
      order: 90,
    },
    {
      label: 'Lịch đóng cửa',
      to: '/library-calendar',
      icon: CalendarDays,
      order: 91,
    },
  ],
}

export default feature