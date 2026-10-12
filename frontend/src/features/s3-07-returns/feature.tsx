import { loadablePage } from '../../app/loadablePage'
import { BookDown } from 'lucide-react'
import type { FeatureModule } from '../../types/feature'
import { loanRoles } from '../s3-01-loans/loanService'
const ReceiveReturnPage = loadablePage(() => import('./ReceiveReturnPage'))

const feature: FeatureModule = {
  id: 's3-07-returns', order: 157,
  appRoutes: [{ path: 'loans/receive-return', element: <ReceiveReturnPage /> }],
  navItems: [{ label: 'Nhận trả sách', to: '/loans/receive-return', icon: BookDown, order: 157, roles: loanRoles }],
}
export default feature
