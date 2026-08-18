# PRD — Cents (personal finance app)

**Product document.** Describes *what* we are building, at what scope and in what order.
Technical decisions about *how* live in [`../CLAUDE.md`](../CLAUDE.md).

- **Version:** 0.1 (initial draft)
- **Date:** 2026-08-18
- **Status:** Phase 0 — no code
- **Author:** Ander

---

## 1. Vision

An Android app to run my personal finances end to end: what comes in, what goes out,
where it goes, how my net worth evolves, and how my investments are doing — all in one
place and without depending on any bank's app.

**Design principles:**

1. **Local-first.** The data is mine and lives on my phone. No user accounts, no server,
   no subscription.
2. **Fast and smooth.** Logging an expense must take under 5 seconds and 3 taps. If it is
   slow I will stop using it, and then the app is worthless.
3. **Categories I define.** No fixed taxonomy imposed by the app.
4. **The numbers never lie.** Exact precision on money, and full transparency when a
   value comes from the internet and may be stale.

**Non-goals** (out of scope, at least for now): multi-user or shared expenses, direct
bank connections, iOS, a web version, publishing on Google Play.

## 2. User

A single user: me. This removes an enormous amount of design surface — no login, no
permissions model, no cross-device sync, no third-party data compliance. If I ever want
it on another phone, that is solved with a backup export/import, not with a server.

## 3. Data model (conceptual)

Understanding these seven entities is understanding the whole app.

| Entity | What it is | Main fields |
|---|---|---|
| **Account** | A container of money: bank, cash, card, broker | name, type, currency, opening balance, color/icon, archived |
| **Category** | A user-created label for classification | name, kind (income/expense), color, icon, parent category (optional) |
| **Transaction** | One income or expense in an account | amount (cents), currency, kind, account, category, date, note, attachment |
| **Transfer** | Money moved between two of my own accounts | source account, target account, amount, date, fee |
| **RecurringRule** | A template for a repeating movement: subscription, salary, rent | amount, category, account, frequency, day, next date, end date, mode (auto/confirm), active |
| **Holding** | Something I hold: stock, ETF, fund | ticker/ISIN, name, quantity, average cost, currency, account/broker |
| **PriceQuote** | Last known price of an instrument, cached | symbol, price, currency, timestamp, source |

**Key decisions:**

- A **transfer is not an expense.** Moving 500 € from checking to savings is not spending
  500 €. Internally it is modelled as a paired outflow and inflow linked by the same
  `transferId`, and it is **excluded from every income and expense report.** Get this
  wrong and every statistic in the app is inflated.
- **Categories can be archived but not deleted** once they have transactions. Deleting
  them would orphan history and break the charts for past months.
- A **recurring rule is a template, not a movement.** Each period it generates a real
  `Transaction` carrying its `ruleId`. Deactivating the rule stops future ones and
  **never deletes past ones** (see §4.4).
- An **account balance is a computed value** (opening balance + movements), not a stored
  mutable field. A mutable field drifts out of sync the moment you edit an old
  transaction. It is computed in SQL and, if performance demands it, cached derivatively.
- Each account has **its own currency**. Totals across currencies require conversion and
  are labelled as approximate.

## 4. Features

Priority: **P0** = required for the app to be worth using · **P1** = important ·
**P2** = nice to have · **P3** = experimental.

### 4.1 Transactions and categories — P0

- Add, edit and delete income and expenses.
- Create, edit, archive and reorder my own categories, with color and icon. Optional
  subcategories (e.g. `Car > Fuel`).
- Fast entry: a dedicated screen with a large numeric keypad, today as the default date,
  and the last used category suggested.
- Transaction list with search and filters (date range, account, category, kind, amount,
  note text).
- Free-text note per transaction and, optionally, a photo of the receipt.

### 4.2 Accounts and transfers — P0

- Multiple accounts, each with its own type, currency and balance.
- Account detail view showing only its movements and its balance over time.
- Transfers between my own accounts, with an optional fee, excluded from expense reports.
- Global filter: view "all accounts" or one specific account.
- Archive closed accounts without losing their history.

