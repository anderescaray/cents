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

## 2. Where things stand (2026-10-07)

- **`main`** is at `978fdb4` (#118). It has **Room v10** and **1014 JVM tests**. PRD phases 1 to 5
  are built, with prices typed by hand (D25). From Phase 6, accessibility is built (#96); the
  widget, the Baseline Profile and Macrobenchmark are not. What the app does is in
  [`README.md`](README.md#what-it-does).
- **Latest merges:**
  - #117: the movement form's category and account sheets, the keypad only for the amount, the
    last account remembered, and broker accounts kept apart (backlog items 2, 3, 4, 5, 9, 10).
  - #112: set an account's balance (Room v10).
  - #113: safer capture, with a notification per capture.
  - #114: the app lock removed (D31).
- **#112 was reviewed after it merged** (`/code-review high`, 2026-10-07). It has 9 findings,
  and they are the next branch (§3).
- **The Pixel runs `f08f20a`**, the 2026-10-05 build. #117 is not installed yet.
- **Documentation** was reorganised on 2026-10-07. This file was cut from 2,700 lines to the
  present only. Known limitations, testing and setup each got their own file, and `CLAUDE.md`
  gained its documentation rules (§8).

## 3. Next, in order

1. **`fix/balance-adjustments-review`**: #112's findings.
   - **Deleting an account and moving its movements carries its balance corrections across,
     but not its opening balance**, so the other account's balance and net worth move by money
     it never had. An account deleted empty loses its corrections, so the two paths disagree.
     *Planned fix:* the corrections are deleted with the account, like its opening balance.
     Confirm with the owner first; the alternative is to move the opening balance across too.
   - The balance screen falls back to `"EUR"` while it loads (rule 2).
   - Removing a correction has no undo and no error handling (rule 7).
   - The save's `runCatching` swallows cancellation.
   - The removal alert is held in `remember`, not `rememberSaveable`.
   - Derived values are computed in composables (§5).
   - The backup mapper for `balance_adjustments` is outside `BackupMappers.kt`.
   - Optional: a correction is measured against the all-time balance, so a future-dated movement
     skews it.
2. **`feat/app-language`** (backlog item 1).
3. **`feat/home-net-worth`** (backlog item 6).
4. **`feat/group-archive`** (backlog item 8; a Room migration, so it runs alone).
5. **`docs/refresh-prd-and-device-check`**, the rest of the documentation cleanup:
   - Rewrite [`docs/device-check.md`](docs/device-check.md) as a checklist by feature, for the
     current UI. Today it is a log of walks, most of them written for screens the rework
     replaced.
   - Bring [`docs/PRD.md`](docs/PRD.md) level with the decisions taken since it was written.

Later, unscheduled, is listed in the backlog: requests not yet placed, and engineering
follow-ups.

## 4. Waiting on the owner

- **Walk 21** on the build already on the Pixel, and **Walk 22** once #117 is installed
  ([`docs/device-check.md`](docs/device-check.md)). Anything they turn up goes in `FEEDBACK.md`.
- **D32**: whether subscriptions and capture should refuse broker accounts too, and how a
  dividend gets in.
- **Local branches** that are merged or superseded and can be deleted when the owner says so:
  `fix/capture-safety-and-feedback`, `docs/session-2026-10-05`, `fix/transaction-entry-ux` and
  `docs/session-2026-10-07`.
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
