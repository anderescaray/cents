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

Walked on a Pixel 8 on 2026-08-30, on the branch build `d7a51a3` (main + PR #20 + PR #21),
after a genuine fresh install. Everything not listed below passed. Written down by the agent
from what the owner reported out loud, in English to keep to rule 10 — the content is his.

### 1. Clearing the filters is buried

**Where:** r8 — a filter is applied and you want it gone.
**What happened:** the only way is Filters, then scroll to the bottom, then clear.
**What you expected:** something reachable from the list itself.
**How much it matters:** annoying, and it is the action you reach for most often.

### 2. A half-swiped row stays parked open

**Where:** d1 — swipe a row sideways and let go before the delete threshold.
**What happened:** the row stays where it was released, with Delete showing.
**What you expected:** it should spring back and cover Delete again. It should only stay open
when the swipe was far enough to actually mean deleting.
**How much it matters:** annoying. Deleting by swiping all the way across works.

### 3. Amounts should be typed in euros, with a comma, not counted up in cents

**Where:** 05 (the movement keypad) and e4 (the amount filter). Both are correct as specified —
`2` `5` `0` `0` reads 25,00 €, and the filter says its range back in real money.
**What happened:** every round number costs two extra taps on `0`.
**What you expected:** typing starts in euros, and a comma is typed only when cents are wanted:
`2` `5` is 25 €, and `2` `,` `5` is 2,50 €. **After a comma, at most two digits** — a third
simply does not go in, rather than being accepted and rounded away.
**How much it matters:** not a bug — a change of convention, and the same one in both places.

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
| 2026-08-30 | **03** — the number keypad came up on the account *name* field | `CentsKeypad` was mounted unconditionally at the bottom of both forms. It is now shown only when the amount is the field being answered, and a text field taking focus puts it away. `fix/keypad-only-for-amounts` |
| 2026-08-30 | **d8** — the number keypad was up when a movement was opened only to be read | Same fix. A form now opens with the keypad up when it is *creating* a record and without it when it is opening one that exists; tapping the amount asks for it back. `fix/keypad-only-for-amounts` |
| 2026-08-30 | **r7/r8** — a second drill-down landed on the filter form instead of the list | `switchToTab` restores a tab's saved back stack, and that stack was holding the filters screen — which is exactly where you go to read the filter the previous drill-down applied. A drill-down now pops back to the list. #21 |