### 4.3 Analytics and charts — P0

This is the reason the app exists, not decoration. Required views:

| View | Chart type | Question it answers |
|---|---|---|
| Spending by category | Donut / horizontal bars | Where does my money go? |
| Income vs expenses | Grouped bars per month | Am I saving or overspending? |
| Balance over time | Cumulative line | Am I trending up or down? |
| Spending by weekday | Bars (Mon–Sun) | Do weekends wreck me? |
| Period comparison | Opposing bars | This month vs last / vs the same month last year? |
| Single category detail | Time series line | Is "Restaurants" creeping up month over month? |

Cross-cutting requirements: a period selector (week / month / quarter / year / custom),
tapping a chart segment drills into the transactions behind it, and every chart reads
well in both light and dark themes.

### 4.4 Recurring rules, subscriptions and budgets — P1

**The problem:** subscriptions (Netflix, Spotify, gym, insurance, phone plan) and fixed
movements (salary, rent, mortgage) repeat every month. Entering them by hand is the most
tedious part of tracking finances and the first thing people abandon.

**How it works:** define a **recurring rule** once — amount, category, account, frequency
and day — and the app creates the real transaction each period without me touching
anything.

- Frequencies: monthly, **yearly** (many subscriptions bill annually), weekly,
  fortnightly, quarterly, and every N days.
- Two modes per rule:
  - **Automatic** — created without asking. For fixed, known amounts (Netflix €12.99).
  - **Confirm first** — surfaced as a proposal to approve or edit. For amounts that vary
    (electricity, water).
- Works identically for income: a salary is just a recurring rule.
- Optional end date, and a start date to cover free trial periods.
- Optional reminder a few days before the charge.
- **Subscriptions screen:** every active rule, and above all **what they cost per month
  and per year in total**, plus what share of my income goes to fixed charges. That
  aggregate figure is what makes the feature worth building; a plain list adds nothing.
- **Committed spending this month:** what I already know is going out, even if it has not
  gone out yet.

**Cancelling a subscription — the part that must be right:**

When I delete a subscription I want it to **stop generating from now on**, not to vanish
from the past. If I cancel Netflix in June, the charges from January to May must remain
in history: if they are deleted, every chart for previous months changes and the app
stops being trustworthy for comparing periods. Therefore:

- "Delete" a subscription = **deactivate the rule** (or give it an end date). It stops
  generating new transactions and leaves the active list.
- The transactions it already generated are **never touched**.
- A true delete of the rule also exists, permitted only if it never generated anything.
- If the price rises (Netflix goes to €14.99), the rule is edited **from a date forward**.
  History keeps the price that was actually paid.

**Budgets:** a monthly budget per category, with a progress bar and a warning as the
limit approaches. Known recurring charges count toward the month's budget from day one,
not only when they are charged — otherwise the budget flatters you early in the month.

### 4.5 Investments — P1 (the hardest part)

- Portfolio of holdings: what I own, how much, at what average cost.
- Current market value, profit/loss in € and %, both per holding and for the total.
- Price refresh on app open, on pull-to-refresh, and in the background every N minutes
  (configurable, with a Wi-Fi-only option).
- Portfolio value over time.
- Currency conversion for USD-denominated holdings.
- Buy and sell records, with average cost recalculation.

**See the feasibility analysis in §5.1: "real time" carries important caveats.**

### 4.6 Auto-capture from Wallet — P3 (experimental)

When I pay with my phone, the expense should be logged automatically, without a category,
landing in an "uncategorized inbox" for me to classify later.

- Must be **off by default** and enabled explicitly in settings.
- **Never** connects to a bank or asks for credentials.
- See the feasibility analysis in §5.2: this is the feature most likely not to work as
  hoped.

### 4.7 Data and security — P1

- Export to CSV and to a full JSON backup (so I am never locked into the app).
- Import from that backup.
- App lock using the device fingerprint/PIN (BiometricPrompt).
- Database encryption with SQLCipher — P2.

