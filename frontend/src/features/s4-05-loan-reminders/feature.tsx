import { BellRing } from 'lucide-react'
import { loadablePage } from '../../app/loadablePage'
import type { FeatureModule } from '../../types/feature'

const LoanRemindersPage = loadablePage(() => import('./LoanRemindersPage'))

const feature: FeatureModule = {
  id: 's4-05-loan-reminders',
  order: 250,
  appRoutes: [
    { path: 'loans/reminders', element: <LoanRemindersPage /> },
  ],
  navItems: [
    {
      label: 'Nhắc nhở đến hạn',
      to: '/loans/reminders',
      icon: BellRing,
      order: 250,
      roles: ['LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN'],
    },
  ],
}

export default feature
