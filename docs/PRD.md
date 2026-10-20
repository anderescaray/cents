# PRD — Cents (personal finance app)

**Product document.** Describes *what* we are building, at what scope and in what order.
Technical decisions about *how* live in [`../CLAUDE.md`](../CLAUDE.md).

- **Version:** 0.1, first written 2026-08-18
- **Status:** phases 1 to 5 are built. What is built and what is next is in
  [`../PLAN.md`](../PLAN.md).
- **Author:** Ander

Where a later answer in [`../DECISIONS.md`](../DECISIONS.md) or an ADR in
[`decisions/`](decisions/) disagrees with this document, the later decision wins, and this
document is corrected to match.

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
4. **The numbers never lie.** Exact precision on money, and no invented value: a price is
   one I typed, shown with its date, and without one the app says *no price yet* (D25).
5. **It has a look of its own.** White cards with a soft shadow on a pale canvas, one clear
   number each, the ring of category colours, chips for one-tap choices, and a floating tab
   capsule with the add button in the middle: not iOS and not Material. Specified in
   [`decisions/0003-the-ring.md`](decisions/0003-the-ring.md), which superseded the earlier
   iOS-flavoured design.

**Non-goals** (out of scope, at least for now): multi-user or shared expenses, direct
bank connections, iOS, a web version, publishing on Google Play.

## 2. User

A single user: me. This removes an enormous amount of design surface — no login, no
permissions model, no cross-device sync, no third-party data compliance. If I ever want
it on another phone, that is solved with a backup export/import, not with a server.

## 3. Data model (conceptual)

Understanding these eight entities is understanding the whole app.

| Entity | What it is | Main fields |
|---|---|---|
| **Account** | A container of money: cash, bank or broker | name, type, currency, opening balance, color/icon, archived |
| **Category** | A user-created label for classification | name, kind (income/expense), color, icon, parent category (optional) |
| **Transaction** | One income or expense in an account | amount (cents), currency, kind, account, category, date, note, attachment |
| **Transfer** | Money moved between two of my own accounts | source account, target account, amount, date, fee |
| **RecurringRule** | A template for a repeating movement: subscription, salary, rent | amount, category, account, frequency, day, next date, end date, mode (auto/confirm), active |
| **Holding** | Something I hold: stock, ETF, fund | ticker/ISIN, name, quantity, average cost, currency, account/broker |
| **PriceQuote** | Last known price of an instrument, cached | symbol, price, currency, timestamp, source |
| **Group** | An occasion that cuts across categories: a trip, a move, a freelance job (§4.9) | name, optional start and end date |

**Key decisions:**

- A **transfer is not an expense.** Moving 500 € from checking to savings is not spending
  500 €. Internally it is modelled as a paired outflow and inflow linked by the same
  `transferId`, and it is **excluded from every income and expense report.** Get this
  wrong and every statistic in the app is inflated.
- **Categories can be archived but not deleted** once they have transactions. Deleting
  them would orphan history and break the charts for past months.
- **An account that holds movements can be deleted, but only on purpose.** Unlike a
  category, an account cannot be detached from a movement — `transactions.accountId` is
  `RESTRICT`, and a movement with no account is not a movement. So the app asks which of two
  things "delete" means: move the movements to another account and delete this one, or
  delete both. The second removes recorded history and has no undo, so it is reached through
  a sheet and then a second confirmation naming how many movements go with it
  (`DECISIONS.md` D7). It is the one deliberate exception to "history is immutable", and it
  exists because an account added by mistake and used before the mistake was noticed would
  otherwise haunt every picker forever.
- A **recurring rule is a template, not a movement.** Each period it generates a real
  `Transaction` carrying its `ruleId`. Deactivating the rule stops future ones and
  **never deletes past ones** (see §4.4).
- An **account balance is a computed value** (opening balance + movements), not a stored
  mutable field. A mutable field drifts out of sync the moment you edit an old
  transaction. It is computed in SQL and, if performance demands it, cached derivatively.
- **The app works in one currency: the device's.** Every account and every movement is
  denominated in it, and the app never asks which — there is no picker (`DECISIONS.md` D2,
  answered 2026-08-21). `Money` still carries an ISO-4217 code and still refuses to add
  euros to dollars, but that is a guard rail against a wrong number, not support for a
  second currency. **No conversion step is built**, so where two currencies somehow coexist
  the app shows *no total at all* rather than an approximate one. An earlier version of this
  document promised approximate cross-currency totals; that is no longer what gets built.
  The one place conversion returns is §4.5, where a holding priced in dollars needs a rate
  whatever the owner spends in.

