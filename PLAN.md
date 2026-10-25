# Plan

Where Cents stands, what comes next, and what is waiting on the owner. **This file is rewritten,
not added to:** each session replaces §2 to §4 with what is true when it stops, and deletes what
no longer is. What happened, and when, is in `git log` and the PR descriptions. The rules for
every document are in [`CLAUDE.md` §8](CLAUDE.md#8-documentation).

## 1. Starting a session

```powershell
git status                                         # anything half-finished?
gh pr list                                         # anything open waiting on review?
git checkout main; git pull --ff-only              # start from what is merged
./gradlew spotlessCheck detekt testDebugUnitTest   # green before touching anything
```

Then read these, in this order, before writing any code:

1. **[`FEEDBACK.md`](FEEDBACK.md)**: anything under *Open* outranks everything below. Fix it,
   then move it to *Fixed* with the date and what was done.
2. **[`DECISIONS.md`](DECISIONS.md)**: an answer given since the last session is an instruction.
   Act on it, then move it to *Answered*.
3. **§2 to §4 below**, then the backlog,
   [`docs/next-improvements.md`](docs/next-improvements.md), when choosing what to build.

## 2. Where things stand (2026-10-25)

- **`main`** is at `3b9924c` (#146), with **Room v13** and **1140 JVM tests**. PRD phases 1 to 5
  are built, with prices typed by hand (D25). From Phase 6, accessibility (#96), R8, the Baseline
  Profile and Macrobenchmark (#146) are built; the widget is not. What the app does is in
  [`README.md`](README.md#what-it-does).
- **Latest merges:** #146, R8 on release (3.5 MB), a committed Baseline Profile and the
  `:baselineprofile` module, measured on the Pixel on 2026-10-20 (a cold start of 162 ms with the
  profile, 175 ms without); #145 and #144, documentation.
- **Every confirmed request is checked on the phone**, item 8 (archiving a group, G2–G4) last,
  reported on 2026-10-25.
- **Two new requests** (2026-10-25), backlog items 13 and 14: the figure switches in Settings
  govern Home only, Wallet always showing everything (D37, amending D34); and clearing every
  filter from the top of the Filters screen.
- **The Pixel runs `8dd2a78`**, installed 2026-10-18. #143 and #146 are not on it yet.
- **No question is open in `DECISIONS.md`.**
- **One finding of #112's review is left on purpose:** a correction is measured against the
  all-time balance ([`docs/known-limitations.md`](docs/known-limitations.md)).

## 3. Next, in order

1. **Item 13**, the figure switches for Home only (`fix/figures-home-only`).
2. **Item 14**, clearing every filter from the top (`feat/clear-filters-at-top`). Disjoint from
   item 13, so the two can run side by side.
3. **Then the owner's choice** from the backlog's *Requested, not yet placed*: the widget, hiding
   a tab, the savings card on Reports, deleting one Drive backup, resizable Reports cards.

## 4. Waiting on the owner

- **This session's docs PR** (`docs/baseline-profile-and-groups-checked`), to review and merge.
- **Items 13 and 14 on the phone** once merged and installed: Settings, then Home and Wallet;
  Movements › Filters.
- **What comes after them** (§3, item 3).
- **Branches** that are merged or superseded and can be deleted when the owner says so. On
  GitHub: `docs/session-2026-10-18` (#142), `docs/next-improvements` (#110) and
  `feat/groups-screens` (#87). Locally: `feat/baseline-profile` (#146), every
  `docs/session-*`, `docs/professional-docs`, `docs/drive-client-and-walk-2026-09-24`,
  `fix/capture-safety-and-feedback`, `fix/transaction-entry-ux` and
  `fix/correction-undo-test-race`, plus the worktree `C:\dev\cents-docs-plan`.
- **Always the owner's:** using the app to judge whether it feels right; anything needing a
  device setting, an unlock or a permission dialog; and reviewing and merging every PR. The agent
  may install, launch, read logcat and take screenshots, and nothing more on the phone.

## 5. How work passes between owner and agent

Two files, one direction each:

- **[`FEEDBACK.md`](FEEDBACK.md), owner → agent:** what the owner found on the phone. The agent
  can build and install, but cannot use the app, so this is the only way "this is wrong" reaches
  it.
- **[`DECISIONS.md`](DECISIONS.md), agent → owner:** questions only the owner can settle.

When a choice would change the app rather than the code, the agent does not guess and does not
stop. It writes the question in `DECISIONS.md` with a **default**, builds everything the answer
does not touch, and says in its handoff that the question is waiting. The owner answers on the
`**Answer:**` line, in any form; "A" is enough. Until then, the default is what the app does. An
answer that shapes the architecture becomes an ADR in [`docs/decisions/`](docs/decisions/).
