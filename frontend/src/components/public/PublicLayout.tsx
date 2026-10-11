import { Outlet } from 'react-router-dom'
import PublicHeader from './PublicHeader'
import PublicSiteFooter from '../../features/s1-08-catalog/PublicSiteFooter'
import PageTransition from '../ui/PageTransition'

export default function PublicLayout() {
  return <div className="public-page"><PublicHeader /><main id="main-content" tabIndex={-1}><PageTransition><Outlet /></PageTransition></main><PublicSiteFooter /></div>
}
