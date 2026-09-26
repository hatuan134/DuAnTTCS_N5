import { Users } from 'lucide-react'
import {
  Navigate,
} from 'react-router-dom'

import { getCurrentUser } from '../../core/auth/authStorage'

import type {
  FeatureModule,
} from '../../types/feature'

import InitialPasswordPage from './InitialPasswordPage'
import UserManagementPage from './UserManagementPage'

function AdminOnlyUserManagement() {
  const currentUser = getCurrentUser()

  if (currentUser?.role !== 'ADMIN') {
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
      element: <AdminOnlyUserManagement />,
    },
  ],

  navItems: [
    {
      label: 'Tài khoản',
      to: '/users',
      icon: Users,
      order: 20,
      roles: ['ADMIN'],
    },
  ],
}

export default feature
