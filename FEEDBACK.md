# FEEDBACK.md — what the owner found on the phone

**What this file is for.** The agent cannot use the app. It can build it, install it, read
logcat and take a screenshot, but it cannot tell whether a gesture feels wrong, whether a
number is the one you expected, or whether a screen is confusing. You can. This is where that
goes.

It is the mirror of [`DECISIONS.md`](DECISIONS.md):

| File | Direction | Holds |
|---|---|---|
| 2026-10-18 | **Ruralvía was not in Settings › Capture › Add an app** | Not a bug. Ruralvía was already chosen, with an account, so it was on Capture's own list and not under *Add an app*, which offers only apps not yet chosen; and Cents had no notification access, so nothing was captured. #134 changed how the list names apps, which this did not need. |
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

Nothing is open, and no check is waiting.

**Checked by 2026-10-25 and correct:** G2–G4, archiving a group (#140), on the build installed
2026-10-18 (`8dd2a78`). Every confirmed request so far has now been checked on the phone.

**Checked on 2026-10-19 and correct:** capture from notifications, the owner reported, which
covers K6–K10, capture recognising a subscription's charge (#131).

**Walked on 2026-10-18 and correct:** Walks 23 (r1–r3), 24 (b1), 25 (l1–l5), 27 (y1–y3), 28
(a1–a2) and 29 (f1–f3). On b1 the owner asked for nothing more to change. Walk 22 passed on
2026-10-09. Walk 21's notes are not coming: Walks 23 and 24 checked again what #120 changed there.

The two requests made with that report on 2026-10-25 are changes rather than faults, so they are
backlog items 13 and 14 in [`docs/next-improvements.md`](docs/next-improvements.md).

---

## Where the numbers come from

Items like `H3` and `K6` are the steps of [`docs/device-check.md`](docs/device-check.md) — the
checklist for the app on a real phone. Quoting the code saves describing the whole step, so
*"H3: the keypad showed 2.500,00"* is a complete entry. Codes in the *Fixed* table below are from
the walks that file replaced, and are in git.

---

## Fixed

Entries move here with the date and the branch that fixed them, so *Open* only ever shows what
is still outstanding.

| Date | What was wrong | What was done |
|---|---|---|
| 2026-10-01 | **The whole UI**: the bubbles, the iOS look, budgets in Settings; wanted a Home that records and files in a few taps, after the film | The rework, D30 and ADR 0003 accepted with amendments. `feat/ui-rework` |
| 2026-10-01 | **Wallet: the amount and *Delete* overlapped, and the account row was red** | Rows were made transparent for the cards, which let the swipe's red layer show through. Rows are opaque again. `feat/ui-rework` |
| 2026-10-01 | **Lines between the cards after Income → back**, and **hard corners on *This month*** | The cards' fade drew into a layer clipped to the card, cutting the soft shadow into straight edges, and replayed on every return; a size animation clipped it too. The fade multiplies alpha instead and plays once per visit; the size animation is gone. `feat/ui-rework` |
| 2026-10-01 | **Overspent / saved did not catch the eye**; **Reports could not be rearranged**; the ring was in two styles | The month card leads with it, large, on a red or green panel. Reports › Customize orders and hides cards. Reports draws the same ring as Home, with its controls inside the cards. `feat/ui-rework` |
| 2026-10-01 | **Capture's words should be per app** | Each app has its own expense and income lists, edited from its card in Settings › Capture. `feat/ui-rework` |
| 2026-09-17 | **A subscription could not remind you before it is charged** — wanted on fixed amounts as much as variable ones, with the number of days chosen per subscription and more than one reminder if wanted | Any number of reminders per rule, each a number of days the owner picks. A fixed one is announced and only beforehand — afterwards the movement is already in the list; a variable one can also be chased after the date, and tapping it lands on the screen that takes the figure. **Nothing is scheduled in advance**: one daily job asks what lands today, so editing a reminder, stopping a rule, answering a bill or a reboot need nothing cancelled. The editor says plainly when the system will show nothing. Room v6. `feat/subscription-reminders` |
| 2026-09-17 | **A subscription starting in May, entered in September, recorded nothing at all.** The screen said the next charge was in October — which was right — but the five that had already fallen due never appeared | Generation ran only on `ON_START`, and the app had never left the foreground, so a rule created in a session generated nothing until Cents was closed and reopened. Saving a rule now catches it up **before the screen closes**, which is what every document about this feature already claimed it did. Two tests pin it: five charges on the five first-of-the-months, and nothing at all for a rule that starts in December. `fix/subscription-backlog-and-frequency` |
| 2026-09-17 | **The *how often* section took up most of the form**, pushing the two fields that need thinking about — the dates — off the bottom | Six permanent rows became one row showing the current answer, opening an action sheet. A choice made once and then read at a glance does not need to be six rows tall. `fix/subscription-backlog-and-frequency` |
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
