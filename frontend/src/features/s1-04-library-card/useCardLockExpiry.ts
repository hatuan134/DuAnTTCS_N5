import { useEffect, useRef } from 'react'
import type { ManualCardLock } from './libraryCardService'

/** Refresh authoritative status at the earliest lock expiry or when returning to the page. */
export default function useCardLockExpiry(locks: (ManualCardLock | null | undefined)[], refresh: () => void) {
  const refreshRef = useRef(refresh)
  useEffect(() => { refreshRef.current = refresh }, [refresh])
  const nextExpiry = locks.filter(lock => lock?.active && lock.endsAt)
    .map(lock => Date.parse(lock!.endsAt!)).filter(Number.isFinite).sort((a, b) => a - b)[0]
  useEffect(() => {
    if (nextExpiry === undefined) return
    let timer: number
    const schedule = () => {
      const remaining = nextExpiry - Date.now()
      timer = window.setTimeout(() => {
        if (nextExpiry > Date.now()) schedule()
        else refreshRef.current()
      }, Math.min(2147483647, Math.max(0, remaining) + 100))
    }
    schedule()
    return () => window.clearTimeout(timer)
  }, [nextExpiry])
  useEffect(() => {
    const onFocus = () => refreshRef.current()
    window.addEventListener('focus', onFocus)
    return () => window.removeEventListener('focus', onFocus)
  }, [])
}
