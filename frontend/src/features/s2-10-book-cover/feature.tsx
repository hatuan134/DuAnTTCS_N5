import { loadablePage } from '../../app/loadablePage'
import type { FeatureModule } from '../../types/feature'
const EditBookCoverPage = loadablePage(() => import('./EditBookCoverPage'))

const feature: FeatureModule = {
  id: 's2-10-book-cover',
  order: 130,
  appRoutes: [{ path: 'books/:bookId/cover/edit', element: <EditBookCoverPage /> }],
}
export default feature
