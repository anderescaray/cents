# DECISIONS.md — questions waiting on the owner

**What this file is for.** Cents is built by an agent that can decide how to write something,
but not what the app should be. When a choice is genuinely the owner's — because different
answers lead to different apps, not just different code — the question is written here instead
of being guessed at, and work continues on everything that does not depend on it.

**How it works:**

1. The agent adds a question below, under *Open*, with what is at stake and a recommendation.
2. The owner writes the answer on the `**Answer:**` line. One word is enough.
3. At the start of the next session the agent reads this file, acts on the answers, and moves
   each answered question to *Answered*, with the date and what was actually done.

Nothing here blocks the project: every open question has a default recorded next to it, and
that default is what the app does until the answer changes it.

> Not to be confused with [`docs/decisions/`](docs/decisions/), which is the architecture
> decision log — decisions already taken and their consequences, written to be read in a
> year. This file is the inbox that feeds it: an answer here that turns out to shape the
> codebase graduates into an ADR there.

**This file runs one way: agent → owner.** Things going the other way — a bug you hit, a
gesture that feels wrong, a screen you want changed — belong in [`FEEDBACK.md`](FEEDBACK.md),
which the agent also reads before writing any code. Keeping them apart is what stops either
one from turning into a general notes file that nobody trusts.

---

## Open

### Waiting for an answer

Nothing is waiting for an answer (2026-10-12).

### Built on the defaults: answer only to change one

Each of these was built as written. A line the owner changes is an instruction, like any answer.

#### D29 · How capture from notifications behaves (asked and answered 2026-09-29, built in #105–#107)

**The owner described the feature on 2026-09-29, and answered the three questions it raised the
same day.** It is PRD §4.6, built in #105–#107, with the words made per app by D30 and the
amount rules tightened in #113. Change any line and the next session changes the app.

What the owner said, in their words turned into rules:

