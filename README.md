# Cents

A local-first personal finance tracker for Android. Income and expenses with
user-defined categories, multiple accounts with transfers, recurring subscriptions with
reminders, spending analytics, and an investment portfolio with net worth.

Built with Kotlin and Jetpack Compose. All data stays on the device — no backend, no user
accounts, no telemetry. The one thing that leaves it is an optional daily backup, to a hidden
folder in the owner's own Google Drive.

> **Status (2026-09-25): Phases 1–3 built; Phase 4 built with prices typed by hand; Phases 5–6
> not started.** Most of Phase 2 onward has still not been walked on a real phone. Since the
> paragraph below was written, the app has gained transfers, an investment portfolio with closed
> positions and net worth, recurring subscriptions that record themselves, ask for variable
> bills, remind you before a charge and say what is still committed this month, JSON/CSV export
> and restore, a daily automatic backup to the owner's own Google Drive, and a biometric lock.
> Budgets, the first piece of Phase 5, landed the same day, and on 2026-09-25 groups: what a
> trip or a job cost or earned across every category it touched, and an inbox that files what
> was saved without a category in one tap. 856 tests.
>
> **Earlier status: Phase 1 of 6 complete; Phase 2 built and awaiting its walk on a real phone.**
> Recording money works end to end: accounts, categories, transactions and balances that trace
> back to the movements behind them; a list grouped by day that can be searched and filtered by
> date range, account, category, kind and amount; and deletion that asks the right question —
> including what should become of the movements inside an account you are removing. Nothing is
> seeded: every row in the app is one the owner typed. **Analytics is built:** a Reports tab
> with six views — where the money went or came from, income against expenses, balance over
> time, spending by weekday, this period against the last or against the same period last year,
> and one category followed over twelve periods — all under one period control and one account
> picker, and every chart drills down to the movements behind it. The app opens on a **canvas of
> bubbles**, one per section ([ADR 0002](docs/decisions/0002-bubble-launcher.md)). 369 tests run
> on the JVM, so CI covers the database and the UI without a device. The roadmap below tracks
> progress honestly.

---

## Why

Bank apps show you what each individual account did. They do not tell you where your money
actually goes across all of them, what your fixed monthly commitments cost you, or how your
investments are doing — and they cannot, because they only see themselves. Cents is a single
place that answers those questions, owned entirely by the person using it.

## Tech stack

| Layer | Choice |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose, Material 3 as a base, styled to iOS conventions |
| Architecture | MVVM with `data` / `domain` / `ui` layers, unidirectional data flow |
| Persistence | Room (KSP), versioned migrations |
| DI | Hilt |
| Async | Coroutines + Flow |
| Charts | Vico |
| Networking | OkHttp + kotlinx.serialization, for the Drive backup only |
| Background work | WorkManager |
| Build | Gradle KTS with a version catalog |
| Quality | detekt, ktlint via Spotless, GitHub Actions CI |
| Tests | JUnit, MockK, Turbine, in-memory Room on the JVM via Robolectric, Compose UI Test |

**minSdk 26 · targetSdk latest stable**

The app is Material 3 underneath and iOS on the surface: inset grouped lists, large
collapsing titles, a bottom tab bar, iOS-style switches, alerts and action sheets,
horizontal push/pop transitions and the Inter typeface. No screen is assembled from default
Material widgets and there is no dynamic colour. The reasoning is in
[`docs/decisions/0001-ios-flavored-ui.md`](docs/decisions/0001-ios-flavored-ui.md), and the one
deliberate departure — the app opens on a canvas of draggable bubbles rather than on a tab, with
the tab bar kept for moving between sections once you are inside one — is in
[`docs/decisions/0002-bubble-launcher.md`](docs/decisions/0002-bubble-launcher.md).

## Architecture

```
ui/       Compose screens, one ViewModel and immutable UiState per screen
  ↓
domain/   Pure Kotlin models, repository interfaces, use cases. No Android imports
  ↑
data/     Room (local), Retrofit (remote), repository implementations
```

The `ui` layer never imports Room or Retrofit; the `domain` layer never imports Android.
Single Gradle module with feature packages, split into modules only if build times or
parallel work demand it.

## Engineering decisions worth calling out

