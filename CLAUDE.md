# CLAUDE.md

Operating context for Claude Code. This file is loaded automatically in every session,
so it holds **rules and commands**, not the product description.

> **What we are building → [`docs/PRD.md`](docs/PRD.md)** (features, scope, roadmap and
> feasibility analysis). If this file and the PRD disagree about *what* to build, the PRD
> wins; about *how* to build it, this file wins.

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
  network calls are to the market data API.
- **Users:** exactly one (the owner). No multi-user, no sharing.

## 2. Stack

| Layer | Choice | Why |
|---|---|---|
| Language | Kotlin | Official Android standard |
| UI | Jetpack Compose + Material 3 as a **base**, styled to look like iOS | Compose gives layout, insets and accessibility; the appearance follows Apple's HIG — see [`docs/decisions/0001-ios-flavored-ui.md`](docs/decisions/0001-ios-flavored-ui.md) |
| Architecture | MVVM with `data` / `domain` / `ui` layers | Testable, with clear boundaries for parallel work |
| State | `StateFlow` + unidirectional data flow | One immutable `UiState` per screen |
| Database | Room (with KSP) | Typed SQLite, returns `Flow`, versioned migrations |
| DI | Hilt | De facto standard, integrates with Compose and WorkManager |
| Navigation | Navigation Compose (type-safe routes) | |
| Charts | Vico | Compose-native charting library, actively maintained. Cartesian charts *and* a pie/donut chart, so the two Phase 2 charts need no second library |
| Networking | Retrofit + OkHttp + kotlinx.serialization | Market data only |
| Preferences | DataStore (Preferences) | Modern replacement for SharedPreferences. In use for the home canvas layout |
| Background work | WorkManager | Periodic price refresh, recurring reminders |
| Build | Gradle KTS + version catalog (`gradle/libs.versions.toml`) | Single source of versions |
| Code quality | detekt + ktlint (via Spotless) | Enforced in CI |
| Tests | JUnit + MockK + Turbine + in-memory Room (on the JVM via Robolectric) + Compose UI Test | Robolectric lets DAO and migration tests run in CI, which has no device |
| CI | GitHub Actions | build + tests + lint on every PR |

**minSdk 26** (Android 8.0) — gives `java.time` without desugaring and covers the vast
majority of devices in use. **targetSdk / compileSdk:** latest stable.

## 3. Structure

We start as a **single module** with feature packages. Split into Gradle modules
(`:core:database`, `:feature:investments`, ...) only if build times degrade or if agents
collide repeatedly. Do not modularize "just in case".

