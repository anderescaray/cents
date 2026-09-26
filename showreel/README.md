# Cents — the film

A one-minute film of what Cents does, one idea every five seconds. The finished film is
[`cents-showreel.mp4`](cents-showreel.mp4) — 1920×1080, 60 fps, H.264 with an AAC soundtrack.

Nothing here is part of the Android build. Gradle reads nothing under `showreel/`.

## The cut

Sixty seconds on a 96 BPM grid: twelve shots of two bars each. Every moment is timed in beats,
so the picture and the music cannot drift apart. Each shot has a title on the left that holds
for four seconds, and the phone on the right doing the thing the title says.

| # | Seconds | Shot | What happens |
|---|---|---|---|
| 0 | 0–5 | Intro | A ring of category colours draws itself around a cent; *Cents — See where your money goes.* |
| 1 | 5–10 | Categories | The ring flies into the phone and becomes the month. *Most spent this month: Housing.* |
| 2 | 10–15 | Quick entry | 42,80 € typed, Groceries picked, saved — the ring re-balances itself |
| 3 | 15–20 | To categorize | Two movements saved without a category, each filed with one tap. *All sorted.* |
| 4 | 20–25 | Groups | A trip's flights, hotel, dinner, taxi and tours — five categories — collected into *Lisbon trip: cost you 684,20 €* |
| 5 | 25–30 | Subscriptions | What they cost a month and a year, and the reminder: *Netflix, 12,99 €, in 3 days.* |
| 6 | 30–35 | Variable amounts | The salary switched to *Ask me the amount*; when it falls due it waits, the real figure is entered and recorded on the day it was due; the electricity is postponed three days |
| 7 | 35–40 | Budgets | Limits filling up: amber at 80 %, red when over |
| 8 | 40–45 | Reports | Income against spending month by month, the day of the week that costs the most, and this month against August |
| 9 | 45–50 | Net worth | In accounts and invested, one number; a transfer to Savings |
| 10 | 50–55 | Privacy | A fingerprint unlocks it; the daily Drive backup runs; JSON and CSV export |
| 11 | 55–60 | Outro | The ring leaves the phone and becomes the logo again |

## The interface

The screens are **designed for the film**, not captured from the app: one idea — a ring of
category colours that says where the money went — and otherwise white cards, one clear number
each, and room around everything. What they show is the app's: every feature in the cut exists
in Cents, the numbers are written the way it writes them (`2.388,40 €`, shares truncated as
`SpendingSection.kt` does), the categories use the colours of `CategoryPalette.kt`, the type is
Inter 4 straight from `app/src/main/res/font/inter_variable.ttf`, and the words — *Waiting for
an amount*, *Cost you*, *Earned you*, *12,99 €, in 3 days.*, *Back up automatically* — come from
the string resources.

The coin-in-a-ring logo is made for the film; the app still ships the default launcher icon.
The figures are samples.

## How it is made

Everything is code; there is no timeline file and no stock footage.

| File | Role |
|---|---|
| `engine.js` | Timing grid, easing (including Compose's curve), springs, and DOM helpers |
| `ui.js` | The film's UI kit: tokens, category glyphs, cards, chips, keypad, switch, and the ring |
| `scenes.js` | The twelve shots. Each is a pure function of time: `render(t)` sets every property from `t` alone |
| `reel.html` | The 1920×1080 stage, and a scrubbable preview player |
| `render.cjs` | Drives headless Chromium frame by frame and pipes the frames to ffmpeg |
| `soundtrack.py` | Synthesises the music and every sound effect from the cue list the picture exports |

Because a frame depends on nothing but its time, the capture can sample any instant as often as
it likes. **Motion blur is real**: each output frame is the average of several renders spread
over a 180° shutter. The soundtrack reads the cues the scenes emit — a key pressed, a movement
filed, a salary recorded — and pitches each interface sound to the chord it lands in, so typing
42,80 € plays a phrase of the tune.

## Rendering it

Needs Node with Playwright's Chromium, Python 3 with NumPy and SciPy, and an ffmpeg with
libx264 (`pip install imageio-ffmpeg` provides one; `render.cjs` finds it).

```bash
node showreel/render.cjs cues          # the cue list → showreel/build/cues.json
python3 showreel/soundtrack.py         # the soundtrack → showreel/build/soundtrack.wav
node showreel/render.cjs video         # frames, motion blur, encode → showreel/cents-showreel.mp4
```

`video` takes `--samples` (motion-blur samples per frame, default 6; the published film used
5), `--fps`, `--workers` and `--crf`. `node showreel/render.cjs stills 7.5,21.5` writes single
frames to `showreel/build/stills/` for review.

To scrub it live, serve the repository root — the page loads the font from the app's resources,
which a `file://` page is not allowed to do — and open `showreel/reel.html`:

```bash
npx http-server -p 8080 .              # then http://localhost:8080/showreel/reel.html
```
