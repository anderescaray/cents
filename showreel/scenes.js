/*
 * Cents — motion reel. The scenes.
 *
 *   bar 1  b0–b4    Detect      a detector finds one cent, then every cent, and they become one
 *   bar 2  b4–b8    Canvas      the coin opens into the app's bubble launcher; a tap grows a bubble
 *   bar 3  b8–b12   Log it      the keypad: 42,80 € typed on the beat — "Log it once."
 *   bar 4  b12–b15  Everywhere  one movement lands in the balance, the ring and the budget at once
 *   bar 5  b15–b21  Reports     the ring card opens out; the ring unrolls, the strip stands up as bars
 *   bar 6+ b21–b26  Montage     five more features, one beat each
 *   bar 7  b26–b28  YOLO        "You Only Log Once."
 *   bar 8  b28–b32  Logo        the initials collapse into the coin; Cents, detected at 1.00
 *
 * Everything on screen is the app's own: its palette, its type, its icons, its strings, its
 * default bubble layout and the 340 ms curve its launcher opens with.
 */
'use strict';

const SCENES = [];
let STAGE;

function layer(name, t0, t1, build, cls = 'layer') {
  const root = h('div', cls, STAGE);
  root.dataset.name = name;
  const update = build(root);
  SCENES.push({ name, t0, t1, root, update });
}

function render(t) {
  for (const sc of SCENES) {
    const on = t >= sc.t0 && t < sc.t1;
    show(sc.root, on);
    if (on) sc.update(t);
  }
}

function addCSS(css) {
  h('style', null, document.head, null, css);
}

addCSS(`
  .coin { position:absolute; left:0; top:0; width:200px; height:200px; border-radius:50%;
    background: radial-gradient(circle at 34% 28%, #55aaff 0%, #0A84FF 44%, #0062d4 100%);
    box-shadow: inset 0 -10px 24px rgba(0,0,0,0.28), inset 0 7px 16px rgba(255,255,255,0.20);
    display:flex; align-items:center; justify-content:center; }
  .coin-glyph { font-size:132px; font-weight:700; color:#fff; line-height:1; margin-top:-4px; }
  .coin-flash { position:absolute; inset:0; border-radius:50%; background:#fff; }
  .coin-glint { position:absolute; left:70px; top:-60px; width:60px; height:320px; opacity:0;
    background: linear-gradient(90deg, rgba(255,255,255,0) 0%, rgba(255,255,255,0.55) 50%, rgba(255,255,255,0) 100%); }
  .ghost { position:absolute; left:0; top:0; width:200px; height:200px; border-radius:50%;
    border: 3px solid rgba(255,255,255,0.10); }

  .bubble { position:absolute; left:0; top:0; width:136px; height:136px; border-radius:50%; background:#fff;
    box-shadow: 0 9px 20px rgba(0,0,0,0.10), 0 2px 5px rgba(0,0,0,0.06);
    display:flex; flex-direction:column; align-items:center; justify-content:center; gap:6px; }
  .bubble-label { font-size:13px; line-height:18px; color:#000; }
  .touch { position:absolute; left:0; top:0; width:46px; height:46px; border-radius:50%;
    background: rgba(40,40,48,0.28); box-shadow: 0 0 0 2px rgba(255,255,255,0.75), 0 2px 10px rgba(0,0,0,0.25); }

  .seg { position:absolute; left:16px; width:368px; height:32px; border-radius:9px; background:rgba(118,118,128,0.12); }
  .seg-thumb { position:absolute; top:2px; height:28px; border-radius:7px; background:#fff;
    box-shadow: 0 3px 8px rgba(0,0,0,0.12), 0 3px 1px rgba(0,0,0,0.04); }
  .seg-item { position:absolute; top:0; height:32px; line-height:32px; text-align:center; font-size:13px; font-weight:600; }
  .key { position:absolute; width:122.67px; height:50px; border-radius:10px; background:#fff; text-align:center;
    line-height:50px; font-size:22px; font-weight:700; letter-spacing:-0.008em; box-shadow: 0 1px 0 rgba(0,0,0,0.08); }
`);

// The five sections, in the order of `TopLevelTab`, where `HomeCanvasGeometry.kt` puts them.
const TABS = [
  { key: 'transactions', label: 'Transactions', fx: 0.29, fy: 0.18 },
  { key: 'accounts', label: 'Accounts', fx: 0.71, fy: 0.34 },
  { key: 'investments', label: 'Investments', fx: 0.3, fy: 0.5 },
  { key: 'reports', label: 'Reports', fx: 0.7, fy: 0.66 },
  { key: 'settings', label: 'Settings', fx: 0.31, fy: 0.82 },
];
/** The canvas is the safe area: below the status bar, above the gesture pill. */
const CANVAS = { top: 36, height: 832, width: 400 };
const BUBBLE_D = 0.34 * 400;

// =============================================================================================
// Bar 1 — Detect.
// =============================================================================================

const S1 = { coinEnd: 220, camEnd: 1.1, wipe: 0.26, litAt: B(4) + 0.25 };

function coinEl(parent) {
  const c = h('div', 'coin', parent);
  h('div', 'coin-glyph', c, null, '¢');
  const flash = h('div', 'coin-flash', c);
  c.flash = flash;
  c.style.overflow = 'hidden';
  c.glint = h('div', 'coin-glint', c);
  return c;
}

function sceneDetect() {
  // Other coins, placed clear of the centre. [x, y, diameter]
  const SPOTS = [
    [250, 215, 58], [540, 140, 34], [800, 262, 44], [1185, 168, 52], [1500, 292, 70],
    [1768, 170, 38], [292, 566, 40], [1650, 608, 46], [178, 884, 50], [628, 860, 64],
    [1014, 936, 36], [1340, 818, 56], [1778, 904, 42],
  ];
  const confs = [0.94, 0.91, 0.97, 0.96, 0.98, 0.92, 0.95, 0.97, 0.93, 0.99, 0.9, 0.96, 0.94];
  const scanY = (t) => lerp(-90, 1170, prog(t, B(1), B(2)));
  const whenScanned = (y) => B(1) + ((y + 90) / 1260) * BEAT;
  const center = [W / 2, H / 2];
  const maxDist = Math.max(...SPOTS.map(([x, y]) => Math.hypot(x - center[0], y - center[1])));
  const others = SPOTS.map(([x, y, d], i) => {
    const dist = Math.hypot(x - center[0], y - center[1]);
    return {
      x, y, d, i, dist,
      seen: whenScanned(y - d / 2),
      arrive: B(2) + 0.2 + 0.26 * (dist / maxDist),
      conf: confs[i],
    };
  });
  others.forEach((c) => {
    cue(c.seen + 0.1, 'detect', { i: c.i, pan: (c.x - 960) / 960 });
    cue(c.arrive, 'absorb', { i: c.i });
  });
  const lastArrival = Math.max(...others.map((c) => c.arrive));
  cue(0.06, 'coin');
  cue(0.42, 'lock');
  cue(B(1), 'scan');
  cue(B(2), 'swoosh', { dir: 1 });
  cue(lastArrival, 'lock', { big: true });
  cue(B(3) + 0.05, 'flip');
  cue(B(4), 'burst');

  layer('detect', 0, B(4) + 0.34, (root) => {
    root.style.background = '#000';
    const cam = h('div', 'a', root, { width: `${W}px`, height: `${H}px`, transformOrigin: '960px 540px' });
    const glow = h('div', 'a', cam, {
      width: '1000px', height: '1000px', borderRadius: '50%',
      background: 'radial-gradient(circle, rgba(10,132,255,0.30) 0%, rgba(10,132,255,0.10) 32%, rgba(10,132,255,0) 68%)',
    });
    const grid = h('div', 'a', cam, {
      left: '-200px', top: '-200px', width: `${W + 400}px`, height: `${H + 400}px`,
      backgroundImage: 'radial-gradient(circle, rgba(255,255,255,0.16) 1.4px, transparent 1.6px)',
      backgroundSize: '48px 48px', backgroundPosition: '0 0',
    });
    const ghosts = others.map(() => h('div', 'ghost', cam));
    const coins = others.map(() => coinEl(cam));
    const boxes = others.map(() => new DetBox(cam, { tagSize: 15, thick: 3 }));
    const main = coinEl(cam);
    const wipe = h('div', 'a', cam, { width: '200px', height: '200px', borderRadius: '50%' });
    const ring = h('div', 'a', cam, { width: '200px', height: '200px', borderRadius: '50%', border: '4px solid #fff' });
    const mainBox = new DetBox(cam, { tagSize: 22, thick: 4 });
    const scan = h('div', 'a', cam, { width: `${W + 400}px`, height: '160px', left: '-200px' });
    scan.innerHTML =
      '<div style="position:absolute;left:0;right:0;bottom:0;height:160px;background:linear-gradient(to bottom, rgba(255,214,10,0) 0%, rgba(255,214,10,0.10) 80%, rgba(255,214,10,0.22) 100%)"></div>' +
      '<div style="position:absolute;left:0;right:0;bottom:0;height:3px;background:#FFD60A;box-shadow:0 0 18px 3px rgba(255,214,10,0.7)"></div>';

    return (t) => {
      const push = 1 + (S1.camEnd - 1) * ease.inOutQuad(prog(t, 0, B(4)));
      cam.style.transform = `scale(${push.toFixed(5)})`;

      // The scanner and the grid it leaves behind it.
      const sy = scanY(t);
      const scanOn = prog(t, B(1) - 0.06, B(1)) * (1 - prog(t, B(2) - 0.02, B(2) + 0.06));
      show(scan, scanOn > 0);
      placeTL(scan, 0, sy - 160, { o: scanOn });
      const gridO = 0.8 * prog(t, B(1), B(1) + 0.1) * (1 - prog(t, B(2), B(2) + 0.35));
      show(grid, gridO > 0);
      if (gridO > 0) {
        const edge = sy + 200;
        const m = `linear-gradient(to bottom, rgba(0,0,0,0) ${edge - 520}px, rgba(0,0,0,1) ${edge - 60}px, rgba(0,0,0,1) ${edge - 4}px, rgba(0,0,0,0) ${edge}px)`;
        grid.style.webkitMaskImage = m;
        grid.style.maskImage = m;
        grid.style.opacity = gridO;
      }

      // The first cent.
      let d = 84;
      others.forEach((c) => {
        d += ((S1.coinEnd - 84) / others.length) * spring(t - c.arrive, 3.6, 0.42);
      });
      const pop = spring(t - 0.06, 3.0, 0.42);
      const flip = TAU * ease.inOutCubic(prog(t, B(3) + 0.03, B(3) + 0.47));
      const flat = Math.max(0.035, Math.abs(Math.cos(flip)));
      const bursting = prog(t, B(4), B(4) + S1.wipe);
      const mainScale = (d / 200) * pop;
      show(main, bursting < 0.3);
      place(main, 960, 540, { sx: mainScale * flat, sy: mainScale, o: 1 - prog(t, B(4) + 0.03, B(4) + 0.09) });
      main.flash.style.opacity = (0.9 * (1 - prog(t, 0.06, 0.24))).toFixed(3);
      place(glow, 960, 540, { s: (d / 220) * pop * (1 + bursting * 3), o: 0.9 * (1 - bursting) });

      // The coin opens into the app's canvas: blue, then the grouped background.
      show(wipe, bursting > 0);
      show(ring, bursting > 0 && bursting < 1);
      if (bursting > 0) {
        const wd = lerp(d, 2500 / S1.camEnd, ease.inCubic(bursting));
        wipe.style.background = mix(C.blueD, C.bgLight, ease.inOutQuad(prog(t, B(4) + 0.04, B(4) + 0.24)));
        place(wipe, 960, 540, { s: wd / 200 });
        const rd = wd * 1.1 + 36;
        ring.style.width = `${rd}px`;
        ring.style.height = `${rd}px`;
        ring.style.borderWidth = `${lerp(5, 2, bursting)}px`;
        place(ring, 960, 540, { o: 0.8 * (1 - ease.inQuad(bursting)) });
      }

      const lockIn = ease.swift(prog(t, 0.13, 0.5));
      const renamed = t >= lastArrival;
      const conf = renamed
        ? lerp(0.61, 0.99, ease.outCubic(prog(t, lastArrival, lastArrival + 0.3)))
        : lerp(0, 0.99, ease.outCubic(prog(t, 0.3, 0.62)));
      const size = d * pop * flat + 30;
      mainBox.set({
        x: 960, y: 540, w: size, h: d * pop + 30,
        spread: 150 * (1 - lockIn) + 1400 * ease.inExpo(bursting) + 10 * Math.exp(-(t - lastArrival) * 18) * (t > lastArrival ? 1 : 0),
        o: prog(t, 0.13, 0.2) * (1 - prog(t, B(4) + 0.08, B(4) + 0.22)),
        tag: prog(t, 0.34, 0.5) * (1 - bursting),
        conf,
        label: renamed ? 'cents' : 'cent',
      });

      // Every other cent: dark until the scanner passes, then found, then pulled in.
      others.forEach((c, i) => {
        const coin = coins[i];
        const ghost = ghosts[i];
        const seen = t >= c.seen;
        const u = prog(t, B(2), c.arrive);
        const pull = ease.inCubic(u);
        const ang = 0.7 * pull;
        const dx = (c.x - 960) * (1 - pull);
        const dy = (c.y - 540) * (1 - pull);
        const x = 960 + dx * Math.cos(ang) - dy * Math.sin(ang);
        const y = 540 + dx * Math.sin(ang) + dy * Math.cos(ang);
        const ghostO = 0.9 * prog(t, 0.25, 0.6) * (seen ? 0 : 1);
        show(ghost, ghostO > 0);
        if (ghostO > 0) place(ghost, c.x, c.y, { s: c.d / 200, o: ghostO });
        const gone = t >= c.arrive;
        show(coin, seen && !gone);
        if (seen && !gone) {
          const sc = spring(t - c.seen, 3.4, 0.5);
          place(coin, x, y, { s: (c.d / 200) * sc * (1 - 0.45 * pull), o: 1 });
          coin.flash.style.opacity = (0.95 * (1 - prog(t, c.seen, c.seen + 0.16))).toFixed(3);
        }
        const bo = (seen ? prog(t, c.seen, c.seen + 0.05) : 0) * (1 - prog(u, 0, 0.3));
        const cd = c.d * (1 - 0.45 * pull) + 16;
        boxes[i].set({
          x, y, w: cd, h: cd,
          spread: 70 * (1 - ease.swift(prog(t, c.seen, c.seen + 0.2))) + 26 * pull,
          o: bo,
          tag: prog(t, c.seen + 0.07, c.seen + 0.19),
          conf: lerp(0.3, c.conf, ease.outCubic(prog(t, c.seen + 0.04, c.seen + 0.26))),
          arm: Math.max(9, cd * 0.28),
        });
      });
    };
  });
}

