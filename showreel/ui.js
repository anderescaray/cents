/*
 * Cents — the film's UI kit.
 *
 * The app on screen is designed for the film: the same features, the same numbers and words,
 * drawn as simply as possible. One idea carries it — a ring of category colours that says where
 * the money went — and everything else is white cards, one clear number each, and the colours
 * of `CategoryPalette.kt`.
 */
'use strict';

// ---------------------------------------------------------------------------------------------
// Tokens.
// ---------------------------------------------------------------------------------------------

const UI = {
  bg: '#F4F5F9',
  card: '#FFFFFF',
  ink: '#0E1117',
  ink2: '#6E7581',
  ink3: '#A9AFB9',
  line: 'rgba(14,17,23,0.07)',
  fill: '#EEF0F4',
  blue: '#007AFF',
  green: '#28B45A',
  red: '#FF3B30',
  amber: '#FF9500',
  shadow: '0 1px 2px rgba(16,24,40,0.05), 0 8px 24px rgba(16,24,40,0.07)',
};

/** Categories, in the app's own palette (`CategoryPalette.kt`). */
const CAT = {
  housing: { name: 'Housing', color: '#007AFF', glyph: 'house' },
  groceries: { name: 'Groceries', color: '#34C759', glyph: 'cart' },
  restaurants: { name: 'Restaurants', color: '#FF9500', glyph: 'fork' },
  shopping: { name: 'Shopping', color: '#FF2D55', glyph: 'bag' },
  transport: { name: 'Transport', color: '#00C7BE', glyph: 'bus' },
  subscriptions: { name: 'Subscriptions', color: '#AF52DE', glyph: 'play' },
  flights: { name: 'Flights', color: '#5856D6', glyph: 'plane' },
  hotel: { name: 'Hotel', color: '#30B0C7', glyph: 'bed' },
  food: { name: 'Food', color: '#FF9500', glyph: 'fork' },
  taxi: { name: 'Taxi', color: '#00C7BE', glyph: 'bus' },
  tours: { name: 'Tours', color: '#FF2D55', glyph: 'ticket' },
  bills: { name: 'Electricity', color: '#FFCC00', ink: '#E0A100', glyph: 'bolt' },
  salary: { name: 'Salary', color: '#34C759', glyph: 'briefcase' },
  gym: { name: 'Gym', color: '#FF3B30', glyph: 'dumbbell' },
  insurance: { name: 'Insurance', color: '#5856D6', glyph: 'shield' },
  phone: { name: 'Phone', color: '#30B0C7', glyph: 'phone' },
  netflix: { name: 'Netflix', color: '#FF3B30', glyph: 'play' },
  spotify: { name: 'Spotify', color: '#34C759', glyph: 'music' },
  rent: { name: 'Rent', color: '#007AFF', glyph: 'house' },
  unknown: { name: 'Uncategorized', color: '#8E8E93', glyph: 'question' },
};

// ---------------------------------------------------------------------------------------------
// Glyphs: 24-unit line drawings, round caps, drawn as strokes so they can draw themselves on.
// ---------------------------------------------------------------------------------------------

