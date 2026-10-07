import { ClipboardList } from 'lucide-react'
import type { FeatureModule } from '../../types/feature'
import LoanListPage from './LoanListPage'
import LoanDetailPage from './LoanDetailPage'
import { loanRoles } from './loanService'

const feature: FeatureModule = {
  id: 's3-01-loans', order: 150,
  appRoutes: [
    { path: 'loans', element: <LoanListPage /> },
    { path: 'loans/:loanId', element: <LoanDetailPage /> },
  ],
  navItems: [{ label: 'Phiếu mượn', to: '/loans', icon: ClipboardList, order: 150, roles: loanRoles }],
}
export default feature