// =============================================================================================
// The phone. One device carried from the launcher (bar 2) to the everywhere shot (bar 4).
// =============================================================================================

const PHONE = {};

function statusBar(parent, color) {
  const bar = h('div', 'statusbar', parent, { color });
  h('div', 'time tnum', bar, null, '10:24');
  const icons = s('svg', { width: 64, height: 16, viewBox: '0 0 64 16' }, bar);
  Object.assign(icons.style, { position: 'absolute', right: '26px', top: '14px' });
  // Signal, Wi-Fi, battery — drawn plainly, as Android draws them.
  [[0, 11, 3, 4], [5, 8, 3, 7], [10, 5, 3, 10], [15, 2, 3, 13]].forEach(([x, y, w, hh]) =>
    s('rect', { x, y, width: w, height: hh, rx: 1, fill: color }, icons));
  s('path', { d: 'M24,6.5 a10,10 0 0,1 14,0 M26.6,9.6 a6,6 0 0,1 8.8,0 M31,14 L31,13.9', fill: 'none', stroke: color, 'stroke-width': 2, 'stroke-linecap': 'round' }, icons);
  s('rect', { x: 43, y: 3, width: 18, height: 10, rx: 3, fill: 'none', stroke: color, 'stroke-width': 1.3, opacity: 0.5 }, icons);
  s('rect', { x: 45, y: 5, width: 11, height: 6, rx: 1.5, fill: color }, icons);
  s('rect', { x: 62, y: 6, width: 1.6, height: 4, rx: 0.8, fill: color, opacity: 0.5 }, icons);
  return bar;
}

function buildPhone(parent) {
  const phone = h('div', 'a', parent, { width: '424px', height: '912px' });
  const body = h('div', 'phone', phone);
  const screen = h('div', 'a', phone, {
    left: '12px', top: '12px', width: '400px', height: '888px', borderRadius: '54px', overflow: 'hidden',
    background: C.bgLight, color: '#000',
  });
  h('div', 'punch', screen);
  h('div', 'gesture', screen);
  Object.assign(PHONE, { phone, body, screen });
  return PHONE;
}

/** The launcher: five white bubbles on the grouped background. */
function buildHome(screen) {
  const home = h('div', 'a', screen, { width: '400px', height: '888px', background: C.bgLight });
  statusBar(home, '#000');
  const bubbles = TABS.map((tab) => {
    const el = h('div', 'bubble', home);
    const ic = icon(tab.key, el, 30, C.blue);
    const label = h('div', 'bubble-label', el, null, tab.label);
    return {
      ...tab, el, ic, label,
      x: tab.fx * CANVAS.width,
      y: CANVAS.top + tab.fy * CANVAS.height,
    };
  });
  return { home, bubbles };
}

function row(card, y, { title, sub, value, valueColor, height = 64, sep = true }) {
  const r = h('div', 'row', card, { top: `${y}px`, height: `${height}px` });
  const two = sub != null;
  const t = h('div', 't', r, { top: two ? '11px' : '11px', lineHeight: '22px' }, title);
  const st = two ? h('div', 'st', r, { top: '33px', lineHeight: '20px' }, sub) : null;
  const v = value != null ? h('div', 'v', r, { top: two ? '21px' : '11px', lineHeight: '22px', color: valueColor || '#000' }, value) : null;
  const sp = sep ? h('div', 'sep', r) : null;
  return { r, t, st, v, sp };
}

/** The Transactions tab, where the launcher's first bubble leads. */
function buildTransactions(screen) {
  const root = h('div', 'a', screen, { width: '400px', height: '888px', background: C.bgLight });
  statusBar(root, '#000');
  // Navigation: filters on the left, add on the right, the large title below.
  const filters = s('svg', { width: 26, height: 26, viewBox: '0 0 24 24' }, root);
  Object.assign(filters.style, { position: 'absolute', left: '16px', top: '50px' });
  ['M4,7 L20,7', 'M7,12 L17,12', 'M10,17 L14,17'].forEach((d) =>
    s('path', { d, stroke: C.blue, 'stroke-width': 2.2, 'stroke-linecap': 'round', fill: 'none' }, filters));
  const plus = s('svg', { width: 26, height: 26, viewBox: '0 0 24 24' }, root);
  Object.assign(plus.style, { position: 'absolute', right: '16px', top: '50px' });
  s('path', { d: 'M12,4 L12,20 M4,12 L20,12', stroke: C.blue, 'stroke-width': 2.4, 'stroke-linecap': 'round', fill: 'none' }, plus);
  h('div', 'large-title', root, { top: '86px' }, 'Transactions');

  h('div', 'sec-h', root, { top: '146px' }, 'THIS MONTH');
  const sumCard = h('div', 'card', root, { top: '170px', height: '132px' });
  row(sumCard, 0, { title: 'Income', value: eur(240000), height: 44 });
  const expenses = row(sumCard, 44, { title: 'Expenses', value: eur(124170), height: 44 });
  const net = row(sumCard, 88, { title: 'Net', value: eur(115830, { sign: true }), height: 44, sep: false });

  const todayH = h('div', 'sec-h', root, { top: '334px' }, 'TODAY');
  const today = h('div', 'card', root, { top: '358px', height: '128px' });
  const fresh = row(today, 0, { title: 'Groceries', sub: 'Main account', value: eur(-4280) });
  const coffee = row(today, 0, { title: 'Coffee', sub: 'Flat white', value: eur(-320) });
  const netflix = row(today, 64, { title: 'Netflix', sub: 'Subscriptions · Main account', value: eur(-1299), sep: false });
  fresh.r.style.overflow = 'hidden';
  const yH = h('div', 'sec-h', root, { top: '522px' }, 'YESTERDAY');
  const yCard = h('div', 'card', root, { top: '546px', height: '128px' });
  row(yCard, 0, { title: 'Salary', sub: 'Main account', value: eur(240000, { sign: true }), valueColor: C.green });
  row(yCard, 64, { title: 'Pharmacy', sub: 'Main account', value: eur(-845), sep: false });

  // The tab bar, Transactions selected.
  const bar = h('div', 'tabbar', root);
  TABS.forEach((tab, i) => {
    const cell = h('div', 'tab', bar, { left: `${i * 80}px`, color: i === 0 ? C.blue : '#8E8E93' });
    icon(tab.key, cell, 26, i === 0 ? C.blue : '#8E8E93');
    h('div', null, cell, null, tab.label);
  });
  return { root, expenses, net, todayH, today, fresh, coffee, netflix, yH, yCard };
}

