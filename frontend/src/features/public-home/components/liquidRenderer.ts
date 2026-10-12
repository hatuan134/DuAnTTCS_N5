/**
 * LIBRA V4: ambient liquid-light WebGL shader + white micro-particle vortex.
 * One WebGL1 context and two programs. No animation framework or third-party CDN.
 * Canvas has transparent pixels and does not capture clicks or focus.
 */
const fullscreenVertex = `
attribute vec2 a_position;
varying vec2 v_uv;
void main() { v_uv = a_position * .5 + .5; gl_Position = vec4(a_position, 0., 1.); }
`

const fluidFragment = `
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
varying vec2 v_uv;
uniform vec2 u_resolution;
uniform vec3 u_pointer;
uniform float u_time;
float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float noise(vec2 p) {
  vec2 i = floor(p), f = fract(p);
  f = f*f*(3.-2.*f);
  return mix(mix(hash(i), hash(i + vec2(1.,0.)), f.x),
             mix(hash(i + vec2(0.,1.)), hash(i + vec2(1.,1.)), f.x), f.y);
}
float fbm(vec2 p) {
  float v=0., a=.5;
  for (int i=0; i<3; i++) { v += a*noise(p); p=mat2(.80,-.60,.60,.80)*p*2.04+2.31; a*=.5; }
  return v;
}
void main() {
  float aspect = u_resolution.x / max(1.,u_resolution.y);
  vec2 uv=v_uv;
  vec2 xy=vec2(uv.x*aspect, uv.y);
  vec2 mouse=vec2(u_pointer.x*aspect,u_pointer.y);
  vec2 d=xy-mouse;
  float r=length(d);
  float attract=u_pointer.z*exp(-r*3.1);
  // Rotate the domain around the mouse. The flowing light forms a spiral wake.
  float angle=attract*2.65;
  mat2 spin=mat2(cos(angle),-sin(angle),sin(angle),cos(angle));
  vec2 coord=mouse+spin*d;
  float t=u_time*.18;
  vec2 p=coord*2.4;
  vec2 drift=vec2(fbm(p*.72+vec2(t,-t*.41)), fbm(p*.75+vec2(4.3,-2.6)-t*.68));
  vec2 warped=p+(drift-.47)*2.9;
  float flow=fbm(warped+vec2(-t*.52,t*.38));
  float ribbon1=pow(1.-abs(sin((warped.y+flow*.65-t*.8)*4.2)), 5.);
  float ribbon2=pow(1.-abs(sin((warped.x*.65-warped.y*.38+flow*.88+t*.62)*5.1)), 7.);
  float surface=(.11+.48*ribbon1+.37*ribbon2)*(.45+.72*flow);
  vec3 blue=vec3(.09,.49,.98);
  vec3 cyan=vec3(.17,.87,1.00);
  vec3 violet=vec3(.57,.36,.99);
  vec3 ink=mix(blue,cyan,clamp(drift.x*.95,0.,1.));
  ink=mix(ink,violet,smoothstep(.35,.82,drift.y)*.56);
  float whirl=attract*(.18+.34*pow(1.-abs(sin(r*27.-u_time*4.)),6.));
  vec3 glow=ink*(surface*.59+whirl*.47);
  glow+=vec3(.42,.84,1.)*attract*exp(-r*4.5)*.18;
  float alpha=clamp(surface*.22+whirl*.14,0.,.30);
  gl_FragColor=vec4(glow,alpha);
}
`

const particleVertex = `
attribute vec4 a_particle;
uniform vec2 u_resolution;
uniform float u_time;
varying float v_alpha;
varying float v_tint;
void main() {
  gl_Position=vec4(a_particle.xy*2.-1.,0.,1.);
  float scale=clamp(u_resolution.y/750.,.75,2.);
  gl_PointSize=clamp((1.2+a_particle.z*5.)*scale,1.,13.);
  v_alpha=a_particle.w*(.78+.22*sin(u_time*1.8+a_particle.z*17.));
  v_tint=a_particle.z;
}
`
const particleFragment = `
precision mediump float;
varying float v_alpha;
varying float v_tint;
void main() {
  float d=length(gl_PointCoord-.5);
  float core=1.-smoothstep(.035,.19,d);
  float halo=1.-smoothstep(.11,.5,d);
  vec3 color=mix(vec3(.64,.87,1.),vec3(1.,1.,1.),.45+.55*core);
  gl_FragColor=vec4(color, (.26*halo+.74*core)*v_alpha);
}
`