const GLYPH = {
  cart: ['M3 4.5h2.3l2.2 10a1.8 1.8 0 0 0 1.8 1.4h7.6a1.8 1.8 0 0 0 1.7-1.3L20.8 8.5H6.2', 'M9.6 19.6h.01', 'M16.8 19.6h.01'],
  house: ['M4 11.2 12 4.6l8 6.6', 'M6.2 9.6v9.9h4.2v-5.2h3.2v5.2h4.2V9.6'],
  fork: ['M7.5 3.5v6a2.3 2.3 0 0 0 4.6 0v-6', 'M9.8 3.5v17', 'M17 20.5v-17c-1.9.9-3.1 3.4-3.1 7.6H17'],
  bag: ['M5.6 8.2h12.8l-1 12H6.6z', 'M9 8.2V6.8a3 3 0 0 1 6 0v1.4'],
  bus: ['M6.3 4.5h11.4a1.6 1.6 0 0 1 1.6 1.6v10.6H4.7V6.1a1.6 1.6 0 0 1 1.6-1.6z', 'M4.7 11.3h14.6', 'M7.6 16.7v2.6', 'M16.4 16.7v2.6', 'M8.2 14h.01', 'M15.8 14h.01'],
  play: ['M12 3.6a8.4 8.4 0 1 0 0 16.8 8.4 8.4 0 1 0 0-16.8z', 'M10.3 8.9v6.2l5-3.1z'],
  plane: ['M12 3.2c.7 0 1.2.8 1.2 1.9v4.4l6.7 4v1.9l-6.7-2v4.2l2.1 1.6v1.6L12 20l-3.3.8v-1.6l2.1-1.6v-4.2l-6.7 2v-1.9l6.7-4V5.1c0-1.1.5-1.9 1.2-1.9z'],
  bed: ['M3.5 7.5v12', 'M3.5 15h17v4.5', 'M20.5 15v-2.2a2.3 2.3 0 0 0-2.3-2.3h-7.4V15', 'M7.2 12.6h.01'],
  ticket: ['M4 7.2h16v3.1a1.8 1.8 0 0 0 0 3.4v3.1H4v-3.1a1.8 1.8 0 0 0 0-3.4z', 'M14.5 7.8v8.4'],
  bolt: ['M13.2 2.8 5.3 13.6h6.1l-.9 7.6 7.9-10.8h-6.1z'],
  briefcase: ['M4 8.5h16v10.8H4z', 'M9 8.5V6.2a1.1 1.1 0 0 1 1.1-1.1h3.8A1.1 1.1 0 0 1 15 6.2v2.3', 'M4 13.4h16'],
  dumbbell: ['M6.8 7.5v9', 'M17.2 7.5v9', 'M4.2 9.6v4.8', 'M19.8 9.6v4.8', 'M6.8 12h10.4'],
  shield: ['M12 3.4 19 6v5.4c0 4.4-2.9 7.5-7 9.1-4.1-1.6-7-4.7-7-9.1V6z'],
  phone: ['M8.2 3.4h7.6a1.6 1.6 0 0 1 1.6 1.6v14a1.6 1.6 0 0 1-1.6 1.6H8.2A1.6 1.6 0 0 1 6.6 19V5a1.6 1.6 0 0 1 1.6-1.6z', 'M11 17.4h2'],
  music: ['M9.2 17.6V6.3l9.6-1.9v11.2', 'M9.2 17.6a2.4 2.4 0 1 1-4.8 0 2.4 2.4 0 0 1 4.8 0z', 'M18.8 15.6a2.4 2.4 0 1 1-4.8 0 2.4 2.4 0 0 1 4.8 0z'],
  question: ['M9.3 9.2a2.8 2.8 0 1 1 3.9 2.6c-.8.4-1.2 1-1.2 1.9v.6', 'M12 17.4h.01'],
  wallet: ['M4.5 7.6h14a1.5 1.5 0 0 1 1.5 1.5v9.3a1.5 1.5 0 0 1-1.5 1.5H6a1.5 1.5 0 0 1-1.5-1.5z', 'M4.5 7.6 15.6 4.3v3.3', 'M16.2 13.7h.01'],
  bank: ['M4.5 9.2h15', 'M4.5 9.2 12 4.4l7.5 4.8', 'M6.8 9.6v7.6', 'M10.3 9.6v7.6', 'M13.7 9.6v7.6', 'M17.2 9.6v7.6', 'M4.2 19.6h15.6'],
  trend: ['M4 16 9.5 10.5 13 14 20 7', 'M15 7h5v5'],
  cloud: ['M8 18.6h9.3a4 4 0 0 0 .7-7.9 5.7 5.7 0 0 0-10.9-.9A4.4 4.4 0 0 0 8 18.6z'],
  check: ['M5.2 12.6 10 17.2 18.8 7.6'],
  plus: ['M12 5.2v13.6', 'M5.2 12h13.6'],
  lock: ['M7.4 10.6V8a4.6 4.6 0 0 1 9.2 0v2.6', 'M5.8 10.6h12.4v9.2H5.8z'],
  chevron: ['M9.5 6.2 15.3 12l-5.8 5.8'],
  calendar: ['M4.6 6.4h14.8v13H4.6z', 'M4.6 10.4h14.8', 'M8.6 4v4', 'M15.4 4v4'],
  bell: ['M6.2 16.4V11a5.8 5.8 0 0 1 11.6 0v5.4l1.5 1.5H4.7z', 'M10.1 20.4a2 2 0 0 0 3.8 0'],
  clock: ['M12 3.8a8.2 8.2 0 1 0 0 16.4 8.2 8.2 0 1 0 0-16.4z', 'M12 7.6V12l3 2'],
  ringIcon: ['M13.2 4.3a7.8 7.8 0 0 1 6.4 8.9', 'M19 15.5a7.8 7.8 0 0 1-9.6 4.1', 'M6.4 17.5a7.8 7.8 0 0 1 3.9-13'],
  list: ['M5 7h.01', 'M9 7h10', 'M5 12h.01', 'M9 12h10', 'M5 17h.01', 'M9 17h10'],
  chart: ['M6 19v-7', 'M12 19V6', 'M18 19v-4'],
  sliders: ['M4 7h16', 'M4 12h16', 'M4 17h16'],
  arrowDown: ['M12 4.5v14', 'M6.5 13.2 12 18.7l5.5-5.5'],
  fingerprint: [
    'M7.6 6.4a8 8 0 0 1 8.8 0', 'M5.2 10.4a8.2 8.2 0 0 1 1.6-2.6', 'M17.2 7.8a8.2 8.2 0 0 1 1.6 2.6',
    'M7.8 18.2a9 9 0 0 1-1.4-4.8 5.6 5.6 0 0 1 11.2 0', 'M10 20a7.5 7.5 0 0 1-1.1-3.9 3.1 3.1 0 0 1 6.2 0v1.1',
    'M12 13.4v2.6a4.6 4.6 0 0 0 1.4 3.3', 'M17.6 15.6a11 11 0 0 1-.8 3.8',
  ],
};

