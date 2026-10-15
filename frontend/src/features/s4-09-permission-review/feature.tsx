import { ShieldCheck } from 'lucide-react'
import { loadablePage } from '../../app/loadablePage'
import type { FeatureModule } from '../../types/feature'

const PermissionReviewPage = loadablePage(() => import('./PermissionReviewPage'))
const feature: FeatureModule = {
  id: 's4-09-permission-review', order: 280,
  appRoutes: [{ path: 'permission-review', element: <PermissionReviewPage /> }],
  navItems: [{ label: 'Rà soát phân quyền', to: '/permission-review', icon: ShieldCheck, order: 280, roles: ['ADMIN'] }],
}
export default feature