export interface LiquidRenderer {
  setActive: (active: boolean) => void
  destroy: () => void
}

type Program = {
  program: WebGLProgram
  time: WebGLUniformLocation | null
  pointer: WebGLUniformLocation | null
  resolution: WebGLUniformLocation | null
}

export function createLiquidRenderer(canvas: HTMLCanvasElement, host: HTMLElement): LiquidRenderer | null {
  const gl = canvas.getContext('webgl', {
    alpha: true, premultipliedAlpha: true, antialias: false,
    depth: false, stencil: false, powerPreference: 'low-power', preserveDrawingBuffer: false,
  })
  if (!gl) return createCanvasFallback(canvas, host)

  const shaders: WebGLShader[] = []
  const programs: WebGLProgram[] = []
  const buffers: WebGLBuffer[] = []
  let destroyed = false, lost = false, active = false, frame = 0, last = 0, elapsed = 0
  let quality = 1, slowFrames = 0
  let pointerX = .5, pointerY = .5, targetX = .5, targetY = .5
  let pointerPower = 0, targetPower = 0
  let cssWidth = 1, cssHeight = 1
  const coarse = matchMedia('(pointer: coarse)')
  const lowPower = coarse.matches || (navigator.hardwareConcurrency > 0 && navigator.hardwareConcurrency <= 4)
    || Boolean((navigator as Navigator & { connection?: { saveData?: boolean } }).connection?.saveData)
  // 480 living particles on desktops; mobile/low-end GPUs use an intentionally smaller budget.
  const count = lowPower ? 110 : 480
  const particleData = new Float32Array(count * 8) // live particle + subtle tail per seed
  const pos = new Float32Array(count * 2)
  const vel = new Float32Array(count * 2)
  const size = new Float32Array(count)
  const phase = new Float32Array(count)
  let randomState = 0x4c494252
  const random = () => {
    randomState ^= randomState << 13
    randomState ^= randomState >>> 17
    randomState ^= randomState << 5
    return (randomState >>> 0) / 4294967296
  }
  for (let i = 0; i < count; i++) {
    pos[2*i] = random()
    pos[2*i+1] = random()
    vel[2*i] = (random()-.5)*.018
    vel[2*i+1] = (random()-.5)*.018
    size[i] = .18+random()*.82
    phase[i] = random()*6.28
  }

  const free = () => {
    buffers.forEach(buffer => gl.deleteBuffer(buffer))
    programs.forEach(program => gl.deleteProgram(program))
    shaders.forEach(shader => gl.deleteShader(shader))
  }
  const compile = (src: string, kind: number) => {
    const shader = gl.createShader(kind)
    if (!shader) throw Error('Shader allocation failed')
    shaders.push(shader)
    gl.shaderSource(shader, src)
    gl.compileShader(shader)
    if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) throw Error(gl.getShaderInfoLog(shader) || 'Shader compilation failed')
    return shader
  }
  const createProgram = (vs: string, fs: string): Program => {
    const program = gl.createProgram()
    if (!program) throw Error('Program allocation failed')
    programs.push(program)
    gl.attachShader(program, compile(vs, gl.VERTEX_SHADER))
    gl.attachShader(program, compile(fs, gl.FRAGMENT_SHADER))
    gl.linkProgram(program)
    if (!gl.getProgramParameter(program, gl.LINK_STATUS)) throw Error(gl.getProgramInfoLog(program) || 'Shader link failed')
    return {
      program,
      time: gl.getUniformLocation(program,'u_time'),
      pointer: gl.getUniformLocation(program,'u_pointer'),
      resolution: gl.getUniformLocation(program,'u_resolution'),
    }
  }

  try {
    const liquid = createProgram(fullscreenVertex, fluidFragment)
    const particles = createProgram(particleVertex, particleFragment)
    const quad = gl.createBuffer(), points = gl.createBuffer()
    if (!quad || !points) throw Error('WebGL buffers unavailable')
    buffers.push(quad, points)
    gl.bindBuffer(gl.ARRAY_BUFFER, quad)
    gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1,-1, 1,-1, -1,1, -1,1, 1,-1, 1,1]), gl.STATIC_DRAW)
    gl.bindBuffer(gl.ARRAY_BUFFER, points)
    gl.bufferData(gl.ARRAY_BUFFER, particleData.byteLength, gl.DYNAMIC_DRAW)
    const quadLoc = gl.getAttribLocation(liquid.program,'a_position')
    const pointLoc = gl.getAttribLocation(particles.program,'a_particle')

    const resize = () => {
      if (destroyed || lost) return
      const rect = host.getBoundingClientRect()
      cssWidth = Math.max(1,rect.width)
      cssHeight = Math.max(1,rect.height)
      const dpr = Math.min(devicePixelRatio || 1, lowPower ? 1 : 1.5)*quality
      const pixelBudget = lowPower ? 390_000 : 1_000_000
      const scale = Math.min(dpr, Math.sqrt(pixelBudget/(cssWidth*cssHeight)))
      canvas.width = Math.max(1,Math.floor(cssWidth*scale))
      canvas.height = Math.max(1,Math.floor(cssHeight*scale))
      gl.viewport(0,0,canvas.width,canvas.height)
    }

    const use = (p: Program) => {
      gl.useProgram(p.program)
      gl.uniform1f(p.time,elapsed)
      gl.uniform3f(p.pointer,pointerX,pointerY,pointerPower)
      gl.uniform2f(p.resolution,canvas.width,canvas.height)
    }

    const updateParticles = (dt: number) => {
      const aspect = cssWidth/cssHeight
      const decay = Math.exp(-dt*(pointerPower>.1 ? 2.7 : 1.7))
      for (let i=0;i<count;i++) {
        const ix=2*i, offset=i*8
        let x=pos[ix], y=pos[ix+1]
        let vx=vel[ix], vy=vel[ix+1]
        const dx=(pointerX-x)*aspect, dy=pointerY-y
        const d2=dx*dx+dy*dy
        // Soft radius ~35% of viewport height, with fast rotation and center seeking.
        const influence=pointerPower*Math.exp(-d2*7.3)
        const orbit=52*influence
        const pull=19*influence*(Math.sqrt(d2)-.055)
        const swirlX=(-dy*orbit+dx*pull)/Math.max(.6,aspect)
        const swirlY=(dx*orbit+dy*pull)
        vx+=dt*(swirlX+Math.sin(elapsed*.72+phase[i]+y*8.)*.018)
        vy+=dt*(swirlY+Math.cos(elapsed*.54+phase[i]+x*9.)*.018)
        vx*=decay; vy*=decay
        const max=.83
        const speed=Math.hypot(vx*aspect,vy)
        if (speed>max) { vx*=max/speed;vy*=max/speed }
        x+=vx*dt
        y+=vy*dt+dt*.004*(.5+size[i])
        if (x<-.025) x=1.025
        if (x>1.025) x=-.025
        if (y>1.025) y=-.025
        if (y<-.025) y=1.025
        pos[ix]=x;pos[ix+1]=y;vel[ix]=vx;vel[ix+1]=vy
        const alpha=.16+.7*size[i]
        particleData[offset]=x;particleData[offset+1]=y
        particleData[offset+2]=size[i];particleData[offset+3]=alpha
        // A short, understated streak makes rapid mouse movement feel energetic.
        particleData[offset+4]=x-vx*.012;particleData[offset+5]=y-vy*.012
        particleData[offset+6]=size[i]*.58;particleData[offset+7]=alpha*.18
      }
    }

    const draw = (now: number) => {
      if (!active || destroyed || lost) { frame=0;return }
      frame=requestAnimationFrame(draw)
      const delta=last?Math.min(now-last,60):16.7
      if (lowPower && delta<30) return
      last=now
      if (!lowPower && delta>35) slowFrames++
      else slowFrames=Math.max(0,slowFrames-2)
      if (slowFrames>100 && quality>.62) {
        quality=.62;slowFrames=0;canvas.dataset.quality='reduced';resize()
      }
      const dt=delta/1000
      elapsed+=dt
      const ease=1-Math.exp(-delta/85)
      pointerX+=(targetX-pointerX)*ease
      pointerY+=(targetY-pointerY)*ease
      pointerPower+=(targetPower-pointerPower)*(1-Math.exp(-delta/240))
      gl.clearColor(0,0,0,0)
      gl.clear(gl.COLOR_BUFFER_BIT)
      gl.enable(gl.BLEND)
      gl.blendFunc(gl.SRC_ALPHA,gl.ONE_MINUS_SRC_ALPHA)
      use(liquid)
      gl.bindBuffer(gl.ARRAY_BUFFER,quad)
      gl.enableVertexAttribArray(quadLoc)
      gl.vertexAttribPointer(quadLoc,2,gl.FLOAT,false,0,0)
      gl.drawArrays(gl.TRIANGLES,0,6)
      gl.disableVertexAttribArray(quadLoc)

      updateParticles(dt)
      gl.blendFunc(gl.SRC_ALPHA,gl.ONE)
      use(particles)
      gl.bindBuffer(gl.ARRAY_BUFFER,points)
      gl.bufferSubData(gl.ARRAY_BUFFER,0,particleData)
      gl.enableVertexAttribArray(pointLoc)
      gl.vertexAttribPointer(pointLoc,4,gl.FLOAT,false,0,0)
      gl.drawArrays(gl.POINTS,0,count*2)
      gl.disableVertexAttribArray(pointLoc)
      gl.disable(gl.BLEND)
      canvas.dataset.renderer='webgl'
    }

    const pointerMove = (event: PointerEvent) => {
      if (!active || coarse.matches || event.pointerType==='touch') return
      const rect=host.getBoundingClientRect()
      if (!rect.width || !rect.height) return
      targetX=Math.max(0,Math.min(1,(event.clientX-rect.left)/rect.width))
      targetY=1-Math.max(0,Math.min(1,(event.clientY-rect.top)/rect.height))
      targetPower=1
    }
    const pointerLeave = () => { targetPower=0 }
    const lostContext = (event: Event) => {
      event.preventDefault()
      lost=true;cancelAnimationFrame(frame);frame=0
      canvas.dataset.renderer='fallback';canvas.dataset.running='false'
    }
    const resizeObserver=new ResizeObserver(resize)
    resizeObserver.observe(host)
    resize()
    host.addEventListener('pointermove',pointerMove,{passive:true})
    host.addEventListener('pointerleave',pointerLeave)
    canvas.addEventListener('webglcontextlost',lostContext)

    return {
      setActive(value) {
        active=value
        canvas.dataset.running=String(value && !lost)
        cancelAnimationFrame(frame);frame=0;last=0
        if (active && !lost && !destroyed) frame=requestAnimationFrame(draw)
      },
      destroy() {
        destroyed=true;cancelAnimationFrame(frame)
        resizeObserver.disconnect()
        host.removeEventListener('pointermove',pointerMove)
        host.removeEventListener('pointerleave',pointerLeave)
        canvas.removeEventListener('webglcontextlost',lostContext)
        free()
        // Context teardown is optional. Avoid forcibly losing contexts shared by browsers.
      },
    }
  } catch {
    free()
    return null
  }
}


