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

## 2. Where things stand (2026-10-12)

- **`main`** is at `2e5b73e` (#122). It has **Room v10** and **1024 JVM tests**. PRD phases 1 to 5
  are built, with prices typed by hand (D25). From Phase 6, accessibility is built (#96); the
  widget, the Baseline Profile and Macrobenchmark are not. What the app does is in
  [`README.md`](README.md#what-it-does).
- **Open: #123**, the fix for the problem #121's independent review found in #120's code. A
  removed correction's Undo now waits behind a movement's Undo instead of running out unseen.
  CI is green on `15f0d64`, and it waits on review. It brings the suite to 1028 tests, and
  Walk 24 (b1) checks it on the phone.
- **Latest merges:**
  - #122: the 2026-10-09 session's documents.
  - #121: every undo window counts on the application's scope (`ui/undo/UndoWindow.kt`).
  - #120: #112's review fixes. Removing a correction asks first and can be undone, and deleting an
    account moves its opening balance with its movements (D33).
- **D35 was answered on 2026-10-12:** capture stops counting a subscription's charge twice. It is
  backlog item 12, not built, and waits on the owner's go.
- **A cloud session now runs every gate**, with the SDK from `dl.google.com`, the JDK 25 daemon
  through foojay, and Maven Central through Google's mirror ([`docs/setup.md`](docs/setup.md)).
- **The Pixel runs `f03f9f8`**, installed 2026-10-09 with every row kept. **Walk 22 passed.**
- **One finding of #112's review is left on purpose:** a correction is measured against the
  all-time balance ([`docs/known-limitations.md`](docs/known-limitations.md)).

## 3. Next, in order

The owner says when each one starts.

1. **The walk fixes** (`fix/walk-21`, or a better name once the notes are in): the owner's Walk 21
   and Walk 23 notes from `FEEDBACK.md`, each checked against `main` first. The problem the
   review found is no longer part of it: that is #123.
2. **`feat/capture-matches-subscriptions`** (backlog item 12, D35). **Proposed here, for the
   owner to confirm.** It goes ahead of the language because it fixes wrong money: every notified
   subscription charge counts twice in spending, budgets and the month's figure until the extra
   one is deleted by hand. If it needs a stored marker, it is a Room migration and runs
   alone.
3. **`feat/app-language`** (backlog item 1).
4. **`feat/account-types`** (backlog item 11): only cash, bank and broker. A Room migration, so it
   runs alone. It needs D34's first choice, or builds on its default.
5. **`feat/home-net-worth`** (backlog item 6): normal money and invested money kept apart, on
   Wallet and at the top of Home. It needs D34's second choice, or builds on its default.
6. **`feat/group-archive`** (backlog item 8; a Room migration, so it runs alone).
7. **`docs/refresh-prd-and-device-check`**, the rest of the documentation cleanup:
   - Rewrite [`docs/device-check.md`](docs/device-check.md) as a checklist by feature, for the
     current UI. Today it is a log of walks, most of them written for screens the rework
     replaced.
   - Bring [`docs/PRD.md`](docs/PRD.md) level with the decisions taken since it was written,
     including the account types and the money split once they are built.

Items 4 and 5 were asked for on 2026-10-09 and can go ahead of item 3 if the owner prefers. Each
Room migration takes the next version number when it merges, so the backlog's *v11* for item 4
holds only if it lands first. Later, unscheduled, is listed in the backlog: requests not yet
placed, and engineering follow-ups.

## 4. Waiting on the owner

- **#123**, to review and merge; then Walk 24 (b1) on a build with it.
- **Backlog item 12 (D35):** the go to build it, and whether item 2 above is its place. Its
  section lists what D35 left open, each with a default. Two are worth a look first: a fixed
  subscription matches only at its own amount, and a waiting variable bill takes the first capture
  of its kind in its account within the window, whatever that payment was for.
- **The Walk 21 notes**, and **Walk 23** (r1–r3) on the build now on the Pixel
  ([`docs/device-check.md`](docs/device-check.md)). They go in `FEEDBACK.md`.
- **D34**: what an existing Card, Savings or Other account becomes, and whether a total of normal
  and invested money is still shown.
- **D32**: whether subscriptions and capture should refuse broker accounts too, and how a
  dividend gets in.
- **This session's docs PR** (`docs/session-2026-10-12`), to review and merge.
- **Local branches** that are merged or superseded and can be deleted when the owner says so:
  `fix/capture-safety-and-feedback`, `fix/transaction-entry-ux`,
  `fix/correction-undo-test-race`, `docs/session-2026-10-05`, `docs/session-2026-10-07`,
  `docs/session-2026-10-09`, `docs/professional-docs` and
  `docs/drive-client-and-walk-2026-09-24`, plus the worktree `C:\dev\cents-docs-plan`.
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
