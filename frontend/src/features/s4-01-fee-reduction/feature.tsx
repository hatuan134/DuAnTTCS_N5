import { ClipboardCheck } from 'lucide-react'
import { loadablePage } from '../../app/loadablePage'
import type { FeatureModule } from '../../types/feature'

const OverdueFeeDetailPage = loadablePage(() => import('./OverdueFeeDetailPage'))
const PendingFeeReductionsPage = loadablePage(() => import('./PendingFeeReductionsPage'))

const feature: FeatureModule = {
  id: 's4-01-fee-reduction', order: 170,
  appRoutes: [
    { path: 'overdue-fees/:feeId', element: <OverdueFeeDetailPage /> },
    { path: 'overdue-fees/reduction-requests/pending', element: <PendingFeeReductionsPage /> },
  ],
  navItems: [{ label: 'Đề nghị miễn giảm phí', to: '/overdue-fees/reduction-requests/pending',
    icon: ClipboardCheck, order: 170, roles: ['LIBRARY_MANAGER'] }],
}
export default feature
