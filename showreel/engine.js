/*
 * Cents — motion reel. The engine.
 *
 * Every frame is a pure function of time. `render(t)` sets every property it touches from `t`
 * alone, never from the previous frame, so the capture can sample any instant, in any order,
 * as many times as motion blur wants — and a frame rendered twice is the same frame twice.
 */
'use strict';

// ---------------------------------------------------------------------------------------------
// Time. The film is written on a 96 BPM grid: 96 beats, 24 bars, exactly 60 seconds — twelve
// shots of two bars (five seconds) each.
// ---------------------------------------------------------------------------------------------

const BPM = 96;
const BEAT = 60 / BPM;
const BEATS = 96;
const DURATION = BEATS * BEAT;
const W = 1920;
const H = 1080;
/** Beats to seconds. Scenes are timed in beats so picture and music cannot drift apart. */
const B = (n) => n * BEAT;

// ---------------------------------------------------------------------------------------------
// Math.
// ---------------------------------------------------------------------------------------------

const clamp = (v, lo = 0, hi = 1) => (v < lo ? lo : v > hi ? hi : v);
const lerp = (a, b, t) => a + (b - a) * t;
/** Where `t` sits between `t0` and `t1`, clamped to 0..1. */
const prog = (t, t0, t1) => (t1 === t0 ? (t >= t1 ? 1 : 0) : clamp((t - t0) / (t1 - t0)));
const TAU = Math.PI * 2;
const rad = (d) => (d * Math.PI) / 180;

/** Deterministic noise, so "random" placement is the same on every render. */
function rng(seed) {
  let s = seed >>> 0;
  return () => {
    s = (s + 0x6d2b79f5) >>> 0;
    let x = s;
    x = Math.imul(x ^ (x >>> 15), x | 1);
    x ^= x + Math.imul(x ^ (x >>> 7), x | 61);
    return ((x ^ (x >>> 14)) >>> 0) / 4294967296;
  };
}

// ---------------------------------------------------------------------------------------------
// Easing.
// ---------------------------------------------------------------------------------------------

function cubicBezier(x1, y1, x2, y2) {
  const cx = 3 * x1;
  const bx = 3 * (x2 - x1) - cx;
  const ax = 1 - cx - bx;
  const cy = 3 * y1;
  const by = 3 * (y2 - y1) - cy;
  const ay = 1 - cy - by;
  const sx = (t) => ((ax * t + bx) * t + cx) * t;
  const sy = (t) => ((ay * t + by) * t + cy) * t;
  const dx = (t) => (3 * ax * t + 2 * bx) * t + cx;
  const solve = (x) => {
    let t = x;
    for (let i = 0; i < 8; i++) {
      const err = sx(t) - x;
      if (Math.abs(err) < 1e-7) return t;
      const d = dx(t);
      if (Math.abs(d) < 1e-7) break;
      t -= err / d;
    }
    let lo = 0;
    let hi = 1;
    t = x;
    for (let i = 0; i < 40; i++) {
      const v = sx(t);
      if (Math.abs(v - x) < 1e-7) break;
      if (x > v) lo = t;
      else hi = t;
      t = (lo + hi) / 2;
    }
    return t;
  };
  return (x) => (x <= 0 ? 0 : x >= 1 ? 1 : sy(solve(x)));
}

const ease = {
  linear: (t) => t,
  inQuad: (t) => t * t,
  outQuad: (t) => 1 - (1 - t) * (1 - t),
  inOutQuad: (t) => (t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2),
  inCubic: (t) => t * t * t,
  outCubic: (t) => 1 - Math.pow(1 - t, 3),
  inOutCubic: (t) => (t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2),
  outQuart: (t) => 1 - Math.pow(1 - t, 4),
  inQuart: (t) => t * t * t * t,
  inOutQuart: (t) => (t < 0.5 ? 8 * t * t * t * t : 1 - Math.pow(-2 * t + 2, 4) / 2),
  outQuint: (t) => 1 - Math.pow(1 - t, 5),
  inOutQuint: (t) => (t < 0.5 ? 16 * Math.pow(t, 5) : 1 - Math.pow(-2 * t + 2, 5) / 2),
  outExpo: (t) => (t >= 1 ? 1 : 1 - Math.pow(2, -10 * t)),
  inExpo: (t) => (t <= 0 ? 0 : Math.pow(2, 10 * t - 10)),
  inOutExpo: (t) =>
    t <= 0 ? 0 : t >= 1 ? 1 : t < 0.5 ? Math.pow(2, 20 * t - 10) / 2 : (2 - Math.pow(2, -20 * t + 10)) / 2,
  outBack: (t, s = 1.70158) => 1 + (s + 1) * Math.pow(t - 1, 3) + s * Math.pow(t - 1, 2),
  inBack: (t, s = 1.70158) => (s + 1) * t * t * t - s * t * t,
  /** A long, confident settle. The default for anything arriving. */
  swift: cubicBezier(0.16, 1, 0.3, 1),
  /** Hard in, hard out: whips and cuts. */
  snap: cubicBezier(0.85, 0, 0.15, 1),
  /** Camera moves: no jolt at either end. */
  smooth: cubicBezier(0.45, 0, 0.2, 1),
  /** Compose's FastOutSlowInEasing — the curve the app's own bubble expansion runs on. */
  standard: cubicBezier(0.4, 0, 0.2, 1),
  /** Leaving the frame. */
  accel: cubicBezier(0.55, 0, 1, 0.45),
  /** A small wind-up before the move. */
  anticipate: cubicBezier(0.5, -0.35, 0.3, 1),
};

