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

- **`main`** is at `4baaa94` (#149), with **Room v13** and **1141 JVM tests**. PRD phases 1 to 5
  are built, with prices typed by hand (D25). Phase 6 is done as far as it is wanted:
  accessibility (#96), R8, the Baseline Profile and Macrobenchmark (#146); the widget was dropped
  (D38). What the app does is in [`README.md`](README.md#what-it-does).
- **Latest merges (2026-10-25):** #149, *Show everything again* first on the Filters screen
  (item 14); #148, the figure switches govern Home only and Wallet always shows all three (item
  13, D37); #147, documentation.
- **Dropped by the owner (D38):** the home-screen widget, and hiding a tab.
- **The Pixel runs `8dd2a78`**, installed 2026-10-18. #143, #146, #148 and #149 are not on it.
- **No question is open in `DECISIONS.md`.**
- **One finding of #112's review is left on purpose:** a correction is measured against the
  all-time balance ([`docs/known-limitations.md`](docs/known-limitations.md)).

## 3. Next, in order

1. **UI and UX improvements**, the owner's choice on 2026-10-25. What exactly is being scoped
   with the owner; it becomes backlog items before any branch starts. The look stays ADR 0003's.
2. **Then, from the backlog's *Requested, not yet placed*:** the savings card on Reports,
   deleting one Drive backup, resizable Reports cards.

## 4. Waiting on the owner

- **This session's docs PR** (`docs/home-only-figures-and-scope`), to review and merge.
- **A4 and M2 on the phone**, items 13 and 14, once a new build is installed.
- **What the UI and UX work should cover** (§3, item 1).
- **Branches** that are merged or superseded and can be deleted when the owner says so. On
  GitHub: `docs/session-2026-10-18` (#142), `docs/next-improvements` (#110) and
  `feat/groups-screens` (#87). Locally: `feat/baseline-profile`, `fix/figures-home-only`,
  `feat/clear-filters-at-top`, `docs/baseline-profile-and-groups-checked`, every
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
