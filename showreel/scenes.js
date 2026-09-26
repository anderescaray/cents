/*
 * Cents — the film. Sixty-five seconds, thirteen shots of five seconds, one idea each.
 *
 *   0  0–5    Intro          a ring of category colours draws itself around a cent: the logo
 *   1  5–10   Categories     the ring flies into the phone and becomes the month: where it went
 *   2  10–15  Quick entry    42,80 € typed, Groceries picked, saved — the ring updates itself
 *   3  15–20  Capture        switched on, the bank's notifications arrive as movements by themselves
 *   4  20–25  To categorize  they wait; a tap gives each a category, and a group if it has one
 *   5  25–30  Groups         a trip's movements, from five categories, less what a friend paid back
 *   6  30–35  Subscriptions  what they cost a month and a year, and a reminder before a charge
 *   7  35–40  Variable       a salary set to "ask me": it waits for the real figure
 *   8  40–45  Budgets        limits filling up: amber at 80 %, red when over
 *   9  45–50  Reports        income against spending, and the days that cost the most
 *   10 50–55  Net worth      accounts, a transfer, investments: one number
 *   11 55–60  Privacy        a fingerprint, then the Drive backup and the export
 *   12 60–65  Outro          the ring leaves the phone and becomes the logo again
 *
 * The UI is designed for the film (`ui.js`); the features, words and colours are the app's —
 * except capture from notifications, which is planned (PRD §4.6) and drawn here as designed.
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

/** Shot `k` starts here: every shot is two bars. */
const S = (k) => B(8 * k);

/** The shots by name, and a moment inside one: `at(SHOT.inbox, 1.5)` is a beat and a half in. */
const SHOT = {
  intro: 0, month: 1, entry: 2, capture: 3, inbox: 4, groups: 5, subs: 6,
  variable: 7, budgets: 8, reports: 9, worth: 10, privacy: 11, outro: 12,
};
const at = (shot, beats) => S(shot) + B(beats);

/** Where the element sits on the stage, whatever the page is scaled to. */
function stageRect(el) {
  const r = el.getBoundingClientRect();
  const sr = STAGE.getBoundingClientRect();
  const k = sr.width / W;
  return {
    left: (r.left - sr.left) / k, right: (r.right - sr.left) / k, top: (r.top - sr.top) / k, bottom: (r.bottom - sr.top) / k,
    cx: ((r.left + r.right) / 2 - sr.left) / k, cy: ((r.top + r.bottom) / 2 - sr.top) / k,
  };
}

/**
 * Screen choreography: parts arrive one after another, rising a little, and leave the same
 * way, faster. Parts are positioned by left/top; only transform and opacity move. A part's
 * `extraTf` is appended — set it for this frame before calling.
 */
function choreo(parts, t, tIn, tOut, o = {}) {
  const st = o.stagger === undefined ? 0.06 : o.stagger;
  const dy = o.dy === undefined ? 26 : o.dy;
  parts.forEach((p, i) => {
    if (!p) return;
    const a = ease.swift(prog(t, tIn + i * st, tIn + i * st + (o.dur || 0.7)));
    const b_ = tOut === undefined ? 0 : ease.inCubic(prog(t, tOut + i * 0.025, tOut + i * 0.025 + 0.34));
    p.style.opacity = (a * (1 - b_)).toFixed(4);
    p.style.transform = `translateY(${((1 - a) * dy - b_ * 14).toFixed(2)}px)${p.extraTf || ''}`;
  });
}

function countUp(t, t0, t1, a, b_, e = ease.outCubic) {
  return lerp(a, b_, e(prog(t, t0, t1)));
}

// ---------------------------------------------------------------------------------------------
// The numbers the film tells. Cents, as the app keeps them.
// ---------------------------------------------------------------------------------------------

const MONTH = [
  { cat: 'housing', before: 65000, after: 65000 },
  { cat: 'groceries', before: 38620, after: 42900 },
  { cat: 'restaurants', before: 21460, after: 21460 },
  { cat: 'shopping', before: 17633, after: 17633 },
  { cat: 'transport', before: 12840, after: 12840 },
  { cat: 'subscriptions', before: 7887, after: 7887 },
];
const TOTAL_BEFORE = MONTH.reduce((a, m) => a + m.before, 0);
const TOTAL_AFTER = MONTH.reduce((a, m) => a + m.after, 0);
/** Shares are truncated, the way `SpendingSection.kt` writes them. */
const pct = (v, total) => Math.floor((v / total) * 100);

const SHOTS = [
  { aura: '#007AFF' },
  { eyebrow: 'Categories', accent: '#007AFF', aura: '#007AFF', title: ['See where', 'it goes.'], sub: 'Every category at a glance — and the one that takes the most.' },
  { eyebrow: 'Quick entry', accent: '#28B45A', aura: '#34C759', title: ['Add it', 'in seconds.'], sub: 'Amount, category, done. The ring updates itself.' },
  { eyebrow: 'Automatic capture', accent: '#1995AD', aura: '#30B0C7', title: ['Or let it', 'add itself.'], sub: 'Switch it on: what your bank notifies, spent or received, arrives on its own.' },
  { eyebrow: 'To categorize', accent: '#E08600', aura: '#FF9500', title: ['Sort it', 'later.'], sub: 'It waits here. A tap gives it a category — and a group, if it belongs to one.' },
  { eyebrow: 'Groups', accent: '#5856D6', aura: '#5856D6', title: ['Group what', 'belongs together.'], sub: 'A trip, a move, a side job — one total, across every category.' },
  { eyebrow: 'Subscriptions', accent: '#AF52DE', aura: '#AF52DE', title: ['Every', 'subscription,', 'counted.'], sub: 'What they cost you a month and a year, and a reminder before each charge.' },
  { eyebrow: 'Variable amounts', accent: '#28B45A', aura: '#34C759', title: ['Salary that', 'changes?', 'Cents asks.'], sub: 'Income and bills that vary wait for the real figure, and land on the day they were due.' },
  { eyebrow: 'Budgets', accent: '#E08600', aura: '#FF9500', title: ['Set limits.', 'See them coming.'], sub: 'Amber at 80 %, red when you go over.' },
  { eyebrow: 'Reports', accent: '#007AFF', aura: '#007AFF', title: ['See the', 'trend.'], sub: 'Income against spending, month by month — and the days you spend the most.' },
  { eyebrow: 'Accounts & investments', accent: '#00A89F', aura: '#00C7BE', title: ['Everything', 'you have.'], sub: 'Accounts, transfers and investments, in one number.' },
  { eyebrow: 'Privacy', accent: '#5856D6', aura: '#5856D6', title: ['Private', 'by design.'], sub: 'It stays on your phone — locked with your fingerprint, backed up to your Drive.' },
  { aura: '#007AFF' },
];

// ---------------------------------------------------------------------------------------------
// Stage: a pale room, and a soft light behind the phone in the colour of the chapter.
// ---------------------------------------------------------------------------------------------

function phonePose(t) {
  const enter = ease.swift(prog(t, S(SHOT.month) - 1.05, S(SHOT.month) + 0.45));
  const leave = ease.inOutCubic(prog(t, S(SHOT.outro) + 0.5, S(SHOT.outro) + 1.7));
  return {
    x: lerp(2500, 1262, enter) + leave * 1300,
    y: 540 + 7 * Math.sin((t / 9) * TAU),
    s: 1,
    rx: 2 + Math.cos((t / 13) * TAU),
    ry: lerp(-30, -7, enter) + 1.6 * Math.sin((t / 11) * TAU) - 20 * leave,
  };
}

function hexOf(c) {
  if (c.startsWith('#')) return c;
  const m = c.match(/\d+/g).map(Number);
  return `#${m.slice(0, 3).map((v) => v.toString(16).padStart(2, '0')).join('')}`;
}

function auraColor(t) {
  let c = SHOTS[0].aura;
  for (let k = 1; k < SHOTS.length; k++) c = hexOf(mix(c, SHOTS[k].aura, prog(t, S(k) - 0.5, S(k) + 0.7)));
  return c;
}

function sceneStage() {
  layer('stage', 0, DURATION + 1, (root) => {
    root.style.background = 'linear-gradient(180deg, #F7F8FB 0%, #EDEFF4 100%)';
    const aura = h('div', 'a', root, { width: '1500px', height: '1500px', borderRadius: '50%' });
    const aura2 = h('div', 'a', root, { width: '1100px', height: '1100px', borderRadius: '50%' });
    return (t) => {
      const c = auraColor(t);
      const p = phonePose(t);
      const onPhone = prog(t, S(SHOT.month) - 1.2, S(SHOT.month) + 0.4) * (1 - prog(t, S(SHOT.outro) + 0.3, S(SHOT.outro) + 1.4));
      const cx = lerp(960, p.x, onPhone);
      aura.style.background = `radial-gradient(circle, ${rgba(c, 0.2)} 0%, ${rgba(c, 0.09)} 34%, ${rgba(c, 0.025)} 55%, ${rgba(c, 0)} 70%)`;
      place(aura, cx, 540, { s: 1 + 0.04 * Math.sin((t / 7) * TAU) });
      aura2.style.background = `radial-gradient(circle, ${rgba(c, 0.07)} 0%, ${rgba(c, 0)} 65%)`;
      place(aura2, 360, 960, { o: onPhone });
    };
  });
}

// ---------------------------------------------------------------------------------------------
// Titles: an eyebrow in the chapter's colour, two or three lines that rise word by word, and a
// sentence under them. They hold for four seconds; nobody has to read fast.
// ---------------------------------------------------------------------------------------------

function sceneTitles() {
  layer('titles', S(SHOT.month) - 0.2, S(SHOT.outro), (root) => {
    const blocks = SHOTS.map((shot, k) => {
      if (!shot.title) return null;
      const wrap = h('div', null, root, { position: 'absolute', left: '150px', top: '0', width: '800px' });
      const eyebrow = h('div', null, wrap, {
        fontSize: '19px', fontWeight: 650, letterSpacing: '0.16em', color: shot.accent, textTransform: 'uppercase', marginBottom: '20px',
      }, shot.eyebrow);
      const lines = shot.title.map((line) => {
        const m = h('div', null, wrap, {
          overflow: 'hidden', fontSize: '86px', fontWeight: 700, letterSpacing: '-0.038em', lineHeight: '1.04', color: UI.ink,
          whiteSpace: 'nowrap', padding: '0 0.05em 0.1em', margin: '0 -0.05em -0.1em',
        });
        const words = line.split(' ').map((wd, i, arr) =>
          h('span', null, m, { display: 'inline-block', marginRight: i < arr.length - 1 ? '0.25em' : '0' }, wd));
        return { m, words };
      });
      const sub = h('div', null, wrap, {
        marginTop: '30px', fontSize: '29px', lineHeight: '1.4', color: UI.ink2, fontWeight: 450, letterSpacing: '-0.012em', maxWidth: '660px',
      }, shot.sub);
      return { k, wrap, eyebrow, lines, sub };
    }).filter(Boolean);
    blocks.forEach((b_) => {
      b_.wrap.style.top = `${Math.round(540 - b_.wrap.offsetHeight / 2)}px`;
    });
    blocks.forEach((b_) => cue(S(b_.k) + 0.28, 'title', { k: b_.k }));
    return (t) => {
      blocks.forEach((b_) => {
        const t0 = S(b_.k);
        const t1 = S(b_.k + 1);
        const on = t >= t0 - 0.05 && t < t1;
        show(b_.wrap, on);
        if (!on) return;
        const out = ease.inCubic(prog(t, t1 - 0.62, t1 - 0.18));
        const e = ease.swift(prog(t, t0 + 0.15, t0 + 0.9));
        b_.eyebrow.style.opacity = e.toFixed(3);
        b_.eyebrow.style.transform = `translateY(${((1 - e) * 16).toFixed(2)}px)`;
        let n = 0;
        b_.lines.forEach((ln) => ln.words.forEach((w) => {
          const d = t0 + 0.28 + n * 0.075;
          n++;
          const p = ease.swift(prog(t, d, d + 0.95));
          w.style.transform = `translateY(${((1 - p) * 108).toFixed(2)}%)`;
          w.style.opacity = p > 0 ? '1' : '0';
        }));
        const sp = ease.swift(prog(t, t0 + 0.85, t0 + 1.7));
        b_.sub.style.opacity = sp.toFixed(3);
        b_.sub.style.transform = `translateY(${((1 - sp) * 18).toFixed(2)}px)`;
        b_.wrap.style.opacity = (1 - out).toFixed(3);
        b_.wrap.style.transform = `translateY(${(-out * 18).toFixed(2)}px)`;
      });
    };
  });
}

// ---------------------------------------------------------------------------------------------
// The logo: a cent inside a ring of category colours. It opens the film, flies into the phone
// to become the month, and comes back out at the end.
// ---------------------------------------------------------------------------------------------