### 4.8 Polish — P2

Light/dark theme with dynamic color (Material You), a home screen widget for quick
expense entry, an app icon shortcut, screen transition animations, and accessibility
support (TalkBack, font scaling).

## 5. Feasibility analysis

These are the two places where the idea meets technical reality. Worth reading before
counting on the features.

### 5.1 Real-time investment prices

**Verdict: feasible, with caveats about what "real time" means.**

Three very different difficulty tiers:

| What you want to track | Difficulty | How |
|---|---|---|
| Listed stocks and ETFs (NVDA, VOO, IWDA...) | **Easy** | Financial APIs with a free tier, looked up by ticker |
| FX rates (USD→EUR) | **Easy** | The same APIs, or a dedicated FX API |
| Non-listed mutual funds, by ISIN | **Hard** | Almost no free provider covers them |

Things to be clear about:

1. **True real time costs money.** Second-by-second data is licensed by the exchanges and
   sold at a premium. Free tiers give **15-minute delayed** prices, or the previous
   close. For tracking a long-term portfolio that is entirely sufficient — and it has to
   be decided now, because chasing the exact second burns months of work for nothing.
2. **Free tiers have request limits** (on the order of tens per minute or a few hundred
   per day). Hence: cache in the database, batch symbols into one request, and refresh
   only when it adds information — not on every UI recomposition.
3. **If my "S&P 500 fund" is a traditional index fund** (the typical Vanguard or iShares
   product bought through a bank or roboadvisor, identified by ISIN rather than a
   ticker), its NAV is published once a day and is not in the free APIs. Options, best
   first: (a) track the equivalent ETF instead, whose price is accessible and moves
   nearly identically; (b) enter the NAV manually now and then; (c) scrape a financial
   website, which is fragile and breaks silently. **Never make scraping plan A.**
4. **Mandatory design:** a `PriceProvider` interface with swappable implementations, so we
   can change provider when one changes its pricing or kills its free tier. No coupling
   to a specific API.
5. Provider candidates to evaluate in Phase 4: **Finnhub**, **Twelve Data**, **Financial
   Modeling Prep**, **EODHD**. Compare European market coverage, free tier limits and
   terms of use — Yahoo Finance's unofficial API is popular but unsupported and its usage
   sits in a grey area, so it is not chosen as the foundation.

### 5.2 Reading expenses from the Wallet app

**Verdict: partially feasible, fragile, and it may simply not work.**

The important part first: **Android offers no API to read Google Wallet transactions.**
There is no permission that says "give me my recent payments". Wallet does not share that
data with other apps. So there is no clean, supported way to do this.

What **is** possible:

- A **`NotificationListenerService`**: a service that, with explicit user consent, reads
  the notifications other apps post. When you pay with your phone, Wallet (or your bank's
  app) usually posts a notification like *"You paid €12.50 at Mercadona"*. The app can
  read that text, extract the amount and the merchant, and create an uncategorized
  transaction.

Its limitations, unvarnished:

1. **It is text parsing, not structured data.** It depends on the exact wording of the
   notification, the locale, and the app that posted it. Google changes the copy and it
   breaks. It must be built with configurable parsing rules and test fixtures, not one
   giant `if`.
2. **It cannot capture what does not notify.** Physical card payments, direct debits,
   instant transfers and online purchases produce no such notification. It will never be
   a complete record, only an assist.
3. **The permission is special.** It is not a normal runtime dialog: the user must be sent
   to Settings → Notification access and grant it manually. It is a very powerful
   permission (access to *all* notifications), so the app must process only the packages
   the user explicitly selected and persist nothing else.
4. **It drains battery** if implemented carelessly. Processing must be minimal and
   asynchronous.
5. **It blocks Google Play publication.** Play heavily restricts this permission. Since
   this app is personal and side-loaded, that does not matter — but if it were ever to be
   published, this feature would be the biggest obstacle.

