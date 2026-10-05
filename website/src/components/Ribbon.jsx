import { useEffect, useRef } from 'react'
import { PauseIcon, PlayIcon } from '../icons.jsx'
import { prefersReducedMotion } from '../hooks.js'

/**
 * Five ribbons of light weaving through each other.
 *
 * Drawn by one fragment shader. Every pixel works out how far it sits from
 * each band's centre line and lights itself from that, so the softness, the
 * glow and the reflection are arithmetic rather than blur passes. The whole
 * thing is one draw call a frame, where the 2D version was a CSS blur over a
 * large layer and hundreds of paths. Without WebGL it falls back to that 2D
 * drawing, simplified.
 *
 * Stops when off screen, hidden or paused; a paused ribbon is not redrawn.
 */

// Each band drifts on its own period, so they pass through each other and the
// color order keeps inverting. Periods share no common factors so the pattern
// doesn't visibly repeat.
const BANDS = [
  { css: '--iris', fallback: '#7b6cff', base: -0.1, drift: 0.105, period: 9, phase: 0, freq: 1, xphase: 0, speed: 0.16, thick: 0.15, alpha: 0.85 },
  { css: '--cyan', fallback: '#38d3d6', base: -0.05, drift: 0.095, period: 11.5, phase: 1.7, freq: 1.25, xphase: 1.1, speed: -0.13, thick: 0.125, alpha: 0.8 },
  { css: null, fallback: '#fff2dc', base: 0, drift: 0.055, period: 7.5, phase: 3.1, freq: 1.1, xphase: 2.4, speed: 0.1, thick: 0.09, alpha: 0.6 },
  { css: '--ember', fallback: '#ff8a6b', base: 0.05, drift: 0.095, period: 13, phase: 4.4, freq: 1.35, xphase: 3.3, speed: -0.17, thick: 0.125, alpha: 0.8 },
  { css: '--rose', fallback: '#ed5564', base: 0.1, drift: 0.105, period: 10, phase: 5.6, freq: 0.95, xphase: 4.9, speed: 0.14, thick: 0.15, alpha: 0.85 },
]

// How far the canvas reaches past the ribbon's frame, above and below, as a
// share of the frame's height. The glow and the floor reflection live there.
const BLEED_TOP = 0.45
const BLEED_BOTTOM = 0.9

const VERTEX = `
attribute vec2 p;
varying vec2 v;
void main() {
  v = p * 0.5 + 0.5;
  gl_Position = vec4(p, 0.0, 1.0);
}
`