/**
 * A glyph as inline SVG, `size` pixels square. `draw(p)` strokes it on, path after path.
 */
function glyph(parent, name, size, color, sw = 2) {
  const root = s('svg', { viewBox: '0 0 24 24', width: size, height: size }, parent);
  root.style.overflow = 'visible';
  root.style.display = 'block';
  const paths = GLYPH[name].map((d) =>
    s('path', { d, fill: 'none', stroke: color, 'stroke-width': sw, 'stroke-linecap': 'round', 'stroke-linejoin': 'round', pathLength: 1 }, root));
  return {
    root,
    paths,
    draw(p) {
      const n = paths.length;
      paths.forEach((path, i) => {
        const local = clamp(p * (n + 1) - i);
        path.style.strokeDasharray = '1 1';
        path.style.strokeDashoffset = String(1 - local);
        path.style.opacity = local > 0 ? 1 : 0;
      });
    },
    color(c) {
      paths.forEach((path) => path.setAttribute('stroke', c));
    },
  };
}

// ---------------------------------------------------------------------------------------------
// Building blocks. Everything is absolutely placed in its parent's pixels.
// ---------------------------------------------------------------------------------------------

/** Text. `x, y` is the top-left of the line box; `align` right or centre uses `w`. */
function tx(parent, x, y, text, o = {}) {
  const size = o.size || 16;
  const style = {
    position: 'absolute',
    left: `${x}px`,
    top: `${y}px`,
    fontSize: `${size}px`,
    fontWeight: o.weight || 500,
    color: o.color || UI.ink,
    letterSpacing: `${o.ls === undefined ? -0.01 : o.ls}em`,
    lineHeight: `${o.lh || 1.2}`,
    whiteSpace: o.wrap ? 'normal' : 'nowrap',
  };
  if (o.w) {
    style.width = `${o.w}px`;
    if (o.align === 'right') style.left = `${x - o.w}px`;
    if (o.align === 'center') style.left = `${x - o.w / 2}px`;
    style.textAlign = o.align || 'left';
  }
  const e = h('div', o.tnum ? 'tnum' : null, parent, style, text);
  if (o.cls) e.classList.add(o.cls);
  return e;
}

