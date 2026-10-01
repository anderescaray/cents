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
  network calls are to **Google Drive**, for the automatic backup the owner turns on (D22), into
  the app's own hidden folder. There is no market data API: prices are typed, for good (D25).
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
| Networking | OkHttp (**5.3.2**) + kotlinx.serialization | **Google Drive only**, since `feat/backup` (#69): multipart upload, list, download and delete against the Drive REST v3 surface, which is small enough that the Drive Java SDK and its Google HTTP client and Guava stack would be most of the app's size for four calls. Retrofit was planned for market data and never added: D25 closed that door. `kotlinx-serialization-json` is also declared directly: the type-safe navigation routes have always used it through navigation-compose, and the export writes a backup file with it — which is exactly the thing not to hand-roll an escaper for |
| Drive authorization | `play-services-auth` (**22.0.0**) | The supported `AuthorizationClient`, for the single `drive.appdata` grant and a short-lived access token per operation. Nothing is stored and no client secret exists: an Android OAuth client is matched by package name and signing certificate (D22, rule 6) |
| Preferences | DataStore (Preferences) | Modern replacement for SharedPreferences. In use for the app lock, capture's settings (which apps, their accounts, each app's words; D29) and the order of the Reports cards |
| App lock | `androidx.biometric` (**1.1.0**, the stable line) | The `BiometricPrompt` PRD §4.7 names: a fingerprint or the device PIN, decided by the system, with the app never seeing a credential (rule 6). Pinned to stable rather than the 1.2.0 and 1.4.0 alphas; 1.1.0 is where `setAllowedAuthenticators` arrived, which is what lets the PIN stand in |
| Background work | WorkManager (**2.11.0**) | **In use since `feat/subscription-reminders`**: one daily job that asks what reminders land today, and since #69 a second, the daily Drive backup, scheduled only while its switch is on. Nothing is scheduled per reminder — a queue of alarms would be a second copy of when the rent is, and the two would drift. Reached without `androidx.hilt:hilt-work`: a plain `CoroutineWorker` asks the Hilt graph through an `@EntryPoint`, which is two fewer artifacts and no `Configuration.Provider` (rule 11) |
| Build | Gradle KTS + version catalog (`gradle/libs.versions.toml`) | Single source of versions |
| Code quality | detekt + ktlint (via Spotless) | Enforced in CI |
| Tests | JUnit + MockK + Turbine + in-memory Room (on the JVM via Robolectric) + Compose UI Test, plus `mockwebserver3` and `work-testing` | Robolectric lets DAO and migration tests run in CI, which has no device. **MockWebServer** checks the real HTTP the Drive client sends — bodies, bearer headers, paging, error mapping — without a Google account; **work-testing** runs the real backup worker, its `@EntryPoint` and its schedule on the JVM. Both test-only |
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
- **How parallel work runs (D24):** through **Orca**, which creates one worktree per task from
  `origin/main`. Devin, Cursor and Gemini implement; they do not load this file, so
  [`AGENTS.md`](AGENTS.md) carries its rules to them, with a territory per task and the
  global locks above. **Claude Code splits the work, writes each task prompt, and reviews each
  PR** — first that it stayed inside its territory, then `/code-review high`. It is also the
  **only agent that edits the shared documents** (`CLAUDE.md`, `PLAN.md`, `README.md`,
  `DECISIONS.md`, `FEEDBACK.md`, `docs/device-check.md`), in one docs branch after the merges.
  Two branches editing the same paragraph has already lost a decision once.

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
change follows (rule 9).