```
app/src/main/java/io/github/anderescaray/cents/
├── data/
│   ├── local/          # Room: entities, DAOs, database, migrations, type converters
│   ├── remote/         # Retrofit: price API, DTOs, mappers
│   └── repository/     # Implementations of the domain repositories
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

**Dependency rule:** `ui` → `domain` ← `data`. The `ui` layer never imports Room or
Retrofit. The `domain` layer imports nothing from Android.

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
12. **The UI looks like iOS, not like Material.** Inset grouped lists, large collapsing
    titles, a bottom tab bar, iOS-style switches and alerts, horizontal push/pop
    transitions, the iOS system palette and the Inter typeface. The app opens on a canvas
    of bubbles in front of that tab bar, which is the one deliberate departure —
    [`docs/decisions/0002-bubble-launcher.md`](docs/decisions/0002-bubble-launcher.md)
    amends the ADR below and is read with it. Material 3 is a base to
    build on, never a look to ship: no screen is assembled from default Material widgets,
    and there is no dynamic colour. The full specification, including what stays native
    Android, is [`docs/decisions/0001-ios-flavored-ui.md`](docs/decisions/0001-ios-flavored-ui.md)
    — read it before writing any composable.

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
  the history of `main` stays readable.
- **Before stopping, update [`PLAN.md` Part C](PLAN.md#part-c--where-the-project-stands):**
  what got merged, what the next branch is, and any gap left behind. A session that ends
  without this leaves the next one guessing what is unfinished versus what is broken.
- **Parallel work:** each agent on its own branch and its own `git worktree` (separate
  directories), so they never fight over files or the Gradle build directory.
- **When to parallelize:** only when tasks touch disjoint files. The database schema, the
  design system (`ui/theme`) and `libs.versions.toml` are shared bottlenecks: they are
  done **first and serially**. Parallelizing before those foundations exist creates more
  conflicts than it saves.

## 8. Current status

**Phase 1 — complete, closed 2026-08-21. Phase 2 (analytics): all six PRD §4.3 views are built
and merged, and the app now opens on a canvas of bubbles (#41) rather than on a tab.** Nothing
planned is unbuilt; what is waiting is a walk on a real phone
([`docs/device-check.md`](docs/device-check.md)), because none of Phase 2 has been seen on
one. Recording money works end to end: the Room schema, the domain and repository layers, the
iOS-flavoured design system and navigation, a transaction list grouped by day with a monthly
summary, one form that adds and edits movements (keypad, date picker, note, delete with undo),
category management, and the Accounts tab with balances computed from the movements behind them.
Categories and accounts are deleted either from their editor or by swiping the row sideways —
both reach the same confirmation, because the question lives in one place rather than one per
screen. The list is searchable and filterable by date range, account, category, kind and amount,
narrowed in SQL rather than in Kotlin.

**Nothing is seeded.** A fresh install has no accounts and no categories: every row in the
app is one the owner typed. Deleting an account that holds movements asks what should become
of them — move them elsewhere, or delete them too — rather than quietly archiving it.
The app works in **one currency**, the device's, and does not offer a choice: `Money` still
carries its ISO-4217 code and still refuses cross-currency arithmetic, but that is a guard
rail rather than a feature, and no conversion step exists (`DECISIONS.md` D2).
The **Reports tab** is the work of Phase 2: a ring that answers where the money went or where it
came from, income against expenses per month, a balance line, spending by day of the week, this
period against the one before it or against the same period last year (#42), and one category
followed over twelve periods — all under one period control and one account picker, with a
slice, a bar or a point opening the movements behind it. A new view there is an additive unit;
what it costs is spelled out in `ui/feature/reports/ReportSection.kt`.
**Phase 4 has begun early, and deliberately** (D11, 2026-09-09): the app has a fifth section,
**Investments** — holdings grouped into stocks, ETFs, index funds and crypto, the trades behind
each one, an average cost that survives a partial sale, and what it is worth at a price typed by
hand — per unit or as a total, whichever the broker is showing — and what is held can be
**corrected** when the two disagree, as a record rather than an overwrite. It brought **Room v2
and this project's first migration and migration test**, which is the pattern every later schema
change follows (rule 9). Two things it leaves to the next branches:
a purchase does not yet take money out of its account (that needs transfers, which are Phase 3),
and no price comes from the internet (D13 asks which provider, and it needs a key only the owner
can create).
441 tests run on the JVM, so CI covers the database and the UI without a device.

**Two files carry everything that passes between the owner and the agent, and both are read
at the start of every session, before any code:**

- **[`DECISIONS.md`](DECISIONS.md)** — questions only the owner can settle. Act on anything
  answered since last time, and add any new question there with a default rather than guessing
  or stalling.
- **[`FEEDBACK.md`](FEEDBACK.md)** — what the owner found on the phone. The agent cannot use
  the app, so this is the only channel for "this is broken" or "this feels wrong". **Anything
  under *Open* there outranks the next planned branch:** fix it first, then move the entry down
  to *Fixed* with the date and what was done.

The protocol for both is [`PLAN.md` C6](PLAN.md#c6-questions-waiting-on-the-owner).

**Phase 2 is the first phase that may run several agents at once — and starting that is the
owner's deliberate decision, not a default.** D5 settled how it would run and D9 answered
*three agents* on 2026-08-30, which is permission for a wave of genuinely disjoint work, not a
standing default. **On 2026-09-03 the owner chose to run the remaining views serially anyway**,
because they append a line each to the same three shared files and one PR at a time is the
rhythm they review in. Ask before opening worktrees; do not open them because the phase number
changed.

**Where to start a session and what is left to build is
[`PLAN.md` Part C](PLAN.md#part-c--where-the-project-stands).** It carries the session
checklist, the ordered list of branches still to build, and the list of known gaps —
keep it updated at the end of every session, because it is the only place that records
what is deliberately unfinished rather than broken.

Notes on pinned versions — both are pins with a reason, not staleness to tidy up:

- **detekt** is on the `dev.detekt` 2.x line rather than the older `io.gitlab.arturbosch.detekt`
  1.23.x, because 1.23.x embeds an IntelliJ core that cannot parse a JDK 25 version string and
  crashes on the current toolchain.
- **Vico is pinned to 3.2.3, not 3.3.0.** 3.3.0 is compiled with Kotlin 2.4, and this project
  is on Kotlin 2.2.10, whose compiler reads metadata up to 2.3.0 — every Vico symbol fails to
  resolve. 3.2.3 is built with Kotlin 2.3.21 and carries the same pie and cartesian API. Moving
  to 3.3.0 means upgrading Kotlin, the Compose compiler plugin and KSP together, which is a
  toolchain decision and not something a chart library should drag behind it.

## 9. Things not to do

- Do not connect to bank APIs or ask for banking credentials. See the auto-capture
  section in the PRD: it reads notifications, it never touches the bank.
- Do not add analytics, crash reporting with personal data, or telemetry. This is a
  private app holding financial data.
- Do not invent quotes or fill in fake prices when the API fails: show the last known
  price with its age, or an explicit error state.
- Do not implement features from later roadmap phases without agreeing on it first.
