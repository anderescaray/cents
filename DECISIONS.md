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

### D18 — what happens when the provider answers in dollars

**Only exists because D13 was answered.** You chose to keep everything in euros and convert by
hand (D12), and you chose **Twelve Data** (D13) — which will answer in **dollars** for NVDA and
in euros for a UCITS ETF. So the provider branch has to do something about a price that arrives
in the wrong currency, and "convert yourself" cannot be the answer once the app is the one
fetching it.

**Options.** **A** — the app fetches an FX rate alongside the price, converts, and stores euros,
with the row saying it was converted and when. **B** — only instruments the provider quotes in
euros refresh themselves; a dollar-priced one keeps its hand-typed price, and the app says why it
is not refreshing. **C** — store the dollar price as it comes and show it in dollars, which is
D12's option B arriving through the back door.

**Recommendation: A.** It is what "I want to see euros" means once a machine is doing the
fetching, and Twelve Data has the FX endpoint. The cost is that a converted price is only as good
as its rate, so the row has to say so — a number that says *converted at 1,08 on 9 Sep* is
honest; the same number with nothing next to it is not.

**Default until answered:** A, with the conversion visible on the row. Nothing is built yet:
`feat/price-provider` is the branch, and it needs your API key before it can run at all.

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
| D12 | What happens to a holding priced in dollars? | **Everything stays in euros, converted by hand.** No currency picker on the holding form: a dollar instrument is entered at what it is worth in euros, the way the rest of the app works (D2). The `currency` column stays on the row for the day that changes, and D18 above is the part of this that a provider forces open. | 2026-09-09 · `feat/investments-portfolio`, `PLAN.md` C4 |
| D13 | Which market data provider? | **Twelve Data**, for European coverage on the free tier — the ETFs are UCITS and listed in Europe. Built behind the `PriceProvider` interface PRD §5.1 makes mandatory, so swapping costs one class. **Blocked until the owner creates an account and puts the key in `local.properties`** (C5, rule 6). | 2026-09-09 · `feat/price-provider` (not built) |
| D14 | Index funds by ISIN, or by the equivalent ETF? | **The equivalent ETF.** A fund's ISIN is the hard tier in PRD §5.1 and no free provider covers it; the ETF that follows the same index is priced, accessible and moves near-identically. The tracking difference is real and is the price of the whole portfolio refreshing itself. The ISIN goes in the holding's note. | 2026-09-09 · PRD §5.1, §8 |
| D15 | Backup: manual to a file, or automatic to Drive? | **Both, in that order.** Manual export first — a file the owner owns, no account and no third party — and automatic backup as a later branch on top of it, with its own answers about encryption and what a restore overwrites. | 2026-09-09 · PRD §8, Phase 3 |
| D16 | Is SQLCipher worth it, with a biometric lock coming? | **No.** Android encrypts the device and no other app can read this one's files; SQLCipher mainly answers root access and physical extraction, at the price of a dependency, slower writes, and a key that can be lost along with the data it protects. The biometric lock is the protection that gets built. | 2026-09-09 · PRD §8, Phase 3 |
| D17 | Should a fully sold holding stay in the portfolio? | **No, keep it hidden.** The portfolio answers *what do I hold*, and a row worth nothing under that heading is noise. What it made is still recorded, so a "closed positions" view can be built the day it is missed — the gap is written down in `PLAN.md` C4 rather than left to be rediscovered. | 2026-09-09 · `feat/investments-portfolio`, `PLAN.md` C4 |
| D11 | Where do investments live, and how does money reach them? | **A fifth bubble and tab**, holdings grouped into **stocks, ETFs, index funds and crypto**, and the portfolio is **not a separate world**: an investments account appears in the Accounts tab, money is transferred into it, and cash sitting there is what buys a holding. Prices are typed by hand for now, with a provider next. | 2026-09-09 · `feat/investments-portfolio`, `PLAN.md` C3 |
| D10 | Should a *spending by weekday* bar open the movements behind it? | **B** — `TransactionFilter` grows a `weekday`, and the Filters form shows a section for it, so a bar drills down like every other chart and the narrowing is visible and clearable. Built in `feat/reports-by-weekday` (2026-09-04): the DAO's filter query gained a weekday clause (`strftime('%w')` rotated onto ISO numbering), the form a "Weekday" section in locale order, and the new view drills each day row down to that weekday inside the period. | 2026-09-04 · `feat/reports-by-weekday`, `PLAN.md` C3 |
| D9 | Phase 2 with one agent, or three? | **Three.** The reason to wait was that Vico and `ui/feature/reports` were shared foundations, and `CLAUDE.md` §7 builds those once and serially; #20 merged them, so that argument is spent. The four remaining PRD §4.3 views — balance over time, spending by weekday, period comparison, single-category detail — split three ways cleanly. Run on Claude Code's worktree parallelism rather than waiting for the harness, which amends D5's *how* and nothing else. | 2026-08-30 · `PLAN.md` Part B §3, D5 above |
| D8 | Bubbles instead of a tab bar? | **Yes, after Phase 1 exits**, as a launcher: tapping a bubble expands it to fill the screen in under 500 ms, the tab bar shows inside a section but can be collapsed, and every piece of that state survives a restart. **Built 2026-09-08** in `feat/bubble-launcher` (#41), with the costs it turned out to carry — accessibility above all — recorded in the ADR. | 2026-08-20, built 2026-09-08 · [`docs/decisions/0002-bubble-launcher.md`](docs/decisions/0002-bubble-launcher.md), `PLAN.md` C3 |