## 4. Features

Priority: **P0** = required for the app to be worth using · **P1** = important ·
**P2** = nice to have · **P3** = experimental.

### 4.1 Transactions and categories — P0

- Add, edit and delete income and expenses.
- Create, edit, archive and reorder my own categories, with color and icon. Optional
  subcategories (e.g. `Car > Fuel`).
- **The app ships with no categories.** It does not propose a starting set and does not
  invent a taxonomy: on a fresh install the list is empty and every category in it is one I
  created, for income and for expenses separately. This is design principle 3 taken
  literally — a suggested list is still the app deciding what my money is spent on.
- **Deleting a category** removes it outright while nothing uses it. Once it classifies
  transactions it can only be archived: it leaves the pickers and its history stays intact
  (see §3, "categories can be archived but not deleted"). The app says which of the two
  happened rather than quietly doing the other one.
- Fast entry: a dedicated screen with a large numeric keypad, today as the default date,
  and the last used category suggested.
- Transaction list with search and filters (date range, account, category, kind, amount,
  note text).
- Free-text note per transaction and, optionally, a photo of the receipt.

### 4.2 Accounts and transfers — P0

- Multiple accounts, each with its own type, colour and balance, all in the one currency
  the app works in (see §3).
- Account detail view showing only its movements and its balance over time.
- Transfers between my own accounts, with an optional fee, excluded from expense reports.
- Global filter: view "all accounts" or one specific account.
- Archive closed accounts without losing their history — and, separately, delete one
  outright, choosing what becomes of its movements (see §3). Its opening balance and balance
  corrections move with them (D33).
- **Three types: Cash, Bank and Broker** (2026-10-09, D34). Room v12 rewrote any older *Card*,
  *Savings* or *Other* account to Bank, and so does restoring an older backup.
- **A broker holds invested money, not everyday spending** (D32):
  - It takes an **income** (a dividend, interest) but not an expense, and transfers in and out.
  - A new movement never starts on a broker, even an income; it is chosen by tapping it, and a
    dividend saved in one does not become where the next movement starts.
  - An income in a broker turned into an expense leaves the broker.
  - Subscriptions and Settings › Capture do not offer a broker, and say so. One already in a
    broker keeps it, as a movement already in one does.
  - Only a broker is offered as where a new investment is held.
- **Set the current balance**, when movements were missed, without inventing an expense or an
  income. Each time, I choose between two ways, and see the current balance, the new one and the
  way chosen before saving:
  - **Keep past balances:** a dated *balance correction* from today. Earlier balances do not
    move. Removing one asks first and has Undo.
  - **Change the opening balance:** past balances shift too, and the app says so.

  Neither touches spending, income, budgets or holdings. A correction is measured against the
  all-time balance (`docs/known-limitations.md`).
- **Cash and bank apart from invested** (2026-10-09, D34), at the top of Home and on Wallet, with
  one formula for both:
  - **Cash and bank:** the active cash and bank accounts' balances.
  - **Invested:** the brokers' cash plus their holdings at the latest typed prices. A broker's
    cash counts while its holdings wait for a price, and the unpriced ones are named. *No price
    yet* shows only when nothing at all has a value.
  - **Net worth:** the two together.

  Settings › *Wallet and Home* switches each one on or off, for both screens. By default the two
  parts show and the total does not; with none on, neither screen shows a figure. On Wallet,
  Invested opens the portfolio, and a row to it stays when Invested is off; on Home the figures
  lead nowhere. A transfer from a bank to a broker moves money from one figure to the other.

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
- Current value, profit/loss in € and %, both per holding and for the total.
- **Prices are typed, for good** (D25): per unit or as a total value, dated. Nothing is fetched,
  and a holding without a price says *no price yet* rather than counting as zero. §5.1 says why.
- **No currency conversion** (D18): a holding tracks the euro listing the broker sells, such as
  `NVD.DE` for Nvidia, so the app shows the broker's own number.
- Buy and sell records, with average cost recalculation; a sale keeps the average. A buy spends
  the broker's cash and a sale gives it back, so neither is income or expense.
- A quantity can be corrected to what the broker says, as a recorded correction that moves no
  money. A holding sold in full leaves the portfolio for **Closed positions**, with what it made
  (D17).

### 4.6 Auto-capture from notifications — P3 (experimental)

*Extended by the owner on 2026-09-26: income as well as spending, the bank's own app as well as
Wallet, and a group as well as a category.*