function box(parent, x, y, w, hh, o = {}) {
  return h('div', null, parent, {
    position: 'absolute', left: `${x}px`, top: `${y}px`, width: `${w}px`, height: `${hh}px`,
    background: o.bg || UI.card, borderRadius: `${o.r === undefined ? 22 : o.r}px`,
    boxShadow: o.shadow === false ? 'none' : o.shadow || UI.shadow, overflow: o.clip ? 'hidden' : 'visible',
  });
}

/** A category's badge: its colour, softly, behind its glyph. */
function catIcon(parent, cat, x, y, size = 36, o = {}) {
  const c = CAT[cat];
  const e = h('div', null, parent, {
    position: 'absolute', left: `${x}px`, top: `${y}px`, width: `${size}px`, height: `${size}px`,
    borderRadius: `${size / 2}px`, background: o.solid ? c.color : rgba(c.color, 0.15),
    display: 'flex', alignItems: 'center', justifyContent: 'center',
  });
  const g = glyph(e, c.glyph, size * 0.56, o.solid ? '#FFFFFF' : c.ink || c.color, 2.1);
  e.glyph = g;
  return e;
}

/** A pill with a category badge and a name: the way a category is picked. */
function chip(parent, x, y, cat, label, o = {}) {
  const c = CAT[cat];
  const hh = o.h || 38;
  const e = h('div', null, parent, {
    position: 'absolute', left: `${x}px`, top: `${y}px`, height: `${hh}px`, borderRadius: `${hh / 2}px`,
    background: o.bg || UI.fill, display: 'flex', alignItems: 'center', gap: '8px',
    padding: `0 ${o.pad || 14}px 0 ${(hh - 26) / 2}px`, whiteSpace: 'nowrap', fontSize: `${o.size || 15}px`, fontWeight: 600,
    color: UI.ink, letterSpacing: '-0.01em',
  });
  const dot = h('div', null, e, {
    width: '26px', height: '26px', borderRadius: '13px', background: rgba(c.color, 0.16),
    display: 'flex', alignItems: 'center', justifyContent: 'center', flex: '0 0 auto',
  });
  glyph(dot, c.glyph, 15, c.ink || c.color, 2.2);
  const label_ = h('span', null, e, null, label || c.name);
  e.label = label_;
  e.dot = dot;
  return e;
}

function button(parent, x, y, w, hh, label, o = {}) {
  const e = h('div', null, parent, {
    position: 'absolute', left: `${x}px`, top: `${y}px`, width: `${w}px`, height: `${hh}px`,
    borderRadius: `${o.r || hh / 2}px`, background: o.bg || UI.blue, color: o.color || '#fff',
    display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px',
    fontSize: `${o.size || 17}px`, fontWeight: 600, letterSpacing: '-0.01em', whiteSpace: 'nowrap',
    boxShadow: o.shadow || 'none',
  });
  if (o.glyph) glyph(e, o.glyph, o.size ? o.size + 2 : 19, o.color || '#fff', 2.4);
  h('span', null, e, null, label);
  return e;
}

/** An iOS-like switch. `set(p)` moves it from off (0) to on (1). */
function toggle(parent, x, y) {
  const track = h('div', null, parent, {
    position: 'absolute', left: `${x}px`, top: `${y}px`, width: '52px', height: '32px', borderRadius: '16px', background: '#E3E5EA',
  });
  const knob = h('div', null, track, {
    position: 'absolute', left: '2px', top: '2px', width: '28px', height: '28px', borderRadius: '14px', background: '#fff',
    boxShadow: '0 2px 6px rgba(0,0,0,0.16), 0 1px 1px rgba(0,0,0,0.1)',
  });
  return {
    track,
    set(p) {
      knob.style.transform = `translateX(${(20 * p).toFixed(2)}px)`;
      track.style.background = mix('#E3E5EA', UI.green, clamp(p));
    },
  };
}

