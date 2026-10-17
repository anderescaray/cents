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

## 2. Where things stand (2026-10-17)

- **`main`** has **#138**: cash and bank apart from invested, on Wallet and at the top of Home
  (backlog item 6, D34), with **Room v12** and **1129 JVM tests**. PRD phases 1 to 5 are built,
  with prices typed by hand (D25). From Phase 6, accessibility is built (#96); the widget, the
  Baseline Profile and Macrobenchmark are not. What the app does is in
  [`README.md`](README.md#what-it-does).
- **Latest merges:**
  - #138: Wallet and Home show cash and bank, invested (the brokers' cash and holdings) and their
    total, each switched on or off in Settings › *Wallet and Home*; by default the two parts and
    no total. Walk 29.
  - #136: an account is Cash, Bank or Broker. Room v12 rewrites any Card, Savings or Other
    account to Bank, and so does restoring an older backup. Walk 28.
  - #134: Settings › Capture's list of apps names each app as its icon does, and offers any app
    with a notification showing now, for Ruralvía, the owner's bank, which was missing
    (`FEEDBACK.md`). Walk 27, y1.
  - #133: D32. A broker takes an income but not an expense, and subscriptions and capture do not
    offer brokers. Walk 27, y2 and y3.
  - #131: capture recognises a subscription's charge instead of counting it twice (backlog item
    12, D35), with Room v11. Walk 26.
- **No question is open in `DECISIONS.md`.** D32, D34, D35 and D36 were answered on 2026-10-12;
  what #138 settled while building is in backlog item 6, each line one the owner can change.
- **A cloud session runs every gate**, with the SDK from `dl.google.com`, the JDK 25 daemon
  through foojay, and Maven Central through Google's mirror ([`docs/setup.md`](docs/setup.md)).
- **The Pixel runs `f03f9f8`**, installed 2026-10-09 with every row kept. **Walk 22 passed.**
  Walks 24 to 29 need a newer build; the owner installs it and walks them later, all together.
- **One finding of #112's review is left on purpose:** a correction is measured against the
  all-time balance ([`docs/known-limitations.md`](docs/known-limitations.md)).

## 3. Next, in order

The owner says when each one starts.

1. **The walk fixes** (`fix/walk-21`, or a better name once the notes are in): the owner's notes
   from Walks 21 and 23 to 29 in `FEEDBACK.md`, each checked against `main` first. Ruralvía comes
   first if Walk 27's y1 still cannot find it.
2. **`feat/group-archive`** (backlog item 8; a Room migration, v13, so it runs alone). It does not
   wait on the walks.
3. **`docs/refresh-prd-and-device-check`**, the rest of the documentation cleanup:
   - Rewrite [`docs/device-check.md`](docs/device-check.md) as a checklist by feature, for the
     current UI. Today it is a log of walks, most of them written for screens the rework
     replaced.
   - Bring [`docs/PRD.md`](docs/PRD.md) level with the decisions taken since it was written,
     including the account types and the money split.

Later, unscheduled, is listed in the backlog: requests not yet placed, and engineering follow-ups.

## 4. Waiting on the owner

- **A build with #138 on the Pixel**, then the walks in
  [`docs/device-check.md`](docs/device-check.md), all in one go: **Walk 27's y1 first** (Ruralvía
  in Capture's list), then Walks 24 to 29.
- **The Walk 21 notes**, and **Walk 23** (r1–r3). They go in `FEEDBACK.md`.
- **When item 8 starts.**
- **D36's names:** with English chosen on a Spanish phone, month and weekday names are Spanish
  (`Octubre` over Home, `10 oct` under *Yesterday*), as B's own `12 oct` is. If English names in
  the phone's order are wanted instead, that is a new answer.

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