/** The entry form, as a sheet: amount, direction, account, category, and the keypad. */
function buildSheet(screen) {
  const dim = h('div', 'a', screen, { width: '400px', height: '888px', background: '#000' });
  const sheet = h('div', 'a', screen, {
    width: '400px', height: '840px', top: '48px', background: C.bgLight, borderRadius: '14px 14px 0 0',
    overflow: 'hidden', boxShadow: '0 -4px 30px rgba(0,0,0,0.12)',
  });
  h('div', 'a', sheet, { left: '182px', top: '6px', width: '36px', height: '5px', borderRadius: '3px', background: '#C7C7CC' });
  h('div', 'a', sheet, { left: '16px', top: '24px', fontSize: '17px', color: C.blue }, 'Cancel');
  h('div', 'a', sheet, { left: '0', width: '400px', top: '24px', fontSize: '17px', fontWeight: 600, textAlign: 'center' }, 'New transaction');
  const save = h('div', 'a', sheet, { right: '16px', left: 'auto', top: '24px', fontSize: '17px', fontWeight: 600, color: C.blue }, 'Save');

  // The amount, one span per character so each can land on its own beat.
  const amount = h('div', 'a tnum', sheet, {
    left: '0', width: '400px', top: '82px', height: '70px', textAlign: 'center', fontSize: '56px', fontWeight: 700,
    letterSpacing: '-0.02em', lineHeight: '70px', whiteSpace: 'nowrap',
  });
  const zero = h('span', null, amount, { color: 'rgba(60,60,67,0.3)' }, '0');
  const typed = [...'42,80'].map((ch) => h('span', null, amount, { display: 'inline-block' }, ch));
  const euro = h('span', null, amount, { color: 'rgba(60,60,67,0.6)', fontWeight: 600, fontSize: '40px' }, ' €');

  const seg = h('div', 'seg', sheet, { top: '176px' });
  const thumb = h('div', 'seg-thumb', seg, { left: '2px', width: '121px' });
  ['Expense', 'Income', 'Transfer'].forEach((label, i) =>
    h('div', 'seg-item', seg, { left: `${i * 122.67}px`, width: '122.67px' }, label));

  const fields = h('div', 'card', sheet, { top: '232px', height: '132px' });
  const account = row(fields, 0, { title: 'Account', value: 'Main account', height: 44 });
  const category = row(fields, 44, { title: 'Category', value: 'Groceries', height: 44 });
  const dot = h('span', null, null, { display: 'inline-block', width: '10px', height: '10px', borderRadius: '50%', background: C.green, marginRight: '8px', verticalAlign: '1px' });
  category.v.prepend(dot);
  row(fields, 88, { title: 'Date', value: 'Today', height: 44, sep: false });
  [account, category].forEach((r) => (r.v.style.color = 'rgba(60,60,67,0.6)'));
  const noteCard = h('div', 'card', sheet, { top: '388px', height: '44px' });
  h('div', 'a', noteCard, { left: '16px', top: '11px', fontSize: '17px', color: 'rgba(60,60,67,0.3)' }, 'Note');

  // The keypad: the even three-by-four grid of the iOS decimal pad (CentsKeypad.kt).
  const pad = h('div', 'a', sheet, { top: '580px', width: '400px', height: '260px', background: C.bar, borderTop: '0.5px solid rgba(60,60,67,0.3)' });
  const keys = {};
  ['123', '456', '789', [',', '0', '⌫']].forEach((rowKeys, r) => {
    [...rowKeys].forEach((k, c) => {
      const key = h('div', 'key', pad, { left: `${8 + c * (122.67 + 8)}px`, top: `${8 + r * 58}px` }, k);
      keys[k] = { el: key, x: 8 + c * 130.67 + 61.33, y: 580 + 8 + r * 58 + 25 };
    });
  });
  return { dim, sheet, save, amount, zero, typed, euro, keys };
}

// Bar 3's keys, on the off-beats: 4, 2, the decimal key, 8, 0.
const PRESSES = [
  { k: '4', t: B(8.5) },
  { k: '2', t: B(9) },
  { k: ',', t: B(9.5) },
  { k: '8', t: B(10) },
  { k: '0', t: B(10.5) },
];
const T_EXPAND = B(7);
const T_SAVE = B(11);

/** Where the phone stands, shot by shot. [x, y, scale, rotateX, rotateY] */
function phonePose(t) {
  const zoom = kf(t, [
    [B(4.7), 2.4],
    [B(6), 1.0, ease.inOutQuart],
    [B(7.2), 1.03, ease.smooth],
  ]);
  const pose = kf(t, [
    [B(6), [960, 540, 1, 0, 0]],
    [B(7.3), [1000, 540, 1, 2, -7], ease.smooth],
    [B(8.35), [1290, 548, 0.97, 3, -16], ease.smooth],
    [B(11.55), [1262, 548, 1.02, 2, -11], ease.linear],
    [B(12.45), [468, 572, 0.79, 3, 13], ease.inOutCubic],
    [T_REPORT, [484, 569, 0.8, 2, 10.5], ease.linear],
    [T_REPORT + 0.6, [380, 600, 0.68, 2, 18], ease.inCubic],
  ]);
  return { x: pose[0], y: pose[1], s: pose[2] * zoom, rx: pose[3], ry: pose[4] };
}

/** A soft light behind the phone, so the device sits in a space rather than on a void. */
function sceneBackdrop() {
  layer('backdrop', B(5.2), B(16.7), (root) => {
    const glow = h('div', 'a', root, {
      width: '1600px', height: '1600px', borderRadius: '50%',
      background: 'radial-gradient(circle, rgba(10,132,255,0.20) 0%, rgba(10,132,255,0.09) 30%, rgba(10,132,255,0.03) 52%, rgba(10,132,255,0) 70%)',
    });
    return (t) => {
      const p = phonePose(t);
      place(glow, p.x, p.y, { s: 0.75 + 0.25 * clamp(p.s), o: prog(t, B(5.2), B(6)) * (1 - prog(t, T_REPORT + 0.3, T_REPORT + 0.7)) });
    };
  });
}

function scenePhone() {
  const bubbleOrder = [2, 1, 3, 0, 4]; // nearest the burst first
  const burstAt = (i) => B(4) + 0.15 + bubbleOrder.indexOf(i) * 0.058;
  TABS.forEach((_, i) => cue(burstAt(i), 'bubble', { i: bubbleOrder.indexOf(i) }));
  cue(B(4.7), 'pullback');
  cue(B(6.75), 'tap');
  cue(T_EXPAND, 'expand');
  cue(B(7.85), 'sheet');
  PRESSES.forEach((p, i) => cue(p.t, 'key', { i, k: p.k }));
  cue(T_SAVE, 'tap', { save: true });
  cue(B(11.35), 'insert');

  layer('phone', B(4), B(16.7), (root) => {
    // The light canvas outside the device while the camera is inside it.
    const outside = h('div', 'a', root, { background: C.bgLight });
    const { phone, body, screen } = buildPhone(root);
    const home = buildHome(screen);
    const grow = h('div', 'a', screen);
    const tx = buildTransactions(screen);
    const sh = buildSheet(screen);
    const touch = h('div', 'touch', screen);
    PHONE.tx = tx;

    return (t) => {
      const p = phonePose(t);
      phone.style.transform =
        `translate(${(p.x - 212).toFixed(3)}px,${(p.y - 456).toFixed(3)}px) rotateX(${p.rx.toFixed(3)}deg) rotateY(${p.ry.toFixed(3)}deg) scale(${p.s.toFixed(5)})`;
      phone.style.transformOrigin = '212px 456px';

      // Bar 2: the iris closes from the whole frame onto the screen, and the body appears.
      const iris = 1 - ease.inOutQuart(prog(t, B(4.7), B(6)));
      const bodyO = prog(t, B(5.3), B(6));
      const lit = t >= S1.litAt;
      screen.style.background = lit ? C.bgLight : 'transparent';
      home.home.style.background = lit ? C.bgLight : 'transparent';
      show(outside, lit && iris > 0.0005);
      if (lit && iris > 0.0005) {
        const sw = 400 * p.s;
        const shh = 888 * p.s;
        const m = 1600 * iris;
        placeTL(outside, p.x - sw / 2 - m, p.y - shh / 2 - m);
        outside.style.width = `${sw + 2 * m}px`;
        outside.style.height = `${shh + 2 * m}px`;
        outside.style.borderRadius = `${54 * p.s + Math.min(m, 300)}px`;
      }
      body.style.opacity = bodyO.toFixed(3);

      // The launcher: bubbles burst out of where the coin was, then drift as they do in the app.
      const burstOrigin = [200, 444];
      home.bubbles.forEach((b, i) => {
        const t0 = burstAt(i);
        const k = spring(t - t0, 2.5, 0.62);
        const sc = 0.15 + 0.85 * spring(t - t0, 3.1, 0.5);
        const driftA = ((t - B(4)) / (5.2 + i * 0.9)) * TAU + i;
        const x = lerp(burstOrigin[0], b.x, k) + Math.cos(driftA) * 3;
        const y = lerp(burstOrigin[1], b.y, k) + Math.sin(driftA) * 5;
        const pressed = i === 0 ? 1 - 0.06 * ease.outCubic(prog(t, B(6.72), B(6.86))) * (1 - prog(t, T_EXPAND, T_EXPAND + 0.05)) : 1;
        show(b.el, t >= t0);
        place(b.el, x, y, { s: sc * pressed });
        b.ic.draw(prog(t, t0 + 0.1, t0 + 0.5));
        opacity(b.label, prog(t, t0 + 0.16, t0 + 0.36));
        b.label.style.transform = `translateY(${(6 * (1 - ease.swift(prog(t, t0 + 0.16, t0 + 0.5)))).toFixed(2)}px)`;
        if (i === 0) {
          b.ic.root.style.opacity = 1 - prog(t, T_EXPAND, T_EXPAND + 0.06);
          b.label.style.opacity = Math.min(prog(t, t0 + 0.16, t0 + 0.36), 1 - prog(t, T_EXPAND, T_EXPAND + 0.06));
        }
      });

      // The tap grows the bubble into the section: 340 ms on the app's own curve, then the
      // section fades in over it for 140 ms (HomeScreen.kt, EXPAND_DURATION_MS).
      const g = ease.standard(prog(t, T_EXPAND, T_EXPAND + 0.34));
      show(grow, t >= T_EXPAND && t < T_EXPAND + 0.6);
      if (t >= T_EXPAND) {
        const b0 = home.bubbles[0];
        const gw = lerp(BUBBLE_D * 0.94, 400, g);
        const gh = lerp(BUBBLE_D * 0.94, CANVAS.height, g);
        const cx = lerp(b0.x, 200, g);
        const cy = lerp(b0.y, CANVAS.top + CANVAS.height / 2, g);
        Object.assign(grow.style, {
          width: `${gw}px`, height: `${gh}px`, borderRadius: `${lerp(BUBBLE_D * 0.47, 0, g)}px`,
          background: mix('#FFFFFF', C.bgLight, g),
          boxShadow: `0 ${9 * (1 - g)}px ${20 * (1 - g)}px rgba(0,0,0,${0.1 * (1 - g)})`,
        });
        placeTL(grow, cx - gw / 2, cy - gh / 2);
      }
      const txO = prog(t, T_EXPAND + 0.22, T_EXPAND + 0.36);
      show(tx.root, txO > 0);
      opacity(tx.root, txO);
      show(home.home, txO < 1);

      // The sheet rises; the keys are pressed on the off-beats; Save sends it back down.
      const up = spring(t - B(7.85), 2.3, 0.86);
      const down = ease.inCubic(prog(t, T_SAVE + 0.1, T_SAVE + 0.42));
      const sheetY = lerp(860, 0, up) + 880 * down;
      const sheetOn = t >= B(7.85) && down < 1;
      show(sh.sheet, sheetOn);
      show(sh.dim, sheetOn);
      if (sheetOn) {
        placeTL(sh.sheet, 0, sheetY);
        opacity(sh.dim, 0.3 * clamp(up) * (1 - down));
      }
      let typed = 0;
      PRESSES.forEach((pr, i) => {
        const key = sh.keys[pr.k];
        const pressed = prog(t, pr.t - 0.035, pr.t) * (1 - prog(t, pr.t + 0.09, pr.t + 0.24));
        key.el.style.background = mix('#FFFFFF', C.pressed, pressed);
        if (t >= pr.t) typed = i + 1;
        const ch = sh.typed[i];
        show(ch, t >= pr.t);
        if (t >= pr.t) {
          const k = spring(t - pr.t, 3.4, 0.55);
          ch.style.transform = `translateY(${(22 * (1 - k)).toFixed(2)}px) scale(${(0.55 + 0.45 * k).toFixed(4)})`;
          ch.style.opacity = clamp(k * 2).toFixed(3);
        }
      });
      show(sh.zero, typed === 0);

      // A finger, drawn the way Android's "show taps" draws one.
      let touchAt = null;
      let touchO = 0;
      const tapAt = (x, y, t0, t1) => {
        const o = prog(t, t0 - 0.07, t0) * (1 - prog(t, t1, t1 + 0.12));
        if (o > touchO) {
          touchO = o;
          touchAt = [x, y, t0];
        }
      };
      tapAt(home.bubbles[0].x, home.bubbles[0].y, B(6.75), T_EXPAND);
      PRESSES.forEach((pr) => {
        const k = sh.keys[pr.k];
        tapAt(k.x, 48 + sheetY + k.y, pr.t, pr.t + 0.08);
      });
      tapAt(356, 48 + sheetY + 36, T_SAVE, T_SAVE + 0.06);
      show(touch, touchO > 0);
      if (touchO > 0) place(touch, touchAt[0], touchAt[1], { s: 0.8 + 0.2 * spring(t - touchAt[2] + 0.07, 4, 0.6), o: touchO });

      // The new movement lands at the top of Today; the month's totals follow it.
      const ins = spring(t - B(11.35), 2.6, 0.72);
      const inserted = t >= B(11.35);
      const rowH = 64 * clamp(ins, 0, 1.2);
      tx.today.style.height = `${128 + rowH}px`;
      placeTL(tx.coffee.r, 0, rowH);
      placeTL(tx.netflix.r, 0, rowH);
      tx.fresh.r.style.height = `${Math.max(0, rowH)}px`;
      tx.fresh.r.style.background = mix('#DCEBFF', '#FFFFFF', prog(t, B(11.9), B(12.6)));
      opacity(tx.fresh.r, inserted ? prog(t, B(11.35), B(11.5)) : 0);
      placeTL(tx.yH, 0, rowH);
      placeTL(tx.yCard, 0, rowH);
      const spent = lerp(124170, 128450, ease.outCubic(prog(t, B(11.45), B(12.2))));
      tx.expenses.v.textContent = eur(spent);
      tx.net.v.textContent = eur(240000 - spent, { sign: true });
    };
  }, 'layer3d');
}

