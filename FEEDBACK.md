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

> Write here. Delete this line when you do.
>
> A template, if a blank page is annoying — none of the fields are required:
>
> ```
> ### Short name for the problem
> **Where:** which screen, and what you had done just before
> **What happened:** what you saw
> **What you expected:** what should have happened
> **How much it matters:** blocks me / annoying / cosmetic
> ```

### 1.

### 2.

### 3.

---

## Reference — the numbers from the device checklist

Only here so you can write "05 was wrong" instead of describing the whole step. There is no
need to fill this in; it is a lookup table.

**Walk 1 — the exit criteria**

| # | Check |
|---|---|
| 01 | First screen is empty — no seeded account, no seeded categories |
| 02 | Tapping + with no account leads into creating one |
| 03 | The account form never asks which currency; it shows EUR, not tappable |
| 04 | Categories can be created, coloured and dragged into order |
| 05 | The keypad counts in cents — `2 5 0 0` reads 25,00 € |
| 06 | Balances add up by hand: opening + income − expenses |
| 07 | Everything survives swiping the app away from recents |

**Walk 2 — features built without a device**

| # | Check |
|---|---|
| d1 | Swiping a row left reveals Delete; parks open; fires on a long swipe |
| d2 | The grip still reorders vertically while the body swipes sideways |
| d3 | Deleting an account with movements asks what becomes of them |
| d4 | Search finds by category name and by note |
| d5 | Filters → This month → Between two dates shows the From/To rows |
| d6 | A filtered list shows Matches with a count and totals |
| d7 | "Show everything again" clears the search text too |
| d8 | A deleted movement can be undone from the list |

**Walk 3 — judgement calls**

| # | Check |
|---|---|
| e1 | Both light and dark themes hold up |
| e2 | It reads as iOS rather than Material |
| e3 | The filter form is the right shape at six sections |
| e4 | The amount filter typed in cents makes sense |

---

## Fixed

Nothing yet. Entries move here with the date and the branch that fixed them, so *Open* only
ever shows what is still outstanding.

| Date | What was wrong | What was done |
|---|---|---|
