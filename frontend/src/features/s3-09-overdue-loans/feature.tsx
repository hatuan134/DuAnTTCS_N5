import { ClockAlert } from 'lucide-react'
import type { FeatureModule } from '../../types/feature'
import { loanRoles } from '../s3-01-loans/loanService'
import OverdueLoansPage from './OverdueLoansPage'

const feature: FeatureModule = {
  id: 's3-09-overdue-loans',
  order: 159,
  appRoutes: [
    { path: 'loans/overdue', element: <OverdueLoansPage /> },
  ],
  navItems: [
    {
      label: 'Phiếu mượn quá hạn',
      to: '/loans/overdue',
      icon: ClockAlert,
      order: 159,
      roles: loanRoles,
    },
  ],
}

export default feature