// =============================================================================================
// Bar 3 — "Log it once."
// =============================================================================================

function sceneLogIt() {
  cue(B(8), 'word', { w: 'Log it' });
  cue(B(10.5), 'word', { w: 'once.' });
  layer('logit', B(7.9), B(12.7), (root) => {
    const logIt = kinetic(root, 'Log it', '', { fontSize: '196px', fontWeight: 700, letterSpacing: '-0.045em', lineHeight: '1', color: '#fff' });
    const once = kinetic(root, 'once.', '', { fontSize: '196px', fontWeight: 700, letterSpacing: '-0.045em', lineHeight: '1', color: C.blueD });
    placeTL(logIt.root, 150, 318);
    placeTL(once.root, 150, 520);
    return (t) => {
      riseLetters(logIt, t, B(8), { stagger: 0.03, dur: 0.55 });
      riseLetters(once, t, B(10.5) - 0.05, { stagger: 0.03, dur: 0.55 });
      // The phone swings across and the words are pushed off ahead of it.
      const drift = tw(t, B(8), B(12), 0, -36, ease.linear);
      const pushOnce = 1400 * ease.inQuart(prog(t, B(11.72), B(11.72) + 0.36));
      const pushLog = 1400 * ease.inQuart(prog(t, B(11.84), B(11.84) + 0.36));
      placeTL(logIt.root, 150 + drift - pushLog, 318);
      placeTL(once.root, 150 + drift * 1.4 - pushOnce, 520);
    };
  });
}

// =============================================================================================
// Bar 4 — "See it everywhere." One movement, and every screen that reads it moves with it.
// =============================================================================================

addCSS(`
  .rcard { position:absolute; left:0; top:0; background:#fff; color:#000; border-radius:28px; overflow:hidden;
    box-shadow: 0 40px 90px rgba(0,0,0,0.5), 0 10px 28px rgba(0,0,0,0.3); transform-origin: 50% 50%; }
  .eyebrow { position:absolute; font-size:14px; font-weight:600; letter-spacing:0.07em; color:rgba(60,60,67,0.6); white-space:nowrap; }
  .odo { display:inline-block; white-space:nowrap; font-feature-settings:'tnum' 1; vertical-align:top; }
  .odo-col { display:inline-block; overflow:hidden; vertical-align:top; position:relative; }
  .odo-strip { display:block; }
  .odo-strip span { display:block; text-align:center; }
  .o0 { transform-origin: 0 0; }
`);

// This month's spending, by category, in cents: before and after the 42,80 € of groceries.
const SPEND = [
  { name: 'Groceries', color: C.green, before: 30000, after: 34280 },
  { name: 'Home', color: C.blue, before: 32450, after: 32450 },
  { name: 'Restaurants', color: C.orange, before: 21460, after: 21460 },
  { name: 'Shopping', color: C.pink, before: 17633, after: 17633 },
  { name: 'Transport', color: C.teal, before: 12840, after: 12840 },
  { name: 'Subscriptions', color: C.purple, before: 9787, after: 9787 },
];
const TOTAL_BEFORE = 124170;
const TOTAL_AFTER = 128450;
/** The app writes shares as `(share * 100).toInt()` — truncated, so they need not sum to 100. */
const pct = (amount, total) => Math.floor((amount / total) * 100);

const EV = {
  A: { x: 880, y: 262, w: 440, h: 250 },
  C: { x: 880, y: 542, w: 440, h: 270 },
  Bc: { x: 1350, y: 262, w: 450, h: 550 },
};
const HITS = { A: B(13), C: B(13.5), B: B(14) };
const T_REPORT = B(15);

/** A little kick that dies away: 0 before `dt`, a peak near 60 ms, gone by 400 ms. */
const bump = (dt) => (dt <= 0 ? 0 : Math.exp(-dt * 9) * Math.sin(dt * 22));
function cardEnter(t, t0) {
  const k = spring(t - t0, 2.3, 0.72);
  return { k, s: lerp(0.88, 1, k), dy: lerp(80, 0, k), o: clamp(k * 3.2) };
}

/** Digits on drums. `setOdometer` rolls each drum down to its new digit, left to right. */
function odometer(parent, text, lh) {
  const root = h('span', 'odo', parent, { height: `${lh}px`, lineHeight: `${lh}px` });
  const cols = [];
  for (const ch of text) {
    if (/\d/.test(ch)) {
      const col = h('span', 'odo-col', root, { height: `${lh}px` });
      const strip = h('span', 'odo-strip', col);
      for (let k = -30; k <= 9; k++) h('span', null, strip, { height: `${lh}px` }, String(((k % 10) + 10) % 10));
      cols.push({ col, strip });
    } else {
      h('span', null, root, null, ch);
    }
  }
  return { root, cols, lh };
}
function setOdometer(odo, from, to, t, t0, { dur = 0.62, stagger = 0.045, spins = [] } = {}) {
  const a = [...from].filter((c) => /\d/.test(c)).map(Number);
  const b = [...to].filter((c) => /\d/.test(c)).map(Number);
  odo.cols.forEach((c, i) => {
    const steps = (((a[i] - b[i]) % 10) + 10) % 10 + 10 * (spins[i] || 0);
    const p = ease.swift(prog(t, t0 + i * stagger, t0 + i * stagger + dur));
    const k = a[i] - steps * p;
    c.strip.style.transform = `translateY(${(-(k + 30) * odo.lh).toFixed(2)}px)`;
  });
}

/** Where the element sits on the stage, whatever the page is scaled to. */
function stageRect(el) {
  const r = el.getBoundingClientRect();
  const sr = STAGE.getBoundingClientRect();
  const k = sr.width / W;
  return { left: (r.left - sr.left) / k, right: (r.right - sr.left) / k, top: (r.top - sr.top) / k, bottom: (r.bottom - sr.top) / k };
}

