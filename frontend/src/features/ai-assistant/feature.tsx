import { CreditCard } from 'lucide-react'
import { loadablePage } from '../../app/loadablePage'
import type { FeatureModule } from '../../types/feature'
const MyLibraryCardPage = loadablePage(() => import('./MyLibraryCardPage'))
const feature: FeatureModule = {
  id: 'ai-assistant', order: 145,
  appRoutes: [{ path: 'my-library-card', element: <MyLibraryCardPage /> }],
  navItems: [{ label: 'Thẻ thư viện của tôi', to: '/my-library-card', icon: CreditCard, order: 139, roles: ['READER'] }],
}
export default feature
