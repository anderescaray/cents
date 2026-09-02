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

Two walks. The first was on a Pixel 8 on 2026-08-30, on the branch build `d7a51a3`
(main + PR #20 + PR #21). The second was on 2026-09-02 on a **HONOR HEY3-W00, Android 16**,
on `main` at `0aa0e0a` (through PR #26 and #28), after a genuine fresh install — so PR #27's
euro amounts were not in the build being walked. Everything not listed below passed. Written
down by the agent from what the owner reported out loud, in English to keep to rule 10 — the
content is his.

### 1. Amounts should be typed in euros, with a comma, not counted up in cents

**Where:** 05 (the movement keypad) and e4 (the amount filter). Both are correct as specified —
`2` `5` `0` `0` reads 25,00 €, and the filter says its range back in real money.
**What happened:** every round number costs two extra taps on `0`.
**What you expected:** typing starts in euros, and a comma is typed only when cents are wanted:
`2` `5` is 25 €, and `2` `,` `5` is 2,50 €. **After a comma, at most two digits** — a third
simply does not go in, rather than being accepted and rounded away.
**How much it matters:** not a bug — a change of convention, and the same one in both places.
**Status:** fixed on `feat/amounts-in-euros` (PR #27), waiting to be merged.

### 2. Switching tabs backwards still animates forwards

**Where:** the tab bar. Reports → Accounts, and Settings → Reports.
**What happened:** the new tab slides in from the right, as if you had moved *forward*, even
though you moved left along the tab bar.
**What you expected:** moving left along the tabs should slide the other way.
**Why it happens:** `switchToTab` in `CentsApp.kt` is a `navigate()`, and every `navigate()` is
a push, so the `NavHost` uses `enterTransition`/`exitTransition` — both `SlideDirection.Start`
— whatever the direction of travel. Nothing compares the two tabs' positions. The tab order is
Transactions, Accounts, Reports, Settings (`TopLevelTab`), so the sign is knowable.
**Worth deciding while fixing it:** iOS itself does not slide between tabs at all — a tab bar
cuts, and the horizontal push is reserved for pushing *within* a tab. Making the slide
direction-aware and dropping the slide between tabs entirely are both defensible; the second is
closer to ADR 0001 and would make this whole class of bug impossible.
**How much it matters:** it reads as wrong every time, and it is the one animation seen most.

### 3. The keypad and the colour picker run together in the account editor

**Where:** creating a new account.
**What happened:** the number keypad and the colour swatches sit directly against each other in
the same colour, so there is no visible line where the keypad ends and the colours begin. They
do not overlap — it is that nothing separates them.
**What you expected:** to be able to see where one stops and the other starts.
**How much it matters:** looks wrong, not broken.

### 4. The donut should be able to show income, not only expenses

**Where:** the Reports tab.
**What you want:** the same ring, for income as well as spending — where the money came *from*,
not only where it went.
**How much it matters:** wanted, not urgent. Not a defect: the ring was only ever specified for
spending.

### 5. Reports should be filterable by account

**Where:** the Reports tab.
**What you want:** to narrow every report to one account, the way the movements list can be.
**How much it matters:** wanted, not urgent.

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
| 2026-08-30 | **d1** — a half-swiped row stayed parked open with Delete showing | The middle state is gone: past 60% of the row's width the swipe fires the same delete confirmation, and short of it the row springs back. The Delete button is no longer tappable, since it never stands still — recorded in ADR 0001 as the first deliberate departure from iOS, with the custom accessibility action left as the non-gesture path. #24 |
| 2026-08-30 | **03** — the number keypad came up on the account *name* field | `CentsKeypad` was mounted unconditionally at the bottom of both forms. It is now shown only when the amount is the field being answered, and a text field taking focus puts it away. `fix/keypad-only-for-amounts` |
| 2026-08-30 | **d8** — the number keypad was up when a movement was opened only to be read | Same fix. A form now opens with the keypad up when it is *creating* a record and without it when it is opening one that exists; tapping the amount asks for it back. `fix/keypad-only-for-amounts` |
| 2026-08-30 | **r8** — clearing a filter meant opening Filters and scrolling to its foot | The Matches card on the list now ends in **Show everything**, so the most frequent action is one tap from where the filter is felt. It appears only when something is narrowing the list, so an unfiltered list is unchanged, and it clears the search box too — spelled out in a footer when there is text to lose. `feat/clear-filters-from-the-list` |
| 2026-08-30 | **r7/r8** — a second drill-down landed on the filter form instead of the list | `switchToTab` restores a tab's saved back stack, and that stack was holding the filters screen — which is exactly where you go to read the filter the previous drill-down applied. A drill-down now pops back to the list. #21 |
