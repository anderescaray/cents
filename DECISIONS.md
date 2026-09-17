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

### D20 · The free tier cannot price the listings D18 chose. Which way out?

**Asked 2026-09-17, the moment the key arrived**, because the first thing done with it was to ask
the API the question the whole branch rests on — and the answer was no.

> **Written here twice.** This entry first landed with #53 and was lost when that PR and #52
> rewrote this same paragraph in parallel; only one survived the merge. It is back, and the
> hazard is recorded in `PLAN.md` C4 so the next pair of documentation branches does not repeat
> it.

**What was measured**, with the owner's own key, against `api.twelvedata.com`:

| Asked for | Answer |
|---|---|
| `NVDA` (NASDAQ, USD) | **Works.** A full quote |
| `EUR/USD` | **Works.** A full quote |
| `NVD` on XETR, and on Munich | *"This symbol is available starting with the Grow or Venture plan"* |
| `VWCE` on XETR | The same |
| `NVD.DE`, `VWCE.DE`, `IWDA.AS` | 404 — the dotted suffix is not how this API addresses an exchange at all |

Plan limits, from `api_usage`: **8 calls a minute, 800 a day**, category `basic`.

**Why this matters more than a missing symbol.** D18 answered "what happens when the provider
answers in dollars?" with *it never does*, because the app tracks the **euro-denominated listing**
— Nvidia is `NVD` in Frankfurt, in euros, which is what a European platform sells you. That
answer removed conversion, the FX rate, the word *approximately*, and a number that would not
match the broker. **The free tier does not carry those listings.** What it carries is the US
listing, in dollars — precisely the case D18 was written to avoid.

There is a second finding underneath it. A bare symbol is **ambiguous, across currencies**:
`NVD` is NVIDIA in EUR on four German venues *and* a Thai property developer in THB on Bangkok.
So whatever is decided below, a holding has to carry its **exchange** (a MIC code) alongside its
symbol, or the app can price a position with a different company's number.

- **A. Pay for a plan that covers European exchanges.** Restores D18 exactly as written: no
  conversion, and the number matches the broker because it is the same quote. Costs money every
  month, forever, to track a handful of long-term holdings.
- **B. Track the US listing and convert.** Free, and automatic: `NVDA` and `EUR/USD` both work
  today. It reopens everything D18 closed — a rate to keep fresh, a total that is approximate,
  and a figure that will **not** match what the broker shows.
- **C. Try another provider's free tier.** PRD §5.1 named Finnhub, Financial Modeling Prep and
  EODHD for exactly this reason, and `PriceProvider` exists so that swapping costs one class.
  Each needs its own signup, and their marketing describes *paid* coverage without separating
  out the free tier — which is the trap this entry is a record of falling into once already.
- **D. Keep typing prices by hand.** Already built, already working, costs nothing, and is
  exactly as accurate as the owner's own reading of their broker.

**Recommendation: C, then D.** One more signup is cheap and settles it with evidence rather than
with a marketing page; if no free tier carries European venues, hand-typed prices already work.
**B is the option to be most careful about**: it looks like progress and quietly gives back the
thing D18 was written to protect.

**Default until answered: D.** Nothing is built, the portfolio keeps its hand-typed prices with
their age shown, and `feat/price-provider` stays where it is.

**Answer:**

---

### D22 · Where does an automatic backup go?

**Asked 2026-09-17**, on finishing the restore. D15 said manual export first and automatic backup
after; the manual half is built in both directions now, and what is left is a copy that happens
**without being asked for** — which is the failure mode backups are famous for, since every
export so far is one the owner remembered to make.

- **A. A scheduled local copy** into a folder chosen once, kept by the system's persisted
  permission. Needs nothing from anybody, no account and no third party, and it survives the app
  being uninstalled if the folder is somewhere ordinary like Documents. It does **not** survive
  the phone being lost, which is the case a backup most exists for.
- **B. Google Drive**, which is what D15 originally weighed. It survives the phone, and it needs
  an **OAuth client the owner creates in Google Cloud Console**, tied to the app's signing
  certificate — the same shape of blocker as D13's API key, and worth knowing before the branch
  is built rather than after. It also puts the ledger somewhere the owner does not control, which
  is where encryption stops being a question D16 can wave away.
- **C. Both**, local first and Drive later on top of it — which is D15's own shape, applied one
  level down.

**Recommendation: A now, and C's second half only if the phone being lost is the case worth
solving.** A is a week of nothing-in-the-way; B is an afternoon of code behind a credential and a
new answer about encryption.

**Default until answered: nothing.** Exports stay manual, and `PLAN.md` C4 records that nothing
backs up on its own.

**Answer:**


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
