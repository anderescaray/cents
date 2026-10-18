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

## 2. Where things stand (2026-10-18)

- **`main`** is at `8dd2a78` (#141), with **#140** (archiving a group), **Room v13** and **1140
  JVM tests**. PRD phases 1 to 5 are built, with prices typed by hand (D25). From Phase 6,
  accessibility is built (#96); the widget, the Baseline Profile and Macrobenchmark are not. What
  the app does is in [`README.md`](README.md#what-it-does).
- **The Pixel runs `8dd2a78`**, installed 2026-10-18 over the build before, every row kept: the
  migrations from v10 to v13 ran on the owner's data and Cents opened.
- **Walked on 2026-10-18 and correct:** Walks 23 to 25 and 27 to 29. Backlog items 1, 6, 7, 9 and
  11 are ticked, and what they settled moved to [`docs/PRD.md`](docs/PRD.md) §4.2 and §4.8.
- **Ruralvía was not a bug** (`FEEDBACK.md`): it was already chosen, so *Add an app* left it out,
  and Cents had no notification access. Capture now runs on the phone for the first time.
- **No question is open in `DECISIONS.md`.**
- **One finding of #112's review is left on purpose:** a correction is measured against the
  all-time balance ([`docs/known-limitations.md`](docs/known-limitations.md)).

## 3. Next, in order

The owner says when each one starts.

1. **Fixes from Walks 26 and 30**, if they turn anything up. Walk 26 is capture on real Ruralvía
   notifications, so it is the likelier of the two to.
2. **`docs/device-check-by-feature`**: rewrite [`docs/device-check.md`](docs/device-check.md) as a
   checklist by feature, for the current UI. Today it is a log of 30 walks, most of them written
   for screens the rework replaced. Then the rest of [`docs/PRD.md`](docs/PRD.md): its vision and
   §4.8 still speak of an iOS-flavoured look, which ADR 0003 replaced.
3. **Phase 6's remainder** (the widget, a Baseline Profile, Macrobenchmark), or a new request: the
   owner's choice.

## 4. Waiting on the owner

- **Walk 26** (w1–w5, capture matching a subscription) and **Walk 30** (g1–g3, archiving a group)
  on the build now on the Pixel. Notes go in `FEEDBACK.md`, or to the agent by step number.
- **This session's docs PR** (`docs/session-2026-10-18`), to review and merge.
- **What comes after §3's item 2.**
- **Local branches** that are merged and can be deleted when the owner says so: every
  `docs/session-*` except this one, `docs/professional-docs`, `docs/drive-client-and-walk-2026-09-24`,
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
