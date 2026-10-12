/**
 * LIBRA — interactive pollen / floating light.
 * Independently implemented for LIBRA after studying the user-supplied Sylva
 * reference: depth-layered ambient motes + distance-based pointer emission.
 * This does NOT ship Sylva code, assets, landscape or its animation engine.
 * One transparent WebGL1 canvas; only static VBO + small cursor-emitter VBO.
 */

import { createLiquidOverlayRenderer, type LiquidOverlayRenderer } from './liquidOverlayRenderer'

export interface SylvaPollenRenderer {
  setActive(active: boolean): void
  destroy(): void
}

const VERTEX = `
precision highp float;
attribute vec4 a_origin;  // xy = normalized canvas position; z = depth (0..1), w = phase
attribute vec4 a_motion;  // xy = velocity for spray; z = drift for ambient, w = size
attribute vec4 a_meta;    // x = mode (0 ambient, 1 spray), y = opacity, z = spawn time, w = lifetime
uniform float u_time;
uniform float u_pixelRatio;
uniform vec2 u_pointer;
uniform float u_pointerPower;
varying float v_opacity;
varying float v_cold;

void main() {
  float depth = a_origin.z;
  float phase = a_origin.w;
  vec2 p;
  float size;
  if (a_meta.x < 0.5) {
    // Airborne motes drift at different speeds and in distinct depth planes.
    float rate = 0.0025 + 0.0045 * a_motion.z;
    p = vec2(
      fract(a_origin.x + 0.013 * sin(u_time * (0.31 + depth*0.42) + phase) * (0.25 + depth)),
      fract(a_origin.y - u_time * rate + 0.008 * cos(u_time * 0.18 + phase * 1.3))
    );
    // A restrained response to nearby pointer movement, not a vortex.
    vec2 d = p - u_pointer;
    float nearMouse = exp(-dot(d, d) * 46.0) * u_pointerPower;
    p += normalize(d + vec2(0.0001)) * nearMouse * 0.016;
    v_opacity = a_meta.y * (0.75 + 0.25 * sin(u_time * (0.9 + depth) + phase))
              * (1.0 + nearMouse * 0.30);
    size = a_motion.w * (0.75 + depth * 0.85);
  } else {
    float age = u_time - a_meta.z;
    float life = max(0.5, a_meta.w);
    float progress = clamp(age / life, 0.0, 1.0);
    // Pointer-sprayed pollen: drift outward gently, rise and evaporate.
    p = a_origin.xy + a_motion.xy * age * (1.0 - 0.23 * progress);
    p += vec2(0.005 * sin(phase + age * 5.0) * progress, -0.018 * age);
    float fadeIn = smoothstep(0.0, 0.09, progress);
    float fadeOut = 1.0 - smoothstep(0.32, 1.0, progress);
    v_opacity = (age < 0.0 || age > life) ? 0.0 : a_meta.y * fadeIn * fadeOut;
    size = a_motion.w * (1.0 - 0.32 * progress);
  }
  v_cold = depth;
  gl_PointSize = clamp(size * u_pixelRatio, 0.0, 14.0);
  gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
}
`

const FRAGMENT = `
precision mediump float;
varying float v_opacity;
varying float v_cold;
void main() {
  vec2 uv = (gl_PointCoord - 0.5) * 2.0;
  float r = length(uv);
  float halo = 1.0 - smoothstep(0.24, 1.0, r);
  float core = 1.0 - smoothstep(0.0, 0.40, r);
  float alpha = clamp((0.32 * halo + 0.68 * core) * v_opacity, 0.0, 1.0);
  vec3 ice = mix(vec3(0.55, 0.82, 1.0), vec3(1.0), clamp(v_cold * 0.65 + core * 0.55, 0.0, 1.0));
  gl_FragColor = vec4(ice, alpha);
}
`

const PARTICLE_FLOATS = 12
const EMITTER_CAPACITY = 250
const TWO_PI = Math.PI * 2
const clamp = (v: number, lo: number, hi: number) => Math.max(lo, Math.min(hi, v))

