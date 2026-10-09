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

## 2. Where things stand (2026-10-09)

- **`main`** is at `f03f9f8` (#121). It has **Room v10** and **1024 JVM tests**. PRD phases 1 to 5
  are built, with prices typed by hand (D25). From Phase 6, accessibility is built (#96); the
  widget, the Baseline Profile and Macrobenchmark are not. What the app does is in
  [`README.md`](README.md#what-it-does).
- **Latest merges:**
  - #121: every undo window counts on the application's scope (`ui/undo/UndoWindow.kt`). It fixes
    a test from #120 that failed every time on Windows and passed on CI by timing.
  - #120: #112's review fixes. Removing a correction asks first and can be undone, the balance
    screen no longer shows `"EUR"` while it loads, and deleting an account moves its opening
    balance with its movements (D33).
  - #119: the documentation reorganised, with its rules in `CLAUDE.md` §8.
  - #117: the movement form's category and account sheets, and broker accounts kept apart.
- **#121 had an independent review** (2026-10-09). It found nothing in #121 and one problem in
  #120's code, which is §3 item 1.
- **The Pixel runs `f03f9f8`**, installed 2026-10-09 with every row kept. **Walk 22 passed**, so
  backlog items 2, 3, 4, 5 and 10 are done.
- **One finding of #112's review is left on purpose:** a correction is measured against the
  all-time balance ([`docs/known-limitations.md`](docs/known-limitations.md)).

## 3. Next, in order

The owner says when each one starts.

1. **The walk fixes** (`fix/walk-21`, or a better name once the notes are in): the owner's Walk 21
   and Walk 23 notes from `FEEDBACK.md`, each checked against `main` first. Plus the problem the
   independent review found in #120's code:
   - **A movement's Undo hides a removed correction's Undo until the correction's window has run
     out.** `AccountDetailUiState.undoableCorrection` is null while `undoableDeletion` shows, but
     the correction's clock keeps running. Remove a correction, then delete a movement of the same
     account within six seconds, and the correction can no longer be put back.
   - *Fix:* start the correction's window only when its banner is the one showing, or show both
     banners. If the notes are slow to come, this goes alone as `fix/correction-undo-hidden`.
2. **`feat/app-language`** (backlog item 1).
3. **`feat/account-types`** (backlog item 11): only cash, bank and broker. A Room migration to
   v11, so it runs alone. It needs D34's first choice, or builds on its default.
4. **`feat/home-net-worth`** (backlog item 6, reshaped 2026-10-09): normal money and invested
   money kept apart, on Wallet and at the top of Home. It needs D34's second choice, or builds on
   its default.
5. **`feat/group-archive`** (backlog item 8; a Room migration, so it runs alone).
6. **`docs/refresh-prd-and-device-check`**, the rest of the documentation cleanup:
   - Rewrite [`docs/device-check.md`](docs/device-check.md) as a checklist by feature, for the
     current UI. Today it is a log of walks, most of them written for screens the rework
     replaced.
   - Bring [`docs/PRD.md`](docs/PRD.md) level with the decisions taken since it was written,
     including the account types and the money split once they are built.

Items 3 and 4 were asked for on 2026-10-09 and can go ahead of item 2 if the owner prefers.
Later, unscheduled, is listed in the backlog: requests not yet placed, and engineering
follow-ups.

## 4. Waiting on the owner

- **The Walk 21 notes**, and **Walk 23** (r1–r3) on the build now on the Pixel
  ([`docs/device-check.md`](docs/device-check.md)). They go in `FEEDBACK.md`.
- **D34**: what an existing Card, Savings or Other account becomes, and whether a total of normal
  and invested money is still shown.
- **D32**: whether subscriptions and capture should refuse broker accounts too, and how a
  dividend gets in.
- **This session's docs PR** (`docs/session-2026-10-09`), to review and merge.
- **Local branches** that are merged or superseded and can be deleted when the owner says so:
  `fix/capture-safety-and-feedback`, `fix/transaction-entry-ux`,
  `fix/correction-undo-test-race`, `docs/session-2026-10-05`, `docs/session-2026-10-07`,
  `docs/professional-docs` and `docs/drive-client-and-walk-2026-09-24`, plus the worktree
  `C:\dev\cents-docs-plan`.
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
