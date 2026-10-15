import { Receipt } from 'lucide-react'
import { loadablePage } from '../../app/loadablePage'
import type { FeatureModule } from '../../types/feature'

const FeeCollectionPage = loadablePage(() => import('./FeeCollectionPage'))
const feature: FeatureModule = {
  id: 's4-02-fee-payments', order: 180,
  appRoutes: [{ path: 'fee-payments', element: <FeeCollectionPage /> }],
  navItems: [{ label: 'Thu phí và biên lai', to: '/fee-payments', icon: Receipt, order: 180, roles: ['LIBRARIAN', 'ADMIN'] }],
}
export default feature
