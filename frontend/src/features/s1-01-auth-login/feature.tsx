import { loadablePage } from '../../app/loadablePage'
import type {
  FeatureModule,
} from '../../types/feature'

const LoginPage = loadablePage(() => import('./LoginPage'))

const feature: FeatureModule = {
  id: 's1-01-auth-login',

  order: 10,

  publicRoutes: [
    {
      path: '/login',
      element: <LoginPage />,
    },
  ],
}

export default feature