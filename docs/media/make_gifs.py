#!/usr/bin/env python3
"""Turns the frames ReadmeAnimations recorded into the README's GIFs.

    CENTS_README_FRAMES=/tmp/frames ./gradlew testDebugUnitTest --tests '*ReadmeAnimations*'
    python3 docs/media/make_gifs.py /tmp/frames docs/media capture investments reports

The first line records the real app in Robolectric, filled with an invented ledger
(app/src/test/.../readme/ReadmeLedger.kt); CENTS_README_SCENE=<name> records one scene only.

Each scene folder holds one PNG per 40 ms of the app's window and an events.txt. What is not
the app's window is drawn here: the status bar, the gesture bar, the finger, and the bank's
notification, which Android draws above every app. Needs Pillow, ffmpeg and gifsicle.
"""

import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

FONT = Path(__file__).resolve().parents[2] / "app/src/main/res/font/inter_variable.ttf"
WIDTH = 360  # the GIF's width in pixels; the README shows it at 270, so it stays sharp
SCALE = 2  # the frames are xhdpi: two pixels per dp
STATUS_DP, NAV_DP, BEZEL_DP, RADIUS_DP = 32, 20, 7, 36


def font(size_dp, weight="Regular"):
    f = ImageFont.truetype(str(FONT), int(size_dp * SCALE))
    try:
        f.set_variation_by_name(weight)
    except (OSError, ValueError):
        pass
    return f


def dp(v):
    return int(round(v * SCALE))


def read_events(scene):
    taps, swipes, notes = [], [], []
    frame_ms = 40
    for line in (scene / "events.txt").read_text().splitlines():
        parts = line.split()
        if not parts:
            continue
        if parts[0] == "frameMs":
            frame_ms = int(parts[1])
        elif parts[0] == "tap":
            taps.append((int(parts[1]), int(parts[2]), int(parts[3])))
        elif parts[0] == "swipe":
            # swipe <first frame> <frames> <x0> <y0> <x1> <y1>
            swipes.append(tuple(int(p) for p in parts[1:7]))
        elif parts[0] == "notification":
            # notification <first frame> <last frame> <app> | <title> | <text>
            first, last = int(parts[1]), int(parts[2])
            app, title, text = [s.strip() for s in " ".join(parts[3:]).split("|")]
            notes.append((first, last, app, title, text))
    return frame_ms, taps, swipes, notes


def status_bar(draw, width, color, ink):
    draw.rectangle([0, 0, width, dp(STATUS_DP)], fill=color)
    y = dp(STATUS_DP) // 2
    draw.text((dp(22), y), "9:41", font=font(13, "SemiBold"), fill=ink, anchor="lm")
    # Signal, wifi and battery, drawn rather than borrowed from anyone's icon set.
    x = width - dp(22)
    draw.rounded_rectangle([x - dp(22), y - dp(5.5), x, y + dp(5.5)], radius=dp(2.5), outline=ink, width=dp(1.2))
    draw.rounded_rectangle([x - dp(19.5), y - dp(3), x - dp(5), y + dp(3)], radius=dp(1), fill=ink)
    draw.rectangle([x + dp(1), y - dp(2), x + dp(2.4), y + dp(2)], fill=ink)
    x -= dp(30)
    for i, r in enumerate((dp(9), dp(6), dp(3))):
        box = [x - r, y + dp(4) - r, x + r, y + dp(4) + r]
        if i == 2:
            draw.pieslice(box, 225, 315, fill=ink)
        else:
            draw.arc(box, 225, 315, fill=ink, width=dp(1.6))
    x -= dp(22)
    for i in range(4):
        h = dp(3 + i * 2.2)
        draw.rounded_rectangle([x + i * dp(4), y + dp(5) - h, x + i * dp(4) + dp(2.6), y + dp(5)], radius=dp(0.8), fill=ink)