/** A tween: `a` at `t0`, `b` at `t1`, eased between, held outside. */
const tw = (t, t0, t1, a, b, e = ease.swift) => lerp(a, b, e(prog(t, t0, t1)));

/**
 * Keyframes: [[time, value, easeIntoThisKey?], ...]. Values may be numbers or arrays of numbers.
 */
function kf(t, keys) {
  if (t <= keys[0][0]) return keys[0][1];
  for (let i = 1; i < keys.length; i++) {
    const [t1, v1, e = ease.smooth] = keys[i];
    if (t <= t1) {
      const [t0, v0] = keys[i - 1];
      const p = e(prog(t, t0, t1));
      if (Array.isArray(v0)) return v0.map((v, j) => lerp(v, v1[j], p));
      return lerp(v0, v1, p);
    }
  }
  return keys[keys.length - 1][1];
}

/**
 * The step response of a damped spring, 0 → 1, `dt` seconds after release.
 * `f` is the natural frequency in hertz, `z` the damping ratio (below 1 overshoots).
 */
function spring(dt, f = 2.4, z = 0.55) {
  if (dt <= 0) return 0;
  const w = TAU * f;
  if (z < 1) {
    const wd = w * Math.sqrt(1 - z * z);
    return 1 - Math.exp(-z * w * dt) * (Math.cos(wd * dt) + ((z * w) / wd) * Math.sin(wd * dt));
  }
  return 1 - Math.exp(-w * dt) * (1 + w * dt);
}
/** A spring from `a` to `b`, released at `t0`. */
const sp = (t, t0, a, b, f, z) => lerp(a, b, spring(t - t0, f, z));

/** A little kick that dies away: 0 before `dt`, a peak near 60 ms, gone by 400 ms. */
const bump = (dt) => (dt <= 0 ? 0 : Math.exp(-dt * 9) * Math.sin(dt * 22));

// ---------------------------------------------------------------------------------------------
// Colour.
// ---------------------------------------------------------------------------------------------

function rgbOf(c) {
  const s = c.replace('#', '');
  return [parseInt(s.slice(0, 2), 16), parseInt(s.slice(2, 4), 16), parseInt(s.slice(4, 6), 16)];
}
function mix(c1, c2, t) {
  const a = rgbOf(c1);
  const b = rgbOf(c2);
  const p = clamp(t);
  return `rgb(${Math.round(lerp(a[0], b[0], p))},${Math.round(lerp(a[1], b[1], p))},${Math.round(lerp(a[2], b[2], p))})`;
}
function rgba(c, a) {
  const [r, g, b] = rgbOf(c);
  return `rgba(${r},${g},${b},${a})`;
}

/** The app's palette, light and dark — `ui/theme/CentsColors.kt` and `CategoryPalette.kt`. */
const C = {
  blue: '#007AFF',
  blueD: '#0A84FF',
  green: '#34C759',
  greenD: '#30D158',
  red: '#FF3B30',
  redD: '#FF453A',
  orange: '#FF9500',
  orangeD: '#FF9F0A',
  yellow: '#FFCC00',
  yellowD: '#FFD60A',
  teal: '#00C7BE',
  cyan: '#30B0C7',
  indigo: '#5856D6',
  purple: '#AF52DE',
  pink: '#FF2D55',
  brown: '#A2845E',
  grey: '#8E8E93',
  bgLight: '#F2F2F7',
  card: '#FFFFFF',
  pressed: '#D1D1D6',
  bar: '#F9F9F9',
  label: '#000000',
  label2: 'rgba(60,60,67,0.6)',
  label3: 'rgba(60,60,67,0.3)',
  sep: 'rgba(60,60,67,0.29)',
  cardD: '#1C1C1E',
  cardD2: '#2C2C2E',
  label2D: 'rgba(235,235,245,0.6)',
  label3D: 'rgba(235,235,245,0.3)',
};