const FRAGMENT = `
precision highp float;
varying vec2 v;
uniform float t;
uniform float intro;
uniform vec2 res;
uniform float bleedTop;
uniform float bleedBottom;
uniform vec3 col[5];
uniform vec4 shape[5];   // base, drift, period, phase
uniform vec4 wave[5];    // freq, xphase, speed, thick
uniform float alpha[5];

const float PI = 3.14159265;
const float TAU = 6.2831853;

// The light at one point of the frame: x along it, y down it, both 0..1, with
// the frame's height as the unit.
vec3 light(float x, float y, float px) {
  float breath = 0.84 + 0.16 * sin(t / 13.0 * TAU);
  float tilt = 0.052 + sin(t / 21.0 * TAU) * 0.038;
  float bloom = 0.88 + 0.12 * sin(t / 9.5 * TAU + 1.2);
  float spine = sin(x * PI * 0.92 + t * 0.17) * 0.055 + sin(x * PI * 1.9 - t * 0.11) * 0.021;

  // Fades in from both ends, peaking a little right of centre.
  float along = pow(max(sin(PI * x), 0.0), 1.15) * (0.82 + 0.18 * x);

  vec3 sum = vec3(0.0);
  for (int i = 0; i < 5; i++) {
    vec4 s = shape[i];
    vec4 w = wave[i];
    float centre = 0.5
      + spine * intro
      + s.x * 0.82 * breath
      + (x - 0.5) * tilt
      + sin(t / s.z * TAU + s.w) * s.y * breath * intro
      + sin(x * TAU * w.x + w.y + t * w.z) * 0.045 * intro
      + sin(x * TAU * w.x * 2.3 + w.y * 1.7 - t * w.z * 0.63) * 0.017 * intro;

    // A flat ribbon turning in space: full where it faces you, a line where
    // it turns edge-on, and the turn travels along it.
    float face = abs(sin(x * PI * 1.15 + t * 0.21 + w.y * 0.6));
    float hw = pow(max(sin(PI * x), 0.0), 0.86) * w.w * (0.12 + 0.88 * face)
      * (0.32 + 0.68 * breath) * intro;
    hw = max(hw, px * 1.2);

    float d = (y - centre) / hw;
    float body = exp(-d * d * 1.1);
    float core = exp(-d * d * 9.0);
    // The rim: silk catches the light along one edge, brightest face-on.
    float rim = exp(-pow((d + 0.8) / 0.16, 2.0)) * (0.35 + 0.65 * face * face);
    // A wide, faint halo: the room the light is in.
    float dg = (y - centre) / (w.w * 2.6 + 0.02);
    float halo = exp(-dg * dg) * 0.07;

    // Edge-on, the same light squeezes into less area and so gets brighter.
    float squeeze = 1.0 + 0.6 * (1.0 - face);
    vec3 c = col[i];
    sum += c * alpha[i] * (body * 0.62 + core * 0.4) * squeeze * along * bloom
      + mix(c, vec3(1.0), 0.6) * rim * 0.55 * along * bloom
      + c * halo * along;
  }
  return sum * intro;
}

void main() {
  // Canvas y, top down, in frame units: 0..1 is the frame.
  float span = 1.0 + bleedTop + bleedBottom;
  float y = (1.0 - v.y) * span - bleedTop;
  float x = v.x;
  float px = span / res.y;

  vec3 c = light(x, y, px);

  // The floor: the ribbon mirrored below its frame, dimmer, softer and
  // falling away with distance.
  if (y > 0.78) {
    float my = 0.78 - (y - 0.78) * 1.35;
    float fall = exp(-(y - 0.78) * 4.2) * smoothstep(0.78, 0.9, y);
    c += light(x, my, px * 3.0) * 0.16 * fall;
  }

  // Nothing reaches the canvas's top or bottom edge, so it has none.
  c *= smoothstep(0.0, 0.18, v.y) * smoothstep(1.0, 0.8, v.y);

  // Additive light rolls off to white instead of clipping.
  c = 1.0 - exp(-c * 1.35);
  float l = dot(c, vec3(0.299, 0.587, 0.114));
  c = mix(vec3(l), c, 1.35);

  // A little noise, so the long dark falloff doesn't band.
  float n = fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453);
  c += (n - 0.5) / 255.0;

  gl_FragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
`

const parse = (css) => {
  const hex = css.replace('#', '').trim()
  const full = hex.length === 3 ? hex.split('').map((c) => c + c).join('') : hex
  return [0, 2, 4].map((i) => parseInt(full.slice(i, i + 2), 16) / 255)
}

function readColors() {
  const styles = getComputedStyle(document.documentElement)
  return BANDS.map((band) => {
    const value = band.css ? styles.getPropertyValue(band.css).trim() : ''
    return parse(value.startsWith('#') ? value : band.fallback)
  })
}

