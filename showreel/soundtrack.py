#!/usr/bin/env python3
"""
Cents — the film's soundtrack, synthesised from nothing.

    python3 showreel/soundtrack.py            reads build/cues.json, writes build/soundtrack.wav

Sixty-five seconds on the same 96 BPM grid the picture is cut to: twenty-six bars, a warm loop
in D major (Dmaj9 – Bm7 – Gmaj7 – A), calm enough to read over. Every sound effect is placed by a
cue the picture exported — a key pressed, a movement filed, a salary recorded — so the mix
cannot drift from the frame, and the interface's sounds are pitched to the chord they land in.

Bars 1–2    the logo: a pad, and a bell for every arc of the ring
Bars 3–4    the phone arrives; a heartbeat kick, a shaker, the bass
Bars 5–22   the features: a gentle groove, plucks and bells for what the app does
Bars 23–24  privacy: the drums step back
Bars 25–26  the ring leaves the phone and becomes the logo; the chord rings out
"""
import json
import os
import wave

import numpy as np
from scipy import signal

HERE = os.path.dirname(os.path.abspath(__file__))
SR = 48000
BPM = 96
BEAT = 60 / BPM
BARS = 26
DUR = 4 * BARS * BEAT  # 65 s
N = int(round(DUR * SR))
rng = np.random.default_rng(128)


def b(x):
    return x * BEAT


def midi(m):
    return 440.0 * 2 ** ((m - 69) / 12)


# ---------------------------------------------------------------------------------------------
# The bus: stereo, with a reverb send.
# ---------------------------------------------------------------------------------------------

dry = np.zeros((2, N))
wet = np.zeros((2, N))
duck_targets = np.zeros((2, N))  # pad, bass and arp: ducked by the kick


def pan_gains(p):
    a = (p + 1) * np.pi / 4
    return np.cos(a), np.sin(a)


def add(x, t0, gain=1.0, pan=0.0, send=0.0, bus=None):
    """Mix a mono (or stereo) buffer in at time t0 (seconds)."""
    start = int(round(t0 * SR))
    if start >= N:
        return
    if x.ndim == 1:
        lg, rg = pan_gains(pan)
        st = np.vstack([x * lg, x * rg])
    else:
        st = x
    fade = min(st.shape[1], int(0.003 * SR))
    st = st.copy()
    st[:, -fade:] *= np.linspace(1, 0, fade)  # never end a sound on a click
    if start < 0:
        st = st[:, -start:]
        start = 0
    end = min(N, start + st.shape[1])
    seg = st[:, : end - start] * gain
    (bus if bus is not None else dry)[:, start:end] += seg
    if send:
        wet[:, start:end] += seg * send


def t_axis(dur):
    return np.arange(int(dur * SR)) / SR


def env_exp(dur, decay, attack=0.002):
    t = t_axis(dur)
    e = np.exp(-t / decay)
    if attack > 0:
        e *= np.clip(t / attack, 0, 1)
    return e


def lowpass(x, f, order=2):
    sos = signal.butter(order, min(f, SR * 0.45), 'low', fs=SR, output='sos')
    return signal.sosfilt(sos, x)


def highpass(x, f, order=2):
    sos = signal.butter(order, f, 'high', fs=SR, output='sos')
    return signal.sosfilt(sos, x)


def bandpass(x, lo, hi, order=2):
    sos = signal.butter(order, [lo, min(hi, SR * 0.45)], 'band', fs=SR, output='sos')
    return signal.sosfilt(sos, x)


def sweep_filter(x, f0, f1, kind='band', q=1.6, curve=1.0, block=256):
    """A filter whose centre glides from f0 to f1 (exponentially) across the buffer."""
    y = np.zeros_like(x)
    zi = None
    n = len(x)
    for s0 in range(0, n, block):
        s1 = min(n, s0 + block)
        u = (s0 / max(1, n - 1)) ** curve
        fc = f0 * (f1 / f0) ** u
        if kind == 'band':
            lo, hi = fc / (1 + 1 / q), fc * (1 + 1 / q)
            sos = signal.butter(2, [max(20, lo), min(hi, SR * 0.45)], 'band', fs=SR, output='sos')
        elif kind == 'high':
            sos = signal.butter(2, min(fc, SR * 0.45), 'high', fs=SR, output='sos')
        else:
            sos = signal.butter(2, min(fc, SR * 0.45), 'low', fs=SR, output='sos')
        if zi is None:
            zi = np.zeros((sos.shape[0], 2))
        y[s0:s1], zi = signal.sosfilt(sos, x[s0:s1], zi=zi)
    return y


