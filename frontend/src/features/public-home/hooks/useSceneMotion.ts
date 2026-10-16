import { useEffect, useState, useSyncExternalStore, type RefObject } from 'react'

const motionQuery = '(prefers-reduced-motion: reduce)'
const subscribe = (notify: () => void) => {
  const media = window.matchMedia(motionQuery)
  media.addEventListener('change', notify)
  return () => media.removeEventListener('change', notify)
}
export function useReducedMotion() {
  return useSyncExternalStore(subscribe, () => window.matchMedia(motionQuery).matches, () => true)
}

/** One visibility gate for CSS floating, pointer tilt and the WebGL renderer. */
export function useSceneMotion(ref: RefObject<HTMLElement>, enabled: boolean) {
  const [visible, setVisible] = useState(false)
  useEffect(() => {
    const element = ref.current
    if (!element) return
    let intersecting = true
    const sync = () => setVisible(intersecting && !document.hidden)
    const observer = new IntersectionObserver(entries => {
      intersecting = entries[0]?.isIntersecting ?? false
      sync()
    }, { threshold: 0.05 })
    observer.observe(element)
    document.addEventListener('visibilitychange', sync)
    sync()
    return () => { observer.disconnect(); document.removeEventListener('visibilitychange', sync) }
  }, [ref])
  return enabled && visible
}
