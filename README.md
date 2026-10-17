# Cents

A local-first personal finance app for Android, for one person. It records income and expenses
in categories that person defines, keeps several accounts with transfers between them, records
subscriptions on its own, answers where the money went, and tracks an investment portfolio and
net worth.

Everything stays on the phone: no backend, no user accounts, no analytics. The one thing that
leaves it is an optional daily backup, to a hidden folder in the owner's own Google Drive.

**Status:** a private project, in daily use. PRD phases 1 to 5 are built. Where it stands today,
and what comes next, is in [`PLAN.md`](PLAN.md).

## What it does

- **Record a movement in a few taps.** The amount goes in on a keypad, and the category and the
  account are picked from a list. The last account used is the default. Nothing is seeded:
  every category and account is one the owner made.
- **Accounts** with balances computed from their movements, never stored. A balance can be set
  to what the bank shows, either as a correction from today or as a new opening balance.
  Deleting an account asks what becomes of its movements.
- **Transfers** between accounts, with an optional fee. They count in every balance and in no
  income or expense figure.
- **Home** records, files what was saved without a category, and leads with what is left or
  overspent this month.
- **Movements**, grouped by day. Search ignores accents (*cafe* finds *Café*). Filters cover
  dates, account, category, kind, amount, weekday and group.
- **Reports:**
  - where the money went or came from;
  - income against expenses, or what was saved, by month, quarter or year;
  - the balance over time;
  - spending by weekday;
  - this period against the last or the same one last year;
  - one category over twelve periods;
  - budgets, amber at 80 % and red over 100 %.

  Every chart opens the movements behind it, and the cards can be reordered and hidden.
- **Subscriptions** that record themselves, with what they cost per month and per year.
  - Variable bills are answered when they arrive.
  - Reminders come before a charge.
  - An occurrence can be postponed by up to 14 days.
  - The screen shows what is still committed this month.
- **Groups**: what an occasion (a trip, a move, a job) cost or earned, across categories.
- **Investments:**
  - holdings in stocks, ETFs, index funds and crypto;
  - purchases that spend the account's cash;
  - an average cost that survives a partial sale;
  - prices typed per unit or as a total;
  - closed positions.

  Wallet and the top of Home show cash and bank apart from what is invested (the brokers' cash
  and holdings), and the net worth they come to, each switched on or off in Settings.
- **Capture:** payments read from the notifications of the apps the owner chooses. Each app has
  its own words for expense and income. Captures land uncategorized, and *Try a notification*
  shows what a given text would record. A payment a subscription already records is recognised
  rather than counted twice, with Undo on the notification.
- **Your data:** JSON or CSV export, restore from a file or from Drive, and a daily Drive
  backup.
- English and Spanish, chosen in Settings apart from the phone's language, with amounts and dates
  kept in the phone's style; TalkBack; and the largest system font.

## How it is built

| Layer | Choice |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose on Foundation, with its own design system ([ADR 0003](docs/decisions/0003-the-ring.md)) |
| Architecture | MVVM, `data` / `domain` / `ui` layers, one immutable `UiState` per screen |
| Persistence | Room (KSP) with versioned, tested migrations; DataStore for settings |
| DI | Hilt |
| Async | Coroutines and Flow |
| Charts | Vico |
| Background work | WorkManager: reminders and the daily backup |
| Networking | OkHttp and kotlinx.serialization, for Google Drive only |
| Quality | detekt, ktlint (Spotless), GitHub Actions on every PR |
| Tests | JUnit, MockK, Turbine, Robolectric, Compose UI Test, all on the JVM |

**minSdk 26 · targetSdk latest stable.** Single Gradle module with feature packages:

```
ui/       Compose screens, a ViewModel and an immutable UiState each
  ↓
domain/   Pure Kotlin models, repository interfaces, use cases. No Android imports
  ↑
data/     Room, the Drive backup client, export, and the repository implementations
```

## Decisions worth knowing

- **Money is a `Long` of cents**, never a float, and every amount carries its currency.
- **Balances are computed, not stored**, so editing an old movement cannot leave one out of
  date.
- **A transfer is not an expense.** Counting either leg would inflate every statistic.
- **Stopping a subscription never deletes what it already recorded**, or past months' charts
  would change.
- **Due charges are written when the app opens**, idempotently, rather than by a scheduled job
  that Android may not run on time.
- **One currency and no conversion.** Two currencies show no total rather than an invented one.
- **Prices are typed.** No free source carries these European listings, and paying for one was
  declined (D25).

The full rules are in [`CLAUDE.md`](CLAUDE.md) §4, and the reasons in
[`DECISIONS.md`](DECISIONS.md) and [`docs/decisions/`](docs/decisions/).

## Building

```bash
./gradlew assembleDebug        # build
./gradlew installDebug         # install on a connected device
./gradlew testDebugUnitTest    # unit tests
./gradlew detekt spotlessCheck # static analysis and formatting
```

Needs Android Studio, whose bundled JDK builds the project. No API key is needed. Setting up a
machine, a phone and the Drive client from zero is [`docs/setup.md`](docs/setup.md).

## Documentation

Start with [`PLAN.md`](PLAN.md) (where things stand) and [`docs/PRD.md`](docs/PRD.md) (what the
product is). Every document, what it is for, and the rules they all follow are listed in
[`CLAUDE.md` §8](CLAUDE.md#8-documentation).

## License

Not decided. The repository is private. The bundled Inter typeface is under the SIL Open Font
License, in [`docs/licenses/`](docs/licenses/).
