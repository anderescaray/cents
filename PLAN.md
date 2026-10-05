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

> **Settled by D24 (2026-09-21): Orca after all.** The home-built harness described below was
> never finished, and the parallel phase now runs on Orca, with Devin, Cursor and Gemini as
> implementers and Claude Code splitting the work and reviewing. How it runs is in C1 and
> [`AGENTS.md`](AGENTS.md). The two constraints in the note below still hold, and Orca does not
> enforce either one, so the task prompts and the reviewer do.
>
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

**Then [`docs/next-improvements.md`](docs/next-improvements.md)**, before choosing what to
build: nine requests the owner confirmed on 2026-10-04, and the order to build them in. It is a
backlog, and nothing in it is built yet. Take the next branch from it rather than from C3's older
plans. `FEEDBACK.md` still outranks it.

**2026-10-05, evening: #111, #109 and #110 are merged; #113 and #114 are open.**
- #109 had its `/code-review high`: 10 findings, 8 fixed in it with regression tests, the ninth
  (starting words unreachable) in #113, the tenth (docs edited on a feature branch) checked
  harmless. The owner merged it while CI could not run.
- **CI could not run for about an hour** (from ~19:30): GitHub Actions had an open incident
  assigning hosted runners ("The job was not acquired by Runner of type hosted"). It was not the
  account's minutes: about 128 used in October and 919 in September, against GitHub Free's 2 000.
  Runs cancelled by it, `main`'s for #109 included, have to be re-run once the incident clears.
- **#113** `feat/capture-safety-and-confirmations`: the uncommitted capture work, rebuilt on #109,
  plus the owner's two requests (ignore words, *Try a notification*), the notification on by
  default, and the `****7146 €23` bug fixed. 987 tests locally.
- **#114** `chore/remove-app-lock`: D31. 937 tests locally. It and #113 touch the same lines of
  `MainActivity`, so the second to merge is rebased. After #114, `CLAUDE.md`'s dependency table
  and §8 still describe the lock and need this branch's treatment.
- The 2026-10-04 local work is also kept as `9de8d5d` on the local, unpushed
  `fix/capture-safety-and-feedback`. Once #113 and #114 merge, that branch can go.