When my bank's app, or Wallet, tells me that money moved, Cents should write the movement down by
itself. It leaves me only what a notification cannot know: what the money was for.

- **An option I switch on.** It is **off by default** and turned on explicitly in settings.
  Turning it on takes me to Android's *Notification access* screen, the only place that
  permission can be granted (§5.2).
- **Only the apps I pick.** I choose which apps it reads: my bank's, Wallet, or both. I tie each
  one to one of my accounts, so what my bank's app notifies lands in that account. Every other
  app's notifications are ignored, and nothing from them is kept.
- **Money out and money in.** A card payment becomes an expense, and a transfer or a Bizum that
  arrives becomes an income. The amount, the date and the time come from the notification, and
  the merchant or the sender becomes the note.
- **Words decide which.** *Added 2026-09-29 (D29).* Each notification is read for a list of
  expense words and a list of income words. A match decides the kind, and the amount is the one
  in the notification. Both lists come with defaults and can be edited in Settings. A
  notification with neither kind of word, or with no amount, is ignored and nothing from it is
  kept.
- **It waits in *To categorize*,** the inbox, with no category, marked as captured and by which
  app. Until it is filed it is still a real movement. It counts in its account's balance and
  shows as *Uncategorized* in reports, like anything else saved without a category.
- **I file it: a category, and a group if it has one.** One tap on a category chip of its own
  kind files it. One more puts it in a group (§4.9): the dinner of a trip, or a friend paying back
  their half of the hotel. The category stays required, as §4.9 says, and the group stays
  optional. The app does not pick either one for me.
