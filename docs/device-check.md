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
`feat/transaction-filters`. Second run: 2026-09-08, closing Phase 2, after all six PRD §4.3
views, the account picker and the direction toggle were built — and extended on 2026-09-09 to
cover the two things that landed after that text was written: the year-on-year segment (#42),
and the canvas of bubbles the app now opens on (#41), which changes the first thing this walk
sees. Extended again on 2026-09-09 with **Walk 6**, for the investments section (#44) — built
the same day, on a phone nobody has held it on either — and on 2026-09-16 with **Walk 7**, for
transfers and for the cash a purchase spends — the features here whose failure is invisible on
the screen that records them: a transfer can move both balances correctly and still leak into
every chart, and a purchase can look perfectly recorded while the account it was paid from never
notices.

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

### 01 · The first screen is the canvas, and everything behind it is empty

Changed 2026-09-08. The app no longer opens on a list: a fresh install opens on the **canvas of
four bubbles** — Transactions, Accounts, Reports, Settings — with no tab bar under them
(ADR 0002). Walk 5 is the canvas itself; here it is only the way in.

Open each bubble in turn. Transactions and Accounts are both empty, Settings → Categories is
empty. Empty is the correct state on a fresh install, not a bug.

> **Wrong looks like** a `Cash` account, or any category you did not type — that would mean the
> removal of first-run seeding regressed (D1) — or the app opening straight onto a tab, which
> would mean the canvas is not the start destination.

### 02 · The front door leads somewhere

Open **Transactions** from the canvas and tap **+** with no account existing. It should walk you
into creating one.

> **Wrong looks like** an empty account field with no way forward. This path used to be an
> edge case and is now the first thing anyone sees after the canvas.

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

It reopens on **the section you were last in**, not on the canvas — that is what D8 asked for,
and it means the bubbles are usually not what you see on launch. It is one flag to change if it
reads wrong in the hand, so say so in `FEEDBACK.md` either way (C4).

> **Wrong looks like** anything missing, or the app reopening on a section you were never in.

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

## Walk 4 — the charts and analytics (Phase 2)

Added 2026-08-27, extended 2026-09-08 to cover all six PRD §4.3 views, the account picker,
the direction toggle and all drill-downs. **Nothing in this section has ever been seen on a
phone**: the views were built across multiple branches with no device attached, and every
judgement below is one a test cannot make.

Walk it with at least **two accounts**, at least **three expenses in two different categories**
(mixing weekdays and weekends), and at least **one income movement**, so every chart, picker
and verdict has real data to draw.

### r1 · The ring is legible, not a colour wheel

Reports opens on **Month**. The ring should read at a glance: distinct slices, a hole big
enough that thin slices are still visible, and the rows beneath it in the same colours.

> **Wrong looks like** slices too thin to see, two categories you cannot tell apart, or a row
> whose swatch does not match its slice. The ring is 220 dp tall with a 104 dp hole — two
> constants at the bottom of `SpendingSection.kt`.

### r2 · The percentages and the total agree with each other

Add the slice amounts by hand: they must come to the **Total spent** row, and the percentages
to 100.

> **Wrong looks like** them not adding up. Uncategorized spending is deliberately its own
> slice; if you have any, it is the grey one with no name of its own.

### r3 · The period selector re-asks every question

Move through **Week → Month → Quarter → Year**. Every section on the tab must change together:
the ring, monthly flow, balance line, weekday bars, period comparison, and category trend.
Year should show twelve month columns on the flow chart (including empty ones as gaps in the
bars rather than as missing months), and the category trend should switch its bucket size to
follow the preset (weeks for Week, months for Month, quarters for Quarter, years for Year).

> **Wrong looks like** one chart updating and another not, a year with only the months that had
> movements in them, or the trend line keeping monthly points under a Year selection.

### r4 · Custom opens on the period you were already looking at

From **Month**, tap **Custom**. Two date rows appear: **From** and **To**. They must show the
month you were just looking at — not today twice, and not blank. Then tap one of them, pick a
different date, and watch every section follow. Try picking a **From** date that is after
**To**: the app must straighten the dates rather than refusing them or drawing blank charts.

> **Wrong looks like** the d5 dead end one screen over: dates that cannot be reached, a range
> that resets every time you touch it, or an inverted range producing a blank screen with no
> explanation.

### r5 · Green is money in, red is money out — in both themes

Switch the phone to dark and back. The flow chart columns (green income, red expenses), the
balance line (green above opening balance, red below), the weekday bars (red), and the axis
labels must all stay readable and consistent with the app's palette.

> **Wrong looks like** a purple or teal column, which would mean Vico's Material defaults
> leaked through (ADR 0001, and check e2).

### r6 · Scrolling the tab stays smooth with all six sections

Scroll Reports up and down a few times with a year selected and all six sections loaded.

> **Wrong looks like** the charts stuttering or visibly re-animating on every scroll — that
> would mean chart models are being rebuilt during recomposition rather than only when totals
> change. Remember the debug-build caveat at the top of this file before judging.

### r7 · A slice opens the movements behind it, and they add up

Tap a category **row** under the ring. The Transactions tab should open already narrowed, and
the amounts on it should add up to the amount that row showed. Then come back and tap the
**ring itself**, on the same slice.

> **Wrong looks like** the row and the ring disagreeing about which category you tapped —
> that would mean the hand-rolled hit test is off, since Vico does no hit testing of its own.
> Also wrong: a list that includes another month, another category, or an income among the
> expenses. Tapping the middle of the ring or outside it is *meant* to do nothing.

### r8 · Coming back from a drill-down leaves the app somewhere sensible

After r7, tap **Reports** again, then **Transactions** again.

> **Wrong looks like** two copies of the movements list on the back stack — one filtered, one
> not — or the filter silently still applied when you did not expect it. The filter is
> deliberately in memory only, so it survives moving between tabs but not restarting the app.

### r9 · The ring shows income too, and flips cleanly

Above the ring, tap the **Income** segment on the Expenses/Income toggle.
The section header must switch from **Where the money went** to **Where the money came from**,
and the total row from **Total spent** to **Total earned**. The ring redraws with income
categories in their own colours. If you have no income in the period, it shows an empty message
("Nothing came in during this period.").

Now scroll down until the ring is completely off-screen, then scroll back up: the toggle must
still be on **Income**. Then tap an income slice or row: the Transactions tab must open filtered
to that category and strictly to **Income** movements.

> **Wrong looks like** the header still saying "Where the money went" or the total saying
> "Total spent" over income, slices mixing colours during the transition, the toggle quietly
> reverting to Expenses when scrolled off-screen, or an income slice opening expense movements.

### r10 · The account picker narrows every view at once

Look just below the period selector. With at least two accounts in the database, an **Account**
section appears with **All accounts** followed by each account by name (with an archived badge
if archived).

Tap one specific account. Every section below must narrow to that account's data simultaneously:
the ring, monthly flow, balance line, weekday bars, period comparison, and category trend.
Then tap **All accounts** to restore the full picture.

> **Wrong looks like** the account picker appearing when you only have one account (it should
> hide itself entirely), an archived account with history missing from the choices, or one
> section updating while another still shows totals for all accounts.

### r11 · An account drill-down never leaks another account's money

In the account picker, select one specific account. Then tap any category row under the ring.
The Transactions tab opens filtered to that category and period. Look at the movements listed:
every movement must belong to the selected account. Tap the **Filters** button at the top right:
the **Account** row must show that specific account ticked, not "Every account".

> **Wrong looks like** a drill-down opening movements from other accounts, or dropping the
> account filter when handing over to the Transactions tab. A report for one account must never
> drill into everyone's money.

### r12 · The balance line starts from what you had, not from zero

Scroll to **Balance over time**. Look at the line and the three rows beneath it: **Started
with**, **Ended with**, and **Change**.

The line must start from the balance carried into the period (opening balances plus all earlier
movements), not from zero. The y-axis must frame the line tightly with a small margin above and
below — it must not be dragged down to zero, which would turn a 200 € move on a 5.000 € balance
into a dead flat line at the top of the box.

The line is green above the starting balance and red below it. Check the arithmetic: **Started
with** plus **Change** must equal **Ended with**. If viewing the current month ending today on
"All accounts", **Ended with** must match the total balance on the Accounts tab.

> **Wrong looks like** the line starting at 0,00 €, a flat line at the very top of a tall empty
> box, **Change** glowing green when your balance dropped, or **Ended with** disagreeing with
> the Accounts tab balance for today.

### r13 · Spending by weekday answers whether weekends wreck you

Scroll to **Spending by weekday**. Seven red columns show your spending for each day of the
week. Below the chart, **Total spent** must match the ring's expense total for the same period.

The seven day rows are listed in your device locale's day order (e.g. Monday to Sunday), each
with its total. Read the footer beneath the section: it compares spending *per day* on the
weekend (Saturday and Sunday averaged) against weekdays (the other five averaged), and gives
the verdict.

> **Wrong looks like** the seven day rows not adding up to the Total spent row, income movements
> being included in the bars (the chart is strictly expenses), or the footer comparing raw
> two-day weekend totals against raw five-day weekday totals rather than spending per day.

### r14 · A weekday row drills down to that day of the week

In **Spending by weekday**, tap a day row that has spending (e.g. **Saturday**).
The Transactions tab opens immediately. The top card says **Matches** with the count and sum of
movements recorded on that weekday in the period.

Tap **Filters**: the **Weekday** section must show **Saturday** ticked, with the date range
still set to your report's period. Tap **Show everything** on the Matches card to clear the
filter.

> **Wrong looks like** tapping a day row doing nothing, opening movements from other days of the
> week, or the Weekday filter being applied invisibly without appearing on the Filters form.
> Also wrong: tapping the chart columns trying to drill down — only the day rows below are
> tappable.

### r15 · This period against the last compares like with like

Scroll to **This period against the last**. Two pairs of opposing bars appear: Income and
Expenses. The past period is grey; the current period is accent blue.

Check the three change rows: **Income**, **Expenses**, and **Net**. Check the tinting:
- Income: earning more is green, earning less is red.
- Expenses: spending *more* is **red**, spending *less* is **green**.
- Net: a higher net is green, a lower net is red.

If the current period is still running (e.g. today is mid-month), check the footer: it must
state that the period is compared against the same elapsed stretch of the previous period.

> **Wrong looks like** heavier spending showing as green "+50,00 €" (spending more is worse,
> not better), or a partial month being compared against the entire previous month (which
> would falsely report spending down 80% early in the month).

### r16 · The previous period row opens the past, not the present

At the bottom of **This period against the last**, tap the row labelled **Movements in the
previous period**. The subtitle shows the exact date range of the comparison period.

Tapping it opens the Transactions tab narrowed to that previous date stretch. Check the dates on
the movements: they must all fall within that previous range. If an account was chosen in the
picker, all movements must belong to that account.

> **Wrong looks like** the row opening the current period instead of the previous one, opening
> the full month when the comparison was clamped to partial dates, or failing to carry the
> selected account filter.

### r17 · One category over time follows the category you pick

Scroll to **One category over time**. The chart shows a line across twelve buckets (months if
on Month preset, weeks if on Week preset), drawn in that category's custom colour.

Tap the category row (**Tap to follow a different category**). A list of your categories
expands inline, with a tick next to the currently selected one. Tap a different category: the
list collapses, the line redraws in the new category's colour, and the summary rows update:
**This period** and **Against the usual** (comparing this period to the average of the earlier
eleven buckets).

Switch to another tab (e.g. Accounts) and come back to Reports. The watched category must still
be the one you just selected.

> **Wrong looks like** the line drawn in a generic grey or blue instead of the category's
> colour, the choice list failing to expand or collapse, the selection resetting when switching
> tabs, or "Against the usual" comparing against only the single previous month instead of the
> eleven-month average.

### r18 · Clearing any drill-down is one tap from the list

From any section (the ring, a weekday row, or the previous period row), drill down into the
Transactions tab. The list shows the filtered movements with the **Matches** card.

Tap **Show everything** at the bottom of the Matches card. The list instantly returns to the
full, unfiltered movement history, the search box is cleared, and the badge on the Filters
button disappears. Tap **Back** (system gesture): since the movements list is a section root,
you land back on the **canvas** — not on Reports, and not outside the app.

> **Wrong looks like** having to open the Filters screen and clear checkboxes by hand, stale
> search text lingering after clearing, pressing Back cycling through multiple copies of the
> movements list on the back stack, or Back closing the app — which is what it used to do
> before the canvas became the floor of the stack (b7).

### r19 · This period against last year is a second question, not a second answer

Added 2026-09-08 with #42. In **This period against the last**, tap the second segment,
**Same period last year**.

The header must change to **This period against last year**, the grey legend to **Same period
last year**, and the drill-down row at the foot to **Movements in the same period last year**.
The bars and all three change rows redraw against the year-ago period. Flip back and forth a
few times: it must answer instantly, because both comparisons are read at once rather than one
query being re-run on each flip.

Then check the two things the arithmetic can get wrong:

- **A running period is clamped on both sides.** On the ninth of a month, "this period" is nine
  days, and it must be compared against the *first nine days* of the same month last year — the
  footer says so. Against the whole month it would read as spending down ~70% for most of every
  month.
- **The drill-down goes back a year.** Tap the row: the movements must all fall inside last
  year's range, and if an account is chosen in the picker they must all belong to it.

> **Wrong looks like** the segment changing the words but not the numbers, the footer still
> saying "the previous one" under the year-ago segment, a February compared against a stretch
> that is not February (leap years are the case that breaks this), or the row opening the
> previous *period* while the chart shows last *year*.

---

## Walk 5 — the canvas of bubbles (`feat/bubble-launcher`)

Added 2026-09-08 with #41, and **none of it has been on a phone**: it is the app's front door,
it is built out of drag and pinch gestures, and a gesture is the thing this repo's tests can
least honestly claim to have checked. [`ADR 0002`](decisions/0002-bubble-launcher.md) is what it
is supposed to do; below is how to tell whether it does.

### b1 · Four bubbles, named, drifting gently

The app opens on the canvas: **Transactions**, **Accounts**, **Investments**, **Reports**,
**Settings** — five since the portfolio arrived — staggered rather than in a grid, each drifting
with a small continuous motion. No tab bar under them.

> **Wrong looks like** bubbles that visibly jump into place a moment after the screen appears
> (they are drawn only once the stored layout has been read, exactly so that cannot happen), a
> bubble under the status bar clock, or two of them overlapping before anybody has dragged one.
> The five are slightly smaller than the four used to be — that is deliberate, and it is what
> keeps them clear of each other when the phone is turned on its side (b4).

### b2 · A bubble stays where you put it

Drag each bubble somewhere deliberate — one hard against a corner, one to the bottom edge.
Then swipe the app away from recents and reopen it. Every bubble is where you left it.

Try to drag one off the screen: it must stop at the edge with the whole circle still visible,
and the one at the bottom must not hand your drag to the system gesture bar.

> **Wrong looks like** a bubble half off the screen, a bubble that springs back after you lift
> your finger, or an arrangement that resets on restart. **Bubbles are allowed to overlap** once
> you have dragged them — that is deliberate, not a bug (C4).

### b3 · Pinch resizes, with a floor and a ceiling

Pinch a bubble larger and smaller. It has both a smallest size — never below a comfortable tap
target — and a largest, a little under two thirds of the screen's shorter side. Both limits
should feel like limits, not like the gesture breaking.

> **Wrong looks like** a bubble you can shrink to a dot you can no longer hit, or grow until it
> covers the others. If one ever does become unusable, Settings → **Reset the bubble layout**
> (b8) is the way out, and reaching for it counts as a finding worth writing down.

### b4 · Turning the phone keeps a bubble the size it was in your hand

Arrange the bubbles, then rotate the phone to landscape and back. A bubble must be about the
same physical size in both, and still whole and on screen — its size is measured against the
screen's **shorter** side for exactly this reason.

> **Wrong looks like** a bubble taller than the landscape screen, one cropped by an edge, or an
> arrangement that comes back rearranged after rotating twice.

### b5 · The tap is one motion, and it is quick

Tap a bubble. It grows into the section — one continuous shape, no sideways slide — and the
section fades in over it, all comfortably inside half a second.

Now tap one bubble and immediately tap a *different* one. You must land in the section you
tapped first; the second tap must not move the growing shape to another bubble.

> **Wrong looks like** a cross-fade instead of a growing shape, a horizontal slide arguing with
> the expansion, a stutter partway through, or landing in a section you did not tap.

### b6 · Inside a section the tab bar is back, and the grabber puts it away

Inside any section the bottom tab bar is showing, and it moves you between sections without
going back to the canvas. Tap the small **grabber** above it: the bar collapses and the grabber
stays. Tap it again to bring the bar back.

Leave it collapsed, close the app from recents, and reopen: it must still be collapsed.

> **Wrong looks like** the bar hidden with no grabber left to bring it back, the grabber missing
> its spoken label, or the collapsed state forgotten on restart.

### b7 · Back means the canvas, whether or not the bar is showing

From a section root — the movements list, the accounts list — press back. You return to the
canvas, and this must be true both with the tab bar showing and with it collapsed.

Then the case that used to be broken: from **Reports**, tap a ring slice to drill into
Transactions, and press back. You must end up in the app, not out of it.

> **Wrong looks like** back leaving the app from a section root, back doing two different things
> depending on whether the bar is collapsed, or back from a drill-down closing the app.

### b8 · Resetting the layout asks first, and rescues the canvas

Settings → Home → **Reset the bubble layout**. It must ask before doing anything (rule 7), and
confirming puts all four bubbles back where a fresh install has them, at their original size.

> **Wrong looks like** it resetting on the first tap with no question — an arrangement made by
> hand can only be rebuilt by hand — or the reset leaving one bubble where it was.

### b9 · With animations off, nothing drifts and nothing grows

A device setting, so it is the owner's (C5). Turn the system animations off — Settings →
Accessibility → **Remove animations**, or Developer options with all three animation scales at
off — then open Cents.

The bubbles must sit still, and tapping one must open its section **immediately** rather than
growing into it.

> **Wrong looks like** the drift still running, or the expansion still animating. The rule is
> that an invisible animation is still one the device asked not to run.
>
> **Known and not a bug:** the setting is read when the canvas is drawn, not watched. Changing
> it while Cents is already open leaves the drift running until the next launch (C4).

### b10 · TalkBack can reach every section

Also a device setting, so also the owner's. Turn TalkBack on and swipe through the canvas. Each
bubble is announced by its section name, in tab order — Transactions, Accounts, Investments,
Reports, Settings — and double-tapping opens it. The section name must be read **once**, not twice.

> **Wrong looks like** a bubble announced as an unlabelled button, an order that follows where
> the bubbles happen to sit rather than the tab order, or the name read twice because the icon
> is described separately.
>
> **Known and not a bug:** there is no gesture-free way to *move* a bubble. TalkBack can open
> them; dragging is a drag. Reset (b8) is the escape hatch (C4).

---

## Walk 6 — the investments section (`feat/investments-portfolio`)

Added 2026-09-09 with #44, and **none of it has been on a phone**. It is the newest section and
the one where a wrong number is hardest to notice, because unlike a balance there is nothing to
check it against except the broker's own app — so bring that, and check the two of them against
each other.

Walk it with **one account you can call a broker**, and be ready to type prices by hand: nothing
here comes from the internet yet (D13).

### i1 · The section exists, and says what to do when it is empty

Open the **Investments** bubble. With nothing held it says so. With no account at all it says
something different — that an investment needs an account to live in — and offers no **+**,
because there would be nowhere to put what you added.

> **Wrong looks like** an empty list with no words, a **+** that opens a form you cannot save,
> or the same message in both situations. They are different problems and the app should not
> pretend otherwise.

### i2 · Adding a holding asks four things and refuses a fifth

Tap **+**. Type a symbol **the way your platform writes it** — `NVD.DE` for Nvidia in Frankfurt,
which is the euro-priced listing a European broker sells you, rather than `NVDA`, which is New
York in dollars (D18). Watch it upper-case itself as you type; give it a name; pick a kind; check
that the account row shows the account you expected. Save.

Now add a second holding with the **same symbol in the same account**. The footer under the
symbol must say that account already holds it, and **Save must be off**.

> **Wrong looks like** the second one saving. Two rows for one instrument split a single
> position in half, and every average, total and profit on the screen would then be right about
> each half and wrong about the holding.
>
> The footer under the symbol is doing real work here: it is what keeps the app and your broker
> quoting the same listing, which is the whole of why nothing in this app converts a currency.

### i3 · A purchase is typed the way the keypad taught you

Open the holding, tap **Record a trade**, leave it on **Buy**. Type a quantity of `2,5`, a price
of `123,45` and a fee of `1,20`. The footer must say what the purchase cost **including the
fee**: 309,83 €.

Check it against your broker's confirmation — the same platform, the same listing, so the two
numbers should agree to the cent. Then try a quantity of `0,000000019` — the ninth decimal must
not go in.

> **Wrong looks like** the footer showing 308,63 € (the fee dropped) or 12.345,00 € (the price
> read as cents, the mistake `05` exists to catch). The quantity allows eight decimals because
> crypto needs them; money still allows two.

### i4 · Nothing is worth anything until you say what it is worth

Back on the holding, the value at the top and the **Price** row both say *no price yet*, and the
portfolio lists it with the same words rather than with 0,00 €. The totals card says how many
holdings it left out.

Tap **Set a price**. Two segments appear: **Per unit** and **Total value** — type whichever your
broker is showing you, set the date to when it was true (not necessarily today), and save.
Everything above fills in: what it is worth, the profit if sold now, and the percentage.

Then reopen it and switch the segment without typing anything. The number must convert rather
than reset: four units at 150,00 shows 600,00 under **Total value**, and switching back shows
150,00 again.

> **Wrong looks like** the two segments disagreeing about the same money, the field emptying when
> you switch, or a total that does not divide back into the price it saved.

> **Wrong looks like** a 0,00 € anywhere before a price is set. A number gets believed; "no
> price yet" does not. Also wrong: the price row not saying **as of** the date you chose.

### i5 · The average cost survives a partial sale

Buy `10` at `10` €, then buy `10` more at `20` €. The average must read 15,00 € and the cost
300,00 €.

Now **sell 10** at `30` €. Check all four numbers: quantity 10, **average still 15,00 €**, cost
150,00 €, and **profit already taken** 150,00 €.

> **Wrong looks like** the average moving after a sale — a sale removes cost at what you paid on
> average, not at what it sold for — or the remaining cost reading 100,00 € (the first ten) or
> 200,00 € (the last ten). This is the arithmetic everything else on the screen rests on.

### i6 · Selling more than you hold is refused, and says how much there is

With those 10 units held, record a sale of `12`. The footer must say you only hold 10, and
**Save must be off**.

> **Wrong looks like** it saving and quietly selling only what was there. You meant one of two
> things — a wrong quantity or a missing purchase — and recording neither is the honest answer.

### i7 · The kinds are separate, and the totals are one

Add an ETF and a crypto asset, each with a price. The portfolio lists them under **their own
headings**, in the order Stocks, ETFs, Index funds, Crypto — and a heading with nothing under it
does not appear at all.

The card at the top adds up everything that has a price. Do the arithmetic by hand once.

> **Wrong looks like** an empty heading, a holding under the wrong one, or a total that does not
> match the rows. If some holdings have no price the total says how many it left out — it must
> never quietly include them as zero.

### i8 · A sold-out holding leaves the list

Sell everything you hold of one instrument. It disappears from the portfolio.

> That is deliberate — it has no quantity, so it has nothing to be worth — but it also means
> what it *made* is no longer reachable, which is written down as a known gap (C4). If it reads
> as losing history rather than as tidying up, say so in `FEEDBACK.md`: that is a judgement only
> you can make.

### i9 · Deleting a holding says what goes with it

Open a holding with trades and tap **Delete this holding**. The confirmation must name **how
many trades** go with it and say it cannot be undone. Cancel. Then try one with no trades: the
message must be the shorter one.

> **Wrong looks like** deleting on the first tap, a message that does not mention the trades, or
> the plural reading "Its 1 recorded trades".

### i10 · An account holding investments cannot be deleted

Go to **Accounts**, swipe the broker account, and try to delete it. You must be told it holds
investments and how many, with one button to acknowledge it.

> **Wrong looks like** being offered *"it holds no movements, so it can be removed for good"* and
> then an error saying something was recorded while you were deciding — the exact dead end this
> check exists to remove. Also wrong: the account disappearing, which would take your recorded
> trades with it.

### i11 · The fifth tab behaves like the other four

Inside Investments, the tab bar shows five tabs and Investments is the lit one. Open a holding,
then a trade, and check the bar stays on **Investments** the whole way down. Press back from the
section root: you land on the canvas (b7).

> **Wrong looks like** the bar lighting Transactions over a holding — the case #40 fixed for
> accounts — or five tabs that no longer fit the width of the phone.

### i12 · Correcting what you hold, when the app and the broker disagree

The number that matters most on this screen is the one you can check against your broker, so this
is the step where you make them agree.

Tap the **Quantity** row in **Position**. The trade form opens on a third segment, **Correct**,
already filled in with what the app believes you hold — and the two money fields are gone,
because a correction moves units, not money. Type what you really hold and save.

Check the four numbers underneath: the quantity is what you typed; **what it cost is unchanged**,
because you did not spend anything by discovering the number was wrong; the average per unit has
moved, since the same money now covers a different number of units; and **profit already taken
has not moved at all**.

> **Wrong looks like** the cost changing, a profit or loss appearing out of a correction, or the
> form asking you for a price. Also wrong: the field opening empty rather than on the number you
> are about to disagree with.

### i13 · A correction is a record, not an overwrite

Look at the **Trades** list. The correction is in it, reading *Corrected to …* with its date and
no amount of money beside it. Nothing you recorded before has been deleted.

Now the case this design exists for: if you later find the *reason* — a purchase you never
recorded — add it as a normal **Buy** dated before the correction. The quantity must stay what
you corrected it to rather than growing by the units you just added, and the cost must now
include what you actually paid.

> **Wrong looks like** the earlier purchase pushing the quantity past what you said you hold —
> that would mean the correction recorded a difference rather than a fact — or the correction
> vanishing from the list once its cause was explained.

## Walk 7 — transfers, and the cash a trade spends (`feat/transfers`, `feat/buying-spends-the-cash`)

Added 2026-09-16, and **none of it has been on a phone**. This is the walk where the app is most
easily right in a way you cannot see: a transfer that moves the balances correctly but leaks into
a chart looks perfectly normal on the screen where it was recorded, and shows up as a month where
you apparently earned 500 € you did not.

Walk it with **two accounts you can tell apart** — call them Checking and Savings — and with
something already recorded this month, so the totals have a before and an after.

### t1 · The entry form has a third answer

Tap **+** on Transactions. The control at the top now reads **Expense · Income · Transfer**. Type
an amount first — say `500` — and *then* tap **Transfer**.

The screen changes: two accounts instead of one, no category, a **Fee** row. **The 500 you typed
is still there.**

> **Wrong looks like** the amount clearing, or the transfer form arriving on top of the movement
> form — press back once and you should be on the list, not looking at the form you just left.

### t2 · It refuses to guess where the money goes

The **From** section opens on your first account. **To** has nothing ticked and **Save is off**.
Pick Savings; Save comes on.

Check that **From** and **To** never offer the same account at once, and that **Swap the accounts**
turns the transfer around without clearing what you typed.

> **Wrong looks like** a destination chosen for you. Moving money into an account you did not
> name is the one mistake this form must not make on your behalf.

### t3 · Both balances move, by the right amounts

Save it. Go to **Accounts** and check both numbers by hand: Checking is 500 € lighter, Savings is
500 € heavier, and the two changes are equal and opposite.

> **Wrong looks like** one side moving, both sides moving the same way, or a balance that changes
> by an amount you did not type.

### t4 · And no chart moved at all

Now the point of the whole feature. Open **Transactions** and read **This month**: income and
expenses must be **exactly what they were before you recorded the transfer**. Open **Reports** and
check the same, on the period covering today:

- the ring — on **Expense** and on **Income** — has no new slice, and no *Uncategorized* slice
  that was not there before;
- **Income and expenses** has the same two bars for this month;
- **This period against the last** has not moved;
- the **balance line** *has* moved, because that is a balance rather than a flow.

> **Wrong looks like** 500 € of income, 500 € of expense, or an *Uncategorized* slice appearing.
> Any of those means the exclusion is missing from one query, and every statistic in the app is
> inflated by every transfer you ever record.

### t5 · A leg says where the money went, and opens the transfer

Back on the list, find the two rows. In Checking's account detail the row reads **Transfer to
Savings**; in Savings it reads **Transfer from Checking**. Neither says *Uncategorized*, and the
arriving one is **not** written in the green income is written in.

Tap either one. You get the **transfer**, with both accounts and the amount — not a movement form
showing one half of it.

> **Wrong looks like** a row called *Uncategorized*, a green +500 that reads as a payday, or a
> movement form that lets you change one side of a pair on its own.

### t6 · The fee comes out of the account it left

Record a second transfer of 100 € with a **Fee** of 3 €. Tap the Fee row first and watch the
keypad start typing into *it* rather than into the amount — the big number greys out, the fee row
turns blue.

The footer under the section should say that 103,00 € leaves the account it comes from. Save, and
check the balances: the source is 103 € lighter, the destination 100 € heavier.

Then open **Reports** again: **the 3 € is not spending**. It is inside the transfer (PRD §4.2), so
it appears in no ring and no expense total.

> **Wrong looks like** the destination receiving 103 €, the fee being typed into the amount, or
> 3 € turning up in the ring's *Uncategorized* slice.

### t7 · Editing a transfer edits both halves

Open the first transfer and swap its two accounts, then save. Both balances must swap with it —
Checking back up, Savings back down — with no third row appearing anywhere.

> **Wrong looks like** a new pair of rows beside the old one, or one side pointing at the new
> account while the other still points at the old one.

### t8 · Deleting one takes both, and says so first

Open a transfer and tap **Delete transfer**. The alert must say that *both* sides go and that this
one cannot be undone — there is no Undo banner for a transfer (C4), unlike a movement.

Confirm, and check both balances return to what they were.

### t9 · Deleting an account tells the truth about transfers

Go to **Accounts**, swipe **Savings**, and choose to delete it *with its movements*. Read the
second confirmation: the number must count the far legs living in Checking too, and the wording
must say that the balance of the account at the other end changes.

Cancel. Then, if you do go through with it on a throwaway account, check Checking afterwards: no
row saying *Transfer to* something that no longer exists.

> **Wrong looks like** a count naming only what lived in Savings, or a half transfer left behind
> in Checking — money that now appears to have arrived from nowhere.

### t10 · With one account, the form says why it cannot

On a fresh install with a single account, choose **Transfer**. It should say a transfer needs
somewhere to go and offer to create a second account, rather than showing two pickers with the
same one account in both.

### t11 · Buying spends the account, and the account says so

This is the step that ties the two halves of the app together, and it is the one to walk slowly.

Transfer **1.000 €** into your broker account (t1–t3), then go to **Investments** and record a
purchase of **400 €** of something — two units at 200 €, say.

Go back to **Accounts**. The broker account reads **600 €**: that is the cash left in it, not the
money you put there. Open it, and the purchase is in the list, reading **Bought NVD.DE** in the
ordinary colour rather than income's green. Tap it: you get the **trade**, not a movement form.

Now **Reports**, on a period covering today: nothing moved. No new expense, no new slice, no
*Uncategorized*.

> **Wrong looks like** the account still reading 1.000 €, a 400 € expense in the ring, a row
> called *Uncategorized*, or a movement form that lets you change the 400 € without the trade
> knowing.

### t12 · Selling gives it back, gain and all

Sell the same units for **500 €**. The account goes to **1.100 €** — the profit arrives as real
money, because it did.

Then delete the sale. The account returns to 600 €, and the row goes with it. Delete the holding
itself, and every purchase it made disappears from the account too, with the balance back to
1.000 €.

> **Wrong looks like** a sale that leaves the balance where it was, a deleted trade whose money
> stays gone, or a deleted holding that leaves its purchases sitting in the account with nothing
> to explain them.

### t13 · A correction still costs nothing

Correct a quantity (i12). The account balance must not move by a cent: discovering that a number
was wrong is not a purchase.

> **Wrong looks like** any change to the balance at all.
