# The device check

**What this is.** A walk through the app on a real phone, written so that someone can spot a
problem without knowing how the code works. Every item says what to tap, and then **what a
failure would look like** — that second part is the point.

**Why it exists.** The agent can build the app, install it, read logcat and take a screenshot.
It cannot use it. Everything below is a thing no test in this repo can catch, which is why it
is a document and not a test.

**How to use it.** Walk it with the phone in your hand. Anything that goes wrong gets written
in [`FEEDBACK.md`](../FEEDBACK.md) — the number is enough to identify it, so *"05: the third
decimal went in anyway"* is a complete entry.

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

The opening balance is the app's **second** keypad, and since 2026-08-30 it obeys the same
rules as the movement one: type `1` `2` `5` `,` `5` `0` and it must read 125,50 €, not
1.255,00 €. Give the account a negative opening balance too — the sign toggle in front of a
half-typed amount must read `−125,5 €`, with nothing lost when the sign flips.

> **Wrong looks like** a currency picker offering USD and GBP, or a currency that is not EUR —
> either means the single-currency change did not take (D2) — or the two keypads disagreeing
> about what a digit means, which is worse than either of them being wrong on its own.

### 04 · Categories are yours to make

Settings → Categories. Create one expense and one income category, colour them, and drag one
by its grip to reorder it.

> **Wrong looks like** the drag not picking up, or the order not surviving leaving the screen
> and coming back.

### 05 · The keypad is typed in euros, and the comma is what asks for cents

Changed 2026-08-30. It used to count in minor units — `2` `5` `0` `0` for 25,00 € — which cost
two taps on `0` for every round number. The owner asked for euros instead (`FEEDBACK.md`), so
this step now checks the opposite convention.

Record a movement. Tapping `2` `5` must read **25 €**, and saving it must produce a movement of
25,00 € in the list. Then start again and tap `2` `,` `5`: it must read **2,5 €** and save as
**2,50 €**. Now try each of these, which are the rules that are easy to get wrong:

- `1` `2` `,` `5` `0` `9` — the third decimal must **not** go in. The screen stays on 12,50 €.
- `2` `,` `5` `,` `0` — the second comma must do nothing; the `0` lands after the first, giving
  2,50 €.
- `,` `5` `0` on an empty amount — must read 0,50 €.
- Backspace over `1` `2` `,` `5` — one tap must remove one character: 12,5 then 12, then 12.
  Typing `3` at that point must give 123 €, not 12,53 €.

Set a date that is not today, and write a note.

> **Wrong looks like 2.500,00 € from `2` `5` `0` `0`** — the old convention still in force — or
> a third decimal being swallowed and rounded rather than refused, or backspace clearing more
> than the one character it removed on screen. This is the single most important line in this
> document: every amount in the app is an integer number of cents (rule 1), and this is where a
> hundredfold error would enter it.

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

### e4 · The amount filter agrees with the keypad

Changed 2026-08-30, with `05`. Filters → Amount is typed in **euros with a comma**, exactly like
the movement keypad: `25` means 25 €, `2,5` means 2,50 €. The line underneath still says the
range back in full — "Between 25,00 € and 50,00 €" — so a half-typed bound is readable.

Type `2`: the footer reads "2,00 € or more". Now type the comma — it must stay on screen
rather than vanishing under your finger — and then `5`, which must take the footer to
"2,50 € or more". A third decimal must be refused here too, exactly as on the keypad.

> **Wrong looks like** the comma disappearing the instant it is typed — the field is a text
> field rather than the keypad, and a bound derived back from the stored number would erase a
> separator with nothing behind it yet. Also wrong: 25 in this field meaning 0,25 € while 25 on
> the keypad means 25 €. The two must agree, which is the whole point of the change.

---

## Walk 4 — the charts (`feat/reports-charts`)

Added 2026-08-27. **Nothing in this section has ever been on a phone**: the branch was built
with no device attached, and every judgement below is one a test cannot make. Walk it with at
least three expenses in two different categories, and one income, so the charts have something
to draw.

### r1 · The ring is legible, not a colour wheel

Reports opens on **Month**. The ring should read at a glance: distinct slices, a hole big
enough that thin slices are still visible, and the rows beneath it in the same colours.

> **Wrong looks like** slices too thin to see, two categories you cannot tell apart, or a row
> whose swatch does not match its slice. The ring is 220 dp tall with a 104 dp hole — two
> constants at the bottom of `ReportCharts.kt`.

### r2 · The percentages and the total agree with each other

Add the slice amounts by hand: they must come to the **Total spent** row, and the percentages
to 100.

> **Wrong looks like** them not adding up. Uncategorized spending is deliberately its own
> slice; if you have any, it is the grey one with no name of its own.

### r3 · The period selector actually re-asks the question

Move through **Week → Month → Quarter → Year**. Both charts must change together, and Year
should show twelve month columns, including empty ones as gaps in the bars rather than as
missing months.

> **Wrong looks like** one chart updating and the other not, or a year with only the months
> that had movements in them.

### r4 · Custom opens on the period you were already looking at

From **Month**, tap **Custom**. The two date rows must show the month you were just looking at
— not today twice, and not blank. Then change one of them and watch both charts follow.

> **Wrong looks like** the d5 dead end one screen over: dates that cannot be reached, or a
> range that resets every time you touch it.

### r5 · Green is money in, red is money out — in both themes

Switch the phone to dark and back. The two column colours must stay the same two colours the
transaction list uses for amounts, and the axis labels must stay readable.

> **Wrong looks like** a purple or teal column, which would mean Vico's Material defaults
> leaked through (ADR 0001, and check e2).

### r6 · Scrolling the tab stays smooth

Scroll Reports up and down a few times with a year selected.

> **Wrong looks like** the charts stuttering or visibly re-animating on every scroll — that
> would mean the chart model is being rebuilt during recomposition rather than only when the
> totals change. Remember the debug-build caveat at the top of this file before judging.

### r7 · A slice opens the movements behind it, and they add up

Tap a category **row** under the ring. The Movements tab should open already narrowed, and
the amounts on it should add up to the amount that row showed. Then come back and tap the
**ring itself**, on the same slice.

> **Wrong looks like** the row and the ring disagreeing about which category you tapped —
> that would mean the hand-rolled hit test is off, since Vico does no hit testing of its own.
> Also wrong: a list that includes another month, another category, or an income among the
> expenses. Tapping the middle of the ring or outside it is *meant* to do nothing.

### r8 · Coming back from a drill-down leaves the app somewhere sensible

After r7, tap **Reports** again, then **Movements** again.

> **Wrong looks like** two copies of the movements list on the back stack — one filtered, one
> not — or the filter silently still applied when you did not expect it. The filter is
> deliberately in memory only, so it survives moving between tabs but not restarting the app.
