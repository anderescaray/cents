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

### D12 — a holding priced in dollars

**What is at stake.** A holding carries the currency its instrument is priced in, and the app
writes the device's currency into it today (D2: one currency, no picker). That is right for a
euro-denominated ETF and wrong for NVDA, which is priced in dollars whatever you spend in — PRD
§4.5 is explicit that **investments are the one place conversion comes back**.

Nothing is broken while every price is typed by hand: you type what the position is worth in
euros and the arithmetic is sound. It stops being sound the day prices come from a provider,
because the provider will answer in dollars.

**Options.** **A** — holdings stay in the device's currency, and a dollar instrument is entered
in euros by hand. **B** — a holding carries its own currency, the portfolio shows a subtotal per
currency and no grand total (what the Accounts tab already does when accounts disagree). **C** —
a rate is fetched with the prices, and totals are labelled *approximate*.

**Recommendation: B now, C when the provider branch lands.** B costs a picker on the holding
form and is honest; C is a real feature with a rate, a date and an "approximately" behind it,
and it belongs with the provider rather than ahead of it.

**Default until answered:** A. The holding form does not ask, and every holding is created in
the account's currency.

**Answer:**

### D13 — which market data provider

**What is at stake.** PRD §8 has carried this since the beginning and §5.1 narrowed it to four
candidates: **Finnhub**, **Twelve Data**, **Financial Modeling Prep**, **EODHD**. It is your
call rather than the agent's for one reason: **it needs an account and a key**, and only you can
create one (C5). The key goes in `local.properties`, which is git-ignored (rule 6).

The other half of the question is your index funds. A fund identified by an ISIN is the hard
tier in §5.1 — almost no free provider covers it — so it is either tracked by its equivalent
ETF, or its price stays typed by hand while everything else refreshes itself.

**Recommendation: Twelve Data or Finnhub**, on European coverage and free-tier limits, with
index funds tracked by their equivalent ETF and the ISIN kept in the holding's note.

**Default until answered:** every price is typed by hand, which is what ships today. The
`PriceProvider` interface is what the next branch adds; nothing about it is decided by waiting.

**Answer:**

---

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
| D11 | Where do investments live, and how does money reach them? | **A fifth bubble and tab**, holdings grouped into **stocks, ETFs, index funds and crypto**, and the portfolio is **not a separate world**: an investments account appears in the Accounts tab, money is transferred into it, and cash sitting there is what buys a holding. Prices are typed by hand for now, with a provider next. | 2026-09-09 · `feat/investments-portfolio`, `PLAN.md` C3 |
| D10 | Should a *spending by weekday* bar open the movements behind it? | **B** — `TransactionFilter` grows a `weekday`, and the Filters form shows a section for it, so a bar drills down like every other chart and the narrowing is visible and clearable. Built in `feat/reports-by-weekday` (2026-09-04): the DAO's filter query gained a weekday clause (`strftime('%w')` rotated onto ISO numbering), the form a "Weekday" section in locale order, and the new view drills each day row down to that weekday inside the period. | 2026-09-04 · `feat/reports-by-weekday`, `PLAN.md` C3 |
| D9 | Phase 2 with one agent, or three? | **Three.** The reason to wait was that Vico and `ui/feature/reports` were shared foundations, and `CLAUDE.md` §7 builds those once and serially; #20 merged them, so that argument is spent. The four remaining PRD §4.3 views — balance over time, spending by weekday, period comparison, single-category detail — split three ways cleanly. Run on Claude Code's worktree parallelism rather than waiting for the harness, which amends D5's *how* and nothing else. | 2026-08-30 · `PLAN.md` Part B §3, D5 above |
| D8 | Bubbles instead of a tab bar? | **Yes, after Phase 1 exits**, as a launcher: tapping a bubble expands it to fill the screen in under 500 ms, the tab bar shows inside a section but can be collapsed, and every piece of that state survives a restart. **Built 2026-09-08** in `feat/bubble-launcher` (#41), with the costs it turned out to carry — accessibility above all — recorded in the ADR. | 2026-08-20, built 2026-09-08 · [`docs/decisions/0002-bubble-launcher.md`](docs/decisions/0002-bubble-launcher.md), `PLAN.md` C3 |