def saw(f, n, phase=0.0):
    """Band-limited sawtooth (polyBLEP)."""
    dt = f / SR
    ph = (phase + dt * np.arange(n)) % 1.0
    y = 2 * ph - 1
    m1 = ph < dt
    t1 = ph[m1] / dt
    y[m1] -= t1 + t1 - t1 * t1 - 1
    m2 = ph > 1 - dt
    t2 = (ph[m2] - 1) / dt
    y[m2] -= t2 * t2 + t2 + t2 + 1
    return y


# ---------------------------------------------------------------------------------------------
# Instruments.
# ---------------------------------------------------------------------------------------------

def kick(t0, gain=1.0):
    dur = 0.5
    t = t_axis(dur)
    f = 50 + 120 * np.exp(-t / 0.03) + 25 * np.exp(-t / 0.15)
    ph = 2 * np.pi * np.cumsum(f) / SR
    body = np.sin(ph) * np.exp(-t / 0.19) * np.clip(t / 0.0015, 0, 1)
    punch = 0.45 * np.sin(2 * np.pi * 175 * t) * np.exp(-t / 0.028)
    click = highpass(rng.standard_normal(len(t)), 2500) * np.exp(-t / 0.004) * 0.45
    x = np.tanh(1.6 * (body + punch + click)) * 0.95
    add(x, t0, gain * 0.72)


def clap(t0, gain=1.0):
    dur = 0.4
    t = t_axis(dur)
    noise = rng.standard_normal(len(t))
    e = np.zeros_like(t)
    for k, d in enumerate([0.0, 0.009, 0.019]):
        e += np.exp(-np.clip(t - d, 0, None) / (0.012 if k < 2 else 0.11)) * (t >= d)
    x = bandpass(noise, 900, 3200) * e * 0.7
    add(x, t0, gain, pan=0.05, send=0.28)


def hat(t0, gain=1.0, open_=False, pan=0.25):
    dur = 0.35 if open_ else 0.08
    t = t_axis(dur)
    x = highpass(rng.standard_normal(len(t)), 7500, order=4) * np.exp(-t / (0.14 if open_ else 0.022))
    add(x, t0, gain * 0.46, pan=pan, send=0.08)


def snare(t0, gain=1.0, pitch=1.0):
    dur = 0.25
    t = t_axis(dur)
    tone = np.sin(2 * np.pi * 190 * pitch * t) * np.exp(-t / 0.05)
    nz = bandpass(rng.standard_normal(len(t)), 1500 * pitch, 7000) * np.exp(-t / 0.07)
    add((tone * 0.4 + nz * 0.6) * 0.5, t0, gain, send=0.2)


def pluck(m, t0, gain=1.0, pan=0.0, decay=0.35, send=0.3, bright=1.0):
    """A soft mallet: two partials and a knock."""
    dur = decay * 5
    t = t_axis(dur)
    f = midi(m)
    x = (np.sin(2 * np.pi * f * t) * np.exp(-t / decay)
         + 0.35 * bright * np.sin(2 * np.pi * 2 * f * t) * np.exp(-t / (decay * 0.4))
         + 0.12 * bright * np.sin(2 * np.pi * 3.01 * f * t) * np.exp(-t / (decay * 0.18)))
    x *= np.clip(t / 0.002, 0, 1)
    knock = bandpass(rng.standard_normal(len(t)), 1500, 6000) * np.exp(-t / 0.006) * 0.15
    add((x + knock) * 0.46, t0, gain, pan=pan, send=send)


def bell(m, t0, gain=1.0, pan=0.0, decay=1.2, ratio=3.5, index=2.2, send=0.4):
    """FM bell: bright at the strike, pure as it rings."""
    dur = decay * 4
    t = t_axis(dur)
    f = midi(m)
    idx = index * np.exp(-t / (decay * 0.25))
    x = np.sin(2 * np.pi * f * t + idx * np.sin(2 * np.pi * f * ratio * t)) * np.exp(-t / decay)
    x *= np.clip(t / 0.001, 0, 1)
    add(x * 0.3, t0, gain, pan=pan, send=send)


def blip(f, t0, dur=0.06, gain=1.0, pan=0.0, send=0.15, glide=1.0):
    t = t_axis(dur)
    ff = f * glide ** (t / dur)
    ph = 2 * np.pi * np.cumsum(ff) / SR
    e = np.clip(t / 0.003, 0, 1) * np.clip((dur - t) / 0.02, 0, 1)
    add(np.sin(ph) * e * 0.25, t0, gain, pan=pan, send=send)