- **Money is a `Long` in cents, never a floating point type.** Binary floats lose precision,
  and in a finance app that means books that do not balance. Formatting happens only at the
  UI edge, using the device locale.
- **The app ships with nothing in it.** No default categories, no starter account. A fresh
  install is empty, and every row in the app is one its owner typed — a suggested list is
  still software deciding what your money is spent on.
- **One currency, the device's, and no picker.** `Money` still carries an ISO-4217 code and
  still refuses to add euros to dollars, but that is a guard rail against a wrong number
  rather than multi-currency support: there is no conversion step, so where two currencies
  somehow coexist the app shows *no* total instead of an approximate one.
- **Account balances are computed, not stored.** A stored balance drifts out of sync the
  moment an old transaction is edited.
- **A transfer is not an expense.** It is a linked outflow/inflow pair excluded from every
  income and expense report — otherwise every statistic in the app is inflated.
- **Deleting an account asks what to keep.** A movement cannot outlive its account, so
  deleting one that holds history is a question rather than an operation: move the movements
  to another account, or delete them too, behind a confirmation that names how many go.
- **Deleting a subscription never deletes its history.** It deactivates the rule from today
  forward. Deleting past charges would silently change every chart for previous months.
- **Recurring transactions are generated by catch-up on app open**, not by a scheduled job.
  Android does not guarantee background work runs on time, so a job-based design silently
  loses whole months. Generation is idempotent on `ruleId` + date and fills gaps.
- **Performance is measured, not assumed.** Baseline Profiles and Macrobenchmark on a
  physical device in release builds; chart aggregation pushed into SQL rather than iterated
  in Kotlin.

## Roadmap

| Phase | Scope | Status |
|---|---|---|
| 0 | Project skeleton, CI, theme, navigation | **Done** |
| 1 | Room schema, accounts, categories, transactions, balances, search and filters | **Done** |
| 2 | Analytics: by category, income vs expenses, balance over time, by weekday, period comparison, category trend | **Built** · closes after the device walk |
| 3 | Transfers, recurring rules and subscriptions, export/import and automatic backup to Drive, biometric lock | **Built** |
| 4 | Investment portfolio with prices and P&L | **Built** · prices are typed by hand: no free provider carries these European listings, and paying for one was declined |
| 5 | Uncategorized inbox, budgets, notification-based auto-capture (experimental) | **Begun** · budgets built; the inbox and capture not started |
| 6 | Widget, animations, Baseline Profile, accessibility | Planned |

## Documentation

- [`docs/PRD.md`](docs/PRD.md) — product spec: features, data model, feasibility analysis,
  roadmap
- [`CLAUDE.md`](CLAUDE.md) — engineering guidelines: stack, architecture, non-negotiable
  rules, git workflow
- [`AGENTS.md`](AGENTS.md) — the same rules for the other coding agents, and how parallel work
  in separate worktrees is kept from colliding
- [`PLAN.md`](PLAN.md) — environment setup, the development process, and **Part C: where
  the project stands right now**, which is where a new session starts
- [`DECISIONS.md`](DECISIONS.md) — questions only the owner can answer, and what was decided
- [`FEEDBACK.md`](FEEDBACK.md) — the other direction: what the owner found while using the app
  on a real phone, which is the one thing the test suite cannot catch
- [`docs/device-check.md`](docs/device-check.md) — the walk that finds it: what to tap, and what
  a failure would look like
- [`docs/decisions/`](docs/decisions/) — architecture decision records: what was decided,
  and why, written to be read in a year

## Building

```bash
./gradlew assembleDebug        # build
./gradlew installDebug         # install on a connected device
./gradlew testDebugUnitTest    # unit tests
./gradlew detekt spotlessCheck # static analysis and formatting
```

Requires Android Studio, which bundles the Android SDK and the JetBrains Runtime used to
build (currently JDK 25). Do not install a separate JDK. No API key is needed: prices are
typed, and the Drive backup's Android OAuth client is matched by package name and signing
certificate, so nothing secret goes in the repo or in `local.properties`.

## License

Not yet decided. The repository is private; the bundled Inter typeface is under the SIL Open
Font License, in [`docs/licenses/`](docs/licenses/).
