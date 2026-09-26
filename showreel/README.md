# Cents — motion reel

A 15-second motion reel of the app, cut for **LibreYOLO**: the object-detection motif is the
thread, and the subject is Cents. The finished film is
[`cents-showreel.mp4`](cents-showreel.mp4) — 1920×1080, 60 fps, H.264 with an AAC soundtrack.

Nothing here is part of the Android build. Gradle reads nothing under `showreel/`.

## The cut

The film is written on a 128 BPM grid — 32 beats, 8 bars, exactly 15 seconds — and every scene
is timed in beats, so the picture and the music cannot drift apart.

| Bar | Beats | Scene | What happens |
|---|---|---|---|
| 1 | 0–4 | Detect | A detector finds one cent (`cent 0.99`), a scan line finds every other one, and they are pulled into one coin |
| 2 | 4–8 | Canvas | The coin opens into the app's bubble launcher; the camera pulls back to the phone; a tap grows the Transactions bubble into the section |
| 3 | 8–12 | Log it | The entry sheet: 42,80 € typed on the off-beats. *Log it once.* |
| 4 | 12–16 | Everywhere | One movement reaches the balance (an odometer), the budget (amber past 80 %) and the spending ring at once |
| 5 | 16–20 | Reports | The ring card opens into the frame; the ring unrolls onto the floor and stands up as bars; the smallest bar floods the frame |
| 6–7 | 20–26 | Montage | Subscriptions, Investments, Transfers, Private, Backed up — one beat each |
| 7 | 26–28 | YOLO | *You Only Log Once.* |
| 8 | 28–32 | Logo | Boxes lock onto Y·O·L·O, the rest falls away, the initials become the coin: Cents, detected at `cents 1.00` |

## What is the app's own

- **Palette** — `ui/theme/CentsColors.kt` and `ui/feature/categories/CategoryPalette.kt`.
- **Type** — Inter 4, loaded straight from `app/src/main/res/font/inter_variable.ttf`, with its
  optical-size axis, so headlines get the display cut.
- **Icons** — the stroke paths in `res/drawable/ic_tab_*.xml`, drawn on stroke by stroke.
- **Copy** — *New transaction*, *Where the money went*, *Total spent*, *342,80 € spent of
  400,00 €*, *Unlock Cents* / *Your finances are behind this.*, the reminder body
  *12,99 €, in 3 days.* — all from `res/values/strings*.xml` and `plurals.xml`.
- **Layout and motion** — the five bubbles sit where `HomeCanvasGeometry.kt` puts them by
  default, and the tapped one grows into the section in 340 ms on Compose's
  `FastOutSlowInEasing`, as `HomeScreen.kt` does (the reel starts the section's fade-in a
  little before the shape lands, so the shot has no empty frame). The budget bar turns amber at
  80 % (D26); shares are truncated the way `SpendingSection.kt` writes them.

What is **not** the app's: the coin mark in the logo (the app still ships the default launcher
icon), and the sample figures.

## How it is made

Everything is code; there is no timeline file and no stock footage.

| File | Role |
|---|---|
| `engine.js` | Timing grid, easing (including Compose's curve), springs, DOM helpers, the app's icons, and the detection box |
| `scenes.js` | The eight scenes. Each is a pure function of time: `render(t)` sets every property from `t` alone |
| `reel.html` | The 1920×1080 stage, styles, and a scrubbable preview player |
| `render.cjs` | Drives headless Chromium frame by frame and pipes the frames to ffmpeg |
| `soundtrack.py` | Synthesises the music and every sound effect from the cue list the picture exports |

Because a frame depends on nothing but its time, the capture can sample any instant as often as
it likes. **Motion blur is real**: each output frame is the average of eight renders spread over
a 180° shutter. The soundtrack reads the same cues the scenes emit — a coin found, a bubble
born, a key pressed, a card hit — so each sound lands on its frame.

## Rendering it

Needs Node with Playwright's Chromium, Python 3 with NumPy and SciPy, and an ffmpeg with
libx264 (`pip install imageio-ffmpeg` provides one; `render.cjs` finds it).

```bash
node showreel/render.cjs cues          # the cue list → showreel/build/cues.json
python3 showreel/soundtrack.py         # the soundtrack → showreel/build/soundtrack.wav
node showreel/render.cjs video         # frames, motion blur, encode → showreel/cents-showreel.mp4
```

`video` takes `--samples` (motion-blur samples per frame, default 6; the published film used
8), `--fps`, `--workers` and `--crf`. `node showreel/render.cjs stills 1.5,7.25` writes single
frames to `showreel/build/stills/` for review.

To scrub it live, serve the repository root — the page loads the font from the app's resources,
which a `file://` page is not allowed to do — and open `showreel/reel.html`:

```bash
npx http-server -p 8080 .              # then http://localhost:8080/showreel/reel.html
```
