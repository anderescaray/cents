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

- **`main`** has the owner's two requests of 2026-10-31 merged: Settings from every tab's header
  (#163) and the general budget (#164, **Room v14**). PRD phases 1 to 5 are built. Phase 6 is done
  as far as it is wanted: accessibility (#96), R8, the Baseline Profile and Macrobenchmark (#146);
  the widget was dropped (D38). What the app does is in [`README.md`](README.md#what-it-does).
- **The Pixel runs #163 and #164** from a local branch merging both, installed 2026-11-01: the
  code `main` now has.
- **D40, answered on 2026-11-01:** investments are rebuilt. **Built in #167, not merged**: Room v15,
  the Friday reminder included, 1103 JVM tests. `/code-review high` ran on its first commit and all
  ten findings are fixed; the fixes and the reminder are reviewed by their author only (D6).
- **One finding of #112's review is left on purpose:** a correction is measured against the
  all-time balance ([`docs/known-limitations.md`](docs/known-limitations.md)).

## 3. Next, in order

1. **Investments, rebuilt** (D40, backlog item 25): #167 merged, installed on the Pixel and
   checked there; its check steps are in the PR until they join `docs/device-check.md`.
2. **A variable bill knows who it is paid to** (D39), backlog item 22, on **Room v16**.
3. **Whatever the owner finds on the phone** (`FEEDBACK.md`).
4. **Regenerate the Baseline Profile** (`docs/testing.md`): Home, the category sheet, the
   navigation, the reminder sheet and now the investments have changed since it was recorded.
5. **More UI polish**, the owner's direction of 2026-10-25. The next candidate is a haptic on save.
6. **Then, from the backlog's *Requested, not yet placed*:** the savings card on Reports,
   deleting one Drive backup, resizable Reports cards.

## 4. Waiting on the owner

- **This docs PR and #167** to review and merge; #167 to try on the phone first (the Pixel was not
  connected when it was built).
- **Checks on the phone:** H5, H7, H8, C1, R3, G1, G2 and X2 for items 15 to 19, A4 and M2 for
  items 13 and 14 (`docs/device-check.md`), and #163's and #164's steps, which are in each PR
  until they join `docs/device-check.md`.
- **One reading to confirm, on #163:** Reports has no filters button, so the button placed beside
  Settings there is Customize.
- **Branches** that are merged or superseded and can be deleted when the owner says so: on
  GitHub, every branch whose PR is merged (`git branch -r --merged origin/main` lists only some,
  because `main` squash-merges); locally, the same, plus the worktree `C:\dev\cents-docs-plan` and
  the local `try/settings-and-budget`.
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
