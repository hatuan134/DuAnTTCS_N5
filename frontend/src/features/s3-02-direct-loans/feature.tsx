import { BookUser } from 'lucide-react'
import type { FeatureModule } from '../../types/feature'
import { loanRoles } from '../s3-01-loans/loanService'
import DirectLoanPage from './DirectLoanPage'

const feature: FeatureModule = {
  id: 's3-02-direct-loans', order: 151,
  appRoutes: [{ path: 'loans/direct', element: <DirectLoanPage /> }],
  navItems: [{ label: 'Cho mượn tại quầy', to: '/loans/direct', icon: BookUser, order: 151, roles: loanRoles }],
}
export default feature