const LOGO = { r: 116, th: 26 };
const LOGO_COLORS = ['#34C759', '#007AFF', '#FF9500', '#FF2D55', '#00C7BE', '#AF52DE'];
const HOME_RING = { x: 200, y: 262, r: 104, th: 24 };
const PHONE = {};
const T_HAND = S(SHOT.month) + 0.15; // the flying ring lands in the phone
const T_LIFT = S(SHOT.outro) + 0.45; // and leaves it again

function monthSlices(q) {
  return MONTH.map((m) => ({ color: CAT[m.cat].color, value: lerp(m.before, m.after, q) }));
}

/** The ring's slices blended from the logo's even split to the month's real one. */
function blendSlices(u, q) {
  const month = monthSlices(q);
  const total = month.reduce((a, m) => a + m.value, 0);
  return month.map((m, i) => ({ color: mix(LOGO_COLORS[i], m.color, u), value: lerp(total / 6, m.value, u) }));
}

function sceneLogo() {
  LOGO_COLORS.forEach((_, i) => cue(0.5 + i * 0.16, 'arc', { i }));
  cue(0.25, 'cent');
  cue(1.6, 'mark');
  cue(2.35, 'tagline');
  cue(S(SHOT.month) - 1.0, 'phoneIn');
  cue(T_HAND, 'land');
  cue(T_LIFT, 'lift');
  cue(S(SHOT.outro) + 1.55, 'cent', { final: true });
  cue(S(SHOT.outro) + 1.95, 'mark', { final: true });

  layer('logo', 0, DURATION + 1, (root) => {
    const ringWrap = h('div', 'a', root, { width: '320px', height: '320px' });
    const ring = makeRing(ringWrap, 320);
    const cent = h('div', 'a', root, { fontSize: '132px', fontWeight: 700, color: UI.ink, lineHeight: '1', letterSpacing: '0' }, '¢');
    const mark = h('div', 'a', root, { fontSize: '150px', fontWeight: 700, color: UI.ink, letterSpacing: '-0.045em', lineHeight: '1', whiteSpace: 'nowrap' }, 'Cents');
    const tagline = h('div', 'a', root, { fontSize: '40px', fontWeight: 500, color: UI.ink2, letterSpacing: '-0.015em', whiteSpace: 'nowrap' }, 'See where your money goes.');
    const small = h('div', 'a', root, { fontSize: '19px', fontWeight: 600, color: UI.ink3, letterSpacing: '0.18em', whiteSpace: 'nowrap' }, 'PERSONAL FINANCE FOR ANDROID');
    const outer = LOGO.r + LOGO.th / 2;
    const gap = 46;
    const markW = mark.offsetWidth;
    const lockW = outer * 2 + gap + markW;
    const lockX = 960 - lockW / 2 + outer;
    const markX = lockX + outer + gap;
    const tagW = tagline.offsetWidth;
    const smallW = small.offsetWidth;
    const Y = 470;

    return (t) => {
      // Where the phone's own ring is on the stage right now.
      let target = null;
      if (PHONE.anchor && ((t > S(SHOT.month) - 1.2 && t < S(SHOT.month) + 0.4) || (t > S(SHOT.outro) - 0.2 && t < S(SHOT.outro) + 1.8))) {
        const a = stageRect(PHONE.anchor);
        const b_ = stageRect(PHONE.anchor2);
        target = { x: a.cx, y: a.cy, k: Math.hypot(b_.cx - a.cx, b_.cy - a.cy) / 100 };
      }
      const intro = t < DURATION / 2;
      let x;
      let y;
      let r;
      let th;
      let u;
      let q;
      let reveal = null;
      let o;
      let rot = 0;
      let ringCx;
      if (intro) {
        const lock = ease.inOutCubic(prog(t, 1.55, 2.35));
        const fly = ease.inOutCubic(prog(t, S(SHOT.month) - 0.85, T_HAND));
        ringCx = lerp(960, lockX, lock);
        x = target ? lerp(ringCx, target.x, fly) : ringCx;
        y = target ? lerp(Y, target.y, fly) : Y;
        const kk = target ? target.k : 1;
        r = lerp(LOGO.r, HOME_RING.r * kk, fly);
        th = lerp(LOGO.th, HOME_RING.th * kk, fly);
        u = ease.inOutCubic(prog(t, S(SHOT.month) - 0.7, T_HAND));
        q = 0;
        reveal = LOGO_COLORS.map((_, i) => ease.outCubic(prog(t, 0.5 + i * 0.16, 1.0 + i * 0.16)));
        o = t < T_HAND ? 1 : 0;
      } else {
        const fly = ease.inOutCubic(prog(t, T_LIFT, T_LIFT + 1.05));
        const lock = ease.inOutCubic(prog(t, S(SHOT.outro) + 1.9, S(SHOT.outro) + 2.7));
        const src = target || { x: 960, y: Y };
        const kk = target ? target.k : 1;
        ringCx = lerp(960, lockX, lock);
        x = lerp(src.x, ringCx, fly);
        y = lerp(src.y, Y, fly);
        r = lerp(HOME_RING.r * kk, LOGO.r, fly);
        th = lerp(HOME_RING.th * kk, LOGO.th, fly);
        u = 1 - ease.inOutCubic(prog(t, T_LIFT + 0.1, T_LIFT + 1.0));
        q = 1;
        o = t >= T_LIFT ? 1 : 0;
        rot = 0.3 * ease.inOutCubic(prog(t, S(SHOT.outro) + 2.4, DURATION));
      }
      show(ringWrap, o > 0);
      if (o > 0) {
        place(ringWrap, x, y);
        ring.draw(blendSlices(u, q), { r, th, gap: lerp(8, 6, u), each: reveal, rot });
      }
      // The cent at the centre, while the ring is the logo.
      let centS;
      let centO;
      if (intro) {
        centS = spring(t - 0.25, 2.6, 0.55) * (1 - 0.6 * ease.inOutCubic(prog(t, S(SHOT.month) - 0.85, S(SHOT.month) - 0.3)));
        centO = 1 - prog(t, S(SHOT.month) - 0.75, S(SHOT.month) - 0.4);
      } else {
        centS = spring(t - (S(SHOT.outro) + 1.55), 2.6, 0.55);
        centO = prog(t, S(SHOT.outro) + 1.55, S(SHOT.outro) + 1.7);
      }
      show(cent, centO > 0 && o > 0);
      if (centO > 0 && o > 0) place(cent, x, y + 4, { s: centS, o: centO });

      // The name, pulled out from behind the ring; the promise under it.
      const t0m = intro ? 1.6 : S(SHOT.outro) + 1.95;
      const t1m = intro ? S(SHOT.month) - 1.5 : DURATION + 1;
      const mp = ease.swift(prog(t, t0m, t0m + 0.9));
      const mOut = ease.inCubic(prog(t, t1m, t1m + 0.4));
      const mx = lerp(ringCx + outer - markW - 20, markX, mp);
      const clipL = Math.max(0, ringCx + outer + 6 - mx);
      show(mark, mp > 0 && mOut < 1);
      mark.style.transform = `translate(${mx.toFixed(2)}px,${(Y - 80).toFixed(2)}px)`;
      mark.style.clipPath = `inset(-30% -10% -30% ${clipL.toFixed(2)}px)`;
      mark.style.opacity = (1 - mOut).toFixed(3);

      const tg0 = intro ? 2.35 : S(SHOT.outro) + 2.55;
      const tp = ease.swift(prog(t, tg0, tg0 + 0.9));
      show(tagline, tp > 0 && mOut < 1);
      placeTL(tagline, 960 - tagW / 2, Y + 176 + 22 * (1 - tp), { o: tp * (1 - mOut) });
      const sp = ease.swift(prog(t, S(SHOT.outro) + 3.1, S(SHOT.outro) + 3.9));
      show(small, !intro && sp > 0);
      placeTL(small, 960 - smallW / 2, 952 + 12 * (1 - sp), { o: sp });
    };
  });
}

// ---------------------------------------------------------------------------------------------
// The phone's screens.
// ---------------------------------------------------------------------------------------------

function buildHome(sc) {
  const root = h('div', 'scr', sc);
  statusBar(root);
  const head = h('div', null, root, { position: 'absolute', left: 0, top: 0, width: '400px', height: '110px' });
  tx(head, 24, 54, 'September', { size: 30, weight: 700, ls: -0.025 });
  tx(head, 24, 92, 'This month', { size: 15, color: UI.ink2 });
  const cal = box(head, 336, 58, 40, 40, { r: 20 });
  glyph(h('div', null, cal, { position: 'absolute', left: '10px', top: '10px' }), 'calendar', 20, UI.ink, 1.9);

  const ringWrap = h('div', null, root, { position: 'absolute', left: `${HOME_RING.x - 150}px`, top: `${HOME_RING.y - 150}px`, width: '300px', height: '300px' });
  const ring = makeRing(ringWrap, 300);
  const centre = h('div', null, root, { position: 'absolute', left: '100px', top: `${HOME_RING.y - 30}px`, width: '200px', height: '64px' });
  const total = tx(centre, 100, 0, eur(TOTAL_BEFORE), { size: 27, weight: 700, w: 200, align: 'center', tnum: true, ls: -0.02 });
  tx(centre, 100, 36, 'spent', { size: 14, color: UI.ink2, w: 200, align: 'center' });
  PHONE.anchor = h('div', null, root, { position: 'absolute', left: `${HOME_RING.x}px`, top: `${HOME_RING.y}px`, width: '1px', height: '1px' });
  PHONE.anchor2 = h('div', null, root, { position: 'absolute', left: `${HOME_RING.x + 100}px`, top: `${HOME_RING.y}px`, width: '1px', height: '1px' });

  const list = box(root, 16, 408, 368, 336);
  const rows = MONTH.map((m, i) => {
    const r = h('div', null, list, { position: 'absolute', left: 0, top: `${8 + i * 54}px`, width: '368px', height: '54px', borderRadius: '16px' });
    catIcon(r, m.cat, 14, 10, 34);
    tx(r, 60, 8, CAT[m.cat].name, { size: 16, weight: 600 });
    const share = tx(r, 60, 29, '', { size: 13, color: UI.ink2, tnum: true });
    const amount = tx(r, 352, 17, '', { size: 16, weight: 600, w: 160, align: 'right', tnum: true });
    return { r, share, amount };
  });

  const bar = box(root, 60, 772, 280, 64, { r: 32 });
  ['ringIcon', 'list', null, 'chart', 'sliders'].forEach((g, i) => {
    if (!g) return;
    const cell = h('div', null, bar, { position: 'absolute', left: `${18 + i * 56}px`, top: '19px' });
    glyph(cell, g, 26, i === 0 ? UI.blue : UI.ink3, 2.1);
  });
  const add = h('div', null, bar, {
    position: 'absolute', left: '112px', top: '6px', width: '56px', height: '52px', borderRadius: '26px', background: UI.blue,
    boxShadow: '0 6px 16px rgba(0,122,255,0.35)', display: 'flex', alignItems: 'center', justifyContent: 'center',
  });
  glyph(add, 'plus', 24, '#fff', 2.6);

  const toast = h('div', null, root, {
    position: 'absolute', left: '24px', top: '60px', height: '44px', borderRadius: '22px', background: UI.ink, color: '#fff',
    display: 'flex', alignItems: 'center', gap: '8px', padding: '0 18px 0 12px', fontSize: '15px', fontWeight: 600, whiteSpace: 'nowrap', zIndex: 20,
  });
  const tick = h('div', null, toast, { width: '24px', height: '24px', borderRadius: '12px', background: UI.green, display: 'flex', alignItems: 'center', justifyContent: 'center' });
  glyph(tick, 'check', 15, '#fff', 3);
  h('span', null, toast, null, 'Added to Groceries');

  return { root, head, ringWrap, ring, centre, total, list, rows, bar, add, toast };
}

function buildSheet(sc) {
  const dim = h('div', 'scr', sc, { background: '#0E1117' });
  const sheet = h('div', null, sc, {
    position: 'absolute', left: 0, top: '64px', width: '400px', height: '804px', background: '#fff', borderRadius: '30px 30px 0 0',
    boxShadow: '0 -8px 30px rgba(16,24,40,0.12)', overflow: 'hidden',
  });
  h('div', null, sheet, { position: 'absolute', left: '180px', top: '9px', width: '40px', height: '5px', borderRadius: '3px', background: '#D5D8DE' });
  tx(sheet, 22, 26, 'Cancel', { size: 16, color: UI.blue });
  tx(sheet, 200, 26, 'New expense', { size: 17, weight: 650, w: 200, align: 'center' });
  const seg = segmented(sheet, 22, 66, 356, ['Expense', 'Income', 'Transfer']);
  seg.set(0);
  const amount = h('div', 'tnum', sheet, {
    position: 'absolute', left: '0', top: '122px', width: '400px', textAlign: 'center', fontSize: '58px', fontWeight: 700,
    letterSpacing: '-0.03em', lineHeight: '70px', whiteSpace: 'nowrap',
  });
  const zero = h('span', null, amount, { color: UI.ink3 }, '0');
  const typed = [...'42,80'].map((ch) => h('span', null, amount, { display: 'inline-block' }, ch));
  h('span', null, amount, { color: UI.ink3, fontWeight: 600, fontSize: '40px' }, ' €');
  let cx = 22;
  const chipEls = ['groceries', 'restaurants', 'shopping', 'transport'].map((c) => {
    const e = chip(sheet, cx, 214, c);
    cx += e.offsetWidth + 10;
    return e;
  });
  const keys = keypad(sheet, 22, 278, 356);
  const addBtn = button(sheet, 22, 578, 356, 58, 'Add expense', { r: 20 });
  return { dim, sheet, seg, amount, zero, typed, chipEls, keys, addBtn };
}

