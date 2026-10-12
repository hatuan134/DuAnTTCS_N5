import { useEffect, useRef } from 'react'

/** Write only transform variables; pointer movement never schedules a React render. */
export default function usePointerTilt(active: boolean) {
  const ref = useRef<HTMLAnchorElement>(null)
  useEffect(() => {
    const link = ref.current
    if (!link) return
    const finePointer = window.matchMedia('(hover: hover) and (pointer: fine)')
    let frame = 0
    let x = 0, y = 0, tx = 0, ty = 0, previous = 0
    const tick = (now: number) => {
      const ease = 1 - Math.exp(-Math.min(now - (previous || now - 16), 50) / 80)
      previous = now
      x += (tx - x) * ease; y += (ty - y) * ease
      link.style.setProperty('--pointer-x', `${x.toFixed(3)}deg`)
      link.style.setProperty('--pointer-y', `${y.toFixed(3)}deg`)
      if (Math.abs(tx - x) + Math.abs(ty - y) > 0.02) frame = requestAnimationFrame(tick)
      else { frame = 0; previous = 0 }
    }
    const start = () => { if (!frame) frame = requestAnimationFrame(tick) }
    const move = (event: PointerEvent) => {
      if (!active || !finePointer.matches || event.pointerType === 'touch') return
      const rect = link.getBoundingClientRect()
      tx = Math.max(-1, Math.min(1, (event.clientY - rect.top) / rect.height * 2 - 1)) * -7
      ty = Math.max(-1, Math.min(1, (event.clientX - rect.left) / rect.width * 2 - 1)) * 10
      start()
    }
    const reset = () => { tx = 0; ty = 0; start() }
    if (active) {
      link.addEventListener('pointermove', move)
      link.addEventListener('pointerleave', reset)
      link.addEventListener('pointercancel', reset)
      link.addEventListener('blur', reset)
    }
    return () => {
      cancelAnimationFrame(frame)
      link.removeEventListener('pointermove', move)
      link.removeEventListener('pointerleave', reset)
      link.removeEventListener('pointercancel', reset)
      link.removeEventListener('blur', reset)
      link.style.removeProperty('--pointer-x'); link.style.removeProperty('--pointer-y')
    }
  }, [active])
  return ref
}
