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

- **`main`** is at `20fe13a` (#131). It has **Room v11** and **1095 JVM tests**. PRD phases 1 to 5
  are built, with prices typed by hand (D25). From Phase 6, accessibility is built (#96); the
  widget, the Baseline Profile and Macrobenchmark are not. What the app does is in
  [`README.md`](README.md#what-it-does).
- **Latest merges:**
  - #131: capture recognises a subscription's charge instead of counting it twice (backlog item
    12, D35). `capture_matches` is Room v11. Its review changed three of the backlog's defaults
    and added a fourth; the backlog item says which, and why. Phone check: Walk 26.
  - #130: the documents after D34 and D36 were answered.
  - #129: D36 answered B. With English chosen on a Spanish phone, amounts and dates keep the
    phone's style (`1.234,56 €`, `12 oct`); the currency stays the phone's.
  - #127: Settings › Language, the last row of Settings: the phone's language, English or Español
    (backlog item 1).
  - #125: the test database runs every query on the thread that asks it, which ended the Room
    races behind the flaky view-model tests ([`docs/testing.md`](docs/testing.md)).
- **Answered on 2026-10-12:** D32, D34, D35 and D36. No question is open.
- **Reported on 2026-10-12:** Ruralvía, the owner's bank, is missing from Settings › Capture's
  list of apps, so its payments cannot be captured (`FEEDBACK.md`).
- **A cloud session runs every gate**, with the SDK from `dl.google.com`, the JDK 25 daemon
  through foojay, and Maven Central through Google's mirror ([`docs/setup.md`](docs/setup.md)).
- **The Pixel runs `f03f9f8`**, installed 2026-10-09 with every row kept. **Walk 22 passed.**
  Walks 24, 25 and 26 need a newer build.
- **One finding of #112's review is left on purpose:** a correction is measured against the
  all-time balance ([`docs/known-limitations.md`](docs/known-limitations.md)).

## 3. Next, in order

The owner says when each one starts.

1. **`fix/capture-app-list`**: Ruralvía in Settings › Capture's list (`FEEDBACK.md`), once the
   owner says where it is installed and how its payments notify.
2. **#133 merged** (backlog item 9, D32 answered): a broker takes an income but not an expense,
   and subscriptions and capture stop offering brokers.
3. **The walk fixes** (`fix/walk-21`, or a better name once the notes are in): the owner's notes
   from Walks 21 and 23 to 26 in `FEEDBACK.md`, each checked against `main` first.
4. **`feat/account-types`** (backlog item 11, D34 answered): only cash, bank and broker. Room v12,
   so it runs alone.
5. **`feat/home-net-worth`** (backlog item 6, D34 answered): normal money and invested money kept
   apart, on Wallet and at the top of Home, with the owner choosing which figures show.
6. **`feat/group-archive`** (backlog item 8; a Room migration, so it runs alone).
7. **`docs/refresh-prd-and-device-check`**, the rest of the documentation cleanup:
   - Rewrite [`docs/device-check.md`](docs/device-check.md) as a checklist by feature, for the
     current UI. Today it is a log of walks, most of them written for screens the rework
     replaced.
   - Bring [`docs/PRD.md`](docs/PRD.md) level with the decisions taken since it was written,
     including the account types and the money split once they are built.

Items 4 to 6 do not wait on the walks. Each Room migration takes the next version number when it
merges. Later, unscheduled, is listed in the backlog: requests not yet placed, and engineering
follow-ups.

## 4. Waiting on the owner

- **A build with #131 on the Pixel**, then **Walks 24, 25 and 26**
  ([`docs/device-check.md`](docs/device-check.md)). Walk 26 uses a test account and small Bizums.
- **The Walk 21 notes**, and **Walk 23** (r1–r3). They go in `FEEDBACK.md`.
- **Ruralvía:** whether it is in a work profile or the private space, and whether a card payment
  notifies from Ruralvía or by SMS.
- **Reviewing and merging #133** (D32).
- **D36's names:** with English chosen on a Spanish phone, month and weekday names are Spanish
  (`Octubre` over Home, `10 oct` under *Yesterday*), as B's own `12 oct` is. If English names in
  the phone's order are wanted instead, that is a new answer.
- **Which item starts next**, of §3's 4 to 6.
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
