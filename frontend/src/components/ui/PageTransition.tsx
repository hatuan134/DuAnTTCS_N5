import { useLayoutEffect, useRef, type ReactNode } from 'react'
import { useLocation } from 'react-router-dom'

/** Animate the existing content container; never key/remount forms or the shell. */
export default function PageTransition({ children }: { children: ReactNode }) {
  const { pathname, hash } = useLocation()
  const container = useRef<HTMLDivElement>(null)
  const previousPath = useRef(pathname)

  useLayoutEffect(() => {
    const node = container.current
    if (!node) return
    const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    const animation = !reduced && node.animate
      ? node.animate([{ opacity: 0, transform: 'translateY(10px)' }, { opacity: 1, transform: 'none' }],
        { duration: 240, easing: 'cubic-bezier(.2,.7,.2,1)' })
      : undefined
    if (previousPath.current !== pathname) {
      window.scrollTo({ top: 0, behavior: 'instant' })
      const heading = node.querySelector<HTMLElement>('h1, h2')
      if (heading) {
        heading.tabIndex = -1
        heading.focus({ preventScroll: true })
      }
    }
    previousPath.current = pathname
    return () => animation?.cancel()
  }, [pathname])

  useLayoutEffect(() => {
    if (!hash) return
    let id = hash.slice(1)
    try { id = decodeURIComponent(id) } catch { /* Keep malformed fragments harmless. */ }
    const target = document.getElementById(id)
    target?.scrollIntoView({ behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth' })
  }, [pathname, hash])

  return <div ref={container} className="page-transition">{children}</div>
}