def notification(frame, width, progress, app, title, text):
    """A heads-up notification in the shape Android gives one, sliding in by [progress] 0..1."""
    h = dp(76)
    card = Image.new("RGBA", (width - dp(16), h), (0, 0, 0, 0))
    d = ImageDraw.Draw(card)
    d.rounded_rectangle([0, 0, card.width - 1, h - 1], radius=dp(22), fill=(252, 252, 253, 255), outline=(225, 226, 230, 255))
    cx, cy = dp(30), dp(38)
    d.ellipse([cx - dp(17), cy - dp(17), cx + dp(17), cy + dp(17)], fill=(26, 115, 92, 255))
    d.text((cx, cy), app[0], font=font(16, "Bold"), fill="white", anchor="mm")
    d.text((dp(58), dp(15)), f"{app} · now", font=font(11.5, "Medium"), fill=(110, 112, 120), anchor="lt")
    d.text((dp(58), dp(32)), title, font=font(14, "SemiBold"), fill=(24, 25, 28), anchor="lt")
    d.text((dp(58), dp(51)), text, font=font(13), fill=(70, 72, 78), anchor="lt")
    # A soft shadow under it, as the system draws.
    shadow = Image.new("RGBA", (card.width + dp(20), h + dp(20)), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle([dp(10), dp(12), card.width + dp(10), h + dp(10)], radius=dp(22), fill=(0, 0, 0, 40))
    ease = 1 - (1 - progress) ** 3
    top = int(-h - dp(10) + (h + dp(10) + dp(STATUS_DP) + dp(6)) * ease)
    frame.alpha_composite(shadow, (dp(8) - dp(10), top - dp(10)))
    frame.alpha_composite(card, (dp(8), top))


def touch(frame, x, y, strength, grow=0.0):
    """A fingertip: a soft grey disc with a white ring, faded by [strength] 0..1."""
    layer = Image.new("RGBA", frame.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    r = dp(19 + grow)
    a = int(200 * strength)
    d.ellipse([x - r, y - r, x + r, y + r], fill=(30, 30, 38, a // 2), outline=(255, 255, 255, a), width=dp(2))
    frame.alpha_composite(layer)


def finger(frame, x, y, age):
    """A tap: shown pressing for the four frames before the click, then lifting over six."""
    if age < 4:
        touch(frame, x, y, min(1.0, (age + 1) / 2))
    elif age < 10:
        touch(frame, x, y, 1 - (age - 4) / 6, grow=(age - 4) * 1.5)


def compose(scene, out_dir, name):
    frame_ms, taps, swipes, notes = read_events(scene)
    files = sorted(scene.glob("*.png"))
    first = Image.open(files[0])
    w, h = first.size
    canvas_color = first.getpixel((w // 2, dp(4)))[:3]
    ink = (20, 20, 24) if sum(canvas_color) > 380 else (240, 240, 245)
    full_h = h + dp(STATUS_DP) + dp(NAV_DP)
    bezel = dp(BEZEL_DP)
    tmp = Path(tempfile.mkdtemp())
    for i, f in enumerate(files):
        screen = Image.new("RGBA", (w, full_h), canvas_color + (255,))
        app = Image.open(f).convert("RGBA")
        screen.alpha_composite(app, (0, dp(STATUS_DP)))
        d = ImageDraw.Draw(screen)
        status_bar(d, w, canvas_color, ink)
        bottom = full_h - dp(NAV_DP)
        d.rectangle([0, bottom, w, full_h], fill=app.getpixel((w // 2, h - 2))[:3])
        pill = dp(54)
        d.rounded_rectangle([w // 2 - pill, bottom + dp(8), w // 2 + pill, bottom + dp(12)], radius=dp(2), fill=ink)
        for frame, x, y in taps:
            if frame <= i:
                finger(screen, x, y + dp(STATUS_DP), i - frame)
        for start, steps, x0, y0, x1, y1 in swipes:
            t = (i - start) / steps
            if 0 <= t <= 1:
                e = t * t * 2 if t < 0.5 else 1 - (-2 * t + 2) ** 2 / 2
                touch(screen, x0 + (x1 - x0) * e, y0 + (y1 - y0) * e + dp(STATUS_DP), 1.0)
            elif 1 < t and i - start - steps < 5:
                touch(screen, x1, y1 + dp(STATUS_DP), 1 - (i - start - steps) / 5)
        for start, end, app_name, title, text in notes:
            slide = 7
            if start <= i <= end:
                p = min(1.0, (i - start) / slide) if i < end - slide else max(0.0, (end - i) / slide)
                notification(screen, w, p, app_name, title, text)
        # The phone: a dark bezel with round corners, nothing outside it.
        phone = Image.new("RGBA", (w + 2 * bezel, full_h + 2 * bezel), (0, 0, 0, 0))
        ImageDraw.Draw(phone).rounded_rectangle(
            [0, 0, phone.width - 1, phone.height - 1], radius=dp(RADIUS_DP) + bezel, fill=(28, 28, 32, 255)
        )
        mask = Image.new("L", screen.size, 0)
        ImageDraw.Draw(mask).rounded_rectangle([0, 0, w - 1, full_h - 1], radius=dp(RADIUS_DP), fill=255)
        phone.paste(screen, (bezel, bezel), mask)
        out_h = round(phone.height * WIDTH / phone.width)
        phone.resize((WIDTH, out_h), Image.LANCZOS).save(tmp / f"{i:04d}.png")
    gif = out_dir / f"{name}.gif"
    fps = 1000 / frame_ms
    palette = tmp / "palette.png"
    subprocess.run(
        ["ffmpeg", "-y", "-loglevel", "error", "-framerate", str(fps), "-i", str(tmp / "%04d.png"),
         "-vf", "palettegen=max_colors=255:reserve_transparent=1:stats_mode=full", str(palette)],
        check=True,
    )
    subprocess.run(
        ["ffmpeg", "-y", "-loglevel", "error", "-framerate", str(fps), "-i", str(tmp / "%04d.png"), "-i", str(palette),
         "-lavfi", "paletteuse=dither=bayer:bayer_scale=5:diff_mode=rectangle:alpha_threshold=128", "-loop", "0", str(gif)],
        check=True,
    )
    # Only what changes between frames is kept, and colours a step apart are merged: an eighth of
    # the size, with no difference visible at this width.
    subprocess.run(["gifsicle", "-O3", "--lossy=60", "-b", str(gif)], check=True)
    shutil.rmtree(tmp)
    print(f"{gif}: {len(files)} frames, {gif.stat().st_size / 1e6:.2f} MB")


def main():
    frames, out = Path(sys.argv[1]), Path(sys.argv[2])
    out.mkdir(parents=True, exist_ok=True)
    scenes = sys.argv[3:] or [p.name for p in sorted(frames.iterdir()) if (p / "events.txt").exists()]
    for name in scenes:
        compose(frames / name, out, name)


if __name__ == "__main__":
    main()