/** A segmented control whose thumb slides between options. */
function segmented(parent, x, y, w, labels, o = {}) {
  const hh = o.h || 36;
  const root = h('div', null, parent, {
    position: 'absolute', left: `${x}px`, top: `${y}px`, width: `${w}px`, height: `${hh}px`, borderRadius: `${hh / 2}px`, background: UI.fill,
  });
  const cw = (w - 6) / labels.length;
  const thumb = h('div', null, root, {
    position: 'absolute', left: '3px', top: '3px', width: `${cw}px`, height: `${hh - 6}px`, borderRadius: `${(hh - 6) / 2}px`,
    background: '#fff', boxShadow: '0 2px 8px rgba(16,24,40,0.10)',
  });
  const items = labels.map((l, i) =>
    tx(root, 3 + i * cw, 0, l, { size: o.size || 14, weight: 600, w: cw, align: 'left', lh: hh / (o.size || 14) }));
  items.forEach((it, i) => {
    it.style.left = `${3 + i * cw}px`;
    it.style.textAlign = 'center';
  });
  return {
    root,
    set(pos) {
      thumb.style.transform = `translateX(${(pos * cw).toFixed(2)}px)`;
      items.forEach((it, i) => (it.style.color = mix(UI.ink2, UI.ink, clamp(1 - Math.abs(pos - i)))));
    },
  };
}

/** A decimal pad, three by four. Returns the keys by label and where their centres are. */
function keypad(parent, x, y, w) {
  const keys = {};
  const gap = 10;
  const kw = (w - 2 * gap) / 3;
  const kh = 62;
  ['123', '456', '789', [',', '0', '⌫']].forEach((row, r) => {
    [...row].forEach((k, c) => {
      const e = h('div', 'tnum', parent, {
        position: 'absolute', left: `${x + c * (kw + gap)}px`, top: `${y + r * (kh + gap)}px`, width: `${kw}px`, height: `${kh}px`,
        borderRadius: '18px', background: UI.fill, display: 'flex', alignItems: 'center', justifyContent: 'center',
        fontSize: '27px', fontWeight: 600, color: UI.ink,
      }, k);
      keys[k] = { el: e, x: x + c * (kw + gap) + kw / 2, y: y + r * (kh + gap) + kh / 2 };
    });
  });
  return keys;
}

// ---------------------------------------------------------------------------------------------
// The ring: category arcs around a centre, with round ends and a little air between them.
// ---------------------------------------------------------------------------------------------

function arcPath(cx, cy, r, a0, a1) {
  const x0 = cx + r * Math.sin(a0);
  const y0 = cy - r * Math.cos(a0);
  const x1 = cx + r * Math.sin(a1);
  const y1 = cy - r * Math.cos(a1);
  const large = a1 - a0 > Math.PI ? 1 : 0;
  return `M${x0.toFixed(2)},${y0.toFixed(2)} A${r.toFixed(2)},${r.toFixed(2)} 0 ${large} 1 ${x1.toFixed(2)},${y1.toFixed(2)}`;
}

/**
 * A ring drawn in an SVG `box` pixels square, centred on the box.
 * `draw(slices, o)`: slices are `{ color, value }`; `o.r`, `o.th` (thickness), `o.gap` (px),
 * `o.reveal` (0..1, all of it drawing clockwise), `o.each` (per-slice 0..1), `o.pop` (per-slice
 * extra radius), `o.alpha` (per-slice opacity), `o.rot` (radians).
 */
