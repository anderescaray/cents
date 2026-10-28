# CLAUDE.md

Operating context for Claude Code. This file is loaded automatically in every session,
so it holds **rules and commands**, not the product description.

> **What we are building → [`docs/PRD.md`](docs/PRD.md)** (features, scope, roadmap and
> feasibility analysis). If this file and the PRD disagree about *what* to build, the PRD
> wins; about *how* to build it, this file wins.

> **Where the project stands, and what is next → [`PLAN.md`](PLAN.md).** The backlog it draws
> from is [`docs/next-improvements.md`](docs/next-improvements.md). Read both before choosing a
> task, after `FEEDBACK.md` and `DECISIONS.md` (§8).

---

## 1. The project in one sentence

Native Android personal finance app: income and expense tracking with user-defined
categories, multiple accounts with transfers between them, visual analytics, and an
investment portfolio with prices pulled from the internet.

- **Name:** Cents
- **Package:** `io.github.anderescaray.cents` *(immutable — never change)*
- **Repo:** <https://github.com/anderescaray/cents> (private)
- **Platform:** Android only. No iOS, no web.
- **Local-first:** all data lives on the device. No backend, no user accounts. The only
  network calls are to **Google Drive**, for the automatic backup the owner turns on (D22), into
  the app's own hidden folder. There is no market data API: prices are typed, for good (D25).
- **Users:** exactly one (the owner). No multi-user, no sharing.

## 2. Stack

