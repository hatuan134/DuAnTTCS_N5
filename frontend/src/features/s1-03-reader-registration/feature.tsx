import { UserRoundCheck } from 'lucide-react'
import { Navigate } from 'react-router-dom'

import { getCurrentUser } from '../../core/auth/authStorage'
import type {
  FeatureModule,
} from '../../types/feature'

import RegisterPage from './RegisterPage'
import ReadersPage from './ReadersPage'
import ReaderProfilePage from './ReaderProfilePage'

const STAFF_ROLES = [
  'LIBRARIAN',
  'LIBRARY_MANAGER',
  'ADMIN',
]

function StaffReadersPage({ detail = false }: { detail?: boolean }) {
  const currentUser = getCurrentUser()

  if (
    !currentUser ||
    !STAFF_ROLES.includes(currentUser.role)
  ) {
    return (
      <Navigate
        to="/dashboard"
        replace
      />
    )
  }

  return detail ? <ReaderProfilePage /> : <ReadersPage />
}

const feature: FeatureModule = {
  id: 's1-03-reader-registration',

  order: 30,

  publicRoutes: [
    {
      path: '/register',
      element: <RegisterPage />,
    },
  ],

  appRoutes: [
    {
      path: 'readers',
      element: <StaffReadersPage />,
    },
    {
      path: 'readers/:readerId',
      element: <StaffReadersPage detail />,
    },
  ],

  navItems: [
    {
      label: 'Bạn đọc',
      to: '/readers',
      icon: UserRoundCheck,
      order: 30,
      roles: STAFF_ROLES,
    },
  ],
}

export default feature