- **Only the apps I pick.** Settings lists the apps on the phone; capture reads the ones chosen
  and nothing else. *(The owner's answer.)*
- **Words decide expense or income.** Each app's notification is read for a list of **expense
  words** and a list of **income words**. **Amended 2026-10-01: the lists are per app** — each
  chosen app has its own two, starting from the shared starting words until edited (D30). A match makes it an expense or an income, and **the
  amount is the one in the notification**. *(The owner's answer.)*
- **The words have defaults and can be edited in Settings**, added to, removed, and put back to
  the defaults. *(The owner's answer.)*
- **Two of the same in the same minute count once.** Two notifications with the same amount and
  the same kind in the same minute are one movement. *(The owner's answer.)* Built as "within a
  minute of each other", so 10:00:59 and 10:01:02 are one payment rather than two clock minutes.
- **A captured movement is saved at once, without a category**, and waits under *To categorize*.
  It counts in balances and reports like any movement saved without one. *(Answered 2026-09-29.)*
- **Each app is tied to one account**, chosen when the app is switched on. *(Answered 2026-09-29.)*
- **A notification with no expense or income word, or no amount, is ignored**, and nothing
  from it is kept. So a bank's advert or security notice never becomes a movement.
  *(Answered 2026-09-29.)*

What the owner did not say, each with the default the branch will build:

- **The default words.** Expense: *pago, compra, cargo, has pagado, retirada, domiciliación,
  payment, purchase, paid, spent*. Income: *ingreso, recibido, has recibido, abono, te han
  enviado, nómina, received, deposit*. Matched ignoring case and accents, as whole words or
  phrases. *Default: these lists.*
- **When a notification has both kinds of word,** the income word wins. *"Has recibido un pago
  de Ana"* holds *pago*, and messages about money arriving often mention a payment, while
  messages about money leaving rarely say *received*. *Default: income wins.*
- **The amount** is the first amount in the text written with a currency sign or code, or with
  two decimals (*23,40 €*, *€23.40*, *EUR 23,40*). An amount in a currency other than the
  account's is ignored, because there is no conversion (D2). *Default: as written.*
- **Same minute across apps, too.** The same payment announced by Wallet and by the bank in the
  same minute is one movement. *Default: the same-minute rule applies across every chosen app.*
- **The note** is the notification's own text, trimmed, so the merchant or sender is in it.
  Parsing the merchant out is left for later. *Default: the whole text.*
- **The date and time** are when the notification arrived. *Default: as written.*
- **A charge a subscription already records** (PRD §4.6, last point) is not matched in the
  first version. If the rent is both a rule and notified, it is recorded twice, and the extra
  one is deleted by hand. *Default: matching comes in a later branch.* **Answered 2026-10-12 by
  D35**, which says how capture matches; built in #131 and checked on the phone 2026-10-19.
- **Where it lives.** Settings › Capture: the switch, the chosen apps with their accounts, and
  the two word lists. *Default: as written.*

**Answer:**

#### D28 · How postponing behaves (asked 2026-09-25, built on the defaults)

**Built on 2026-09-25, as #97, on every default below.** Change any line and the next session
changes the app.

The owner asked on 2026-09-25 to be able to postpone a charge or a salary by some number of days,
including a salary whose amount changes and is asked for like a variable bill. Asked whether that
should move only the reminder or the date too, the answer was **both**: postponing by 3 days means
it is recorded on that day, and the owner is reminded that morning, for income as well as
expenses.

- **What moves.** One occurrence: the movement is dated the new day, and the rule's schedule
  does not change, so next month is on its usual day. *Default: as written (the owner's answer).*
- **How far.** 1, 2, 3, 5, 7 or 14 days, each shown with the date it lands on, and **never as
  far as the next occurrence**, so two occurrences can never swap order. *Default: as written.*
- **From where.**
  - From the rule's editor, for its next occurrence, counted from its own date.
  - From the pending screen, for a variable bill or salary already waiting, counted from today.
  - *Default: both.*
- **A reminder on the new day, always**, even for a rule with no reminders set: moving something
  is taken as asking to be told when it comes round again. Its own reminders that land after the
  original day follow the new date. *Default: as written.*
- **Undoable.** *Back to its own day* in the editor puts it back. *Default: as written.*
- **Not for something already written.** Once a charge is recorded, it is a movement, and its date
  is edited on the movement itself. *Default: as written.*
- **Budgets and *committed this month* count a moved charge in the month it moved to**, the
  month its movement is dated in. First built as "leave it until it matters"; the owner asked
  for the fix on 2026-09-27, and it is built.

**Answer:**

#### D27 · How groups behave (asked 2026-09-25, built on the defaults)

**Built on 2026-09-25, as #86 and #87, on every default below.** Change any line and the next
session changes the app.

The owner asked on 2026-09-25 for a way to see what an occasion cost — a trip, say — across
several categories (PRD §4.9), and then that incomes can be grouped as well. These are the
choices it needed, **each with the default that was built** unless a line changes.

- **What it is called.** *Groups* in English and *Grupos* in Spanish. *Events* or *Trips* are the
  alternatives. *Default: Groups.*
- **One group per movement, not several tags.** One group keeps every total a plain sum. Tags
  would let a dinner be both *Lisbon trip* and *Work*, at the price of totals that overlap.
  *Default: one group.*
- **Income joins a group exactly as an expense does** (the owner asked for this explicitly on
  2026-09-25). A group can be about spending, like a trip where a friend's repayment lowers the
  cost, or about earning, like a freelance job whose expenses are what it took to earn it. It
  shows what came in, what went out and the net, worded as *cost you …* or *earned you …*
  depending on the sign. *Default: yes.*
- **Optional dates.** A group can carry a start and end date. While a movement's date falls
  inside them, the form **offers** that group at the top of the picker, but never picks it on its
  own. *Default: as written.*
- **Reports and budgets are unchanged.** The trip's expenses still count in their month and
  their category. There is no switch to take them out, because a trip that disappears from
  September makes September lie. *Default: as written.*
- **Where it lives.** A **Groups** row in Settings, beside Subscriptions and Budgets, and a group
  filter on the movement list. *Default: as written.*
- **Adding past movements.** The first version assigns a group from the movement form, one at a
  time. Selecting many movements on the list and grouping them at once would come after, if
  recording as you go turns out not to be enough. *Default: one at a time.*
- **Subscriptions do not carry a group.** A subscription is not an occasion. *Default: as
  written.*
- **A budget for a group** (*the trip may cost 1 200 €*) is not in the first version. *Default:
  later, if asked.*

**Answer:**

#### D26 · How budgets behave (asked 2026-09-23, built on the defaults)

The owner chose budgets (PRD §4.4) as the next work on 2026-09-23. The PRD fixes two things: a
monthly limit per category, and that known recurring charges count from day one. What follows is
everything else, **each with the default that is being built**. Change any line and the next
session changes the app.

- **Monthly only, one limit per expense category.** No weekly budgets, no budget across several
  categories, and none on income. *Default: as written.*
- **What counts as spent:** that category's expenses this month. Transfers and a trade's cash have
  no category, so they never count. *Default: as written.*
- **What counts as committed:** subscriptions in that category still due before the month ends,
  plus variable bills still waiting, at their estimate. This is the same rule as *committed this
  month* on the Subscriptions screen, narrowed to one category. *Default: as written.*
- **The warning:** the bar turns amber at **80 %** of the limit and red above **100 %**, with the
  amount left or the amount over written beside it. **No notification.** A budget that interrupts
  you is a different feature, and D23 kept interruptions to subscriptions. *Default: as written.*
- **Where it lives:** a **Budgets** row in Settings, beside Subscriptions. It is not a chart on
  Reports, which answers questions about the past rather than about a limit. *Default: as
  written.*
- **An archived category's budget** is kept but hidden, and it returns if the category does.
  *Default: as written.*

**Answer:**

New questions get added here as they come up, each with a default so that an unanswered one
never stops the work — see [`PLAN.md` §5](PLAN.md#5-how-work-passes-between-owner-and-agent). An empty
inbox is the normal state, not a sign the agent stopped asking: what goes here is only the
choices that are genuinely the owner's, and things going the other way belong in
[`FEEDBACK.md`](FEEDBACK.md).

---

## Answered

The last column names where each answer was built. Mentions of `PLAN.md` C3, C4, C7 or Part B
point at that file as it was before 2026-10-07, when it was cut to the present; git history has
those versions. Limitations that are still true are now in
[`docs/known-limitations.md`](docs/known-limitations.md).

| # | Question | Answer | Where it lives now |
|---|---|---|---|
| D32 | Item 9 keeps brokers out of the movement form. Should subscriptions and capture refuse them too, and how does a dividend get into a broker? | **1, no:** a new subscription is not offered a broker. **2, no:** an app chosen in Settings › Capture is not offered a broker either, which changes the default (it was yes). **3, B:** a broker takes an **income** but not an expense, so a dividend or interest is recorded as money earned and counts in Reports. Asked 2026-10-07, answered 2026-10-12. What the answer left open, each line with its default, is in `docs/PRD.md` §4.2 (it was the backlog's item 9). | 2026-10-12 · #133: `takesNew(kind)` in `AccountEligibility.kt`; the defaults it left open are in `docs/PRD.md` §4.2 |
| D36 | With English chosen on a Spanish phone, how do amounts and dates look? | **B, the phone's style:** `1.234,56 €` and `12 oct`. The app's language changes the words; amounts, dates and the currency stay the phone's. Asked in #128 with A built in #127, answered 2026-10-12. Month and weekday names follow the phone too, as B's own `12 oct` does, so *Today* sits beside `10 oct 2026`. | 2026-10-12 · #129: `formatLocale()` in `ui/util/FormatLocale.kt`, the phone's locale held by `ui/language/PhoneLocale.kt` |
| D34 | Only cash, bank and broker: what does an existing Card, Savings or Other account become? And with brokers kept apart from cash and bank, is a total of the two still shown? | **Types:** the owner has no account of those types, so they simply go. The migration still rewrites any it finds to Bank, so a stray row or an older backup cannot crash a read. **Figures:** the owner chooses which to show, normal money, invested and their total, any of them, all of them or none. Asked 2026-10-09, answered 2026-10-12. What the answer left open, each with its default, is in `docs/PRD.md` §4.2 (it was the backlog's items 6 and 11). | 2026-10-12 · **Types:** #136, `MIGRATION_11_12` (Room v12) and `BackupMappers.kt`. **Figures:** #138, `netWorthOf` in `BuildNetWorth.kt`, Settings › *Wallet and Home* |
| D35 | Capture counts a subscription's charge twice, once from the rule and once from the notification. What should it do? | **1, A:** when a captured payment matches a subscription's charge, nothing new is saved, and the capture notification says *Matched <subscription>, nothing added*, with Undo. **2, yes:** for a subscription marked variable, a capture within the window fills in the bill's real amount, even when it differs. **3:** the window is **3 days either side** of the charge's date, in the **same account**, of the **same kind**. Asked and answered 2026-10-12. What the answer left open is in the backlog item, each line with its default. | 2026-10-12 · #131: `CaptureMatcher.kt`, Room v11. Checked on the phone 2026-10-19; what it settled, defaults included, is in `docs/PRD.md` §4.6 |
| D33 | Deleting an account and moving its movements: what happens to its opening balance and its balance corrections? | **B: both move with the movements.** The other account's opening balance grows by the deleted one's, and the corrections move across, so its balance becomes the two accounts' together. Asked in #120, after #112's review found that the corrections moved and the opening balance did not; answered 2026-10-08. Deleting an empty account still deletes its corrections, because nothing moves. | 2026-10-08 · #120, `reassignAndDelete` in `RoomRepositories.kt` |
| D30 | What replaces the bubbles, and how far does the rework go? | **Everything, after the film, and not iOS.** Home is for recording and filing (quick add, *To categorize*, the month with what was left or overspent as its headline); the capsule is Home · Movements · + · Reports · Wallet; budgets move to Reports; Reports' cards can be reordered and hidden; investments open from Wallet › Invested. **Capture's words are per app** (amends D29). Defaults taken without asking: Settings opens from Home's header; the bubble layout is deleted, not kept as an option; the ring is the launcher icon. | 2026-10-01 · `feat/ui-rework`, ADR 0003 (accepted, with amendments) |
| — | Does the app propose a starting set of categories? | No. It ships with none; every category is one the owner typed. | PRD §4.1, `DefaultDataSeeder` |
| — | Material or an iOS-flavoured UI? | iOS conventions, Material only as a base. | [`docs/decisions/0001-ios-flavored-ui.md`](docs/decisions/0001-ios-flavored-ui.md) |
| — | Who decides when the project goes multi-agent? | The owner, deliberately, when Phase 1 exits. | `PLAN.md` Part B §3, D5 below |
| D1 | Does the app still create a *Cash* account on first run? | **No.** A fresh install has no accounts at all; the first run asks for one. | 2026-08-20 · seeding removed in `feat/account-deletion`, `PLAN.md` C3 |
| D2 | More than one currency, genuinely? | **One, always — EUR.** Confirmed 2026-08-21: the currency picker is gone and the device's currency is used, shown read-only. Totals and charts stay single-currency and no conversion step is built. `Money` keeps carrying a code and refusing cross-currency arithmetic, as a guard rail rather than a feature. | 2026-08-20, sharpened 2026-08-21 · `chore/single-currency`, `PLAN.md` C4 |
| D3 | Subcategories? | **No, flat.** The `parentId` column stays unused rather than being removed. | 2026-08-20 · `PLAN.md` C4 |
| D4 | Which two charts first? | **Spending by category**, then **income vs expenses per month**. Long term they become cards on a customisable dashboard — reorderable, resizable, filterable by date range. | 2026-08-20 · `PLAN.md` C7 |
| D5 | How is the parallel phase run? | **The owner's own harness** — Orca-like orchestration plus the ideas from `xmihura/kanvas` — running **at most 3 agents** at once. **Amended 2026-08-30 by D9:** the harness was still being built when the phase came due, so the first parallel run uses Claude Code's own worktree parallelism instead. The cap of 3 and everything else about D5 stands; only the mechanism changed, and the harness remains the intended one. | 2026-08-20, amended 2026-08-30 · `PLAN.md` Part B §4 |
| D6 | What if the review agent cannot run? | **A** — self-review, and the PR says plainly that it was not independent. | 2026-08-20 · `PLAN.md` Part B §2 |
| D7 | Deleting an account that still has movements? | **Delete outright when it is empty.** When it is not, an alert offers two ways out: reassign every movement to another account and then delete, or delete the account *and* its movements after an explicit confirmation. | 2026-08-20 · `feat/account-deletion`, `PLAN.md` C3 |
| D31 | Keep the app lock (fingerprint or PIN on opening)? | **No, remove it.** Asked on 2026-10-05, because the uncommitted capture work of 2026-10-04 had already taken it out with no decision behind it. The owner confirmed. **This amends D16**: SQLCipher was declined partly because the lock was the protection being built, so with the lock gone what protects the data at rest is the phone's own encryption, its lock screen and the app sandbox. That is still the reason SQLCipher stays out. `androidx.biometric` leaves the dependency table, and `MainActivity` goes back to a `ComponentActivity`. | 2026-10-05 · `chore/remove-app-lock`, PRD §4.7 |
| D25 | Pay for a price provider, the one way D20 left to automate prices? | **No.** Prices are typed, for good, and `feat/prices-in-one-place` is the answer rather than a stopgap. `feat/price-provider` is closed without being built: the `PriceProvider` interface, the cache and a quote's age stay, because they cost nothing and a free provider carrying European venues could still turn up. Nothing in the app suggests a provider is coming. | 2026-09-21 · `PLAN.md` C3 (`feat/price-provider` closed) |
| D24 | How does the parallel phase run now? | **Orca, with four tools and fixed roles**, amending D5 and D9 on the mechanism only. Orca creates a worktree per task from `origin/main`, and the cap stays at **three implementers at once**, because it comes from review capacity. **Devin** (CLI, in Orca) is the main implementer and takes the heaviest branches, schema work included. **Cursor** (`cursor-agent`) is the second implementer, for UI kept inside one feature folder. **Gemini CLI** takes small, well-scoped branches and gives a second review. **Claude Code** splits the work, writes the task prompts, reviews each PR with `/code-review`, and is the only one that edits the shared documents, in one docs branch after merges. `AGENTS.md` gives the non-Claude agents the rules; `.worktreeinclude` copies `local.properties` into every worktree. What stays with the owner is unchanged: deciding the split, settling contradictions, merging. | 2026-09-21 · `AGENTS.md`, `GEMINI.md`, `.worktreeinclude` |
| D23 | Should a subscription ever interrupt you? | **Yes, and only on the owner’s terms — which is C, with more detail than the question had.** A subscription can be marked **variable amount**, and only those get asked about: a notification **N days before or after** the date, **as many as you like** per rule, each one reminding you to put in what it actually came to. A **fixed** subscription is silent by default and can opt in to a single heads-up — *Spotify is paid in 3 days* — with N chosen per rule. Nothing is ever announced after the fact for a rule that recorded itself. **It lands in two branches, because the second needs the first:** somewhere to answer a variable bill has to exist before a notification can send you there. | 2026-09-17 · `feat/subscription-amounts` (the answering), then `feat/subscription-reminders` (the notifications) |
| D22 | Where does an automatic backup go? | **Google Drive, and only a backup — no sync.** It survives the phone being lost, which is the case a backup most exists for, and it costs an OAuth client the owner creates in Google Cloud Console (nothing secret reaches the repo: an Android client is matched by package name and signing certificate). **The owner asked whether this means the same data on a phone and a tablet, and the honest answer is no** — a restore replaces everything (D21), so two devices recording would diverge silently and whichever restored next would destroy its own changes. Confirmed after that was explained: **one device records**, the other gets the data by restoring. Real sync is a different feature needing a conflict model and a backend this app has deliberately never had (§1), and it is not being built. | 2026-09-17, built 2026-09-23 · `feat/backup` (#69); subscriptions in the backup since #72 |
| D20 | The free tier cannot price the listings D18 chose. Which way out? | **None of them, for now — make typing fast instead.** The owner asked for "the euros I put into each asset, kept up to date from the internet, however you do it, or failing that a way to update it by hand", and the measurements say the first half is not available free: Twelve Data refuses XETR listings on `basic`, Finnhub's free tier is US-only, EODHD's free plan is US exchanges. **Converting does not rescue it**, which is the finding that settles this: a UCITS ETF like `VWCE` has no US listing to convert *from* — it is a European-only instrument, and a US proxy is a different fund. So conversion would automate the stocks and leave the ETFs, which is most of the point. Typing is therefore how prices get in, and `feat/prices-in-one-place` makes that one screen instead of one visit per holding. **Paying is the open door**: it is A in the list above, and it would restore D18 exactly. | 2026-09-17 · `feat/prices-in-one-place`, PRD §5.1 |
| D21 | What does a restore do to what is already on the phone? | **Replace everything**, behind a confirmation naming both halves — what the file holds, and what is about to go — shown only after the file has been read, so those numbers are checkable. Merging needs id remapping and duplicate detection for a case that barely exists with one owner; refusing unless the app is empty is useless exactly when it is needed. **Encryption stays out** for the same reason D16 gave, and comes back when a backup goes somewhere the owner does not control. | 2026-09-17 · `feat/restore`, `RoomBackupRepository.restore` |
| D19 | Is there a net worth figure, and what does an unpriced holding do to it? | **Yes, and show all three numbers**: what the accounts hold, what the portfolio is worth, and the two added together — so net worth can be read with the portfolio and without it. Closest to option A, but asking for the parts as well as the sum: a single figure hides which of the two moved when it changes. An unpriced holding follows the rule the portfolio already applies one screen down — count the priced ones, and say how many were left out. | 2026-09-17 · `feat/net-worth`, Accounts tab. Amended by D34 (#138): a broker's cash is invested rather than in accounts, and which of the three show is the owner's choice |
| D18 | What happens when the provider answers in dollars? | **It does not.** The holding tracks the **euro-denominated listing** of the same company — Nvidia trades on XETRA, Tradegate and Gettex as **NVD**, in euros — which is the listing the owner actually bought on a European platform. So there is no conversion anywhere: no FX rate, no "approximately", and the number in the app is the same number the broker shows, because it is the same quote. The symbol field already carries this: it is free text, and it holds the ticker as the platform writes it. | 2026-09-09 · `feat/price-provider`, PRD §4.5 |
| D12 | What happens to a holding priced in dollars? | **Everything stays in euros.** No currency picker on the holding form. What makes this hold rather than merely hope is D18: a company priced in dollars in New York is priced in euros in Frankfurt, and the holding tracks *that* listing — so nothing is ever converted, by hand or otherwise. The `currency` column stays on the row for the day a genuinely dollar-only instrument turns up. | 2026-09-09 · `feat/investments-portfolio`, `PLAN.md` C4 |
| D13 | Which market data provider? | **Twelve Data**, for European coverage on the free tier — the ETFs are UCITS and listed in Europe. Built behind the `PriceProvider` interface PRD §5.1 makes mandatory, so swapping costs one class. **Blocked until the owner creates an account and puts the key in `local.properties`** (C5, rule 6). | 2026-09-09 · `feat/price-provider` (not built) |
| D14 | Index funds by ISIN, or by the equivalent ETF? | **The equivalent ETF.** A fund's ISIN is the hard tier in PRD §5.1 and no free provider covers it; the ETF that follows the same index is priced, accessible and moves near-identically. The tracking difference is real and is the price of the whole portfolio refreshing itself. The ISIN goes in the holding's note. | 2026-09-09 · PRD §5.1, §8 |
| D15 | Backup: manual to a file, or automatic to Drive? | **Both, in that order.** Manual export first — a file the owner owns, no account and no third party — and automatic backup as a later branch on top of it, with its own answers about encryption and what a restore overwrites. | 2026-09-09 · PRD §8, Phase 3 |
| D16 | Is SQLCipher worth it, with a biometric lock coming? | **No.** Android encrypts the device and no other app can read this one's files; SQLCipher mainly answers root access and physical extraction, at the price of a dependency, slower writes, and a key that can be lost along with the data it protects. The biometric lock is the protection that gets built. | 2026-09-09 · PRD §8, Phase 3 |
| D17 | Should a fully sold holding stay in the portfolio? | **No, keep it hidden.** The portfolio answers *what do I hold*, and a row worth nothing under that heading is noise. What it made is still recorded, so a "closed positions" view can be built the day it is missed — the gap is written down in `PLAN.md` C4 rather than left to be rediscovered. | 2026-09-09 · `feat/investments-portfolio`, `PLAN.md` C4 |
| D11 | Where do investments live, and how does money reach them? | **A fifth bubble and tab**, holdings grouped into **stocks, ETFs, index funds and crypto**, and the portfolio is **not a separate world**: an investments account appears in the Accounts tab, money is transferred into it, and cash sitting there is what buys a holding. Prices are typed by hand for now, with a provider next. | 2026-09-09 · `feat/investments-portfolio`, `PLAN.md` C3 |
| D10 | Should a *spending by weekday* bar open the movements behind it? | **B** — `TransactionFilter` grows a `weekday`, and the Filters form shows a section for it, so a bar drills down like every other chart and the narrowing is visible and clearable. Built in `feat/reports-by-weekday` (2026-09-04): the DAO's filter query gained a weekday clause (`strftime('%w')` rotated onto ISO numbering), the form a "Weekday" section in locale order, and the new view drills each day row down to that weekday inside the period. | 2026-09-04 · `feat/reports-by-weekday`, `PLAN.md` C3 |
| D9 | Phase 2 with one agent, or three? | **Three.** The reason to wait was that Vico and `ui/feature/reports` were shared foundations, and `CLAUDE.md` §7 builds those once and serially; #20 merged them, so that argument is spent. The four remaining PRD §4.3 views — balance over time, spending by weekday, period comparison, single-category detail — split three ways cleanly. Run on Claude Code's worktree parallelism rather than waiting for the harness, which amends D5's *how* and nothing else. | 2026-08-30 · `PLAN.md` Part B §3, D5 above |
| D8 | Bubbles instead of a tab bar? | **Yes, after Phase 1 exits**, as a launcher: tapping a bubble expands it to fill the screen in under 500 ms, the tab bar shows inside a section but can be collapsed, and every piece of that state survives a restart. **Built 2026-09-08** in `feat/bubble-launcher` (#41), with the costs it turned out to carry — accessibility above all — recorded in the ADR. | 2026-08-20, built 2026-09-08 · [`docs/decisions/0002-bubble-launcher.md`](docs/decisions/0002-bubble-launcher.md), `PLAN.md` C3 |
