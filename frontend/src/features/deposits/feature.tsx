import { Wallet } from 'lucide-react'
import { loadablePage } from '../../app/loadablePage'
import type { FeatureModule } from '../../types/feature'
const DepositPage = loadablePage(() => import('./DepositPage'))
const feature: FeatureModule = {
  id: 'deposits', order: 181,
  appRoutes: [{ path: 'deposits', element: <DepositPage /> }],
  navItems: [{ label: 'Tiền đặt cọc', to: '/deposits', icon: Wallet, order: 181, roles: ['ADMIN', 'LIBRARY_MANAGER', 'LIBRARIAN', 'READER'] }],
}
export default feature
