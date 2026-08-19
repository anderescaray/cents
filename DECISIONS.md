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

---

## Open

### D1 · Does the app still create a *Cash* account on first run?

**Needed by:** the next branch. **Default while unanswered:** it keeps creating it.

Until now a *Cash* account was seeded on first run for one reason: a movement cannot be saved
without an account, so without it the entry form was a dead end. That reason is gone — accounts
are created inside the app now, and the form leads you to creating one when there are none.

The categories went the same way for the same reason, on your instruction: *the app ships with
none, and every category is one you typed*. The question is whether that principle covers
accounts too.

- **A — Remove it.** A fresh install creates nothing at all. The first thing the app asks is
  what your account is called and what is in it. *(Recommended: it is the same argument as the
  categories, and "Cash" in your locale's currency is still the app guessing.)*
- **B — Keep it.** One less step before the first expense can be logged.

**Answer:**

---

### D2 · Do you actually hold money in more than one currency?

**Needed by:** Phase 2 (reports), and again in Phase 4 (investments).
**Default while unanswered:** multi-currency stays possible but never converted.

The app already refuses to add euros to dollars: `Money` will not do arithmetic across
currencies, and the accounts list shows no total at all when its accounts disagree. That is
correct but minimal. What comes next depends on the real answer:

- **A — One currency in practice.** Totals, charts and net worth are simple, and a conversion
  step is never built. Cross-currency stays a guard rail, not a feature.
- **B — More than one, genuinely.** Then Phase 2 needs a designed answer: where rates come
  from, when they are fetched, how a total made of converted amounts is labelled so it never
  looks exact. That is real work and should be planned, not improvised inside a chart.

**Answer:**

---

### D3 · Do you want subcategories?

**Needed by:** before filters and reports are built on top of categories.
**Default while unanswered:** no — the list stays flat.

The database already supports one level (`Car > Fuel`) and the PRD lists it as optional. The
editor does not offer it. Adding it touches the picker, the filters and every chart that groups
by category, so it is cheaper to decide now than to retrofit.

- **A — No, keep it flat.** *(Recommended: with categories you create yourself, "Fuel" and
  "Car maintenance" as two flat categories answer the same questions with less machinery.)*
- **B — Yes.** Then it lands as its own branch before the reports work starts.

**Answer:**

---

### D4 · Which two charts do you want first?

**Needed by:** the start of Phase 2. **Default while unanswered:** spending by category, then
income vs expenses.

The PRD lists six views (§4.3). Building all six at once is how a phase turns into a month, so
the first branch builds two, properly, and the rest follow once you have used them. Pick the
two questions you most want answered:

- Where does my money go? *(spending by category — donut or bars)*
- Am I saving or overspending? *(income vs expenses per month)*
- Am I trending up or down? *(balance over time)*
- Do weekends wreck me? *(spending by weekday)*
- How does this month compare? *(period comparison)*
- Is one category creeping up? *(single category over time)*

**Answer:**

---

### D5 · How do you want the parallel phase run?

**Needed by:** the moment Phase 1 exits — which is one branch away.
**Default while unanswered:** nothing starts; the work stays single-agent until you answer.

You have already said this decision is yours, and it is recorded that way. When the four exit
criteria in [`PLAN.md` Part B §2](PLAN.md#2-single-agent-phase-roadmap-phases-01) hold, the
question becomes concrete:

- How many agents at once? *(the plan's own advice is at most three, limited by how many PRs
  you can genuinely review in a day, not by the machine)*
- Plain `git worktree` by hand, or install Orca as the control panel?
- Do you want fixed-role agents (`android-implementer`, `test-writer`, `reviewer`,
  `db-migrator`) with restricted write access, or one general agent per branch?

**Answer:**

---

### D6 · What should happen when the review agent cannot run?

**Needed by:** any session that hits the limit. **Default while unanswered:** option A.

`/code-review` runs as a separate agent and costs session budget. Today it died mid-run on the
accounts branch because the limit was reached, and I reviewed my own diff instead and said so
in the PR. That is not the same thing as a second reader.

- **A — Self-review and label it.** Work continues; the PR says plainly that the review was not
  independent. *(Recommended, with a re-run when the limit resets.)*
- **B — Stop and wait.** The branch is pushed but no PR is opened until a real review has run.
- **C — Skip it.** Rely on CI and the tests alone.

**Answer:**

---

## Answered

| # | Question | Answer | Where it lives now |
|---|---|---|---|
| — | Does the app propose a starting set of categories? | No. It ships with none; every category is one the owner typed. | PRD §4.1, `DefaultDataSeeder` |
| — | Material or an iOS-flavoured UI? | iOS conventions, Material only as a base. | [`docs/decisions/0001-ios-flavored-ui.md`](docs/decisions/0001-ios-flavored-ui.md) |
| — | Who decides when the project goes multi-agent? | The owner, deliberately, when Phase 1 exits. | `PLAN.md` Part B §3, D5 above |