/**
 * What the bank's app notified, as Cents records it. `note` is the notification itself; `pick`
 * and `group` are what the owner gives it once it is waiting.
 */
const CAPTURED = [
  {
    name: 'Café Central', when: 'Today, 13:05 · from your bank', amount: -2340,
    note: ['Card payment', `${eur(2340)} at Café Central`], cats: ['restaurants', 'groceries'], pick: 'restaurants',
  },
  {
    name: 'Ana', when: '“Lisbon hotel” · from your bank', amount: 12005,
    note: ['Transfer received', `${eur(12005)} from Ana`], cats: ['refunds', 'gifts'], pick: 'refunds', group: 'lisbon',
  },
];
const INBOX = { capY: 128, capH: 132, rowH: 76, fullH: 168 };
/** Where the two movements sit: under the switch while they arrive, at the top once filing starts. */
const ROW_TOP = {
  capture: [INBOX.capY + INBOX.capH + 16, INBOX.capY + INBOX.capH + 16 + INBOX.rowH + 12],
  filing: [128, 128 + INBOX.fullH + 14],
};
/** The bank's app, as its notifications show it: no bank in particular. */
const BANK = '#12355B';

function buildInbox(sc) {
  const root = h('div', 'scr', sc);
  statusBar(root);
  const head = h('div', null, root, { position: 'absolute', left: 0, top: 0, width: '400px', height: '120px' });
  tx(head, 24, 54, 'To categorize', { size: 30, weight: 700, ls: -0.025 });
  const badge = h('div', 'tnum', head, {
    position: 'absolute', left: '234px', top: '60px', width: '30px', height: '30px', borderRadius: '15px', background: UI.blue,
    color: '#fff', fontSize: '16px', fontWeight: 700, display: 'flex', alignItems: 'center', justifyContent: 'center',
  }, '0');
  tx(head, 24, 94, 'Waiting for a category', { size: 15, color: UI.ink2 });

  // The switch that lets Cents read notifications, and the one app it is allowed to read.
  const cap = box(root, 16, INBOX.capY, 368, INBOX.capH);
  const bellC = h('div', null, cap, {
    position: 'absolute', left: '16px', top: '16px', width: '40px', height: '40px', borderRadius: '20px', background: rgba('#30B0C7', 0.15),
    display: 'flex', alignItems: 'center', justifyContent: 'center',
  });
  glyph(bellC, 'bell', 22, '#30B0C7', 2.1);
  tx(cap, 68, 16, 'Capture from notifications', { size: 16, weight: 650 });
  tx(cap, 68, 39, 'Only the apps you pick', { size: 13, color: UI.ink2 });
  const sw = toggle(cap, 300, 20);
  h('div', null, cap, { position: 'absolute', left: '68px', right: '0', top: '72px', height: '1px', background: UI.line });
  const app = h('div', null, cap, {
    position: 'absolute', left: '20px', top: '86px', width: '32px', height: '32px', borderRadius: '9px', background: BANK,
    display: 'flex', alignItems: 'center', justifyContent: 'center',
  });
  glyph(app, 'bank', 20, '#fff', 2);
  tx(cap, 68, 82, 'Your bank', { size: 16, weight: 600 });
  tx(cap, 68, 104, 'Payments and transfers', { size: 13, color: UI.ink2 });
  const check = glyph(h('div', null, cap, { position: 'absolute', left: '324px', top: '91px' }), 'check', 22, UI.ink3, 2.6);

  const empty = tx(root, 200, 300, 'Nothing waiting', { size: 15, color: UI.ink3, w: 300, align: 'center' });
  const foot = tx(root, 24, 460, 'Cents reads the notification on this phone. It never logs in to your bank.', { size: 13, color: UI.ink2, wrap: true, w: 352, lh: 1.45 });

  // A captured movement: one line while it arrives; categories, then groups, once it opens.
  const rows = CAPTURED.map((m) => {
    const c = box(root, 16, 0, 368, INBOX.rowH, { clip: true });
    const icon = h('div', null, c, { position: 'absolute', left: '16px', top: '18px', width: '38px', height: '38px' });
    const q = catIcon(icon, 'unknown', 0, 0, 38);
    const pickedIcon = catIcon(icon, m.pick, 0, 0, 38);
    tx(c, 66, 18, m.name, { size: 17, weight: 600 });
    tx(c, 66, 41, m.when, { size: 13, color: UI.ink2 });
    tx(c, 352, 27, eur(m.amount, { sign: m.amount > 0 }), {
      size: 17, weight: 600, w: 130, align: 'right', tnum: true, color: m.amount > 0 ? UI.green : UI.ink,
    });
    let x = 16;
    const cats = m.cats.map((cat) => {
      const e = chip(c, x, 74, cat, null, { h: 36, size: 14 });
      x += e.offsetWidth + 8;
      return e;
    });
    const label = tx(c, 16, 128, 'Group', { size: 13, weight: 600, color: UI.ink3 });
    x = 68;
    const groups = ['lisbon', 'moving'].map((g) => {
      const e = chip(c, x, 118, g, null, { h: 36, size: 14 });
      x += e.offsetWidth + 8;
      return e;
    });
    // The chips the owner taps: the movement's category, and its group if it has one.
    const pickChip = cats[m.cats.indexOf(m.pick)];
    const groupChip = m.group ? groups[['lisbon', 'moving'].indexOf(m.group)] : null;
    return { c, q, pickedIcon, cats, groups, pickChip, groupChip, choices: [...cats, label, ...groups], m };
  });

  const done = h('div', null, root, { position: 'absolute', left: 0, top: '300px', width: '400px', height: '200px' });
  const doneCircle = h('div', null, done, {
    position: 'absolute', left: '160px', top: '0', width: '80px', height: '80px', borderRadius: '40px', background: rgba(UI.green, 0.14),
    display: 'flex', alignItems: 'center', justifyContent: 'center',
  });
  const doneTick = glyph(doneCircle, 'check', 40, UI.green, 3);
  tx(done, 200, 100, 'All sorted', { size: 24, weight: 700, w: 300, align: 'center' });
  tx(done, 200, 134, 'Nothing is waiting for a category.', { size: 15, color: UI.ink2, w: 320, align: 'center' });
  return { root, head, badge, cap, sw, check, empty, foot, rows, done, doneCircle, doneTick };
}

const TRIP = [
  { cat: 'flights', name: 'Flights', v: 27360 },
  { cat: 'hotel', name: 'Hotel', v: 24010 },
  { cat: 'food', name: 'Dinner', v: 6230 },
  { cat: 'taxi', name: 'Taxi', v: 3820 },
  { cat: 'tours', name: 'Tours', v: 7000 },
];
const TRIP_TOTAL = TRIP.reduce((a, m) => a + m.v, 0);
/** Ana's half of the hotel, captured from the bank and filed into the trip: it lowers the cost. */
const REFUND = { cat: 'refunds', name: 'Ana', v: CAPTURED[1].amount };
const GROUP_ARRIVE = (i) => at(SHOT.groups, 1.5 + i * 0.75);

function buildGroups(sc) {
  const root = h('div', 'scr', sc);
  statusBar(root);
  const head = h('div', null, root, { position: 'absolute', left: 0, top: 0, width: '400px', height: '120px' });
  tx(head, 24, 54, 'Groups', { size: 30, weight: 700, ls: -0.025 });
  tx(head, 24, 94, 'What an occasion cost, across categories', { size: 15, color: UI.ink2 });
  const lis = box(root, 16, 130, 368, 262);
  catIcon(lis, 'flights', 18, 18, 46, { solid: true });
  tx(lis, 76, 20, 'Lisbon trip', { size: 20, weight: 700, ls: -0.02 });
  tx(lis, 76, 46, '12 – 16 August', { size: 14, color: UI.ink2 });
  tx(lis, 18, 90, 'Cost you', { size: 14, color: UI.ink2 });
  const total = tx(lis, 18, 110, eur(0), { size: 42, weight: 700, tnum: true, ls: -0.03 });
  const split = tx(lis, 18, 162, `${eur(TRIP_TOTAL)} spent · ${eur(REFUND.v)} paid back`, { size: 13, color: UI.ink2, tnum: true });
  const track = h('div', null, lis, { position: 'absolute', left: '18px', top: '186px', width: '332px', height: '12px', borderRadius: '6px', background: UI.fill, overflow: 'hidden' });
  const segs = TRIP.map((m) => h('div', null, track, { position: 'absolute', top: 0, height: '12px', background: CAT[m.cat].color }));
  let lx = 18;
  const legend = TRIP.map((m) => {
    const e = h('div', null, lis, { position: 'absolute', left: `${lx}px`, top: '214px', display: 'flex', alignItems: 'center', gap: '6px', fontSize: '13px', color: UI.ink2, whiteSpace: 'nowrap' });
    h('div', null, e, { width: '8px', height: '8px', borderRadius: '4px', background: CAT[m.cat].color });
    h('span', null, e, null, m.name);
    lx += e.offsetWidth + 12;
    return e;
  });
  const other = (y, cat, name, when, label, amount, color) => {
    const c = box(root, 16, y, 368, 84);
    catIcon(c, cat, 16, 20, 44);
    tx(c, 72, 21, name, { size: 17, weight: 600 });
    tx(c, 72, 44, when, { size: 13, color: UI.ink2 });
    tx(c, 352, 22, label, { size: 12, color: UI.ink2, w: 140, align: 'right' });
    tx(c, 352, 40, amount, { size: 17, weight: 700, w: 160, align: 'right', tnum: true, color: color || UI.ink });
    return c;
  };
  const move = other(408, 'rent', 'Moving house', 'March · 23 movements', 'Cost you', eur(192000));
  const job = other(508, 'salary', 'Freelance job', 'Spring · 9 movements', 'Earned you', eur(320000), UI.green);
  return { root, head, lis, total, split, segs, legend, move, job };
}

const SUBS = [
  { cat: 'rent', name: 'Rent', v: 65000, next: '1 Oct' },
  { cat: 'insurance', name: 'Insurance', v: 4250, next: '7 Oct' },
  { cat: 'gym', name: 'Gym', v: 3490, next: '5 Oct' },
  { cat: 'phone', name: 'Phone', v: 1999, next: '12 Oct' },
  { cat: 'netflix', name: 'Netflix', v: 1299, next: '3 Oct', bell: true },
  { cat: 'spotify', name: 'Spotify', v: 1099, next: '21 Oct' },
];
const SUBS_MONTH = SUBS.reduce((a, m) => a + m.v, 0);

function buildSubs(sc) {
  const root = h('div', 'scr', sc);
  statusBar(root);
  const head = h('div', null, root, { position: 'absolute', left: 0, top: 0, width: '400px', height: '100px' });
  tx(head, 24, 54, 'Subscriptions', { size: 30, weight: 700, ls: -0.025 });
  const sum = box(root, 16, 106, 368, 124);
  tx(sum, 18, 18, 'Every month', { size: 14, color: UI.ink2 });
  const month = tx(sum, 18, 38, eur(0), { size: 40, weight: 700, tnum: true, ls: -0.03 });
  const year = tx(sum, 18, 90, '', { size: 15, color: UI.ink2, tnum: true });
  const list = box(root, 16, 246, 368, 12 + SUBS.length * 64);
  const rows = SUBS.map((m, i) => {
    const r = h('div', null, list, { position: 'absolute', left: 0, top: `${6 + i * 64}px`, width: '368px', height: '64px' });
    catIcon(r, m.cat, 16, 13, 38);
    const name = tx(r, 66, 12, m.name, { size: 16, weight: 600 });
    tx(r, 66, 34, `Monthly · next ${m.next}`, { size: 13, color: UI.ink2 });
    tx(r, 352, 21, eur(m.v), { size: 16, weight: 600, w: 120, align: 'right', tnum: true });
    if (m.bell) {
      const b_ = h('div', null, r, { position: 'absolute', left: `${66 + name.offsetWidth + 8}px`, top: '14px' });
      glyph(b_, 'bell', 15, UI.ink3, 2);
    }
    if (i < SUBS.length - 1) h('div', null, r, { position: 'absolute', left: '66px', right: '0', bottom: '0', height: '1px', background: UI.line });
    return r;
  });
  const note = h('div', null, sc, {
    position: 'absolute', left: '12px', top: '12px', width: '376px', height: '96px', borderRadius: '26px', background: 'rgba(255,255,255,0.97)',
    boxShadow: '0 12px 34px rgba(16,24,40,0.18)', zIndex: 30,
  });
  const ic = h('div', null, note, { position: 'absolute', left: '16px', top: '15px', width: '26px', height: '26px', borderRadius: '8px', background: UI.ink });
  makeRing(ic, 26).draw(LOGO_COLORS.map((c) => ({ color: c, value: 1 })), { r: 8, th: 3, gap: 1.2 });
  tx(note, 52, 19, 'CENTS', { size: 12, weight: 650, color: UI.ink2, ls: 0.06 });
  tx(note, 358, 19, 'now', { size: 12, color: UI.ink2, w: 60, align: 'right' });
  tx(note, 16, 48, 'Netflix', { size: 16, weight: 700 });
  tx(note, 16, 69, `${eur(1299)}, in 3 days.`, { size: 15 });
  return { root, head, sum, month, year, list, rows, note };
}