function sceneEverywhere() {
  cue(B(12.3), 'word', { w: 'See it everywhere.' });
  [HITS.A, HITS.C, HITS.B].forEach((hit, i) => {
    cue(hit - 0.27, 'wire', { i });
    cue(hit, 'hit', { i });
  });
  cue(HITS.A + 0.05, 'roll');

  layer('everywhere', B(11.9), B(16.7), (root) => {
    const caption = kinetic(root, 'See it everywhere.', '', {
      fontSize: '88px', fontWeight: 700, letterSpacing: '-0.035em', lineHeight: '1', color: '#fff',
    });

    // Card A: the account the money left.
    const A = h('div', 'rcard', root, { width: `${EV.A.w}px`, height: `${EV.A.h}px` });
    h('div', 'eyebrow', A, { left: '30px', top: '28px' }, 'ACCOUNTS');
    h('div', 'a', A, { left: '30px', top: '54px', fontSize: '28px', fontWeight: 600, letterSpacing: '-0.012em' }, 'Main account');
    const amountLine = h('div', 'a', A, { left: '27px', top: '118px', fontSize: '66px', fontWeight: 700, letterSpacing: '-0.025em', whiteSpace: 'nowrap' });
    const odo = odometer(amountLine, '2.431,20', 80);
    h('span', null, amountLine, { fontSize: '44px', color: 'rgba(60,60,67,0.6)', fontWeight: 600, verticalAlign: 'top', lineHeight: '88px' }, ' €');
    const chip = h('div', 'a tnum', A, {
      left: '30px', top: '196px', padding: '5px 12px', borderRadius: '10px', fontSize: '20px', fontWeight: 600,
      color: C.red, background: 'rgba(255,59,48,0.12)', whiteSpace: 'nowrap',
    }, `Groceries  ${eur(-4280)}`);

    // Card C: the budget the money counted against — amber from 80 % (D26).
    const Cc = h('div', 'rcard', root, { width: `${EV.C.w}px`, height: `${EV.C.h}px` });
    h('div', 'eyebrow', Cc, { left: '30px', top: '28px' }, 'BUDGET · SEPTEMBER');
    const bTitle = h('div', 'a', Cc, { left: '30px', top: '54px', fontSize: '28px', fontWeight: 600, letterSpacing: '-0.012em' }, 'Groceries');
    bTitle.prepend(h('span', null, null, { display: 'inline-block', width: '14px', height: '14px', borderRadius: '50%', background: C.green, marginRight: '12px', verticalAlign: '2px' }));
    const bPct = h('div', 'a tnum', Cc, { left: 'auto', right: '30px', top: '44px', fontSize: '46px', fontWeight: 700, letterSpacing: '-0.02em' });
    const bDetail = h('div', 'a tnum', Cc, { left: '30px', top: '100px', fontSize: '20px', color: 'rgba(60,60,67,0.6)', whiteSpace: 'nowrap' });
    const track = h('div', 'a', Cc, { left: '30px', top: '154px', width: '380px', height: '14px', borderRadius: '7px', background: 'rgba(60,60,67,0.16)', overflow: 'hidden' });
    const fill = h('div', 'a', track, { height: '14px', borderRadius: '7px' });
    h('div', 'a', Cc, { left: `${30 + 0.8 * 380 - 1}px`, top: '146px', width: '2px', height: '30px', borderRadius: '1px', background: 'rgba(60,60,67,0.45)' });
    h('div', 'a', Cc, { left: `${30 + 0.8 * 380 - 20}px`, top: '182px', width: '40px', textAlign: 'center', fontSize: '14px', color: 'rgba(60,60,67,0.6)' }, '80 %');
    const bLeft = h('div', 'a tnum', Cc, { left: '30px', top: '214px', fontSize: '22px', fontWeight: 600 });

    // The wires: from the new row, out to each screen that reads it.
    const svg = s('svg', { width: W, height: H, viewBox: `0 0 ${W} ${H}` }, root);
    Object.assign(svg.style, { position: 'absolute', left: 0, top: 0, overflow: 'visible' });
    const wires = [0, 1, 2].map(() => ({
      glow: s('path', { fill: 'none', stroke: 'rgba(10,132,255,0.28)', 'stroke-width': 10, 'stroke-linecap': 'round' }, svg),
      line: s('path', { fill: 'none', stroke: '#0A84FF', 'stroke-width': 3, 'stroke-linecap': 'round' }, svg),
      halo: s('circle', { r: 18, fill: 'rgba(10,132,255,0.35)' }, svg),
      dot: s('circle', { r: 7, fill: '#fff' }, svg),
      ripple: s('circle', { r: 10, fill: 'none', stroke: '#0A84FF', 'stroke-width': 3 }, svg),
    }));

    return (t) => {
      riseLetters(caption, t, B(12.25), { stagger: 0.018, dur: 0.5 });
      const leave = ease.inCubic(prog(t, T_REPORT, T_REPORT + 0.3));
      placeTL(caption.root, 880, 112 - 60 * leave, { o: 1 - leave });

      // Cards arrive on the half-beats, and kick when the money reaches them.
      const ea = cardEnter(t, B(12.3));
      const ec = cardEnter(t, B(12.45));
      const back = ease.inCubic(prog(t, T_REPORT, T_REPORT + 0.42));
      const fade = 1 - back;
      place(A, EV.A.x + EV.A.w / 2 - 90 * back, EV.A.y + EV.A.h / 2 + ea.dy - 40 * back, { s: ea.s * (1 + 0.035 * bump(t - HITS.A)) * (1 - 0.14 * back), o: ea.o * fade });
      place(Cc, EV.C.x + EV.C.w / 2 - 90 * back, EV.C.y + EV.C.h / 2 + ec.dy + 40 * back, { s: ec.s * (1 + 0.035 * bump(t - HITS.C)) * (1 - 0.14 * back), o: ec.o * fade });

      setOdometer(odo, '243120', '238840', t, HITS.A + 0.02, { spins: [0, 0, 0, 1, 1, 2] });
      const chipK = spring(t - HITS.A - 0.05, 3, 0.6);
      chip.style.opacity = clamp(chipK * 2).toFixed(3);
      chip.style.transform = `translateY(${(14 * (1 - chipK)).toFixed(2)}px) scale(${(0.8 + 0.2 * chipK).toFixed(4)})`;

      const spentB = lerp(30000, 34280, ease.swift(prog(t, HITS.C + 0.02, HITS.C + 0.55)));
      const frac = spentB / 40000;
      const amber = prog(frac, 0.795, 0.81);
      const tint = mix(C.blue, C.orange, amber);
      fill.style.width = `${(380 * frac).toFixed(2)}px`;
      fill.style.background = tint;
      bPct.textContent = `${Math.floor(frac * 100)} %`;
      bPct.style.color = mix('#000000', C.orange, amber);
      bDetail.textContent = `${eur(spentB)} spent of ${eur(40000)}`;
      bLeft.textContent = `${eur(40000 - spentB)} left`;
      bLeft.style.color = mix('#000000', C.orange, amber);

      // Wires. The row's right edge is read off the phone itself, 3D and all.
      const fresh = PHONE.tx && PHONE.tx.fresh.r;
      const rr = fresh ? stageRect(fresh) : { right: 620, top: 510, bottom: 544 };
      const from = [rr.right - 6, (rr.top + rr.bottom) / 2];
      const targets = [
        [EV.A.x, EV.A.y + EV.A.h / 2 + ea.dy, HITS.A],
        [EV.C.x, EV.C.y + EV.C.h / 2 + ec.dy, HITS.C],
        [EV.Bc.x, 527, HITS.B],
      ];
      targets.forEach(([tx, ty, hit], i) => {
        const w = wires[i];
        const d = `M${from[0].toFixed(1)},${from[1].toFixed(1)} C${(from[0] + 170).toFixed(1)},${from[1].toFixed(1)} ${(tx - 150).toFixed(1)},${ty.toFixed(1)} ${tx.toFixed(1)},${ty.toFixed(1)}`;
        w.line.setAttribute('d', d);
        w.glow.setAttribute('d', d);
        const len = w.line.getTotalLength();
        const p = ease.inOutCubic(prog(t, hit - 0.3, hit));
        const wireO = (1 - prog(t, T_REPORT, T_REPORT + 0.25)) * (p > 0 ? 1 : 0);
        const dash = `${(len * p).toFixed(1)} ${(len + 10).toFixed(1)}`;
        w.line.style.strokeDasharray = dash;
        w.glow.style.strokeDasharray = dash;
        w.line.style.opacity = (wireO * (1 - 0.55 * prog(t, hit + 0.1, hit + 0.6))).toFixed(3);
        w.glow.style.opacity = (wireO * (1 - prog(t, hit, hit + 0.5))).toFixed(3);
        const travelling = p > 0 && t < hit + 0.04;
        const pt = w.line.getPointAtLength(len * p);
        [w.dot, w.halo].forEach((c) => {
          c.setAttribute('cx', pt.x.toFixed(1));
          c.setAttribute('cy', pt.y.toFixed(1));
          c.style.opacity = travelling ? 1 : 0;
        });
        const rp = prog(t, hit, hit + 0.45);
        w.ripple.setAttribute('cx', tx.toFixed(1));
        w.ripple.setAttribute('cy', ty.toFixed(1));
        w.ripple.setAttribute('r', (10 + 70 * ease.outCubic(rp)).toFixed(1));
        w.ripple.style.opacity = rp > 0 && rp < 1 ? (1 - rp).toFixed(3) : 0;
      });
    };
  });
}

// =============================================================================================
// Bars 4–5 — the report. Card B in the everywhere shot opens out into the whole frame; its ring
// then unrolls into a strip, and the strip stands up into bars. One set of shapes throughout.
// =============================================================================================

const RING = {
  card: { dy: 22, Rm: 124, T: 46 },
  hero: { cx: 590, cy: 620, Rm: 222, T: 92 },
  strip: { x: 960, y: 858, L: 1500, T: 64 },
  bars: { base: 890, w: 150, gap: 250, max: 500 },
};
const T_UNROLL = B(17.5);
const T_BARS = B(18.5);
const T_FLOOD = B(20.1);

/**
 * One slice as a closed outline, sampled so that a ring, a strip and a bar all have the same
 * number of points in the same order — which is what lets one become the next.
 *
 * The ring runs clockwise from twelve o'clock. Unrolling holds its lowest point still and lowers
 * the curvature to zero, so the two halves fold down flat on either side of it.
 */
function sliceOutline(g, s0, s1, n) {
  const pts = [];
  const L = g.L;
  const kappa = g.kappa;
  const gap = g.gap;
  const at = (sigma, side) => {
    let x;
    let y;
    let nx;
    let ny;
    if (Math.abs(kappa) < 1e-7) {
      x = -sigma;
      y = 0;
      nx = 0;
      ny = 1;
    } else {
      const a = kappa * sigma;
      x = -Math.sin(a) / kappa;
      y = (Math.cos(a) - 1) / kappa;
      nx = -Math.sin(a);
      ny = Math.cos(a);
    }
    return [g.ax + x + nx * side * (g.T / 2), g.ay + y + ny * side * (g.T / 2)];
  };
  const a = (s0 - 0.5) * L + gap / 2;
  const b = (s1 - 0.5) * L - gap / 2;
  for (let i = 0; i <= n; i++) pts.push(at(lerp(a, b, i / n), 1));
  for (let i = n; i >= 0; i--) pts.push(at(lerp(a, b, i / n), -1));
  return pts;
}

/** A rectangle with the same point count and order as `sliceOutline`: bottom edge, then top. */
function rectOutline(x0, x1, yTop, yBottom, n) {
  const pts = [];
  for (let i = 0; i <= n; i++) pts.push([lerp(x1, x0, i / n), yBottom]);
  for (let i = n; i >= 0; i--) pts.push([lerp(x1, x0, i / n), yTop]);
  return pts;
}

const toPath = (pts) => `M${pts.map(([x, y]) => `${x.toFixed(2)},${y.toFixed(2)}`).join('L')}Z`;
const lerpPts = (a, b, p) => a.map(([x, y], i) => [lerp(x, b[i][0], p), lerp(y, b[i][1], p)]);

