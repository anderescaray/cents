# NEXT_SESSION.md — temporary handoff for the session after 2026-09-21

> ⚠️ **Temporary. This file exists only to carry context into the next session.** When every
> step below is done, **delete this file and the pointer to it at the top of `CLAUDE.md`**, in
> the same docs branch that records what the session did. Anything worth keeping goes into
> `PLAN.md` Part C, not here. `PLAN.md` stays the permanent record; this is a sticky note.

## Where the last session stopped

The first parallel wave through **Orca** (D24) is done. Devin, Cursor and Gemini each built one
branch, Claude Code reviewed them, and all of them are merged: #61 (reminders), #62 (the Orca
setup: `AGENTS.md`, `GEMINI.md`, `.worktreeinclude`), #63 (committed this month, Devin), #64
(closed positions, Cursor), #65 (transfer undo, Gemini). D25: no paid price provider, ever.

**Left open for the owner to merge:**
- **#66** `fix/subscription-totals-currency`: the one finding from the review of #63 (an empty
  side of the totals is zero in any currency). Gates were green locally, 700 tests.
- **#67** `docs/first-orca-wave`: the docs pass for all of the above, plus this file.

The machine is set up for Orca: long paths are enabled (Windows `LongPathsEnabled` and git
`core.longpaths`), the old `.claude/worktrees` are gone, and the Gemini, Cursor and Devin
command-line tools are installed and logged in.

## Steps for this session, in order

1. **Start-of-session checklist** (`PLAN.md` C1), with the toolchain paths from `CLAUDE.md` §6.
   Check with `gh pr list` whether #66 and #67 are merged. If they are not, ask the owner to
   merge them before anything else, because the docs a new wave depends on are in #67. Then
   `git checkout main && git pull --ff-only`.
2. **Read `FEEDBACK.md` and `DECISIONS.md`.** An *Open* item in `FEEDBACK.md` outranks
   everything below.
3. **Check that Orca is clean.** `git worktree list` should show only `C:\dev\cents`. If the
   three wave worktrees (`feat-committed-this-month`, `feat-closed-positions`,
   `feat-undo-a-deleted-transfer`) are still there, ask the owner to delete them from Orca.
   Their branches are merged. Deleting them from inside Orca keeps its state consistent, and
   the auto-mode classifier blocks `git worktree remove` from this session anyway.
4. **Ask the owner: has the Google Cloud OAuth client been created?** The steps are in the
   `feat/backup` entry in `PLAN.md` C3. If yes, write the Devin prompt for **`feat/backup`**.
   It runs **alone**, because it adds `play-services-auth` and the Drive REST client to
   `libs.versions.toml`. Use `AuthorizationClient` with the `drive.appdata` scope and the Drive
   REST API, and reuse the existing JSON export and restore (D21, D22). Nothing secret goes in
   the repo.
5. **Ask the owner which wave comes next.** Everything left is a later roadmap phase (§9) or
   deferred work, so it is their choice. The options are in `PLAN.md` → *Next concrete action*:
   - **Small C4 gaps.** These are disjoint and need no schema change, so they suit three agents
     at once: the undo banner on the account detail, the "export first" line in the restore
     alert, the trade note field, and the midnight tick. The midnight tick touches many
     screens, so check its territory before pairing it with anything.
   - **Phase 5**: the uncategorized inbox and budgets. Budgets need a table, so their
     schema-and-migration branch runs **alone** first, and the UI work fans out after it.
   - **Phase 6**: the widget, the Baseline Profile with Macrobenchmark, and accessibility. The
     first two add dependencies, so each runs alone.
   - The dashboard of cards (`PLAN.md` C7).
6. **For the chosen wave, write one prompt per agent** in the shape used on 2026-09-21: branch
   name, "read AGENTS.md then …", what to build, a **territory list** of the only files it may
   write, the tests expected, and "run the gates in AGENTS.md, open a PR, do not merge".
   Before handing them over, **check the territories against the code with a grep**: two
   prompts that look disjoint can still share a file. Roles (D24): Devin takes the heaviest
   work, Cursor UI kept inside one feature folder, Gemini small, well-scoped work.
7. **As each PR arrives:** check it stayed inside its territory (`gh pr diff N --name-only`),
   then run `/code-review high N`. Merging is the owner's job. After each merge, tell the other
   agents to rebase onto `origin/main`.
8. **After the wave, one docs branch** updates `PLAN.md` Part C, `CLAUDE.md` §8 and the README.
   It also **adds the device-walk steps for #63, #64 and #65** to `docs/device-check.md`, which
   #67 left undone. Then **delete this file and its pointer in `CLAUDE.md`.**

## Lessons from the last session, so they are not relearned

- **Fetch the PR fresh before claiming it lacks something.** `git fetch origin pull/N/head` goes
  stale once an agent pushes again, and on 2026-09-21 that made Gemini's fix look missing when
  it was not. Compare against `origin/<branch>` or the agent's worktree under
  `C:\Users\ander\orca\workspaces\cents\`.
- **A branch stacked on an unmerged one conflicts after the squash.** It happened to #61. The
  fix is `git rebase --onto origin/main <old parent commit>`, after checking with `git diff`
  that the old commit is identical to the squash that landed.
- **`keytool` crashes on the Spanish-locale JDK** unless it is run with `-J-Duser.language=en`.
- **A `!` prefix only works in the Claude prompt.** Pasted into Git Bash, `!` negates the
  command's result, so `! a && b` skips `b`.
- **Orca may name a branch `anderescaray/feat-...` rather than `feat/...`.** #64 and #65 were
  named that way, while Devin's #63 kept `feat/...`. It makes no difference to the work, but
  searches by branch name have to allow for both.