/** Safe visual fallback on restricted devices without WebGL. */
function createCanvasFallback(canvas: HTMLCanvasElement, host: HTMLElement): LiquidRenderer | null {
  const ctx = canvas.getContext('2d', { alpha: true })
  if (!ctx) return null
  const coarse = matchMedia('(pointer: coarse)').matches
  const count = coarse ? 85 : 175
  const seeds = Array.from({ length: count }, (_, i) => ({
    x: ((i * 41 + 17) % count) / count,
    y: ((i * 73 + 31) % (count + 1)) / (count + 1),
    vx: 0, vy: 0, depth: .3 + ((i * 53) % 91) / 91,
  }))
  let active = false, disposed = false, frame = 0, last = 0, elapsed = 0
  let width = 1, height = 1, px = .5, py = .5, power = 0
  const resize = () => {
    const bounds = host.getBoundingClientRect()
    width = Math.max(1,bounds.width)
    height = Math.max(1,bounds.height)
    const scale = Math.min(devicePixelRatio || 1, 1.25, Math.sqrt(900000/(width*height)))
    canvas.width = Math.max(1, Math.floor(width*scale))
    canvas.height = Math.max(1, Math.floor(height*scale))
    ctx.setTransform(canvas.width/width,0,0,canvas.height/height,0,0)
  }
  const move = (event: PointerEvent) => {
    if (!active || coarse || event.pointerType==='touch') return
    const bounds = host.getBoundingClientRect()
    px = Math.max(0,Math.min(1,(event.clientX-bounds.left)/Math.max(1,bounds.width)))
    py = Math.max(0,Math.min(1,(event.clientY-bounds.top)/Math.max(1,bounds.height)))
    power = 1
  }
  const leave = () => { power = 0 }
  const draw = (now: number) => {
    if (!active || disposed) { frame=0; return }
    frame = requestAnimationFrame(draw)
    const dt = Math.min(last ? now-last : 16.7,50)/1000
    if (coarse && last && now-last<29) return
    last=now;elapsed+=dt
    ctx.clearRect(0,0,width,height)
    ctx.globalCompositeOperation='screen'
    // A moving liquid-light field with multiple offset, gently folding currents.
    const centerX=px*width, centerY=py*height
    const gradient=ctx.createRadialGradient(centerX,centerY,2,centerX,centerY,Math.max(170,height*.46))
    gradient.addColorStop(0,`rgba(80,197,255,${.07+power*.14})`)
    gradient.addColorStop(.4,`rgba(70,96,226,${.035+power*.065})`)
    gradient.addColorStop(1,'rgba(37,44,105,0)')
    ctx.fillStyle=gradient; ctx.fillRect(0,0,width,height)
    for (let j=0;j<5;j++) {
      ctx.beginPath()
      const baseY=height*(.21+j*.14)
      const phase=elapsed*(.25+j*.032)+j*2.3
      for (let s=0;s<=42;s++) {
        const x=s*width/42
        const dist=Math.abs(x-centerX)/Math.max(width,1)
        const whirl=power*Math.exp(-dist*dist*15)*Math.sin(phase*3+dist*13)*height*.055
        const y=baseY+Math.sin(x/width*9+phase)*height*.054 + Math.sin(x/width*18-phase*.7)*height*.013+whirl
        if (s===0) ctx.moveTo(x,y); else ctx.lineTo(x,y)
      }
      ctx.lineWidth=2.5+j*.9
      ctx.strokeStyle=j%2 ? 'rgba(98,135,255,.11)' : 'rgba(43,213,255,.14)'
      ctx.shadowBlur=29; ctx.shadowColor=j%2 ? '#7179ff' : '#5edcff'
      ctx.stroke()
    }
    ctx.shadowBlur=0
    for (const p of seeds) {
      const dx=(px-p.x)*width, dy=(py-p.y)*height
      const radius=Math.hypot(dx,dy)
      const influence=power*Math.exp(-radius*radius/Math.max(10000,height*height*.11))
      const swirl=8.5*influence
      p.vx=(p.vx + (-dy*swirl+dx*2.7*influence)*dt)*Math.exp(-dt*3.4)
      p.vy=(p.vy + (dx*swirl+dy*2.7*influence)*dt)*Math.exp(-dt*3.4)
      p.x+=(p.vx*dt)/width+.000007*Math.sin(elapsed+p.depth*13)
      p.y+=(p.vy*dt)/height+.000025*p.depth
      if(p.x<0)p.x=1;if(p.x>1)p.x=0
      if(p.y<0)p.y=1;if(p.y>1)p.y=0
      const x=p.x*width,y=p.y*height
      ctx.beginPath();ctx.arc(x,y,.5+p.depth*1.5,0,Math.PI*2)
      ctx.fillStyle=`rgba(233,246,255,${.18+.63*p.depth})`
      ctx.fill()
    }
    canvas.dataset.renderer='canvas2d'
  }
  const observer = new ResizeObserver(resize)
  observer.observe(host);resize()
  host.addEventListener('pointermove',move,{passive:true})
  host.addEventListener('pointerleave',leave)
  return {
    setActive(value) {
      active=value;cancelAnimationFrame(frame);frame=0;last=0
      canvas.dataset.running=String(value)
      if(active && !disposed)frame=requestAnimationFrame(draw)
    },
    destroy() {
      disposed=true;cancelAnimationFrame(frame);observer.disconnect()
      host.removeEventListener('pointermove',move)
      host.removeEventListener('pointerleave',leave)
    },
  }
}
