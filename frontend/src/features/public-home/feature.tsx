import type { FeatureModule } from '../../types/feature'
import { lazy, Suspense } from 'react'
const HomePage = lazy(() => import('./HomePage'))

const feature: FeatureModule = { id: 'public-home', order: -1, publicRoutes: [{ path: '/', element: <Suspense fallback={<div className="public-container py-12" role="status">Đang mở thư viện…</div>}><HomePage /></Suspense> }] }
export default feature
