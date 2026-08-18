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
| UI | Jetpack Compose + Material 3 | Declarative UI, smooth animations, Google's recommendation |
| Architecture | MVVM with `data` / `domain` / `ui` layers | Testable, with clear boundaries for parallel work |
| State | `StateFlow` + unidirectional data flow | One immutable `UiState` per screen |
| Database | Room (with KSP) | Typed SQLite, returns `Flow`, versioned migrations |
| DI | Hilt | De facto standard, integrates with Compose and WorkManager |
| Navigation | Navigation Compose (type-safe routes) | |
| Charts | Vico | Compose-native charting library, actively maintained |
| Networking | Retrofit + OkHttp + kotlinx.serialization | Market data only |
| Preferences | DataStore (Preferences) | Modern replacement for SharedPreferences |
| Background work | WorkManager | Periodic price refresh, recurring reminders |
| Build | Gradle KTS + version catalog (`gradle/libs.versions.toml`) | Single source of versions |
| Code quality | detekt + ktlint (via Spotless) | Enforced in CI |
| Tests | JUnit + MockK + Turbine + in-memory Room + Compose UI Test | |
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

> These do not exist yet: they appear once the project is generated in Android Studio
> (Phase 0).

```bash
./gradlew assembleDebug               # build
./gradlew installDebug                # install on the connected device
./gradlew testDebugUnitTest           # unit tests
./gradlew connectedDebugAndroidTest   # instrumented tests (needs a device/emulator)
./gradlew detekt spotlessCheck        # code quality
./gradlew spotlessApply               # auto-format
adb devices                           # check the phone is connected
```

## 7. Git and agent workflow

- `main` always builds and passes tests. Never commit directly to `main`.
- Branches: `feat/<thing>`, `fix/<thing>`, `chore/<thing>`, `docs/<thing>`.
- [Conventional Commits](https://www.conventionalcommits.org):
  `feat(transactions): add category picker`.
- One PR per branch, reviewed with `/code-review` before merging, and **squash merge** so
  the history of `main` stays readable.
- **Parallel work:** each agent on its own branch and its own `git worktree` (separate
  directories), so they never fight over files or the Gradle build directory.
- **When to parallelize:** only when tasks touch disjoint files. The database schema, the
  design system (`ui/theme`) and `libs.versions.toml` are shared bottlenecks: they are
  done **first and serially**. Parallelizing before those foundations exist creates more
  conflicts than it saves.

## 8. Current status

**Phase 0 — in progress.** The skeleton (Compose Empty Activity) builds and installs on a
physical device, and the build tooling is in place: Hilt + KSP, detekt, Spotless/ktlint and
CI on GitHub Actions running build + tests + lint on every PR. Next: Room entities, DAOs
and migrations with their tests, then theme, navigation and the core screens. Roadmap in
[`docs/PRD.md`](docs/PRD.md).

Note on tool versions: detekt is on the `dev.detekt` 2.x line rather than the older
`io.gitlab.arturbosch.detekt` 1.23.x, because 1.23.x embeds an IntelliJ core that cannot
parse a JDK 25 version string and crashes when run on the current toolchain.

## 9. Things not to do

- Do not connect to bank APIs or ask for banking credentials. See the auto-capture
  section in the PRD: it reads notifications, it never touches the bank.
- Do not add analytics, crash reporting with personal data, or telemetry. This is a
  private app holding financial data.
- Do not invent quotes or fill in fake prices when the API fails: show the last known
  price with its age, or an explicit error state.
- Do not implement features from later roadmap phases without agreeing on it first.