function makeRing(parent, box_) {
  const svg = s('svg', { width: box_, height: box_, viewBox: `0 0 ${box_} ${box_}` }, parent);
  Object.assign(svg.style, { position: 'absolute', left: '0', top: '0', overflow: 'visible' });
  const arcs = [];
  const c = box_ / 2;
  return {
    svg,
    box: box_,
    draw(slices, o) {
      while (arcs.length < slices.length) {
        arcs.push(s('path', { fill: 'none', 'stroke-linecap': 'round' }, svg));
      }
      const total = slices.reduce((a, sl) => a + sl.value, 0) || 1;
      const r = o.r;
      const th = o.th;
      const cap = th / 2 / r;
      const gapA = (o.gap === undefined ? 6 : o.gap) / r;
      let a = o.rot || 0;
      const sweepEnd = (o.rot || 0) + (o.reveal === undefined ? 1 : o.reveal) * TAU;
      arcs.forEach((arc, i) => {
        const sl = slices[i];
        if (!sl) {
          arc.style.display = 'none';
          return;
        }
        const span = (sl.value / total) * TAU;
        const a0 = a + cap + gapA / 2;
        let a1 = a + span - cap - gapA / 2;
        a1 = Math.min(a1, sweepEnd - cap);
        if (o.each) a1 = a0 + (a1 - a0) * clamp(o.each[i]);
        a += span;
        const vis = a1 > a0 + 0.0005 || (o.each && o.each[i] > 0 && a1 >= a0);
        arc.style.display = vis ? '' : 'none';
        if (!vis) return;
        const rr = r + ((o.pop && o.pop[i]) || 0);
        arc.setAttribute('d', arcPath(c, c, rr, a0, Math.max(a1, a0 + 0.0001)));
        arc.setAttribute('stroke', sl.color);
        arc.setAttribute('stroke-width', ((o.thEach && o.thEach[i]) || th).toFixed(2));
        arc.style.opacity = o.alpha ? o.alpha[i] : 1;
      });
    },
  };
}

// ---------------------------------------------------------------------------------------------
// The device.
// ---------------------------------------------------------------------------------------------

function statusBar(parent, color = UI.ink) {
  const bar = h('div', null, parent, { position: 'absolute', left: 0, top: 0, width: '400px', height: '44px', color, zIndex: 40 });
  tx(bar, 30, 14, '10:24', { size: 15, weight: 600, color, tnum: true });
  const icons = s('svg', { width: 64, height: 16, viewBox: '0 0 64 16' }, bar);
  Object.assign(icons.style, { position: 'absolute', right: '28px', top: '15px' });
  [[0, 11, 3, 4], [5, 8, 3, 7], [10, 5, 3, 10], [15, 2, 3, 13]].forEach(([x, y, w, hh]) =>
    s('rect', { x, y, width: w, height: hh, rx: 1, fill: color }, icons));
  s('path', { d: 'M24,6.5 a10,10 0 0,1 14,0 M26.6,9.6 a6,6 0 0,1 8.8,0 M31,13.4 L31,13.3', fill: 'none', stroke: color, 'stroke-width': 2, 'stroke-linecap': 'round' }, icons);
  s('rect', { x: 43, y: 3, width: 18, height: 10, rx: 3, fill: 'none', stroke: color, 'stroke-width': 1.3, opacity: 0.45 }, icons);
  s('rect', { x: 45, y: 5, width: 12, height: 6, rx: 1.5, fill: color }, icons);
  return bar;
}

function makePhone(parent) {
  const root = h('div', null, parent, { position: 'absolute', left: 0, top: 0, width: '420px', height: '888px', transformOrigin: '210px 444px' });
  const body = h('div', null, root, {
    position: 'absolute', inset: '0', borderRadius: '62px',
    background: 'linear-gradient(150deg, #3b3c41 0%, #16171a 22%, #0c0d0f 55%, #1a1b1e 85%, #34353a 100%)',
    boxShadow: 'inset 0 0 0 1.5px rgba(255,255,255,0.10), 0 60px 110px rgba(22,28,48,0.28), 0 18px 40px rgba(22,28,48,0.16)',
  });
  const screen = h('div', null, root, {
    position: 'absolute', left: '10px', top: '10px', width: '400px', height: '868px', borderRadius: '52px', overflow: 'hidden', background: UI.bg,
  });
  h('div', null, root, {
    position: 'absolute', left: '203px', top: '24px', width: '14px', height: '14px', borderRadius: '50%', background: '#050506',
    boxShadow: '0 0 0 2px #1a1b1e', zIndex: 60,
  });
  h('div', null, root, { position: 'absolute', left: '150px', bottom: '19px', width: '120px', height: '5px', borderRadius: '3px', background: 'rgba(14,17,23,0.85)', zIndex: 60 });
  return { root, body, screen };
}
