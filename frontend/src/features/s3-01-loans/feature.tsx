import { ClipboardList, Search } from 'lucide-react'
import type { FeatureModule } from '../../types/feature'
import LoanListPage from './LoanListPage'
import LoanSearchPage from './LoanSearchPage'
import LoanDetailPage from './LoanDetailPage'
import { loanRoles } from './loanService'

const feature: FeatureModule = {
  id: 's3-01-loans', order: 150,
  appRoutes: [
    { path: 'loans', element: <LoanListPage /> },
    { path: 'loans/search', element: <LoanSearchPage /> },
    { path: 'loans/:loanId', element: <LoanDetailPage /> },
  ],
  navItems: [
    { label: 'Phiếu mượn', to: '/loans', icon: ClipboardList, order: 150, roles: loanRoles },
    { label: 'Tra cứu phiếu mượn', to: '/loans/search', icon: Search, order: 151, roles: loanRoles },
  ],
}
export default feature
