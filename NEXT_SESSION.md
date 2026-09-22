# NEXT_SESSION.md — temporary handoff for the session after 2026-09-22

> ⚠️ **Temporary. This file exists only to carry context into the next session.** When every
> step below is done, **delete this file and the pointer to it at the top of `CLAUDE.md`**, in
> the same docs branch that records what the session did. Anything worth keeping goes into
> `PLAN.md` Part C, not here.

## Where the last session stopped

The second Orca wave is half done. **Agents for this wave: Devin, Cursor and Claude Code** (an
Orca instance working as an implementer). **The owner does not want agy/Antigravity used.**
Gemini CLI was used in the first wave. It is not in this one, because the owner chose these three.

| PR / branch | Agent | State on 2026-09-22 |
|---|---|---|
| **#68** `anderescaray/feat-trade-note` | Claude Code | Reviewed (`/code-review high`), fixes pushed, CI green. Two follow-ups recorded in `PLAN.md` C4. Probably merged by the owner already. |
| **#69** `anderescaray/feat-backup` | Devin | Inside its territory (checked with `--name-only`), CI green, +3146 lines. **A `/code-review high 69` was started at the end of the session; its result may never have been read.** Do not assume it was reviewed. |
| `anderescaray/feat-account-detail-undo` | Cursor | **Failed midway.** Uncommitted work in `C:\Users\ander\orca\workspaces\cents\feat-account-detail-undo`: `UndoBanner.kt` extracted to `ui/components`, AccountDetail screen and view model edited, a tests folder started. There is no commit and no PR. |

The OAuth client for Drive **exists** (the owner confirmed it on 2026-09-22), so #69 can be walked
on the phone.

## Steps for this session, in order

1. **Start-of-session checklist** (`PLAN.md` C1), with the toolchain paths from `CLAUDE.md` §6.
   `gh pr list --state all --limit 6` to see what the owner merged. `git checkout main && git pull --ff-only`.
2. **Read `FEEDBACK.md` and `DECISIONS.md`.** An *Open* item in `FEEDBACK.md` outranks everything.
3. **#68**: if it is still open, it is ready to merge. Tell the owner so.
4. **#69 (backup)**: run `/code-review high 69` again, unless the owner already has its result.
   Check with `gh pr view 69 --json commits` whether Devin pushed after 2026-09-22 18:24. Send any
   findings back to Devin as one message, the same way #68's were sent. Only then tell the owner it
   can be merged. **Before or right after merging, the owner walks the consent flow on the phone**:
   Settings › Google Drive › turn on, grant, Back up now, then Restore from Drive lists the backup.
   The steps are in the PR description. Remind them: while the consent screen stays in *Testing*,
   Google expires the grant after 7 days, and the app should then show *Reconnect*.
5. **Cursor's branch**: look at its worktree (`git -C <worktree> status` and `diff`). Then give the
   owner a message to paste into Cursor's Orca task so it finishes, commits, runs the gates and
   opens the PR. The original prompt is below. If Cursor cannot finish it, ask the owner whether
   Claude Code (in Orca) should take it over from the same worktree.
6. **After #69 merges: the rest of the C4 wave.**
   - **The "export first" line in the restore alert.** It waited because it touches
     `ui/feature/settings`, the same folder as #69.
   - **The midnight tick.** It touches many screens, including the account detail, so it waits for
     Cursor's branch too. Check its territory with a grep before assigning it.
   - **The two follow-ups from #68** in `PLAN.md` C4: the note on the cash movement, and a line
     limit in `CentsListRow`.
7. **After the wave, one docs branch:**
   - update `PLAN.md` Part C, `CLAUDE.md` §8 and the README
   - record the backup's new libraries in the `CLAUDE.md` §2 table (rule 11), using the
     justification lines in #69's description
   - fix CLAUDE.md §1, which says the only network calls are to the market data API; Drive is
     now one too, and the market data API is closed (D25)
   - add the device-walk steps for #63, #64, #65, #68, #69 and the account-detail undo to
     `docs/device-check.md`
   - then **delete this file and its pointer in `CLAUDE.md`**
8. **Orca cleanup**: once their branches are merged, ask the owner to delete the wave's worktrees
   from inside Orca. The auto-mode classifier blocks `git worktree remove` from here anyway.

## Cursor's original prompt (feat/account-detail-undo), for reference

It moves the private `UndoBanner` from `TransactionsScreen.kt` to `ui/components/UndoBanner.kt`,
unchanged, and shows it on the account detail. `AccountDetailViewModel` reads the same
`DeletedTransactionBuffer` (read-only) and gets `undoDeletion()` and `dismissUndo()`, mirroring
`TransactionsViewModel`. Its territory is exactly these files:
- `ui/components/UndoBanner.kt`
- `ui/feature/transactions/TransactionsScreen.kt` (only to call the moved banner)
- `ui/feature/accounts/AccountDetailScreen.kt` and `AccountDetailViewModel.kt`
- `values{,-es}/strings_account_undo.xml`, only if the "from the list" wording needs to change
- tests under `ui/feature/accounts/`

The tests expected: the view model covers a single movement, a transfer's two legs, and dismiss,
plus a Compose test that the banner is shown and Undo calls undo.

## Lessons from these two sessions, so they are not relearned

- **Always paste the prompt text inline in chat.** The owner copies it into Orca. A path to a
  scratchpad file is useless to them.
- **A Claude Code implementer in Orca loads `CLAUDE.md`, and with it this file.** Its prompt has
  to say at the top that it is an implementer, and that it must ignore `NEXT_SESSION.md` and the
  coordinator duties in §7.
- **Fetch the PR fresh before claiming it lacks something.** Compare against `origin/<branch>`
  or the agent's worktree under `C:\Users\ander\orca\workspaces\cents\`.
- **A branch stacked on an unmerged one conflicts after the squash.** The fix is
  `git rebase --onto origin/main <old parent commit>`.
- **Orca names branches `anderescaray/feat-...`**, so searches have to allow for that as well as `feat/...`.
- **A commit made after a PR's squash is lost.** The previous handoff file never reached `main`
  for that reason. Commit the handoff before the owner merges, or put it in its own PR, as this
  one is.
