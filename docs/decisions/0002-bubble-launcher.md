# 0002 — The app opens on a canvas of bubbles, not on a tab

- **Status:** superseded on 2026-10-01 by [`0003-the-ring.md`](0003-the-ring.md). The canvas, its
  stored layout and *Reset the bubble layout* were removed in `feat/ui-rework`; the app opens on
  Home. What follows is kept as the record of what was built and why it was dropped.
- **Date:** 2026-09-08
- **Amends:** [`0001-ios-flavored-ui.md`](0001-ios-flavored-ui.md), whose table says the app
  navigates with a bottom tab bar
- **Answers:** D8 in [`DECISIONS.md`](../../DECISIONS.md), asked and answered 2026-08-20

## Decision

Cents opens on a **canvas of four bubbles**, one per section, which the owner drags, resizes and
arranges. Tapping one **expands it into that section**. The bottom tab bar still exists and is
still how you move between sections once you are inside one; it can be collapsed to a grabber.

The tab bar is therefore **not replaced**. The canvas is a **launcher in front of it**.

## Why this is an amendment and not a contradiction

ADR 0001 says the app navigates with a bottom tab bar, and rule 12 in `CLAUDE.md` makes that
document binding before any composable is written. That sentence is now narrower: the tab bar is
how you move *within* the app, and the canvas is how you enter it. Everything else in 0001 holds
unchanged — the canvas uses the same grouped background, the same card colour, the same type
scale and the same fixed palette, and nothing on it is a Material component.

**This is the second deliberate departure from iOS in this project** (the first is the swipe that
does not park open, recorded at the end of 0001). iOS has no home canvas inside an app; this is
the owner's own idea for their own app, and it is written down rather than quietly made.

## What it costs, and what was decided about each cost

**Accessibility stops being free.** A tab bar tells TalkBack "tab 2 of 4" without being asked. A
canvas of draggable circles has no reading order at all. So:

- Each bubble is a button with the section's name and an explicit `traversalIndex`, in tab
  order, inside a traversal group. The icon is not separately described — the bubble already
  carries the name, and announcing both reads the section twice.
- **The drift honours the system's animation setting.** With animations off, the drift is never
  started (not merely set to zero amplitude — an invisible animation is still one the device
  asked not to run), and a tap opens the section immediately instead of growing into it.
- **A screen reader cannot drag a bubble.** There is no gesture-free way to move one, so
  Settings carries **Reset the bubble layout**, which puts every bubble back where a fresh
  install has it. That is the escape hatch for a bubble dragged somewhere unusable, by anyone.

**A layout is arranged on one screen and read on another.** Positions are stored as fractions of
the canvas and the diameter as a fraction of its **shorter side**, so rotating the phone leaves a
bubble the size it was in the hand. Anything that no longer fits is shrunk or pushed inside when
it is drawn, without rewriting what was stored — a bubble that fell off a small screen comes back
where it was on the large one.

**Back means one thing.** The canvas is the start destination and every section is pushed on top
of it, so back from a section root returns to the canvas whether or not the tab bar is collapsed.
A gesture that means two different things depending on a toggle is worse than a toggle that only
hides a bar. This also fixed something that predates the canvas: back after a drill-down from
Reports used to leave the app, because the movements list was the floor of the stack.

**One motion, not two.** The tap grows the bubble into a full-screen shape (340 ms), and the
section then fades in over it (140 ms) — under the half second D8 asked for, and no horizontal
slide, which would be a second motion arguing with the first. **The way back is a fade, not the
morph in reverse**: a section does not know which bubble it came from, and teaching it would mean
threading the bubble's rectangle through navigation for one animation.

**Every screen already built keeps working untouched.** That is what makes the launcher model
affordable where a full replacement would not be: nothing below the shell knows the canvas
exists.

## Alternatives considered

- **Replacing the tab bar entirely.** Rejected in D8 itself. Moving between two sections would
  become canvas-out-and-in every time, which is slower than a tab bar for the thing done most.
- **Nested navigation graphs, one per tab, with the canvas as the root.** It would have made
  "which tab is this screen in" a fact of the graph instead of a list (see `TopLevelTab.owns`),
  but every screen two tabs can reach — the movement form, the account and category editors —
  would have to be registered in each graph. The cost lands on the screens rather than on the
  navigation, which is the wrong place for it.
- **A shared-element transition for the expansion.** The morph is drawn by hand as one rounded
  rectangle rather than with `SharedTransitionLayout`, which would tie the animation to an
  experimental API and to both screens knowing about each other.