function createGl(canvas) {
  const gl = canvas.getContext('webgl', {
    alpha: false,
    antialias: false,
    depth: false,
    stencil: false,
    premultipliedAlpha: false,
    powerPreference: 'low-power',
  })
  if (!gl) return null

  const compile = (type, source) => {
    const shader = gl.createShader(type)
    gl.shaderSource(shader, source)
    gl.compileShader(shader)
    return gl.getShaderParameter(shader, gl.COMPILE_STATUS) ? shader : null
  }
  const vs = compile(gl.VERTEX_SHADER, VERTEX)
  const fs = compile(gl.FRAGMENT_SHADER, FRAGMENT)
  if (!vs || !fs) return null

  const program = gl.createProgram()
  gl.attachShader(program, vs)
  gl.attachShader(program, fs)
  gl.linkProgram(program)
  if (!gl.getProgramParameter(program, gl.LINK_STATUS)) return null
  gl.useProgram(program)

  const buffer = gl.createBuffer()
  gl.bindBuffer(gl.ARRAY_BUFFER, buffer)
  gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 3, -1, -1, 3]), gl.STATIC_DRAW)
  const loc = gl.getAttribLocation(program, 'p')
  gl.enableVertexAttribArray(loc)
  gl.vertexAttribPointer(loc, 2, gl.FLOAT, false, 0, 0)

  const u = (name) => gl.getUniformLocation(program, name)
  gl.uniform3fv(u('col'), readColors().flat())
  gl.uniform4fv(u('shape'), BANDS.flatMap((b) => [b.base, b.drift, b.period, b.phase]))
  gl.uniform4fv(u('wave'), BANDS.flatMap((b) => [b.freq, b.xphase, b.speed, b.thick]))
  gl.uniform1fv(u('alpha'), BANDS.map((b) => b.alpha))
  gl.uniform1f(u('bleedTop'), BLEED_TOP)
  gl.uniform1f(u('bleedBottom'), BLEED_BOTTOM)

  const uTime = u('t')
  const uIntro = u('intro')
  const uRes = u('res')

  return {
    resize(w, h) {
      gl.viewport(0, 0, w, h)
      gl.uniform2f(uRes, w, h)
    },
    draw(time) {
      const raw = Math.min(1, time / 1.6)
      gl.uniform1f(uTime, time)
      gl.uniform1f(uIntro, raw * raw * (3 - 2 * raw))
      gl.drawArrays(gl.TRIANGLES, 0, 3)
    },
  }
}

/** The 2D fallback: the same weave as filled paths, softened by a CSS blur. */
function create2d(canvas) {
  const ctx = canvas.getContext('2d', { alpha: true })
  if (!ctx) return null
  const rgb = readColors().map((c) => c.map((v) => Math.round(v * 255)))
  const TAU = Math.PI * 2
  let w = 1
  let h = 1
  let frameTop = 0
  let frameH = 1

  return {
    resize(cw, ch) {
      w = cw
      h = ch
      frameH = ch / (1 + BLEED_TOP + BLEED_BOTTOM)
      frameTop = frameH * BLEED_TOP
    },
    draw(t) {
      ctx.clearRect(0, 0, w, h)
      ctx.globalCompositeOperation = 'lighter'
      const raw = Math.min(1, t / 1.6)
      const intro = raw * raw * (3 - 2 * raw)
      const breath = 0.84 + 0.16 * Math.sin((t / 13) * TAU)
      const tilt = 0.052 + Math.sin((t / 21) * TAU) * 0.038
      const steps = 64
      BANDS.forEach((band, index) => {
        const [r, g, b] = rgb[index]
        const centre = (u) =>
          frameTop +
          frameH *
            (0.5 +
              (Math.sin(u * Math.PI * 0.92 + t * 0.17) * 0.055 + Math.sin(u * Math.PI * 1.9 - t * 0.11) * 0.021) * intro +
              band.base * 0.82 * breath +
              (u - 0.5) * tilt +
              Math.sin((t / band.period) * TAU + band.phase) * band.drift * breath * intro +
              Math.sin(u * TAU * band.freq + band.xphase + t * band.speed) * 0.045 * intro)
        const half = (u) =>
          Math.sin(Math.PI * u) ** 0.86 *
          frameH *
          band.thick *
          (0.12 + 0.88 * Math.abs(Math.sin(u * Math.PI * 1.15 + t * 0.21 + band.xphase * 0.6))) *
          intro
        const gradient = ctx.createLinearGradient(0, 0, w, 0)
        const peak = band.alpha * 0.7 * intro
        gradient.addColorStop(0, `rgba(${r},${g},${b},0)`)
        gradient.addColorStop(0.58, `rgba(${r},${g},${b},${peak})`)
        gradient.addColorStop(1, `rgba(${r},${g},${b},0)`)
        ctx.fillStyle = gradient
        ctx.beginPath()
        for (let i = 0; i <= steps; i += 1) {
          const u = i / steps
          ctx.lineTo(u * w, centre(u) - half(u))
        }
        for (let i = steps; i >= 0; i -= 1) {
          const u = i / steps
          ctx.lineTo(u * w, centre(u) + half(u))
        }
        ctx.closePath()
        ctx.fill()
      })
      ctx.globalCompositeOperation = 'source-over'
    },
  }
}

