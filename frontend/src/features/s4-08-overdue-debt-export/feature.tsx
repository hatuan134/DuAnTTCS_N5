import { FileSpreadsheet, History } from 'lucide-react'
import { loadablePage } from '../../app/loadablePage'
import type { FeatureModule } from '../../types/feature'
const OverdueDebtExportPage = loadablePage(() => import('./OverdueDebtExportPage'))
const ExportHistoryPage = loadablePage(() => import('./ExportHistoryPage'))
const feature: FeatureModule = {
  id: 's4-08-overdue-debt-export', order: 270,
  appRoutes: [
    { path: 'reports/overdue-debts', element: <OverdueDebtExportPage /> },
    { path: 'reports/overdue-debts/history', element: <ExportHistoryPage /> },
  ],
  navItems: [
    { label: 'Xuất quá hạn và nợ phí', to: '/reports/overdue-debts', icon: FileSpreadsheet, order: 270, roles: ['LIBRARY_MANAGER'] },
    { label: 'Lịch sử xuất dữ liệu', to: '/reports/overdue-debts/history', icon: History, order: 271, roles: ['LIBRARY_MANAGER'] },
  ],
}
export default feature
