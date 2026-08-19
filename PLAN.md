# PLAN.md — From empty folder to running app

Operational guide. **Part A**: what to install and configure. **Part B**: how the project
is run, first solo and then with several agents in parallel.

Companion documents: [`CLAUDE.md`](CLAUDE.md) (technical rules) ·
[`docs/PRD.md`](docs/PRD.md) (what we build and why).

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

It bundles the **Android SDK**, the **emulator**, `adb` and **its own JDK 21** — which
solves the problem of the locally installed Java 11 being too old for modern Android. Do
not install a separate JDK.

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

Even with a single agent. By the time there are five, the process is already worn in and
does not have to be invented under pressure.

**Exit criteria for this phase** — do not parallelize until all four hold:

- [ ] The app installs and runs on your real phone
- [ ] You can create accounts, categories and transactions, and they survive a restart
- [ ] Balances are correct
- [ ] CI is green on every PR (build + tests + lint)

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
| **2. Analytics** | 2–3 + Orca | Decompose, arbitrate, accept merges | The charts answer your questions |
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

Then take the first unchecked branch in C3 and work it end to end:
`branch → implement → tests → gates → PR → wait for CI → hand back`.

## C2. What is built and merged

| Area | State |
|---|---|
| Toolchain | Gradle KTS + version catalog, Hilt + KSP, detekt (`dev.detekt` 2.x), Spotless/ktlint, GitHub Actions running build + tests + lint on every PR |
| Database | Room v1: `accounts`, `categories`, `transactions`. Balances computed in SQL, foreign keys `RESTRICT`, schema exported to `app/schemas/` |
| Domain | `Money` (exact minor units + currency), `Account`, `Category`, `Transaction`, repository interfaces with Room implementations |
| Design system | iOS-flavoured tokens and components — see [`docs/decisions/0001-ios-flavored-ui.md`](docs/decisions/0001-ios-flavored-ui.md) |
| Navigation | Four tabs, type-safe routes, push/pop transitions |
| Transactions | List grouped by day with a monthly summary, and one form that adds and edits: minor-unit keypad, date picker, note, and a delete that can be undone from the list |
| Categories | The owner's own: nothing is seeded, and they are created, renamed, recoloured, dragged into order, archived and deleted from Settings → Categories |
| Tests | 76, all on the JVM (Robolectric), so CI covers the database and the UI without a device |

## C3. Remaining Phase 1 work

One branch and one PR each, in this order. They touch overlapping files, so they stay
serial — this is still the single-agent phase.

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
- [ ] **`feat/accounts`** — the Accounts tab for real: list with balances, create, edit,
      archive, and an account detail showing only its movements plus its own income and
      expenses.
      *Done when:* balances shown match the transactions that produced them.
- [ ] **`feat/transaction-filters`** — search and filters on the list: date range, account,
      category, kind, amount, note text.
      *Done when:* "what did I spend on restaurants in July" is answerable in the app.

**Phase 1 is finished when the four exit criteria in Part B §2 hold.** Do not start Phase 2
before that, and do not parallelize before that.

## C4. Known gaps, so they are not mistaken for bugs

- **Installs made before `feat/categories` still hold the eleven categories the app used
  to seed.** Nothing removes them automatically, because deleting rows that may already
  classify movements is the owner's call: they can be deleted or archived one by one in
  Settings → Categories. A fresh install has none.
- **Category subcategories and icons are not built.** The schema has `parentId` and
  `iconKey` columns and the PRD lists both as optional; the editor writes neither. One
  consequence to fix when subcategories arrive: deleting a category runs a bare `DELETE`,
  and the self-referencing foreign key is `RESTRICT`, so deleting a parent that still has
  children would throw out of the view model with nothing catching it. Unreachable today
  because nothing can create a child.
- **Accounts and Reports tabs are placeholders.** They render the design system with
  invented content and read nothing from the database.
- **No transfers, recurring rules or investments.** Those are PRD phases 3 and 4, and the
  schema has no tables for them yet: they arrive with a tested migration.
- **Cross-currency totals are not supported.** `Money` refuses arithmetic between different
  currencies on purpose; a conversion step has to be designed when a second currency
  appears.
- **Only schema v1 exists**, so there is no migration test yet. The first schema change must
  ship one (rule 9).
- **An undone deletion comes back with a new `createdAt`.** The row id, amount, date, note
  and classification are restored exactly; only the internal bookkeeping timestamp moves,
  and nothing in the app reads it. It would take a repository method that carries the
  original instant to preserve it.
- **The undo window is six seconds and lives in memory.** Leaving the list or closing the
  app forgets the deletion. That is the intent: an undo that survives a restart is a
  recycle bin, which is a different feature.

## C5. What only the owner can do

The agent may install the APK, launch the app, read logcat and take screenshots — nothing
else on the phone (rule 13). So these are always handed back:

- Tapping through a flow to judge whether it feels right.
- Anything needing a device setting, an unlock, or a permission dialog.
- Reviewing and merging every PR.

---

## Next concrete action

**`feat/accounts`**, as described in C3: the Accounts tab for real — list with balances,
create, edit, archive, and an account detail showing only its movements. It is also where
the seeded *Cash* account gets decided (see the note in the `feat/categories` entry), so
plan that first-run question with it.