// ---------------------------------------------------------------------------------------------
// DOM.
// ---------------------------------------------------------------------------------------------

function h(tag, cls, parent, style, text) {
  const e = document.createElement(tag);
  if (cls) e.className = cls;
  if (style) Object.assign(e.style, style);
  if (text != null) e.textContent = text;
  if (parent) parent.appendChild(e);
  return e;
}

const SVGNS = 'http://www.w3.org/2000/svg';
function s(tag, attrs, parent) {
  const e = document.createElementNS(SVGNS, tag);
  if (attrs) for (const k in attrs) e.setAttribute(k, attrs[k]);
  if (parent) parent.appendChild(e);
  return e;
}

/**
 * Place an element by its centre. Rotation and scale happen about that centre.
 * `o`: s, sx, sy (scale), r (deg), rx, ry (deg, needs a perspective above), o (opacity).
 */
function place(e, x, y, o = {}) {
  const sc = o.s === undefined ? 1 : o.s;
  const sx = sc * (o.sx === undefined ? 1 : o.sx);
  const sy = sc * (o.sy === undefined ? 1 : o.sy);
  let tf = `translate(${x.toFixed(3)}px,${y.toFixed(3)}px) translate(-50%,-50%)`;
  if (o.z) tf += ` translateZ(${o.z}px)`;
  if (o.rx) tf += ` rotateX(${o.rx}deg)`;
  if (o.ry) tf += ` rotateY(${o.ry}deg)`;
  if (o.r) tf += ` rotate(${o.r}deg)`;
  if (sx !== 1 || sy !== 1) tf += ` scale(${sx.toFixed(5)},${sy.toFixed(5)})`;
  e.style.transform = tf;
  if (o.o !== undefined) e.style.opacity = clamp(o.o).toFixed(4);
}

/** Same, anchored at the top-left corner. */
function placeTL(e, x, y, o = {}) {
  const sc = o.s === undefined ? 1 : o.s;
  let tf = `translate(${x.toFixed(3)}px,${y.toFixed(3)}px)`;
  if (o.r) tf += ` rotate(${o.r}deg)`;
  if (sc !== 1) tf += ` scale(${sc.toFixed(5)})`;
  e.style.transform = tf;
  if (o.o !== undefined) e.style.opacity = clamp(o.o).toFixed(4);
}

/** Hide or show an element, keeping whatever display it had (flex stays flex). */
function show(e, visible) {
  if (visible) {
    if (e.style.display === 'none') e.style.display = e.dataset.display || '';
  } else if (e.style.display !== 'none') {
    e.dataset.display = e.style.display;
    e.style.display = 'none';
  }
}

function opacity(e, v) {
  e.style.opacity = clamp(v).toFixed(4);
}

// ---------------------------------------------------------------------------------------------
// Money, written the way the app writes it on the owner's phone: `2.388,40 €`.
// ---------------------------------------------------------------------------------------------

function eur(cents, { sign = false, symbol = true, decimals = true } = {}) {
  const neg = cents < 0;
  const v = Math.abs(Math.round(cents));
  const whole = String(Math.floor(v / 100)).replace(/\B(?=(\d{3})+(?!\d))/g, '.');
  const frac = decimals ? ',' + String(v % 100).padStart(2, '0') : '';
  return (neg ? '−' : sign ? '+' : '') + whole + frac + (symbol ? ' €' : '');
}

// ---------------------------------------------------------------------------------------------
// The app's own icons — `res/drawable/ic_*.xml`, one path per subpath so each can draw on.
// ---------------------------------------------------------------------------------------------