**How to approach it:** only once the rest of the app is solid (Phase 5). First build the
"uncategorized inbox" — useful on its own for fast manual entry — and wire auto-capture in
afterwards as one more source feeding it.

**The professional alternative**, should it ever matter: real bank sync is done through
regulated open banking (PSD2) aggregators such as GoCardless, Tink or Plaid. They require
registering as a business and usually cost money, so they are out of scope for a personal
project.

### 5.3 When recurring transactions get generated

**Verdict: feasible and simple, with one Android detail that must be respected from the
design stage.**

Android **does not guarantee** that a background job runs on the 1st at 00:00. The system
delays or drops work to save battery (Doze), the phone may be off, or the app may go
weeks without being opened. If generation depends on a job firing punctually, sooner or
later whole months of subscriptions go missing.

**The correct solution: catch-up generation on app open.** On every launch, the app walks
the active rules, computes which occurrences should exist between the last check and
today, and creates the missing ones. Two properties make it reliable:

- **Idempotent:** each occurrence is identified by `ruleId` + date, so it cannot be
  duplicated even if the check runs several times.
- **Gap-filling:** it behaves correctly even if the phone was off for two months.

WorkManager is used only as a complement, to fire pre-charge reminders — **never as the
source of truth** for generation.

**Classic date trap:** a monthly rule on day 31 in a 30-day month, or 29 February in a
non-leap year. The behaviour is pinned down in writing (clamp to the last day of the
month) and covered by tests. It is the single most common bug in this kind of feature.

## 6. Roadmap

Every phase ends with an app that **works and can be used**, not with half a feature.
This order is designed to put something useful in my hands as early as possible.

| Phase | Goal | Deliverable |
|---|---|---|
| **0. Foundations** | Environment and skeleton | Android Studio working, GitHub repo wired up, a project that builds and installs on the phone, green CI, base theme and navigation |
| **1. Core** | Record money | Room schema, accounts, categories, income/expense entry and editing, transaction list, correct balances |
| **2. Analytics** | Understand money | Reports screen with spending by category, income vs expenses, spending by weekday, period selector |
| **3. Accounts and recurring** | Organize money and automate the fixed parts | Transfers, account detail, currencies, export/import, biometric lock, recurring rules and subscriptions screen |
| **4. Investments** | Track net worth | Manual portfolio → `PriceProvider` integration → P&L and background refresh |
| **5. Automation** | Less manual work | Uncategorized inbox, budgets, and notification capture (experimental) |
| **6. Polish** | Make it a pleasure to use | Widget, animations, Baseline Profile, accessibility, performance measurement |

**Rule:** no phase begins until the previous one has its tests passing and its branch
merged into `main`.

## 7. How the work is run (parallel agents)

The strategy is detailed in [`../CLAUDE.md`](../CLAUDE.md) §7. In short:

- **Phases 0 and 1 run serially.** The database schema and the design system are the
  foundation for everything else: if two agents touch them simultaneously, the result is a
  parade of merge conflicts. They get built once, properly, one at a time.
- **From Phase 2 onward we parallelize**, because clear module boundaries exist by then.
  Example of a simultaneous split, each on its own branch and worktree:
  - Agent A → `feat/reports-charts` (only touches `ui/feature/reports`)
  - Agent B → `feat/price-provider` (only touches `data/remote`)
  - Agent C → `feat/csv-export` (only touches `data/repository` plus a settings screen)
- **Check file overlap before splitting.** Two tasks that edit the same file do not run in
  parallel; they run back to back.
- Every branch is reviewed with `/code-review` before it merges.

## 8. Open decisions

- [x] ~~Final app and package name.~~ → **Cents**, `io.github.anderescaray.cents`
- [ ] Subcategory model: real hierarchy or flat tags?
- [ ] Market data provider (decide in Phase 4, after comparing European coverage).
- [ ] Track the index fund by its ISIN, or by the equivalent ETF? (see §5.1)
- [ ] Is SQLCipher worth it, given the biometric lock already exists?
- [ ] Backup strategy: manual to a file, or automatic to the user's Google Drive?