def click(t0, gain=1.0, f=1800, pan=0.0):
    t = t_axis(0.03)
    x = np.sin(2 * np.pi * f * t) * np.exp(-t / 0.004) + highpass(rng.standard_normal(len(t)), 3000) * np.exp(-t / 0.002) * 0.4
    add(x * 0.25, t0, gain, pan=pan, send=0.05)


def whoosh(t0, dur, f0, f1, gain=1.0, pan=0.0, shape='swell', send=0.25, q=1.4):
    t = t_axis(dur)
    x = sweep_filter(rng.standard_normal(len(t)), f0, f1, 'band', q=q)
    if shape == 'swell':  # grows into the hit
        e = (t / dur) ** 2.2 * np.clip((dur - t) / 0.015, 0, 1)
    elif shape == 'pass':  # rises and falls, a thing flying past
        e = np.sin(np.pi * t / dur) ** 1.5
    else:  # 'fade': starts strong and dies
        e = np.exp(-t / (dur * 0.35)) * np.clip(t / 0.01, 0, 1)
    add(x * e * 0.42, t0, gain, pan=pan, send=send)


def sub_boom(t0, gain=1.0, f_start=110, f_end=38, dur=1.2):
    t = t_axis(dur)
    f = f_end + (f_start - f_end) * np.exp(-t / 0.12)
    ph = 2 * np.pi * np.cumsum(f) / SR
    x = np.sin(ph) * np.exp(-t / 0.5) * np.clip(t / 0.003, 0, 1)
    add(np.tanh(1.4 * x) * 0.55, t0, gain)


def crash(t0, gain=1.0, dur=2.2):
    t = t_axis(dur)
    x = lowpass(highpass(rng.standard_normal(len(t)), 900), 9000) * np.exp(-t / 0.55)
    add(x * 0.2, t0, gain, pan=0.0, send=0.5)


def pad(chord, t0, dur, gain=1.0, cutoff=2400, attack=0.25, release=0.5, voices=3, detune=0.11):
    n = int((dur + release) * SR)
    t = np.arange(n) / SR
    x = np.zeros((2, n))
    for m in chord:
        for v in range(voices):
            cents = (v - (voices - 1) / 2) * detune * 100 / max(1, voices - 1) * 2
            f = midi(m) * 2 ** (cents / 1200)
            w = saw(f, n, phase=rng.random())
            p = (v - (voices - 1) / 2) / max(1, (voices - 1) / 2) * 0.6
            lg, rg = pan_gains(p)
            x[0] += w * lg
            x[1] += w * rg
    x = np.vstack([lowpass(x[0], cutoff, 2), lowpass(x[1], cutoff, 2)])
    e = np.clip(t / attack, 0, 1) * np.where(t > dur, np.exp(-(t - dur) / (release / 3)), 1.0)
    x *= e * 0.12 / np.sqrt(len(chord))
    add(x, t0, gain, bus=duck_targets)


def bass(m, t0, dur, gain=1.0):
    n = int(dur * SR)
    t = np.arange(n) / SR
    f = midi(m)
    x = 0.55 * np.sin(2 * np.pi * f * t) + 0.65 * lowpass(saw(f * 2, n), 1500)
    e = np.clip(t / 0.004, 0, 1) * np.clip((dur - t) / 0.02, 0, 1) * np.exp(-t / 0.4)
    add(np.tanh(1.2 * x * e) * 0.3, t0, gain, bus=duck_targets)


def arp_note(m, t0, gain=1.0, pan=0.0, stab=False):
    n = int((0.45 if stab else 0.3) * SR)
    t = np.arange(n) / SR
    f = midi(m)
    x = lowpass(saw(f, n) + 0.5 * saw(f * 1.004, n), 3200 if stab else 2600) * np.exp(-t / (0.14 if stab else 0.09))
    x *= np.clip(t / 0.002, 0, 1)
    if stab:  # stabs land on the kick, so they skip the sidechain
        add(x * 0.06, t0, gain, pan=pan, send=0.3)
    else:
        add(x * 0.105, t0, gain, pan=pan, bus=duck_targets)


# ---------------------------------------------------------------------------------------------
# The arrangement.
# ---------------------------------------------------------------------------------------------

