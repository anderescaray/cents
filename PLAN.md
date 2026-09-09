# PLAN.md — From empty folder to running app

Operational guide. **Part A**: what to install and configure. **Part B**: how the project
is run, first solo and then with several agents in parallel.

Companion documents: [`CLAUDE.md`](CLAUDE.md) (technical rules) ·
[`docs/PRD.md`](docs/PRD.md) (what we build and why) ·
[`DECISIONS.md`](DECISIONS.md) (questions waiting on the owner).

---

# Part A — Setup

Follow the order. Each step has a verification: do not move on if it fails.

## A0. Name and package — DECIDED

| | |
|---|---|
| **Name** | Cents |
| **Repo** | <https://github.com/anderescaray/cents> (private) |
| **Package / applicationId** | `io.github.anderescaray.cents` |
| **Local path** | `C:\dev\cents` |

**What the package is:** the globally unique identifier of the app on Android — its ID
card. Three practical consequences:

1. Two apps with the same package cannot coexist on a device, and Google Play uses it as
   the app's primary key forever.
2. It defines the source folder structure:
   `app/src/main/java/io/github/anderescaray/cents/`.
3. **It is immutable once published.** It cannot be changed; you would have to publish a
   brand new app. That is why it is fixed before generating the project.

The convention is a reversed domain name (Gmail is `com.google.android.gm`). Without an
owned domain, the accepted standard is `io.github.<user>.<app>`: GitHub grants that
namespace and nobody else can claim it.

- [x] Name and package fixed

## A1. Get the project out of OneDrive

**Why this is mandatory:** Gradle creates and deletes thousands of temporary files on
every build. OneDrive tries to sync all of them, which causes extremely slow builds, files
locked mid-compilation and, in the worst case, a corrupted project.

```powershell
New-Item -ItemType Directory -Force C:\dev
Move-Item "C:\Users\ander\OneDrive\Desktop\mi app" C:\dev\cents
```

> ⚠️ **After moving the folder, the current Claude Code session points at a path that no
> longer exists.** Close Claude Code, open a new terminal in `C:\dev\cents` and launch it
> from there. The markdown files travel with you.

**Verify:** `ls C:\dev\cents` lists `CLAUDE.md`, `PLAN.md`, `README.md` and `docs/`.

- [x] Folder moved and Claude Code relaunched from the new path

## A2. Android Studio

It bundles the **Android SDK**, the **emulator**, `adb` and **its own JDK** — the JetBrains
Runtime, currently 25 — which solves the problem of the locally installed Java 11 being too
old for modern Android. Do not install a separate JDK.

```powershell
winget install --id Google.AndroidStudio
```

Real notes about this step:

- On first use, `winget` may ask you to accept the source agreements: answer `Y`.
- A UAC prompt asking for administrator rights may appear. That is expected.
- **The SDK download is not a terminal step.** `winget` installs the IDE; the SDK (~4-6
  GB) is downloaded by the graphical wizard the first time you open Android Studio. Open
  it, pick the **Standard** install, and accept the SDK licences. It takes a while.

**Verify** (in a **new** terminal, so it picks up the updated PATH):

```powershell
adb version
```

If `adb` is not found, add `C:\Users\ander\AppData\Local\Android\Sdk\platform-tools` to
the user PATH manually.

- [x] Android Studio installed and the Standard wizard completed
- [x] `adb version` responds

## A3. GitHub CLI

```powershell
winget install --id GitHub.cli
```

Then authenticate. **`gh auth login` is interactive** (it asks questions and opens a
browser), so run it yourself. From a Claude Code session you can prefix it with `!` so the
output lands in the conversation:

```
! gh auth login
```

Choose: `GitHub.com` → `HTTPS` → `Yes` (authenticate git with your credentials) →
`Login with a web browser`.

**Verify:** `gh auth status` reports `Logged in to github.com`.

- [x] `gh` installed and authenticated

## A4. Git identity

Without this, commits have no author.

```powershell
git config --global user.name "Ander"
git config --global user.email "anderescaray@gmail.com"
git config --global init.defaultBranch main
```

**Verify:** `git config --global --list` shows the three lines.

- [x] Identity configured

## A5. Wire the local folder to the repo

The repo already exists at <https://github.com/anderescaray/cents>. The local folder is
**not empty** (it holds the project documents), so it cannot be cloned over: git has to be
initialized here and linked to the remote.

From `C:\dev\cents`:

```powershell
git init
git branch -M main
git remote add origin https://github.com/anderescaray/cents.git
git add .
git commit -m "docs: add product spec, technical guidelines and setup plan"
```

Now pull whatever the remote already has (if you generated a README, a licence or a
`.gitignore` when creating the repo, they live there and must be integrated before
pushing):

```powershell
git fetch origin
if (git branch -r | Select-String "origin/main") { git rebase origin/main }
git push -u origin main
```

If the repo was created empty, the `if` does nothing and the push simply uploads your
commits.

> **Authentication:** no tokens or passwords needed. The `gh auth login` from step A3
> already authenticated git for HTTPS, so the push just works.

**Verify:** `git remote -v` points at `anderescaray/cents`, and `gh repo view --web` opens
the repo with the documents in it.

- [x] Folder linked to the repo and first commit pushed

### Before the first commit with code

There is nothing to ignore yet, but in Phase 0, as soon as Android Studio generates the
project, files appear that **must never be pushed**:

- `local.properties` — holds local paths and, later, your API keys
- `/build`, `/app/build`, `.gradle/` — build artifacts, hundreds of MB
- `*.keystore`, `*.jks` — app signing keys

Android Studio generates a correct `.gitignore` when it creates the project. Even so,
before the first commit containing code, check that `git status` does **not** list
`local.properties`. That is the most common way an API key leaks into a repository.

## A6. The phone as a test device

Testing on a real phone is not optional: emulators and `debug` builds **lie about
performance**, and the stated goal is an app that feels smooth.

1. Settings → *About phone* → tap **Build number** 7 times.
2. Settings → *Developer options* → enable **USB debugging**.
3. Plug in the cable and accept the *"Allow USB debugging?"* prompt on the phone.

**Verify:** `adb devices` lists your device as `device` (not `unauthorized`).

- [x] Phone detected by `adb`

## A7. What not to install yet

Stated explicitly, because the temptation is real:

| Tool | When | Why not now |
|---|---|---|
| Orca | Phase 2 | It orchestrates parallel agents over code that does not exist yet |
| Obsidian | Phase 2, optional | The repo markdown is already the source of truth |
| Pi | Probably never on this project | It is an alternative harness, not a complement (see Part B §4) |
| Market data API keys | Phase 4 | They do nothing until investments exist |
| Google Play account (€25) | Only if you decide to publish | Not a goal of this project |

---

# Part B — How the project is run

## 1. The rule that decides whether this goes well

**The bottleneck on this project is not how fast code gets generated: it is how much of it
you can understand and review.**

Ten agents in parallel producing Kotlin you cannot read do not give you an app; they give
you a ball of code you cannot fix when it breaks, and it will break. Parallel agents
multiply the volume of work arriving at your review. They do not multiply your judgement.

Hence the order of this plan: **build judgement first, scale second.** The single-agent
phase is not a toll or a beginner mode — it is where the data schema, the conventions and
the judgement that the parallel phase depends on are born.

## 2. Single-agent phase (roadmap phases 0–1)

**Goal:** an app installed on your phone where you can create accounts and categories, and
log income and expenses that actually persist.

**Why serially and not in parallel.** Everything that follows depends on three shared
things: the **database schema**, the **design system** (`ui/theme`) and the **dependency
catalog** (`libs.versions.toml`). If two agents write them simultaneously, each invents
its own version and you spend the day resolving merge conflicts instead of making
progress. They get written once, properly, one at a time.

**Order of work:**

1. Generate the project in Android Studio (*Empty Activity* with Compose) and confirm that
   `./gradlew installDebug` puts it on your phone.
2. `libs.versions.toml`, Hilt, detekt + Spotless, and **CI on GitHub Actions from day
   one**. CI is the only impartial judge you will have once several agents are running: if
   it is not set up before parallelizing, it never gets set up.
3. Room entities, DAOs and migrations, with their tests.
4. Theme, navigation and base components.
5. The core screens: accounts, categories, transaction entry, transaction list.

**Routine for every task, from day one:**

```
branch → implement → tests → ./gradlew detekt testDebugUnitTest → /code-review → PR → merge
```

**When `/code-review` cannot run** — it is a separate agent and it costs session budget, so
it does sometimes die mid-run — the answer is D6: review the diff yourself, **say in the PR
that the review was not independent**, and carry on. A blocked branch helps nobody, but an
unlabelled self-review quietly pretends to be a second reader, and that is the part that is
not allowed.

Even with a single agent. By the time there are five, the process is already worn in and
does not have to be invented under pressure.

**Exit criteria for this phase** — do not parallelize until all four hold:

- [x] The app installs and runs on your real phone
- [x] You can create accounts, categories and transactions, and they survive a restart
- [x] Balances are correct
- [x] CI is green on every PR (build + tests + lint)

**Closed 2026-08-21.** The fourth is the agent's own observation — every PR of Phase 1 went
green. **The first three are the owner's sign-off, not the agent's:** no device was attached
for the last four branches, so the app has been seen running only in tests. That is what these
three criteria exist for, and why they are the owner's to tick (C5).

## 3. Multi-agent phase (roadmap phases 2+)

Clear module boundaries now exist, and parallelizing starts to genuinely save time.

### 3.1 How work is split

The golden rule: **two tasks run in parallel only if they do not touch the same files.**
Check the overlap before splitting. If two tasks share a file, they run back to back.

A concrete Phase 2 split:

| Agent | Branch | Exclusive territory |
|---|---|---|
| A | `feat/reports-charts` | `ui/feature/reports/` |
| B | `feat/price-provider` | `data/remote/` |
| C | `feat/csv-export` | `data/repository/export/` + `ui/feature/settings/` |

Each in its own **git worktree**: a separate copy of the repo in its own folder, sharing
the same history. This avoids what breaks parallel work most often — two agents writing
the same files and fighting over the Gradle build directory.

```powershell
git worktree add ../cents-reports  -b feat/reports-charts
git worktree add ../cents-prices   -b feat/price-provider
```

And inside Claude Code, one managed worktree per session:

```
> work in a worktree on the reports charts
```

### 3.2 The orchestration pattern

