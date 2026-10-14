import { ShieldAlert, Settings2 } from 'lucide-react'
import { loadablePage } from '../../app/loadablePage'
import type { FeatureModule } from '../../types/feature'
const Suggestions = loadablePage(() => import('./CardLockSuggestionsPage'))
const Settings = loadablePage(() => import('./CardLockSuggestionSettingsPage'))
const feature: FeatureModule = {
  id: 's4-04-card-lock-suggestions', order: 200,
  appRoutes: [
    { path: 'card-lock-suggestions', element: <Suggestions /> },
    { path: 'card-lock-suggestion-settings', element: <Settings /> },
  ],
  navItems: [
    { label: 'Đề xuất khóa thẻ', to: '/card-lock-suggestions', icon: ShieldAlert, order: 200, roles: ['LIBRARY_MANAGER'] },
    { label: 'Ngưỡng nợ phí', to: '/card-lock-suggestion-settings', icon: Settings2, order: 201, roles: ['LIBRARY_MANAGER'] },
  ],
}
export default feature