function createWebGLPollen(canvas: HTMLCanvasElement, host: HTMLElement): SylvaPollenRenderer | null {
  const context = canvas.getContext('webgl', {
    alpha: true, antialias: false, depth: false, stencil: false,
    preserveDrawingBuffer: false, premultipliedAlpha: true, powerPreference: 'low-power',
  })
  if (!context) return null
  const gl: WebGLRenderingContext = context

  const resources: { shaders: WebGLShader[]; buffers: WebGLBuffer[]; programs: WebGLProgram[] } = {
    shaders: [], buffers: [], programs: [],
  }
  const fail = (message: string): never => { throw new Error(`LIBRA pollen: ${message}`) }
  const makeShader = (type: number, source: string) => {
    const shader = gl.createShader(type) || fail('cannot create shader')
    resources.shaders.push(shader)
    gl.shaderSource(shader, source)
    gl.compileShader(shader)
    if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) fail(gl.getShaderInfoLog(shader) || 'shader compilation')
    return shader
  }
  const destroyResources = () => {
    resources.buffers.forEach(b => gl.deleteBuffer(b))
    resources.programs.forEach(p => gl.deleteProgram(p))
    resources.shaders.forEach(s => gl.deleteShader(s))
  }
  try {
    const program = gl.createProgram() || fail('cannot create program')
    resources.programs.push(program)
    gl.attachShader(program, makeShader(gl.VERTEX_SHADER, VERTEX))
    gl.attachShader(program, makeShader(gl.FRAGMENT_SHADER, FRAGMENT))
    gl.linkProgram(program)
    if (!gl.getProgramParameter(program, gl.LINK_STATUS)) fail(gl.getProgramInfoLog(program) || 'program link')

    const aOrigin = gl.getAttribLocation(program, 'a_origin')
    const aMotion = gl.getAttribLocation(program, 'a_motion')
    const aMeta = gl.getAttribLocation(program, 'a_meta')
    const uTime = gl.getUniformLocation(program, 'u_time')
    const uRatio = gl.getUniformLocation(program, 'u_pixelRatio')
    const uPointer = gl.getUniformLocation(program, 'u_pointer')
    const uPower = gl.getUniformLocation(program, 'u_pointerPower')
    if ([aOrigin, aMotion, aMeta].some(value => value < 0)) fail('missing vertex attributes')

    const reducedHardware = window.matchMedia('(pointer: coarse)').matches
      || Boolean((navigator as Navigator & { connection?: { saveData?: boolean } }).connection?.saveData)
      || (navigator.hardwareConcurrency > 0 && navigator.hardwareConcurrency <= 4)
    const ambientCount = reducedHardware ? 460 : 1280
    const intervalMs = 1000 / (reducedHardware ? 30 : 50)
    const ambient = new Float32Array(ambientCount * PARTICLE_FLOATS)
    const spray = new Float32Array(EMITTER_CAPACITY * PARTICLE_FLOATS)
    let seed = 0x4c494252
    const random = () => {
      seed ^= seed << 13; seed ^= seed >>> 17; seed ^= seed << 5
      return (seed >>> 0) / 4294967296
    }
    // Each point receives immutable start data; the GPU handles its motion.
    for (let i = 0; i < ambientCount; i++) {
      const k = i * PARTICLE_FLOATS
      const depth = Math.pow(random(), 1.65)
      ambient[k] = random()           // starting X
      ambient[k + 1] = random()       // starting Y
      ambient[k + 2] = depth
      ambient[k + 3] = random() * TWO_PI
      ambient[k + 6] = random()       // rising speed
      ambient[k + 7] = 1.75 + 2.50 * Math.pow(random(), 1.6)  // visible, depth-scaled size
      ambient[k + 8] = 0             // ambient
      ambient[k + 9] = 0.38 + random() * 0.44
    }
    for (let i = 0; i < EMITTER_CAPACITY; i++) spray[i * PARTICLE_FLOATS + 10] = -1000

    const ambientBuffer = gl.createBuffer() || fail('cannot allocate ambient buffer')
    const sprayBuffer = gl.createBuffer() || fail('cannot allocate spray buffer')
    resources.buffers.push(ambientBuffer, sprayBuffer)
    gl.bindBuffer(gl.ARRAY_BUFFER, ambientBuffer)
    gl.bufferData(gl.ARRAY_BUFFER, ambient, gl.STATIC_DRAW)
    gl.bindBuffer(gl.ARRAY_BUFFER, sprayBuffer)
    gl.bufferData(gl.ARRAY_BUFFER, spray, gl.DYNAMIC_DRAW)
    const stride = PARTICLE_FLOATS * 4
    const bindAttributes = (buffer: WebGLBuffer) => {
      gl.bindBuffer(gl.ARRAY_BUFFER, buffer)
      gl.enableVertexAttribArray(aOrigin)
      gl.enableVertexAttribArray(aMotion)
      gl.enableVertexAttribArray(aMeta)
      gl.vertexAttribPointer(aOrigin, 4, gl.FLOAT, false, stride, 0)
      gl.vertexAttribPointer(aMotion, 4, gl.FLOAT, false, stride, 16)
      gl.vertexAttribPointer(aMeta, 4, gl.FLOAT, false, stride, 32)
    }

    let w = 1, h = 1, ratio = 1, active = false, dead = false, lost = false
    let time = 0, lastTick = 0, lastDraw = 0, raf = 0
    let prevX = -1, prevY = -1, sprayHead = 0, sprayDirty = false
    let pointerX = -10, pointerY = -10, pointerPower = 0
    let mouseLast = 0, emissionCredit = 0

    const resize = () => {
      const rect = host.getBoundingClientRect()
      w = Math.max(1, rect.width); h = Math.max(1, rect.height)
      // Cap overdraw: a hero effect should not rasterize 4K on a laptop.
      const desired = Math.min(window.devicePixelRatio || 1, reducedHardware ? 1 : 1.4)
      ratio = Math.min(desired, Math.sqrt((reducedHardware ? 550_000 : 1_350_000) / (w * h)))
      canvas.width = Math.max(1, Math.round(w * ratio))
      canvas.height = Math.max(1, Math.round(h * ratio))
      if (!lost) gl.viewport(0, 0, canvas.width, canvas.height)
    }

    const emit = (x: number, y: number, vx: number, vy: number, count: number) => {
      for (let i = 0; i < count; i++) {
        const k = sprayHead * PARTICLE_FLOATS
        sprayHead = (sprayHead + 1) % EMITTER_CAPACITY
        spray[k] = clamp(x + (random() - 0.5) * 0.011, 0, 1)
        spray[k + 1] = clamp(y + (random() - 0.5) * 0.015, 0, 1)
        spray[k + 2] = random()
        spray[k + 3] = random() * TWO_PI
        spray[k + 4] = (random() - 0.5) * 0.052 + vx * 0.09
        spray[k + 5] = (random() - 0.5) * 0.065 + vy * 0.065
        spray[k + 6] = 0
        spray[k + 7] = 3.2 + random() * 3.0
        spray[k + 8] = 1
        spray[k + 9] = 0.68 + random() * 0.29
        spray[k + 10] = time
        spray[k + 11] = 0.85 + random() * 0.62
      }
      sprayDirty = true
    }
    const move = (event: PointerEvent) => {
      if (!active || event.pointerType === 'touch') return
      const rect = host.getBoundingClientRect()
      const x = (event.clientX - rect.left) / Math.max(rect.width, 1)
      const y = 1 - (event.clientY - rect.top) / Math.max(rect.height, 1)
      if (x < 0 || x > 1 || y < 0 || y > 1) return
      pointerX = x; pointerY = y
      const now = performance.now()
      if (prevX >= 0) {
        const dx = x - prevX, dy = y - prevY
        const distPx = Math.hypot(dx * w, dy * h)
        // Distance-spaced emission gives a spray, not a string of blobs.
        emissionCredit += distPx
        const count = Math.min(12, Math.floor(emissionCredit / (reducedHardware ? 14 : 10)))
        if (count) {
          emissionCredit -= count * (reducedHardware ? 14 : 10)
          for (let i = 0; i < count; i++) emit(prevX + dx * ((i + 1) / count), prevY + dy * ((i + 1) / count), dx, dy, 1)
        }
        pointerPower = Math.min(1, pointerPower + distPx / 180)
      }
      prevX = x; prevY = y; mouseLast = now
    }
    const leave = () => { prevX = prevY = -1; emissionCredit = 0; pointerPower = 0; pointerX = pointerY = -10 }
    const pointerDown = (event: PointerEvent) => {
      if (!active || event.pointerType === 'touch') return
      const rect = host.getBoundingClientRect()
      emit((event.clientX - rect.left) / Math.max(1, rect.width), 1 - (event.clientY - rect.top) / Math.max(1, rect.height), 0, 0, reducedHardware ? 10 : 18)
    }
    const lostHandler = (event: Event) => { event.preventDefault(); lost = true; cancelAnimationFrame(raf); raf = 0; canvas.dataset.running = 'false' }
    const restoredHandler = () => { lost = false; lastTick = lastDraw = 0; resize(); if (active && !raf) raf = requestAnimationFrame(tick) }
    canvas.addEventListener('webglcontextlost', lostHandler)
    canvas.addEventListener('webglcontextrestored', restoredHandler)
    host.addEventListener('pointermove', move, { passive: true })
    host.addEventListener('pointerleave', leave)
    host.addEventListener('pointerdown', pointerDown, { passive: true })
    const observer = new ResizeObserver(resize)
    observer.observe(host)
    resize()
    canvas.dataset.mode = 'pollen-gpu'
    canvas.dataset.version = 'v6.2-visibility'
    canvas.dataset.particles = String(ambientCount)

    function tick(now: number) {
      raf = 0
      if (!active || dead || lost) return
      raf = requestAnimationFrame(tick)
      // Accumulate simulation time at normal display cadence. Sleep offscreen.
      const dt = lastTick ? clamp((now - lastTick) / 1000, 0, 0.055) : 0.016
      lastTick = now
      time += dt
      pointerPower *= Math.exp(-dt * 3.8)
      if (now - mouseLast > 150) pointerPower *= Math.exp(-dt * 6)
      if (now - lastDraw < intervalMs) return
      lastDraw = now

      gl.viewport(0, 0, canvas.width, canvas.height)
      gl.clearColor(0, 0, 0, 0)
      gl.clear(gl.COLOR_BUFFER_BIT)
      gl.useProgram(program)
      gl.enable(gl.BLEND)
      // Composite a transparent canvas correctly: rgb is premultiplied by the
      // blend equation, while alpha accumulates once (not alpha squared).
      gl.blendFuncSeparate(gl.SRC_ALPHA, gl.ONE, gl.ONE, gl.ONE)
      gl.uniform1f(uTime, time)
      gl.uniform1f(uRatio, ratio)
      gl.uniform2f(uPointer, pointerX, pointerY)
      gl.uniform1f(uPower, pointerPower)
      bindAttributes(ambientBuffer)
      gl.drawArrays(gl.POINTS, 0, ambientCount)
      if (sprayDirty) {
        gl.bindBuffer(gl.ARRAY_BUFFER, sprayBuffer)
        gl.bufferSubData(gl.ARRAY_BUFFER, 0, spray)
        sprayDirty = false
      }
      bindAttributes(sprayBuffer)
      gl.drawArrays(gl.POINTS, 0, EMITTER_CAPACITY)
      canvas.dataset.running = 'true'
    }
    return {
      setActive(value) {
        if (dead || active === value) return
        active = value
        if (value) { lastTick = lastDraw = 0; if (!raf && !lost) raf = requestAnimationFrame(tick) }
        else { cancelAnimationFrame(raf); raf = 0; leave(); canvas.dataset.running = 'false' }
      },
      destroy() {
        dead = true; cancelAnimationFrame(raf)
        host.removeEventListener('pointermove', move)
        host.removeEventListener('pointerleave', leave)
        host.removeEventListener('pointerdown', pointerDown)
        canvas.removeEventListener('webglcontextlost', lostHandler)
        canvas.removeEventListener('webglcontextrestored', restoredHandler)
        observer.disconnect()
        destroyResources()
        canvas.dataset.running = 'false'
      },
    }
  } catch (error) {
    console.warn('LIBRA: WebGL pollen unavailable; using a 2D fallback', error)
    destroyResources()
    return null
  }
}

/** One canvas only. WebGL unsupported or shader failure => 2D fallback.
 * A failed WebGL canvas cannot acquire a 2D context, so replace its node.
 */
export function createSylvaPollenRenderer(canvas: HTMLCanvasElement, host: HTMLElement): SylvaPollenRenderer | null {
  const gpu = createWebGLPollen(canvas, host)
  if (gpu) return gpu
  const alternate = document.createElement('canvas')
  alternate.className = canvas.className
  alternate.setAttribute('aria-hidden', 'true')
  alternate.dataset.renderer = 'canvas2d-fallback'
  alternate.style.position = 'absolute'
  alternate.style.inset = '0'
  alternate.style.width = '100%'
  alternate.style.height = '100%'
  alternate.style.pointerEvents = 'none'
  alternate.style.opacity = '0.8'
  canvas.style.display = 'none'
  canvas.insertAdjacentElement('afterend', alternate)
  const fallback: LiquidOverlayRenderer | null = createLiquidOverlayRenderer(alternate, host)
  if (!fallback) {
    alternate.remove(); canvas.style.display = ''
    return null
  }
  return {
    setActive(value) { fallback.setActive(value) },
    destroy() { fallback.destroy(); alternate.remove(); canvas.style.display = '' },
  }
}