**2026-10-05: item 7 is merged, ahead of its turn (#112), and #109 has `main` merged in.** The
owner asked for the balance first. #112 adds Room **v10** (`balance_adjustments`). It was merged
without the `/code-review high` §7 asks for, so that is owed after the fact. The phone check is
owed too. #109 conflicted with it on the account screen's rows; `main` was merged into
`feat/ui-rework` (no force-push), #112's new screens took the rework's colours, badge and
full-screen list, and the gates are green there (947 tests). The owner also asked for a
notification on every capture and for editable amount detection: both are recorded in
`docs/next-improvements.md`, under *Requested on 2026-10-05*.

**Where the 2026-10-04 session stopped: the backlog is written down (#110), and three things come
before it.** In this order:
1. **#109, the UI rework (`feat/ui-rework`), is still open** and still owes its
   `/code-review high` (§7). The backlog is written against its screens, so it lands first.
2. **Uncommitted work sits in `C:\dev\cents`**, on `fix/capture-safety-and-feedback` (cut from
   #108). It holds safer amount parsing in capture, an optional notification for each capture,
   and the app lock **removed**. It is in no commit and on no remote, so it exists only on that
   PC: commit it to its branch before anything else happens in that folder.
   - **It shares 19 files with #109**: the capture settings, screen and view model, `CentsApp`,
     `SettingsScreen`, both strings files, and `AppLockGate`, which #109 edits and this deletes.
     Once #109 merges, this has to be rebased by hand.
   - **Removing the lock goes back on PRD §4.7 and on D16**, which turned SQLCipher down because
     the lock was the protection being built. `DECISIONS.md` has no entry for it yet. Add one
     with the owner's answer before it ships.
   - **Its copies of `docs/next-improvements.md` and of the `CLAUDE.md` link are #110's**, line
     endings apart: drop them from that work. Its `AGENTS.md` adds two notes beyond #110's lines
     (Spotless on Windows, Hilt entry-point getter names), and those are worth keeping.
3. **Then the backlog**, `fix/transaction-entry-ux` first, as its table orders.

#110 changed only Markdown, and its CI still failed three times. The owner read the blocked merge
as a conflict, and it was not one: a race in `CaptureViewModelsTest`, fixed in #111 (C4).
**A red check on a PR that touches no code is a test problem, not a conflict.** Read the failing
test in the CI log before re-running anything.

Then take the first unchecked branch in C3 and work it end to end:
`branch → implement → tests → gates → PR → wait for CI → hand back`.

**Phase 1 closed on 2026-08-21 and C3 now lists Phase 2.** D9 is answered — three agents, on
Claude Code's own worktrees — and the first wave has landed. That is permission for *this*
phase, not a standing default: `CLAUDE.md` §7 still only allows parallelism across genuinely
disjoint files, and on 2026-08-30 the four remaining §4.3 views looked separable and were not.
Check the code before trusting a plan that says they are. **On 2026-09-03 the owner chose
serial anyway** for the views after that. **On 2026-09-21 D24 turned parallel work back on, this
time through Orca**, so how it runs now is described in the next paragraph.

**Where the 2026-10-01 session stopped: the rework, and the first walk in weeks.** Local, with the
tablet and then the Pixel attached.
- **Walked first:** the Drive backup end to end on the tablet — `n7`, `n8`, `n9` (a real restore
  with subscriptions), `f1` (tapping a column), budgets `g1`–`g4`. All passed. The owner wants
  deleting one Drive copy at a time, later (C7).
- **Then the owner asked for the UI rework** (D30): everything, after the film, not iOS. Built on
  `feat/ui-rework`, seen on the tablet screen by screen and fixed as it went:
  - tokens, cards, chips, the ring, money that counts, a floating capsule with **+**;
  - **Home** for recording and filing, with the month's left/overspent as its headline;
  - the movement and transfer forms as sheets: amount, category chips, one button;
  - budgets in Reports; **Reports › Customize** (order and hide, DataStore); Reports' ring is
    Home's; Wallet is *What you have*, investments behind *Invested*;
  - the bubbles, their layout store and the reset row deleted; ADR 0002 superseded, 0003 accepted;
  - **capture's words per app** (amends D29).
- **Bugs found on the tablet and fixed:** rows let the swipe's red layer through; the card fade
  and a size animation clipped shadows into hard lines, and the fade replayed on every return.
- **The Pixel has the build**, fresh: the owner is clearing the tablet's fake Drive copies
  (Drive › Manage apps › Cents › *Delete hidden app data*) and starting real data there.
- **Not done:** the PR's `/code-review high` (§7), and Walk 19 (capture with a real bank
  notification), which is the owner's next step on the Pixel.

**Where the 2026-09-27 session stopped: income and expenses, reshaped.** A cloud session, with
no Android SDK, so ktlint 1.8.0 and detekt 2.0.0-alpha.6 ran as CLIs and CI is the gate for
the rest. The owner asked for the chart to show what was saved, and to choose its columns.
- **The income-and-expenses view on Reports has three controls of its own.**
  - *In and out* or *Saved*: income beside expenses, or one column of what was left, green when
    something was left and red when more went out than came in.
  - *Months*, *Quarters* or *Years* for each column, and **6, 12 or 24** columns. The default is
    12 months.
  - Like the category trend, it reads **outside the selected period**. The last column is the
    one the period ends in, cut where the period ends, and the rest run back from there. Size
    and count are held in the source, in memory, like the trend's category.
- **Tapping a column describes it under the chart**: its income, its expenses, and *Saved* or
  *Overspent* in words, with *Show movements* to open them. The latest column is described until
  another is tapped. Vico only reports taps to a marker controller when the chart has a marker,
  so the chart carries one that draws nothing. The tapped column is drawn in full and the rest
  faded. **The tap is new and has not been seen on a phone.** It is also not reachable with
  TalkBack: only the latest column's figures are.
- The owner turned down an earlier version of this branch with **a row per month under the
  chart**, because it grows long at 24 columns.
- Quarters and years are summed in Kotlin from the same monthly `GROUP BY` the view always ran,
  bounded by the months on the chart. `MonthlyFlow` became `FlowBucket` and `IncomeAndExpenses`,
  and `filterFor` lives on the model as it does for every other view.
- **A bug on the way:** a filtered list's totals left out transfer legs but **counted the cash
  of a share purchase or sale** as an expense or income. They now use `movesNoIncomeOrExpense`,
  like every report, so a column opened from the chart adds up to what it shows.
- The *Net* row under *This month* on the movement list is unchanged. Rewording it and showing
  what was saved at the top of Reports belong to the UI rework (C7, #99).
- **Then postponed charges in budgets** (the owner's pick). *Committed this month* and budgets
  now count a moved occurrence in the month it moved to. Before, rent moved from the 30th to
  the 2nd stayed in the month it left, and a charge moved from the 20th to the 27th, looked at
  on the 25th, counted nowhere. `subscriptionTotalsOf` and `budgetProgressOf` take the
  postponements, and D28's last point is updated.
- **2026-09-29: an edited rule drops the moves it no longer fits.** The review of #102 found a
  pre-existing gap: after a move, changing a rule's frequency or start date could leave the move
  pointing at a day the rule no longer has, still steering the generator, the budget and
  *committed this month*. `updateRule` now checks every move against the saved rule.

**Where the 2026-09-25 session stopped: groups are built.** The owner asked for a way to see
what an occasion cost — a trip, say — across categories, and then for income to count as fully
as expenses. It went into the PRD as §4.9 and D27 (#85), and was built the same day, with the
phone still out of reach:
- **#86** is the data: Room **v8**, a `movement_groups` table (not `groups`, which is an SQLite
  keyword) and a nullable `transactions.groupId`, `SET NULL` on delete. `8.json` was written by
  hand like `7.json`, with its hash from the same reconstructed algorithm, and CI accepted it.
  Group totals exclude transfer legs and trade cash, like every report. Backups carry groups; the
  CSV gains a trailing `group` column.
- **#87** is the screens: Settings › Groups, a group's screen (what it cost or earned, in words,
  a ring of where it went, and drill-downs into the list), the editor, a *Group* picker on the
  movement form (covering groups offered first, never chosen), and a group filter.
- **On the way, a real bug:** editing a movement a subscription wrote dropped its `ruleId`,
  because the form rebuilt the movement without it. The form now keeps it.
- **Walk 13** (`h1`–`h6`) is the phone check for groups.
- **Then the uncategorized inbox** (#90, PRD §4.6), the owner's choice once groups were in: a
  *To categorize* row on the movement list counting what has no category, and a screen where each
  one is filed with one tap on a category chip. No schema change; the count, the list and the
  Reports *Uncategorized* slice share one predicate, which leaves out transfer legs and trade
  cash, and filing is one `UPDATE` that refuses them. **Walk 14** (`i1`–`i3`) checks it.
- **Then a `/code-review high` of everything merged without one** — budgets, groups, the inbox —
  and its eight findings fixed in **#92**: a budget counting last month's unanswered variable bill
  (now only this month's; the Subscriptions screen still counts all, as money owed); a hard-coded
  EUR fallback replaced by `deviceCurrency()`; the restore alert naming budgets and groups; the
  inbox's filing refusing a movement already categorized or a category of the other kind;
  deleting a group clearing a list filter on it; undo no longer crashing when the movement's
  group, rule or category was deleted in the window; and two efficiency fixes. **Lesson:** the
  review §7 asks for is not optional because the owner is merging fast — run it before saying a
  PR is ready.
- **#93**: search ignores accents as well as case (*cafe* finds *Café*, *ñ* matches *Ñ*), the
  owner's request. Matched in the repository after SQL narrows by everything else, so it needed
  no schema change after all.
- **#95**: filtering the list to *Income* or *Expenses* leaves out transfer legs and trade cash,
  which closes the C4 gap. The owner asked whether a transfer shows anywhere as money leaving one
  account; it does, in every balance and in the Reports balance line with one account chosen, and
  that is unchanged.
- **#96**: the accessibility pass (Phase 6). The shared components gained TalkBack roles,
  headings, rows read as one item, *selected* on picker rows, 48 dp targets through a new
  `Modifier.tappable`, and minimum heights instead of fixed ones so text grows with the system
  font. **Walk 16** (`k1`–`k3`) checks it. The font test needed Robolectric's **native graphics**,
  since the default mode measures text with metrics that ignore font size. Also, a dialog reads its
  font scale from the device rather than from a CompositionLocal, so an alert's growth is only
  checked on the phone.
- **#97**: **postponing** (D28). A rule's next occurrence, or a variable bill or salary already
  waiting, can be moved 1–14 days, never as far as the next one. It is recorded on the new day and
  announced that morning; the rule's schedule does not move. Room **v9** (`rule_postponements`),
  carried in backups. A variable salary's notification now asks what came in. **Walk 17**
  (`p1`–`p3`). **Walk 15** (`j1`) checks #95.
- **Two requests for later** went into C7 (#94): choosing the order of the charts and hiding any
  of them, and hiding a tab that is rarely used. Both belong to the UI/UX pass that replaces the
  bubbles.

**Where the 2026-09-24 session stopped: the phone has the build, and Drive is connected.**
Nothing was built. The session untangled a local merge, installed `main` on the phone, and got
the Drive backup connected:
- **The local checkout had merged `main` into the stale handoff branch**, after the web sessions
  moved on. The merge was aborted rather than resolved, because every commit on that branch was
  already in `main` as #70. Every merged branch, local and remote, was deleted, and so were the
  second wave's Orca worktrees.
- **The first local build since the web sessions** passed every gate and regenerated #80's
  hand-written `7.json` byte for byte. It was installed on the development tablet, an Honor (`HEY3`), and
  opened on the owner's data, so **`MIGRATION_6_7` has now run on a real phone.**
- **Drive failed with an authorization error, because there was no Android OAuth client.** The
  setup below says the owner had done it, but only part of it had been done. The owner created
  the client on 2026-09-24, with the debug SHA-1 recorded below, and the switch now reads
  connected. No code changed: the app carries no client ID, and Google matches it by package
  and certificate.
- **The walk itself has not started.** `n6` went as far as *connected*. Everything else in
  walks 4 to 12 is still unwalked, and the owner means to walk it in the next session (see
  *Next concrete action*).

**Where the 2026-09-23 session stopped, second half: budgets, and the bubbles on notice.**
After the second wave the owner chose **budgets** (PRD §4.4, Phase 5), with the phone still out
of reach.
- **#80** is the table: Room **v7**, `MIGRATION_6_7` and its tests, and budgets carried in
  every backup.
  - Its `7.json` was **written by hand**, because the container still could not reach
    `dl.google.com`. The `identityHash` uses Room's own algorithm, reconstructed and first
    checked by reproducing the hashes of versions 1–6 exactly.
  - CI and the migration test accepted it.
  - If a local build ever rewrites the file, the diff is the thing to look at.
- **#81** is the screen, Settings › Budgets: one row per budget, with what is left, what made
  it up and a bar that goes amber at 80 % and red over 100 %; and an editor with the keypad.
  - "Spent" is the Reports ring's own SQL.
  - "To come" is the Subscriptions screen's own *committed this month*, per category.
- **D26** records every choice the PRD left open, each with the default that was built.
- On the way, **#78** fixed a trade-form test that read the table before its save had landed;
  it turned main red after #76. **#79** fixed the delete confirmation's *"undo it from the
  list"*.
- **The owner does not much like the bubbles**, and said they will be changed later. Until then
  **no time goes into them**: not into the canvas, and not into walking `b1`–`b10`. What the
  replacement is will be the owner's call when the time comes. The C7 entry says so.

**Where this session stopped (2026-09-23), first half: the second wave, finished by one agent.** The owner
ran this session with Claude Code alone, no Orca, so the coordinator also implemented. Seven PRs
merged:
- **#69, the automatic backup to Drive** (Devin's branch). The nine findings of its 2026-09-22
  review were fixed on the branch here, not by Devin:
  - a stale token is renewed silently before anything asks the owner to reconnect;
  - pruning can no longer fail a backup that is already uploaded;
  - retention keeps copies by age (7 days, 4 weeks, 6 months), and a ledger identical to the
    newest copy is not uploaded again;
  - a cancelled Play services task is a failure rather than a hang;
  - a full Drive has its own message and no retry loop;
  - a listing survives a file without a size;
  - backup times say *Today* / *Yesterday* like the rest of the app.
- **#71, undo from the account detail.** Cursor's branch had failed with nothing committed and
  the work only on the owner's PC, so it was rebuilt from the original prompt. `UndoBanner` now
  lives in `ui/components`.
- **#72, subscriptions in every backup.** #69's own description named it: the export left out
  rules, reminders and `ruleId`. Worse, a restore on any phone with a subscription was refused,
  because `recurring_rules.accountId` is `RESTRICT`. Both are fixed. A rule keeps its
  `lastGenerated` watermark through a restore, so nothing is written twice.
- **#73, the two #68 follow-ups.** A trade's note reaches its cash movement, and a note on a list
  row stops at two lines.
- **#74, the restore alert says to export first.**
- **One test came in racy with #71**: the account balance and its movements are two flows, and the
  transfer-undo test asserted one on the state where the other had moved. It was fixed in #72
  and #73 on their first CI run.
- **#76, the midnight tick.** It was first deferred, because it touches four view models and
  their tests in a session that could not run them. Then the owner asked for it, and it was
  built with CI as the gate. How it works is in C4.
- **This session's container could not build the app.** It could not reach `dl.google.com`, so
  it had no Android SDK and no Google Maven. What ran locally was ktlint 1.8.0 and detekt
  2.0.0-alpha.6 as CLIs, with the repo's config, over all of `app/src`, and kotlinc plus JUnit for
  the pure-JVM files (the Drive client, retention, the backup format). CI was the gate for the
  rest. A cloud session that should build needs `dl.google.com` allowed in its environment's
  network settings.

**Where the session before it stopped (2026-09-21): the first Orca wave.** The owner moved the parallel
phase onto [Orca](https://www.onorca.dev/), and **D24** fixed which tool does what. **Devin**
(Devin CLI) is the main implementer. **Cursor** (`cursor-agent`) is the second, for UI work that
stays inside one feature folder. **Gemini CLI** takes small, well-scoped branches. **Claude
Code** splits the work, writes the task prompts, reviews every PR, and is **the only one that
edits the shared documents**, in one docs branch after the merges. The cap of three implementers
stands. The ground rules for the non-Claude agents are in [`AGENTS.md`](AGENTS.md), which
`GEMINI.md` imports. `.worktreeinclude` copies `local.properties` into every Orca worktree, and
long paths are enabled on the machine (`core.longpaths` in git, `LongPathsEnabled` in Windows),
because a Gradle build under `~\orca\workspaces\cents\<branch>` goes past 260 characters.
- **The wave.** #61 (reminders) was rebased past a stacked squash and merged. #62 carried the
  setup. Then three branches ran at once, with no file shared between them, and all three landed:
  **#63** (Devin, committed this month and the income share), **#64** (Cursor, closed positions)
  and **#65** (Gemini, undoing a deleted transfer). Each was checked against the territory its
  prompt named and reviewed with `/code-review high` before merging. #65 went back once for two
  crashes and came back fixed. `fix/subscription-totals-currency` closed the one finding left
  on #63.
- **D25 closed a door.** The owner will not pay for a price provider, so typing prices is
  permanent and `feat/price-provider` is closed without being built.
- **What waits on the owner** is unchanged: the phone walk, and the Google Cloud OAuth client
  that `feat/backup` needs (D22). The steps for creating that client are in the `feat/backup`
  entry in C3.

**The owner asked for one more thing on 2026-09-09, and it is built.** Seeing the portfolio
described, they asked to type prices as a **total in euros** rather than per unit, and to be able
to say *"it says 50, I really have 60"*. Both are on `feat/correct-what-you-hold`: a segmented
control on the price editor, and a third kind of entry — a **correction** — that states what is
held without moving money and without deleting anything already recorded. 441 tests.

**They also had to be told what the app was already doing**, which is worth remembering: they
believed NVDA was being tracked in dollars. It is not, and D18 is exactly why — the app holds
`NVD.DE`, the Frankfurt listing, quoted in euros. The words that carry that are the symbol
field's footer and walk step `i2`, and if the misunderstanding survived a whole conversation it
will survive the footer, so watch for it on the phone.

**Both branches of 2026-09-09 are merged** — #43, the device walk, and #44, the investments
portfolio — so `main` carries **Room v2, the first migration, the fifth section and 426 tests**.
That merge is what unblocks the queue: `feat/transfers` needs schema v3 on top of v2, and
`feat/export` needs holdings and trades to exist, and both of those were the reason nothing else
could be built while #44 sat open (the stacking rule in C4).

**Seven decisions were answered the same day**, D12 through D18. The last one was opened by the
two before it — "keep everything in euros" and "fetch prices automatically" cannot both be true
if something has to convert — and then closed by the owner in one sentence: their platform sells
them the **euro-denominated listing**, so the app tracks that. Nvidia is `NVD` in Frankfurt. No
rate, no conversion, no *approximately*, and one less thing for `feat/price-provider` to build.

**The next branch is `feat/transfers`**, which is Phase 3's first piece arriving inside Phase 4
because D11's model needs it: money has to reach the investments account before a purchase can
spend it. After it, `feat/buying-spends-the-cash` finishes what D11 described.
`feat/price-provider` stays blocked on an API key only the owner can create (D13).

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
| Investments | The fifth section: holdings grouped into stocks, ETFs, index funds and crypto, each with the trades behind it, an average cost that survives a partial sale, and what it is worth at a price typed by hand — per unit or as a total, whichever the broker is showing. What the owner holds can be **corrected** when the app and the broker disagree, as a record rather than an overwrite. Room v2, and the first migration this project has shipped |
| Transfers | Money moves between the owner's own accounts, with an optional fee: two ordinary rows sharing one `transferId`, **counted by every balance and by no income-or-expense report**. A third segment on the entry form, its own screen behind it, and a leg that reads as *Transfer to Savings* and opens the transfer rather than half of it. Room v3 |
| Investments and cash | A purchase takes its cost out of the account it is held in and a sale puts it back, so **a broker account's balance is the cash left in it** and the portfolio sits beside it rather than inside it. The movement carries a `tradeId`, obeys the same exclusion a transfer leg does, reads as *Bought NVD.DE* and opens the trade. Room v4 |
| Export | Settings writes the app out to a file the owner keeps: **JSON** carrying every table with the schema version that wrote it, or **CSV** of the movements for a spreadsheet. Through the system picker, so the app asks for no storage permission and nothing leaves the phone. Since #72 the JSON carries the subscriptions and their reminders too |
| Net worth | The Accounts tab says what the owner has in three numbers — **in accounts**, **invested**, and **net worth** — because D19 asked to read it with the portfolio and without it. They only add up because a purchase spends the account's cash; an unpriced holding is left out and counted aloud |
| Restore | A backup file read back in, replacing everything (D21), behind a confirmation naming what arrives and what goes. Older files are read, newer ones refused, a damaged one turned away whole — and the round trip is pinned by a test that exports, wipes and restores |
| App lock *(removed 2026-10-05, D31, #114)* | Cents asked who you are on a cold start, and again after more than half a minute away. A fingerprint or the device PIN, through `BiometricPrompt` — the app never sees either. While it is shut the app is **not composed at all**, rather than covered |
| Prices | Every open holding priced from **one screen** — one date for the lot, a row left empty left alone, and what is already stored shown as the placeholder. Typing is how prices get in, because no free provider carries these listings (D20) |
| Recurring | Rent, Netflix and the salary record themselves. A rule is a **template**: each launch it writes whatever has fallen due, once, and the movements it makes are ordinary from that moment on. Settings › Subscriptions lists them with **what they come to per month and per year** — the figure the screen exists for — and a cancelled subscription is **stopped**, never erased. Room v5 |
| Variable bills | A subscription whose amount changes — the electricity — is marked **ask me the amount**, and what the app refuses to guess waits on the Subscriptions screen instead of being invented. Several are answered in one sitting, each recorded **on the day it fell due** rather than the day it was answered; *there was no bill that month* is its own gesture, because it is not a zero |
| Reminders | A subscription can say something before the money goes: any number of reminders per rule, a number of days the owner picks, before the date or — for a variable one — after it. One daily job asks **what lands today** rather than booking an alarm per occurrence. Room v6 |
| Committed this month | The Subscriptions screen also shows **what is still due to go out before the month ends**, variable bills still waiting included at their estimate, and **what share of recurring income goes to fixed charges**. Estimates are marked as estimates, and the share is shown as unavailable with a reason rather than as 0 %. #63 |
| Closed positions | A fully sold holding stays off the portfolio (D17), and what it made is listed in a collapsed **Closed positions** section at the foot of the screen, with realised profit in euros and as a percentage. It never counts toward the portfolio total or net worth. #64 |
| Transfer undo | A deleted transfer can be put back from the banner on the Transactions list, **both legs or neither**, with their original ids, in one database transaction. #65. Since #71 the same banner shows on an account's detail, which is where a deletion made from there returns |
| Drive backup | Settings › Google Drive: a **daily backup** to the app's hidden folder on the owner's Drive, **Back up now**, and **Restore from Drive** through the same confirmation a file restore uses. Keeps the newest copy of each of the last 7 days, 4 weeks and 6 months, and skips a copy identical to the newest. A refused token is renewed silently; only a grant that needs the owner asks to **Reconnect**. `play-services-auth` and OkHttp, no Drive SDK. #69 |
| Subscriptions in backups | A backup carries the recurring rules, their reminders and which movements each rule wrote, with the watermark that keeps a restored rule from writing its past again. A restore works on a phone that has subscriptions, and its alert counts them on both sides and says to export first. #72, #74 |
| Budgets | Settings › Budgets: a monthly limit per expense category. Each row shows what is left or how far over, what was spent and what is still to come from the category's subscriptions, and a bar that turns amber at 80 % and red above 100 %. The editor uses the keypad, and a budget is deleted behind a confirmation. Room v7. #80, #81, D26 |
| Groups | Settings › Groups: occasions that cut across categories — a trip, a move, a freelance job. A movement can belong to one, picked on the form; a group says what it **cost** or **earned** in words, splits it by category, and opens its movements in the list. Reports and budgets are unchanged. Room v8. #86, #87, D27 |
| Inbox | Movements saved without a category wait under **To categorize** on the Transactions list, with how many; the inbox files each with one tap on a category chip of its own kind, or opens the form. Transfers and trade cash never appear. #90 |
| Filters | *Income* and *Expenses* on the movement list leave out transfer legs and trade cash, as every report does; the plain list still shows them. #95 |
| Accessibility | TalkBack reads a row as one item, says *selected* on picker rows and *heading* on titles, and every small tappable word has a 48 dp target. Controls that held text have minimum rather than fixed heights, so the largest system font grows them instead of clipping. #96 |
| Postponing | A rule's next occurrence, or a variable bill or salary already waiting, can be moved 1–14 days, never as far as the next one. It is recorded on the new day and announced that morning; the rule's own schedule does not move, and *Back to its own day* undoes it. Carried in backups. Room v9. #97, D28 |
| Tests | 873 on `main` after #97, all on the JVM (Robolectric), so CI covers the database and the UI without a device |

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
      **Since 2026-09-27** a column opens its movements too. Tapping one describes it under the
      chart, and *Show movements* opens them. Vico reports the tap through a marker
      controller.

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

- [x] **`feat/correct-what-you-hold`** — the owner asked for two things after the portfolio
      landed, and they turn out to be one: *"I want it in euros, and I want to be able to correct
      the amount when it says 50 and I really have 60."*
      - **Typing what the whole lot is worth.** The price editor gained a segmented control:
        **Per unit** or **Total value**. Both store the same thing — one price per unit, because
        a total is a fact about a quantity and the quantity changes with the next trade — and
        switching converts what is already typed rather than clearing it. It exists because a
        broker's app shows both, and making the owner divide one by the other before they can
        type is how correcting a number turns into arithmetic homework.
      - **Correcting the quantity, as a record rather than an overwrite.** `TradeSide` gained
        **`CORRECTION`**, which needed no migration: `side` is stored as a name and this is a new
        name. It states the quantity held **on its date** rather than a difference, so recording
        the real cause later — the purchase that was forgotten — cannot count it twice. It moves
        no money: finding more units costs nothing and lowers the average (which is what a split
        does), finding fewer takes their share of the cost with them, and **neither realises a
        profit**, because discovering that a number was wrong is not a trade.
      - The form is the same one, with a third segment. On a correction the price and fee fields
        are gone rather than ignorable, the quantity field opens on what the app believes is
        held, and zero is allowed — *"I actually hold nothing"* is a legitimate thing to say
        about a position the app believes in, which a purchase of nothing is not.
      - Reached from the **Quantity row itself**, so the correction is offered where the disputed
        number is read rather than from a menu somewhere else.
      *Done when:* the app agrees with the broker without anything being deleted. **Done**,
      441 tests.

- [x] **`feat/transfers`** — Phase 3's first piece, needed here: **money has to be able to reach
      the investments account**. A transfer is a paired outflow and inflow linked by one
      `transferId`, excluded from every income and expense report (PRD §3) — get that
      wrong and every statistic in the app inflates. Schema v3, a migration and its test.
      *Done when:* moving 500 € from Checking to Broker changes both balances and no chart.
      **Done**, 470 tests. What it actually settled, beyond the plan above:
      - **One column, and no transfers table.** A transfer is a *relationship between two
        movements*, so giving it a table of its own would mean the same amount written twice and
        free to disagree with the balances it is made of. `transactions` gained one nullable
        `transferId` and its index; the value is the row id of the outgoing leg, which is unique
        by construction and already exists by the time the second leg is written. Room v3 and
        `MIGRATION_2_3`, one `ALTER TABLE ... ADD COLUMN` — the one change SQLite makes in place,
        so nothing copies the ledger.
      - **The rule is written once and applied eight times.** Every income-and-expense query on
        `TransactionDao` carries `transferId IS NULL`; every balance query deliberately does not,
        because the money really moved. Two of the eight — the trend's two queries — would exclude
        legs anyway through their `INNER JOIN` on a category; the clause is written out regardless,
        so the rule is *every report query has it* rather than *every report query except the two
        you have to reason about*. `TransferQueriesTest` asks all of them about the same transfer.
      - **The fee is the difference between the legs, not a third row.** 500 € sent with a 3 €
        fee leaves 503 € and lands 500 €, and the fee is read back as what is missing. It cannot
        drift from the balances because it is not stored anywhere else, and it stays out of the
        expense reports because PRD §4.2 puts it inside the transfer rather than beside it.
      - **The third segment is a different screen, and that is the point.** A transfer has two
        accounts and no category, so folding it into the movement form would have made every
        section of that form a conditional. `EntryKind` gives both forms the same three-way
        control, and choosing a kind the current screen cannot record **replaces** it rather than
        stacking on it — so back still means the list, and a few changes of mind do not build a
        pile of forms. The amount typed so far travels across as a route argument.
      - **A leg reads as what it is.** It has no category, so the movement row would have called
        it *Uncategorized* and filed it beside the things that genuinely want classifying. It now
        says *Transfer to Savings*, resolved in SQL by a subquery guarded on `transferId IS NULL`
        so ordinary movements pay nothing for it, and it is written in the ordinary colour rather
        than income's green. Tapping either leg opens the **transfer**, because editing one side
        on a form that knows nothing about the other is how the two stop agreeing.
      - **And one thing this branch found elsewhere.** Deleting an account *and its movements*
        would have orphaned the far leg of every transfer it was part of — money arriving from
        nowhere, in an account the owner was not deleting. Transfers now go whole, and because
        that changes a balance somewhere else, the confirmation counts those rows and says so in
        its own sentence rather than quietly growing a number. Reassignment is the case left
        standing on purpose, in C4.
      - **The review pass landed after the merge, as #48.** `/code-review` finished just as #47
        went in, and found three behaviour bugs worth knowing about, all of them the same shape:
        a rule applied in one place and not in its neighbour. The kind control was offered while
        *editing*, so an existing movement could be "turned into" a transfer while quietly
        staying where it was — save it and you owned both. `load()` re-derived the account list
        from the already-pruned one, so an archived destination could vanish from a transfer
        being edited, depending on which of two loads landed first. And the `uncategorized`
        filter never gained the `transferId IS NULL` its own chart had, so the ring's
        *Uncategorized* slice drilled into a list containing every transfer leg in the period.
        Two smaller ones and a dead import came with them. **It also went red on CI for a reason
        it did not cause** — a family of tests that read the database immediately after a write
        through a view model, which races it; that is fixed across the suite and written up in
        C4, because it is easy to write the racing way and it passes until it does not.

- [x] **`feat/buying-spends-the-cash`** — what D11 actually described, finished. A purchase takes
      its cost out of the account's cash and a sale puts it back, using the same exclusion the
      transfer above establishes, so an investments account's balance reads as **uninvested cash**
      and the portfolio value sits beside it rather than inside it.
      *Done when:* transfer 1.000 €, buy 400 € of NVDA, and the account shows 600 € of cash
      beside a 400 € position. **Done**, 484 tests. What it settled on the way:
      - **A purchase is two records, not one fact stored twice.** The trade says what was bought;
        a movement in the holding's account says the money left. They answer different questions
        — one is about units, the other about a balance — and `RoomTradeRepository` writes,
        rewrites and removes them inside one `withTransaction`, for the reason the transfer's two
        legs are written that way: nothing but that class enforces the pairing.
      - **The derived alternative was tried on paper and is wrong.** Cash could have been
        computed as *balance minus what was invested*, with no schema change and no second row —
        and it gets the cash right while getting the **account** wrong: a sale for more than was
        paid never reaches the balance, so an account that really holds 1.100 € keeps reporting
        1.000 €. Writing the movement keeps `balance = opening + movements` true, which is the
        invariant every other screen in the app is built on.
      - **Room v4, `MIGRATION_3_4`.** One nullable `tradeId` and its index — and its foreign key,
        which is the one wrinkle: SQLite cannot add a constraint to an existing table, so the
        column is declared with `REFERENCES` inline, exactly as the exported `4.json` spells it.
        The migration test proves Room accepts the result and that the key really arrived.
      - **The exclusion rule now has two members and one sentence.** A report counts rows that
        are money earned or spent; a balance counts every row. Both a transfer leg and a trade's
        cash are neither earned nor spent, and the `TransactionDao` header says so once for both.
      - **`RESTRICT`, not `CASCADE`** — so deleting a trade means deleting its movement first, on
        purpose and in the open. Rule 8 is a standing instruction not to let a delete elsewhere
        quietly rewrite recorded history here, and a cascade is exactly that mechanism.
      - A purchase reads as **Bought NVD.DE** rather than *Uncategorized*, in the ordinary colour
        rather than income's green, and opens the trade — the third case of the rule transfers
        established, so where a row leads now lives beside how it reads, in `MovementRow.kt`.

- [x] **`feat/prices-in-one-place`** — every open holding's price on one screen, because D20
      settled that typing is how prices get in rather than a fallback. **Done**, 558 tests.
      - **One date for the lot**, since they are read in one sitting off one broker screen. A
        date per row would be more precise about something nobody is imprecise about, and would
        put a calendar between the owner and every number they came to type.
      - **A row left empty is left alone.** Updating three prices out of eight without disturbing
        the other five is the whole point, and a blank field is how somebody says *not this one*.
        A typed zero reads the same way: nothing here is worth nothing, and a stray keystroke
        must not wipe a real price.
      - **Only open positions.** A sold holding has no quantity, so a price for it would be a
        number about nothing (D17).
      - The placeholder in each field is **the price already stored**, so an untouched row shows
        what it is keeping rather than an empty box that reads as missing.
      - It is read once rather than observed: this is a form, and a portfolio that re-emitted
        mid-typing would take the half-typed number with it.
- [x] ~~**`feat/price-provider`**~~ — **closed without being built (D25, 2026-09-21).** The
      owner will not pay for a provider, and no free one carries these listings (D20), so typing
      prices is permanent. What already exists stays: the `PriceProvider` interface, the cache
      and a quote's age cost nothing to keep, and a free provider covering European venues could
      still turn up. The history below explains how the branch got here.
      Prices from the internet, behind the `PriceProvider` interface
      PRD §5.1 makes mandatory, with the cache and the age of a quote already built. **D13 is
      answered: Twelve Data**, chosen for European coverage on the free tier, and **D14** says an
      index fund is priced by its equivalent ETF rather than by an ISIN no free provider carries.
      **Still blocked on the owner**: it needs an account and an API key in `local.properties`,
      and only they can create one (C5, rule 6).
      **Answered on 2026-09-17: not on a free tier, and not by converting either — see D20.**
      Twelve Data refuses XETR listings on the free plan, Finnhub's free tier is US-only and
      EODHD's free plan is US exchanges. Converting from the US listing looked like the way out
      and is not: **a UCITS ETF has no US listing to convert from**, so conversion would automate
      the stocks and leave the ETFs, which is most of this portfolio. The branch therefore waits
      on somebody deciding to pay, and what got built instead is `feat/prices-in-one-place` —
      making the typing fast, since typing is now how prices get in.

      **Asked the API on 2026-09-17, and the answer was no.** The key arrived and the first thing
      done with it was to ask for the listings D18 chose: `NVD` on XETR and `VWCE` on XETR both
      come back *"available starting with the Grow or Venture plan"*, while `NVDA` on NASDAQ and
      `EUR/USD` both work. The free tier carries the **US listing in dollars** — the exact case
      D18 was written to avoid — and not the euro-denominated one it chose. **D20 is the question
      that opens**, and this branch waits for it rather than being built against an API that
      cannot price what the owner holds. Two things were learned that survive whatever is
      decided: the dotted `NVD.DE` form is not how this API addresses an exchange at all, and a
      bare symbol is ambiguous **across currencies** (`NVD` is NVIDIA in EUR on four German
      venues and a Thai company in THB), so a holding will have to carry a MIC code beside its
      symbol whichever provider wins.
      **D18 removed the hardest part of this branch before it was built.** The question was what
      to do with a price that arrives in dollars; the answer is that it never does, because a
      holding tracks the **euro-denominated listing** — Nvidia is `NVD` in Frankfurt, quoted in
      euros, which is what a European platform sells you. No FX call, no rate to keep fresh, no
      total labelled *approximate*, and the number in the app is the one the broker shows because
      it is the same quote. What the branch does instead is pass the symbol through with its
      exchange (Twelve Data addresses exchanges by MIC code), and say plainly when a symbol
      cannot be found rather than falling back to something that looks like a price.

### Phase 3 — the parts of it the answers of 2026-09-09 defined

Transfers are listed above, inside Phase 4, because investments need them. These are the rest, in
the order the roadmap puts them, and all three now have their questions answered.

- [x] **`feat/export`** — **D15: a manual export first**, a file the owner owns, with no account
      and no third party in it. It reads every table, holdings and trades included, which is
      why it waited for the investments schema — that landed with #44.
      *Done when:* the app can be written out and read by something that is not the app.
      **Done**, 507 tests. What it settled:
      - **Two formats, because they answer two questions** (PRD §4.7). JSON is the whole app in
        one file — every table, ids intact, amounts in minor units, dates as epoch days — which
        is the "never locked in" guarantee and the thing `feat/backup` will have to send
        somewhere. CSV is the movements only, for opening in a spreadsheet.
      - **`schemaVersion` is the field that makes the file survivable.** A backup says which
        database version wrote it, so whatever reads it next can refuse what it cannot read
        instead of guessing — and guessing is what turns a backup into data loss.
      - **The JSON tree is built by hand rather than by annotating the model `@Serializable`.**
        The file format stays a deliberate decision in one readable place instead of a side
        effect of what the classes happen to be called today: a rename breaks a test here rather
        than silently writing a file last year's app cannot read.
      - **The CSV is the one number in this app that ignores the locale.** Amounts go out in
        major units with a dot, because the file is handed to another program, and a comma is a
        column separator in half of Europe. Escaping is RFC 4180, so a category called
        `Food, drink` stays one column and a note with a quote or a newline survives.
      - **The system picker is what makes it permissionless.** `ACTION_CREATE_DOCUMENT` means the
        owner names a place and the app is handed a door to that one file — no storage
        permission, ever. Opened with `"wt"`, because writing over a longer file without
        truncating leaves the tail of the old one behind: a backup that parses and lies.
      - **No import, on purpose.** Restoring asks what happens to what is already on the phone,
        and D15 put that question in `feat/backup` rather than leaving it to be discovered by
        whoever taps the button first.
      - Settings gained a stateless `SettingsContent`, the shape the movements list already uses,
        because a screen holding a view model cannot be put on screen by a test that only wanted
        to check a confirmation.
- [x] **`feat/restore`** — the half of `feat/backup` that needed nothing from the owner, split
      out and built first: **a backup file read back in**. #51 wrote files with no way home, which
      C4 recorded as a gap, and a backup that cannot be restored is an export with a reassuring
      name. **Done**, 535 tests.
      - **D21 is answered in the open: a restore replaces everything.** Merging two ledgers needs
        id remapping and duplicate detection — a backup is a graph, and two ledgers numbering
        their accounts from 1 cannot be poured together — for a case that barely exists with one
        owner. Refusing unless the app is empty is useless exactly when it is needed.
      - **The file is read before the question is asked**, so the confirmation names both halves
        of the trade — what the file holds, and what is about to be replaced — and the numbers in
        it are checkable rather than promises. That ordering moved once during the build: what is
        currently recorded used to be read when the flow started, which races an owner who picks
        a file quickly, and the half that loses that race is the one saying what they are about
        to lose.
      - **Older files are read, newer ones are refused.** A v2 backup has no `transferId` because
        transfers did not exist when it was written, and absent reads as null — which is what it
        means. A file from a later version might hold a table this one cannot see, so it is
        turned away rather than partly understood.
      - **A damaged file changes nothing.** A missing amount is a refusal naming the field, not a
        zero written into a ledger; and the whole restore is one transaction, so a write that
        fails partway leaves the app exactly as it was.
      - **Ids are kept.** A movement names its account by id, so renumbering on the way in would
        file it under somebody else's account and every balance would be wrong with nothing on
        screen to show it.
      - The strongest test here is the **round trip**: populate, export, wipe, restore, compare.
        It is the only one that would catch the writer and the reader agreeing with each other
        while both being wrong about the database.
- [x] **`feat/buy-by-amount`** — recording a purchase as **500 € of something** rather than as
      units × price, because that is how the platform sells it. Asked for in the owner's own
      words while answering D20. **Done**, 562 tests.
      - **Units are still what is stored**, and that is the point rather than a compromise: a
        partial sale reduces units, an average cost divides by units, a price multiplies them.
        The euros are turned into units once, at the moment of typing, instead of every time
        anything is read.
      - **Nothing is lost in the turning.** At eight decimals one unit-step is about a
        hundred-thousandth of a cent, so multiplying the stored units back by the price returns
        the amount that was typed, to the cent — which a test asserts rather than assumes.
      - **The footer says how many units it came to**, before the save. The owner is then
        agreeing to the record rather than to the number they typed.
      - Switching between the two **converts what is already typed**, the same bargain the price
        editor strikes between *per unit* and *total value*: a control that empties the fields is
        one nobody touches twice.
      - **A correction is always in units**, and the control is not offered there at all. A
        correction says what is *held*, and holding is counted in units — asking how much of
        something you own in money is a different question with no answer.
- [x] **`feat/backup`** — **#69, merged 2026-09-23.** Built by Devin in the second Orca wave.
      Its review findings were fixed here, and #72 made the file it uploads carry the
      subscriptions. The owner's walk is `n6`–`n10` in `docs/device-check.md`. What follows is
      the setup as it was written down before the branch, kept because a release key will need
      the same steps again.
      What it waited on was the credential — an OAuth client the owner creates in Google Cloud Console for package
      `io.github.anderescaray.cents` and the signing certificate's SHA-1 — plus the Drive API
      enabled and the consent screen left in *Testing* with the owner as its only user. Nothing
      secret lands in the repo: an Android OAuth client carries no secret and is matched by
      package name and certificate, so unlike D13's key there is nothing to keep out of git.
      The shape, now that the deprecations are checked: **`AuthorizationClient`** from
      play-services-auth for the `drive.appdata` scope — `GoogleSignIn` is removed in 22.0.0 —
      and the **Drive REST API** to upload, the Android Drive API having been dead since 2019.
      `appdata` rather than a visible folder, so the backup cannot be opened or shared by
      accident and goes when the app does.
      **The owner's steps, written down on 2026-09-21:**
      1. console.cloud.google.com → new project **Cents**.
      2. APIs & Services → Library → **Google Drive API** → Enable.
      3. Google Auth Platform (the consent screen): app name Cents, audience **External**, left in
         **Testing**, with the owner's account added as a **test user**.
      4. Data access → add the scope `.../auth/drive.appdata`.
      5. Clients → Create client → **Android**, with package `io.github.anderescaray.cents` and the
         debug key's SHA-1 `A6:15:3B:AD:C8:AB:09:A0:FF:34:14:D9:24:4A:2F:FA:93:13:B6:13`
         (`%USERPROFILE%\.android\debug.keystore`, shared by every build on that PC). A release
         key, if one is ever made, needs a second Android client with its own SHA-1.
      **Step 5 had not been done when the branch merged.** The first real attempt on the phone,
      on 2026-09-24, failed with an authorization error until the owner created the Android
      client. If a connect ever fails again, check the client before the code.
      Nothing from those steps is pasted into the repo. The owner says it is done, and the branch
      runs **alone**, because it adds libraries to `libs.versions.toml`, which is a global lock.
      One thing to read from the keytool output: on a Spanish-locale JDK it crashes printing the
      certificate unless it is run with `-J-Duser.language=en`.
- [x] **`feat/biometric-lock`** — and **D16 settles what is under it: no SQLCipher.** Android
      encrypts the device and no other app can read these files; what SQLCipher answers is root
      access and physical extraction, at the price of a dependency, slower writes and a key that
      can be lost with the data. The lock is the protection that got built. **Done**, 549 tests.
      - **When it locks is the whole feature, and it is a plain object with no Android in it.**
        `AppLock` decides: always on a cold start, and on coming back only after **thirty seconds**
        away. Locking on every return would mean authenticating after two seconds in a calculator,
        which is the fastest way to make somebody turn the lock off — and a lock that is off
        protects nothing. Thirty is a judgement rather than a finding, and it is one line.
      - **Two states exist only to stop the prompt looping.** `ASKING` is separate because showing
        `BiometricPrompt` backgrounds the activity on some devices, and a lock that re-arms on
        backgrounding would fight its own prompt. `DISMISSED` is separate because the gate raises
        a prompt from `LOCKED`: without it, dismissing would raise the prompt again immediately
        and the owner could never reach the screen underneath — not even to leave the app. Both
        were found while building, and both have a test named after the failure.
      - **While shut, the app is not composed.** Not blurred, not behind a scrim: `AppLockGate`
        renders the lock screen *instead of* the app. A lock that draws the app underneath still
        has it in the view tree, where a recents screenshot or an accessibility service can find
        it. And nothing at all is drawn until the setting has been read — assuming *open* for that
        frame would flash the ledger at exactly the person this exists to stop.
      - **The preference fails closed.** A DataStore read that throws answers `true`, unlike the
        home layout next door, which reads an I/O failure as an unarranged canvas. Losing an
        arrangement costs an arrangement; treating "I could not tell" as *unlocked* would let a
        disk hiccup quietly turn the lock off. The cost is being asked once too often, which the
        owner can answer.
      - **A fingerprint or the device PIN, whichever the phone has.** Insisting on a biometric
        would shut out a phone with none enrolled; the PIN is what the device already trusts to
        unlock itself. On a phone with neither, the switch is shown **off and unusable with a line
        saying why** rather than hidden — the same argument the account-deletion sheet makes about
        an option it cannot offer.
      - **`MainActivity` is a `FragmentActivity` now**, and only because `BiometricPrompt` puts up
        a fragment of its own. It is a superclass of what was there, so nothing else moved.

- [x] **`feat/recurring-rules`** — **what repeats, recorded without being asked** (PRD §4.4).
      The biggest unbuilt thing in the PRD when it was picked up, and Phase 3's own work rather
      than a jump. **Done**, 605 tests, **Room v5**. What it settled:
      - **A rule is a template, and the movements it makes are ordinary.** They carry a `ruleId`
        and nothing else unusual: counted by every report, editable, deletable. That separation
        is rule 8 — cancelling Netflix in June must not remove January to May, or every chart of
        the past changes and the app stops being worth comparing periods with.
      - **Every occurrence is counted from the start date, never from the one before it.** A
        monthly rule beginning on the 31st lands on the 28th in February — `java.time` clamps it
        — and **returns to the 31st in March**, because March is *start plus two months* rather
        than *February plus one*. Stepping forward from the previous occurrence pins it to the
        28th for ever, and nobody notices until a year of rent is three days early.
      - **Generating is idempotent, and everything else rests on that.** It runs on every
        `ON_START`, so writing the same charge twice is the failure to design against, not a
        missed one. Each rule records how far it has got, **in the same database transaction as
        the movements it describes** — separately, a crash in between either repeats a charge or
        skips a period for ever.
      - **The start date is also the backlog.** A rule entered in September with a start date in
        June writes the three charges it missed the moment it is saved, which is what somebody
        typing in their rent for the first time expects.
      - **Stopping and deleting are one button in a fixed order.** A running subscription with
        charges behind it is *stopped*; a stopped one can then be *deleted*, and its movements
        stay either way because `transactions.ruleId` is `SET NULL`. The order matters: the
        default is the safe one, and the escape hatch exists for an account that cannot be
        deleted while a rule records into it.
      - **The total is the screen.** A list of subscriptions says nothing the movement list does
        not; "these come to 84 € a month" is the thing nobody works out by hand. Computed in
        integers throughout (rule 1), with a year taken as 365 days so a weekly charge is 52.14
        of them rather than 52 — and **null rather than wrong** when the rules do not share a
        currency. Income is listed but left out: a salary is a recurring rule too, and netting it
        off would answer no question.
      - **`CONFIRM` mode writes nothing yet, on purpose.** An amount that varies — electricity —
        is not one the app may assert on the owner's behalf (PRD §4.4). Proposals are their own
        branch; until then those rules are skipped and their watermark left alone, so nothing is
        lost when proposals arrive.
      - **The account key is `RESTRICT` and the category key is `SET_NULL`**, and the difference
        is whether the rule survives losing them. An account a rule records into cannot be
        deleted — the flow says so, the way it already does for holdings — because a rule with
        nowhere to record cannot be repaired by moving movements elsewhere. A category can go:
        the rule keeps working and its movements arrive unclassified.

- [x] **`feat/subscription-amounts`** — **D23, first half: somewhere to answer a bill the app
      would not guess.** `feat/recurring-rules` stored a *variable amount* mode and skipped it,
      which was honest and useless: the refusal to invent a figure was indistinguishable from the
      feature not working. **Done**, 622 tests, **no schema change**. What it settled:
      - **The waiting list is derived, not stored.** A rule already remembers how far it has got,
        so what is pending is what `dueOccurrences` returns for a variable rule — answering one
        writes the movement and moves that watermark, in one transaction. A table of pending rows
        would be a second place for "has this period been dealt with" to be true, and the two
        would eventually disagree.
      - **Only the oldest occurrence of each rule is offered**, with a count of what is behind it.
        The watermark is a single date, so recording March would take January and February with
        it in silence. One at a time is what makes that impossible rather than merely unlikely,
        and the repository refuses a date that is not the oldest rather than trusting the screen.
      - **It is recorded on the day it fell due, not the day it was answered.** A bill opened a
        week late still belongs to the period it was for; dating it by when somebody got round to
        it would move money between months in every chart in the app.
      - **A row left empty is left alone, and skipping is a separate gesture.** Blank means *not
        this one yet*. *There was no bill that month* is a real answer and is **not** a zero — a
        zero is a figure that would sit in the charts saying the month cost nothing.
      - **The estimate on the rule earns its keep.** It is the placeholder on the screen that
        asks, and it stands in for the real figure in the monthly total — leaving variable rules
        out of that total would understate the month by exactly the bills that are hardest to
        predict. The caption says a guess is in there rather than letting the number look like
        arithmetic.
- [x] **`feat/subscription-reminders`** — **D23's second half, asked for again from the phone.**
      **Done**, 641 tests, **Room v6**. What it settled:
      - **Nothing is scheduled in advance, and that is the whole design.** One daily job asks
        *what lands today* and computes the answer from the rules. A booked alarm per occurrence
        would be a second copy of when the rent is, and it would go stale every time a reminder
        was edited, a rule stopped, a bill answered, the phone rebooted or the clock moved. There
        is nothing to cancel because there was never a queue.
      - **It is therefore safe to run twice**: the notification id is the rule and the occurrence,
        so a second run replaces rather than stacks.
      - **`occursOn` goes through `occurrenceAt` rather than around it.** "Is the 26th two days
        before an occurrence" cannot be answered by comparing day-of-month, because a rule
        anchored on the 31st lands on the 28th in February. One definition of where a rule lands,
        used by the generator and by the reminders, so the two cannot disagree about when the
        rent is.
      - **The two modes get different offers, because they are different requests.** A fixed
        amount is *announced*, and only beforehand — afterwards the movement is already in the
        list, so the notification would be telling the owner something one screen away. A
        variable one is *asked about*, so it can also be chased after the date, and its tap lands
        on the screen that takes the figure. Switching a rule to a fixed amount turns an *after*
        into the same number of days *before*, which is what was actually wanted.
      - **A bill already answered stops being chased.** Being nagged about a figure just typed is
        how somebody turns notifications off.
      - **The editor says when the reminders are being written down and thrown away.** A
        preference the system will never act on is worse than no preference: the owner sets three,
        hears nothing, and concludes the feature is broken. The row asks for the permission on
        Android 13+, and opens the app's notification settings when that is the only way left.
      - **`CASCADE` here, where every other key is `RESTRICT` or `SET NULL`.** A reminder is
        configuration, not history — no movement points at it — so a deleted rule takes its
        reminders with it. Rule 8 is about movements, and a migration test pins both directions at
        once: the reminder goes, the movement stays.
      - **Verified on the phone, which is not the same as tested.** The daily job is booked —
        `dumpsys jobscheduler` shows it after a launch — and a failure to book it is logged
        rather than swallowed, because a worker that silently never schedules is a feature that
        simply never happens and nothing on screen would ever say so.
      - **No `androidx.hilt:hilt-work`.** That needs a compiler and a `Configuration.Provider` to
        inject two objects into one class; a plain `CoroutineWorker` asking the graph through an
        `@EntryPoint` is the same thing with two fewer artifacts (rule 11). Scheduling moved out
        of `Application.onCreate` for a duller reason: initialising WorkManager there pulls it
        into every Robolectric test, including the migration ones.
      Merged as **#61**, after being rebased past the pre-squash copy of #60 it was stacked on
      (the C4 warning about stacking, met again).

### The first Orca wave (2026-09-21, D24)

Three branches at once, one per tool, with no file shared between them. Each was checked
against the territory its prompt named and reviewed with `/code-review high` before merging.

- [x] **`feat/committed-this-month`** (Devin) — **#63.** PRD §4.4's two missing figures on the
      Subscriptions screen: **what is committed this month** — every unrecorded expense occurrence
      from today to the end of the month, plus variable bills still waiting at their estimate —
      and **what share of recurring income goes to fixed charges**. Reuses `occursOn` and
      `dueOccurrences` rather than stepping from the previous occurrence. Waiting bills are
      counted strictly before today and the remaining-month window starts at today, so today's
      bill cannot be counted twice. The share keeps an exact numerator and denominator until
      display. **The owner chose that the waiting backlog counts**, earlier months included:
      "committed" means *still to pay*, and an unanswered July bill is still to be paid in
      September. The review left one finding, which only a multi-currency install could reach.
      It was fixed in #66: an empty side of the totals is zero in any currency, so it no longer
      needs every rule to agree on one.
- [x] **`feat/closed-positions`** (Cursor) — **#64.** The fix C4 named for D17: a collapsed
      **Closed positions** section at the foot of the portfolio, one row per fully sold holding,
      with realised profit in euros and as a percentage of what the sold units cost. It opens
      the existing holding detail. The open portfolio, its totals and net worth are unchanged. A
      holding never traded, or emptied by a correction rather than a sale, is not a closed
      position: it has no realised result. The review found nothing.
- [x] **`feat/undo-a-deleted-transfer`** (Gemini) — **#65.** The C4 gap closed:
      `DeletedTransactionBuffer` holds a sealed `DeletedTransaction`, either a movement or a
      transfer's two legs, and undo restores both in one `withTransaction`, with their original
      ids and shared `transferId`. **The review sent it back once**, for two crashes: the read of
      the legs sat outside the `runCatching` guarding the delete, and a transfer with more than
      two legs (only a malformed backup could make one) was deleted first and crashed after. The
      leg count is now checked **before** anything is deleted, and a test pins three legs.

### The second wave (2026-09-22 → 2026-09-23)

Devin, Cursor and a Claude Code implementer were given three branches through Orca on
2026-09-22. The owner then finished the wave with Claude Code alone, so its tail was built here.

- [x] **`feat/trade-note`** (Claude Code in Orca): **#68**. A trade carries a note, and editing
      no longer erases it. Its two follow-ups are **#73**: the note reaches the cash movement,
      and a note on a row stops at two lines.
- [x] **`feat/backup`** (Devin): **#69**. Its review of 2026-09-22 sent it back with nine
      findings. They were fixed on the branch on 2026-09-23 by the coordinator, since Devin was
      not in that session, and CI was green on the first push.
- [x] **`feat/account-detail-undo`** (Cursor, then Claude Code): **#71**. Cursor's run failed
      before its first commit, with the work only in its worktree on the owner's PC. It was
      rebuilt from the same prompt, inside the same territory.
- [x] **`fix/backup-carries-subscriptions`**: **#72**. Not planned. #69's description named the
      gap: a backup without subscriptions, and a restore refused on any phone with one. It came
      first because it made the automatic backup not a backup.
- [x] **`feat/restore-says-export-first`**: **#74**. The C4 line, after #69 and #72 had stopped
      touching the settings screen.
- [x] **`feat/midnight-tick`**: **#76**. Screens left open across midnight move to the new day.
      See C4.

### Phase 5 — begun with budgets (2026-09-23)

- [x] **`feat/budgets-schema`**: **#80**. Room v7: `budgets`, one per expense category, with
      `CASCADE` from the category. Budgets travel in backups.
- [x] **`feat/budgets-screen`**: **#81**. Settings › Budgets and the editor, on D26's defaults.
- [x] **Groups** (PRD §4.9, D27, outside the phase order): **#86**, Room v8 and the repository;
      **#87**, the screens.
- [x] **`feat/uncategorized-inbox`**: **#90**. The *To categorize* row and the inbox screen.
- [x] **`feat/notification-capture`** (PRD §4.6, experimental; #105–#107, done 2026-10-01): payments read from the
      notifications of the apps the owner picks, landing in the inbox. **Asked for on
      2026-09-29, and D29 records how it behaves**: editable expense and income words decide the
      kind, the amount comes from the text, each app is tied to one account, a match is saved at
      once without a category, and two of the same in the same minute count once. Three parts:
      - [x] **Part 1, the logic** (2026-09-29, #105): `CaptureParser` (words and amount, pure Kotlin,
            its tests run locally with kotlinc too), `RoomCaptureRecorder` (every check D29 lists,
            then an uncategorized movement; "the same minute" is remembered in memory and only
            for captured notifications, so a restored, typed or subscription movement of the same
            amount never swallows a real payment), and the settings in
            **DataStore rather than Room**. They are settings, not the ledger, so there is no
            schema change, and a restore that replaces the ledger leaves them alone.
      - [x] **Part 2, the listener** (2026-09-29, #106): `ui/capture/CaptureListenerService`, a
            `NotificationListenerService` that hands the text of a chosen app's notification
            to the recorder, off the main thread. **It reads nothing from an app nobody
            chose**: it follows capture's switch and chosen apps from settings and asks
            `CaptureText.worthReading` before looking at any text. It skips Cents' own
            reminders, ongoing notifications and group summaries. It reads the expanded body,
            or a bundled notification's lines, before the title, so a balance in the title is
            never taken as the payment. A repost with the same key, time and text is dropped
            (`SeenNotifications`), a dismissed key is forgotten, and it asks to be rebound if the
            system lets go of it. It never logs a word of a notification. It is declared in the manifest, so
            Android lists Cents under *Notification access*, but nothing reaches the ledger
            until part 3 lets the owner switch capture on and choose an app. It was compiled
            against Robolectric's Android classes and Hilt in the container; only a phone can
            check that it hears anything. **On the owner's phone, a Pixel, battery optimisation
            may have to be turned off for Cents**, or the system can stop the listener. (The
            Honor is the tablet the app is developed on, not where it will live.)
      - [x] **Part 3, Settings › Capture** (2026-09-30, #107): the switch; *Notification access*,
            asked of the phone each time the screen comes back into view and opening the system
            list; the apps, each with its account by name (or saying its account is gone), with a
            sheet to move it or stop reading it; a picker of the launcher's apps, searchable, then
            the account; and the two word lists, where a word the parser would read as one
            already there (case and accents aside) is refused, removal asks, and *Restore the
            default words* appears once the list differs. The manifest's `<queries>` asks for the
            launcher's apps only, not `QUERY_ALL_PACKAGES`. No schema change. **Walk 19**
            (`c1`–`c4`) in `docs/device-check.md`, on the Pixel.

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
- **Net worth counts active accounts, and names the money in archived ones rather than adding
  it.** D19 is answered and built: the Accounts tab gives *in accounts*, *invested* and *net
  worth*. The first of those sums the same accounts the tab has always totalled — the active ones
  — so an archived account still holding money is money the headline figure does not include. It
  is said out loud in the footer instead, because the alternatives are both worse: counting it
  would make the top of the screen disagree with the total under the list, and leaving it
  unmentioned would understate net worth silently.
- **A restore replaces everything, and there is no undo behind it.** D21 chose that deliberately
  over merging, and the protection is the confirmation rather than a way back: it names what the
  file holds and what is about to go, subscriptions included since #72. Since #74 it also ends
  by saying to cancel and export first, which is the one safety net there is.
- ~~**Nothing backs up on its own.**~~ **It does since #69**, daily to Drive. What a backup
  still leaves out is **app settings**: the bubble layout, the lock, and the Drive switch itself.
  A restore on a new phone brings the money back, not the arrangement. The Drive section's footer
  says so.
- **A backup made before #72 has no subscriptions in it.** Restoring one replaces the phone's
  subscriptions with none. The alert says so in numbers, because it counts subscriptions on both
  sides, but it does not say why. Every Drive copy made since #72 carries them.
- **Two devices are not two copies of the app.** D22 chose Drive as a *backup*, and the owner
  asked the obvious next question — whether that means the same data on a phone and a tablet. It
  does not, and the record says so rather than leaving it to be discovered: a restore replaces
  everything (D21), so two devices both recording would diverge with nothing warning them, and
  whichever restored next would destroy its own changes. The intended use is **one device
  records**; the other gets the data by restoring. Sync would need per-row change tracking, a
  conflict rule and somewhere for the two to meet — a backend §1 has deliberately never had.
- **Two documentation branches can edit the same paragraph and one will vanish.** It happened on
  2026-09-17: #52 and #53 both rewrote the *Open* section of `DECISIONS.md`, both merged, and
  **D20 was silently gone** — no conflict surfaced in the working tree afterwards, and the branch
  that lost had already been deleted. It is the same family as the squash-merge warning below,
  and the same fix: when two branches are in flight, keep their documentation edits in different
  sections, or land one before writing the other. D20 is restored, and this is why it says so.
- **The CSV covers movements and not investments.** A holding is not a row in a ledger, and
  flattening one into the same columns would either invent fields or lose the trade behind it.
  The JSON has everything; the CSV is the spreadsheet view, and it says so in the sheet it offers.
- **A trade recorded before its account existed in this form still has no cash row.** Nothing
  backfills: `MIGRATION_3_4` gives every existing movement a null `tradeId`, and every trade
  recorded before it keeps its balance untouched. Editing such a trade writes the movement it
  never had, which is right but is also the only way it happens. Backfilling would mean inventing
  movements on dates the owner never saw, and an account's history is not the place to guess.
- **A transfer reassigned into its own counterpart is left standing.** Deleting an account by
  moving its movements elsewhere moves transfer legs too, so a transfer can end up with both legs
  in one account: a row saying *Transfer to Checking*, read in Checking. It changes no balance
  (the pair nets to zero) and no report (both legs are still excluded), and the two alternatives
  are worse — clearing the `transferId` would turn it into a real income and a real expense and
  inflate both by the amount, and deleting the pair would destroy history inside an operation
  whose whole promise is that nothing is lost. **Since 2026-10-01 the reassignment sheet names
  them before it runs**: a target that shares transfers with the account carries a line saying
  how many would end up with both sides in it.
- ~~**A deleted transfer has no undo.**~~ **It has one since #65**: both legs or neither.
  ~~**The undo banner lives only on the Transactions list.**~~ **The account detail shows it too
  since #71**, which is where a deletion made from there returns. The confirmation now says the
  undo is on "the screen you go back to", which covers both.
- ~~**The kind filter still sorts transfer legs by their kind.**~~ **Closed by #95 on
  2026-09-25**: *Income* and *Expenses* now leave out transfer legs and trade cash. What follows
  is the gap as it was. Filtering the list to *Income*
  shows the arriving side of every transfer in the period. The rows read as transfers and are
  excluded from the totals above them, so nothing adds up wrongly — but *Income* is not quite
  what they are. The filter form gaining a transfers switch is one more section on a form that
  already has six, and it waits for somebody to actually want it. **The *uncategorized* filter is
  the one case already closed**, because it is not a preference but a drill-down: the ring's
  *Uncategorized* slice counts no legs, so the list behind it shows none either. A leg has no
  category because a transfer is not classified, not because nobody got round to it.
- **~~A postponed charge still counts in its own month for budgets and *committed this month*.~~**
  Fixed on 2026-09-27: both now count a moved occurrence in the month of the day it moved to,
  which is the day its movement is dated. That includes one moved in from last month, and one
  moved from earlier this month to a day still ahead, which had counted nowhere.
- **~~Editing a rule keeps its postponements, even one that no longer fits the new schedule.~~**
  Fixed on 2026-09-29. Saving an edited rule now drops, in the same transaction, any move whose
  occurrence is no longer one of the rule's days or whose new day would overtake the next
  occurrence. `stillFits` is the check, and `canPostpone` is built on it.
- **A capture app is tied to an account by its id, and a restore keeps ids.** Restoring a
  backup in which that id is a different account would send captured movements there. It needs
  an old backup and an account deleted and recreated since, so it is written down rather than
  guarded. Settings › Capture (part 3) shows each app's account by name, which is where it would
  be seen. A duplicate that arrives across a restart of the app is the one the same-minute memory
  can miss, because that memory is kept in the process.
- **No conversion, and that is now the answer rather than the gap.** D12 was answered on
  2026-09-09: **everything stays in euros**, a dollar instrument is entered at what it is worth,
  and there is no currency picker on the holding form. The `currency` column stays on the row —
  the portfolio still totals per currency, so an install that somehow holds two does not add
  them — but no conversion is built and none is planned. **D18 is what makes that sustainable
  rather than a corner being cut**: a company priced in dollars in New York is priced in euros in
  Frankfurt, and the holding tracks that listing, so the question never reaches the app. It comes
  back only for an instrument with no euro listing at all, and that is the day PRD §4.5 gets read
  again.
- **A fully sold holding disappears from the portfolio, and the owner chose that** (D17,
  2026-09-09). It has no quantity, so it has nothing to be worth, and a row worth 0,00 € under a
  heading about what is held is noise. The cost is real and is written down rather than argued
  away: realised profit on a closed position was recorded and unreachable. **#64 made it
  reachable**, in a collapsed *Closed positions* section at the foot of the portfolio. Walk step
  `i8` is still where the judgement gets made with the phone in hand. **One case falls between
  the two lists:** a holding whose units were corrected down to zero without a sale is neither
  open nor closed, because it has no quantity and no realised result. It is simply not shown,
  and a portfolio holding only such rows shows its empty state. The review of #64 found it; it
  predates that branch.
- **The portfolio is rebuilt in Kotlin whenever anything changes.** One query for holdings, one
  for *every* trade, one for every price, combined and folded. It is bounded by how many times
  the owner has traded rather than by how long they have used the app, and it is the honest shape
  for an aggregation SQL cannot express (see `positionFrom`). If a portfolio ever gets slow, the
  split is one query per holding's totals plus the fold only where a sale exists.
- **Prices are typed, and that is now the answer rather than the gap** (D20). No free provider
  carries European venues, and converting from a US listing cannot rescue a UCITS ETF, which has
  no US listing to convert from. `feat/prices-in-one-place` makes typing one screen, and **D25
  (2026-09-21) closed the last door**: the owner will not pay for a provider, so nothing about
  this is open any more.
- **Nothing checks that a symbol is real, and a symbol alone would not be enough anyway.**
  "NVDAA" is accepted and will simply never be priced. What 2026-09-17 added to that: a symbol
  that *is* real can still be ambiguous — `NVD` is NVIDIA in euros on four German venues and a
  Thai property developer in baht on Bangkok — so a provider asked for a bare symbol can price a
  holding with a different company's number, in a different currency. Whenever a provider lands,
  a holding gains a **MIC code** beside its symbol, and the editor asks which listing is meant
  rather than guessing. Known now rather than suspected.
- ~~**A trade carries no note in the form.**~~ **It does since #68**, and since #73 the note
  reaches the cash movement too, so a search finds *Bought NVDA* by its note. A note on a list row
  stops at `NOTE_MAX_LINES` (2). **A trade saved before #73 keeps a cash row with no note** until
  it is saved again. Nothing backfills it, for the same reason nothing backfills trades older
  than their cash.
- **A test that writes through a view model and then reads the database has to wait.** `save()`
  launches into `viewModelScope` and the write lands on Room's executor, so the next line races
  it: the flow's first emission can predate the row. It passes on a quiet machine and loses on a
  loaded one — `HoldingEditViewModelTest` and `HoldingPriceEntryTest` went red on CI on 2026-09-16
  having been green locally and on their own PR. The fix is the idiom the category tests already
  used: wait for `finished` before reading, or, where the write has no `finished` to wait on
  (`savePrice`), read the flow with a predicate rather than taking its first emission. Every site
  in the suite was corrected then; a new one is easy to write the racing way, and it will pass
  until it does not.
- **Do not branch off an unmerged branch when the merge will be a squash.** `main` squash-merges,
  so a stacked branch carries commits whose content arrives on `main` as one different commit,
  and rebasing then conflicts on every hunk. It happened on 2026-09-03 and the fix was to rebuild
  the branch as a single commit on the merged `main` — `git checkout -B <branch> origin/main`
  followed by `git checkout <old tip> -- .`, which works only because the stacked tree is a strict
  superset. Cheaper to wait for the merge.
- **The first migration this project shipped has now run on the owner's own data.** On
  2026-09-16 the phone came back to adb and `installDebug` went onto an install dating from
  2026-09-02 — that is, a **v1 database**, made before investments existed. It cold-started with
  no `FATAL` and no `AndroidRuntime` line, so `MIGRATION_1_2` and `MIGRATION_2_3` both ran in
  sequence over real accounts and real movements, and Room opened what came out. Until then every
  migration here had been proved only against a database a test had built. It is evidence rather
  than a guarantee — one install, one path — but it is the path that matters, and the pattern
  rule 9 asks for now has a device behind it.
- **The canvas overlaps itself on the tablet in landscape, and nobody has decided whether that is
  wrong.** The same launch rendered five bubbles stacked into two overlapping columns on a
  2560×1600 screen held sideways. Two explanations fit and a screenshot cannot separate them: the
  layout saved in September, made when there were four bubbles and the screen was upright, meeting
  a rotation it was never placed for — which is what ADR 0002 says happens, since a bubble keeps
  the size and position it was given — or the five-bubble default simply not fitting a short
  screen, which is the thing #44 resized the defaults to avoid. **Settings → Reset the bubble
  layout separates them in one tap**, and that tap is the owner's (C5). Walk step `b4` is where it
  gets judged.
- **Nobody has walked Phase 2, the canvas, the portfolio or transfers on a phone.** Every view on the Reports tab is
  verified by JVM tests and by nothing else: the ring's Expense/Income toggle, the account picker
  above the charts, the comparison columns and both its segments, and the trend line have never
  been rendered on a device. Neither has the bubble canvas, which is worse in kind rather than in
  degree — it is the app's front door and it is made of drag and pinch gestures, which is exactly
  what a Robolectric test can least honestly claim to have checked. **The walk itself is no longer
  the gap**: `docs/device-check.md` now covers all of it (r1–r19, and Walks 5 to 7), and what is
  missing is an hour. **The phone is no longer missing either**: it was offline to adb through
  most of 2026-09-16 — surviving `adb reconnect` and a server restart, and ending up enumerating
  with no serial at all — and came back after a replug, with the current build installed and
  cold-started on it. Transfers joined that list on 2026-09-16, and they are the worst
  of it to leave unwalked — a transfer that moves both balances correctly and still leaks into a
  chart looks completely normal on the screen that recorded it. On 2026-09-03 the phone reached adb once as `device` but was asleep, and
  every later attempt saw it absent or `offline` — over USB and over wireless debugging, whose
  mDNS advertised nothing. Screenshots need it awake and unlocked, and unlocking is the owner's
  (C5).
- ~~**A report's idea of "today" is fixed when its flow is built.**~~ **Not since #76.** The
  day is part of the Reports pipeline's key, so every section is asked again when it changes, and
  a preset's period is re-derived for the new day. A custom range stays where the owner put it.
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
- **~~No transfers, recurring rules or investments.~~** All three exist now, each with its own
  migration and migration test — transfers at v3, trades’ cash at v4, recurring rules at v5.
  Phase 3 is complete since `feat/backup` (#69).
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
- **~~Search folds case for ASCII only.~~** Fixed in #93: the text is matched in Kotlin,
  accents and case folded, after SQL has narrowed by every other filter. The cost is a pass over
  the narrowed rows per search, which a personal ledger does not notice; a normalised column
  would be the fix if one ever does.
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
- **~~Only schema v1 exists.~~** The database is on **v5**, and every step from v1 has a
  migration and a test that opens the result. `CentsDatabaseMigrationTest` is the pattern every
  later schema change follows (rule 9).
- **An undone deletion comes back with a new `createdAt`.** The row id, amount, date, note
  and classification are restored exactly; only the internal bookkeeping timestamp moves,
  and nothing in the app reads it. It would take a repository method that carries the
  original instant to preserve it.
- **The undo window is six seconds, counted by the buffer itself and held in memory.**
  Closing the app forgets the deletion. That is the intent: an undo that survives a
  restart is a recycle bin, which is a different feature.
- **A subscription with no reminders on it waits to be noticed.** Reminders are per rule and
  off until one is added, which is the right default for something that interrupts — but it does
  mean a variable bill with none set is only as visible as how often the Subscriptions screen
  gets opened. There is still no badge on the canvas.
- **A reminder fires when the daily job runs, not at a chosen time of day.** It is booked for
  around 09:00 and WorkManager decides the rest: Doze, battery saver and the system's own
  batching can move it by hours, and a phone that is off all day runs it when it comes back. It
  is a reminder rather than an alarm, and nothing in the app claims otherwise.
- **Turning notifications off in system settings leaves the reminders stored.** They are kept,
  not deleted, and the subscription editor says plainly that nothing will be shown — so turning
  them back on restores what was set rather than losing it.
- **Only the oldest waiting occurrence of each rule can be answered.** With three months of
  electricity behind you they are answered oldest first, one save at a time, and the row says how
  many are behind it. Answering out of order would move the rule's watermark past the ones before
  it, which is why the repository refuses rather than trusting the screen.
- **Skipping a waiting occurrence cannot be undone from the screen that did it.** Nothing was
  recorded, so there is nothing to restore — but the rule has moved past that period, and getting
  it back means moving the rule's start date, which is obscure. The confirmation is what stands
  in the way.
- **Generation runs when the app comes to the front, and when a rule is saved.** Those two,
  and nothing else. No WorkManager, no
  notification: a phone left untouched for a week records the rent when it is next opened, dated
  correctly. PRD §4.4's reminders are `feat/subscription-reminders`.
- **Editing a rule changes what it writes next, never what it has written.** Raise Netflix from
  12,99 to 14,99 and the charges already recorded keep the old price — which is correct, because
  that is what was paid — but there is no way to correct a past charge from the rule. The
  movement itself is editable, as any movement is.
- **The monthly and yearly figures are estimates, and a year is 365 days.** A weekly charge is
  counted 52.14 times a year rather than 52, which is the honest average and not the calendar:
  no leap years, and no notion of which months a quarterly charge actually lands in.
- **An account a subscription records into cannot be deleted** until the rule is deleted first,
  and a rule with charges behind it has to be stopped before it can be deleted. Two steps, both
  named on screen, and the alternative was a constraint violation reported as "try again".
- ~~**Nothing invalidates the screen at midnight.**~~ **Something does since #76.**
  - **`ui/util/Today`** holds the date as a `StateFlow`. It re-reads the clock on `refresh()`
    and emits only when the day changes.
  - **`MainActivity`** calls `refresh()` in `onStart`. While visible, it also calls it on
    `TIME_TICK`, `DATE_CHANGED`, `TIME_CHANGED` and `TIMEZONE_CHANGED`. `TIME_TICK` is the one
    that reliably carries midnight.
  - **Nothing in `Today` keeps time.** A timer sleeping until midnight never finishes under a
    fixed test clock. A test that wants midnight moves its clock and calls `refresh()`.
  - **What reads it:** Transactions, the account detail, Subscriptions and Reports, which are
    the screens that stay open and show a day or a month.
  - **What still reads the clock once:** the forms. They are short-lived, and a form left open
    across midnight keeps the date it was opened with, which is what someone typing expects.
  - **To walk:** leave Transactions open past midnight. *Today* becomes *Yesterday* within a
    minute, and on the 1st the month summary starts again.
- **The view-model tests let Room's threads into the view model, and that can race.**
  - **Why.** `Dispatchers.Main` in the tests is an `UnconfinedTestDispatcher`, so a view model
    that collects a Room flow can be running on Room's thread when the test acts. The state the
    action produces may then land a moment after the call returns. On the phone everything runs
    on the main thread, so this is a test hazard, not an app bug.
  - **The rule.** After an action, wait for the whole state it should produce with
    `uiState.first { … }` (or Turbine's `awaitStateThat`). Never read `uiState.value` on the
    spot when the view model combines a Room flow. A bare `first { }` is unbounded, so a state
    that never comes fails at `runTest`'s one-minute timeout. Turbine's wait gives up after 3 s,
    at the line that waited.
  - **Fixed in #111.** `CaptureViewModelsTest` read `value` on the spot. It failed CI three runs
    in a row on 2026-10-04, on #110, a branch that changed only Markdown, while `main` and local
    runs passed.
  - **Still exposed:** `CategoryEditViewModelTest` › *the same name is free on the other kind*
    timed out once in those three runs. `setName` there computes `nameTaken` from names that a
    Room collector writes on another thread. If the keystroke and the first load pass each
    other, the flag is left wrong for good, so waiting longer would not help. If it fails again,
    that is the cause, not the branch: re-run the job with `gh run rerun <id> --failed`, and give
    the fix a branch of its own.
  - **The fix at the root, not yet tried.** #111's review pointed at `createTestDatabase()`
    (`TestDatabaseFactory.kt`). It leaves Room on its own executors. Pinning the test database's
    query context to the test thread (`setQueryCoroutineContext`, or direct executors) would
    close every one of these races at once, the category one included. It touches every Room
    test, and Robolectric's test thread counts as the main thread, so it needs a branch and a
    full run of its own.
  - **Also from that review:** `CaptureViewModel.moveTo` finds the app being edited through the
    derived `uiState.value`, not through `editingPackage` as `requestStop` does. If the state
    lags, it returns without saving and says nothing. That cannot happen on the phone, where
    both run on the main thread, but reading `editingPackage` would make the two consistent.

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

> **2026-10-01: replaced** by Home and the tab capsule (D30, ADR 0003, `feat/ui-rework`). The rest
> of this entry is history.
>
> **2026-09-23: to be replaced.** Having lived with them, the owner does not much like the bubbles
> and means to change them later. Until then no time goes into the canvas: no fixes, no polish,
> no walk (`b1`–`b10` are skipped). The replacement is undecided and is the owner's to describe:
> the plain tab bar that ADR 0001 specified, or something new. When it is decided, it supersedes
> ADR 0002, and that ADR should say so.

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

### Choosing which charts show, and in what order (requested 2026-09-25)

Requested by the owner on 2026-09-25, to be placed during the **UI/UX pass**, the same one that
replaces the bubbles. It is not built, and nothing about where it lives is decided yet.

- **Every chart has a title.** The Reports tab shows a list of those titles.
- **The owner drags the list into the order they want** and the Reports tab shows the charts in
  that order.
- **Any chart can be hidden**, and shown again from the same list.
- **Where the list lives is open.** It could be the Reports tab itself, with an *Edit* mode
  where the owner sees the result while arranging, or Settings. The owner leans towards the
  Reports tab, but it is to be decided in the UI/UX pass.

What makes it cheap: each view on the Reports tab is already a self-contained
`ReportSection` (`ui/feature/reports/ReportSection.kt`), so an order and a set of hidden ones is
a list of section ids in DataStore, which already stores the home canvas layout. The iOS pattern
for it (rule 12) is an edit list with drag handles and a show/hide control on each row, as in
Control Center's *Customize*. This is the modest first step towards the dashboard above.
Reordering and hiding come first; resizing cards can come later.

### Hiding a tab that is rarely used (requested 2026-09-25)

The same idea applied to the app's sections. **Accounts** is the example given: accounts are
created at the start and then very rarely, so its tab could be hidden while it is not in use.
Decide it together with the bubble replacement, since both are about what the main navigation
shows. Two things to keep in mind:

- **Nothing may become unreachable.** A new movement needs an account, and the net worth lives
  on the Accounts tab. A hidden tab needs another way in, for example a row in Settings. Hiding
  must not delete any data or turn any feature off.
- **At least one tab must stay visible**, and hiding a tab is undone the same way it was done.

### What was saved or overspent, and the movements, inside Reports (requested 2026-09-27)

Requested by the owner on 2026-09-27, for the **UI rework to ADR 0003**, not before it.

- **A saved-or-overspent card on Reports.** The figure already exists: the *Net* row under
  *This month* on the Transactions list (*Balance* in Spanish), and *Net* in the period
  comparison. Both leave out transfer legs and trade cash. The request is to put it on the Reports
  page as a card of its own, following the period control. ADR 0003's rule 2 fits: lead with one
  figure in words, *You saved 312,40 €* or *You overspent 85,10 €*, rather than a signed *Net*.
  The Spanish label should change as well, because *Balance* reads as an account balance. A
  savings rate (saved ÷ income, *no income yet* rather than 0 %) was offered alongside it.
- **The movement list reachable from inside Reports**, as a pushed subpage or a sheet. Which of
  the two is still open, and the owner will decide it during the rework. The drill-downs from a
  slice, bar or point already hand the list a `TransactionFilter`, so the list itself does not
  change, only where it opens.
- **Neither is in ADR 0003's stills yet.** `10-reports.png` has no saved card, so the ADR should
  gain one when the rework starts.

---


### Deleting one Drive backup at a time

Asked 2026-10-01 while walking `n8`: from *Restore from Drive*, remove a single copy (swipe, with a
confirmation). Today only retention prunes, and Drive's *Delete hidden app data* removes them all.
Not built; the owner said later.
## Next concrete action

**2026-10-01: merge `feat/ui-rework` after its `/code-review high`, then Walk 19 on the Pixel**
— capture from a real bank notification, with the per-app words (Walk 20 `u5`). Requested for
later: deleting one Drive copy at a time from *Restore from Drive*.

*What follows was written before the rework and is kept for its lists of unwalked steps.*

**Walk the app on the phone, with [`docs/device-check.md`](docs/device-check.md) open.** Still
the owner's (C5), and still the biggest gap. **As of 2026-09-24 the phone has the current
`main`, and Drive is connected**, so `n6` is done as far as connecting. Start at `n7`:
- Walks 4 to 10 have never been walked.
- **Walk 11** (`n1`–`n11`) is new: everything from 2026-09-21 to 2026-09-23.
- **Walk 12** (`g1`–`g5`) is budgets (#80, #81).
- **Walk 13** (`h1`–`h6`) is groups (#86, #87).
- **Walk 14** (`i1`–`i3`) is the uncategorized inbox (#90).
- **Walk 15** (`j1`) is the Income and Expenses filters (#95), **Walk 16** (`k1`–`k3`) is TalkBack
  and the largest font (#96), and **Walk 17** (`p1`–`p3`) is postponing (#97).
- **Walk 18** (`f1`–`f2`) is the reshaped income-and-expenses chart (#101). **`f1`, tapping a
  column, is the owner's to check first**: it is the one part of that view no test can reach.
- **Walk 19** (`c1`–`c4`) is capture from notifications, **on the Pixel** with a real bank
  notification. `c3`, a real payment arriving, is what no test can check.
- If there is time for only one thing, make it **`n6`–`n9`**. The Drive backup is the one feature
  whose failure is silent until the day it is needed, and `n9` is the restore that #72 made
  possible on a phone with subscriptions.
- Seven days after first granting Drive access, **`n10`** checks that the *Testing*-mode expiry
  reads as *Reconnect* and not as a retry loop.

```bash
adb devices                  # awake, unlocked, authorised
./gradlew installDebug
```

Anything that goes wrong goes in [`FEEDBACK.md`](FEEDBACK.md) with its number, and that is the
next branch: an *Open* entry there outranks anything planned.

**What comes next when there is no feedback:**
- **A wave the owner has to choose.** Everything else left is a later roadmap phase
  (`CLAUDE.md` §9) or deferred work:
  - **Phase 5** is built: budgets (#80, #81), the inbox (#90) and notification capture
    (#105–#107), which feeds it. Capture waits on Walk 19 on the Pixel.
  - **Phase 6** is the widget, the Baseline Profile with Macrobenchmark, and accessibility. The
    first two add dependencies and run alone.
  - The dashboard of cards is in C7.
  - **What replaces the bubbles** (C7). The owner does not much like the canvas and will say
    what they want instead. Until they do, no time goes into it.
- Groups (#86, #87), the inbox (#90) and capture (#105–#107) are built. As of 2026-10-01 the
  owner has not chosen between Phase 6, the UI rework of ADR 0003 and the bubble replacement. **Open the next session by asking which one**, unless `FEEDBACK.md` has
  something *Open*.

**A cloud session cannot build locally** while the environment blocks `dl.google.com` (Google
Maven, where the Android Gradle plugin and AndroidX come from). CI still builds and tests every
PR. Allowing that host in the environment's network settings would let a session run the gates
before it pushes.

**Before running agents in Orca again, clean up:** the second wave's worktrees can be deleted
from inside Orca now that every branch in it is merged.

**Two questions the walk should settle**, both written into it rather than left to be
remembered:

- **`07`**: the app reopens on the section that was last open, not on the canvas. That question
  now belongs to whatever replaces the bubbles.
- **`g1`–`g5`**: whether a budget reads right. The "to come" part is the one most likely to
  surprise, because it counts a charge before it has happened, on purpose.

The canvas steps `b1`–`b10` are **not to be walked**: the owner is replacing the bubbles (C7).
