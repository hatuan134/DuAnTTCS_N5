import {
  ScrollText,
} from 'lucide-react'

import AuditLogPage
  from './AuditLogPage'

import type {
  FeatureModule,
} from '../../types/feature'

const feature: FeatureModule = {
  id: 's1-10-audit-log',

  order: 100,

  appRoutes: [
    {
      path: 'audit-log',
      element: <AuditLogPage />,
    },
  ],

  navItems: [
    {
      label: 'Nhật ký hoạt động',
      to: '/audit-log',
      icon: ScrollText,
      order: 100,
    },
  ],
}

export default feature