| Layer | Choice | Why |
|---|---|---|
| Language | Kotlin | Official Android standard |
| UI | Jetpack Compose on Foundation, with Material 3 only as a **base** | Compose gives layout, insets and accessibility; the look is the app's own — the ring, cards and chips of [`docs/decisions/0003-the-ring.md`](docs/decisions/0003-the-ring.md) (rule 12) |
| Architecture | MVVM with `data` / `domain` / `ui` layers | Testable, with clear boundaries for parallel work |
| State | `StateFlow` + unidirectional data flow | One immutable `UiState` per screen |
| Database | Room (with KSP) | Typed SQLite, returns `Flow`, versioned migrations |
| DI | Hilt | De facto standard, integrates with Compose and WorkManager |
| Navigation | Navigation Compose (type-safe routes) | |
| Charts | Vico | Compose-native charting library, actively maintained. Cartesian charts *and* a pie/donut chart, so the two Phase 2 charts need no second library |
| Networking | OkHttp (**5.3.2**) + kotlinx.serialization | **Google Drive only**, since `feat/backup` (#69): multipart upload, list, download and delete against the Drive REST v3 surface, which is small enough that the Drive Java SDK and its Google HTTP client and Guava stack would be most of the app's size for four calls. Retrofit was planned for market data and never added: D25 closed that door. `kotlinx-serialization-json` is also declared directly: the type-safe navigation routes have always used it through navigation-compose, and the export writes a backup file with it — which is exactly the thing not to hand-roll an escaper for |
| Drive authorization | `play-services-auth` (**22.0.0**) | The supported `AuthorizationClient`, for the single `drive.appdata` grant and a short-lived access token per operation. Nothing is stored and no client secret exists: an Android OAuth client is matched by package name and signing certificate (D22, rule 6) |
| Emoji picker | `androidx.emoji2:emoji2-emojipicker` (**1.7.0**) | Since #155: Android's own picker, with every emoji the phone has, by group, with recents and skin tones, for a category's emoji. `emoji2` was already in the app through Compose, and a list of our own would always miss the one wanted |
| Preferences | DataStore (Preferences) | Modern replacement for SharedPreferences. In use for capture's settings (which apps, their accounts, each app's words; D29), the order of the Reports cards, and the movement form's last account. **One exception:** before Android 13 the app's own language is a `SharedPreferences` file, because it is read synchronously in `attachBaseContext`, before injection and the first frame; from 13 the system keeps it (`ui/language/AppLanguages.kt`, #127) |
| Background work | WorkManager (**2.11.0**) | **In use since `feat/subscription-reminders`**: one daily job that asks what reminders land today, and since #69 a second, the daily Drive backup, scheduled only while its switch is on. Nothing is scheduled per reminder — a queue of alarms would be a second copy of when the rent is, and the two would drift. Reached without `androidx.hilt:hilt-work`: a plain `CoroutineWorker` asks the Hilt graph through an `@EntryPoint`, which is two fewer artifacts and no `Configuration.Provider` (rule 11) |
| Build | Gradle KTS + version catalog (`gradle/libs.versions.toml`) | Single source of versions |
| Code quality | detekt + ktlint (via Spotless) | Enforced in CI |
| Tests | JUnit + MockK + Turbine + in-memory Room (on the JVM via Robolectric) + Compose UI Test, plus `mockwebserver3` and `work-testing` | Robolectric lets DAO and migration tests run in CI, which has no device. **MockWebServer** checks the real HTTP the Drive client sends — bodies, bearer headers, paging, error mapping — without a Google account; **work-testing** runs the real backup worker, its `@EntryPoint` and its schedule on the JVM. Both test-only |
| Release performance | `androidx.baselineprofile` plugin and `benchmark-macro-junit4` (**1.5.0**), `uiautomator` (**2.4.0**), `profileinstaller` (**1.4.1**) | Since #146, for §5: the `:baselineprofile` module generates the Baseline Profile and measures cold startup and scrolling with Macrobenchmark, driving the app from outside its process with UI Automator. 1.5.0 is the first stable line for AGP 9. Those two are test-module only and run on the phone, never in CI (`docs/testing.md`). **profileinstaller** ships in the app, because it is what puts the profile in place on a sideloaded install, which is every install of this one |
| CI | GitHub Actions | build + tests + lint on every PR |

**minSdk 26** (Android 8.0) — gives `java.time` without desugaring and covers the vast
majority of devices in use. **targetSdk / compileSdk:** latest stable.

**Pinned versions.** Both are pins with a reason, not staleness to tidy up:

- **detekt** is on the `dev.detekt` 2.x line rather than the older `io.gitlab.arturbosch.detekt`
  1.23.x, because 1.23.x embeds an IntelliJ core that cannot parse a JDK 25 version string and
  crashes on the current toolchain.
- **Vico is pinned to 3.2.3, not 3.3.0.** 3.3.0 is compiled with Kotlin 2.4, and this project
  is on Kotlin 2.2.10, whose compiler reads metadata up to 2.3.0 — every Vico symbol fails to
  resolve. 3.2.3 is built with Kotlin 2.3.21 and carries the same pie and cartesian API. Moving
  to 3.3.0 means upgrading Kotlin, the Compose compiler plugin and KSP together, which is a
  toolchain decision and not something a chart library should drag behind it.

## 3. Structure

We start as a **single module** with feature packages. Split into Gradle modules
(`:core:database`, `:feature:investments`, ...) only if build times degrade or if agents
collide repeatedly. Do not modularize "just in case".

```
app/src/main/java/io/github/anderescaray/cents/
├── data/
│   ├── local/          # Room: entities, DAOs, database, migrations; DataStore preferences
│   ├── backup/         # Google Drive REST client and backup retention
│   ├── export/         # JSON and CSV backup writing and reading
│   └── repository/     # Implementations of the domain repositories, and their mappers
├── domain/
│   ├── model/          # Pure Kotlin models. Zero Room/Retrofit annotations
│   ├── repository/     # Interfaces
│   └── usecase/        # Non-trivial business logic (balances, aggregations, P&L)
├── ui/
│   ├── theme/          # Colors, typography, shapes
│   ├── components/     # Reusable composables
│   └── feature/        # One folder per screen: Screen + ViewModel + UiState
└── di/                 # Hilt modules
```

`ui/` also holds the Android-facing services that are not screens: `capture/` (the notification
listener), `notifications/` (reminders), `backup/` (the Drive worker and authorization), and
`util/`.

**Dependency rule:** `ui` → `domain` ← `data`. The `ui` layer never imports Room or OkHttp. The
`domain` layer imports nothing from Android.

## 4. Non-negotiable rules

1. **Money is stored as `Long` in the smallest unit (cents), never `Double` or
   `Float`.** Binary floating point loses precision, and in a finance app that means
   books that don't balance. Formatting to text happens only in the UI layer, using
   `NumberFormat` and the device `Locale`.
2. **Every amount carries its currency** (ISO-4217 code, e.g. `"EUR"`). Never assume
   euros implicitly.
3. **Dates and times use `java.time`** (`LocalDate`, `Instant`, `ZoneId`). Never
   `java.util.Date` or `Calendar`. Persist as epoch millis or ISO-8601 text.
4. **No work on the main thread.** Queries, computation and networking run in coroutines
   on the appropriate dispatcher.
5. **Everything leaving Room is a `Flow`**, so the UI updates itself. No manual reloads
   after a write.
6. **No secrets in the repository.** API keys live in `local.properties` (git-ignored)
   and are exposed via `buildConfigField`. If a key leaks, rotate it.
7. **Deleting user data always asks for confirmation** and, where reasonable, is
   undoable (soft delete or a snackbar with "Undo").
8. **Deactivating or deleting a recurring rule never deletes the transactions it already
   generated.** History is immutable: if it disappears, every chart for previous months
   changes. "Delete a subscription" means stop generating from today onward, not rewrite
   the past. No `ON DELETE CASCADE` from `RecurringRule` to `Transaction`.
9. **Every Room schema change ships with its migration** and a migration test. Never
   `fallbackToDestructiveMigration` outside early development.
10. **Language:** everything in the repository is in **English** — code, identifiers,
    commits, documentation and comments. User-facing strings always live in
    `strings.xml`; `values/` holds English, `values-es/` holds the Spanish translation.
    Never hardcode display text.
11. **No new libraries without justification.** Every added dependency is recorded in the
    table in section 2.
12. **The UI is the film's: the ring, cards and chips — not iOS, not Material.** White cards
    with a soft shadow on a pale canvas, one clear number each, the ring of category colours,
    chips for one-tap choices, one filled button per sheet, a floating tab capsule with the add
    button in the middle, round icon buttons, Inter. The app opens on **Home** (record, file,
    the month). Material 3 is a base to build on, never a look to ship: no screen is assembled
    from default Material widgets, and there is no dynamic colour. The specification is
    [`docs/decisions/0003-the-ring.md`](docs/decisions/0003-the-ring.md), accepted 2026-10-01
    with the amendments at its top — read it before writing any composable. ADR 0001 still
    holds for what stays native Android; ADR 0002 (the bubbles) is superseded.

## 5. Performance (the "it must feel smooth" goal)

Not something fixed at the end; watched from day one.

- `LazyColumn` / `LazyRow` always with a stable `key` per item.
- `UiState` objects are immutable `data class`es built from stable types, so Compose can
  skip recompositions. Use immutable collections (`kotlinx.collections.immutable`) where
  needed.
- Derived values via `derivedStateOf` or precomputed in the ViewModel, never in the body
  of a composable.
- Chart aggregations run in SQL (`GROUP BY`) wherever possible, not by iterating
  thousands of rows in Kotlin.
- Room indices on every column we filter by (`accountId`, `categoryId`, `date`).
- Before shipping: **R8 enabled**, a **Baseline Profile** generated, and startup and
  scrolling measured with **Macrobenchmark**. Measure on a physical device in a `release`
  build; emulators and `debug` builds lie about performance.

## 6. Commands

**Toolchain paths.** Gradle needs a JDK, and the one that works here is the JBR bundled
with Android Studio. From a Bash shell neither it, `adb` nor `gh` are on `PATH`, so a
session that shells out sets them itself:

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
export PATH="$JAVA_HOME/bin:$PATH:/c/Users/ander/AppData/Local/Android/Sdk/platform-tools:/c/Program Files/GitHub CLI"
```

```bash
./gradlew assembleDebug               # build
./gradlew installDebug                # install on the connected device
./gradlew testDebugUnitTest           # unit tests
./gradlew connectedDebugAndroidTest   # instrumented tests (needs a device/emulator)
./gradlew detekt spotlessCheck        # code quality
./gradlew spotlessApply               # auto-format
adb devices                           # check the phone is connected
```

A full local run of what CI checks, worth doing before opening a PR:

```bash
./gradlew spotlessApply                                            # fix formatting first
./gradlew spotlessCheck detekt testDebugUnitTest assembleDebug      # then the gates
```

## 7. Git and agent workflow

- `main` always builds and passes tests. Never commit directly to `main`.
- Branches: `feat/<thing>`, `fix/<thing>`, `chore/<thing>`, `docs/<thing>`.
- [Conventional Commits](https://www.conventionalcommits.org):
  `feat(transactions): add category picker`.
- One PR per branch, reviewed with `/code-review` before merging, and **squash merge** so
  the history of `main` stays readable. If the review cannot run, review the diff yourself and
  say in the PR that the review was not independent (D6).
- **Start every branch from `origin/main`**, never from an unmerged branch. `main`
  squash-merges, so a stacked branch conflicts on every hunk once its parent lands.
- **Before stopping, rewrite [`PLAN.md`](PLAN.md) §2–§4**: what got merged, what is next, and
  what waits on the owner. A session that ends without this leaves the next one guessing what is
  unfinished and what is broken.
- **Parallel work:** each agent on its own branch and its own `git worktree` (separate
  directories), so they never fight over files or the Gradle build directory.
- **When to parallelize:** only when tasks touch disjoint files. The database schema, the
  design system (`ui/theme`) and `libs.versions.toml` are shared bottlenecks: they are
  done **first and serially**. Parallelizing before those foundations exist creates more
  conflicts than it saves.
- **How parallel work runs (D24):** through **Orca**, which creates one worktree per task from
  `origin/main`. Devin, Cursor and Gemini implement; they do not load this file, so
  [`AGENTS.md`](AGENTS.md) carries its rules to them, with a territory per task and the
  global locks above. **Claude Code splits the work, writes each task prompt, and reviews each
  PR** — first that it stayed inside its territory, then `/code-review high`. At most three
  implementers run at once, and **which tasks make a wave is the owner's decision**: propose one,
  with each task's territory, and do not start one on your own.
- **Claude Code is the only agent that edits the documentation** — every Markdown file in the
  repository — in one docs branch after the merges. Two branches editing the same paragraph has
  already lost a decision once. What a feature branch learned goes in its PR description until
  then.

## 8. Documentation

Kept the way Google's documentation guide asks: **a small set of fresh, accurate documents is
better than a large one in disrepair.** Every fact has one home, and every other mention links
to it.

| File | Answers | Kind |
|---|---|---|
| [`README.md`](README.md) | What Cents is and does, and how to build it | Living |
| `CLAUDE.md`, [`AGENTS.md`](AGENTS.md) | The rules and commands for agents; `AGENTS.md` carries them to the non-Claude ones | Living |
| [`docs/PRD.md`](docs/PRD.md) | What the product is for, its scope and its roadmap | Living |
| [`PLAN.md`](PLAN.md) | Where the project stands, what is next, what waits on the owner | Living, rewritten each session |
| [`docs/next-improvements.md`](docs/next-improvements.md) | The backlog: confirmed requests, their details, the build order | Living |
| [`docs/known-limitations.md`](docs/known-limitations.md) | What is deliberately unfinished, so it is not taken for a bug | Living |
| [`docs/testing.md`](docs/testing.md) | How tests are written and run here, and the traps already met | Living |
| [`docs/setup.md`](docs/setup.md) | A machine, a phone and the Google Cloud client, from zero | Living |
| [`docs/device-check.md`](docs/device-check.md) | What to check by hand on a phone | Living |
| [`DECISIONS.md`](DECISIONS.md) | Questions only the owner can settle, and the answers | Record |
| [`FEEDBACK.md`](FEEDBACK.md) | What the owner found on the phone, and what was done | Record |
| [`docs/decisions/`](docs/decisions/) | Architecture decisions (ADRs) | Record |

**The rules:**

1. **A living document says what is true now.** When something stops being true, rewrite or
   delete it. No strikethrough, no *(fixed)*, no paragraph about how it used to be.
2. **A record is append-only.** A decision is never deleted or rewritten: a superseded one says
   what replaced it, as ADR 0002 points at ADR 0003. Only an entry's status moves (*Open* to
   *Answered*, *Open* to *Fixed*).
3. **History lives in git.** Commit messages and PR descriptions say what changed and why;
   documents do not retell it. Name the PR (`#112`) when a reader needs the trail.
4. **One home per fact.** Link to it instead of copying it, and when two documents disagree, fix
   the one that is not the home. This file holds rules and commands only, because every session
   loads it.
5. **Lead with the answer.** Short sentences, lists and tables, the conclusion before the
   reasons. A section that only ever grows gets pruned or split.
6. **Absolute dates** (2026-10-07), never *today*, *yesterday* or *now*.
7. **Code points at files, not at sections.** A comment that needs a document names the file
   (`docs/known-limitations.md`), never a section number that can move.
8. **A session leaves the documents true.** Before stopping, rewrite `PLAN.md` §2–§4 and fix
   whatever the session's work made stale, in a docs branch (§7).

**Every session starts the same way** ([`PLAN.md`](PLAN.md) §1): `FEEDBACK.md` first, where
anything under *Open* outranks the planned work; then `DECISIONS.md`, where an answer given
since the last session is an instruction; then `PLAN.md` and the backlog.

## 9. Things not to do

- Do not connect to bank APIs or ask for banking credentials. See the auto-capture
  section in the PRD: it reads notifications, it never touches the bank.
- Do not add analytics, crash reporting with personal data, or telemetry. This is a
  private app holding financial data.
- Do not invent a price. A price is one the owner typed, shown with its date; without one,
  the app says *no price yet*.
- Do not implement features from later roadmap phases without agreeing on it first.