- **Nothing is counted twice.** Android reposts and updates notifications, and a payment made with
  the phone can be announced by both Wallet and the bank. One payment is one movement, however
  many times it is notified. The first rule, set by the owner on 2026-09-29: **two notifications with
  the same amount and kind in the same minute are one movement**, whichever chosen app sent them. A charge that a subscription already records (§4.4), like the rent
  or Netflix, is the same money too (D35, 2026-10-12). A captured payment in the same account, of
  the same kind, within three days either side of a subscription's charge adds nothing new, and a
  variable bill takes its real amount from it, even when it differs. The capture's notification
  says what it matched, with Undo. Settled while building (#131), kept by the owner on 2026-10-12
  and checked on the phone on 2026-10-19:
  - A fixed subscription matches only at its own amount; a different amount is another payment.
    A variable one matches only while its bill waits, and the first capture of its kind in its
    account inside the window fills it, keeping the bill's own date.
  - The charge nearest in date wins, and one charge takes one capture: paying twice does happen.
  - A charge notified early still matches. What has fallen due is written first, as opening the
    app would, and a charge still to come is written by the match, dated its own day.
  - Only an announced match happens. With *Notify after capture* off, or the app's notifications
    blocked, the payment is saved as an ordinary capture, because a wrong match nobody saw could
    not be undone.
  - **Undo**, which works with the app closed, saves the payment as an ordinary capture and puts
    back a charge the match wrote, postponement included, while the subscription still stands
    where the match left it.
- **Never** connects to a bank or asks for credentials: it reads the text the phone already
  shows.
- See the feasibility analysis in §5.2: this is still the feature most likely not to work as
  hoped, and what it catches depends on what each app bothers to notify.

### 4.7 Data and security — P1

- Export to CSV and to a full JSON backup (so I am never locked into the app).
- Import from that backup.
- No lock of the app's own and no SQLCipher (D16, D31): the phone's encryption, its lock screen
  and the app sandbox are the protection.
- An automatic daily backup to the app's own hidden folder on Google Drive, when switched on
  (D22), with a restore from any copy.

### 4.8 Polish — P2

Light/dark theme, a home screen widget for quick expense entry, an app icon shortcut,
screen transition animations, and accessibility support (TalkBack, font scaling).

*Dynamic color (Material You) was dropped:* an accent that follows the wallpaper would
break design principle 5. The palette is fixed, with a proper dark variant.

**The app's own language** (#127): the last row of Settings offers the phone's language, English
and Español, apart from the phone's own setting, and the phone's *Settings › Apps › Cents ›
Language* shows the same choice. Screens, alerts and notifications follow it; what I typed does
not change. **Language never changes money** (D36): amounts, dates and a new account's currency
keep the phone's style and currency, so English on a Spanish phone still reads `1.234,56 €` and
`12 oct`.

### 4.9 Groups: what an occasion cost or earned — P1

*Added by the owner on 2026-09-25. The questions it raises are `DECISIONS.md` D27, each with the
default that gets built.*

A category answers *what kind of thing* the money went on; a group answers *what for*. A trip to
Lisbon has flights under Transport, a hotel under Housing and dinners under Restaurants, and
none of those categories can say what the trip cost. A group can.

- I create a group — *Lisbon trip* — and, while recording a movement, pick it as well as the
  category. **The category stays required and works exactly as before**; the group is an extra,
  optional label on top.
- **A movement belongs to at most one group.** The total of a group is then a plain sum, and two
  groups never count the same euro twice.
- **Income joins a group exactly as an expense does.** A group can be mostly spending — a trip,
  where a friend paying back their half of the hotel lowers what it cost — or mostly earning — a
  freelance job, a market stall, selling the old car — where the expenses are what it took to
  earn it. The group shows what came in, what went out, and the net, and says in words which way
  it went: *cost you 840 €* or *earned you 1 150 €*.
- A **Groups** section lists every group with its total; opening one shows the net, the date of
  its first and last movement, a ring of its spending by category, and the movements themselves,
  each opening the usual form.
- **A group changes no other number.** The trip's dinner is still a Restaurants expense in
  September's reports and budgets. Grouping is a second way of reading the same movements, not a
  way of moving them out of the month they happened in.
- Transfers and the cash movement of a trade cannot join a group, for the same reason they never
  count as income or expense (§3).
- **Deleting a group never deletes a movement** — they only lose the label, as rule 8 already
  requires of subscriptions. It asks for confirmation first.
- **An old group can be archived instead** (#140), so a June trip is not offered in December.
  Archive and Reactivate are in the group's editor. An archived group keeps its movements and
  totals, is listed apart under *Archived*, and is still offered in Filters and when editing a
  movement already recorded, marked *(archived)*. Only a new movement leaves it out.

## 5. Feasibility analysis

These are the two places where the idea meets technical reality. Worth reading before
counting on the features.

### 5.1 Investment prices from the internet

**Verdict: not built, by choice (D25).** Listed stocks and ETFs are easy to price from a paid
API, but the free tiers are delayed, rate-limited and thin on European venues, and the owner's
non-listed funds are in none of them. Paying for a provider was declined on 2026-09-21. Prices are
typed instead, with their date. No provider was built; the price table keeps each quote's date and
where it came from, so one could be added if a free provider covering European venues turns up.

### 5.2 Reading movements from notifications

**Verdict: partially feasible, fragile, and it may simply not work.**

The important part first: **Android offers no API to read Google Wallet transactions,** nor
any bank's. There is no permission that says "give me my recent payments". Neither Wallet nor a
bank's app shares that data with other apps. So there is no clean, supported way to do this.

What **is** possible:

- A **`NotificationListenerService`**: a service that, with explicit user consent, reads
  the notifications other apps post. When you pay, or money arrives, your bank's app (or
  Wallet, for a payment made with the phone) usually posts a notification like *"You paid
  €12.50 at Mercadona"* or *"You have received €120.05 from Ana"*. The app can read that
  text, extract the amount, the direction and the merchant or sender, and create an
  uncategorized transaction.

Its limitations, unvarnished:

1. **It is text parsing, not structured data.** It depends on the exact wording of the
   notification, the locale, and the app that posted it. Every bank words its notifications
   its own way, and Google or the bank changes the copy and it breaks. It must be built with
   configurable parsing rules per app and test fixtures taken from real notifications, not one
   giant `if`.
2. **It only catches what each app notifies.** Some banks announce every card payment and
   every transfer that arrives, while others announce only some. Direct debits and online
   purchases often produce no notification at all, and Wallet only sees payments made with the
   phone. It will never be a complete record, only an assist.
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
| **3. Accounts and recurring** | Organize money and automate the fixed parts | Transfers, account detail, export/import, recurring rules and subscriptions screen. A fingerprint lock was built and then removed (D31); one currency only (D2) |
| **4. Investments** | Track net worth | Portfolio with typed prices, P&L, closed positions (prices are not fetched: D25) |
| **5. Automation** | Less manual work | Uncategorized inbox, budgets, and notification capture (experimental) |
| **6. Polish** | Make it a pleasure to use | Widget, animations, Baseline Profile, accessibility, performance measurement |

**Groups (§4.9)** were added on 2026-09-25, after the phases were set. They belong to none of
them and depend on nothing unbuilt, so they are built whenever the owner picks them.

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

Questions only the owner can settle, and every answer so far, are in
[`../DECISIONS.md`](../DECISIONS.md).