```
                    ┌─────────────────────┐
                    │   YOU (the judge)   │
                    │  decide what lands  │
                    └──────────┬──────────┘
                               │ hand out non-overlapping tasks
              ┌────────────────┼────────────────┐
              ▼                ▼                ▼
        ┌──────────┐     ┌──────────┐     ┌──────────┐
        │ Agent A  │     │ Agent B  │     │ Agent C  │
        │ worktree │     │ worktree │     │ worktree │
        │ branch A │     │ branch B │     │ branch C │
        └────┬─────┘     └────┬─────┘     └────┬─────┘
             │                │                │
             └────────────────┼────────────────┘
                              ▼
                   ┌─────────────────────┐
                   │  CI (neutral judge) │
                   │ build + tests + lint│
                   └──────────┬──────────┘
                              ▼  /code-review per branch
                   ┌─────────────────────┐
                   │ main (squash merge) │
                   └─────────────────────┘
```

**The three roles that are never delegated to an agent:**

1. **Decomposing.** Splitting a phase into independent tasks is the real design work, and
   it is where you decide whether parallelism helps or produces conflicts.
2. **Arbitrating.** When two branches contradict each other, you decide. An agent cannot
   know which one fits what you have in your head.
3. **Accepting the merge.** Nothing enters `main` without you looking at it.

**House rules:**

- Every branch lives **less than a day**. Long branches diverge from `main` and turn into
  giant conflicts.
- After every merge into `main`, the other branches rebase onto `main` immediately. Small,
  frequent conflicts are cheap; large, rare ones are ruinous.
- **CI is the judge, not the agent's opinion.** If CI is red, the branch does not land — no
  matter how convinced the agent is that it works.
- **At most 3 parallel agents at first.** The real limit is not the machine: it is how many
  PRs you can genuinely review in a day.
- Changes to the Room schema, the theme or `libs.versions.toml` are **always done solo**,
  on their own branch, and merged before parallel work resumes.

### 3.3 Specializing agents

Once territory-based splitting works, the next level is defining fixed-role agents in
`.claude/agents/*.md`, each with its own prompt, tools and model:

- **`android-implementer`** — writes features following the rules in `CLAUDE.md`.
- **`test-writer`** — tests only, with write access restricted to `src/test/`.
- **`reviewer`** — read-only. Reviews diffs and cannot touch code.
- **`db-migrator`** — the only one allowed to touch the Room schema and its migrations.

That last part is what actually reduces accidents: not because of the prompt, but because
an agent that cannot write somewhere cannot break it.

### 3.4 Automating the guardrails

**Hooks** in `.claude/settings.json` run commands automatically at defined moments — for
example `spotlessApply` after every `.kt` edit, or `detekt` before every commit. The
advantage over asking for it in a prompt: a hook always runs, because the harness executes
it and it does not depend on an agent remembering.

## 4. The "pro" tooling, evaluated

### Orca — yes, but in Phase 2

