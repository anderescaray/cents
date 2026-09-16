# FEEDBACK.md — what the owner found on the phone

**What this file is for.** The agent cannot use the app. It can build it, install it, read
logcat and take a screenshot, but it cannot tell whether a gesture feels wrong, whether a
number is the one you expected, or whether a screen is confusing. You can. This is where that
goes.

It is the mirror of [`DECISIONS.md`](DECISIONS.md):

| File | Direction | Holds |
|---|---|---|
| [`DECISIONS.md`](DECISIONS.md) | agent → owner | Questions only you can answer |
| **`FEEDBACK.md`** | **owner → agent** | **Things you saw go wrong, and things you want changed** |

**How to use it.** Write under *Open* below, in any form. Bullet points are fine, one line is
fine, Spanish is fine. You do not have to be precise about the cause — "the balance was wrong
after I deleted something" is more useful than a guess at which query is broken.

**What happens next.** The agent reads this file at the start of every session, before writing
any code. Each item is fixed on its own branch, and then moved down to *Fixed* with the date
and what was actually done — so this file stays short and the history stays somewhere.

---

## Open

**Nothing is waiting here right now.** Both items from the 2026-09-02 walk on the HONOR
HEY3-W00 were built on 2026-09-03 and are in *Fixed* below; everything else that walk covered
passed.

An empty *Open* is the normal state between walks, not a sign the channel went quiet — it
fills up again the next time the app is used on a phone. The walk itself is
[`docs/device-check.md`](docs/device-check.md).

**The second walk is written and waiting to be walked** (2026-09-09, extended since). It now
covers the whole Reports tab (`r1`–`r19`), the canvas of bubbles the app opens on (`b1`–`b10`),
the investments section (`i1`–`i13`) and transfers (`t1`–`t10`), and **none of that has been on
a phone** — six chart views, a year-on-year comparison, a front door made of drag and pinch
gestures, a portfolio, and money moving between accounts, all verified by JVM tests and by
nothing else. Whatever it turns up lands here, and it becomes the next branch.

---

## Where the numbers come from

Items like `05` and `d5` are the steps of [`docs/device-check.md`](docs/device-check.md) — the
walk through the app on a real phone. Quoting the number saves describing the whole step, so
*"05: the keypad showed 2.500,00"* is a complete entry.

---

## Fixed

Entries move here with the date and the branch that fixed them, so *Open* only ever shows what
is still outstanding.

| Date | What was wrong | What was done |
|---|---|---|
| 2026-09-03 | **Reports could not be narrowed to one account** the way the movements list can | A picker under the period control, and it governs the whole tab rather than one chart — the three views are answers about the same money, so letting them disagree about *whose* money makes the tab unreadable. What made it a seam change rather than an additive one: a section source was asked for a period and a currency, and both became one `ReportScope` that also builds the drill-down filter, so a slice tapped with one account chosen can no longer open everyone's movements. Archived accounts are offered, because the charts have always counted them; the picker is hidden entirely until there are two accounts to choose between. `feat/reports-for-one-account` |
| 2026-09-03 | **The ring only ever showed spending** — there was no way to ask where the money came *from* | A segmented control above the ring picks the direction, and the section's words follow it: *Where the money went* / *Where the money came from*, *Total spent* / *Total earned*. Both breakdowns are read at once rather than one query re-run on each flip, so the toggle answers instantly and the two sides can never be about different periods; the cost is a second `GROUP BY` bounded by the number of categories. A slice of the income ring drills down to income, because the kind already travelled with the filter. `feat/the-ring-shows-income-too` |
| 2026-09-02 | **The keypad and the colour picker ran together** in the account editor — same colour, no line between them | The keypad now stands on its own bar background with a hairline above it, the two tokens the tab bar already uses to sit above content. Its keys are grouped cards like the colour swatches, so sharing a ground with the form left nothing between them. The bottom inset moved into the keypad, since padding it from outside would leave a strip of the form's background below the surface. `fix/the-keypad-sits-on-its-own-surface` |
| 2026-09-02 | **Switching tabs backwards animated forwards** — Reports → Accounts and Settings → Reports slid in from the right | A tab switch is a `navigate()`, and every `navigate()` is a push, so the slide was one fixed direction. `slideDirectionFor` compares the two tabs' positions, which `TopLevelTab` has always ordered. One case is knowingly left: switching to a tab whose saved stack has a detail screen on top still slides forward, because a pushed screen carries no record of its tab. #31 |
| 2026-09-02 | **Amounts were counted up in cents** rather than typed in euros | Typing starts in euros and the comma asks for cents; after it, two digits at most and a third is refused rather than rounded away. One `AmountEntry` decides this for both the keypad and the amount filter, and what reaches `Money` is still an exact `Long` of cents. #27 |
| 2026-08-30 | **d1** — a half-swiped row stayed parked open with Delete showing | The middle state is gone: past 60% of the row's width the swipe fires the same delete confirmation, and short of it the row springs back. The Delete button is no longer tappable, since it never stands still — recorded in ADR 0001 as the first deliberate departure from iOS, with the custom accessibility action left as the non-gesture path. #24 |
| 2026-08-30 | **03** — the number keypad came up on the account *name* field | `CentsKeypad` was mounted unconditionally at the bottom of both forms. It is now shown only when the amount is the field being answered, and a text field taking focus puts it away. `fix/keypad-only-for-amounts` |
| 2026-08-30 | **d8** — the number keypad was up when a movement was opened only to be read | Same fix. A form now opens with the keypad up when it is *creating* a record and without it when it is opening one that exists; tapping the amount asks for it back. `fix/keypad-only-for-amounts` |
| 2026-08-30 | **r8** — clearing a filter meant opening Filters and scrolling to its foot | The Matches card on the list now ends in **Show everything**, so the most frequent action is one tap from where the filter is felt. It appears only when something is narrowing the list, so an unfiltered list is unchanged, and it clears the search box too — spelled out in a footer when there is text to lose. `feat/clear-filters-from-the-list` |
| 2026-08-30 | **r7/r8** — a second drill-down landed on the filter form instead of the list | `switchToTab` restores a tab's saved back stack, and that stack was holding the filters screen — which is exactly where you go to read the filter the previous drill-down applied. A drill-down now pops back to the list. #21 |
