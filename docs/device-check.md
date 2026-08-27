# The device check

**What this is.** A walk through the app on a real phone, written so that someone can spot a
problem without knowing how the code works. Every item says what to tap, and then **what a
failure would look like** — that second part is the point.

**Why it exists.** The agent can build the app, install it, read logcat and take a screenshot.
It cannot use it. Everything below is a thing no test in this repo can catch, which is why it
is a document and not a test.

**How to use it.** Walk it with the phone in your hand. Anything that goes wrong gets written
in [`FEEDBACK.md`](../FEEDBACK.md) — the number is enough to identify it, so *"05: the keypad
showed 2.500,00"* is a complete entry.

**When to run it.** After any run of branches built without a device attached, and before
closing a phase. First run: 2026-08-21, closing Phase 1, after four branches had been built
blind — `feat/account-deletion`, `chore/single-currency`, `feat/swipe-to-delete` and
`feat/transaction-filters`.

```bash
adb devices                  # the phone is there and authorised
adb uninstall io.github.anderescaray.cents    # only when a *fresh install* is the point
./gradlew installDebug
```

> **A caveat before judging speed:** `installDebug` is a debug build, and debug builds are
> meaningfully slower than release. Jank here is not evidence of anything (`CLAUDE.md` §5).

---

## Walk 1 — the exit criteria

These are the checks that close a phase, and they run **in order**: each one leaves the state
the next one needs.

### 01 · The first screen is empty, and says so in words

Transactions and Accounts both empty, Settings → Categories empty. Empty is the correct state
on a fresh install, not a bug.

> **Wrong looks like** a `Cash` account, or any category you did not type. That would mean the
> removal of first-run seeding regressed (D1).

### 02 · The front door leads somewhere

On Transactions, tap **+** with no account existing. It should walk you into creating one.

> **Wrong looks like** an empty account field with no way forward. This path used to be an
> edge case and is now the first thing anyone sees.

### 03 · Creating an account never asks which currency

Name, type, colour, opening balance on the keypad. The currency row shows `EUR` and is **not
tappable**.

> **Wrong looks like** a currency picker offering USD and GBP, or a currency that is not EUR.
> Either means the single-currency change did not take (D2).

### 04 · Categories are yours to make

Settings → Categories. Create one expense and one income category, colour them, and drag one
by its grip to reorder it.

> **Wrong looks like** the drag not picking up, or the order not surviving leaving the screen
> and coming back.

### 05 · The keypad counts in cents

Record a movement. Tapping `2` `5` `0` `0` must read **25,00 €**. Set a date that is not today,
and write a note.

> **Wrong looks like 2.500,00 €.** This is the single most important line in this document.
> Every amount in the app is an integer number of cents (rule 1), and this is where that
> assumption would visibly break.

### 06 · Balances add up, checked by hand

Record at least three movements, mixing income and expenses. Open Accounts and do the
arithmetic yourself: opening balance, plus income, minus expenses.

> **Wrong looks like** being off by any amount at all. Balances are computed from the
> movements and never stored, so a wrong number means a wrong query rather than bad data.

### 07 · It survives being closed properly

Swipe the app away from the recents list — not just Home — and reopen it. Accounts, categories,
movements and balances all still there.

> **Wrong looks like** anything missing.

---

## Walk 2 — features built without a device

No particular order. This is where a problem is most likely to be hiding.

### d1 · Swipe a row left, slowly

A red **Delete** appears from under the row. Let go about a third of the way: it parks open.
Tap anywhere on the row: it closes. Swipe most of the way across and release: it fires, and a
confirmation opens.

> **Judge, don't just check.** Do the two thresholds feel right, or does it fire when you
> meant to scroll? They are two constants at the bottom of `CentsSwipeableRow.kt` and trivially
> changed. This is exactly what a test cannot answer.

### d2 · The grip still reorders while the body still swipes

On the same list, drag a row vertically **by its grip**, then swipe the **body** of a row
sideways. Neither gesture should steal the other.

> **Wrong looks like** a vertical drag on the grip starting a sideways swipe, or the list
> scrolling when you meant to swipe.

### d3 · Deleting an account that holds movements asks a real question

A sheet naming **how many movements** are at stake, offering to move them elsewhere or to
delete both — and the destructive answer asks again, on its own (D7).

> **Wrong looks like** it simply deleting, or the reassign option vanishing when it is the only
> account. It should be **visible but disabled, with the reason**.

### d4 · Search finds things by category and by note

Type part of a category name into the search field, then part of a note, then tap the round
**✕** to clear it.

> **Wrong looks like** characters being dropped or reordered while typing quickly, or the ✕
> being hard to hit. Both were fixed without a device to check them on.

### d5 · Filters → This month → Between two dates

In that exact order. Two date rows, **From** and **To**, must appear underneath.

> **Wrong looks like** nothing happening and the tick staying on **This month**. That was a
> real dead end in the first version of `feat/transaction-filters` — custom dates were
> unreachable once any preset had been chosen — so it is worth confirming the fix on hardware.

### d6 · A filtered list says what it found

Apply a filter that matches something: the monthly summary is replaced by **Matches**, a count
and the totals of exactly the rows on screen. Then narrow it until nothing matches.

> **Wrong looks like** an empty screen that reads the same as having no movements at all. It
> should say nothing matched, and offer a way back.

### d7 · "Show everything again" really does

Type something in the search field, open Filters, tap **Show everything again**, then
**Apply**.

> **Wrong looks like** the list still narrowed to what you typed.

### d8 · Deleting a movement can be taken back

Open a movement, delete it, and watch for the undo banner on the list. It lasts about six
seconds.

> **Wrong looks like** no banner, or the movement coming back changed.

---

## Walk 3 — judgement calls

No right answers here. These are design decisions the agent made alone, and they are far
cheaper to reopen now than after the next phase is built on top of them.

### e1 · Both themes hold up

Switch the phone between light and dark and walk the same screens again.

> **Wrong looks like** grey-on-grey text, a section keeping its light background, or the red
> delete losing its punch.

### e2 · It reads as iOS, not as Material

Inset grouped lists, large titles that collapse on scroll, alerts as centred cards, push-and-pop
transitions sliding sideways (ADR 0001).

> **Wrong looks like** a floating action button, a ripple on tap, a Material switch, or a
> purple accent that came from nowhere.

### e3 · The filter form is the right shape

Six sections on one screen: kind, date, account, category, amount, clear. Is that one question,
or does it feel like a settings screen?

### e4 · The amount filter makes sense to a human

Filters → Amount is typed in **cents**, like the keypad: 2500 means 25,00 €. The line
underneath says the range back in real money.

> If that reads as a trap rather than a convention, it should become a keypad like the movement
> form has.
