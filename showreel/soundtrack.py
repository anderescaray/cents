#!/usr/bin/env python3
"""
Cents — motion reel. The soundtrack, synthesised from nothing.

    python3 showreel/soundtrack.py            reads build/cues.json, writes build/soundtrack.wav

The music sits on the same 128 BPM grid the picture is cut to: eight bars, A minor to C major.
Every sound effect is placed by a cue the picture exported — a coin found, a bubble born, a key
pressed, a card hit — so the mix cannot drift from the frame.

Bar 1  Am   the detector: a drone, scanner ticks, a blip per coin found
Bar 2  F    the kick arrives with the burst; the bubbles are the melody
Bar 3  C    the groove drops on "Log it"; the keypad plays the tune
Bar 4  G    three bells for the three screens the movement reaches
Bar 5  Am   the ring unrolls; the bars climb a scale
Bar 6  F    the montage, a stab on every cut
Bar 7  G    …then everything drops out for "You Only Log Once."
Bar 8  C    the impact, the coin, the chord
"""
import json
import os
import wave

import numpy as np
from scipy import signal

HERE = os.path.dirname(os.path.abspath(__file__))
SR = 48000
BPM = 128
BEAT = 60 / BPM
DUR = 32 * BEAT  # 15 s
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

CHORDS = {
    'Am': [57, 64, 67, 72],
    'F': [53, 60, 64, 69],
    'C': [60, 67, 74, 76],
    'G': [55, 62, 67, 71],
    'Gsus': [55, 60, 62, 67],
    'Cmaj9': [48, 55, 64, 71, 74],
}
BAR_CHORD = ['Am', 'F', 'C', 'G', 'Am', 'F', 'G', 'Cmaj9']
BAR_ROOT = [45, 41, 36, 43, 45, 41, 43, 36]
ARP = {'C': [72, 76, 79, 84], 'G': [71, 74, 79, 83], 'Am': [69, 72, 76, 81], 'F': [69, 72, 77, 81]}

# Pad: filtered in the intro, opening up with the drop, suspended for the breakdown.
pad(CHORDS['Am'], b(0), b(4), gain=0.8, cutoff=700, attack=1.2)
pad(CHORDS['F'], b(4), b(4), gain=0.9, cutoff=1300, attack=0.05)
for bar in (2, 3, 4, 5):
    pad(CHORDS[BAR_CHORD[bar]], b(bar * 4), b(4), gain=1.0, cutoff=2600, attack=0.03)
pad(CHORDS['G'], b(24), b(2), gain=1.0, cutoff=2600, attack=0.03, release=0.2)
pad(CHORDS['Gsus'], b(26), b(2), gain=0.9, cutoff=1600, attack=0.3, release=0.1)
pad(CHORDS['Cmaj9'], b(28), b(4) - 0.05, gain=1.5, cutoff=3200, attack=0.02, release=1.2)
pad([60, 67, 76, 79], b(29.25), b(2.75) - 0.1, gain=0.8, cutoff=4200, attack=0.4, release=1.0)

# Drone under the detector.
t = t_axis(b(4) + 0.3)
drone = np.sin(2 * np.pi * midi(33) * t) * np.clip(t / 1.0, 0, 1) * np.clip((b(4) + 0.3 - t) / 0.3, 0, 1)
add(drone * 0.1, 0)

# Scanner ticks in bar 1: quiet sixteenths.
for k in range(16):
    hat(b(k * 0.25), gain=0.35 + 0.25 * (k % 4 == 0), pan=0.5 if k % 2 else -0.5)

# Drums: the kick lands with the burst (bar 2), the full kit with the drop (bar 3), everything
# stops for the words (bar 7, second half).
kicks = [b(k) for k in range(4, 26)]
for tk in kicks:
    kick(tk, gain=0.8 if tk < b(8) else 1.0)
for k in range(8, 26):
    if k % 2 == 1:
        clap(b(k), gain=0.9)
    hat(b(k + 0.5), gain=1.0, pan=0.3)
    if k >= 12:
        hat(b(k + 0.25), gain=0.35, pan=-0.35)
        hat(b(k + 0.75), gain=0.4, pan=-0.35)
    if k % 4 == 3:
        hat(b(k + 0.5), gain=0.7, open_=True, pan=0.2)
# The build into the drop, end of bar 2.
for k in range(8):
    snare(b(6 + k * 0.25), gain=0.25 + 0.1 * k, pitch=1 + k * 0.04)
