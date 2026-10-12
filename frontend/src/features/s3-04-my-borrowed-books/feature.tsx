import { loadablePage } from '../../app/loadablePage'
import { BookOpen } from 'lucide-react'
import type { FeatureModule } from '../../types/feature'
const MyBorrowedBooksPage = loadablePage(() => import('./MyBorrowedBooksPage'))

const feature: FeatureModule = {
  id: 's3-04-my-borrowed-books',
  order: 140,
  appRoutes: [{ path: 'my-borrowed-books', element: <MyBorrowedBooksPage /> }],
  navItems: [{ label: 'Sách đang mượn', to: '/my-borrowed-books', icon: BookOpen,
    order: 140, roles: ['READER'] }],
}
export default feature