export default function Ribbon({ playing, onToggle }) {
  const canvasRef = useRef(null)
  const wrapRef = useRef(null)
  const playingRef = useRef(playing)
  playingRef.current = playing

  useEffect(() => {
    const canvas = canvasRef.current
    const wrap = wrapRef.current
    if (!canvas || !wrap) return

    let renderer = createGl(canvas)
    const isGl = !!renderer
    // A canvas that has handed out a WebGL context can never give a 2D one,
    // so a shader that fails to compile draws its fallback on a fresh canvas.
    let canvas2d = null
    if (!renderer) {
      canvas2d = document.createElement('canvas')
      canvas2d.className = 'ribbon-canvas'
      canvas2d.setAttribute('aria-hidden', 'true')
      canvas.style.display = 'none'
      canvas.after(canvas2d)
      renderer = create2d(canvas2d)
    }
    if (!renderer) return
    const surface = canvas2d ?? canvas
    wrap.dataset.renderer = isGl ? 'gl' : '2d'

    let clock = 0
    let frame = 0
    let last = performance.now()
    let onScreen = true
    let painted = false

    const resize = () => {
      const rect = surface.getBoundingClientRect()
      // The light is soft everywhere but the rims, so it doesn't need every
      // device pixel: a little over one per CSS pixel on a retina screen,
      // capped so a wide monitor doesn't shade millions of pixels a frame.
      const dpr = Math.min(window.devicePixelRatio || 1, 2)
      let scale = isGl ? dpr * 0.6 : 0.5
      const cap = 1.1e6
      if (rect.width * rect.height * scale * scale > cap) {
        scale = Math.sqrt(cap / (rect.width * rect.height))
      }
      const w = Math.max(1, Math.round(rect.width * scale))
      const h = Math.max(1, Math.round(rect.height * scale))
      if (surface.width !== w || surface.height !== h) {
        surface.width = w
        surface.height = h
      }
      renderer.resize(w, h)
      renderer.draw(clock)
    }
    resize()
    const observer = new ResizeObserver(resize)
    observer.observe(surface)

    const tick = (now) => {
      frame = 0
      const dt = Math.min(0.05, (now - last) / 1000)
      last = now
      const moving = playingRef.current
      if (moving) clock += dt
      // A paused ribbon stands still, so there is nothing to redraw.
      if (moving || !painted) {
        renderer.draw(clock)
        painted = true
      }
      if (onScreen && !document.hidden) frame = requestAnimationFrame(tick)
    }

    const start = () => {
      if (frame || prefersReducedMotion()) return
      last = performance.now()
      frame = requestAnimationFrame(tick)
    }
    const stop = () => {
      if (frame) cancelAnimationFrame(frame)
      frame = 0
    }

    // Reduced motion gets one still frame of the full shape.
    if (prefersReducedMotion()) {
      clock = 6
      renderer.draw(clock)
    } else {
      start()
    }

    const onVisibility = () => (document.hidden ? stop() : onScreen && start())
    document.addEventListener('visibilitychange', onVisibility)

    const io = new IntersectionObserver(
      ([entry]) => {
        onScreen = entry.isIntersecting
        if (onScreen) start()
        else stop()
      },
      { rootMargin: '80px' },
    )
    io.observe(wrap)

    return () => {
      stop()
      document.removeEventListener('visibilitychange', onVisibility)
      observer.disconnect()
      io.disconnect()
      canvas2d?.remove()
    }
  }, [])

  return (
    <div className="ribbon-wrap">
      <div className="ribbon" ref={wrapRef}>
        <canvas className="ribbon-canvas" ref={canvasRef} aria-hidden="true" />
      </div>

      <button
        type="button"
        className="ambient-toggle"
        onClick={onToggle}
        aria-label={playing ? 'Pause the animation' : 'Play the animation'}
      >
        {playing ? <PauseIcon /> : <PlayIcon />}
      </button>
    </div>
  )
}
