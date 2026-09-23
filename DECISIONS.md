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

### D26 · How budgets behave (asked 2026-09-23, built on the defaults)

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

No door is waiting on the owner either. **D22**'s backup to Drive was built once the owner had
created the OAuth client, and merged as #69 on 2026-09-23. The other door D20 left open, paying
for a price provider, was closed on 2026-09-21 (D25).

New questions get added here as they come up, each with a default so that an unanswered one
never stops the work — see [`PLAN.md` C6](PLAN.md#c6-questions-waiting-on-the-owner). An empty
inbox is the normal state, not a sign the agent stopped asking: what goes here is only the
choices that are genuinely the owner's, and things going the other way belong in
[`FEEDBACK.md`](FEEDBACK.md).

---

## Answered

| # | Question | Answer | Where it lives now |
|---|---|---|---|
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
| D25 | Pay for a price provider, the one way D20 left to automate prices? | **No.** Prices are typed, for good, and `feat/prices-in-one-place` is the answer rather than a stopgap. `feat/price-provider` is closed without being built: the `PriceProvider` interface, the cache and a quote's age stay, because they cost nothing and a free provider carrying European venues could still turn up. Nothing in the app suggests a provider is coming. | 2026-09-21 · `PLAN.md` C3 (`feat/price-provider` closed) |
| D24 | How does the parallel phase run now? | **Orca, with four tools and fixed roles**, amending D5 and D9 on the mechanism only. Orca creates a worktree per task from `origin/main`, and the cap stays at **three implementers at once**, because it comes from review capacity. **Devin** (CLI, in Orca) is the main implementer and takes the heaviest branches, schema work included. **Cursor** (`cursor-agent`) is the second implementer, for UI kept inside one feature folder. **Gemini CLI** takes small, well-scoped branches and gives a second review. **Claude Code** splits the work, writes the task prompts, reviews each PR with `/code-review`, and is the only one that edits the shared documents, in one docs branch after merges. `AGENTS.md` gives the non-Claude agents the rules; `.worktreeinclude` copies `local.properties` into every worktree. What stays with the owner is unchanged: deciding the split, settling contradictions, merging. | 2026-09-21 · `AGENTS.md`, `GEMINI.md`, `.worktreeinclude` |
| D23 | Should a subscription ever interrupt you? | **Yes, and only on the owner’s terms — which is C, with more detail than the question had.** A subscription can be marked **variable amount**, and only those get asked about: a notification **N days before or after** the date, **as many as you like** per rule, each one reminding you to put in what it actually came to. A **fixed** subscription is silent by default and can opt in to a single heads-up — *Spotify is paid in 3 days* — with N chosen per rule. Nothing is ever announced after the fact for a rule that recorded itself. **It lands in two branches, because the second needs the first:** somewhere to answer a variable bill has to exist before a notification can send you there. | 2026-09-17 · `feat/subscription-amounts` (the answering), then `feat/subscription-reminders` (the notifications) |
| D22 | Where does an automatic backup go? | **Google Drive, and only a backup — no sync.** It survives the phone being lost, which is the case a backup most exists for, and it costs an OAuth client the owner creates in Google Cloud Console (nothing secret reaches the repo: an Android client is matched by package name and signing certificate). **The owner asked whether this means the same data on a phone and a tablet, and the honest answer is no** — a restore replaces everything (D21), so two devices recording would diverge silently and whichever restored next would destroy its own changes. Confirmed after that was explained: **one device records**, the other gets the data by restoring. Real sync is a different feature needing a conflict model and a backend this app has deliberately never had (§1), and it is not being built. | 2026-09-17, built 2026-09-23 · `feat/backup` (#69); subscriptions in the backup since #72 |
| D20 | The free tier cannot price the listings D18 chose. Which way out? | **None of them, for now — make typing fast instead.** The owner asked for "the euros I put into each asset, kept up to date from the internet, however you do it, or failing that a way to update it by hand", and the measurements say the first half is not available free: Twelve Data refuses XETR listings on `basic`, Finnhub's free tier is US-only, EODHD's free plan is US exchanges. **Converting does not rescue it**, which is the finding that settles this: a UCITS ETF like `VWCE` has no US listing to convert *from* — it is a European-only instrument, and a US proxy is a different fund. So conversion would automate the stocks and leave the ETFs, which is most of the point. Typing is therefore how prices get in, and `feat/prices-in-one-place` makes that one screen instead of one visit per holding. **Paying is the open door**: it is A in the list above, and it would restore D18 exactly. | 2026-09-17 · `feat/prices-in-one-place`, PRD §5.1 |
| D21 | What does a restore do to what is already on the phone? | **Replace everything**, behind a confirmation naming both halves — what the file holds, and what is about to go — shown only after the file has been read, so those numbers are checkable. Merging needs id remapping and duplicate detection for a case that barely exists with one owner; refusing unless the app is empty is useless exactly when it is needed. **Encryption stays out** for the same reason D16 gave, and comes back when a backup goes somewhere the owner does not control. | 2026-09-17 · `feat/restore`, `RoomBackupRepository.restore` |
| D19 | Is there a net worth figure, and what does an unpriced holding do to it? | **Yes, and show all three numbers**: what the accounts hold, what the portfolio is worth, and the two added together — so net worth can be read with the portfolio and without it. Closest to option A, but asking for the parts as well as the sum: a single figure hides which of the two moved when it changes. An unpriced holding follows the rule the portfolio already applies one screen down — count the priced ones, and say how many were left out. | 2026-09-17 · `feat/net-worth`, Accounts tab |
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
