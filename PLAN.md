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

## 2. Where things stand (2026-11-01)

- **`main`** is at `0d05aed` (#161), with **Room v13** and **1155 JVM tests**. PRD phases 1 to 5
  are built, with prices typed by hand (D25). Phase 6 is done as far as it is wanted:
  accessibility (#96), R8, the Baseline Profile and Macrobenchmark (#146); the widget was dropped
  (D38). What the app does is in [`README.md`](README.md#what-it-does).
- **Built on 2026-11-01, not merged:** the owner's two requests of 2026-10-31.
  - **#163**, Settings from every tab's header (backlog item 24).
  - **#164**, the general budget (backlog item 23). It takes **Room v14** and amends D26. With it,
    1168 JVM tests.
  - Both had `/code-review high`, and each PR says what was fixed. On #163 the review changed the
    design: Settings opens in Home's stack from any tab, so there is only ever one.
- **The Pixel runs `4416198`**, #160's branch, the same code as `main`, installed 2026-10-28.
  Neither #163 nor #164 is on it.
- **D39 was answered on 2026-10-28** and is not built yet: §3, item 2.
- **One finding of #112's review is left on purpose:** a correction is measured against the
  all-time balance ([`docs/known-limitations.md`](docs/known-limitations.md)).

## 3. Next, in order

1. **#163 and #164 merged**, then installed and checked on the phone. The check steps are in each
   PR; once merged they go into `docs/device-check.md` (U and H).
2. **A variable bill knows who it is paid to** (D39): words to recognise each variable bill by,
   and a *Yes / No* question when the date fits but no word does. Everything needed to build it,
   defaults included, is backlog item 22 in
   [`docs/next-improvements.md`](docs/next-improvements.md). It changes the schema, **Room v15**
   after #164's v14, so it starts after #164 is merged, on one branch.
3. **Whatever the owner finds on the phone** in #151–#157 (`FEEDBACK.md`).
4. **Regenerate the Baseline Profile** (`docs/testing.md`): Home, the category sheet, the
   navigation and the reminder sheet have changed since it was recorded.
5. **More UI polish**, the owner's direction of 2026-10-25 (a little more impressive, still
   basic; built, then judged on the phone). The next candidate is a haptic on save.
6. **Then, from the backlog's *Requested, not yet placed*:** the savings card on Reports,
   deleting one Drive backup, resizable Reports cards.

## 4. Waiting on the owner

- **Three PRs to review and merge:** #163 and #164 (code), and #162 (this docs PR). #164 before
  D39 starts, because both change the schema.
- **One reading to confirm, on #163:** Reports has no filters button, so the button placed beside
  Settings there is Customize. And back from Settings goes to Home, whichever tab's button opened
  it.
- **Checks on the phone:** H5, H7, H8, C1, R3, G1, G2 and X2 for items 15 to 19, and A4 and M2
  for items 13 and 14 (`docs/device-check.md`); then #163's and #164's.
- **Branches** that are merged or superseded and can be deleted when the owner says so: on
  GitHub, every branch whose PR is merged (`git branch -r --merged origin/main` lists only some,
  because `main` squash-merges); locally, the same, plus the worktree `C:\dev\cents-docs-plan`.
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
