# AGENTS.md

For every coding agent working on Cents that is not Claude Code: Devin, Cursor, Gemini CLI
and anything else Orca launches. Claude Code loads `CLAUDE.md` by itself; you have to read it.

## Before writing any code, read these

1. **[`CLAUDE.md`](CLAUDE.md)**: the stack, the architecture and the **non-negotiable rules**
   (§4). Money is a `Long` of cents, dates are `java.time`, everything out of Room is a `Flow`,
   every schema change ships with a migration and a migration test, and every user-facing string
   lives in `strings.xml` with a Spanish translation in `values-es/`.
2. **[`docs/decisions/0001-ios-flavored-ui.md`](docs/decisions/0001-ios-flavored-ui.md)** and
   **[`0002-bubble-launcher.md`](docs/decisions/0002-bubble-launcher.md)**, before any composable.
   The app looks like iOS, not Material. Build from `ui/components`, not from default Material
   widgets.
3. **The task prompt you were given.** It names your branch and the only folders you may write
   to.

## How parallel work runs here

Several agents run at once, each in its own git worktree created by Orca from `origin/main`.
That is only safe because each one keeps to its own files, so these rules are not optional:

- **Write only inside the territory your task names.** If the work turns out to need a file
  outside it, stop and say so in your final message rather than editing it.
- **Never touch the global locks** unless your task explicitly says you own them: the Room schema
  (`data/local/` entities, `CentsDatabase`, migrations, `app/schemas/`), `ui/theme/`, and
  `gradle/libs.versions.toml`. Anything that changes one of those runs alone.
- **Do not edit the project's shared documents**: `CLAUDE.md`, `PLAN.md`, `README.md`,
  `DECISIONS.md`, `FEEDBACK.md`, `docs/device-check.md`. Two branches editing the same paragraph
  has already silently deleted a decision once. Put what you learned in the PR description
  instead; the documentation is updated in one place after merging.
- **New strings go in their own resource pair**: `values/strings_<feature>.xml` and
  `values-es/strings_<feature>.xml`, rather than appending to the shared `strings.xml`.
- **Shared wiring is one line.** A `@Binds` in `RepositoryModule`, a route or a section entry:
  add your line and change nothing around it, so a rebase conflicts on one line or none.
- **Start from `origin/main`, never from another open branch.** `main` squash-merges, so a
  stacked branch conflicts on every hunk once its parent lands.

## Before opening the PR

```bash
./gradlew spotlessApply
./gradlew spotlessCheck detekt testDebugUnitTest assembleDebug
```

All green, then read the test results rather than trusting `BUILD SUCCESSFUL` (`PLAN.md` C4
explains why). Commits are Conventional Commits in English (`feat(budgets): ...`). Open one PR
against `main`, and in its description say what was built, what was deliberately left out, and
anything a reviewer should look at first. Do not merge it: the owner merges after review.

Things no agent does: connect to bank APIs, add analytics or telemetry, invent a price, commit
`local.properties` or any key, or run `fallbackToDestructiveMigration`.