function buildVariable(sc) {
  // Part one: the rule, switched from a fixed amount to "ask me".
  const edit = h('div', 'scr', sc);
  statusBar(edit);
  const eHead = h('div', null, edit, { position: 'absolute', left: 0, top: 0, width: '400px', height: '120px' });
  catIcon(eHead, 'salary', 24, 58, 50, { solid: true });
  tx(eHead, 88, 58, 'Salary', { size: 28, weight: 700, ls: -0.025 });
  tx(eHead, 88, 92, 'Income · every month on the 25th', { size: 14, color: UI.ink2 });
  const amt = box(edit, 16, 136, 368, 206);
  tx(amt, 18, 18, 'Amount', { size: 14, color: UI.ink2 });
  const seg = segmented(amt, 18, 44, 332, ['Fixed', 'Ask me the amount']);
  const fixed = h('div', null, amt, { position: 'absolute', left: '18px', top: '100px', width: '332px', height: '90px' });
  tx(fixed, 0, 8, eur(230000), { size: 36, weight: 700, tnum: true, ls: -0.03 });
  tx(fixed, 0, 56, 'The same every month', { size: 14, color: UI.ink2 });
  const ask = h('div', null, amt, { position: 'absolute', left: '18px', top: '100px', width: '332px', height: '90px' });
  const q = h('div', null, ask, {
    position: 'absolute', left: '0', top: '8px', width: '48px', height: '48px', borderRadius: '24px', background: rgba(UI.green, 0.14),
    display: 'flex', alignItems: 'center', justifyContent: 'center',
  });
  glyph(q, 'question', 26, UI.green, 2.4);
  tx(ask, 62, 9, 'Cents asks when it’s due', { size: 17, weight: 650 });
  tx(ask, 62, 33, 'Nothing is recorded until you say', { size: 14, color: UI.ink2 });
  tx(ask, 62, 52, 'what came in.', { size: 14, color: UI.ink2 });
  const more = box(edit, 16, 358, 368, 112);
  tx(more, 18, 16, 'Repeats', { size: 16 });
  tx(more, 350, 16, 'Every month', { size: 16, color: UI.ink2, w: 160, align: 'right' });
  h('div', null, more, { position: 'absolute', left: '18px', right: '0', top: '55px', height: '1px', background: UI.line });
  tx(more, 18, 72, 'Into', { size: 16 });
  tx(more, 350, 72, 'Main account', { size: 16, color: UI.ink2, w: 160, align: 'right' });

  // Part two: what fell due and is waiting for a figure.
  const wait = h('div', 'scr', sc);
  statusBar(wait);
  const wHead = h('div', null, wait, { position: 'absolute', left: 0, top: 0, width: '400px', height: '120px' });
  tx(wHead, 24, 54, 'Waiting for an amount', { size: 27, weight: 700, ls: -0.025 });
  tx(wHead, 24, 90, 'These fell due. Say what they came to.', { size: 15, color: UI.ink2 });
  const sal = box(wait, 16, 128, 368, 76, { clip: true });
  const salTint = h('div', null, sal, { position: 'absolute', inset: '0', background: rgba(UI.green, 0.1), opacity: 0 });
  catIcon(sal, 'salary', 16, 18, 40);
  tx(sal, 68, 17, 'Salary', { size: 17, weight: 600 });
  const salWhen = tx(sal, 68, 40, 'Due today · usually ~2.300 €', { size: 13, color: UI.ink2 });
  const enter = button(sal, 262, 20, 90, 36, 'Enter', { size: 15, r: 18 });
  const field = h('div', 'tnum', sal, {
    position: 'absolute', left: '16px', top: '76px', width: '336px', height: '56px', borderRadius: '16px', background: UI.fill,
    display: 'flex', alignItems: 'center', padding: '0 16px', fontSize: '28px', fontWeight: 700, letterSpacing: '-0.02em',
  });
  const fieldText = h('span', null, field, null, '');
  const caret = h('span', null, field, { display: 'inline-block', width: '2px', height: '30px', background: UI.blue, marginLeft: '3px' });
  const save = button(field, 0, 10, 72, 36, 'Save', { size: 15, r: 18 });
  save.style.left = 'auto';
  save.style.right = '10px';
  const rec = h('div', null, sal, { position: 'absolute', left: '16px', top: '82px', width: '336px', height: '48px', display: 'flex', alignItems: 'center', gap: '10px', opacity: 0 });
  const recTick = h('div', null, rec, { width: '30px', height: '30px', borderRadius: '15px', background: UI.green, display: 'flex', alignItems: 'center', justifyContent: 'center', flex: '0 0 auto' });
  glyph(recTick, 'check', 17, '#fff', 3);
  h('span', null, rec, { fontSize: '15px', fontWeight: 600 }, 'Recorded on 25 Sep');
  h('span', 'tnum', rec, { marginLeft: 'auto', fontSize: '17px', fontWeight: 700, color: UI.green }, `+${eur(241250)}`);
  const elec = box(wait, 16, 220, 368, 134);
  catIcon(elec, 'bills', 16, 18, 40);
  tx(elec, 68, 17, 'Electricity', { size: 17, weight: 600 });
  const elecWhen = tx(elec, 68, 40, 'Due 28 Sep · usually ~65 €', { size: 13, color: UI.ink2 });
  const elecNew = tx(elec, 68, 40, 'Due 1 Oct · usually ~65 €', { size: 13, color: UI.ink2 });
  const plus3 = h('div', null, elec, {
    position: 'absolute', left: '240px', top: '37px', height: '22px', borderRadius: '11px', padding: '0 9px', background: rgba(UI.blue, 0.12),
    color: UI.blue, fontSize: '12px', fontWeight: 700, lineHeight: '22px', whiteSpace: 'nowrap',
  }, '+3 days');
  const postpone = chip(elec, 16, 80, 'bills', 'Postpone', { h: 36, size: 14 });
  postpone.dot.innerHTML = '';
  glyph(postpone.dot, 'clock', 15, '#E0A100', 2.2);
  h('div', null, elec, {
    position: 'absolute', left: `${16 + postpone.offsetWidth + 8}px`, top: '80px', height: '36px', borderRadius: '18px', background: UI.fill,
    padding: '0 14px', fontSize: '14px', fontWeight: 600, lineHeight: '36px', whiteSpace: 'nowrap',
  }, 'No bill this month');
  const foot = tx(wait, 24, 388, 'Nothing is recorded for these until you say. A bill that did not come is not a zero.', { size: 13, color: UI.ink2, wrap: true, w: 352, lh: 1.45 });
  return { edit, eHead, amt, seg, fixed, ask, more, wait, wHead, sal, salTint, salWhen, enter, field, fieldText, caret, save, rec, elec, elecWhen, elecNew, plus3, postpone, foot };
}

const BUDGETS = [
  { cat: 'groceries', spent: 42900, limit: 50000 },
  { cat: 'restaurants', spent: 21460, limit: 30000 },
  { cat: 'shopping', spent: 17633, limit: 17000 },
  { cat: 'transport', spent: 12840, limit: 20000 },
];

/** The budget bar's colour: the accent, amber from 80 %, red past 100 % (D26). */
function budgetTint(f) {
  if (f < 0.8) return UI.blue;
  if (f < 1) return mix(UI.blue, UI.amber, prog(f, 0.8, 0.83));
  return mix(UI.amber, UI.red, prog(f, 1.0, 1.03));
}

function buildBudgets(sc) {
  const root = h('div', 'scr', sc);
  statusBar(root);
  const head = h('div', null, root, { position: 'absolute', left: 0, top: 0, width: '400px', height: '110px' });
  tx(head, 24, 54, 'Budgets', { size: 30, weight: 700, ls: -0.025 });
  tx(head, 24, 92, 'September', { size: 15, color: UI.ink2 });
  const cards = BUDGETS.map((m, i) => {
    const c = box(root, 16, 124 + i * 120, 368, 106);
    catIcon(c, m.cat, 16, 16, 38);
    tx(c, 66, 16, CAT[m.cat].name, { size: 17, weight: 600 });
    const detail = tx(c, 66, 39, '', { size: 13, color: UI.ink2, tnum: true });
    const status = tx(c, 352, 24, '', { size: 15, weight: 650, w: 150, align: 'right', tnum: true });
    const track = h('div', null, c, { position: 'absolute', left: '16px', top: '76px', width: '336px', height: '10px', borderRadius: '5px', background: UI.fill });
    const fill = h('div', null, track, { position: 'absolute', left: 0, top: 0, height: '10px', borderRadius: '5px' });
    h('div', null, track, { position: 'absolute', left: `${336 * 0.8 - 1}px`, top: '-4px', width: '2px', height: '18px', borderRadius: '1px', background: 'rgba(14,17,23,0.18)' });
    return { c, detail, status, fill, m };
  });
  return { root, head, cards };
}

const FLOW = [
  { m: 'Apr', inc: 230000, exp: 154000 },
  { m: 'May', inc: 230000, exp: 172000 },
  { m: 'Jun', inc: 245000, exp: 198000 },
  { m: 'Jul', inc: 230000, exp: 161000 },
  { m: 'Aug', inc: 238000, exp: 209000 },
  { m: 'Sep', inc: 241250, exp: 167720 },
];
const WEEK = [42, 38, 51, 47, 96, 128, 64];

function buildReports(sc) {
  const root = h('div', 'scr', sc);
  statusBar(root);
  const head = h('div', null, root, { position: 'absolute', left: 0, top: 0, width: '400px', height: '100px' });
  tx(head, 24, 54, 'Reports', { size: 30, weight: 700, ls: -0.025 });
  const segWrap = h('div', null, root, { position: 'absolute', left: 0, top: 0, width: '400px', height: '150px' });
  segmented(segWrap, 16, 102, 368, ['Week', 'Month', 'Year']).set(1);
  const a = box(root, 16, 154, 368, 250);
  tx(a, 18, 16, 'Income and expenses', { size: 16, weight: 700 });
  const lg = h('div', null, a, { position: 'absolute', right: '18px', top: '19px', display: 'flex', gap: '12px', fontSize: '12px', color: UI.ink2 });
  [['Income', UI.green], ['Spent', '#FF6B5E']].forEach(([l, c]) => {
    const e = h('div', null, lg, { display: 'flex', alignItems: 'center', gap: '5px' });
    h('div', null, e, { width: '8px', height: '8px', borderRadius: '4px', background: c });
    h('span', null, e, null, l);
  });
  const barsA = FLOW.map((f, i) => {
    const gx = 26 + i * 55;
    const inc = h('div', null, a, { position: 'absolute', left: `${gx}px`, width: '17px', borderRadius: '6px', background: UI.green, bottom: '44px' });
    const exp = h('div', null, a, { position: 'absolute', left: `${gx + 21}px`, width: '17px', borderRadius: '6px', background: '#FF6B5E', bottom: '44px' });
    tx(a, gx + 19, 216, f.m, { size: 12, color: i === 5 ? UI.ink : UI.ink2, w: 50, align: 'center', weight: i === 5 ? 700 : 500 });
    return { inc, exp, f };
  });
  const b_ = box(root, 16, 420, 368, 196);
  tx(b_, 18, 16, 'By day of the week', { size: 16, weight: 700 });
  const wkMax = Math.max(...WEEK);
  const barsB = WEEK.map((v, i) => {
    const e = h('div', null, b_, { position: 'absolute', left: `${24 + i * 47}px`, width: '26px', borderRadius: '7px', bottom: '48px', background: '#D9E7FF' });
    tx(b_, 24 + i * 47 + 13, 154, 'MTWTFSS'[i], { size: 12, color: UI.ink2, w: 30, align: 'center', weight: 600 });
    return { e, v };
  });
  const satNote = tx(b_, 350, 18, 'Saturdays cost the most', { size: 12, color: '#E08600', w: 200, align: 'right', weight: 650 });
  const c = box(root, 16, 632, 368, 72);
  tx(c, 18, 14, 'Compared with August', { size: 16, weight: 600 });
  tx(c, 18, 38, 'Spent this month', { size: 13, color: UI.ink2 });
  const chipC = h('div', 'tnum', c, {
    position: 'absolute', right: '16px', top: '20px', height: '32px', borderRadius: '16px', padding: '0 12px', background: rgba(UI.green, 0.14),
    color: UI.green, fontSize: '15px', fontWeight: 700, lineHeight: '32px',
  }, '−20 %');
  return { root, head, segWrap, a, barsA, b_, barsB, wkMax, satNote, c, chipC };
}