# The roll into the impact, end of bar 7: sixteenths, then thirty-seconds.
roll = [26 + 0.25 * k for k in range(6)] + [27.5 + 0.125 * k for k in range(4)]
for k, at in enumerate(roll):
    snare(b(at), gain=0.14 + 0.05 * k, pitch=1 + k * 0.05)

# Bass: offbeat eighths, sidechained, from the burst to the breakdown.
for bar in range(1, 7):
    root = BAR_ROOT[bar]
    for k in range(4):
        start = b(bar * 4 + k + 0.5)
        if start >= b(26):
            break
        bass(root, start, b(0.45), gain=0.8 if bar == 1 else 1.0)
        bass(root + 12, start, b(0.2), gain=0.25)
bass(36, b(28), b(3), gain=1.1)

# Arpeggio: sixteenths through the middle of the film.
for bar in range(3, 7):
    notes = ARP[BAR_CHORD[bar]]
    for k in range(16):
        at = b(bar * 4 + k * 0.25)
        if at >= b(26):
            break
        arp_note(notes[k % 4] + (12 if k % 8 >= 4 else 0), at, gain=0.9, pan=0.45 if k % 2 else -0.45)

# Riser into the impact, and a reverse swell into the burst.
whoosh(b(26), b(2), 300, 9000, gain=1.1, shape='swell', send=0.3)
t = t_axis(b(2))
rise = np.sin(2 * np.pi * np.cumsum(midi(43) * 2 ** (2 * (t / b(2)) ** 1.5)) / SR) * (t / b(2)) ** 2 * 0.08
add(rise, b(26), send=0.3)
whoosh(b(3), b(1), 400, 7000, gain=0.6, shape='swell')

# ---------------------------------------------------------------------------------------------
# Sound design, from the picture's cues.
# ---------------------------------------------------------------------------------------------

with open(os.path.join(HERE, 'build', 'cues.json')) as fh:
    cues = json.load(fh)['cues']

DETECT = [81, 84, 86, 88, 91, 93]
BUBBLE = [72, 77, 81, 84, 89]
KEYS = [76, 79, 81, 84, 88]
HITS = [79, 83, 86]
BARS = [69, 72, 74, 76, 79, 81]
STAB = {'F': [65, 69, 72, 77], 'G': [67, 71, 74, 79]}

