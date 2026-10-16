import { loadablePage } from '../../app/loadablePage'
import { Users } from 'lucide-react'
import {
  Navigate,
} from 'react-router-dom'

import { getCurrentUser } from '../../core/auth/authStorage'

import type {
  FeatureModule,
} from '../../types/feature'

const InitialPasswordPage = loadablePage(() => import('./InitialPasswordPage'))
const UserManagementPage = loadablePage(() => import('./UserManagementPage'))

function StaffUserManagement() {
  const currentUser = getCurrentUser()

  if (!['ADMIN', 'LIBRARY_MANAGER', 'LIBRARIAN'].includes(currentUser?.role ?? '')) {
    return <Navigate to="/dashboard" replace />
  }

  return <UserManagementPage />
}

const feature: FeatureModule = {
  id: 's1-02-user-management',

  order: 20,

  publicRoutes: [
    {
      path: '/set-initial-password',
      element: <InitialPasswordPage />,
    },
  ],

  appRoutes: [
    {
      path: 'users',
      element: <StaffUserManagement />,
    },
  ],

  navItems: [
    {
      label: 'Tài khoản',
      to: '/users',
      icon: Users,
      order: 20,
      roles: ['LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN'],
    },
  ],
}

export default feature
