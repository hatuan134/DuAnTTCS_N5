import { useEffect, useRef, type RefObject } from 'react'
import { createSylvaPollenRenderer, type SylvaPollenRenderer } from './sylvaPollenRenderer'

/**
 * One particle layer for LIBRA: depth-layered white motes + cursor pollen spray.
 * Keep the public Hero and its other content unchanged.
 */
export default function LiquidParticleBackground({ hostRef, active }: { hostRef: RefObject<HTMLElement>; active: boolean }) {
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const rendererRef = useRef<SylvaPollenRenderer | null>(null)
  const activeRef = useRef(active)

  useEffect(() => {
    activeRef.current = active
    rendererRef.current?.setActive(active)
  }, [active])

  useEffect(() => {
    const canvas = canvasRef.current
    const host = hostRef.current
    if (!canvas || !host) return
    const renderer = createSylvaPollenRenderer(canvas, host)
    rendererRef.current = renderer
    renderer?.setActive(activeRef.current)
    return () => {
      renderer?.destroy()
      if (rendererRef.current === renderer) rendererRef.current = null
    }
  }, [hostRef])

  return <canvas ref={canvasRef} className="libra-fluid-overlay" data-renderer="pollen-gpu" aria-hidden="true" />
}