function sceneReport() {
  cue(HITS.B + 0.02, 'ring');
  cue(T_REPORT, 'open');
  cue(T_UNROLL, 'unroll');
  SPEND.forEach((_, i) => cue(T_BARS + 0.2 + (SPEND.length - 1 - i) * 0.055, 'bar', { i }));
  cue(T_FLOOD, 'flood');

  layer('report', B(12.2), B(21.05), (root) => {
    root.style.color = '#000';
    const bg = h('div', 'a', root, { background: '#fff', boxShadow: '0 40px 90px rgba(0,0,0,0.5), 0 10px 28px rgba(0,0,0,0.3)' });
    const svg = s('svg', { width: W, height: H, viewBox: `0 0 ${W} ${H}` }, root);
    Object.assign(svg.style, { position: 'absolute', left: 0, top: 0, overflow: 'visible' });
    const flood = s('path', { fill: C.purple }, svg);
    const slices = SPEND.map((c) => s('path', { fill: c.color }, svg));
    const eyebrow = h('div', 'eyebrow o0', root, { fontSize: '20px' }, 'REPORTS · SEPTEMBER');
    const title = h('div', 'a o0', root, { fontSize: '76px', fontWeight: 700, letterSpacing: '-0.035em', whiteSpace: 'nowrap', lineHeight: '1' }, 'Where the money went');
    const cLabel = h('div', 'a', root, { fontSize: '28px', color: 'rgba(60,60,67,0.6)', whiteSpace: 'nowrap' }, 'Total spent');
    const cValue = h('div', 'a tnum', root, { fontSize: '54px', fontWeight: 700, letterSpacing: '-0.025em', whiteSpace: 'nowrap' });
    const legend = SPEND.map((c) => {
      const el = h('div', 'a o0', root, { fontSize: '34px', whiteSpace: 'nowrap', lineHeight: '40px' });
      h('span', null, el, { display: 'inline-block', width: '22px', height: '22px', borderRadius: '50%', background: c.color, marginRight: '16px', verticalAlign: '0px' });
      h('span', null, el, { fontWeight: 600 }, c.name);
      const share = h('span', 'tnum', el, { color: 'rgba(60,60,67,0.6)', marginLeft: '14px' });
      return { el, share };
    });
    const barValue = SPEND.map(() => h('div', 'a tnum', root, { fontSize: '30px', fontWeight: 700, letterSpacing: '-0.02em', whiteSpace: 'nowrap' }));
    const barName = SPEND.map((c) => h('div', 'a', root, { fontSize: '24px', color: 'rgba(60,60,67,0.6)', whiteSpace: 'nowrap' }, c.name));

    root.style.transformOrigin = '960px 540px';
    return (t) => {
      const push = 1 + 0.035 * ease.smooth(prog(t, B(16.2), B(17.6)));
      root.style.transform = push > 1 ? `scale(${push.toFixed(5)})` : '';
      // The card, then the whole frame.
      const e = cardEnter(t, B(12.6));
      const pulse = 1 + 0.03 * bump(t - HITS.B);
      const X = ease.inOutQuart(prog(t, T_REPORT, T_REPORT + 0.72));
      const sc = e.s * pulse;
      const card = {
        cx: lerp(EV.Bc.x + EV.Bc.w / 2, W / 2, X),
        cy: lerp(EV.Bc.y + EV.Bc.h / 2 + e.dy, H / 2, X),
        w: lerp(EV.Bc.w * sc, W + 4, X),
        h: lerp(EV.Bc.h * sc, H + 4, X),
      };
      Object.assign(bg.style, {
        width: `${card.w}px`, height: `${card.h}px`, borderRadius: `${lerp(28 * sc, 0, X)}px`,
        opacity: e.o.toFixed(3),
      });
      bg.style.boxShadow = X < 1 ? '0 40px 90px rgba(0,0,0,0.5), 0 10px 28px rgba(0,0,0,0.3)' : 'none';
      placeTL(bg, card.cx - card.w / 2, card.cy - card.h / 2);
      const cardTL = [card.cx - (EV.Bc.w * sc) / 2, card.cy - (EV.Bc.h * sc) / 2];
      const local = (x, y) => [cardTL[0] + x * sc, cardTL[1] + y * sc];

      // Header and centre text: card sizes, then hero sizes, a little behind the card itself.
      const Xt = ease.inOutQuart(prog(t, T_REPORT + 0.06, T_REPORT + 0.8));
      const ebCard = local(30, 28);
      placeTL(eyebrow, lerp(ebCard[0], 140, Xt), lerp(ebCard[1], 118, Xt), { s: lerp((13 / 20) * sc, 1, Xt), o: e.o });
      const tiCard = local(30, 52);
      placeTL(title, lerp(tiCard[0], 136, Xt), lerp(tiCard[1], 150, Xt), { s: lerp((26 / 76) * sc, 1, Xt), o: e.o });

      // Amounts: the ring re-divides when the money lands.
      const q = ease.swift(prog(t, HITS.B + 0.02, HITS.B + 0.6));
      const amounts = SPEND.map((c) => lerp(c.before, c.after, q));
      const total = amounts.reduce((a, b) => a + b, 0);
      cValue.textContent = eur(total);
      SPEND.forEach((c, i) => {
        legend[i].share.textContent = `${pct(amounts[i], total)} %`;
      });

      // The ring's geometry, from card to hero to strip.
      const Xr = ease.inOutQuart(prog(t, T_REPORT + 0.03, T_REPORT + 0.78));
      const ringC = [card.cx, card.cy + RING.card.dy * sc];
      const Rm = lerp(RING.card.Rm * sc, RING.hero.Rm, Xr);
      const cx = lerp(ringC[0], RING.hero.cx, Xr);
      const cy = lerp(ringC[1], RING.hero.cy, Xr);
      const T = lerp(RING.card.T * sc, RING.hero.T, Xr) * (1 + 0.06 * bump(t - B(16.5)) + 0.06 * bump(t - B(17)));
      const u = ease.inOutCubic(prog(t, T_UNROLL, T_UNROLL + 0.55));
      const L = lerp(TAU * Rm, RING.strip.L, u);
      const g = {
        L,
        kappa: (1 - u) / Rm,
        gap: lerp(4 * (Rm / 124), 14, u),
        T: lerp(T, RING.strip.T, u),
        ax: lerp(cx, RING.strip.x, u),
        ay: lerp(cy + Rm, RING.strip.y, u),
      };
      // Centre text rides with the ring and leaves before it opens.
      const centreO = e.o * (1 - prog(t, T_UNROLL - 0.05, T_UNROLL + 0.15));
      const cs = lerp((15 / 28) * sc, 1, Xr);
      place(cLabel, cx, cy - 26 * lerp((30 / 54) * sc, 1, Xr), { s: cs, o: centreO });
      place(cValue, cx, cy + 20 * lerp((30 / 54) * sc, 1, Xr), { s: lerp((30 / 54) * sc, 1, Xr), o: centreO });

      // Legend: a two-by-three grid under the card's ring, then a list beside the hero's.
      SPEND.forEach((c, i) => {
        const Xl = ease.inOutQuart(prog(t, T_REPORT + 0.1 + i * 0.03, T_REPORT + 0.82 + i * 0.03));
        const lc = local(30 + (i % 2) * 212, 462 + Math.floor(i / 2) * 28);
        const lo = e.o * (1 - prog(t, T_UNROLL + (5 - i) * 0.02, T_UNROLL + 0.2 + (5 - i) * 0.02));
        placeTL(legend[i].el, lerp(lc[0], 1080, Xl), lerp(lc[1], 330 + i * 84, Xl), { s: lerp((15 / 34) * sc, 1, Xl), o: lo });
      });

      // Slices: ring → strip → bars → the purple one becomes the next frame.
      let s0 = 0;
      const n = 48;
      const rise = [];
      SPEND.forEach((c, i) => {
        const s1 = s0 + amounts[i] / total;
        let pts = sliceOutline(g, s0, s1, n);
        const order = SPEND.length - 1 - i; // bars left to right: smallest first
        const bx = RING.bars;
        const xMid = RING.strip.x + (order - (SPEND.length - 1) / 2) * bx.gap;
        const hBar = (c.after / SPEND[0].after) * bx.max;
        // Two moves: each segment narrows into its place on the floor, then stands up.
        const narrow = ease.inOutCubic(prog(t, T_BARS + order * 0.025, T_BARS + order * 0.025 + 0.26));
        const k = spring(t - (T_BARS + 0.2 + order * 0.055), 2.2, 0.58);
        if (narrow > 0) {
          const hNow = lerp(RING.strip.T, hBar, k);
          const bar = rectOutline(xMid - bx.w / 2, xMid + bx.w / 2, bx.base - hNow, bx.base, n);
          pts = narrow < 1 ? lerpPts(pts, bar, narrow) : bar;
        }
        const drop = ease.inBack(prog(t, T_FLOOD - 0.06 + order * 0.02, T_FLOOD + 0.3 + order * 0.02), 1.4);
        if (i !== SPEND.length - 1 && drop > 0) pts = pts.map(([x, y]) => [x, y + drop * 700]);
        slices[i].setAttribute('d', toPath(pts));
        slices[i].style.opacity = e.o;
        rise.push({ xMid, hBar, k, order });
        s0 = s1;
      });

      // Bar labels.
      SPEND.forEach((c, i) => {
        const r = rise[i];
        const shown = prog(t, T_BARS + 0.25 + r.order * 0.055, T_BARS + 0.45 + r.order * 0.055);
        const gone = 1 - prog(t, T_FLOOD - 0.1, T_FLOOD + 0.08);
        const hNow = lerp(RING.strip.T, r.hBar, r.k);
        place(barValue[i], r.xMid, RING.bars.base - hNow - 32, { o: shown * gone });
        barValue[i].textContent = eur(c.after * clamp(ease.outCubic(prog(t, T_BARS + 0.2 + r.order * 0.055, T_BARS + 0.8 + r.order * 0.055))));
        place(barName[i], r.xMid, RING.bars.base + 34, { o: shown * gone });
      });

      // The smallest bar — subscriptions — floods the frame and becomes the next shot.
      const fl = ease.inOutQuart(prog(t, T_FLOOD, T_FLOOD + 0.4));
      show(flood, fl > 0);
      if (fl > 0) {
        const r = rise[SPEND.length - 1];
        const x0 = lerp(r.xMid - RING.bars.w / 2, -20, fl);
        const x1 = lerp(r.xMid + RING.bars.w / 2, W + 20, fl);
        const y0 = lerp(RING.bars.base - r.hBar, -20, fl);
        const y1 = lerp(RING.bars.base, H + 20, fl);
        flood.setAttribute('d', `M${x0},${y0}L${x1},${y0}L${x1},${y1}L${x0},${y1}Z`);
      }
      const headO = 1 - prog(t, T_FLOOD - 0.1, T_FLOOD + 0.12);
      eyebrow.style.opacity = (e.o * headO).toFixed(3);
      title.style.opacity = (e.o * headO).toFixed(3);
    };
  });
}

// =============================================================================================
// Bars 6–7 — the montage. Five more things Cents does, one beat each: the field wipes up, the
// word rolls over, the card flips.
// =============================================================================================

addCSS(`
  .mcard { position:absolute; left:0; top:0; width:540px; background:#fff; color:#000; border-radius:30px;
    box-shadow: 0 50px 100px rgba(0,0,0,0.28), 0 12px 30px rgba(0,0,0,0.16); overflow:hidden; }
  .mc { position:absolute; left:0; top:0; width:540px; }
  .app-ic { position:absolute; display:flex; align-items:center; justify-content:center; color:#fff; font-weight:700;
    line-height:1; background: linear-gradient(160deg, #3d9dff, #0A84FF 55%, #0064d6); }
  .note-h { position:absolute; font-size:15px; color:rgba(60,60,67,0.6); white-space:nowrap; }
  .note-t { position:absolute; font-size:22px; font-weight:600; letter-spacing:-0.01em; white-space:nowrap; }
  .note-b { position:absolute; font-size:20px; white-space:nowrap; }
`);

function appIcon(parent, x, y, size) {
  return h('div', 'app-ic', parent, {
    left: `${x}px`, top: `${y}px`, width: `${size}px`, height: `${size}px`, borderRadius: `${size * 0.26}px`, fontSize: `${size * 0.7}px`,
  }, '¢');
}

function subsCard(card) {
  const el = h('div', 'mc', card);
  const n1 = h('div', 'a', el, { width: '540px', height: '140px' });
  appIcon(n1, 32, 30, 30);
  h('div', 'note-h', n1, { left: '74px', top: '35px' }, 'Cents · now');
  h('div', 'note-t', n1, { left: '32px', top: '76px' }, 'Netflix');
  h('div', 'note-b', n1, { left: '32px', top: '106px' }, `${eur(1299)}, in 3 days.`);
  const sep = h('div', 'a', el, { left: '32px', top: '160px', width: '476px', height: '1px', background: 'rgba(60,60,67,0.18)' });
  const n2 = h('div', 'a', el, { top: '160px', width: '540px', height: '140px' });
  appIcon(n2, 32, 26, 30);
  h('div', 'note-h', n2, { left: '74px', top: '31px' }, 'Cents · 9:00');
  h('div', 'note-t', n2, { left: '32px', top: '72px' }, 'Electricity');
  h('div', 'note-b', n2, { left: '32px', top: '102px' }, 'Due today. Tap to say what it came to.');
  return {
    el, height: 300,
    update(t, t0) {
      const k1 = spring(t - t0, 2.8, 0.7);
      const k2 = spring(t - t0 - 0.12, 2.8, 0.7);
      n1.style.transform = `translateY(${(-24 * (1 - k1)).toFixed(2)}px)`;
      n2.style.transform = `translateY(${(30 * (1 - k2)).toFixed(2)}px)`;
      opacity(n2, clamp(k2 * 2));
      opacity(sep, clamp(k2 * 2));
    },
  };
}

