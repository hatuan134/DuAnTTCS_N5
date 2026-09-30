import type { FeatureModule } from '../../types/feature'
import BookDetailPage from './BookDetailPage'
import BookCopyDetailPage from './BookCopyDetailPage'

const feature: FeatureModule = {
  id: 's2-02-book-copies',
  order: 120,
  appRoutes: [
    { path: 'books/:bookId', element: <BookDetailPage /> },
    { path: 'book-copies/:copyId', element: <BookCopyDetailPage /> },
  ],
}
export default feature