function buildWorth(sc) {
  const root = h('div', 'scr', sc);
  statusBar(root);
  const head = h('div', null, root, { position: 'absolute', left: 0, top: 0, width: '400px', height: '100px' });
  tx(head, 24, 54, 'What you have', { size: 30, weight: 700, ls: -0.025 });
  const nw = box(root, 16, 106, 368, 132);
  tx(nw, 18, 18, 'Net worth', { size: 14, color: UI.ink2 });
  const nwVal = tx(nw, 18, 38, '', { size: 42, weight: 700, tnum: true, ls: -0.03 });
  tx(nw, 18, 94, `${eur(124000, { sign: true })} this month`, { size: 14, color: UI.green, weight: 600, tnum: true });
  const tile = (x, color, g, label, value, extra) => {
    const t_ = box(root, x, 254, 176, 150);
    const ic = h('div', null, t_, { position: 'absolute', left: '16px', top: '16px', width: '36px', height: '36px', borderRadius: '18px', background: rgba(color, 0.15), display: 'flex', alignItems: 'center', justifyContent: 'center' });
    glyph(ic, g, 20, color, 2.1);
    tx(t_, 16, 64, label, { size: 13, color: UI.ink2 });
    tx(t_, 16, 84, value, { size: 20, weight: 700, tnum: true, ls: -0.02 });
    if (extra) tx(t_, 16, 114, extra, { size: 13, color: UI.green, weight: 650, tnum: true });
    return t_;
  };
  const inAcc = tile(16, '#007AFF', 'wallet', 'In accounts', eur(858840));
  const inv = tile(208, '#34C759', 'trend', 'Invested', eur(1248000), '+8,4 %');
  const spk = s('svg', { width: 76, height: 40, viewBox: '0 0 76 40' }, inv);
  Object.assign(spk.style, { position: 'absolute', left: '86px', top: '100px', overflow: 'visible' });
  const spkPath = s('path', { d: 'M2 34 L12 30 L20 32 L30 22 L38 25 L48 16 L56 19 L66 9 L74 4', fill: 'none', stroke: UI.green, 'stroke-width': 2.6, 'stroke-linecap': 'round', 'stroke-linejoin': 'round', pathLength: 1 }, spk);
  const acc = box(root, 16, 420, 368, 176);
  const accRow = (y, color, g, name, sub) => {
    const ic = h('div', null, acc, { position: 'absolute', left: '16px', top: `${y + 14}px`, width: '38px', height: '38px', borderRadius: '19px', background: rgba(color, 0.15), display: 'flex', alignItems: 'center', justifyContent: 'center' });
    glyph(ic, g, 21, color, 2.1);
    tx(acc, 66, y + 14, name, { size: 16, weight: 600 });
    tx(acc, 66, y + 36, sub, { size: 13, color: UI.ink2 });
    return tx(acc, 352, y + 23, '', { size: 16, weight: 650, w: 140, align: 'right', tnum: true });
  };
  const mainBal = accRow(8, '#007AFF', 'wallet', 'Main account', 'Everyday');
  h('div', null, acc, { position: 'absolute', left: '66px', right: '0', top: '88px', height: '1px', background: UI.line });
  const savBal = accRow(96, '#00C7BE', 'bank', 'Savings', 'Rainy day');
  const pill = h('div', 'tnum', acc, {
    position: 'absolute', left: '150px', top: '70px', height: '34px', borderRadius: '17px', padding: '0 12px 0 8px', background: UI.blue,
    color: '#fff', fontSize: '15px', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '6px', boxShadow: '0 6px 14px rgba(0,122,255,0.3)',
  });
  glyph(h('div', null, pill, { display: 'flex' }), 'arrowDown', 18, '#fff', 2.6);
  h('span', null, pill, null, eur(50000));
  const tr = tx(root, 200, 612, 'Transfer to Savings', { size: 14, color: UI.ink2, w: 300, align: 'center' });
  return { root, head, nw, nwVal, inAcc, inv, spkPath, acc, mainBal, savBal, pill, tr };
}

function buildPrivacy(sc) {
  const lock = h('div', 'scr', sc, { background: UI.bg });
  statusBar(lock);
  const logoWrap = h('div', null, lock, { position: 'absolute', left: '150px', top: '200px', width: '100px', height: '100px' });
  makeRing(logoWrap, 100).draw(LOGO_COLORS.map((c) => ({ color: c, value: 1 })), { r: 36, th: 9, gap: 4 });
  tx(logoWrap, 50, 25, '¢', { size: 44, weight: 700, w: 60, align: 'center' });
  tx(lock, 200, 330, 'Cents is locked', { size: 24, weight: 700, w: 300, align: 'center' });
  tx(lock, 200, 364, 'Unlock with your fingerprint', { size: 15, color: UI.ink2, w: 300, align: 'center' });
  const fpBox = h('div', null, lock, {
    position: 'absolute', left: '150px', top: '600px', width: '100px', height: '100px', borderRadius: '50px', background: '#fff', boxShadow: UI.shadow,
  });
  const fpAt = () => h('div', null, fpBox, { position: 'absolute', left: '22px', top: '22px', width: '56px', height: '56px' });
  glyph(fpAt(), 'fingerprint', 56, '#C9CDD5', 1.9);
  const fp = glyph(fpAt(), 'fingerprint', 56, UI.blue, 1.9);
  const fpOk = h('div', null, fpBox, {
    position: 'absolute', right: '-4px', bottom: '-4px', width: '34px', height: '34px', borderRadius: '17px', background: UI.green,
    display: 'flex', alignItems: 'center', justifyContent: 'center', boxShadow: `0 0 0 4px ${UI.bg}`,
  });
  glyph(fpOk, 'check', 18, '#fff', 3);

  const back = h('div', 'scr', sc);
  statusBar(back);
  const head = h('div', null, back, { position: 'absolute', left: 0, top: 0, width: '400px', height: '110px' });
  tx(head, 24, 54, 'Backup', { size: 30, weight: 700, ls: -0.025 });
  tx(head, 24, 92, 'Everything lives on this phone', { size: 15, color: UI.ink2 });
  const drive = box(back, 16, 124, 368, 214);
  const cl = h('div', null, drive, { position: 'absolute', left: '16px', top: '16px', width: '40px', height: '40px', borderRadius: '20px', background: rgba(UI.blue, 0.12), display: 'flex', alignItems: 'center', justifyContent: 'center' });
  glyph(cl, 'cloud', 22, UI.blue, 2.1);
  tx(drive, 68, 17, 'Google Drive', { size: 17, weight: 650 });
  tx(drive, 68, 40, 'Daily, into the app’s own folder', { size: 13, color: UI.ink2 });
  h('div', null, drive, { position: 'absolute', left: '16px', right: '0', top: '72px', height: '1px', background: UI.line });
  tx(drive, 16, 90, 'Back up automatically', { size: 16 });
  const sw = toggle(drive, 300, 84);
  h('div', null, drive, { position: 'absolute', left: '16px', right: '0', top: '132px', height: '1px', background: UI.line });
  tx(drive, 16, 150, 'Last backup', { size: 16 });
  const last = tx(drive, 352, 150, 'Yesterday, 03:00', { size: 16, color: UI.ink2, w: 200, align: 'right' });
  const lastNow = tx(drive, 352, 150, 'Just now', { size: 16, color: UI.green, w: 200, align: 'right', weight: 650 });
  const now = button(back, 16, 354, 368, 54, 'Back up now', { bg: '#fff', color: UI.blue, shadow: UI.shadow, glyph: 'cloud', r: 20 });
  const exp = box(back, 16, 424, 368, 132);
  tx(exp, 16, 16, 'Export', { size: 17, weight: 650 });
  tx(exp, 16, 40, 'A file you keep: every table, or a spreadsheet.', { size: 13, color: UI.ink2 });
  button(exp, 16, 70, 162, 44, 'JSON', { bg: UI.fill, color: UI.ink, r: 14, size: 15 });
  button(exp, 190, 70, 162, 44, 'CSV', { bg: UI.fill, color: UI.ink, r: 14, size: 15 });
  return { lock, fp, fpOk, back, head, drive, sw, last, lastNow, now, exp };
}

/** Paint a category chip as picked (1) or not (0). */
function pickChip(ch, cat, p) {
  ch.style.background = mix(UI.fill, CAT[cat].color, p);
  ch.style.color = mix(UI.ink, '#FFFFFF', p);
  ch.dot.style.background = p > 0.5 ? 'rgba(255,255,255,0.25)' : rgba(CAT[cat].color, 0.16);
  ch.dot.querySelectorAll('path').forEach((pth) => pth.setAttribute('stroke', p > 0.5 ? '#FFFFFF' : CAT[cat].ink || CAT[cat].color));
}

// Moments, on the beat: each is so many beats into its shot.
const KEYS = [['4', at(SHOT.entry, 1)], ['2', at(SHOT.entry, 1.5)], [',', at(SHOT.entry, 2)], ['8', at(SHOT.entry, 2.5)], ['0', at(SHOT.entry, 3)]];
const T_CHIP = at(SHOT.entry, 3.75);
const T_ADD = at(SHOT.entry, 4.5);
const T_CAPTURE = at(SHOT.capture, 1.5);
const T_NOTE = [at(SHOT.capture, 2.5), at(SHOT.capture, 4)];
const T_LAND = [at(SHOT.capture, 4.75), at(SHOT.capture, 6.5)];
const T_FILE1 = at(SHOT.inbox, 1.5);
const T_FILE2 = at(SHOT.inbox, 3.5);
const T_GROUP = at(SHOT.inbox, 4.5);
const T_TOGGLE = at(SHOT.variable, 1.5);
const T_WAIT = at(SHOT.variable, 3);
const T_ENTER = at(SHOT.variable, 4);
const T_SAVE = at(SHOT.variable, 5.75);
const T_POSTPONE = at(SHOT.variable, 6.5);
const T_TR = S(SHOT.worth) + 1.9;
const T_FP = at(SHOT.privacy, 1.5);
const T_UNLOCK = at(SHOT.privacy, 2.6);
const T_BACKUP = at(SHOT.privacy, 4.5);

