import { UserRoundCheck } from 'lucide-react'
import { Navigate } from 'react-router-dom'

import { getCurrentUser } from '../../core/auth/authStorage'
import type {
  FeatureModule,
} from '../../types/feature'

import RegisterPage from './RegisterPage'
import ReadersPage from './ReadersPage'
import ReaderProfilePage from './ReaderProfilePage'
import ReaderBasicProfilePage from './ReaderBasicProfilePage'
import ReaderHistoryDenied from './ReaderHistoryDenied'
import { canViewReaderLoanHistory } from './readerPermissions'

const STAFF_ROLES = [
  'LIBRARIAN',
  'LIBRARY_MANAGER',
  'ADMIN',
]

function StaffReadersPage({ detail = false, basic = false }: { detail?: boolean; basic?: boolean }) {
  const currentUser = getCurrentUser()

  if (detail && !canViewReaderLoanHistory(currentUser?.role)) {
    return <ReaderHistoryDenied />
  }

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

  return detail ? <ReaderProfilePage /> : basic ? <ReaderBasicProfilePage /> : <ReadersPage />
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
      path: 'readers/:readerId/profile',
      element: <StaffReadersPage basic />,
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