function investCard(card) {
  const el = h('div', 'mc', card);
  h('div', 'eyebrow', el, { left: '32px', top: '30px' }, 'PORTFOLIO');
  const value = h('div', 'a tnum', el, { left: '32px', top: '52px', fontSize: '52px', fontWeight: 700, letterSpacing: '-0.025em', whiteSpace: 'nowrap' });
  h('div', 'a tnum', el, { left: '32px', top: '122px', fontSize: '20px', fontWeight: 600, color: C.green, whiteSpace: 'nowrap' }, `Profit: ${eur(96820, { sign: true })} · +8,4 %`);
  const svg = s('svg', { width: 476, height: 120, viewBox: '0 0 476 120' }, el);
  Object.assign(svg.style, { position: 'absolute', left: '32px', top: '166px', overflow: 'visible' });
  const defs = s('defs', null, svg);
  const grad = s('linearGradient', { id: 'invfill', x1: 0, y1: 0, x2: 0, y2: 1 }, defs);
  s('stop', { offset: '0', 'stop-color': C.green, 'stop-opacity': 0.28 }, grad);
  s('stop', { offset: '1', 'stop-color': C.green, 'stop-opacity': 0 }, grad);
  const R = rng(11);
  const pts = [];
  let v = 78;
  for (let i = 0; i <= 28; i++) {
    v += (R() - 0.42) * 16 - 1.1;
    v = Math.max(8, Math.min(108, v));
    pts.push([(i / 28) * 476, i === 28 ? 14 : v]);
  }
  const lineD = `M${pts.map(([x, y]) => `${x.toFixed(1)},${y.toFixed(1)}`).join('L')}`;
  const area = s('path', { d: `${lineD}L476,120L0,120Z`, fill: 'url(#invfill)' }, svg);
  const line = s('path', { d: lineD, fill: 'none', stroke: C.green, 'stroke-width': 4, 'stroke-linejoin': 'round', 'stroke-linecap': 'round', pathLength: 1 }, svg);
  const clip = s('clipPath', { id: 'invclip' }, defs);
  const clipRect = s('rect', { x: -10, y: -20, width: 0, height: 160 }, clip);
  area.setAttribute('clip-path', 'url(#invclip)');
  const dot = s('circle', { cx: 476, cy: 14, r: 7, fill: C.green }, svg);
  const ring = s('circle', { cx: 476, cy: 14, r: 7, fill: 'none', stroke: C.green, 'stroke-width': 3 }, svg);
  return {
    el, height: 316,
    update(t, t0) {
      const p = ease.inOutCubic(prog(t, t0 - 0.02, t0 + 0.42));
      line.style.strokeDasharray = '1 1';
      line.style.strokeDashoffset = String(1 - p);
      clipRect.setAttribute('width', (486 * p).toFixed(1));
      value.textContent = eur(lerp(1151180, 1248000, ease.outCubic(prog(t, t0, t0 + 0.5))));
      const d = spring(t - t0 - 0.4, 3.5, 0.5);
      dot.setAttribute('r', (7 * clamp(d, 0, 1.3)).toFixed(2));
      const rp = prog(t, t0 + 0.42, t0 + 0.9);
      ring.setAttribute('r', (7 + 22 * ease.outCubic(rp)).toFixed(2));
      ring.style.opacity = rp > 0 ? (1 - rp).toFixed(3) : 0;
    },
  };
}

function transferCard(card) {
  const el = h('div', 'mc', card);
  h('div', 'eyebrow', el, { left: '32px', top: '30px' }, 'TRANSFER');
  const rowAt = (y, label, name) => {
    h('div', 'a', el, { left: '32px', top: `${y}px`, fontSize: '16px', color: 'rgba(60,60,67,0.6)' }, label);
    h('div', 'a', el, { left: '32px', top: `${y + 22}px`, fontSize: '26px', fontWeight: 600, letterSpacing: '-0.012em' }, name);
    return h('div', 'a tnum', el, { left: 'auto', right: '32px', top: `${y + 22}px`, fontSize: '26px', fontWeight: 500, whiteSpace: 'nowrap' });
  };
  const fromBal = rowAt(66, 'From', 'Main account');
  const toBal = rowAt(206, 'To', 'Savings');
  const svg = s('svg', { width: 60, height: 80, viewBox: '0 0 60 80' }, el);
  Object.assign(svg.style, { position: 'absolute', left: '32px', top: '128px', overflow: 'visible' });
  s('path', { d: 'M14,6 L14,70 M4,58 L14,70 L24,58', fill: 'none', stroke: C.blue, 'stroke-width': 3.5, 'stroke-linecap': 'round', 'stroke-linejoin': 'round' }, svg);
  const pill = h('div', 'a tnum', el, {
    left: '70px', top: '146px', padding: '6px 14px', borderRadius: '14px', background: C.blue, color: '#fff',
    fontSize: '22px', fontWeight: 700, whiteSpace: 'nowrap',
  }, eur(50000));
  return {
    el, height: 300,
    update(t, t0) {
      const p = ease.inOutCubic(prog(t, t0 + 0.02, t0 + 0.4));
      pill.style.transform = `translateY(${lerp(-30, 26, p).toFixed(2)}px)`;
      opacity(pill, prog(t, t0, t0 + 0.08));
      const q = ease.outCubic(prog(t, t0 + 0.12, t0 + 0.45));
      fromBal.textContent = eur(lerp(238840, 188840, q));
      toBal.textContent = eur(lerp(620000, 670000, q));
    },
  };
}

function lockCard(card) {
  const el = h('div', 'mc', card);
  const svg = s('svg', { width: 120, height: 120, viewBox: '0 0 48 48' }, el);
  Object.assign(svg.style, { position: 'absolute', left: '210px', top: '34px', overflow: 'visible' });
  // A fingerprint, drawn as nested arcs.
  const arcs = [
    'M12,17 A13,13 0 0,1 36,17',
    'M9,26 A15,15 0 0,1 17,11.5',
    'M31,11.5 A15,15 0 0,1 39,26',
    'M15,31 A9,9 0 1,1 33,27',
    'M19,37 A6,6 0 0,1 18,26 A6,6 0 0,1 30,27 L30,31',
    'M24,26 L24,33 A4,4 0 0,0 26,37',
    'M34,33 A13,13 0 0,1 30,40',
    'M12,36 A15,15 0 0,1 10,30',
  ].map((d) => s('path', { d, fill: 'none', stroke: C.blue, 'stroke-width': 2.4, 'stroke-linecap': 'round', pathLength: 1 }, svg));
  h('div', 'a', el, { left: '0', width: '540px', top: '178px', textAlign: 'center', fontSize: '28px', fontWeight: 600, letterSpacing: '-0.012em' }, 'Unlock Cents');
  h('div', 'a', el, { left: '0', width: '540px', top: '220px', textAlign: 'center', fontSize: '20px', color: 'rgba(60,60,67,0.6)' }, 'Your finances are behind this.');
  return {
    el, height: 290,
    update(t, t0) {
      arcs.forEach((a, i) => {
        const p = ease.outCubic(prog(t, t0 + i * 0.028, t0 + i * 0.028 + 0.3));
        a.style.strokeDasharray = '1 1';
        a.style.strokeDashoffset = String(1 - p);
        a.style.opacity = p > 0 ? 1 : 0;
        a.setAttribute('stroke', mix(C.blue, C.green, prog(t, t0 + 0.38, t0 + 0.46)));
      });
    },
  };
}

function backupCard(card) {
  const el = h('div', 'mc', card);
  h('div', 'eyebrow', el, { left: '32px', top: '30px' }, 'GOOGLE DRIVE');
  const svg = s('svg', { width: 76, height: 56, viewBox: '0 0 38 28' }, el);
  Object.assign(svg.style, { position: 'absolute', left: '432px', top: '22px', overflow: 'visible' });
  const cloud = s('path', {
    d: 'M10,25 L28,25 A7,7 0 0,0 29,11 A10,10 0 0,0 10,9.5 A8,8 0 0,0 10,25 Z',
    fill: 'none', stroke: C.blue, 'stroke-width': 2.4, 'stroke-linejoin': 'round', pathLength: 1,
  }, svg);
  const tick = s('path', { d: 'M14,17 L18,21 L25,13', fill: 'none', stroke: C.blue, 'stroke-width': 2.6, 'stroke-linecap': 'round', 'stroke-linejoin': 'round', pathLength: 1 }, svg);
  const rowY = [84, 146];
  h('div', 'a', el, { left: '32px', top: `${rowY[0]}px`, fontSize: '22px' }, 'Back up automatically');
  const track = h('div', 'a', el, { left: '446px', top: `${rowY[0] - 3}px`, width: '62px', height: '36px', borderRadius: '18px' });
  const knob = h('div', 'a', track, { left: '3px', top: '3px', width: '30px', height: '30px', borderRadius: '15px', background: '#fff', boxShadow: '0 3px 8px rgba(0,0,0,0.15), 0 1px 1px rgba(0,0,0,0.16)' });
  h('div', 'a', el, { left: '32px', top: `${rowY[0] + 44}px`, width: '476px', height: '1px', background: 'rgba(60,60,67,0.18)' });
  h('div', 'a', el, { left: '32px', top: `${rowY[1]}px`, fontSize: '22px' }, 'Last backup');
  h('div', 'a', el, { left: 'auto', right: '32px', top: `${rowY[1]}px`, fontSize: '22px', color: 'rgba(60,60,67,0.6)' }, 'Today, 03:00');
  h('div', 'a', el, { left: '32px', top: '206px', width: '476px', fontSize: '17px', lineHeight: '24px', color: 'rgba(60,60,67,0.6)' },
    'Keeps the newest copy of each of the last 7 days, 4 weeks and 6 months.');
  return {
    el, height: 290,
    update(t, t0) {
      const c = ease.inOutCubic(prog(t, t0, t0 + 0.32));
      cloud.style.strokeDasharray = '1 1';
      cloud.style.strokeDashoffset = String(1 - c);
      const k = ease.outCubic(prog(t, t0 + 0.26, t0 + 0.44));
      tick.style.strokeDasharray = '1 1';
      tick.style.strokeDashoffset = String(1 - k);
      tick.style.opacity = k > 0 ? 1 : 0;
      const on = spring(t - t0 - 0.06, 3.2, 0.72);
      knob.style.transform = `translateX(${(26 * on).toFixed(2)}px)`;
      track.style.background = mix('#E9E9EA', C.green, clamp(on));
    },
  };
}

const MONTAGE = [
  { word: 'Subscriptions.', bg: C.purple, ink: '#FFFFFF', card: subsCard },
  { word: 'Investments.', bg: C.green, ink: '#000000', card: investCard },
  { word: 'Transfers.', bg: C.blue, ink: '#FFFFFF', card: transferCard },
  { word: 'Private.', bg: C.indigo, ink: '#FFFFFF', card: lockCard },
  { word: 'Backed up.', bg: C.orange, ink: '#000000', card: backupCard },
];
const T_MONTAGE = B(21);
const T_YOLO = B(26);
const beatOf = (i) => T_MONTAGE + i * BEAT;

function sceneMontage() {
  MONTAGE.forEach((m, i) => {
    cue(beatOf(i), 'cut', { i });
    if (i > 0) cue(beatOf(i) - 0.12, 'whoosh', { i });
  });

  layer('montage', T_MONTAGE - 0.1, T_YOLO + 0.2, (root) => {
    root.style.perspective = '2200px';
    const fields = MONTAGE.map((m) => h('div', 'a', root, { width: `${W}px`, height: `${H}px`, background: m.bg }));
    const words = MONTAGE.map((m) =>
      kinetic(root, m.word, '', { fontSize: '146px', fontWeight: 700, letterSpacing: '-0.044em', lineHeight: '1', color: m.ink }));
    const card = h('div', 'mcard', root);
    const contents = MONTAGE.map((m) => m.card(card));
    return (t) => {
      // Fields wipe up from the floor, the way the last bar grew.
      fields.forEach((f, i) => {
        const p = i === 0 ? 1 : ease.inOutQuart(prog(t, beatOf(i) - 0.13, beatOf(i) + 0.13));
        show(f, p > 0);
        placeTL(f, 0, (1 - p) * H);
      });
      // Words roll over on the beat.
      words.forEach((w, i) => {
        const on = t >= beatOf(i) - 0.02 && t < beatOf(i + 1) + 0.05;
        show(w.root, on);
        if (!on) return;
        riseLetters(w, t, beatOf(i) + 0.02, { stagger: 0.012, dur: 0.4 });
        leaveLetters(w, t, beatOf(i + 1) - 0.14, { stagger: 0.006, dur: 0.16, e: ease.inCubic });
        placeTL(w.root, 128, 460);
      });
      // One card, flipping to the next feature on each beat.
      const i = clamp(Math.floor((t - T_MONTAGE + 0.001) / BEAT), 0, MONTAGE.length - 1);
      const tb = beatOf(i);
      const pre = prog(t, beatOf(i + 1) - 0.11, beatOf(i + 1));
      const post = prog(t, tb, tb + 0.15);
      let ry = 0;
      if (i < MONTAGE.length - 1 && pre > 0) ry = 90 * ease.inCubic(pre);
      else if (i > 0 && post < 1) ry = -90 * (1 - ease.outCubic(post));
      const hPrev = i > 0 ? contents[i - 1].height : contents[0].height;
      const hk = spring(t - tb, 3.2, 0.7);
      const ch = i > 0 ? lerp(hPrev, contents[i].height, clamp(hk, 0, 1.15)) : contents[0].height;
      card.style.height = `${ch.toFixed(2)}px`;
      const enter = spring(t - T_MONTAGE + 0.08, 2.4, 0.72);
      const exit = ease.inCubic(prog(t, T_YOLO - 0.16, T_YOLO + 0.1));
      const x = 1486 + 760 * (1 - enter);
      const y = 540 + 1000 * exit;
      place(card, x, y, { ry: ry + 30 * (1 - enter), r: -4 * (1 - enter) + 3 * exit, s: 1.2 * (1 - 0.06 * Math.sin((ry / 90) * Math.PI)) });
      contents.forEach((c, j) => {
        show(c.el, j === i);
        if (j === i) c.update(t, tb);
      });
    };
  });
}

