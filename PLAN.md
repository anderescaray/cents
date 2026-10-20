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

## 2. Where things stand (2026-10-19)

- **`main`** is at `fbe642c` (#144), with **Room v13** and **1140 JVM tests**. PRD phases 1 to 5
  are built, with prices typed by hand (D25). From Phase 6, accessibility is built (#96); the
  widget, the Baseline Profile and Macrobenchmark are not. What the app does is in
  [`README.md`](README.md#what-it-does).
- **Latest merges:** #144, [`docs/device-check.md`](docs/device-check.md) as one checklist by
  feature (step codes such as H3 and K6) and [`docs/PRD.md`](docs/PRD.md) level with the
  decisions; #143, Home's first-account message names a bank account or cash rather than a card;
  #142, Walks 23 to 25 and 27 to 29 passed and Ruralvía closed as not a bug.
- **Capture works on the phone** (2026-10-19), including recognising a subscription's charge
  (K6–K10). Backlog item 12 is ticked and what it settled is in `docs/PRD.md` §4.6. Every
  confirmed request is now checked except item 8, archiving a group (G2–G4).
- **The Pixel runs `8dd2a78`**, installed 2026-10-18 over the build before, every row kept. #143 is
  a string change, so it can wait for the next install.
- **No question is open in `DECISIONS.md`.**
- **One finding of #112's review is left on purpose:** a correction is measured against the
  all-time balance ([`docs/known-limitations.md`](docs/known-limitations.md)).

## 3. Next, in order

The owner says when each one starts.

1. **A fix from G2–G4**, if archiving a group turns anything up.
2. **Phase 6's remainder** (the widget, a Baseline Profile, Macrobenchmark), or a request from
   the backlog's *Requested, not yet placed*: the owner's choice. It does not wait on G2–G4.

## 4. Waiting on the owner

- **G2–G4** (archiving a group) in [`docs/device-check.md`](docs/device-check.md), on the build now
  on the Pixel. Notes go in `FEEDBACK.md`, or to the agent by step code.
- **This session's docs PR** (`docs/capture-checked`), to review and merge.
- **What comes next** (§3, item 2).
- **Branches** that are merged or superseded and can be deleted when the owner says so. On
  GitHub: `docs/session-2026-10-18` (#142 and #144), `docs/next-improvements` (#110, its later plan
  notes long rewritten) and `feat/groups-screens` (#87). Locally: every `docs/session-*`,
  `docs/professional-docs`, `docs/drive-client-and-walk-2026-09-24`,
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
