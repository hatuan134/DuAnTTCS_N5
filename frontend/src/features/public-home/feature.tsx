import type { FeatureModule } from '../../types/feature'
import HomePage from './HomePage'

const feature: FeatureModule = { id: 'public-home', order: -1, publicRoutes: [{ path: '/', element: <HomePage /> }] }
export default feature