BAR = 4 * BEAT
PRIV = BARS - 4  # the privacy shot: the drums step back
OUT = BARS - 2  # the ending: the ring becomes the logo again
CHORDS = {
    'D': [50, 57, 61, 64, 66],    # Dmaj9: D A C# E F#
    'Bm': [47, 54, 57, 62, 66],   # Bm11-ish: B F# A D F#
    'G': [43, 55, 59, 62, 66],    # Gmaj7: G G B D F#
    'A': [45, 57, 61, 64, 69],    # A: A A C# E A
    'Asus': [45, 57, 62, 64, 69],  # Asus4, resolving to A
}
LOOP = ['D', 'Bm', 'G', 'A']
ROOT = {'D': 38, 'Bm': 35, 'G': 43, 'A': 45}
ARP = {'D': [69, 73, 76, 78], 'Bm': [66, 69, 74, 78], 'G': [67, 71, 74, 78], 'A': [69, 73, 76, 81]}


def chord_at(bar):
    return LOOP[bar % 4]


# The pad, all the way through: soft in the intro, open in the middle, full at the end.
for bar in range(BARS):
    name = chord_at(bar)
    if bar == OUT:
        # The last two bars: one long chord for the logo.
        pad(CHORDS['D'] + [74], bar * BAR, 2 * BAR - 0.05, gain=1.35, cutoff=3600, attack=0.08, release=1.5)
        break
    cutoff = 900 if bar < 2 else 1800 if bar < 4 else 2600 if bar < PRIV else 2000
    gain = 0.8 if bar < 2 else 1.0
    notes = CHORDS['Asus'] if (name == 'A' and bar % 8 == 3) else CHORDS[name]
    pad(notes, bar * BAR, BAR, gain=gain, cutoff=cutoff, attack=0.9 if bar == 0 else 0.06, release=0.35)

# Drums: a heartbeat when the phone arrives (bar 3), the full, gentle kit from bar 5, a step
# back for privacy, and one last kick with the logo.
kicks = []
for bar in range(2, OUT + 1):
    for beat in (0, 2):
        at = bar * BAR + beat * BEAT
        if PRIV <= bar < OUT:
            continue  # privacy: no kick
        kicks.append(at)
        kick(at, gain=0.55 if bar < 4 else 0.7)
    if 4 <= bar < PRIV and bar % 2 == 1:
        kicks.append(bar * BAR + 3.5 * BEAT)
        kick(bar * BAR + 3.5 * BEAT, gain=0.45)
kicks.append(OUT * BAR)
kick(OUT * BAR, gain=0.8)
for bar in range(4, OUT):
    clap(bar * BAR + BEAT, gain=0.42)
    clap(bar * BAR + 3 * BEAT, gain=0.42)
# Shaker: eighths from bar 3, sixteenths in the middle.
for bar in range(2, OUT + 1):
    step = 0.25 if 6 <= bar < PRIV else 0.5
    k = 0
    while k * step < 4:
        at = bar * BAR + k * step * BEAT
        accent = 1.0 if abs((k * step) % 1 - 0.5) < 1e-6 else 0.55
        hat(at, gain=0.42 * accent * (0.8 if bar >= PRIV else 1.0), pan=0.3 if k % 2 else -0.25)
        k += 1
    if 12 <= bar < PRIV:
        hat(bar * BAR + 3.5 * BEAT, gain=0.45, open_=True, pan=0.2)

# Bass: roots on the beat, an octave answer on the off-beat, from bar 3 to the privacy break.
for bar in range(2, OUT):
    root = ROOT[chord_at(bar)]
    for beat in range(4):
        at = bar * BAR + beat * BEAT
        bass(root, at, BEAT * 0.8, gain=0.75 if bar < 4 else 0.9)
        if bar >= 4:
            bass(root + 12, at + 0.5 * BEAT, BEAT * 0.35, gain=0.3)
bass(38, OUT * BAR, 2 * BAR - 0.3, gain=0.9)

