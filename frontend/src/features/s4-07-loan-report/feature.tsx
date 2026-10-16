import { BarChart3 } from 'lucide-react'
import { loadablePage } from '../../app/loadablePage'
import type { FeatureModule } from '../../types/feature'

const LoanReportPage = loadablePage(() => import('./LoanReportPage'))

const feature: FeatureModule = {
  id: 's4-07-loan-report',
  order: 260,
  appRoutes: [{ path: 'reports/loans', element: <LoanReportPage /> }],
  navItems: [
    {
      label: 'Báo cáo mượn trả',
      to: '/reports/loans',
      icon: BarChart3,
      order: 260,
      roles: ['LIBRARY_MANAGER', 'ADMIN'],
    },
  ],
}

export default feature