function scenePhone() {
  cue(S(SHOT.entry) + 0.02, 'sheet');
  KEYS.forEach(([k, t], i) => cue(t, 'key', { i }));
  cue(T_CHIP, 'select');
  cue(T_ADD + 0.55, 'saved');
  cue(T_CAPTURE, 'toggle');
  T_NOTE.forEach((tn, i) => cue(tn, 'incoming', { i }));
  T_LAND.forEach((tl, i) => cue(tl, 'captured', { i }));
  cue(T_FILE1 + 0.1, 'file', { i: 0 });
  cue(T_FILE2 + 0.02, 'pick');
  cue(T_GROUP + 0.1, 'file', { i: 1 });
  cue(T_GROUP + 0.75, 'done');
  cue(S(SHOT.subs) + 2.5, 'notify');
  cue(T_TOGGLE, 'toggle');
  cue(T_ENTER + 0.35, 'typing');
  cue(T_SAVE + 0.1, 'recorded');
  cue(T_POSTPONE + 0.1, 'postpone');
  BUDGETS.forEach((m, i) => {
    const f0 = S(SHOT.budgets) + 0.7 + i * 0.3;
    const cross = (x) => f0 + 1.3 * inverseInOutCubic(x);
    if (m.spent / m.limit >= 0.8) cue(cross((0.8 * m.limit) / m.spent), 'amber', { i });
    if (m.spent / m.limit >= 1) cue(cross(m.limit / m.spent), 'over', { i });
  });
  cue(S(SHOT.reports) + 0.5, 'bars');
  cue(S(SHOT.reports) + 2.6, 'highlight');
  cue(T_TR, 'transfer');
  cue(T_FP + 0.55, 'unlock');
  cue(T_BACKUP + 0.9, 'backedup');

  layer('phone', S(SHOT.month) - 1.2, S(SHOT.outro) + 1.9, (root) => {
    const ph = makePhone(root);
    const sc = ph.screen;
    const home = buildHome(sc);
    const sheet = buildSheet(sc);
    const inbox = buildInbox(sc);
    const groups = buildGroups(sc);
    const subs = buildSubs(sc);
    const vr = buildVariable(sc);
    const budgets = buildBudgets(sc);
    const reports = buildReports(sc);
    const worth = buildWorth(sc);
    const priv = buildPrivacy(sc);
    const touch = h('div', null, sc, {
      position: 'absolute', left: 0, top: 0, width: '48px', height: '48px', borderRadius: '24px', background: 'rgba(14,17,23,0.16)',
      boxShadow: '0 0 0 2.5px rgba(255,255,255,0.9), 0 4px 14px rgba(14,17,23,0.18)', zIndex: 80,
    });
    PHONE.groupsCard = groups.lis;
    PHONE.inboxRows = inbox.rows.map((r) => r.c);

    // Every tap, where the thing tapped actually is. [time, x, y] in screen pixels.
    const TAPS = [];
    const tapAt = (t, x, y) => {
      TAPS.push([t, x, y]);
      cue(t, 'tap');
    };
    const sheetTop = 64;
    tapAt(S(SHOT.entry) - 0.5, 200, 804);
    KEYS.forEach(([k, t]) => TAPS.push([t, sheet.keys[k].x, sheetTop + sheet.keys[k].y]));
    tapAt(T_CHIP, 22 + sheet.chipEls[0].offsetWidth / 2, sheetTop + 214 + 19);
    tapAt(T_ADD, 200, sheetTop + 578 + 29);
    tapAt(T_CAPTURE, 16 + 300 + 26, INBOX.capY + 20 + 16);
    const [row1, row2] = inbox.rows;
    const centre = (e) => 16 + e.offsetLeft + e.offsetWidth / 2;
    tapAt(T_FILE1, centre(row1.pickChip), ROW_TOP.filing[0] + 74 + 18);
    tapAt(T_FILE2, centre(row2.pickChip), ROW_TOP.filing[0] + 74 + 18);
    tapAt(T_GROUP, centre(row2.groupChip), ROW_TOP.filing[0] + 118 + 18);
    tapAt(T_TOGGLE, 16 + 18 + 3 + 332 * 0.75, 136 + 44 + 18);
    tapAt(T_ENTER, 16 + 262 + 45, 128 + 20 + 18);
    tapAt(T_SAVE, 16 + 16 + 336 - 46, 128 + 76 + 28);
    tapAt(T_POSTPONE, 16 + 16 + vr.postpone.offsetWidth / 2, 128 + 150 + 16 + 80 + 18);
    tapAt(T_FP, 200, 650);
    tapAt(T_BACKUP, 200, 354 + 27);

    return (t) => {
      const p = phonePose(t);
      ph.root.style.transform = `translate(${(p.x - 210).toFixed(2)}px,${(p.y - 444).toFixed(2)}px) rotateX(${p.rx.toFixed(3)}deg) rotateY(${p.ry.toFixed(3)}deg) scale(${p.s})`;

      // ---- Home: shots 1–2, and again for the ending.
      const second = t > S(SHOT.outro) - 0.7;
      const homeOn = (t > S(SHOT.month) - 1.2 && t < S(SHOT.capture) + 0.7) || (second && t > S(SHOT.outro) - 0.15);
      show(home.root, homeOn);
      if (homeOn) {
        const q = second ? 1 : ease.inOutCubic(prog(t, T_ADD + 0.55, T_ADD + 1.35));
        const hl = second ? 0 : ease.inOutCubic(prog(t, S(SHOT.month) + 1.6, S(SHOT.month) + 2.2)) * (1 - ease.inOutCubic(prog(t, S(SHOT.month) + 3.9, S(SHOT.month) + 4.5)));
        const grow = second ? 0 : ease.inOutCubic(prog(t, T_ADD + 0.55, T_ADD + 0.9)) * (1 - ease.inOutCubic(prog(t, T_ADD + 1.4, T_ADD + 1.9)));
        const ringOut = second ? 1 - ease.swift(prog(t, S(SHOT.outro) - 0.12, S(SHOT.outro) + 0.35)) : ease.inCubic(prog(t, S(SHOT.capture) - 0.2, S(SHOT.capture) + 0.25));
        choreo([home.head, home.list, home.bar], t, second ? S(SHOT.outro) - 0.12 : S(SHOT.month) - 0.75, second ? undefined : S(SHOT.capture) - 0.2, { stagger: second ? 0.06 : 0.12, dur: second ? 0.5 : 0.7 });
        const toastO = second ? 0 : ease.swift(prog(t, T_ADD + 0.5, T_ADD + 0.9)) * (1 - ease.inCubic(prog(t, S(SHOT.capture) - 0.75, S(SHOT.capture) - 0.45)));
        home.head.style.opacity = (parseFloat(home.head.style.opacity) * (1 - toastO)).toFixed(4);
        // The ring is the logo's until it lands; then it is the phone's, until it leaves again.
        show(home.ringWrap, t >= T_HAND && t < T_LIFT);
        home.ringWrap.style.opacity = (1 - ringOut).toFixed(3);
        home.ringWrap.style.transform = `scale(${(1 - 0.12 * ringOut).toFixed(4)})`;
        home.ring.draw(monthSlices(q), {
          r: HOME_RING.r, th: HOME_RING.th, gap: 6,
          pop: MONTH.map((m, i) => (i === 0 ? 7 * hl : i === 1 ? 7 * grow : 0)),
          alpha: MONTH.map((m, i) => (i === 0 ? 1 : 1 - 0.62 * hl)),
          thEach: MONTH.map((m, i) => HOME_RING.th + (i === 0 ? 4 * hl : i === 1 ? 4 * grow : 0)),
        });
        const centreO = (second ? 1 - prog(t, T_LIFT - 0.15, T_LIFT) : prog(t, S(SHOT.month) + 0.1, S(SHOT.month) + 0.5)) * (1 - ringOut);
        home.centre.style.opacity = centreO.toFixed(3);
        const totalNow = second ? TOTAL_AFTER : countUp(t, T_ADD + 0.55, T_ADD + 1.35, TOTAL_BEFORE, TOTAL_AFTER);
        home.total.textContent = eur(totalNow);
        MONTH.forEach((m, i) => {
          const v = second ? m.after : lerp(m.before, m.after, q);
          home.rows[i].amount.textContent = eur(v);
          home.rows[i].share.textContent = `${pct(v, totalNow)} %`;
          const rowIn = second ? 1 : ease.swift(prog(t, S(SHOT.month) + 0.15 + i * 0.07, S(SHOT.month) + 0.85 + i * 0.07));
          const hlRow = i === 0 ? hl : i === 1 ? grow : 0;
          home.rows[i].r.style.opacity = rowIn.toFixed(3);
          home.rows[i].r.style.transform = `translateY(${((1 - rowIn) * 16).toFixed(2)}px)`;
          home.rows[i].r.style.background = hlRow > 0 ? rgba(CAT[m.cat].color, 0.09 * hlRow) : 'transparent';
        });
        const tt = ease.swift(prog(t, T_ADD + 0.55, T_ADD + 1.0)) * (1 - ease.inCubic(prog(t, S(SHOT.capture) - 0.75, S(SHOT.capture) - 0.45)));
        show(home.toast, !second && tt > 0);
        home.toast.style.opacity = tt.toFixed(3);
        home.toast.style.transform = `translateY(${(-20 * (1 - tt)).toFixed(2)}px) scale(${(0.9 + 0.1 * tt).toFixed(3)})`;
        const press = 1 - 0.08 * (prog(t, S(SHOT.entry) - 0.56, S(SHOT.entry) - 0.5) * (1 - prog(t, S(SHOT.entry) - 0.4, S(SHOT.entry) - 0.3)));
        home.add.style.transform = `scale(${press.toFixed(4)})`;
      }

      // ---- The entry sheet: rises, takes the keys, and goes.
      const up = spring(t - S(SHOT.entry), 2.1, 0.86);
      const down = ease.inCubic(prog(t, T_ADD + 0.2, T_ADD + 0.6));
      const sheetOn = t > S(SHOT.entry) && down < 1;
      show(sheet.sheet, sheetOn);
      show(sheet.dim, sheetOn);
      if (sheetOn) {
        placeTL(sheet.sheet, 0, lerp(820, 0, clamp(up, 0, 1.2)) + 820 * down);
        sheet.dim.style.opacity = (0.28 * clamp(up) * (1 - down)).toFixed(3);
        let typed = 0;
        KEYS.forEach(([k, kt], i) => {
          const key = sheet.keys[k].el;
          const pr = prog(t, kt - 0.04, kt) * (1 - prog(t, kt + 0.1, kt + 0.3));
          key.style.background = mix(UI.fill, '#D8DCE3', pr);
          key.style.transform = `scale(${(1 - 0.05 * pr).toFixed(4)})`;
          if (t >= kt) typed = i + 1;
          const ch = sheet.typed[i];
          show(ch, t >= kt);
          if (t >= kt) {
            const k2 = spring(t - kt, 3.2, 0.6);
            ch.style.transform = `translateY(${(18 * (1 - k2)).toFixed(2)}px) scale(${(0.6 + 0.4 * k2).toFixed(4)})`;
            ch.style.opacity = clamp(k2 * 2).toFixed(3);
          }
        });
        show(sheet.zero, typed === 0);
        pickChip(sheet.chipEls[0], 'groceries', ease.swift(prog(t, T_CHIP, T_CHIP + 0.35)));
        sheet.chipEls[0].style.transform = `scale(${(1 + 0.06 * Math.sin(Math.PI * prog(t, T_CHIP, T_CHIP + 0.3))).toFixed(4)})`;
        const bp = prog(t, T_ADD - 0.04, T_ADD) * (1 - prog(t, T_ADD + 0.1, T_ADD + 0.25));
        sheet.addBtn.style.transform = `scale(${(1 - 0.04 * bp).toFixed(4)})`;
      }

      // ---- Capture, then To categorize: one screen across two shots. The switch goes on, the
      // bank's notifications land as movements, then each is given a category — and a group.
      const inbOn = t > S(SHOT.capture) - 0.3 && t < S(SHOT.groups) + 0.2;
      show(inbox.root, inbOn);
      if (inbOn) {
        choreo([inbox.head], t, S(SHOT.capture) - 0.05, S(SHOT.groups) - 0.45);
        // The switch: in with the screen, folded away when filing starts.
        const fold = ease.inOutCubic(prog(t, S(SHOT.inbox) - 0.4, S(SHOT.inbox) + 0.3));
        const capIn = ease.swift(prog(t, S(SHOT.capture) + 0.05, S(SHOT.capture) + 0.75));
        inbox.cap.style.opacity = (capIn * (1 - fold)).toFixed(4);
        inbox.cap.style.transform = `translateY(${((1 - capIn) * 26 - 18 * fold).toFixed(2)}px) scale(${(1 - 0.04 * fold).toFixed(4)})`;
        const on = ease.inOutCubic(prog(t, T_CAPTURE + 0.02, T_CAPTURE + 0.3));
        inbox.sw.set(on);
        inbox.check.color(mix(UI.ink3, UI.blue, on));
        inbox.empty.style.opacity = (capIn * (1 - prog(t, T_LAND[0] - 0.25, T_LAND[0]))).toFixed(3);
        const footIn = ease.swift(prog(t, S(SHOT.capture) + 0.3, S(SHOT.capture) + 1.0));
        inbox.foot.style.opacity = (footIn * (1 - prog(t, S(SHOT.inbox) - 0.55, S(SHOT.inbox) - 0.25))).toFixed(3);

        // The movements: each lands from its notification, then they move up and open.
        const [r1, r2] = inbox.rows;
        const pick = (chipEl, cat, tf) => {
          const sel = ease.swift(prog(t, tf, tf + 0.3));
          pickChip(chipEl, cat, sel);
          chipEl.style.transform = `scale(${(1 + 0.06 * Math.sin(Math.PI * prog(t, tf, tf + 0.3))).toFixed(4)})`;
          return sel;
        };
        const picked = [pick(r1.pickChip, r1.m.pick, T_FILE1), pick(r2.pickChip, r2.m.pick, T_FILE2)];
        pick(r2.groupChip, r2.m.group, T_GROUP);
        const gone = [ease.inCubic(prog(t, T_FILE1 + 0.5, T_FILE1 + 0.9)), ease.inCubic(prog(t, T_GROUP + 0.5, T_GROUP + 0.9))];
        const lift = spring(t - (T_FILE1 + 0.85), 2.4, 0.78);
        inbox.rows.forEach((r, i) => {
          r.pickedIcon.style.opacity = picked[i].toFixed(3);
          r.q.style.opacity = (1 - picked[i]).toFixed(3);
          const land = ease.swift(prog(t, T_LAND[i] - 0.1, T_LAND[i] + 0.3));
          r.c.style.top = `${lerp(ROW_TOP.capture[i], ROW_TOP.filing[i], fold).toFixed(2)}px`;
          r.c.style.height = `${lerp(INBOX.rowH, INBOX.fullH, fold).toFixed(2)}px`;
          r.choices.forEach((e) => (e.style.opacity = clamp(fold * 1.6).toFixed(3)));
          const up = i === 1 ? -(ROW_TOP.filing[1] - ROW_TOP.filing[0]) * clamp(lift, 0, 1.1) : 0;
          r.c.style.opacity = land.toFixed(4);
          r.c.style.transform = `translate(${(-430 * gone[i]).toFixed(2)}px,${(up + 10 * (1 - land)).toFixed(2)}px) scale(${(1 + 0.03 * bump(t - T_LAND[i])).toFixed(4)})`;
        });
        const waiting = [t >= T_LAND[0], t >= T_LAND[1], t < T_FILE1 + 0.5, t < T_GROUP + 0.5];
        inbox.badge.textContent = String(Number(waiting[0] && waiting[2]) + Number(waiting[1] && waiting[3]));
        inbox.badge.style.background = t < T_GROUP + 0.5 ? UI.blue : UI.green;
        const bIn = clamp(spring(t - T_LAND[0], 3, 0.55), 0, 1.3);
        const bb = bump(t - T_LAND[1]) + bump(t - (T_FILE1 + 0.5)) + bump(t - (T_GROUP + 0.5));
        inbox.badge.style.transform = `scale(${(bIn * (1 + 0.18 * bb)).toFixed(4)})`;
        const dn = spring(t - (T_GROUP + 0.75), 2.6, 0.62);
        show(inbox.done, t > T_GROUP + 0.75);
        inbox.done.style.opacity = (clamp(dn * 2) * (1 - ease.inCubic(prog(t, S(SHOT.groups) - 0.45, S(SHOT.groups) - 0.1)))).toFixed(3);
        inbox.doneCircle.style.transform = `scale(${clamp(dn, 0, 1.3).toFixed(4)})`;
        inbox.doneTick.draw(prog(t, T_GROUP + 0.9, T_GROUP + 1.3));
      }

      // ---- Groups: the trip's total climbs as its movements arrive.
      const grOn = t > S(SHOT.groups) - 0.3 && t < S(SHOT.subs) + 0.2;
      show(groups.root, grOn);
      if (grOn) {
        let sum = 0;
        let x = 0;
        TRIP.forEach((m, i) => {
          const k = ease.swift(prog(t, GROUP_ARRIVE(i), GROUP_ARRIVE(i) + 0.5));
          sum += m.v * k;
          const w = 332 * (m.v / TRIP_TOTAL) * k;
          groups.segs[i].style.left = `${x.toFixed(2)}px`;
          groups.segs[i].style.width = `${Math.max(0, w - (i < TRIP.length - 1 ? 2 : 0)).toFixed(2)}px`;
          x += w;
          groups.legend[i].style.opacity = (0.35 + 0.65 * k).toFixed(3);
        });
        // Ana's half of the hotel arrives last, and brings the cost down.
        const tb = GROUP_ARRIVE(TRIP.length);
        const back = ease.swift(prog(t, tb, tb + 0.5));
        sum -= REFUND.v * back;
        groups.total.textContent = eur(sum);
        groups.total.style.color = mix(UI.ink, UI.green, prog(t, tb, tb + 0.12) * (1 - prog(t, tb + 0.7, tb + 1.2)));
        groups.split.style.opacity = back.toFixed(3);
        groups.split.style.transform = `translateY(${(6 * (1 - back)).toFixed(2)}px)`;
        const lb = [...TRIP, REFUND].reduce((a, m, i) => a + bump(t - GROUP_ARRIVE(i)), 0);
        groups.lis.extraTf = ` scale(${(1 + 0.012 * lb).toFixed(4)})`;
        choreo([groups.head, groups.lis, groups.move, groups.job], t, S(SHOT.groups) - 0.05, S(SHOT.subs) - 0.45, { stagger: 0.12 });
      }

      // ---- Subscriptions.
      const suOn = t > S(SHOT.subs) - 0.3 && t < S(SHOT.variable) + 0.2;
      show(subs.root, suOn);
      show(subs.note, suOn && t > S(SHOT.subs) + 2.45);
      if (suOn) {
        choreo([subs.head, subs.sum, ...subs.rows], t, S(SHOT.subs) - 0.05, S(SHOT.variable) - 0.45, { stagger: 0.07 });
        const mv = countUp(t, S(SHOT.subs) + 0.4, S(SHOT.subs) + 1.6, 0, SUBS_MONTH);
        subs.month.textContent = eur(mv);
        subs.year.textContent = `${eur(mv * 12)} a year`;
        const nd = spring(t - (S(SHOT.subs) + 2.5), 2.2, 0.8);
        const nu = ease.inCubic(prog(t, S(SHOT.variable) - 0.75, S(SHOT.variable) - 0.4));
        subs.note.style.transform = `translateY(${(lerp(-120, 0, clamp(nd, 0, 1.1)) - 130 * nu).toFixed(2)}px)`;
      }

      // ---- Variable amounts: the rule, then what is waiting.
      const vaOn = t > S(SHOT.variable) - 0.3 && t < S(SHOT.budgets) + 0.2;
      show(vr.edit, vaOn && t < T_WAIT + 0.4);
      show(vr.wait, vaOn && t > T_WAIT - 0.1);
      if (vaOn) {
        choreo([vr.eHead, vr.amt, vr.more], t, S(SHOT.variable) - 0.05, T_WAIT - 0.35, { stagger: 0.1 });
        vr.seg.set(ease.inOutCubic(prog(t, T_TOGGLE, T_TOGGLE + 0.35)));
        vr.fixed.style.opacity = (1 - prog(t, T_TOGGLE + 0.05, T_TOGGLE + 0.3)).toFixed(3);
        const ak = ease.swift(prog(t, T_TOGGLE + 0.2, T_TOGGLE + 0.7));
        vr.ask.style.opacity = ak.toFixed(3);
        vr.ask.style.transform = `translateY(${(10 * (1 - ak)).toFixed(2)}px)`;

        choreo([vr.wHead, vr.sal, vr.elec, vr.foot], t, T_WAIT, S(SHOT.budgets) - 0.45, { stagger: 0.12 });
        // Enter opens a field under the salary; the figure is typed; Save records it.
        const open = ease.inOutCubic(prog(t, T_ENTER, T_ENTER + 0.35));
        vr.enter.style.opacity = (1 - open).toFixed(3);
        const salH = lerp(76, 150, open);
        vr.sal.style.height = `${salH.toFixed(2)}px`;
        vr.elec.style.top = `${(128 + salH + 16).toFixed(2)}px`;
        const done = ease.swift(prog(t, T_SAVE + 0.05, T_SAVE + 0.45));
        vr.field.style.opacity = (open * (1 - done)).toFixed(3);
        const digits = '2.412,50';
        const nTyped = Math.floor(clamp((t - (T_ENTER + 0.35)) / 0.08, 0, digits.length));
        vr.fieldText.textContent = nTyped > 0 ? `${digits.slice(0, nTyped)} €` : '';
        vr.caret.style.opacity = nTyped < digits.length || Math.floor(t * 2.5) % 2 === 0 ? '1' : '0';
        const sb = prog(t, T_SAVE - 0.04, T_SAVE) * (1 - prog(t, T_SAVE + 0.08, T_SAVE + 0.22));
        vr.save.style.transform = `scale(${(1 - 0.06 * sb).toFixed(4)})`;
        vr.rec.style.opacity = done.toFixed(3);
        vr.rec.style.transform = `translateY(${(10 * (1 - done)).toFixed(2)}px)`;
        vr.salTint.style.opacity = done.toFixed(3);
        vr.salWhen.textContent = t > T_SAVE + 0.1 ? 'Income · Main account' : 'Due today · usually ~2.300 €';
        // Postpone moves the electricity three days on.
        const po = ease.inCubic(prog(t, T_POSTPONE + 0.05, T_POSTPONE + 0.2));
        const pp = ease.swift(prog(t, T_POSTPONE + 0.18, T_POSTPONE + 0.5));
        vr.elecWhen.style.opacity = (1 - po).toFixed(3);
        vr.elecWhen.style.transform = `translateY(${(-8 * po).toFixed(2)}px)`;
        vr.elecNew.style.opacity = pp.toFixed(3);
        vr.elecNew.style.transform = `translateY(${(8 * (1 - pp)).toFixed(2)}px)`;
        vr.foot.style.top = `${(128 + salH + 16 + 134 + 18).toFixed(2)}px`;
        const pk = spring(t - (T_POSTPONE + 0.15), 3, 0.55);
        vr.plus3.style.opacity = clamp(pk * 2).toFixed(3);
        vr.plus3.style.transform = `scale(${clamp(pk, 0, 1.3).toFixed(4)})`;
        vr.plus3.style.left = `${68 + vr.elecNew.offsetWidth + 8}px`;
        const pb = prog(t, T_POSTPONE - 0.04, T_POSTPONE) * (1 - prog(t, T_POSTPONE + 0.08, T_POSTPONE + 0.25));
        vr.postpone.style.transform = `scale(${(1 - 0.06 * pb).toFixed(4)})`;
      }

      // ---- Budgets.
      const buOn = t > S(SHOT.budgets) - 0.3 && t < S(SHOT.reports) + 0.2;
      show(budgets.root, buOn);
      if (buOn) {
        budgets.cards.forEach((c, i) => {
          const f0 = S(SHOT.budgets) + 0.7 + i * 0.3;
          const spent = c.m.spent * ease.inOutCubic(prog(t, f0, f0 + 1.3));
          const f = spent / c.m.limit;
          c.fill.style.width = `${(336 * Math.min(1, f)).toFixed(2)}px`;
          const tint = budgetTint(f);
          c.fill.style.background = tint;
          c.detail.textContent = `${eur(spent)} of ${eur(c.m.limit)}`;
          const left = c.m.limit - spent;
          c.status.textContent = left >= 0 ? `${eur(left)} left` : `${eur(-left)} over`;
          c.status.style.color = f < 0.8 ? UI.ink : tint;
          const nudge = f > 1 && t > f0 + 1.2 ? Math.sin((t - f0) * 40) * 3 * Math.exp(-(t - (f0 + 1.25)) * 6) : 0;
          c.c.extraTf = ` translateX(${nudge.toFixed(2)}px)`;
        });
        choreo([budgets.head, ...budgets.cards.map((c) => c.c)], t, S(SHOT.budgets) - 0.05, S(SHOT.reports) - 0.45, { stagger: 0.09 });
      }

      // ---- Reports.
      const reOn = t > S(SHOT.reports) - 0.3 && t < S(SHOT.worth) + 0.2;
      show(reports.root, reOn);
      if (reOn) {
        choreo([reports.head, reports.segWrap, reports.a, reports.b_, reports.c], t, S(SHOT.reports) - 0.05, S(SHOT.worth) - 0.45, { stagger: 0.09 });
        reports.barsA.forEach((b2, i) => {
          const k = ease.swift(prog(t, S(SHOT.reports) + 0.5 + i * 0.1, S(SHOT.reports) + 1.4 + i * 0.1));
          b2.inc.style.height = `${(150 * (b2.f.inc / 250000) * k).toFixed(2)}px`;
          b2.exp.style.height = `${(150 * (b2.f.exp / 250000) * k).toFixed(2)}px`;
        });
        reports.barsB.forEach((b2, i) => {
          const k = ease.swift(prog(t, S(SHOT.reports) + 1.5 + i * 0.08, S(SHOT.reports) + 2.3 + i * 0.08));
          b2.e.style.height = `${(92 * (b2.v / reports.wkMax) * k).toFixed(2)}px`;
          const sat = i === 5 ? ease.inOutCubic(prog(t, S(SHOT.reports) + 2.6, S(SHOT.reports) + 3.0)) : 0;
          b2.e.style.background = i === 5 ? mix('#D9E7FF', UI.amber, sat) : '#D9E7FF';
        });
        reports.satNote.style.opacity = ease.swift(prog(t, S(SHOT.reports) + 2.7, S(SHOT.reports) + 3.3)).toFixed(3);
        const cp = spring(t - (S(SHOT.reports) + 3.1), 2.8, 0.6);
        reports.chipC.style.transform = `scale(${clamp(cp, 0, 1.2).toFixed(4)})`;
        reports.chipC.style.opacity = clamp(cp * 2).toFixed(3);
      }

      // ---- What you have.
      const woOn = t > S(SHOT.worth) - 0.3 && t < S(SHOT.privacy) + 0.2;
      show(worth.root, woOn);
      if (woOn) {
        choreo([worth.head, worth.nw, worth.inAcc, worth.inv, worth.acc], t, S(SHOT.worth) - 0.05, S(SHOT.privacy) - 0.45, { stagger: 0.08 });
        worth.nwVal.textContent = eur(countUp(t, S(SHOT.worth) + 0.35, S(SHOT.worth) + 1.6, 1900000, 2106840));
        const mv = ease.inOutCubic(prog(t, T_TR, T_TR + 0.8));
        worth.pill.style.opacity = (prog(t, T_TR - 0.15, T_TR) * (1 - prog(t, T_TR + 0.8, T_TR + 1.0))).toFixed(3);
        worth.pill.style.transform = `translateY(${lerp(-44, 30, mv).toFixed(2)}px)`;
        const q = ease.outCubic(prog(t, T_TR + 0.55, T_TR + 1.2));
        worth.mainBal.textContent = eur(lerp(288840, 238840, q));
        worth.savBal.textContent = eur(lerp(570000, 620000, q));
        worth.spkPath.style.strokeDasharray = '1 1';
        worth.spkPath.style.strokeDashoffset = String(1 - ease.inOutCubic(prog(t, S(SHOT.worth) + 1.0, S(SHOT.worth) + 2.0)));
        const tb = ease.swift(prog(t, T_TR + 0.3, T_TR + 0.8));
        worth.tr.style.opacity = (tb * (1 - ease.inCubic(prog(t, S(SHOT.privacy) - 0.45, S(SHOT.privacy) - 0.1)))).toFixed(3);
      }

      // ---- Privacy: the lock opens, and the backup runs.
      const prOn = t > S(SHOT.privacy) - 0.3 && t < S(SHOT.outro) + 0.2;
      show(priv.lock, prOn && t < T_UNLOCK + 0.7);
      show(priv.back, prOn && t > T_UNLOCK);
      if (prOn) {
        const lin = ease.swift(prog(t, S(SHOT.privacy) - 0.05, S(SHOT.privacy) + 0.6));
        const lout = ease.inOutCubic(prog(t, T_UNLOCK, T_UNLOCK + 0.55));
        priv.lock.style.opacity = lin.toFixed(3);
        priv.lock.style.transform = `translateY(${(-900 * lout).toFixed(2)}px)`;
        priv.fp.draw(prog(t, T_FP, T_FP + 0.45));
        priv.fp.color(mix(UI.blue, UI.green, prog(t, T_FP + 0.45, T_FP + 0.6)));
        const ok = spring(t - (T_FP + 0.55), 3, 0.55);
        priv.fpOk.style.transform = `scale(${clamp(ok, 0, 1.3).toFixed(4)})`;
        priv.fpOk.style.opacity = t > T_FP + 0.55 ? '1' : '0';
        priv.sw.set(1);
        const bk = ease.inOutCubic(prog(t, T_BACKUP + 0.1, T_BACKUP + 0.9));
        priv.last.style.opacity = (1 - prog(t, T_BACKUP + 0.8, T_BACKUP + 1.0)).toFixed(3);
        priv.lastNow.style.opacity = prog(t, T_BACKUP + 0.9, T_BACKUP + 1.1).toFixed(3);
        const nb = prog(t, T_BACKUP - 0.04, T_BACKUP) * (1 - prog(t, T_BACKUP + 0.1, T_BACKUP + 0.3));
        priv.now.extraTf = ` scale(${(1 - 0.04 * nb).toFixed(4)})`;
        priv.now.style.background = `linear-gradient(90deg, ${rgba(UI.blue, 0.1)} ${(bk * 100).toFixed(1)}%, #fff ${(bk * 100).toFixed(1)}%)`;
        choreo([priv.head, priv.drive, priv.now, priv.exp], t, T_UNLOCK + 0.15, S(SHOT.outro) - 0.45, { stagger: 0.09 });
      }

      // ---- The finger.
      let best = null;
      TAPS.forEach(([tt, x, y]) => {
        const o = prog(t, tt - 0.12, tt - 0.02) * (1 - prog(t, tt + 0.1, tt + 0.3));
        if (o > 0 && (!best || o > best.o)) best = { o, x, y, tt };
      });
      show(touch, !!best);
      if (best) place(touch, best.x, best.y, { s: 1.15 - 0.2 * prog(t, best.tt - 0.05, best.tt), o: best.o });
    };
  }, 'layer3d');
}