// =============================================================================================
// Bars 7–8 — "You Only Log Once." The initials are what a detector would keep; they fall into
// the coin, and the coin is the logo. Cents, detected at 1.00.
// =============================================================================================

const WORDS = ['You', 'Only', 'Log', 'Once.'];
const T_SNAP = B(28);
const T_FALL = B(28.25);
const T_GATHER = B(28.62);
const T_COIN = B(29.25);
const T_MARK = B(29.55);
const T_FINAL = B(30);

function sceneYolo() {
  WORDS.forEach((w, i) => cue(T_YOLO + i * BEAT * 0.5, 'word', { w, i }));
  cue(T_SNAP, 'impact');
  cue(T_FALL, 'fall');
  cue(T_GATHER, 'gather');
  cue(T_COIN, 'coin', { final: true });
  cue(T_MARK, 'mark');
  cue(T_FINAL + 0.12, 'lock', { final: true });

  layer('yolo', T_YOLO - 0.16, DURATION + 1, (root) => {
    const black = h('div', 'a', root, { width: `${W}px`, height: `${H}px`, background: '#000' });
    const FONT = 196;
    const lines = WORDS.map((w) =>
      kinetic(root, w, '', { fontSize: `${FONT}px`, fontWeight: 700, letterSpacing: '-0.045em', lineHeight: '1', color: '#fff' }));
    const LINE = 216;
    const widest = Math.max(...lines.map((l) => l.root.offsetWidth));
    const left = Math.round((W - widest) / 2);
    const top = Math.round((H - (LINE * 3 + FONT)) / 2) + 12;
    lines.forEach((l, i) => placeTL(l.root, left, top + i * LINE));
    // Where each letter sits, measured once, before anything moves.
    const sr = STAGE.getBoundingClientRect();
    const k = sr.width / W;
    const boxes = lines.map((l) => {
      const r = l.chars[0].getBoundingClientRect();
      return { x: (r.left - sr.left) / k, y: (r.top - sr.top) / k, w: r.width / k, h: r.height / k };
    });
    const R = rng(29);
    const falls = lines.map((l) => l.chars.map(() => ({ vx: (R() - 0.5) * 260, vr: (R() - 0.5) * 520, d: R() * 0.08 })));
    const dets = WORDS.map(() => new DetBox(root, { tagSize: 20, thick: 5, fill: 0 }));
    const labels = ['you', 'only', 'log', 'once'];
    const confs = [0.98, 0.97, 0.99, 0.99];

    const mark = kinetic(root, 'Cents', '', { fontSize: '176px', fontWeight: 700, letterSpacing: '-0.045em', lineHeight: '1', color: '#fff' });
    mark.mask.style.overflow = 'visible';
    const coin = coinEl(root);
    const tagline = kinetic(root, 'Every cent, accounted for.', '', { fontSize: '46px', fontWeight: 500, letterSpacing: '-0.015em', lineHeight: '1', color: 'rgba(235,235,245,0.62)' });
    const credit = h('div', 'a', root, {
      width: `${W}px`, textAlign: 'center', fontSize: '22px', fontWeight: 600, letterSpacing: '0.16em', color: 'rgba(235,235,245,0.55)',
    }, 'MOTION REEL FOR LIBREYOLO');
    const finalBox = new DetBox(root, { tagSize: 26, thick: 5, fill: 0 });
    const D = 156;
    const markW = mark.root.offsetWidth;
    const lockW = D + 38 + markW;
    const coinX = W / 2 - lockW / 2 + D / 2;
    const coinY = 492;
    mark.chars.forEach((c) => (c.style.transform = 'none'));
    const tagW = tagline.root.offsetWidth;
    placeTL(tagline.root, W / 2 - tagW / 2, 640);
    placeTL(credit, 0, 992);

    return (t) => {
      const wipe = ease.inOutQuart(prog(t, T_YOLO - 0.17, T_YOLO + 0.05));
      placeTL(black, 0, -(1 - wipe) * H);

      const snap = prog(t, T_SNAP, T_SNAP + 0.001);
      const gather = ease.inExpo(prog(t, T_GATHER, T_COIN));
      lines.forEach((l, i) => {
        riseLetters(l, t, T_YOLO + i * BEAT * 0.5 - 0.03, { stagger: 0.02, dur: 0.4 });
        l.mask.style.overflow = t >= T_FALL - 0.05 ? 'visible' : 'hidden';
        l.chars.forEach((c, j) => {
          if (j === 0) {
            // The initial: lit by the detector, then pulled into the coin.
            const b = boxes[i];
            const dx = (960 - (b.x + b.w / 2)) * gather;
            const dy = (coinY - (b.y + b.h / 2)) * gather;
            if (t >= T_GATHER) c.style.transform = `translate(${dx.toFixed(2)}px,${dy.toFixed(2)}px) scale(${(1 - 0.8 * gather).toFixed(4)})`;
            if (t >= T_COIN) c.style.opacity = 0;
            return;
          }
          const f = falls[i][j];
          const dim = 1 - 0.72 * ease.outCubic(prog(t, T_SNAP, T_SNAP + 0.12));
          const dt = t - (T_FALL + f.d + j * 0.012);
          if (dt > 0) {
            c.style.transform = `translate(${(f.vx * dt).toFixed(2)}px,${(0.5 * 5600 * dt * dt - 120 * dt).toFixed(2)}px) rotate(${(f.vr * dt).toFixed(2)}deg)`;
            c.style.opacity = (dim * (1 - prog(dt, 0.12, 0.34))).toFixed(3);
          } else if (t >= T_SNAP) {
            c.style.opacity = dim.toFixed(3);
          }
        });
        const b = boxes[i];
        const cx = b.x + b.w / 2 + (960 - (b.x + b.w / 2)) * gather;
        const cy = b.y + b.h / 2 + (coinY - (b.y + b.h / 2)) * gather;
        const sz = 1 - 0.8 * gather;
        const lock = ease.swift(prog(t, T_SNAP + i * 0.03, T_SNAP + i * 0.03 + 0.22));
        dets[i].set({
          x: cx, y: cy, w: (b.w + 28) * sz, h: (FONT * 0.7275 + 34) * sz,
          spread: 110 * (1 - lock),
          o: prog(t, T_SNAP + i * 0.03, T_SNAP + i * 0.03 + 0.04) * (1 - prog(t, T_COIN - 0.06, T_COIN)),
          tag: prog(t, T_SNAP + 0.1 + i * 0.03, T_SNAP + 0.24 + i * 0.03) * (1 - prog(gather, 0.3, 0.6)),
          conf: lerp(0.5, confs[i], ease.outCubic(prog(t, T_SNAP + 0.05, T_SNAP + 0.3))),
          label: labels[i],
        });
      });

      // The coin, then the lockup.
      const pop = spring(t - T_COIN, 3.0, 0.45);
      const slide = ease.inOutCubic(prog(t, T_MARK - 0.1, T_MARK + 0.42));
      const cxNow = lerp(960, coinX, slide);
      show(coin, t >= T_COIN);
      place(coin, cxNow, coinY, { s: (D / 200) * pop });
      coin.flash.style.opacity = (0.9 * (1 - prog(t, T_COIN, T_COIN + 0.2))).toFixed(3);
      // The name is pulled out from behind the coin as the coin makes room for it: the word
      // slides right and is clipped at the coin's centre line, so it only exists past the coin.
      const markLeft = coinX + D / 2 + 38;
      const wp = ease.swift(prog(t, T_MARK - 0.04, T_MARK + 0.62));
      const tx = lerp(-(markW + 60), 0, wp);
      const clipLeft = Math.max(0, cxNow - (markLeft + tx));
      mark.root.style.transform = `translate(${(markLeft + tx).toFixed(2)}px,${(coinY - 108).toFixed(2)}px)`;
      mark.root.style.clipPath = `inset(-40% -10% -40% ${clipLeft.toFixed(2)}px)`;
      show(mark.root, t >= T_MARK - 0.04);
      riseLetters(tagline, t, T_FINAL - 0.08, { stagger: 0.005, dur: 0.45 });
      opacity(credit, prog(t, B(30.3), B(30.3) + 0.35));
      // A glint crosses the coin while the mark holds.
      const gl = prog(t, B(30.7), B(30.7) + 0.5);
      coin.glint.style.opacity = gl > 0 && gl < 1 ? '1' : '0';
      coin.glint.style.transform = `translateX(${lerp(-260, 260, ease.inOutCubic(gl)).toFixed(1)}px) rotate(22deg)`;

      // One box for the whole mark: the opening shot's `cent 0.99`, answered.
      const grow = ease.inOutCubic(prog(t, T_FINAL - 0.12, T_FINAL + 0.2));
      const coinBox = { x: cxNow, y: coinY, w: D * pop + 34, h: D * pop + 34 };
      const lockBox = { x: W / 2 + 4, y: coinY, w: lockW + 70, h: D + 60 };
      const settle = spring(t - T_FINAL - 0.08, 3.4, 0.55);
      finalBox.set({
        x: lerp(coinBox.x, lockBox.x, grow),
        y: lerp(coinBox.y, lockBox.y, grow),
        w: lerp(coinBox.w, lockBox.w, grow),
        h: lerp(coinBox.h, lockBox.h, grow),
        spread: 70 * (1 - ease.swift(prog(t, T_COIN, T_COIN + 0.24))) + 16 * (1 - clamp(settle)) * (t > T_FINAL ? 1 : 0),
        o: prog(t, T_COIN, T_COIN + 0.05),
        tag: prog(t, T_COIN + 0.12, T_COIN + 0.26),
        conf: t < T_FINAL ? lerp(0.62, 0.97, ease.outCubic(prog(t, T_COIN + 0.1, T_COIN + 0.4))) : lerp(0.97, 1, prog(t, T_FINAL + 0.05, T_FINAL + 0.2)),
        label: 'cents',
      });
    };
  });
}

// =============================================================================================
// Assembly.
// =============================================================================================

function buildReel() {
  STAGE = document.getElementById('stage');
  sceneDetect();
  sceneBackdrop();
  sceneLogIt();
  scenePhone();
  sceneEverywhere();
  sceneReport();
  sceneMontage();
  sceneYolo();
  CUES.sort((a, b) => a.t - b.t);
}