const ICONS = {
  transactions: {
    w: 2,
    d: ['M4,6.5 L4.01,6.5', 'M9,6.5 L20,6.5', 'M4,12 L4.01,12', 'M9,12 L20,12', 'M4,17.5 L4.01,17.5', 'M9,17.5 L20,17.5'],
  },
  accounts: {
    w: 1.9,
    d: [
      'M5,6.5 a2.5,2.5 0 0,1 2.5,-2.5 h11 a2.5,2.5 0 0,1 2.5,2.5 v11 a2.5,2.5 0 0,1 -2.5,2.5 h-11 a2.5,2.5 0 0,1 -2.5,-2.5 z',
      'M3.5,9.5 L20.5,9.5',
      'M16.5,15 L16.51,15',
    ],
  },
  investments: { w: 2.4, d: ['M4,16 L9.5,10.5 L13,14 L20,7', 'M15,7 L20,7 L20,12'] },
  reports: { w: 2.4, d: ['M5,19 L5,11', 'M12,19 L12,5', 'M19,19 L19,14'] },
  settings: {
    w: 1.9,
    d: ['M4,7 L20,7', 'M4,12 L20,12', 'M4,17 L20,17'],
    knobs: [
      [9, 7],
      [16, 12],
      [8, 17],
    ],
  },
  chevronRight: { w: 2.4, d: ['M9,5 L16.5,12 L9,19'] },
  chevronLeft: { w: 2.4, d: ['M15,5 L7.5,12 L15,19'] },
  search: { w: 2, d: ['M11,4 A7,7 0 1,1 11,18 A7,7 0 1,1 11,4', 'M16,16 L21,21'] },
};

/**
 * An icon as inline SVG. Returns `{ root, paths, knobs, draw(p) }` where `draw` strokes it on,
 * one subpath after another.
 */
function icon(name, parent, size, color, extraClass) {
  const def = ICONS[name];
  const root = s('svg', { viewBox: '0 0 24 24', width: size, height: size, class: extraClass || '' }, parent);
  root.style.overflow = 'visible';
  const paths = def.d.map((d) =>
    s(
      'path',
      {
        d,
        fill: 'none',
        stroke: color,
        'stroke-width': def.w,
        'stroke-linecap': 'round',
        'stroke-linejoin': 'round',
        pathLength: 1,
      },
      root,
    ),
  );
  const knobs = (def.knobs || []).map(([cx, cy]) =>
    s('circle', { cx, cy, r: 2, fill: '#FFFFFF', stroke: color, 'stroke-width': def.w }, root),
  );
  const draw = (p) => {
    const n = paths.length;
    paths.forEach((path, i) => {
      const local = clamp(p * n - i * 0.65);
      path.style.strokeDasharray = '1 1';
      path.style.strokeDashoffset = String(1 - local);
      path.style.opacity = local > 0 ? 1 : 0;
    });
    knobs.forEach((k, i) => {
      const local = ease.outBack(clamp((p - 0.45 - i * 0.12) / 0.35));
      k.setAttribute('r', String(2 * Math.max(0, local)));
    });
  };
  return { root, paths, knobs, draw };
}

// ---------------------------------------------------------------------------------------------
// Kinetic type: a line split into letters, each inside the line's mask so it can rise from
// below the baseline.
// ---------------------------------------------------------------------------------------------

function kinetic(parent, text, cls, style) {
  const root = h('div', `kin ${cls || ''}`, parent, style);
  const mask = h('div', 'kin-mask', root);
  const chars = [...text].map((ch) => h('span', 'kin-ch', mask, null, ch === ' ' ? ' ' : ch));
  return { root, mask, chars };
}

/** Letters rising into place one after another. `p` is not clamped per letter on purpose. */
function riseLetters(k, t, t0, { stagger = 0.022, dur = 0.5, from = 1.05, e = ease.swift, blur = false } = {}) {
  k.chars.forEach((c, i) => {
    const p = e(prog(t, t0 + i * stagger, t0 + i * stagger + dur));
    c.style.transform = `translateY(${((1 - p) * from * 100).toFixed(3)}%)`;
    c.style.opacity = p > 0 ? 1 : 0;
  });
}

/** Letters leaving upward, the same way they came. */
function leaveLetters(k, t, t0, { stagger = 0.015, dur = 0.35, to = -1.05, e = ease.accel } = {}) {
  k.chars.forEach((c, i) => {
    const p = e(prog(t, t0 + i * stagger, t0 + i * stagger + dur));
    if (p <= 0) return;
    c.style.transform = `translateY(${(p * to * 100).toFixed(3)}%)`;
  });
}

// ---------------------------------------------------------------------------------------------
// Sound cues. The picture writes down what happens and when; the soundtrack reads the list.
// ---------------------------------------------------------------------------------------------

const CUES = [];
function cue(t, type, extra) {
  CUES.push(Object.assign({ t: +t.toFixed(5), type }, extra || {}));
}