/** Where x sits on inOutCubic's curve — for cueing the moment a bar crosses a line. */
function inverseInOutCubic(y) {
  let lo = 0;
  let hi = 1;
  for (let i = 0; i < 40; i++) {
    const mid = (lo + hi) / 2;
    if (ease.inOutCubic(mid) < y) lo = mid;
    else hi = mid;
  }
  return (lo + hi) / 2;
}

// ---------------------------------------------------------------------------------------------
// Overlays: the note that says what matters, and a trip's movements flying into their group.
// ---------------------------------------------------------------------------------------------

function sceneOverlays() {
  [...TRIP, REFUND].forEach((_, i) => cue(GROUP_ARRIVE(i), 'collect', { i }));
  cue(S(SHOT.month) + 1.9, 'insight');

  layer('overlays', S(SHOT.month), S(SHOT.variable), (root) => {
    // "Most spent", beside the ring, while the rest of the ring steps back.
    const call = h('div', null, root, {
      position: 'absolute', left: 0, top: 0, width: '300px', height: '124px', borderRadius: '26px', background: '#fff',
      boxShadow: '0 20px 50px rgba(16,24,40,0.14), 0 4px 12px rgba(16,24,40,0.06)',
    });
    tx(call, 20, 18, 'MOST SPENT THIS MONTH', { size: 12, weight: 650, color: UI.ink2, ls: 0.1 });
    catIcon(call, 'housing', 20, 46, 44, { solid: true });
    tx(call, 76, 46, 'Housing', { size: 22, weight: 700, ls: -0.02 });
    tx(call, 76, 76, `${eur(65000)} · ${pct(65000, TOTAL_BEFORE)} %`, { size: 15, color: UI.ink2, tnum: true });

    // The trip's movements, waiting beside the phone, then collected one by one.
    const chips = [...TRIP, REFUND].map((m) => {
      const e = h('div', null, root, {
        position: 'absolute', left: 0, top: 0, height: '60px', borderRadius: '30px', background: '#fff', display: 'flex', alignItems: 'center',
        gap: '12px', padding: '0 22px 0 10px', boxShadow: '0 14px 34px rgba(16,24,40,0.13), 0 3px 8px rgba(16,24,40,0.06)', whiteSpace: 'nowrap',
      });
      const ic = h('div', null, e, { width: '40px', height: '40px', borderRadius: '20px', background: rgba(CAT[m.cat].color, 0.15), display: 'flex', alignItems: 'center', justifyContent: 'center' });
      glyph(ic, CAT[m.cat].glyph, 22, CAT[m.cat].color, 2.1);
      h('span', null, e, { fontSize: '19px', fontWeight: 650, color: UI.ink }, m.name);
      const back = m === REFUND;
      h('span', 'tnum', e, { fontSize: '19px', fontWeight: 600, color: back ? UI.green : UI.ink2 }, back ? eur(m.v, { sign: true }) : eur(-m.v));
      return e;
    });
    const spots = [[1700, 236], [1752, 358], [1690, 480], [1760, 602], [1702, 724], [1748, 846]];

    // The bank's notifications, beside the phone, until Cents takes them in.
    const notes = CAPTURED.map((m) => {
      const e = h('div', null, root, {
        position: 'absolute', left: 0, top: 0, width: '350px', height: '128px', borderRadius: '30px', background: '#fff',
        boxShadow: '0 20px 50px rgba(16,24,40,0.15), 0 4px 12px rgba(16,24,40,0.06)',
      });
      const ic = h('div', null, e, {
        position: 'absolute', left: '22px', top: '20px', width: '32px', height: '32px', borderRadius: '10px', background: BANK,
        display: 'flex', alignItems: 'center', justifyContent: 'center',
      });
      glyph(ic, 'bank', 20, '#fff', 2);
      tx(e, 66, 26, 'YOUR BANK', { size: 14, weight: 650, color: UI.ink2, ls: 0.06 });
      tx(e, 328, 26, 'now', { size: 14, color: UI.ink2, w: 60, align: 'right' });
      tx(e, 22, 64, m.note[0], { size: 20, weight: 700 });
      tx(e, 22, 92, m.note[1], { size: 18, tnum: true });
      return e;
    });
    const noteSpots = [[1721, 318], [1721, 488]];

    // The Netflix reminder, lifted out of the phone so it can be read.
    const note = h('div', null, root, {
      position: 'absolute', left: 0, top: 0, width: '350px', height: '128px', borderRadius: '30px', background: '#fff',
      boxShadow: '0 20px 50px rgba(16,24,40,0.15), 0 4px 12px rgba(16,24,40,0.06)',
    });
    const nIc = h('div', null, note, { position: 'absolute', left: '22px', top: '20px', width: '32px', height: '32px', borderRadius: '10px', background: UI.ink });
    makeRing(nIc, 32).draw(LOGO_COLORS.map((c) => ({ color: c, value: 1 })), { r: 10, th: 3.6, gap: 1.4 });
    tx(note, 66, 26, 'CENTS', { size: 14, weight: 650, color: UI.ink2, ls: 0.06 });
    tx(note, 328, 26, 'now', { size: 14, color: UI.ink2, w: 60, align: 'right' });
    tx(note, 22, 64, 'Netflix', { size: 20, weight: 700 });
    tx(note, 22, 92, `${eur(1299)}, in 3 days.`, { size: 18 });

    return (t) => {
      const ci = spring(t - (S(SHOT.month) + 1.85), 2.2, 0.75);
      const co = ease.inCubic(prog(t, S(SHOT.month) + 3.9, S(SHOT.month) + 4.3));
      show(call, t > S(SHOT.month) + 1.8 && co < 1);
      call.style.transform = `translate(${(1546 - 40 * (1 - clamp(ci))).toFixed(2)}px,330px) scale(${(1.1 * (0.94 + 0.06 * clamp(ci))).toFixed(4)})`;
      call.style.opacity = (clamp(ci * 1.6) * (1 - co)).toFixed(3);

      const ni = spring(t - (S(SHOT.subs) + 2.65), 2.2, 0.75);
      const no = ease.inCubic(prog(t, S(SHOT.variable) - 0.8, S(SHOT.variable) - 0.45));
      show(note, t > S(SHOT.subs) + 2.6 && no < 1);
      note.style.transform = `translate(${(1546 - 40 * (1 - clamp(ni))).toFixed(2)}px,190px) scale(${(0.94 + 0.06 * clamp(ni)).toFixed(4)})`;
      note.style.opacity = (clamp(ni * 1.6) * (1 - no)).toFixed(3);

      notes.forEach((e, i) => {
        const ni = spring(t - T_NOTE[i], 2.2, 0.75);
        const fly = ease.inOutCubic(prog(t, T_LAND[i] - 0.62, T_LAND[i]));
        const on = t > T_NOTE[i] - 0.02 && t < T_LAND[i] + 0.02;
        show(e, on);
        if (!on) return;
        const row = PHONE.inboxRows ? stageRect(PHONE.inboxRows[i]) : null;
        const [sx, sy] = noteSpots[i];
        const x = lerp(sx - 40 * (1 - clamp(ni)), row ? row.cx : sx, fly);
        const y = lerp(sy, row ? row.cy : sy, fly) + 40 * Math.sin(Math.PI * fly);
        place(e, x, y, { s: (0.94 + 0.06 * clamp(ni)) * lerp(1, 0.9, fly), o: clamp(ni * 1.6) * (1 - prog(fly, 0.7, 1)) });
      });

      const card = PHONE.groupsCard && t > S(SHOT.groups) - 0.2 && t < S(SHOT.subs) ? stageRect(PHONE.groupsCard) : null;
      chips.forEach((e, i) => {
        const appear = spring(t - (S(SHOT.groups) + 0.35 + i * 0.09), 2.4, 0.7);
        const arrive = GROUP_ARRIVE(i);
        const fly = ease.inOutCubic(prog(t, arrive - 0.62, arrive));
        const on = t > S(SHOT.groups) + 0.3 && t < arrive + 0.02;
        show(e, on);
        if (!on) return;
        const [sx, sy] = spots[i];
        const tx_ = card ? card.left + (card.right - card.left) * 0.42 : sx;
        const ty_ = card ? card.top + (card.bottom - card.top) * 0.45 : sy;
        const x = lerp(sx, tx_, fly);
        const y = lerp(sy, ty_, fly) - 70 * Math.sin(Math.PI * fly);
        place(e, x, y, { s: lerp(0.9 + 0.1 * clamp(appear), 0.35, fly), o: clamp(appear * 1.5) * (1 - prog(fly, 0.8, 1)) });
      });
    };
  });
}

// ---------------------------------------------------------------------------------------------
// Assembly.
// ---------------------------------------------------------------------------------------------

function buildReel() {
  STAGE = document.getElementById('stage');
  sceneStage();
  sceneTitles();
  scenePhone();
  sceneOverlays();
  sceneLogo();
  CUES.sort((a, b_) => a.t - b_.t);
}