**What repeats now records itself.** `feat/recurring-rules` built the biggest unbuilt thing in
the PRD (§4.4): rent, Netflix and the salary as **templates**, listed under Settings ›
Subscriptions with **what they come to per month and per year** — the figure that makes the
screen worth having, since a plain list adds nothing to the movements already recorded. Each
launch writes whatever has fallen due, **once**: every occurrence is counted from the start date
rather than from the one before it, so a monthly rule on the 31st clamps in February and returns
in March, and each rule's watermark is written in the same database transaction as the movements
it describes, so a run killed halfway leaves nothing behind. A rule entered late writes the
charges it missed. Cancelling a subscription **stops** it — rule 8, as an operation — and only a
stopped one can then be deleted, with its movements left standing. **Room v5.**
**And the bills it will not guess have somewhere to be answered.** D23 was settled on 2026-09-17:
a subscription marked *ask me the amount* — the electricity — waits on the Subscriptions screen
instead of asserting a figure nobody has seen, and several are answered in one sitting. Each is
recorded **on the day it fell due**, not the day it was answered, so a bill opened late still
counts in its own month; a row left blank is left alone, and *there was no bill that month* is a
separate gesture, because it is not a zero. Only the **oldest** waiting occurrence of each rule is
offered at a time — the watermark is a single date, so answering out of order would swallow the
periods before it. **And a subscription can tell you before the money goes.** D23's other half:
any number of reminders per rule, each a number of days the owner chooses. A **fixed** one is
announced — *Netflix, 12,99 €, in 3 days* — and only beforehand, because afterwards the movement
is already in the list; a **variable** one can also be chased *after* the date, and tapping it
lands on the screen that takes the figure. Nothing is scheduled in advance: **one daily job asks
what lands today** and computes the answer from the rules, so editing a reminder, stopping a rule,
answering a bill, a reboot or a clock change need nothing cancelled. A bill already answered stops
being chased. **Room v6**, and the one permission this app asks for — requested from the editor at
the moment a reminder is added, and the editor says so when the system will show nothing.
**And the screen says what is still to come** (#63): what is **committed this month**, meaning
every expense still due before the month ends plus the variable bills still waiting at their
estimate, and **what share of recurring income goes to fixed charges**, shown as unavailable
with a reason rather than as 0 % when there is no income to divide by.

**Money can now move between accounts.** `feat/transfers` brought Phase 3's first piece forward,
because the investments section needs it: a transfer is **two ordinary rows sharing one
`transferId`** — no transfers table, so the amount that left and the amount that arrived are the
same facts the balances are made of — with an optional fee that is the difference between them.
Every balance counts a transfer and **no income-or-expense report does**, which is the one thing
this feature must not get wrong: counting a leg as spending inflates every statistic in the app,
and the same money arriving on the other side inflates income too. It is reached as a third
segment on the entry form, it reads on the list as *Transfer to Savings*, and it took **Room v3**.
**And buying now spends the cash.** A purchase takes its cost out of the account the holding
lives in and a sale puts it back, so a broker account's **balance is the money still in it** and
the portfolio is what the rest became — two numbers that no longer overlap. The movement carries
a `tradeId`, obeys the same exclusion a transfer leg does, and reads as *Bought NVD.DE*. **Room
v4.** A deleted transfer can be **undone** from the list's banner, both legs or neither (#65). A
fully sold holding stays off the portfolio (D17), and what it made is listed under **Closed
positions** at the foot of that screen (#64), outside every total.

**And the data can leave.** Settings writes the whole app to a file the owner keeps — **JSON**
with every table and the schema version that wrote it, or **CSV** of the movements for a
spreadsheet — through the system picker, so the app asks for **no storage permission** and
nothing goes anywhere near a third party. **And it can come back.** A backup file is read back in,
**replacing everything** (D21) behind a confirmation that names what arrives and what goes — read
first, so those numbers are checkable. Older files are read, newer ones refused, and a damaged one
is turned away whole rather than half written. **And it backs itself up** (#69, D22): once a day,
to the app's own hidden folder on the owner's Google Drive, keeping the newest copy of each of
the last 7 days, 4 weeks and 6 months and skipping a copy identical to the last. A token Drive
refuses is renewed silently before anything asks the owner to reconnect. Since #72 a backup
carries the **subscriptions and their reminders** too, with the watermark that stops a restored
rule writing its past charges twice, and a restore is no longer refused on a phone that has
subscriptions. The restore alert ends by saying to export first (#74).
**And the three numbers add up.** D19 was answered on 2026-09-17: the Accounts tab shows what is
**in accounts**, what is **invested**, and the **net worth** those come to — the parts as well as
the sum, so it can be read with the portfolio and without it. It is only a legitimate sum because
buying spends the account's cash; an unpriced holding is left out of it and counted aloud, the
same rule the portfolio already applies one screen down.
**And it can be locked.** Cents asks who you are
on a cold start and after more than half a minute away — a fingerprint or the device PIN, through
`BiometricPrompt`, so the app never sees either (D16: no SQLCipher under it). While the lock is
shut the app is **not composed at all** rather than covered, and nothing is drawn until the
setting has been read.
**And prices are typed in one place.** D20 settled
that no free provider carries this portfolio's listings — and that converting from a US listing
cannot rescue a UCITS ETF, which has no US listing to convert from — so typing is how prices get
in rather than a fallback — and **D25** (2026-09-21) made that permanent: the owner will not pay
for a provider, so `feat/price-provider` is closed. Every open holding is priced from one screen, under one date, with a
row left empty left alone. **A purchase can also be recorded as 500 € of something** rather than
as units × price, which is how the platform sells it: units are still what is stored, because
that is what a sale reduces and a price multiplies, and the footer says how many it came to
before the save.
**And a month can have limits** (#80, #81, **Room v7**): Settings › Budgets gives an expense
category a monthly limit, and measures it against what went out plus what its subscriptions will
still take before the month ends, counted from day one as PRD §4.4 asks. Amber at 80 %, red above
100 %, no notification (D26). **And the bubbles are gone** (`feat/ui-rework`, 2026-10-01, ADR
0003): the app opens on Home — record a movement in a few taps, file what capture wrote, see the
month — under a floating capsule of Home · Movements · **+** · Reports · Wallet. Budgets moved to
Reports, whose cards the owner can reorder and hide.
**And an occasion has a total** (#86, #87, **Room v8**, D27): Settings › Groups gathers movements
from any category under one occasion — a trip, a move, a freelance job — and says what it
**cost** or **earned** in words, with a ring of where it went and the way into its movements. A
movement belongs to at most one group, so totals never overlap, and joining one changes no report
and no budget: the trip's dinner is still a restaurant expense in its own month.
**And what was saved in a hurry gets sorted later** (#90): a movement saved without a category
waits under **To categorize** on the movement list, and one tap on a category chip files it —
the inbox PRD §4.6 asks for before any notification capture feeds it.
**And it can fill itself** (#105, #106, and Settings › Capture): the notifications of the apps
the owner chooses are read, the owner's editable words decide expense or income, the amount is
the one in the text, and each match lands uncategorized in the app's account, the same payment
within a minute once (D29). Nothing is read until the owner grants *Notification access*.
**And a charge or a salary can wait a few days** (#97, **Room v9**, D28): a rule's next
occurrence, or a variable bill or salary already waiting, is moved 1–14 days, never as far as the
next one, recorded on the new day and announced that morning, while the rule's own schedule stays
put. The same day brought the **accessibility pass** (#96): TalkBack roles and headings, 48 dp
targets, and text that grows with the system font. It also stopped the *Income* and *Expenses*
filters listing transfer legs (#95).
919 tests run on the JVM, so CI covers the database and the UI without a device.

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

**Several agents run at once now, through Orca (D24, 2026-09-21)** — with the roles set in §7,
at most three implementers, and never two branches writing the same file. The first wave, #63
to #65, landed with no file shared between them. Which tasks go into a wave is still the owner's
decision: propose one, with each task's territory, and do not start one on your own.

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
