# 0001 — The UI follows iOS conventions, not Material

- **Status:** accepted
- **Date:** 2026-08-18
- **Applies to:** everything under `ui/`

## Decision

Cents is an Android app that **looks and behaves like a well-made iOS app**. The visual
language and the interaction flow follow Apple's Human Interface Guidelines, not Material
Design, even though the app is built with Jetpack Compose and ships only on Android.

This is an explicit product preference by the owner and single user of the app. It is not
a technical constraint and not an accident of the toolkit.

## What this means concretely

| Aspect | What we do | What we do **not** do |
|---|---|---|
| Lists | Inset grouped lists: rounded cards on a grey background, separators inset to the text, chevron on tappable rows | Edge-to-edge Material lists with ripples |
| Headers | Large title that collapses into a centred compact title on scroll | Material large top app bar |
| Back | Chevron plus the previous screen's label, on the left | Left arrow with no label |
| Tabs | Bottom tab bar, icon over label, tinted when selected | Material `NavigationBar` styling |
| Sheets | Modal sheet with a grabber and rounded top corners; action sheets for destructive choices | Material bottom sheets |
| Alerts | Centred dialog, buttons side by side, destructive action in red | Material dialogs with text buttons bottom-right |
| Controls | iOS-style switch, segmented control, plain-text buttons with a tint colour | Filled tonal buttons, FABs, chips |
| Feedback | Selection changes and confirmations produce a light haptic | Ripples as the primary feedback |
| Motion | Push/pop horizontal slide, spring physics, interactive swipe-back from the left edge | Material shared-axis or fade-through transitions |
| Swipe to delete | A swipe that carries most of the way across fires; anything shorter springs back | Parking the row open with a tappable button, which is what iOS itself does — see Consequences |
| Colour | The iOS system palette (systemBlue tint, grouped background greys), fixed in light and dark | Material You dynamic colour from the wallpaper |
| Type | One SF-like family across an iOS text scale (large title → caption) | The Material type scale |

## Consequences

**We still use Material 3 as a dependency**, because Compose's building blocks, layout,
accessibility plumbing and window insets handling come with it. What we do not use is its
*appearance*: components are wrapped or rebuilt so that no screen is assembled from
default Material widgets. When a Material component cannot be restyled convincingly, we
write our own on Compose Foundation rather than shipping something that looks Material.

**Material You dynamic colour is dropped.** The PRD listed it under polish (§4.8). It is
incompatible with this decision: an iOS-flavoured app whose accent colour changes with the
wallpaper looks like neither platform. The palette is fixed, with a proper dark variant.

**The typeface is Inter, not SF Pro.** SF Pro is licensed by Apple for use on Apple
platforms only and cannot be shipped in an Android APK. Inter (SIL Open Font License) is
the closest widely-used substitute: it was designed on similar principles and reads as
neutral-modern rather than as Android's Roboto. The bundled file is the variable font, so
every weight costs one 880 KB asset. Licence in `docs/licenses/Inter-OFL.txt`.

**Icons are drawn in-house.** SF Symbols cannot be shipped outside Apple platforms, and
Compose's Material icon artifacts are frozen at 1.7.8 and no longer part of the Compose
BOM — so the app carries its own stroke-based vector drawables in `res/drawable`. They are
thin, rounded and geometric, which is what makes an icon read as iOS rather than Material.

**Swipe-to-delete does not park the row open, which iOS does.** Added 2026-08-30, after the
first device walk. On iOS a short drag leaves the row open with a tappable **Delete**, and the
component was built that way. On the phone the owner read the parked row as the row getting
stuck rather than as an offer, and asked for it removed: a swipe now either carries far enough
to fire or springs back, with nothing in between.

This is the first deliberate departure from iOS in this document, so it is recorded rather
than quietly made. What it costs is real — deleting by gesture now always takes a confident
swipe across most of the row, and the revealed strip is feedback during the drag rather than a
button. What it does not cost is the non-gesture path: that was never the strip, it is the
custom accessibility action on the row itself, which is unchanged and is now the only way to
delete without a gesture. The rule in the table above still holds everywhere else; a look
borrowed from another platform is a starting point, and the owner using the app is the thing
that settles it.

**Android platform behaviour is still respected.** This is about appearance and flow, not
about fighting the operating system. The system back gesture keeps working (the swipe-back
animation is ours, the gesture is Android's), notification, share and permission dialogs
stay native, and the app honours system font scaling, dark mode and accessibility
services. An iOS look that breaks Android's back gesture is a broken app, not a stylish
one.

## Why record this

Every future screen has to make the same fifty small choices, and an agent reading only
the code would reasonably reach for Material defaults, because that is what Compose hands
you. Written down once, it applies to every screen without being re-litigated.

## Alternatives considered

- **Plain Material 3.** Cheapest and most idiomatic, and rejected because it is not what
  the owner wants to look at every day. For a personal app used by one person, that is a
  sufficient reason.
- **A Cupertino component library for Compose.** The available ones are aimed at Compose
  Multiplatform, are thinly maintained, and would put the app's entire appearance behind a
  third-party dependency. Rejected under the "no new libraries without justification"
  rule; a small set of our own components is more predictable and less code than it
  sounds.
