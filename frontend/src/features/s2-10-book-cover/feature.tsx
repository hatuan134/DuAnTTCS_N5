import type { FeatureModule } from '../../types/feature'
import EditBookCoverPage from './EditBookCoverPage'

const feature: FeatureModule = {
  id: 's2-10-book-cover',
  order: 130,
  appRoutes: [{ path: 'books/:bookId/cover/edit', element: <EditBookCoverPage /> }],
}
export default feature
