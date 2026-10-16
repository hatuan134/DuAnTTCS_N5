import { loadablePage } from '../../app/loadablePage'
import type { FeatureModule } from '../../types/feature'
const BookDetailPage = loadablePage(() => import('./BookDetailPage'))
const BookCopyDetailPage = loadablePage(() => import('./BookCopyDetailPage'))

const feature: FeatureModule = {
  id: 's2-02-book-copies',
  order: 120,
  appRoutes: [
    { path: 'books/:bookId', element: <BookDetailPage /> },
    { path: 'book-copies/:copyId', element: <BookCopyDetailPage /> },
  ],
}
export default feature
