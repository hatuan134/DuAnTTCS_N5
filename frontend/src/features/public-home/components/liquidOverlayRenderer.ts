/**
 * LIBRA soft liquid wake and drifting dust — V4.2.
 * Inspired by the interaction style in the user's second video, NOT a copy of
 * that scene: dust floats slowly; the pointer pushes dust aside and leaves a
 * gentle, fading liquid wake. There is no gravity well, spinning or vortex.
 *
 * Performance: exactly one canvas, capped drawing resolution, no shaders,
 * no costly blur/shadow filters, no full-screen gradients and a 30/45 FPS cap.
 */
export interface LiquidOverlayRenderer {
  setActive(active: boolean): void
  destroy(): void
}

type Mote = {
  x: number
  y: number
  vx: number
  vy: number
  depth: number
  radius: number
  opacity: number
  phase: number
}

type WakePoint = { x: number; y: number; age: number }

const clamp = (value: number, min: number, max: number) => Math.max(min, Math.min(max, value))

export function createLiquidOverlayRenderer(canvas: HTMLCanvasElement, host: HTMLElement): LiquidOverlayRenderer | null {
  const context = canvas.getContext('2d', { alpha: true, desynchronized: true })
  if (!context) return null
  const ctx: CanvasRenderingContext2D = context

  let destroyed = false
  let active = false
  let raf = 0
  let lastPaint = 0
  let clock = 0
  let width = 1
  let height = 1
  let pointerX = -1000
  let pointerY = -1000
  let targetX = -1000
  let targetY = -1000
  let pointerVx = 0
  let pointerVy = 0
  let lastMove = 0
  let pointerInside = false
  let initialized = false
  let seed = 67129

  const random = () => {
    seed = (Math.imul(seed, 1664525) + 1013904223) | 0
    return (seed >>> 0) / 4294967296
  }
  const mobileOrSlow = matchMedia('(pointer: coarse)').matches ||
    (navigator.hardwareConcurrency > 0 && navigator.hardwareConcurrency <= 4) ||
    Boolean((navigator as Navigator & { connection?: { saveData?: boolean } }).connection?.saveData)
  const particleCount = mobileOrSlow ? 85 : 190
  const frameInterval = 1000 / (mobileOrSlow ? 30 : 45)
  const motes: Mote[] = Array.from({ length: particleCount }, () => ({
    x: random(), y: random(), vx: 0, vy: 0,
    depth: .4 + random() * .95,
    radius: .55 + random() * 1.05,
    opacity: .28 + random() * .56,
    phase: random() * Math.PI * 2,
  }))
  const wake: WakePoint[] = []

  function resize() {
    const bounds = host.getBoundingClientRect()
    const nextWidth = Math.max(1, bounds.width)
    const nextHeight = Math.max(1, bounds.height)
    if (!initialized) {
      motes.forEach(p => { p.x *= nextWidth; p.y *= nextHeight })
      initialized = true
    } else {
      motes.forEach(p => { p.x *= nextWidth / width; p.y *= nextHeight / height })
    }
    width = nextWidth
    height = nextHeight
    // A small Canvas2D effect needs neither 4K rendering nor native Retina DPI.
    const ratio = Math.min(devicePixelRatio || 1, 1, Math.sqrt(950000 / (width * height)))
    canvas.width = Math.max(1, Math.round(width * ratio))
    canvas.height = Math.max(1, Math.round(height * ratio))
    ctx.setTransform(ratio, 0, 0, ratio, 0, 0)
  }

  function onPointer(event: PointerEvent) {
    if (!active || event.pointerType === 'touch') return
    const bounds = host.getBoundingClientRect()
    const x = event.clientX - bounds.left
    const y = event.clientY - bounds.top
    if (x < 0 || y < 0 || x > bounds.width || y > bounds.height) return
    const now = performance.now()
    if (!pointerInside) {
      pointerX = x
      pointerY = y
      targetX = x
      targetY = y
      pointerVx = 0
      pointerVy = 0
      pointerInside = true
      lastMove = now
      return
    }
    const dt = clamp((now - lastMove) / 1000, 1 / 90, .1)
    const dx = x - targetX
    const dy = y - targetY
    targetX = x
    targetY = y
    lastMove = now
    if (dx * dx + dy * dy < 2.25) return
    pointerVx = clamp(dx / dt, -850, 850)
    pointerVy = clamp(dy / dt, -850, 850)
    // Sparse wake points, with distance-based spacing to avoid an enormous trail.
    if (wake.length === 0 || Math.hypot(x - wake[wake.length - 1].x, y - wake[wake.length - 1].y) >= 14) {
      wake.push({ x, y, age: 0 })
      if (wake.length > 10) wake.shift()
    }
  }

  function onLeave() {
    pointerInside = false
    pointerVx = 0
    pointerVy = 0
  }

  function paint(now: number) {
    if (!active || destroyed) { raf = 0; return }
    raf = requestAnimationFrame(paint)
    if (now - lastPaint < frameInterval) return
    const dt = clamp(lastPaint ? (now - lastPaint) / 1000 : 1 / 45, .004, .05)
    lastPaint = now
    clock += dt
    // The wake fades when movement stops; particles continue floating quietly.
    const moving = pointerInside && now - lastMove < 170
    const velocity = Math.hypot(pointerVx, pointerVy)
    const force = moving ? clamp(velocity / 520, .18, 1) : 0
    if (pointerInside) {
      const smoothing = 1 - Math.exp(-dt * 19)
      pointerX += (targetX - pointerX) * smoothing
      pointerY += (targetY - pointerY) * smoothing
    }

    ctx.clearRect(0, 0, width, height)
    ctx.globalCompositeOperation = 'screen'

    // One local glow, instead of a full-screen gradient or a rendered vortex.
    if (force > .04) {
      const radius = 92 + 38 * force
      const glow = ctx.createRadialGradient(pointerX, pointerY, 0, pointerX, pointerY, radius)
      glow.addColorStop(0, `rgba(116,219,255,${(.12 * force).toFixed(3)})`)
      glow.addColorStop(.45, `rgba(104,171,253,${(.04 * force).toFixed(3)})`)
      glow.addColorStop(1, 'rgba(104,171,253,0)')
      ctx.fillStyle = glow
      ctx.fillRect(pointerX - radius, pointerY - radius, radius * 2, radius * 2)
    }

    // Short, fading water-like trail. No circular spiral / rotating ribbons.
    for (let i = wake.length - 1; i >= 0; i--) {
      wake[i].age += dt
      if (wake[i].age > .57) wake.splice(i, 1)
    }
    if (wake.length >= 2) {
      ctx.beginPath()
      ctx.moveTo(wake[0].x, wake[0].y)
      for (let i = 1; i < wake.length - 1; i++) {
        const current = wake[i]
        const next = wake[i + 1]
        ctx.quadraticCurveTo(current.x, current.y, (current.x + next.x) * .5, (current.y + next.y) * .5)
      }
      const end = wake[wake.length - 1]
      ctx.lineTo(end.x, end.y)
      ctx.lineCap = 'round'
      ctx.lineJoin = 'round'
      ctx.lineWidth = 2
      ctx.strokeStyle = `rgba(156,224,255,${(.2 * (1 - wake[0].age / .57)).toFixed(3)})`
      ctx.stroke()
      ctx.lineWidth = 7
      ctx.strokeStyle = `rgba(87,189,244,${(.035 * (1 - wake[0].age / .57)).toFixed(3)})`
      ctx.stroke()
    }

    // Three apparent depth bands. Push motes aside as the cursor passes, then
    // let them drift back to their original relaxed motion. Never orbit the pointer.
    const influenceRadius = 155
    const friction = Math.exp(-dt * 6.5)
    for (const p of motes) {
      if (force > 0) {
        const dx = p.x - pointerX
        const dy = p.y - pointerY
        const dist = Math.hypot(dx, dy)
        if (dist < influenceRadius && dist > .001) {
          const near = (1 - dist / influenceRadius) ** 2
          const impulse = force * near * 265 * dt * p.depth
          p.vx += (dx / dist) * impulse + pointerVx * near * dt * .028
          p.vy += (dy / dist) * impulse + pointerVy * near * dt * .028
        }
      }
      p.vx *= friction
      p.vy *= friction
      p.x += (p.vx + Math.cos(clock * .24 + p.phase) * (3.5 * p.depth)) * dt
      p.y += (p.vy - (2.8 + p.depth * 4)) * dt
      if (p.x < -7) p.x = width + 7
      if (p.x > width + 7) p.x = -7
      if (p.y < -7) p.y = height + 7
      if (p.y > height + 7) p.y = -7
      const twinkle = .85 + .15 * Math.sin(clock * (.55 + p.depth * .3) + p.phase)
      ctx.fillStyle = `rgba(237,247,255,${(p.opacity * twinkle).toFixed(2)})`
      ctx.beginPath()
      ctx.arc(p.x, p.y, p.radius * p.depth, 0, Math.PI * 2)
      ctx.fill()
    }
    ctx.globalCompositeOperation = 'source-over'
    canvas.dataset.running = 'true'
  }

  const observer = new ResizeObserver(resize)
  observer.observe(host)
  resize()
  host.addEventListener('pointermove', onPointer, { passive: true })
  host.addEventListener('pointerleave', onLeave)
  canvas.dataset.particles = String(particleCount)
  canvas.dataset.mode = 'soft-wake'
  return {
    setActive(value) {
      if (destroyed || active === value) return
      active = value
      cancelAnimationFrame(raf)
      raf = 0
      lastPaint = 0
      canvas.dataset.running = String(value)
      if (value) raf = requestAnimationFrame(paint)
      else { onLeave(); wake.length = 0; ctx.clearRect(0, 0, width, height) }
    },
    destroy() {
      destroyed = true
      cancelAnimationFrame(raf)
      observer.disconnect()
      host.removeEventListener('pointermove', onPointer)
      host.removeEventListener('pointerleave', onLeave)
      wake.length = 0
      canvas.dataset.running = 'false'
    },
  }
}