[Orca](https://www.onorca.dev/) is an ADE (agent development environment) by Stably AI,
MIT-licensed and open source, which passed 24,000 GitHub stars and shipped its first
stable release in March 2026. It does exactly what you described: it runs dozens of
different CLI agents (Claude Code, Codex, Gemini CLI, OpenCode...) **in parallel, each in
its own git worktree**, from a single panel, with session tracking, token counting, and
help resolving conflicts when two agents touch the same thing. One agent can also delegate
to another, so phased flows are possible (planner → implementer → tester → you arbitrate).

**Verdict: the right tool for what you want, arriving at the wrong moment if installed
today.** Orca solves the problem of *coordinating many agents*. In Phase 0 the problem is
the opposite: there is no code to coordinate, and building the cockpit before you have
anything to fly is time that does not turn into an app. Once you reach Phase 2 with three
real workstreams and start feeling the friction of managing worktrees by hand, Orca saves
real work — and by then you will know what you are asking it for.

> **Superseded in part by D5 (2026-08-20).** The owner is not installing Orca: they are
> building **their own harness**, taking Orca's orchestration model together with the ideas
> in `xmihura/kanvas`, and running **at most 3 agents** at once. The reasoning above still
> holds for *when* — not before Phase 1 exits — and the ceiling of three still comes from
> review capacity rather than from the machine. What changes is the *what*: the control panel
> is written rather than installed.
>
> Two constraints this plan already implies, and that a home-grown orchestrator has to encode
> rather than assume, because it becomes the thing enforcing them:
>
> - **Two tasks may run side by side only if their write sets are disjoint** (§3.1). That is
>   what makes the schedule a DAG, and it is the whole safety argument for running agents at
>   once.
> - **The Room schema, `ui/theme` and `libs.versions.toml` are global locks.** Anything
>   touching them is a barrier: everything downstream waits, nothing runs beside it. An
>   orchestrator that does not model that will cheerfully hand three agents their own
>   migration.
>
> What stays off the DAG is what §3.2 already names: decomposing, arbitrating and accepting
> the merge. A scheduler can fan out and gate on green CI. It cannot decide which of two
> contradicting branches matches what the owner had in mind.

### Pi — no, and not because of quality

[Pi](https://pi.dev/) is Mario Zechner's minimal harness: a small hackable core,
extensions, skills, packages, and support for 15+ model providers. It is very good — in an
internal Databricks benchmark it had the highest pass rate of any harness tested on Opus
4.8, at significantly lower cost, because it sends roughly 3x less context per turn. There
is also [`pi-agent-harness`](https://github.com/baryonlabs/pi-agent-harness), a package
that turns one domain sentence into a team of specialist agents with their skills and
orchestration prompts.

**Verdict: not for this project, at least not now.** Pi is a *replacement* for Claude Code,
not an addition, and its value lies in reshaping the harness to fit you — something you can
only do well once you know what fit you want. Its own documentation frames it as being for
people who live in the terminal and want to study how coding agents are built. You are
about to learn Kotlin, Android, Compose, Room and Git simultaneously; adding "design my own
harness" to that list does not accelerate the app, it delays it. Save it for when the
project works and you feel like optimizing the process; it is an excellent choice then.

### Obsidian and the knowledge graph — useful, with one condition

The idea is sound: markdown notes linked with `[[wikilinks]]`, and a visual graph showing
how the project connects.

**The condition: no second source of truth.** If documentation lives in Obsidian and code
lives in the repo, within two weeks they contradict each other, and documentation that
contradicts the code is worse than no documentation.

**How to do it right:** open `C:\dev\cents\docs` as an Obsidian vault. Same files, version
controlled with git, read by the agents, and now with Obsidian's graph and navigation on
top. Zero duplication. One use where it genuinely pays off: keeping a **decision log**
(`docs/decisions/`), one short file per decision — why Kotlin and not Flutter, why money in
cents, why that price provider — linked to each other. Six months from now, when you cannot
remember why something is the way it is, that graph is what saves you. And it gives agents
the *why*, which is exactly what they cannot infer from the code.

### The tooling you already have and are not using

Before installing anything: the most powerful harness in your stack right now is the
combination of `CLAUDE.md` (rules loaded every session), **subagents** with restricted
permissions, **worktrees**, **hooks**, `/code-review` and **GitHub Actions**. All of it is
already available, free, and covers 90% of what an orchestrator is for. Orca adds the
control panel once volume justifies it; it does not replace any of the above.

## 5. Summary

| Phase | Agents | Your role | Done when |
|---|---|---|---|
| **0. Foundations** | 1 | Install, approve the skeleton | The app builds and installs on your phone |
| **1. Core** | 1 | Review every PR | You log transactions and balances add up |
| **2. Analytics** | 2–3, own harness (D5) | Decompose, arbitrate, accept merges | The charts answer your questions |
| **3–4. Accounts and investments** | 3 | Same, with more judgement | Transfers and a portfolio with live prices |
| **5–6. Automation and polish** | 2–3 | Decide what is worth building | You enjoy opening it every day |

---

# Part C — Where the project stands

**This section is the handoff between sessions. Whoever finishes a piece of work updates it
before stopping.** Everything else in this file describes how the project is run; this
describes what is actually built right now.

## C1. Start of session checklist

```powershell
git status                                   # anything half-finished?
gh pr list                                   # anything open waiting on review?
git checkout main; git pull --ff-only        # start from what is merged
./gradlew spotlessCheck detekt testDebugUnitTest   # green before touching anything
```

**Then read [`FEEDBACK.md`](FEEDBACK.md) and [`DECISIONS.md`](DECISIONS.md) before writing any
code.** Both are instructions rather than reading material:

- **`FEEDBACK.md` first.** Anything under *Open* is something the owner saw go wrong on a real
  phone, which is the one thing no test in this repo can catch. It **outranks the next planned
  branch**. Fix it, then move the entry to *Fixed* with the date and what was done.
- **`DECISIONS.md` next.** Anything answered since the last session is an instruction: act on
  it, then move that question to its *Answered* table with the date and what was done.

See C6.

Then take the first unchecked branch in C3 and work it end to end:
`branch → implement → tests → gates → PR → wait for CI → hand back`.

**Phase 1 closed on 2026-08-21 and C3 now lists Phase 2.** D9 is answered — three agents, on
Claude Code's own worktrees — and the first wave has landed. That is permission for *this*
phase, not a standing default: `CLAUDE.md` §7 still only allows parallelism across genuinely
disjoint files, and on 2026-08-30 the four remaining §4.3 views looked separable and were not.
Check the code before trusting a plan that says they are. **On 2026-09-03 the owner chose
serial anyway** for the views after that, so ask before forking.

**Where this session stopped (2026-09-09), second half.** With the phone not to hand, the owner
asked for investments — Phase 4 — to be started alongside the device walk waiting for review.
**D11 was asked and answered in the same session**, and settled four things: a fifth bubble and
tab, holdings grouped into stocks, ETFs, index funds and crypto, an investments account that
lives in the Accounts tab like any other, and manual prices now with a provider later.
`feat/investments-portfolio` builds all of it — Room v2 and **the project's first migration and
migration test**, `Quantity` as an exact integer, a cost basis that survives a partial sale, and
a portfolio that says *no price yet* rather than showing zero. 426 JVM tests, green twice on a
clean run.

**Two things it deliberately did not do.** A purchase does not yet take money out of the account:
that needs transfers, which are Phase 3, and which are now the next branch precisely because
D11's model needs them. And no price comes from the internet — **D13 asks which provider, because
it needs a key only the owner can create** (C5). Both are in C4 and in the C3 list.

**The investments section has its own walk, written after #43 merged.** It could not be written
while that branch was open — `docs/device-check.md` was being rewritten there, and a Walk 6
alongside it would have conflicted hunk for hunk (the stacking rule in C4). Once #43 landed, this
branch rebased onto it and added **Walk 6 (i1–i11)**: the empty states, the duplicate symbol, a
purchase typed with its fee, a price that has a date, **the average cost surviving a partial
sale** — the arithmetic everything else rests on — an oversized sale being refused, the headings,
deleting a holding, and the account that cannot be deleted because it holds investments. Walk 5
also gained its fifth bubble.

**Where this session stopped (2026-09-09), first half.** Everything C3 listed was merged,
`FEEDBACK.md` was empty and `DECISIONS.md` was empty. What was left was the walk that turns that
into knowledge. `docs/device-walk-phase-2` carried **the second run of
[`docs/device-check.md`](docs/device-check.md)**, written across two sessions and finished there:
the Phase 2 steps r9–r18 that were sitting uncommitted in the working copy, plus **r19** for the
year-on-year segment and a new **Walk 5 (b1–b10)** for the canvas — the two things that merged
*after* that text was written. It also fixed what the canvas made stale: the walk used to open
on a list, and step 01 is now the canvas, 02 goes through it, 07 asks whether reopening in the
last section reads right, and r18's "Back returns to Reports" is no longer true. Tab names in the
walk now say **Transactions**, which is what the bar says, rather than "Movements". Merged as
**#43**.

**The next step is the owner's, and it is the phone.** Nothing on the Reports tab, none of the
canvas and none of the portfolio has been seen on a device. The walk is written to be handed to
someone with the phone in their hand; what it turns up goes in `FEEDBACK.md`, and that becomes
the next branch.

**Where the session before it stopped (2026-09-08).** Four things landed, in this order.
`feat/reports-by-weekday` merged as **#39**, so **PRD §4.3 is complete and Phase 2's chart work
is done**. `fix/the-tab-bar-knows-where-you-are` merged as **#40**, closing the one case #31 left
open: a pushed screen now carries the tab it belongs to. `feat/bubble-launcher` merged as **#41**
and builds **D8** — the app opens on a canvas of bubbles, and the tab bar is what you use once
you are inside a section. It ships **ADR 0002**, because ADR 0001 specifies a tab bar and rule 12
makes that binding. And `feat/reports-year-on-year` merged as **#42**, closing the C4 gap that
said the period comparison had only half of what PRD §4.3 asks for. 369 JVM tests.

**Two agents ran side by side for the first time on 2026-09-08**, and not the way §3.1 describes:
the owner drove a second agent in *this* working copy rather than in a worktree, on
`docs/device-check.md`. It worked because the write sets did not overlap, but that was the split
being disjoint rather than the setup enforcing it — one index, one branch, and nothing but care
between them. Next time, `git worktree add`.

**Where the session before it stopped (2026-09-04).** `feat/reports-by-weekday` was built on
D10's answer **B** and was then waiting for review — the sixth and last PRD §4.3 view. `TransactionFilter`
grew a `weekday`, the DAO's filter query a clause that rotates SQLite's `strftime('%w')` onto
ISO day numbering, the Filters form a "Weekday" section in locale order, and the new view drills
each day row down to that weekday inside the period. Before it, the previous four branches all
merged: #34 (the ring shows income), #35 (reports narrowed to one account), #36 (this period
against the last) and #37 (one category over time). 317 JVM tests, green locally on a clean run.

**The next branch** is whatever `FEEDBACK.md` turns up on the walk above, or the deferred work
in C4 (the dashboard of cards in C7) — none of it decided. `DECISIONS.md` is empty.

Two things carried over that no test can settle, both in C4 below: **nobody has seen any of
Phase 2 on a phone**, and a branch should start from `main` rather than stacking on an
unmerged one.

## C2. What is built and merged

**Phase 1 is complete.** Everything in this table is on `main`, reviewed and green.

| Area | State |
|---|---|
| Toolchain | Gradle KTS + version catalog, Hilt + KSP, detekt (`dev.detekt` 2.x), Spotless/ktlint, GitHub Actions running build + tests + lint on every PR |
| Database | Room v1: `accounts`, `categories`, `transactions`. Balances computed in SQL, foreign keys `RESTRICT`, schema exported to `app/schemas/` |
| Domain | `Money` (exact minor units + currency), `Account`, `Category`, `Transaction`, repository interfaces with Room implementations |
| Design system | iOS-flavoured tokens and components, including a row that swipes sideways to delete and a search field — see [`docs/decisions/0001-ios-flavored-ui.md`](docs/decisions/0001-ios-flavored-ui.md) |
| Navigation | A canvas of bubbles the app opens on, five tabs underneath it, type-safe routes and push/pop transitions. A tab owns the screens pushed inside it, so the bar stays lit on the tab you are in and a slide travels the way you moved; a section is pushed on top of the canvas, so back from a section root returns there |
| Transactions | List grouped by day with a monthly summary, and one form that adds and edits: minor-unit keypad, date picker, note, and a delete that can be undone from the list. Searchable, and filterable by date range, account, category, kind and amount |
| Categories | The owner's own: nothing is seeded, and they are created, renamed, recoloured, dragged into order, archived and deleted from Settings → Categories — by opening one, or by swiping its row |
| Accounts | The tab is real: balances computed from the movements behind them, create, edit, archive, restore, and an account detail with its own month summary and movements. Deleting one that holds movements asks what should become of them — move them to another account, or delete them too — whether that is reached from the editor or by swiping the row |
| First run | **Nothing is seeded.** A fresh install has no accounts and no categories; every row in the app is one the owner typed |
| Reports | The tab reads the database: a ring that answers **where the money went or where it came from**, income against expenses per month, a balance line over the period, **spending by day of the week**, **this period against the one before it — or against the same period last year** — and **one category followed over twelve periods**, all under one period control and one account picker. A slice, a weekday row or the previous-period row opens the movements behind it. All six PRD §4.3 views exist |
| Investments | The fifth section: holdings grouped into stocks, ETFs, index funds and crypto, each with the trades behind it, an average cost that survives a partial sale, and what it is worth at a price typed by hand. Room v2, and the first migration this project has shipped |
| Tests | 426, all on the JVM (Robolectric), so CI covers the database and the UI without a device |

## C3. The work, branch by branch

One branch and one PR each, in order.

### Phase 1 — complete

Kept rather than deleted: each entry records what was actually built and what was found on the
way, which is the part that is expensive to rediscover. All merged, in this order.

- [x] **`feat/categories`** — **the owner's categories, created by the owner.** Merged. Requested
      2026-08-18, and first in the queue because the app currently imposes a taxonomy that
      contradicts PRD design principle 3.
      - The app **ships with no categories at all**. The first-run seeding of eleven
        default categories is removed from `DefaultDataSeeder`.
      - Full management from inside the app: create, rename, recolour and reorder
        categories, separately for income and for expenses.
      - **Delete** removes the category outright when nothing uses it
        (`CategoryDao.countTransactions` already answers this). When it does have
        transactions, "delete" means **archive**: it disappears from the pickers and the
        history it classified is left untouched (rule 8). The UI has to say which of the
        two happened, rather than silently doing something different from what the button
        said.
      - With no categories, the entry form's category section needs an empty state that
        leads straight to creating one, instead of an empty box.
      - *Done when:* a fresh install has zero categories, and every category in the app is
        one the owner typed. **Done**, plus: archived categories are no longer offered on
        the entry form (they were), a category list can be dragged into order, and the
        design system gained a text field, an alert, a back button and a shared modal bar.
      - **Still open for the owner:** the same argument applies to the seeded *Cash*
        account, which is still created on first run. It cannot simply be dropped —
        a transaction cannot be saved without an account — so removing it means adding a
        first-run step that asks for one account before anything else. Decide this when
        `feat/accounts` is planned.

- [x] **`feat/transaction-edit`** — tap a row to edit or delete it. Delete asks for
      confirmation and offers Undo (rule 7). Also add the two fields the entry form is
      missing: **date picker** (it is hardcoded to today) and **note input**.
      *Done when:* a wrong amount can be corrected and a mistake removed, without touching
      the database by hand. **Done.** Adding and editing are one screen
      (`ui/feature/transactionform`), the date picker is an iOS-style month grid that
      starts the week where the device locale says, and the undo lives in a banner on the
      list, fed by an in-memory buffer that is deliberately not persisted.
- [x] **`feat/accounts`** — the Accounts tab for real: list with balances, create, edit,
      archive, and an account detail showing only its movements plus its own income and
      expenses.
      *Done when:* balances shown match the transactions that produced them. **Done**, with
      three things worth knowing: an account's currency is fixed once it holds movements
      (they are already denominated in it), an account can open in the red for money owed
      on a card, and the accounts list shows **no total at all** when its accounts are in
      different currencies rather than adding them up at an invented rate.
- [x] **`feat/account-deletion`** — what the owner decided on 2026-08-20, applied to the
      Accounts tab that already exists. Two answers, one branch, because they meet at the
      same edge: with nothing seeded, deleting the last account becomes reachable.
      - **D1 — nothing is seeded.** `DefaultDataSeeder` stops creating *Cash*, so a fresh
        install has no accounts and no categories: everything in the app is something the
        owner typed. The class then seeds nothing at all and goes with it, along with its
        injection in `CentsApplication` and its test.
      - The movement form already leads to creating an account when there are none
        (`field_account_empty`), which is what makes removing the seed safe rather than a
        dead end. That path stops being an edge case and becomes the first run, so it is
        worth walking it on the phone before the PR.
      - **D7 — deleting an account that holds movements.** Today it can only be archived.
        The decision replaces that with a choice, because an account is not a category:
        `transactions.accountId` is `RESTRICT`, so a movement cannot be detached from it.
        - **No movements** → deleted outright, as now.
        - **Has movements** → an action sheet naming how many, offering **reassign** them
          to another account and then delete, or **delete the account and its movements**
          behind a second, explicit confirmation. Cancel is always there.
        - Reassign needs a destination. When this is the only account, that option appears
          disabled with a line saying why, rather than vanishing — an alert that changes
          shape for invisible reasons is worse than one that explains itself.
        - Both paths run inside one `withTransaction`: a half-finished reassignment would
          leave movements pointing at an account that is already gone.
      - **Archive stays**, unchanged and separate. Delete means gone; archive means keep
        the history and stop offering it.
      - New in the design system: an action sheet, which is how iOS asks a question with
        more than two answers.
      *Done when:* a fresh install starts empty and walks you into your first account, and
      an account with movements in it can be emptied or destroyed on purpose, never by
      accident. **Done.** Three things worth knowing beyond the plan above: reassignment is
      offered only to accounts in the **same currency**, because a movement keeps the
      currency it was recorded in and a balance sums its account's movements under that
      account's currency — moving euros into a dollar account would not convert them, it
      would silently make that balance a sum of two currencies. **Archive became its own
      row** rather than something "delete" fell back to, since delete now has a real answer
      for an account with history. And the design system gained a `CentsActionSheet`, which
      is how iOS asks a question with more than two answers. One thing found on the way: the
      account detail screen used to stay open on an account that had just been deleted from
      the editor it pushed to — a page with a fallback title, no balance and no way out but
      the back button. It now leaves by itself when its account is gone.

- [x] **`chore/single-currency`** — the app stops offering a choice it will never honour.
      Asked 2026-08-21, on reading the branch above: *"so you are preparing it for more than
      one currency? I'm only going to use EUR always."*
      - The account form had a **currency picker** offering EUR, USD, GBP, CHF and JPY. It
        was the only way to reach a state D2 says the app will never resolve: with two
        currencies in play the accounts list shows no total at all, movements cannot move
        between accounts, and no conversion is coming. Offering the choice was offering a
        trapdoor. It is now a **read-only row** showing the device's currency.
      - **What stays, and why it is not the same thing.** `Money` still carries an
        ISO-4217 code and still refuses to add euros to dollars (rule 2); the `currency`
        columns stay in the schema; the accounts list still shows no total when its accounts
        disagree; reassignment still only offers same-currency accounts. None of that is
        multi-currency *support* — it is a guard rail that costs nothing and catches a class
        of bug that would otherwise produce a confidently wrong number. Removing the columns
        would need a migration and buy nothing, which is the same argument that kept
        `parentId` after D3.
      - **An install that predates this keeps whatever it has.** An account created in
        dollars stays in dollars and its movements are left exactly as recorded — they are
        real amounts, and restating them as euros would be inventing a rate.
      *Done when:* the app never asks which currency, and never has to guess one. **Done.**
      Found on the way: a new account could be stored in the fallback `"EUR"` rather than the
      device's currency, because the currency was applied by a suspending `load()` and a form
      saved quickly enough was read before it arrived. Harmless while a picker could correct
      it; not harmless once the picker is gone. The currency is now resolved synchronously at
      construction, since it never needed the database — only the colour did.

- [x] **`feat/swipe-to-delete`** — swipe a row sideways to delete it, as an alternative to
      opening it and tapping Delete. Requested 2026-08-20.
      - A new `CentsSwipeableRow` in `ui/components`, following the iOS pattern: dragging
        left reveals a red **Delete** action, and releasing past a threshold performs it.
        Rule 7 still holds, and so does D7 — a swipe is a faster way to reach the same
        question, not a way to skip it.
      - It has to coexist with the vertical drag that reorders a category. The grip handle
        keeps the vertical gesture; the row body takes the horizontal one, so neither
        gesture steals the other's drag.
      - Applied to categories and to accounts in the same branch: one component wired into
        both lists at once, rather than written for one and retrofitted onto the other.
      *Done when:* a category and an account can each be deleted by swiping, and reordering
      still works with the same finger. **Done**, with four things worth knowing beyond the
      plan above.
      - **The question moved out of the editors before the swipe was wired to it.** Both
        deletion flows lived inside `CategoryEditViewModel` and `AccountEditViewModel`,
        reachable only from a screen that had loaded one row. Copying either into a list
        view model would have meant two implementations of "what does delete mean here",
        and the one thing that must not drift between them is exactly that. They are now
        `CategoryDeletion` and `AccountRemoval` — small state holders taking a repository
        and a scope, owning the count, the step machine and the writes — and both the
        editor and the list hold one. The alerts and the action sheet render that state and
        nothing else, so there is literally one sequence of confirmations in the app.
      - **The swipe ends in a question, never in a write.** A full swipe fires `onDelete`,
        which opens the same alert or action sheet the button opens, and the row closes
        itself immediately so the answer is given to the dialog rather than to a row left
        hanging open behind it. Deleting an account with its movements still asks twice.
      - **Two thresholds, because one is not enough.** Past half the revealed button the
        row parks open with **Delete** tappable; past 60% of the row's width, letting go
        performs it. An open row closes on a tap anywhere on it, which is what iOS does and
        what stops "I changed my mind" from opening the editor instead.
      - **TalkBack has no swipe to make**, so `CentsSwipeableRow` hands its content a
        modifier carrying a custom accessibility action, and the caller must put it on the
        row it builds. That is not decoration: the first attempt put the action on a
        wrapper around the row, and because `CentsListRow` merges its own semantics, the
        node a screen reader actually focuses never carried it. An action announced to
        nobody is not an action, and a unit test caught it. The mirror image cost a second
        fix — the revealed button is always composed, because its width is what the drag
        settles against, so every *closed* row was publishing a clickable "Delete" hidden
        only by the row sitting on top of it. TalkBack read one between every pair of rows,
        which is precisely the noise the custom action exists to avoid. It is now cleared
        from the semantics tree while closed, and there is a test that counts them.
      - **A deletion the database refuses is a one-button alert**, not a line in a footer.
        Both foreign keys are `RESTRICT`, so a row counted as free and then referenced
        before the confirmation lands turns a legitimate delete into a constraint violation.
        The first attempt reported it as a flag on the list's state rendered into a section
        footer, which was wrong three ways: it never appeared when every account was
        archived, an archived row's failure was reported under the *active* section's
        header, and nothing cleared the flag, so one refusal replaced the accounts total
        with an error message indefinitely. The failure now lives on the question that was
        being asked — `RemovalStep.FAILED`, `CategoryDeletionState.failed` — so it is shown
        by whichever screen asked, and it goes when the alert acknowledging it goes.
        `CentsAlert` gained an optional `dismissLabel` for it: with nothing to decide, a
        "Cancel" beside "OK" offers a choice that does not exist.

- [x] **`feat/transaction-filters`** — search and filters on the list: date range, account,
      category, kind, amount, note text.
      *Done when:* "what did I spend on restaurants in July" is answerable in the app.
      **Done** — that sentence is the name of a test in `TransactionFilterTest`. Five things
      worth knowing beyond the line above.
      - **The narrowing happens in SQLite**, in one query whose every clause is written
        `:x IS NULL OR column = :x`, so an unset filter costs nothing and the set ones use
        the indices already on `accountId`, `categoryId` and `date`. The same argument that
        puts chart aggregation in `GROUP BY` puts this there: the alternative is pulling
        every movement into Kotlin and walking it.
      - **The search text is escaped before it becomes a LIKE pattern.** `%` and `_` are
        wildcards, so an unescaped search for "50%" would quietly mean "50 followed by
        anything" — it now finds the note that says 50%. It matches the note, the category
        name and the account name, which is what makes typing "restaurants" work without
        opening the filter form at all.
      - **The filter lives in a `TransactionFilterStore`**, a singleton beside
        `DeletedTransactionBuffer`, because it is chosen on one screen and obeyed on another.
        Deliberately in memory only: a filter that survived a restart would be a saved view,
        and until that exists, reopening the app to a list quietly hiding most of the
        movements would read as data loss rather than as a filter still being on.
      - **The form edits a draft and writes it once, on Apply**, so Cancel means something
        and the query underneath does not churn while a half-built filter is assembled. It
        offers archived accounts and categories, because history recorded against them is
        still history. It does not own the search field — that stays on the list — so
        applying a filter never silently empties something visible behind it.
      - **A filtered list says what it found.** The month summary is replaced by a Matches
        section with the count and the totals of exactly the rows on screen, summed in Kotlin
        from the rows already in memory rather than by a second query that could disagree
        with the list above it. And "nothing matched" is a different empty state from "you
        have no movements", with a way out on it — the two look identical and mean opposite
        things.

**Phase 1 finished on 2026-08-21**, with the four exit criteria in Part B §2 signed off.

### Phase 2 — under way: understanding the money

PRD §4.3 lists six views. **The first branch builds two of them properly** (D4), because six
charts at once is how a phase turns into a month.

- [x] **`feat/reports-charts`** — merged (#20). The Reports tab reads the database: spending by
      category as a ring and income against expenses per month, in that order and nothing else
      (D4). One period governs both charts — week / month / quarter / year / custom — and
      `ReportPeriod` hands back a `TransactionFilter` rather than being a second idea of a
      period. Both aggregations are `GROUP BY` queries; Kotlin's share of the work is bounded
      by the size of the answer, not by how many movements exist. Uncategorized spending is its
      own slice rather than a dropped row, and empty months are zeros rather than gaps.
      **Vico is pinned to 3.2.3 rather than 3.3.0** — see the note at the end of `CLAUDE.md` §8.

- [x] **`feat/reports-drilldown`** — merged (#21). Tapping a slice of the ring, or its row
      underneath, narrows the transaction list to the movements behind it and switches to that
      tab, handing over the same `TransactionFilter` the filter form produces. Vico offers no
      hit testing for pie charts, so the ring tap reconstructs the geometry and a tap in the
      hole or off the ring deliberately does nothing.
      *Still not built:* tapping a **month column** to see that month's movements.

- [x] **`feat/reports-balance-over-time`** — merged (#28), and **this is the branch that made
      the rest of §4.3 parallelisable.** It builds the third view and the seam under it in one
      go, because they could not honestly be separated: before it, each remaining view would
      have touched the same seven files.
      A view is now an **additive unit**. It owns `domain/model/<View>.kt`,
      `domain/repository/reports/<View>Repository.kt`,
      `data/repository/reports/Room<View>Repository.kt`,
      `ui/feature/reports/section/<View>Section.kt`, its tests and its own `strings_<view>.xml`
      pair. Of shared wiring it touches **one line** in `RepositoryModule`, **two** in
      `ReportSections`, one appended `@Query` in `TransactionDao` and one line in the test
      factory. `ReportsViewModel` and `ReportsScreen` do not change at all.
      **A decision the owner should know he can overrule:** the balance line starts from the
      money carried into the period — opening balances plus everything before the first day —
      not from zero. It is the only version checkable by hand, since the last point of a line
      ending today equals the Accounts tab total, and a test asserts exactly that. Archived
      accounts count, which makes the line legitimately differ from that total when one holds a
      balance.

- [x] **`feat/the-ring-shows-income-too`** — merged (#34). The first thing the owner asked for
      after the second device walk, and it outranked the next planned branch (`FEEDBACK.md`). A segmented control
      above the ring picks the direction; the section's header, total and empty line follow it,
      because *Total spent* over income would be wrong rather than merely terse.
      **Both breakdowns are read at once** rather than one query re-run on each flip. The kind
      was already a parameter of `SpendingByCategoryRepository` and already travelled inside
      `SpendingBreakdown.filterFor`, so a slice of the income ring drills down to income with
      nothing added — the work was two more strings, a `combine`, and a toggle that survives
      being scrolled off a `LazyColumn`.

- [x] **`feat/reports-for-one-account`** — the second and last `FEEDBACK.md` item, and **the
      one branch of Phase 2 that was not additive**. A section source was asked for a period
      and a currency; an account is a third thing *every* view has to be asked about at once,
      because a ring showing one account beside a balance line showing all of them is a screen
      that contradicts itself. So the two parameters became one `ReportScope`, and the seam
      changed with them.
      Three things are worth knowing about it. **`ReportPeriod.asFilter()` is gone**, and
      `ReportScope.asFilter()` is the only way a report becomes a list — a filter built from
      the period alone carries the dates and silently drops the account, which is a drill-down
      that opens everyone's movements under a chart that added up one account's; deleting the
      method is what stops it being written by accident. **`observeBalanceBefore` narrows both
      halves together**: with an account chosen it carries in that account's opening balance
      *and* counts only its earlier movements, and a test asserts the line ends on the number
      the Accounts tab shows for that account. **Archived accounts are offered**, because the
      charts have always counted their movements — the opposite of the reassignment rule in C4,
      where hiding them was the point.
      The picker does not appear at all until there are two accounts.

- [x] **`feat/reports-period-comparison`** — the fourth PRD §4.3 view, and **the first one
      that cost the seam nothing**: a model, a repository interface, a Room implementation, one
      section file, its own strings pair, and single lines in `RepositoryModule`,
      `ReportSections` and the test factory. The only existing assertion that changed was the
      one in `ReportsViewModelTest` that names the sections in order, which is exactly what its
      comment said would happen.
      **A period that has not finished is compared against the same stretch of the one before
      it**, not against the whole of it. The tab opens on the whole calendar month, so on the
      third of September "this period" holds three days; against the whole of August that reads
      as spending down 95%, and it would read that way for about twenty-seven days out of every
      thirty. `comparedWith` clamps both sides and the section says so in its footer. It is the
      same flattery `previous()` exists to avoid, arrived at from the other side — and it was
      shipped and then caught in review, which is the argument for the review step.
      Two other things it decided. **The comparison is against a stretch of the same shape** —
      `ReportPeriod.previous()` — because a month against "the last thirty days" overlaps
      itself and a month against thirty days of the previous month flatters it; a calendar
      shape stays a calendar shape, so February's predecessor is January whatever their
      lengths. **The tinting is not "up is green"**: each row carries which direction is good,
      or a month of heavier spending would glow green.
      It also needed no new SQL. Two totals queries already existed, one for every account and
      one for a given account, so the repository picks between them — which means the narrowed
      case uses `index_transactions_accountId_date` instead of the non-sargable `OR` form the
      other three reports use (C4). That is what the split recorded in that gap looks like.

- [x] **`feat/reports-category-detail`** — merged (#37). The fifth PRD §4.3 view, and the first whose
      question does not fit inside the selected period: "is Restaurants creeping up" cannot be
      read off one month, and a line with one point is not a trend. **The period picks the
      bucket instead** — a month selected means the last twelve months, a week means the last
      twelve weeks — so the one-period rule still holds in the sense that matters, which is
      that nothing on the tab compares a month against a quarter. That argument lives on
      `CategoryTrend`, and this is the one view that reads outside the selection.
      **It is also the one section that holds a choice of its own.** Which category to watch
      belongs to the view, not to the tab, so `CategoryTrendSectionSource` keeps it rather than
      `ReportsViewModel` learning what a category detail is. The callback reaches the state as
      a property of that singleton — a stable instance, so two states carrying it still compare
      equal and Compose can skip the section. Being a singleton, the choice outlives leaving
      the tab and dies with the process, which is the intent.
      The change is measured against the **average** of the earlier buckets rather than the
      previous one, or a single quiet month would make the next read as a spike.
      Three things review caught and the branch fixes: **a category is summed in its own
      direction and movements against it are netted**, or an income category leads a list
      documented as "what is most spent on" and a pay rise renders red; **a running period cuts
      every bucket to the same elapsed days**, the same rule the comparison view follows one
      section up; and the axis label follows the bucket, because twelve years labelled as
      months all read "Jan".

- [x] **`feat/reports-by-weekday`** — the sixth and last PRD §4.3 view, on D10's answer **B**.
      Merged as #39. It was the one §4.3 view that was not purely
      additive: it needed the filter model to grow a concept it had never had.
      - **`TransactionFilter` gained a `weekday: DayOfWeek?`.** The DAO's one filter query got a
        clause — `((CAST(strftime('%w', t.date * 86400, 'unixepoch') AS INTEGER) + 6) % 7) + 1
        = :weekday` — that rotates SQLite's Sunday-0 numbering onto ISO Monday-1, so the
        parameter is just `DayOfWeek.getValue()` with no converter. `activeCount` counts it, so
        the Filters badge and the "show everything" path already cover it.
      - **The Filters form gained a "Weekday" section** — "Any day" plus the seven days in the
        locale's own order (`weekdays(locale)`, the same helper the date picker uses). That is
        the whole of B: a weekday bar drills down, and what it applied is visible and clearable
        beside every other filter rather than narrowing the list invisibly (the `r8` mistake).
      - **The view itself is additive**: `SpendingByWeekday` + its repository interface + Room
        impl + `SpendingByWeekdaySection` + its strings, one `@Binds`, one line in
        `ReportSections` (between `balance` and `comparison`, matching §4.3's order), one
        appended `@Query`, one line in the test factory. `ReportsViewModel` and `ReportsScreen`
        did not change.
      - **Expenses only, and the footer answers the question.** "Do weekends wreck me" is about
        the *typical* day, so the footer compares Saturday-and-Sunday-per-day against the other
        five-per-day (cross-multiplied, so rounding never decides a close call) rather than the
        raw two-against-five totals a weekend would always lose. The weekend is Saturday and
        Sunday flatly — both shipped locales mean that, and `WeekFields` has no portable notion
        of it; a locale that disagrees would revisit it, like the swipe's LTR assumption.
      - The model holds `days` in fixed Monday-first order so it is testable; the section
        reorders for display. The chart is one red series (the flow chart's expense colour),
        seven columns; per-column tinting was judged more Vico than the point was worth.

- [x] **`fix/the-tab-bar-knows-where-you-are`** — the last case #31 left open, and the tab-bar
      highlight that had the same cause. A screen pushed inside a tab carried no record of which
      tab that was, so two things read the top of the back stack and got the same wrong answer:
      the bar lit **Transactions** over an account's detail screen, and switching into a tab
      whose saved stack had a detail on top slid forwards.
      - **A tab now owns its screens.** `TopLevelTab.owns` lists what can only be reached from
        inside it — the filter form under Transactions, an account's detail under Accounts, the
        category list under Settings — and `routes` is the one list both the bar and the slide
        ask. Four nested nav graphs would answer the same question, at the price of registering
        every shared screen in each of them.
      - **A screen two tabs can reach is owned by neither, and that is the interesting half.**
        The movement form opens from the list and from an account; the account and category
        editors likewise. Naming an owner would pick one of two truths, so the bar reads the
        *nearest owned screen down the stack* instead: the form sits above an account's detail
        when it was opened from an account and directly above the list when it was opened from
        the list, so the same screen is filed under wherever it was actually opened.
      - **Remembering the last owned tab is the version this branch shipped first, and review
        caught it.** Each tab keeps its own saved stack, so leaving Accounts with a movement form
        open, visiting Reports and tapping Accounts again restores that form — and one remembered
        value would still have been saying *Reports*, a tab the owner had just left. The stack
        already knew; it also survives a rotation and process death with nothing saved beside it,
        so the saver went with the bug. There is a test that walks exactly that sequence.
      - What is not tested is `hasRoute` itself, which is androidx's — but a Robolectric test
        drives a real `NavHost` and pins the case that would break if the lookup only matched
        bare objects: `AccountDetail(1)` carries an argument, and it still resolves to Accounts.
      *Done when:* the bar names the tab you are actually in, and no switch slides the wrong way.
      **Done**, 330 tests.

- [x] **`feat/bubble-launcher`** — **D8, built.** The app opens on a canvas of four bubbles, one
      per section, dragged and pinched wherever the owner wants them, drifting gently, and
      expanding into the section when tapped. The tab bar is not replaced: it is what moves you
      between sections once you are in one, and it collapses to a grabber. It ships
      [`docs/decisions/0002-bubble-launcher.md`](docs/decisions/0002-bubble-launcher.md), because
      ADR 0001 specifies a tab bar and rule 12 makes that binding.
      - **The canvas is the start destination**, so every section is pushed on top of it and back
        from a section root returns there — collapsed or not, because a gesture that means two
        things depending on a toggle is worse than a toggle that only hides a bar. That also
        fixed something older: back after a drill-down from Reports used to leave the app,
        because the movements list was the floor of the stack.
      - **A layout is fractions, not pixels** — of the canvas for the position, of its *shorter
        side* for the diameter, so turning the phone leaves a bubble the size it was in the hand
        rather than taller than the screen. Anything that no longer fits is shrunk or pushed
        inside when it is drawn, without rewriting what was stored, so a bubble that fell off a
        small screen comes back where it was on the large one.
      - **Only what was moved is stored.** A section with nothing saved sits where a fresh
        install puts it, so a future section needs no migration and a half-written bubble reads
        as unplaced rather than as a position with invented parts.
      - **Accessibility is the part that is not free**, and it is why the branch is not a coat of
        paint: bubbles are buttons with names and an explicit traversal order, the drift is never
        *started* when the device has animations off (an invisible animation is still one the
        device asked not to run), a tap then opens the section immediately instead of growing
        into it, and Settings carries **Reset the bubble layout** — because no gesture-free way
        to move a bubble exists, and a screen reader cannot drag one.
      - **DataStore arrives**, which `CLAUDE.md` §2 has named as the preferences layer since the
        beginning and nothing had needed until now. It is bound in its own `PreferencesModule`
        rather than in `RepositoryModule`: none of it goes near Room, and a module per storage
        mechanism is one fewer file that two branches both have to edit.
      - **What review caught was mostly about the canvas being the first screen.** An
        unreadable preferences file used to be a blank screen and a crash on *every* launch with
        no way into the app, so nothing in the layout store throws any more: a read that fails is
        a canvas nobody has arranged, a write that fails leaves the bubble where it was, and a
        corrupt file is replaced rather than thrown from. The drift was recomposing all four
        bubbles sixty times a second — modifiers, shadow and face — because the offset was read
        in composition rather than in the layout lambda it was already using. The canvas ignored
        system bar insets, so the first default bubble sat under the status bar clock and one
        dragged to the bottom handed its drag to the gesture bar. A second tap during an
        expansion moved the shape to another bubble and still arrived at the first. And the
        reset now asks first (rule 7), because an arrangement made by hand can only be rebuilt by
        hand.
      *Done when:* the app opens on the canvas, a bubble stays where it was put across a restart,
      and every section is still reachable without a gesture. **Merged as #41**, 362 tests.

- [x] **`feat/reports-year-on-year`** — merged (#42), and it closes a C4 gap rather than opening a
      view: PRD §4.3 asks for "this month vs last **and** vs the same month last year", and only
      the first half existed. A segmented control above the comparison picks which, on the shape
      the ring's Expense/Income toggle established, and both sides are queried at once so the
      toggle answers instantly.
      The arithmetic is where a year-on-year comparison goes wrong, and both traps are held: a
      **running period is clamped on both sides** — the first nine days of this month against the
      first nine days of the same month last year, not against the whole of it, which is the same
      mistake `comparedWith` was already built to avoid one period back — and a **calendar shape
      stays a calendar shape across a leap year**, so February against February rather than
      February against twenty-eight days of an earlier March. The drill-down row follows the
      segment, so tapping it under "same period last year" opens last year's range.

- [x] **`docs/device-walk-phase-2`** — the walk, not a feature. It finishes the second run of
      [`docs/device-check.md`](docs/device-check.md), which had been written up to r18 in a
      working copy and never committed, and brings it level with what merged afterwards: **r19**
      for the year-on-year segment, and **Walk 5 (b1–b10)** for the canvas — drag, pinch,
      rotation, the expansion, the tab bar's grabber, back, reset, and the two steps that need a
      device setting and are therefore the owner's (animations off, TalkBack).
      It also repairs what #41 made stale, which is the part worth noticing: **the walk used to
      open on a list and the app now opens on the canvas**, so step 01 was checking a screen that
      is no longer first, and r18 asserted that back from a drill-down returns to Reports when it
      now returns to the canvas. A device walk that is wrong about the app is worse than no walk,
      because a correct app reads as a failure. Tab names were also brought to what the bar
      actually says — **Transactions**, not "Movements".
      *Done when:* the owner can walk the app end to end from this file without knowing the code.

### Phase 4 — begun early, deliberately: what the money is invested in

**Out of roadmap order, and that was the owner's call** (2026-09-09). Phase 3 — transfers,
recurring rules, export — is unbuilt, and `CLAUDE.md` §9 says not to jump a phase without
agreeing on it first. The agreement is D11. What it costs is written into the branch list below:
the owner's model of investments *needs* a transfer, so Phase 3's first piece arrives inside
Phase 4 rather than being skipped.

- [x] **`feat/investments-portfolio`** — the portfolio, at manual prices. **D11 answered the four
      questions it needed**: a fifth bubble and tab, holdings grouped by asset class, an
      investments account that lives in the Accounts tab like any other, and prices typed by hand
      until a provider arrives.
      - **Room v2, and the first migration this project has ever shipped.** Three tables —
        `holdings`, `trades`, `price_quotes` — purely additive, with `CentsDatabaseMigrationTest`
        building a *real* v1 database from the exported `1.json`, putting money in it, migrating,
        and checking both that the money survived and that Room agrees to open what came out.
        That test is the pattern every later schema change follows (rule 9), and it closes the C4
        gap that said no migration test existed.
      - **A quantity is an exact integer too.** `Quantity` holds hundred-millionths of a unit,
        because half a share and 0.00000001 BTC are both real, and a `Double` holding either is a
        portfolio that does not reconcile with the broker's own app. Multiplying one by a price
        goes through `BigInteger`: the product overflows a `Long` long before either factor is
        unreasonable, and overflowing silently would render a negative portfolio.
      - **A cost basis is a fold, not a `GROUP BY`** — the one aggregation in this app that is
        not pushed into SQLite, because a sale removes cost *at the average paid so far*, so each
        trade has to be applied to the result of the one before it. It is bounded by how many
        times one instrument has been traded. The basis is reduced **proportionally** rather than
        by a rounded average, so selling everything leaves exactly zero rather than four cents.
      - **A price is stored against the symbol, not the holding.** The same instrument held in
        two accounts cannot be worth two different things. Every quote carries the date it was
        *true* — a price read on Friday and typed on Sunday is Friday's — and every screen shows
        that age, because until a provider lands the whole portfolio rests on it.
      - **A holding with no price says so, and is left out of the total**, which then says how
        many it left out. A portfolio showing 0,00 € for something nobody has priced is worse
        than one that admits it does not know: the first is a number, and numbers get believed.
      - **The canvas has a fifth bubble**, so the default arrangement was re-laid out and the
        default diameter is slightly smaller — five at the old size could not be both clear of
        each other and whole when the phone is turned on its side. It changes nothing for a
        bubble already moved: only unplaced ones are laid out from the defaults.
      - Deleting a holding takes its trades with it, in one transaction, behind a confirmation
        that says how many go — the bargain D7 struck for accounts, applied to the same shape of
        problem. A duplicate symbol in one account is refused by a unique index *and* by the
        form, so the owner gets a reason instead of a constraint violation.
      - **What review caught was mostly about money that is not there.** A total over a portfolio
        nobody has priced folded to 0,00 € and rendered it — the failure a single unpriced row
        already refused to commit, one level up; a total is now null until something in it has a
        price, and profit is measured against what the *priced* half cost. A sale bigger than the
        position clamped its quantity but not its money, booking proceeds for units that were
        never held and calling the result *realised*. A quote is shared by symbol, so one stored
        in another currency reached `Money`'s refusal to add euros to dollars from inside the
        portfolio's `combine`, which would have taken the whole tab down rather than mispricing
        one row — a mismatch now reads as no price.
      - **And one thing this branch broke elsewhere.** `holdings.accountId` is `RESTRICT`, but
        the account-deletion flow only ever counted movements: an account holding investments and
        no movements took the "nothing lives in it" path, hit the constraint, and reported *"try
        again"* — advice that could never work. Deleting such an account is now refused with a
        reason, which is a third answer rather than a variation on D7's two: a movement can be
        moved to another account, a holding cannot, and nothing here is going to delete somebody's
        recorded trades as a side effect of tidying an account list.
      - The holding editor also learned three things: archived accounts are listed (a holding does
        not stop existing because its account was put away, and the currency used to fall through
        to a hard-coded default), the duplicate check runs one query at a time so a stale answer
        cannot clear the warning, and a write the database refuses becomes an alert rather than an
        exception escaping the view model.
      - It also carries **Walk 6 of the device check** (`i1`–`i11`), added after #43 merged and
        this branch rebased onto it, so the portfolio can be walked on the phone in the same pass
        as the Reports tab and the canvas.
      *Done when:* what is held can be recorded, priced by hand, and read back with its profit.
      **Done**, 426 tests.

- [ ] **`feat/transfers`** — Phase 3's first piece, needed here: **money has to be able to reach
      the investments account**. A transfer is a paired outflow and inflow linked by one
      `transferId`, excluded from every income and expense report (PRD §3) — get that
      wrong and every statistic in the app inflates. Schema v3, a migration and its test.
      *Done when:* moving 500 € from Checking to Broker changes both balances and no chart.

- [ ] **`feat/buying-spends-the-cash`** — what D11 actually described, finished. A purchase takes
      its cost out of the account's cash and a sale puts it back, using the same exclusion the
      transfer above establishes, so an investments account's balance reads as **uninvested cash**
      and the portfolio value sits beside it rather than inside it.
      *Done when:* transfer 1.000 €, buy 400 € of NVDA, and the account shows 600 € of cash
      beside a 400 € position.

- [ ] **`feat/price-provider`** — prices from the internet, behind the `PriceProvider` interface
      PRD §5.1 makes mandatory, with the cache and the age of a quote already built.
      **Blocked on D13**: it needs an account and a key, and only the owner can create one (C5).

**D9 is answered: three agents, on Claude Code's own worktrees** (2026-08-30). The first wave
ran three at once and all three landed — #26, #27 and #28, with no file touched by two of them.
What that wave actually taught is in C4 rather than here, because the lesson was not about
parallelism.

## C4. Known gaps, so they are not mistaken for bugs

- **Installs made before `feat/categories` still hold the eleven categories the app used
  to seed.** Nothing removes them automatically, because deleting rows that may already
  classify movements is the owner's call: they can be deleted or archived one by one in
  Settings → Categories. A fresh install has none.
- **Category subcategories are not built, and will not be** (D3, 2026-08-20: the list stays
  flat). `parentId` stays in the schema unused rather than being migrated away — removing a
  column costs a migration and buys nothing. **Icons** are still unbuilt but undecided: the
  schema has `iconKey` and the editor does not write it. One
  consequence to fix when subcategories arrive: deleting a category runs a bare `DELETE`,
  and the self-referencing foreign key is `RESTRICT`, so deleting a parent that still has
  children would throw out of the view model with nothing catching it. Unreachable today
  because nothing can create a child.
- **All six PRD §4.3 views exist**, `feat/reports-by-weekday` (#39) last. The weekday
  filter it added is the odd one on the form: every other filter narrows to a stretch or a set,
  a weekday narrows to every Saturday inside whatever range is already set. It also assumes the
  weekend is Saturday and Sunday, which both shipped locales agree on.
- **The weekday chart is expenses only.** "Do weekends wreck me" is not asked of income, and the
  ring one section up already toggles direction. If income-by-weekday is ever wanted it is a
  second query on the shape the ring's toggle established, not a redesign.
- **The trend view reads outside the selected period, on purpose.** Every other view reports on
  the period and nothing else; this one shows the twelve periods ending with it, because a
  trend needs history. The rule it keeps is that the period decides the bucket, so no two
  points anywhere on the tab are different lengths of time. Anything later that assumes "a
  report only ever touches the selected range" has to account for this one.
- **An account holding investments cannot be deleted at all.** Not archived-instead, not
  reassigned: refused, with a message naming how many holdings are in the way. A movement can be
  moved to another account and a holding cannot, and deleting one would take its recorded trades
  with it — a decision that belongs to whoever is looking at the portfolio. The fix, when it is
  wanted, is a fourth answer in the removal sheet, not a quiet cascade.
- **A quote stored in a currency the holding does not use reads as no price.** It is the honest
  answer without a conversion rate (D12) and it cannot arrive at all on a single-currency install;
  what it means in practice is that an install predating D2 could show *no price yet* for
  something it does have a price for, until a new one is typed.
- **Buying does not spend anything yet.** D11 describes money transferred into an investments
  account and then allocated to a holding; this branch built the second half. A purchase records
  what was bought and at what price, and the account's balance does not move — because moving it
  needs the transfer machinery, which is Phase 3 and is the next branch. Until then the portfolio
  and the balances are two true statements that do not add up to net worth.
- **No conversion, so a dollar holding is entered in euros.** Every holding is created in its
  account's currency (D2), and the portfolio totals per currency rather than across them. PRD
  §4.5 says investments are where conversion has to come back; D12 is the question, and
  the column is already on the row, so answering it costs no migration.
- **A fully sold holding disappears from the portfolio.** It has no quantity, so it has nothing
  to be worth, and listing it would put a row worth 0,00 € under a heading about what is held.
  What it made is still recorded and still reachable — but only while some of it is held, which
  means realised profit on a closed position is currently write-only. A "closed positions"
  section is the fix when somebody misses it.
- **The portfolio is rebuilt in Kotlin whenever anything changes.** One query for holdings, one
  for *every* trade, one for every price, combined and folded. It is bounded by how many times
  the owner has traded rather than by how long they have used the app, and it is the honest shape
  for an aggregation SQL cannot express (see `positionFrom`). If a portfolio ever gets slow, the
  split is one query per holding's totals plus the fold only where a sale exists.
- **Nothing checks that a symbol is real.** "NVDAA" is accepted and will simply never be priced.
  Validating it means asking a provider, which is D13's branch.
- **A trade carries no note in the form.** The column exists and the model carries it; the form
  does not offer it, because a trade is already four fields and the note had no obvious question
  to answer. It costs one row the day somebody misses it.
- **Do not branch off an unmerged branch when the merge will be a squash.** `main` squash-merges,
  so a stacked branch carries commits whose content arrives on `main` as one different commit,
  and rebasing then conflicts on every hunk. It happened on 2026-09-03 and the fix was to rebuild
  the branch as a single commit on the merged `main` — `git checkout -B <branch> origin/main`
  followed by `git checkout <old tip> -- .`, which works only because the stacked tree is a strict
  superset. Cheaper to wait for the merge.
- **Nobody has seen Phase 2 — or the canvas — on a phone.** Every view on the Reports tab is
  verified by JVM tests and by nothing else: the ring's Expense/Income toggle, the account picker
  above the charts, the comparison columns and both its segments, and the trend line have never
  been rendered on a device. Neither has the bubble canvas, which is worse in kind rather than in
  degree — it is the app's front door and it is made of drag and pinch gestures, which is exactly
  what a Robolectric test can least honestly claim to have checked. **The walk itself is no longer
  the gap**: `docs/device-check.md` now covers all of it (r1–r19 and Walk 5), and what is missing
  is a phone and an hour. On 2026-09-03 the phone reached adb once as `device` but was asleep, and
  every later attempt saw it absent or `offline` — over USB and over wireless debugging, whose
  mDNS advertised nothing. Screenshots need it awake and unlocked, and unlocking is the owner's
  (C5).
- **A report's idea of "today" is fixed when its flow is built.** The period comparison and
  the trend both read the clock once, when the scope changes, so an app left open across
  midnight keeps yesterday's clamp — a movement dated today is left out of "this period" until
  the flow is rebuilt. The tab's flows are `WhileSubscribed(5s)`, so leaving the tab for five
  seconds is enough to refresh it, and the same root cause is the "nothing invalidates the
  screen at midnight" gap further down this list. Fixing it properly is one midnight tick that
  every screen reads, not a clock re-read per view.
- **The watched category is app-scoped and forgotten on restart.** It lives in a `@Singleton`
  section source, so it survives leaving the tab and dies with the process. Persisting it is a
  DataStore key, and it was not worth one until someone misses it.
- **A report built from two flows can render one frame of a half-updated answer.** The balance
  line and the period comparison each `combine` two queries, and both files used to claim that
  this made the pair atomic. It does not: `combine` pairs the newest value of one flow with the
  *last* value of the other, so one write that touches both halves — moving a movement from
  July to August, say — briefly renders it counted twice or not at all before the second
  emission lands. It is transient, self-correcting and cosmetic. Making it impossible needs one
  query returning both halves, which for the comparison would cost the index it currently gets
  to use. Written down rather than traded away for a frame.
- ~~**The period comparison has no year-on-year mode.**~~ **Built in feat-reports-year-on-year.**
  PRD §4.3 asks "this month vs last / vs the same month last year", and both halves are now built.
  A segmented control toggles between "Previous period" and "Same period last year" on the shape
  the ring's Expense/Income toggle established. Both segments are queried concurrently (`combine`
  in `PeriodComparisonSectionSource`), keeping toggling instantaneous. Running periods are clamped
  on both sides (the first N days of this period against the first N days of last year's counterpart),
  calendar shapes are preserved (including leap years), and the drill-down row navigates to the
  year-ago range.
- **The Reports account picker is an inline list, not a sheet.** With a handful of accounts it
  reads at a glance and needs no tap to say what the tab is narrowed to, which is why it was
  chosen; with fifteen it would push the first chart off the screen. It hides itself entirely
  below two accounts. If the list ever gets long, the fix is a row that opens a picker, not a
  scrollable box inside a scrolling screen.
- **The chosen account does not survive closing the app**, the same way filters do not and for
  the same reason: remembering it is a saved-view feature with a name and a list, not a flag.
- **Narrowing a report to one account does not use the index that exists for it.** The four
  report queries take the account as `:accountId IS NULL OR t.accountId = :accountId`, and an
  `OR` over a bound parameter is not sargable — SQLite cannot use
  `index_transactions_accountId_date` and falls back to the index on `date`, testing the
  account row by row. The alternative is two `@Query`s per report that have to be kept saying
  the same thing, which is a duplication bug waiting to happen in exchange for a saving nobody
  has measured on a phone. The work is still bounded by the length of the period. If a device
  walk ever finds the Reports tab slow with a long history, this is the first thing to split —
  and `CLAUDE.md` §5 is why it is written down rather than left to be discovered.
- **A weekday filter is evaluated with `strftime` per row.** When `weekday` is set the list
  query computes the day of the week for every candidate row rather than reading an index — no
  index on a weekday exists, and one would be a stored column and a migration. The date range is
  still applied first where one is set, so it is bounded by the period the same way the account
  `OR` above is. Same trigger for revisiting: a device walk finding the filtered list slow.
- **Any change to any account restarts every chart query.** The tab combines the selection with
  the account list and `flatMapLatest`es the result, so renaming an account or archiving one
  re-runs the three aggregations. It predates the picker — the currency was already read from
  that list — and it is bounded work on a handful of rows, but it is why the account list is
  not the place to hang anything expensive.
- **The way back from a section is a fade, not the expansion in reverse.** A section does not
  know which bubble it came from, and teaching it would mean threading a rectangle through
  navigation for one animation. Going in is the continuous shape D8 asked for; coming out is a
  fade, and nobody has said whether that is enough.
- **There is no gesture-free way to move or resize a bubble.** TalkBack can open one — they are
  buttons with names and an explicit traversal order — but dragging is a drag. Settings →
  **Reset the bubble layout** is the escape hatch, chosen over per-bubble "nudge" actions because
  it is the one that also rescues a bubble pinched down to nothing in a corner.
- **Bubbles may overlap once they are dragged**, and nothing stops it: pushing them apart would
  mean fighting the finger. Only the *default* arrangement is held clear, and a test measures it.
- **The reduce-motion setting is read once per composition, not observed.** Turning animations
  off while the app is open leaves the drift running until the next launch. Observing it is a
  `ContentObserver` for a setting that changes about once a year.
- **The canvas reopens the section that was last open**, which is what D8 asked for and may not
  be what it feels like in the hand: it means the app usually does *not* open on the bubbles. It
  is one write and one flag to change to "always open on the canvas" if that reads better on the
  phone.
- **The drift is a live animation for as long as the canvas is on screen.** It is four
  interpolations and it stops with the composition, but it is the first thing in this app that
  runs when nobody is touching it — worth a look if the battery figures ever get examined.
- **Which tab a screen lives in is a hand-kept list.** `fix/the-tab-bar-knows-where-you-are`
  closed the gap #31 left — a pushed screen now carries the tab it belongs to, so the bar stays
  lit on Accounts over an account's detail and switching into that saved stack slides backwards
  — but it does so because `TopLevelTab.owns` says which screens are inside which tab, and
  nothing makes a new destination declare itself. One that does not is treated like the three
  screens two tabs can reach: no owner, so the nearest owned screen underneath it answers
  instead. That is the right answer for those three and a harmless one for a forgotten route,
  which is why it is a list rather than four nested nav graphs — those would answer the same
  question by duplicating every shared screen per tab.
- **A green CI run is one machine, one ordering, one run.** `main` was green on CI and red
  locally on 2026-09-02: nine of the ten view-model test classes let their view model outlive
  the test, so a leaked collector kept using a `Dispatchers.Main` the *next* class was trying
  to install. The failure always landed on a class that had done nothing wrong, and which class
  that was depended on the order JUnit happened to run them in. #30 fixed it in one place, but
  the habit it argues for is the point: run the suite more than once, and read
  `test-results/*.xml` rather than `BUILD SUCCESSFUL`. Three of the four branches before it hit
  some version of this and each fixed only the file it owned.
- **Installs made before `feat/account-deletion` still hold the seeded *Cash* account.**
  Nothing removes it automatically, for the same reason the eleven seeded categories were
  left alone: it may already hold movements, and deciding what happens to those is the
  owner's. It can now be deleted from inside the app, whichever way is wanted. A fresh
  install has no accounts at all.
- **Deleting an account together with its movements cannot be undone.** It is the one
  operation in the app that removes recorded history on purpose (D7), it asks twice and
  names how many movements go, and after that they are gone — no undo banner, no recycle
  bin. Reassigning is the path that keeps everything.
- **Reassignment only offers active accounts in the same currency.** Archived ones are not
  offered as a destination, because "where did my movements go?" should not be answered by
  a row that is hidden from the list. If the only same-currency account is archived, the
  option shows disabled with the reason, and restoring that account makes it available.
- **No transfers, recurring rules or investments.** Those are PRD phases 3 and 4, and the
  schema has no tables for them yet: they arrive with a tested migration.
- **The app works in one currency, and no longer offers a choice** (D2, and
  `chore/single-currency`). The device's currency is used, shown read-only on the account
  form. `Money` still carries a code and still refuses to add euros to dollars, the schema
  still has the columns, and the accounts list still shows no total when its accounts
  disagree — those are guard rails, not a feature, and they cost nothing. **What is not
  built, and is not coming, is conversion.** If a second currency ever becomes real, that is
  a new question with a rate and an "approximately" behind it, not a gap to quietly fill.
- **Phase 4 will reopen this, and that is expected.** A broker account holding US stocks is
  priced in dollars whatever the owner spends in, so investments need a rate anyway. That is
  a different problem from "an account you spend from", and it gets designed then rather
  than half-supported now.
- **View-model tests race Room unless their waits name what they expect.** Several build the
  view model in `setUp`, before the test inserts its rows, so the flows it collects emit in
  stages — two inserts are two emissions. A wait like `awaitStateThat { it.categories
  .isNotEmpty() }` can therefore pass while only the first row has arrived, and the
  assertion after it reads a list that has not settled. Worse, `awaitStateThat { !it.isNew }`
  waits for nothing at all: `isNew` is already false at construction for an existing row.
  Both patterns cost a green CI run on 2026-08-21 and were fixed by having each wait name
  the state it expects. **Write the wait as the condition you are about to assert on**, not
  as a proxy for it. It happened again the same day, on CI only: a wait for `name == "Salary"`
  stood in for "the names of this kind have been reloaded", which is a *different* collector's
  emission — green on every local run, red on the slower runner. The rule holds without
  exception, and the failure mode is always a machine you are not testing on.
- **Swiping does not close whatever other row was left open.** Each `CentsSwipeableRow`
  owns its own offset, so two rows can sit open at once. It is cosmetic — a tap on either
  closes it, and any deletion closes the row that asked — and coordinating them needs a
  controller shared across the list, which is more machinery than the annoyance is worth
  today.
- **The archived category section does not swipe; the archived account section does.** They
  are not inconsistent by accident: what "delete" means for an account is the same question
  wherever it is listed, while an archived category with history behind it cannot be deleted
  at all (rule 8), so a swipe there would have to do nothing and explain why. The editor
  still offers delete for an archived category that nothing uses.
- **The swipe assumes a left-to-right layout.** Dragging left reveals the action, with no
  mirroring for RTL. The app ships English and Spanish, both left-to-right; a right-to-left
  translation would have to revisit it.
- **A deletion the database refuses reports itself and stops there.** Both foreign keys are
  `RESTRICT`, so a row counted as free and then referenced before the confirmation lands turns
  a legitimate delete into a constraint violation. The account path always caught that; the
  category path did not, and an exception escaping the view-model scope would have taken the
  app with it. Both now end in a one-button alert instead, and nothing is retried
  automatically — the owner asks again if they still want to.
- **Search folds case for ASCII only.** SQLite's `LIKE` lowercases A–Z and nothing else, so
  searching "cafe" does not find "Café" and vice versa. Fixing it properly means storing a
  normalised copy of the searchable text, which is a schema change and a migration; it is
  recorded here rather than half-done.
- **Filters do not survive closing the app**, by design — see the note in C3. If they should,
  that is a saved-views feature with a name and a list, not a flag.
- **The date row the form shows ticked is held, not worked out from the dates.** Deriving it
  reads as tidier and is a trap that cost a fix on this branch: "between two dates" becomes
  unreachable the moment any preset has been chosen, because the range it produced keeps
  reading back as that preset and the two calendars never appear. A pristine form opened on
  the last day of a month walked into the same wall. **When a choice and its consequence can
  both be observed, store the choice.**
- **The search field is fed from the text as typed, not from the applied filter**, with a
  250 ms debounce before the query runs. A value-based text field showing text that has been
  to SQLite and back visibly drops characters, and every keystroke would otherwise cancel a
  running full-table `LIKE` and start another.
- **The amount filter is typed in minor units**, the way the movement keypad is: 2500 means
  25,00. The section footer says the range back in real money so the two cannot be confused,
  but it is a text field rather than the keypad the movement form uses.
- **A filter narrows the Transactions tab only.** The account detail screen has its own list
  and ignores it, which is the intent — that screen is already filtered, by the account it is
  about.
- **Only schema v1 exists**, so there is no migration test yet. The first schema change must
  ship one (rule 9).
- **An undone deletion comes back with a new `createdAt`.** The row id, amount, date, note
  and classification are restored exactly; only the internal bookkeeping timestamp moves,
  and nothing in the app reads it. It would take a repository method that carries the
  original instant to preserve it.
- **The undo window is six seconds, counted by the buffer itself and held in memory.**
  Closing the app forgets the deletion. That is the intent: an undo that survives a
  restart is a recycle bin, which is a different feature.
- **Nothing invalidates the screen at midnight.** "Today" and "Yesterday" come from the
  injected clock when the state is built, so an app left open across midnight keeps
  yesterday's labels until something else refreshes it.

## C5. What only the owner can do

The agent may install the APK, launch the app, read logcat and take screenshots — nothing
else on the phone (rule 13). So these are always handed back, and
[`docs/device-check.md`](docs/device-check.md) is the walk that collects them:

- Tapping through a flow to judge whether it feels right.
- Anything needing a device setting, an unlock, or a permission dialog.
- Reviewing and merging every PR.

## C6. The two files that pass work between owner and agent

**[`FEEDBACK.md`](FEEDBACK.md) runs owner → agent**, and holds what the owner found on the
phone. It exists because of the hole this project has had since the beginning: the agent can
build, install, read logcat and screenshot, but it cannot use the app. It cannot tell that a
swipe fires too early, that a balance is off by two cents, or that a screen is confusing.
Before it existed, that kind of thing was said once in a chat and then lost. Anything under
*Open* there is fixed before the next planned branch, and moves to *Fixed* when it is.

**[`DECISIONS.md`](DECISIONS.md) runs agent → owner**, one entry per decision, each with what is
at stake, the options, a recommendation and a default. It exists because the two documents
either side of it answer different things: `PLAN.md` records what *is*, `docs/decisions/`
records what *was decided and why*, and neither had a place for *what has not been decided
yet* — so those questions used to end up buried in a pull request description, where they were
read once and then lost.

**The protocol for `DECISIONS.md`, in both directions:**

- **Agent → owner.** When a choice would change the app rather than the code — what it ships
  with, what it is for, how it is built as a project — do not guess and do not stop. Write the
  question in `DECISIONS.md` with a **default**, build everything the answer does not touch,
  and say in the handoff that it is waiting.
- **Owner → agent.** Answers are written on the `**Answer:**` line, in the file, in any form.
  They do not have to be long: "A", or "no subcategories", is enough.
- **Next session.** Read the file first (C1). For each newly answered question: do the work it
  implies, then move the row to the *Answered* table at the bottom with the date and what was
  actually done. An answer that turns out to shape the architecture graduates into an ADR in
  `docs/decisions/`, and the table points at it.

A question with no answer is never a reason to stall: the default is what the app does until
the owner says otherwise, and that default is written down next to the question so nobody has
to guess what "unanswered" currently means.

## C7. Requested, and where it lands

Things the owner has asked for that are real and wanted, recorded here so they are neither
lost nor smuggled into a branch that was supposed to be about something else.

### Bubbles instead of a tab bar

Requested 2026-08-20, decided the same day in D8. Instead of four fixed tabs, the app opens
on a canvas of **four bubbles** — one per section — draggable anywhere, resizable, each
drifting with a small continuous motion.

> **Built on 2026-09-08, on `feat/bubble-launcher`.** The wait was deliberate and it was the
> right order: it amends the ADR that governs navigation, and every chart screen was built
> inside that navigation, so doing it first would have meant building the charts against a shell
> that was about to change. What it actually shipped is in C3; what it decided is in
> [`docs/decisions/0002-bubble-launcher.md`](docs/decisions/0002-bubble-launcher.md).

**Yes, after Phase 1 exits, and as a launcher rather than a replacement.** The shape:

- The canvas is the **home screen**. Tapping a bubble **expands it to fill the screen in
  under 500 ms** — the bubble becomes the section rather than being replaced by it, so the
  transition is one continuous shape and not a cross-fade.
- **Inside a section the bottom tab bar shows by default** and can be collapsed. Collapsed,
  back returns to the canvas. Every screen already built keeps working untouched, which is
  exactly what makes the launcher model affordable and the full replacement expensive.
- **State survives restart and process death**: bubble positions and sizes, whether the tab
  bar is collapsed, and which section was last open. That is a DataStore schema plus a rule
  for what a saved position means on a screen it no longer fits.

What it costs, so the branch is sized honestly rather than as a coat of paint:

- It **amends a decision already taken**:
  [`docs/decisions/0001-ios-flavored-ui.md`](docs/decisions/0001-ios-flavored-ui.md)
  specifies a bottom tab bar, and `CLAUDE.md` rule 12 makes that ADR binding before any
  composable is written. So the branch **ships ADR 0002** saying what changed and why.
  Leaving the two to contradict each other is how the next session ends up guessing.
- **Accessibility stops being free.** A tab bar tells TalkBack "tab 2 of 4" without being
  asked. A canvas of draggable circles has no reading order at all, so it has to be given
  one explicitly, and a continuously moving target is harder to hit for anyone with a motor
  impairment. The drift has to honour the system "reduce motion" setting.

### A customisable dashboard for the charts

Decided in D4, alongside which two charts come first. Phase 2 builds **spending by category**
and **income vs expenses per month**, properly, and that is the whole of the first reports
branch — six charts at once is how a phase turns into a month.

The longer-term shape is a **dashboard of cards**: every chart and KPI from PRD §4.3
available as a card, reorderable and resizable, filtered smoothly by date range or month,
and staying fast while doing it. Deliberately not the first branch, and deliberately noted
next to the bubbles: it is the same interaction — drag, resize, persist a layout — and
building that twice in two places would be the waste. Whichever lands first should leave
behind something the other reuses.

Two things to hold onto when it arrives, both already project rules rather than new
constraints: chart aggregation belongs in SQL `GROUP BY`, not in a Kotlin loop over thousands
of rows, and a resizable grid of live charts is exactly the screen where a missing `key` or a
non-skippable `UiState` turns into visible jank.

---

## Next concrete action

**Walk the app on the phone, with [`docs/device-check.md`](docs/device-check.md) open.** That is
the whole of it, and it is the owner's (C5): every branch of Phase 2 and the canvas in front of
it were built with no device attached, so the only thing standing between "the tests are green"
and "the app works" is an hour with the phone in hand.

```bash
adb devices                  # awake, unlocked, authorised
./gradlew installDebug
```

Walk 1 (`01`–`07`) is the exit criteria and runs in order. Walk 4 (`r1`–`r19`) is the Reports
tab, and Walk 5 (`b1`–`b10`) is the canvas — those two are the ones nobody has seen. Anything
that goes wrong goes in [`FEEDBACK.md`](FEEDBACK.md) with its number, and that is the next
branch: an *Open* entry there outranks anything planned.

**If the walk is clean**, nothing is waiting: `DECISIONS.md` is empty, C3 has no unchecked
branch, and what is left is deferred work nobody has chosen yet — the dashboard of cards in C7,
or any of the gaps in C4. Choosing between them is the owner's, and Phase 3 (transfers,
recurring rules) is the roadmap's own answer.

**Two questions the walk should settle**, both written into it rather than left to be
remembered:

- **`07`** — the app reopens on the section that was last open, not on the canvas, so the
  bubbles are usually not what you see on launch. That is what D8 asked for; whether it is what
  it should do is a thing only the hand can say. It is one flag either way.
- **`b3`/`b8`** — whether a bubble can be pinched or dragged somewhere unusable, and whether
  **Reset the bubble layout** is a real escape hatch or a thing you would never find in time.