# A soft arpeggio through the middle of the film.
for bar in range(4, OUT):
    notes = ARP[chord_at(bar)]
    for k in range(8):
        at = bar * BAR + k * 0.5 * BEAT
        arp_note(notes[k % 4] + (12 if (k // 4) % 2 and bar >= 12 else 0), at, gain=0.75 if bar < PRIV else 0.55, pan=0.4 if k % 2 else -0.4)

# ---------------------------------------------------------------------------------------------
# Sound design, from the picture's cues. Everything the interface does has a sound, and every
# sound is a note of the chord it lands in.
# ---------------------------------------------------------------------------------------------

with open(os.path.join(HERE, 'build', 'cues.json')) as fh:
    cues = json.load(fh)['cues']

ARCS = [74, 78, 81, 85, 86, 90]
KEYS = [74, 76, 78, 81, 83]
COLLECT = [74, 76, 78, 81, 83, 86]


def tones(at, lo, n):
    """The first `n` notes of the chord sounding at `at`, from `lo` up: for a sound that must agree
    with whatever bar the picture put it in."""
    bar = int(at // BAR)
    pcs = {m % 12 for m in CHORDS['D' if bar >= OUT else chord_at(bar)]}
    out = []
    m = lo
    while len(out) < n:
        if m % 12 in pcs:
            out.append(m)
        m += 1
    return out


for c in cues:
    at, kind = c['t'], c['type']
    if kind == 'arc':
        bell(ARCS[c['i']], at, gain=0.55, pan=(c['i'] - 2.5) * 0.15, decay=1.4)
    elif kind == 'cent':
        if c.get('final'):
            for j, m in enumerate([74, 78, 81, 86]):
                bell(m, at + j * 0.05, gain=0.6, pan=(j - 1.5) * 0.2, decay=1.8)
        else:
            bell(86, at, gain=0.5, decay=1.2)
    elif kind == 'mark':
        whoosh(at - 0.1, 0.7, 2500, 500, gain=0.25, shape='pass')
    elif kind == 'tagline':
        bell(81, at + 0.1, gain=0.25, decay=1.5, index=1.2)
    elif kind == 'phoneIn':
        whoosh(at, 1.1, 300, 2600, gain=0.35, shape='pass', q=1.2)
    elif kind == 'land':
        pluck(74, at, gain=0.7, decay=0.5, bright=0.6, send=0.4)
        bell(86, at + 0.02, gain=0.3, decay=1.0)
    elif kind == 'lift':
        whoosh(at, 1.0, 500, 4000, gain=0.35, shape='pass', q=1.2)
    elif kind == 'title':
        whoosh(at - 0.05, 0.6, 1200, 5000, gain=0.12, shape='pass', q=1.6)
    elif kind == 'tap':
        click(at, gain=0.45, f=1500)
    elif kind == 'key':
        pluck(KEYS[c['i']], at, gain=0.75, pan=(c['i'] - 2) * 0.15, decay=0.26)
        click(at, gain=0.25, f=2200)
    elif kind == 'sheet':
        whoosh(at, 0.45, 500, 2500, gain=0.25, shape='pass')
    elif kind == 'select':
        pluck(81, at + 0.02, gain=0.55, decay=0.3)
    elif kind == 'saved':
        bell(81, at, gain=0.5, decay=0.9)
        bell(86, at + 0.12, gain=0.5, decay=1.2)
    elif kind == 'file':
        whoosh(at + 0.35, 0.4, 3000, 800, gain=0.2, shape='pass')
        pluck([78, 81][c['i']], at, gain=0.6, decay=0.3)
    elif kind == 'done':
        bell(86, at, gain=0.45, decay=1.0)
        bell(90, at + 0.1, gain=0.4, decay=1.2)
    elif kind == 'insight':
        bell(78, at, gain=0.35, decay=1.2)
    elif kind == 'collect':
        pluck(COLLECT[c['i']], at, gain=0.65, pan=0.1, decay=0.32)
        whoosh(at - 0.6, 0.6, 900, 3500, gain=0.12, shape='swell')
        if c['i'] == len(COLLECT) - 1:
            bell(tones(at, 84, 1)[0], at + 0.05, gain=0.35, decay=1.0)  # money coming back
    elif kind == 'incoming':
        a, b_ = tones(at, 84 + 2 * c['i'], 2)
        bell(a, at, gain=0.45, decay=0.8, pan=0.3)
        bell(b_, at + 0.12, gain=0.4, decay=1.1, pan=0.3)
    elif kind == 'captured':
        whoosh(at - 0.55, 0.55, 3500, 900, gain=0.14, shape='swell')
        pluck(tones(at, 76 + 3 * c['i'], 1)[0], at, gain=0.6, decay=0.3)
    elif kind == 'pick':
        pluck(tones(at, 79, 1)[0], at, gain=0.5, decay=0.28)
    elif kind == 'notify':
        bell(88, at, gain=0.5, decay=0.8)
        bell(93, at + 0.14, gain=0.45, decay=1.1)
    elif kind == 'toggle':
        click(at + 0.05, gain=0.4, f=1100)
        blip(midi(81), at + 0.08, dur=0.07, gain=0.4, glide=1.1)
    elif kind == 'typing':
        for j in range(8):
            click(at + j * 0.08, gain=0.22, f=2400 + 60 * j)
    elif kind == 'recorded':
        for j, m in enumerate(tones(at, 74, 4)):
            bell(m, at + j * 0.07, gain=0.4, decay=0.9, pan=(j - 1.5) * 0.2)
    elif kind == 'postpone':
        click(at, gain=0.3, f=1800)
        click(at + 0.08, gain=0.3, f=2100)
        pluck(76, at + 0.12, gain=0.45, decay=0.25)
    elif kind == 'amber':
        pluck(69, at, gain=0.55, decay=0.45, bright=0.5)
    elif kind == 'over':
        pluck(68, at, gain=0.5, decay=0.35, bright=0.4)
        pluck(64, at + 0.08, gain=0.4, decay=0.4, bright=0.4)
    elif kind == 'bars':
        for j, m in enumerate([67, 71, 74, 78, 79, 83]):
            pluck(m, at + 0.1 * j, gain=0.35, decay=0.25, pan=(j - 2.5) * 0.15)
    elif kind == 'highlight':
        bell(83, at + 0.1, gain=0.35, decay=1.0)
    elif kind == 'transfer':
        whoosh(at, 0.8, 2800, 900, gain=0.2, shape='pass')
        pluck(81, at + 0.75, gain=0.5, decay=0.35)
    elif kind == 'unlock':
        bell(81, at, gain=0.45, decay=0.8)
        bell(86, at + 0.1, gain=0.45, decay=1.1)
        whoosh(at + 0.45, 0.6, 600, 3000, gain=0.2, shape='pass')
    elif kind == 'backedup':
        a, b_ = tones(at, 84, 2)
        bell(a, at, gain=0.4, decay=0.9)
        bell(b_, at + 0.1, gain=0.4, decay=1.2)

# ---------------------------------------------------------------------------------------------
# Sidechain, reverb, master.
# ---------------------------------------------------------------------------------------------

tt = np.arange(N) / SR
duck = np.ones(N)
for tk in kicks:
    d = tt - tk
    m = (d >= 0) & (d < 0.45)
    duck[m] = np.minimum(duck[m], 1 - 0.7 * np.exp(-d[m] / 0.11) * np.clip(d[m] / 0.004, 0, 1))
dry += duck_targets * duck
wet += duck_targets * duck * 0.35

# A plate-ish reverb: decaying stereo noise, darkened as it rings.
ir_len = int(2.4 * SR)
ti = np.arange(ir_len) / SR
ir = np.zeros((2, ir_len))
for ch in range(2):
    nz = rng.standard_normal(ir_len) * np.exp(-ti / 0.55)
    early = lowpass(nz, 7000)
    late = lowpass(nz, 2500)
    mixw = np.clip(ti / 1.2, 0, 1)
    ir[ch] = early * (1 - mixw) + late * mixw
    ir[ch][: int(0.018 * SR)] = 0
ir /= np.sqrt(np.sum(ir ** 2, axis=1, keepdims=True))
rev = np.vstack([signal.fftconvolve(wet[ch], ir[ch])[:N] for ch in range(2)])
mix = dry + rev * 0.55

mix = np.vstack([highpass(mix[ch], 28) for ch in range(2)])
# A gentle high shelf, so the top end speaks on a laptop.
mix = mix + 0.35 * np.vstack([highpass(mix[ch], 3500, order=1) for ch in range(2)])
# Glue: a gentle soft clip, then a peak ceiling.
mix = np.tanh(mix * 1.25) / np.tanh(1.25)
peak = np.max(np.abs(mix))
mix *= 10 ** (-1.0 / 20) / peak
fade = int(0.004 * SR)
mix[:, :fade] *= np.linspace(0, 1, fade)
tail = int(2.0 * SR)
mix[:, -tail:] *= np.linspace(1, 0, tail) ** 2

out = os.path.join(HERE, 'build', 'soundtrack.wav')
pcm = (np.clip(mix, -1, 1).T * 32767).astype('<i2')
with wave.open(out, 'wb') as w:
    w.setnchannels(2)
    w.setsampwidth(2)
    w.setframerate(SR)
    w.writeframes(pcm.tobytes())
rms = np.sqrt(np.mean(mix ** 2))
print(f'{out}  {DUR:.3f} s  peak -1.0 dBFS  rms {20 * np.log10(rms):.1f} dBFS')