for c in cues:
    at, kind = c['t'], c['type']
    if kind == 'coin':
        if c.get('final'):
            for j, m in enumerate([84, 88, 91, 96]):
                bell(m, at + j * 0.035, gain=0.9, pan=(j - 1.5) * 0.2, decay=1.6)
            blip(2200, at, dur=0.05, gain=0.6)
        else:
            bell(88, at, gain=0.7, decay=0.9)
    elif kind == 'lock':
        if c.get('final'):
            blip(midi(84), at, dur=0.07, gain=0.9)
            blip(midi(91), at + 0.08, dur=0.14, gain=0.9)
        elif c.get('big'):
            blip(midi(81), at, dur=0.06, gain=0.8)
            blip(midi(88), at + 0.07, dur=0.1, gain=0.8)
        else:
            blip(midi(88), at, dur=0.045, gain=0.6)
            blip(midi(93), at + 0.055, dur=0.07, gain=0.6)
    elif kind == 'scan':
        whoosh(at, b(1), 600, 6000, gain=0.45, shape='pass', q=2.5)
    elif kind == 'detect':
        blip(midi(DETECT[c['i'] % len(DETECT)]), at, dur=0.05, gain=0.55, pan=c.get('pan', 0) * 0.8)
    elif kind == 'swoosh':
        whoosh(at, 0.45, 5000, 500, gain=0.5, shape='swell')
    elif kind == 'absorb':
        click(at, gain=0.35, f=2400 + 120 * c['i'], pan=0.0)
    elif kind == 'flip':
        bell(95, at, gain=0.35, decay=0.4, ratio=5.1, index=1.2)
    elif kind == 'burst':
        sub_boom(at, gain=0.7, f_start=140, f_end=44, dur=0.9)
        whoosh(at, 0.5, 300, 5000, gain=0.7, shape='fade')
        crash(at, gain=0.45, dur=1.5)
    elif kind == 'bubble':
        pluck(BUBBLE[c['i']], at, gain=0.9, pan=(c['i'] - 2) * 0.3, decay=0.3)
        blip(700 + 140 * c['i'], at, dur=0.04, gain=0.35, glide=2.0)
    elif kind == 'pullback':
        whoosh(at, 0.62, 3500, 300, gain=0.45, shape='pass')
    elif kind == 'tap':
        click(at, gain=0.8, f=1400)
    elif kind == 'expand':
        whoosh(at, 0.34, 400, 3000, gain=0.45, shape='pass')
    elif kind == 'sheet':
        whoosh(at, 0.3, 500, 2500, gain=0.3, shape='pass')
    elif kind == 'key':
        pluck(KEYS[c['i']], at, gain=1.0, pan=(c['i'] - 2) * 0.15, decay=0.28)
        click(at, gain=0.45, f=2000)
    elif kind == 'insert':
        blip(420, at, dur=0.07, gain=0.5, glide=2.2)
    elif kind == 'word':
        w = c.get('w', '')
        if w in ('You', 'Only', 'Log', 'Once.'):
            i = c.get('i', 0)
            sub_boom(at, gain=0.55 + 0.1 * i, f_start=120, f_end=49, dur=0.6)
            snare(at, gain=0.35, pitch=0.7 + 0.08 * i)
            pluck([55, 59, 62, 67][i], at, gain=0.5, decay=0.4, bright=0.5, send=0.4)
        else:
            chord = CHORDS['C'] if w in ('Log it', 'once.') else CHORDS['G']
            for m in chord:
                arp_note(m + 12, at, gain=1.4, stab=True)
    elif kind == 'wire':
        whoosh(at, 0.27, 800, 5000, gain=0.35, shape='swell', q=2.2)
    elif kind == 'hit':
        bell(HITS[c['i']], at, gain=0.9, decay=0.9, pan=[-0.3, 0.3, 0.0][c['i']])
    elif kind == 'roll':
        for j in range(14):
            click(at + 0.6 * (j / 14) ** 1.6, gain=0.3 * (1 - j / 16), f=3000, pan=-0.2)
    elif kind == 'ring':
        whoosh(at, 0.5, 2500, 800, gain=0.25, shape='pass')
    elif kind == 'open':
        whoosh(at, 0.75, 300, 6000, gain=0.6, shape='pass')
        sub_boom(at + 0.6, gain=0.3, f_start=100, f_end=50, dur=0.5)
    elif kind == 'unroll':
        whoosh(at, 0.55, 6000, 600, gain=0.45, shape='pass', q=3)
        for j in range(10):
            click(at + 0.05 * j, gain=0.18, f=2600 - 120 * j, pan=-0.4 + 0.08 * j)
    elif kind == 'bar':
        pluck(BARS[5 - c['i']], at, gain=0.9, pan=(2.5 - c['i']) * 0.18, decay=0.32)
    elif kind == 'flood':
        whoosh(at - 0.05, 0.45, 400, 7000, gain=0.6, shape='swell')
    elif kind == 'whoosh':
        whoosh(at - 0.02, 0.16, 800, 6000, gain=0.45, shape='swell', q=1.2)
    elif kind == 'cut':
        chord = STAB['F'] if c['i'] < 3 else STAB['G']
        for m in chord:
            arp_note(m + 12, at, gain=1.8, stab=True)
        blip(midi(chord[-1] + 12), at, dur=0.05, gain=0.3)
    elif kind == 'impact':
        kick(at, gain=1.2)
        sub_boom(at, gain=1.0, f_start=120, f_end=36, dur=1.6)
        crash(at, gain=0.9, dur=2.4)
        for j, m in enumerate([93, 96, 100, 103]):
            blip(midi(m), at + 0.03 * j, dur=0.06, gain=0.5, pan=(j - 1.5) * 0.3)
    elif kind == 'fall':
        blip(1400, at, dur=0.55, gain=0.35, glide=0.18, send=0.3)
        for j in range(8):
            click(at + 0.18 + 0.045 * j, gain=0.15, f=900 + 200 * j, pan=(j - 3.5) * 0.2)
    elif kind == 'gather':
        whoosh(at, 0.66, 500, 8000, gain=0.55, shape='swell', q=2)
        blip(300, at + 0.1, dur=0.56, gain=0.3, glide=6.0, send=0.3)
    elif kind == 'mark':
        whoosh(at, 0.5, 2500, 400, gain=0.3, shape='pass')

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
tail = int(0.35 * SR)
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
