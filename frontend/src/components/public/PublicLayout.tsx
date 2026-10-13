import FeedbackAlert from '../ui/FeedbackAlert'
import { useEffect, useState } from 'react'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import PublicHeader from './PublicHeader'
import PublicSiteFooter from '../../features/s1-08-catalog/PublicSiteFooter'
import PageTransition from '../ui/PageTransition'
import './libra-public-extras.css'

/** Navigate to a section without hiding its heading beneath the sticky header. */
function scrollToPublicAnchor(hash: string) {
  let id = hash.slice(1)
  try { id = decodeURIComponent(id) } catch { /* Ignore malformed encodings */ }
  const target = document.getElementById(id)
  if (!target) return
  const headerHeight = document.querySelector('.public-header')?.getBoundingClientRect().height ?? 0
  const y = Math.max(0, window.scrollY + target.getBoundingClientRect().top - headerHeight - 20)
  const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  window.scrollTo({ top: y, behavior: prefersReducedMotion ? 'instant' : 'smooth' })
}

export default function PublicLayout() {
  const location = useLocation()
  const navigate = useNavigate()
  const [logoutMessage, setLogoutMessage] = useState('')
  useEffect(() => {
    if (location.state?.loggedOut) {
      setLogoutMessage('Đã đăng xuất khỏi hệ thống.')
      navigate(location.pathname + location.search + location.hash, { replace: true, state: null })
    }
  }, [location, navigate])

  useEffect(() => {
    if (!location.hash) return
    // Wait until HomePage has mounted after navigation from another route.
    const timer = window.setTimeout(() => scrollToPublicAnchor(location.hash), 85)
    return () => window.clearTimeout(timer)
  }, [location.pathname, location.hash])

  useEffect(() => {
    // React Router does not always re-trigger effects when clicking the exact
    // same hash. Support repeated clicks on Header/Footer section links.
    const onClick = (event: MouseEvent) => {
      if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.altKey || event.shiftKey) return
      const link = (event.target as Element | null)?.closest<HTMLAnchorElement>('a[href^="/#"]')
      if (!link || window.location.pathname !== '/') return
      const hash = new URL(link.href).hash
      if (!hash || hash !== window.location.hash) return
      event.preventDefault()
      scrollToPublicAnchor(hash)
    }
    document.addEventListener('click', onClick, true)
    return () => document.removeEventListener('click', onClick, true)
  }, [])

  return <div className="public-page"><PublicHeader />
    {logoutMessage && <div className="mx-auto w-full max-w-7xl px-4 pt-3"><FeedbackAlert tone="success" message={logoutMessage} onDismiss={() => setLogoutMessage('')} /></div>}<main id="main-content" tabIndex={-1}><PageTransition><Outlet /></PageTransition></main><PublicSiteFooter /></div>
}
