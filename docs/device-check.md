# The device check

**What this is.** A checklist for the app on a real phone, by feature, written so that someone can
spot a problem without knowing how the code works. Every step says what to do, and then **what
wrong looks like**. That second part is the point.

**Why it exists.** The agent can build the app, install it, read logcat and take a screenshot. It
cannot use it. Everything below is something no test in this repository can catch.

**How to use it.**

- After a branch that changes what the owner sees, walk the steps for that feature. Before a
  release, walk all of it.
- The code is enough to report a problem: *"M3: the third decimal went in"* is a complete entry in
  [`FEEDBACK.md`](../FEEDBACK.md).
- Use a **test account** (Bank, EUR, called *Test*) and test categories for anything that writes,
  and delete them at the end, so the real numbers never move.
- Steps marked **Pixel** need the owner's phone: a real bank's notifications, or Google Drive.

```bash
adb devices                                         # the phone is there and authorised
./gradlew installDebug                              # installs over the app, keeping its data
adb uninstall io.github.anderescaray.cents          # only when a fresh install is the point
```

> **Before judging speed:** `installDebug` is a debug build, and debug builds are much slower than
> release. Jank here is not evidence of anything (`CLAUDE.md` §5).

**What a step checks that is not built yet** is not here. Known gaps, which are not bugs, are in
[`known-limitations.md`](known-limitations.md).

---

## H · Home and recording a movement

### H1 · A fresh install starts empty, and leads somewhere

Only on a fresh install. Home opens with no accounts and asks for the first one; no category
exists anywhere (Settings › Categories is empty). The account editor's currency reads `EUR` and
cannot be changed.

> **Wrong looks like** a *Cash* account or a category you did not type (D1), a currency picker
> (D2), or **+** leading to a form with no account and no way forward.

### H2 · Recording takes a few taps

Tap **+** in the tab capsule (or *Expense* on Home), type an amount, choose a category, **Add
expense**. Back on Home the month's figures and the ring move to their new values.

> **Wrong looks like** more taps than that, or Home not updating.

### H3 · The keypad is typed in euros, and the comma asks for cents

On a new movement:

- `2` `5` reads **25 €** and saves as 25,00 €; `2` `,` `5` reads 2,5 € and saves as 2,50 €.
- `1` `2` `,` `5` `0` `9`: the third decimal is refused, and it stays 12,50 €.
- `2` `,` `5` `,` `0`: the second comma does nothing, giving 2,50 €.
- `,` `5` `0` on an empty amount reads 0,50 €.
- Backspace over `1` `2` `,` `5` removes one character at a time; then `3` gives 123 €.
- `12345,67` shrinks to fit rather than losing digits.

> **Wrong looks like** 2.500,00 € from `2` `5` `0` `0`, a third decimal rounded rather than
> refused, or backspace removing more than one character. **This is the most important step in
> this file:** every amount is a whole number of cents (rule 1), and this is where a hundredfold
> error would enter.

### H4 · The keypad steps aside

A new movement opens with the keypad. Tap the **Category** row: the keypad goes and the sheet opens,
in one tap. Tap the **date**: the calendar opens and the keypad is gone. Tap the amount: the
keypad is back, with the figure typed. Tap the note: only the keyboard. Tap empty space: both go.
A movement that already exists opens without the keypad.

> **Wrong looks like** a tap that only closes the keypad and has to be repeated, or the keypad and
> the keyboard on screen together.

### H5 · Category and account from a sheet

The **Category** row opens a sheet with only the movement's kind of category, the chosen one
ticked, and **No category** and **New category** at its foot. With more than seven, it has a
search (*cafe* finds *Café*). **New category** (and **New account** in the account sheet) comes
back with the new one chosen. A new movement starts on the account last used, never on nothing.

> **Wrong looks like** income categories under an expense, a sheet needing a second tap to close,
> or a new movement with no account.

### H6 · To categorize

Save an expense and an income without a category. Home's **To categorize** card lists them, each
with chips of its own kind and no archived category. Tapping a chip files it and the card shrinks.
Tapping the movement itself opens the form.

> **Wrong looks like** a transfer or a share purchase listed, or a movement filed under another
> category than the chip tapped.

### H7 · What the month comes to

Home leads with *Left this month* in green or *Overspent this month* in red: income less expenses,
with no transfer, trade or balance correction in it.

### H8 · The tabs keep their place

Home › Settings › another tab › **Home**: Settings is still open. Tap **Home** again: back to
Home's top. After swiping Cents away and reopening, everything is still there.

---

## M · Movements, search and filters

### M1 · Deleting a movement can be taken back

Open a movement and delete it: it asks first, and an **Undo** banner shows for about six seconds,
on Movements or on the account's own screen. Undo brings it back unchanged, with the balance.

### M2 · Search and filters

- Search finds a movement by part of its category's name and by part of its note; **✕** clears it.
- The round **filter** button sits beside **+**, apart from the title even when it collapses. With a
  filter on, it turns blue with a count.
- **Filters › This month › Between two dates** shows **From** and **To** underneath.
- A filtered list shows **Matches**, with a count and totals, and **Show everything** clears every
  filter and the search in one tap. When nothing matches, it says so.
- **Income** and **Expenses** never list a transfer leg or the cash of a trade.

> **Wrong looks like** custom dates that cannot be reached, an empty list that reads like having no
> movements, or stale search text after *Show everything*.

### M3 · The amount filter agrees with the keypad

Filters › Amount: `2` reads *2,00 € or more*; then the comma stays on screen, and `5` makes it
*2,50 € or more*. A third decimal is refused.

> **Wrong looks like** the comma vanishing under your finger, or 25 meaning 0,25 €.

### M4 · Past midnight

Leave Movements open across midnight. Within a minute *Today* reads *Yesterday*; on the last night
of a month, *This month* starts again from zero. A custom range on Reports stays where it was set.

---

## C · Categories

### C1 · Yours to make, order and archive

Settings › Categories: create an expense and an income category with a colour, drag one by its
grip to reorder it, and come back: the order stayed. Delete one that classifies a movement: it is
archived instead, and the app says so; its movements keep it.

> **Wrong looks like** the drag not picking up, or a category with movements deleted outright.

---

## A · Accounts and Wallet

### A1 · Three types

Wallet › **Add account**: *Type* offers **Cash**, **Bank** and **Broker** (*Efectivo*, *Banco*,
*Bróker*), and nothing else. The opening balance uses the same keypad as H3, and its sign toggle
keeps a half-typed amount.

### A2 · Balances add up, checked by hand

Opening balance, plus income, minus expenses, plus transfers in, minus transfers out: the account's
balance, to the cent.

> **Wrong looks like** being off by any amount. Balances are computed, never stored, so a wrong
> number is a wrong query.

### A3 · Cash and bank apart from invested

Wallet shows **Cash and bank** and **Invested** side by side, and Home the same two at the top.
Cash and bank is the cash and bank accounts added up; Invested is the brokers' cash plus their
holdings at the typed prices. On Wallet, Invested opens the portfolio; on Home it does nothing.

> **Wrong looks like** a broker's balance inside Cash and bank, or the two screens disagreeing.

### A4 · You choose what shows

Settings › **Wallet and Home**: switch **Net worth** on and **Invested** off. Both screens follow,
and Wallet keeps a row to the portfolio. All three off: no figure. Leave Settings and come back:
the switches stayed.

### A5 · Deleting an account asks a real question

Delete an account that holds movements. A sheet names how many, and offers to move them to another
account or to delete them too; the second asks again. Moving them carries the opening balance and
the corrections (D33), so the other account's balance becomes both together. An account a
subscription records into, or that holds investments, says so instead of failing.

> **Wrong looks like** it simply deleting, a half transfer left in another account, or a balance
> that did not add the two.

---

## T · Transfers

### T1 · A third answer on the form

Type `500`, then tap **Transfer**: the amount is still there, with **From**, **To** and a **Fee**.
**To** starts with nothing chosen and Save off; the two never offer the same account, and **Swap
them** keeps what was typed. With only one account, it offers to create a second.

### T2 · Both balances move, and no chart does

Save it. One account is 500 € lighter and the other 500 € heavier. Reports' ring, *Income and
expenses* and Home's month are exactly as before; only the balance line moves. In each account the
row reads *Transfer to …* or *Transfer from …*, and opens the transfer, not half of it.

> **Wrong looks like** 500 € of income or expense anywhere, or an *Uncategorized* slice: every
> statistic in the app would be inflated by every transfer.

### T3 · The fee leaves with the money

A 100 € transfer with a 3 € **Fee**: the source is 103 € lighter, the destination 100 € heavier,
and the 3 € is in no ring or expense total.

### T4 · Editing and deleting both halves

Swap a transfer's accounts and save: both balances swap, with no third row. Delete it: the alert
says both sides go, and **Undo** brings both back.

---

## B · Setting an account's balance

### B1 · Keep earlier balances

An account › **Set current balance**. Nothing flashes in before the account's own amount, and the
screen shows *Balance in Cents*, the difference, and two ways, **neither chosen**. Choose **Keep
them as they were** and save: the balance is the one typed, with a **Balance correction** row today.

> **Wrong looks like** Save allowed before a way is chosen, or *EUR* shown in passing.

### B2 · Change the opening balance

The same with **Change the opening balance**: it says from what to what, and that the balance chart
moves. Afterwards the account editor shows the new opening balance.

### B3 · Nothing else moves

After B1 and B2, Reports, Budgets and Home's month are unchanged. Cash and bank changes.

### B4 · Removing a correction, with Undo

Tap a **Balance correction** row: it asks first, naming the balance it goes back to. *Correction
removed* shows with **Undo**. If a movement is deleted within six seconds, the movement's Undo shows
first, and the correction's comes back after it.

---

## R · Reports

Walk it with two accounts, expenses in two categories on weekdays and a weekend, and an income.

### R1 · Arranged your way

**Customize** at the top: move *Budgets* down, hide a card, go back. Reports follows, and still does
after closing the app, with no hidden card flashing in on opening.

### R2 · The ring

Distinct slices with rows beneath in the same colours; the rows add up to **Total spent** and the
percentages to 100. **Expenses · Income** flips it to *Where the money came from* and *Total
earned*, and stays flipped after scrolling away. Tapping a slice or a row opens Movements narrowed
to it, adding up to the row.

> **Wrong looks like** the ring and the row disagreeing about which category was tapped, or an
> income slice opening expenses.

### R3 · The period and the account re-ask everything

**Week · Month · Quarter · Year · Custom**, and an **Account** picker (only with two accounts):
every card follows together. Custom opens on the period being looked at, and an inverted range is
straightened. A drill-down with one account chosen shows only that account.

### R4 · Income and expenses

Under the chart the latest column is described. Tap an older column: it is the one drawn in full,
and **Show movements** opens that month. **Saved** shows green and red columns; **Quarters**,
**Years** and **24** change the columns without labels overlapping.

> **Wrong looks like** the chart scrolling instead of selecting. No test can check this tap.

### R5 · Balance over time

The line starts from what was carried into the period, not zero, framed tightly. **Started with**
plus **Change** is **Ended with**, and today's *Ended with* on all accounts matches the balances.

### R6 · Weekday, comparison and trend

- **Spending by weekday:** the seven rows add up to Total spent, in the phone's day order, and the
  footer compares weekend and weekday spending per day. A day row opens that weekday's movements.
- **This period against the last:** spending more is red, less is green. A running period is
  compared with the same stretch of the last one, and the footer says so. **Same period last year**
  changes the numbers, not only the words. Its foot row opens the previous period's movements.
- **One category over time:** drawn in the category's colour; tapping the row picks another, and the
  choice survives switching tabs.

### R7 · Green in, red out, in both themes

Switch the phone between light and dark: every chart and label stays readable, in the app's own
colours.

> **Wrong looks like** a purple or teal column, Vico's defaults leaking through.

---

## U · Budgets

### U1 · A budget, and what it says

The **Budgets** card on Reports › **Add a budget**: only expense categories without one are
offered. The row shows what is **left**, what was spent this month, and what is **to come** from a
subscription in that category still due. Blue, then amber from 80 %, red with how much **over**
past the limit.

> **Wrong looks like** a transfer or a share purchase counted as spending, or Save on a zero limit.

### U2 · Editing, deleting, restoring

The limit can change and the category cannot. **Delete budget** asks first and leaves the category
and its movements alone. A budget comes back from a backup.

---

## S · Subscriptions and salaries

Settings › Subscriptions. Use the Test account.

### S1 · What was missed is filled in, once

Add *Rent*, 750, every month, starting the 1st of three months ago. Movements has **three** charges,
on each 1st, the moment it is saved. Swipe Cents away and reopen twice: still three.

> **Wrong looks like** one charge dated today, or six after reopening.

### S2 · The end of the month

*Storage*, 40, every month, starting 31 January: February's is the 28th (29th in a leap year), and
March's the 31st again.

### S3 · What it all costs

The figure at the top is per month: a yearly 12 € adds 1 €, and a salary adds nothing. Below,
what is still committed this month, and the share of recurring income it takes, which says it
cannot be worked out rather than 0 % with no recurring income.

### S4 · Stopping keeps the past

Stop one: it moves to **Stopped**, the total drops, and **every charge it made stays**. Its button
becomes **Delete**, saying the movements stay.

> **Wrong looks like** a charge disappearing with the rule (rule 8).

### S5 · A bill that asks for its amount

*Electricity*, **Ask me the amount**, 60, every month, from three months ago: nothing is written, and *Waiting for
an amount* lists it. Answer the first: it is dated the day it fell due, not today, and the next
waits. **Skip** asks first and records nothing, not a zero.

### S6 · Reminders

Add a reminder 3 days before and one on the day; the phone asks to send notifications. Both stay
after saving. **After** is offered only on one that asks for its amount, and its notification opens the pending
amounts. One arrives on its day for a subscription a few days away, never for a stopped one.

### S7 · Postponing

**Postpone…** 3 days: the row reads *Moved to …* and the charge is written on the new day; the one
after stays on its own date. **Back to its own day** undoes it. A salary that asks for its amount, postponed from the
pending screen comes back on the new day, as income.

### S8 · No broker

A new subscription's account list has no broker, and says so.

---

## G · Groups

Settings › Groups. A test group, *Walk trip*, with a 1,00 € expense and an income in it.

### G1 · Recording into it

With a group, the movement form has a **Group** chip, never filled in for you. The group's row says
*Cost you …* or *Earned you …*: expenses less income. Its screen shows *Came in*, *Went out*, the
dates, a ring and the movements; a slice opens them in Movements. Reports and budgets are unchanged.

### G2 · Archived, it is no longer offered

The group › Edit › **Archive group**. It moves under **Archived**, still with its totals, and its
screen says it is archived. A new movement does not offer it (with no other group, no Group chip).

> **Wrong looks like** the group offered for a new movement, or its total changing.

### G3 · An old movement keeps it

Change the note of its 1,00 € expense: the group reads *Walk trip (archived)* and stays after
saving. Movements › Filters › Group lists it, marked *(archived)*.

### G4 · Reactivated, deleted, restored

Edit › **Reactivate group**: back in the list, offered again. **Delete group** asks first, and every
movement stays, in its category, with no group. A group comes back from a backup with its movements.

---

## I · Investments

Wallet › **Invested**. Prices are typed, never fetched (D25). Use a broker account, and check the
numbers against the broker's own app.

### I1 · Only a broker holds an investment

Adding a holding offers only broker accounts; with none, one row, **New broker account**, which opens
the editor set to *Broker*. With nothing held, the portfolio says so.

### I2 · A holding, and not two of it

Type the symbol as the platform writes it (`NVD.DE`); it upper-cases itself. A second holding with
the same symbol in the same account is refused, with Save off.

### I3 · A purchase, the fee included

**Record a trade** › Buy: quantity `2,5`, price `123,45`, fee `1,20`. The footer says **309,83 €**.
A quantity takes eight decimals, not nine.

### I4 · Nothing is worth anything until a price is typed

Before a price: *No price yet* everywhere, never 0,00 €, and totals say how many they leave out.
**Set a price**, **Per unit** or **Total value**, dated: everything fills in, and switching between
the two converts rather than empties.

### I5 · The arithmetic

Buy 10 at 10 €, then 10 at 20 €: average 15,00 €, cost 300,00 €. Sell 10 at 30 €: quantity 10,
**average still 15,00 €**, cost 150,00 €, profit taken 150,00 €. Selling 12 of 10 is refused, saying
how many are held.

### I6 · Buying spends the broker's cash

Transfer 1.000 € to the broker, buy 400 €: the broker reads 600 €, with *Bought …* in its list,
opening the trade. Nothing moves in Reports, and Invested barely moves. Sell for 500 €: 1.100 €.
Deleting the trade or the holding gives the money back.

### I7 · Correcting a quantity

Tap **Quantity**: **Correct** opens on what Cents believes is held, with no money fields. After
saving, the cost and profit taken are unchanged, the average moves, the broker's balance does not,
and the trades list shows *Corrected to …*.

### I8 · Kinds, closed positions, deleting

Holdings sit under their kind's heading, with no empty heading. A holding sold out moves to
**Closed positions**, with what it made. Deleting a holding names how many trades go with it.

### I9 · A broker takes an income only

A new movement: as an **Expense**, no broker is listed and the sheet says why. As an **Income**, the
broker is listed but not chosen for you.

---

## K · Capture from notifications (Pixel)

Settings › Capture, with the bank's app sending real notifications.

### K1 · Switching it on

**Read payment notifications** on asks to post notifications. **Notification access** reads *Not
allowed* in red until Android's list is opened from it and Cents is allowed; it reads *Allowed* on
return. If capture ever goes quiet, this row is the first thing to look at.

### K2 · Choosing the bank's app

**Add an app** lists the phone's apps by the name under their icon, without Cents and without the
apps already chosen; any app with a notification showing is listed too. Choose the bank and its
account (never a broker). The app is listed *Into* that account; tapping it changes the account or
**Stop reading this app**.

> **Wrong looks like** the bank missing both ways. Send the name it has in *Settings › Apps*.

### K3 · Try a notification

**Try a notification**: paste the title and text of a real bank notification. It says *Would record
an expense* with the right amount and account, also with a masked card (`****7146`), a balance after
the payment (*Saldo disponible*) and `Amazon · 23,40 €`.

> **Wrong looks like** a real payment it would ignore. That text is what to send.

### K4 · A real payment, announced, once

Pay something small. **Expense added** arrives with the amount and account; the movement waits under
*To categorize*, with the notification's text as note. One payment is one movement, even when the
bank and Wallet both announce it. With **Notify after capture** off, it is recorded silently.

> **Wrong looks like** nothing arriving, the card's digits as the amount, or two movements. If it
> works and then stops, set Cents' battery use to *Unrestricted*.

### K5 · The words

Each app has its own **Expense words** and **Income words**; editing one app's leaves the other's
alone. A word already there is refused; **Restore the default words** puts back a removed one.
**Ignore words** holds *saldo* and *balance*.

### K6 · A subscription's charge counts once

Point the bank at the Test account. Add *Walk fixed*: **Record it for me**, 1,00 €, monthly, from today; today's
charge is written. Send yourself a 1,00 € Bizum: **Matched Walk fixed**, *Nothing added*, with
**Undo**, and still one 1,00 € movement.

> **Wrong looks like** a second 1,00 € under *To categorize*, or *Expense added*.

### K7 · Undo, with the app closed

Swipe Cents away and tap **Undo**: the notification becomes **Expense added**, Cents does not open,
and the Bizum is under *To categorize* beside *Walk fixed*, which stays.

### K8 · A variable bill takes what was paid

*Walk bill*: **Ask me the amount**, 5,00 €, monthly, from today. Send 1,50 €: **Walk bill filled in**. It leaves
the pending amounts, and *Walk bill* is 1,50 € today, with nothing extra.

### K9 · Nothing is matched unseen

**Notify after capture** off. *Walk quiet*: Record it for me, 2,00 €. Send 2,00 €: an ordinary capture under *To
categorize*, beside *Walk quiet*.

### K10 · Putting it back

Notify back on, the bank back on its own account, and the three subscriptions and Test deleted.

---

## D · Your data: export, restore and Google Drive

### D1 · Export, checked in the file

Settings › **Export your data** asks which: a backup file or a spreadsheet. The system's picker
opens on `cents-<date>.json`, with no permission asked. Open the file: it starts with
`schemaVersion`, and has the accounts, the movements with amounts in cents (25,50 € is `2550`), the
holdings, subscriptions, budgets and groups. The `.csv` opens as a sheet, a comma in a note staying
in its cell. Backing out of the picker is not an error.

### D2 · Restore replaces, after saying what with

Only with a backup just made. **Restore from a backup**: the alert names what the file holds and
what it replaces, and suggests exporting first. Afterwards the app is exactly as exported. A photo,
the `.csv`, or a file whose `schemaVersion` is too new is refused and changes nothing.

> **Wrong looks like** a restore half done: worse than one refused.

### D3 · Google Drive (Pixel)

**Back up automatically** on: cancelling Google's screen leaves it off; granting asks only for the
app's own folder. **Back up now** says when; tapping it again with nothing changed adds no copy.
**Restore from Drive** lists the copies, newest first, with the same alert as D2. When Google
withdraws access, a dated problem and **Reconnect** show.

---

## L · Language

### L1 · English, everywhere, and money unchanged

Settings ends with **Language** (*Idioma del teléfono* on a Spanish phone), offering the phone's,
*English* and *Español*. English redraws every screen, alert and new notification; what was typed
stays as typed. Amounts and dates keep the phone's style (`1.234,56 €`, `12 oct`, a comma key), and
month names stay Spanish (D36). A new account is still EUR.

### L2 · The phone agrees, and nothing is lost

With an amount and a note typed, change the language in *Settings › Apps › Cents › Language*: back
in Cents the form is translated and keeps both. Changing it from Settings after opening Cents from a
reminder stays on Settings.

---

## X · Look, themes and accessibility

### X1 · The film's look

White cards with a soft shadow on a pale canvas, chips, one filled button per sheet, the floating
capsule with **+** in the middle (ADR 0003), in light and in dark. No Material widget, ripple or
colour from the wallpaper, no lock screen of Cents' own (D31).

> **Wrong looks like** grey-on-grey text, a card that keeps its light background in dark, or a
> purple accent from nowhere.

### X2 · Swipes

A row swiped past 60 % asks to delete it; short of that it springs back. Dragging a category by its
grip still reorders, and swiping the body does not steal it.

### X3 · TalkBack

A row reads as one stop (an account's name and balance together); a chosen row says *selected*.
Titles and section headers are headings. **+** says *Add a movement*.

### X4 · The largest font

System font and display size at their largest: the movement form, an alert, the Expense · Income ·
Transfer control and the search field keep every word whole, growing taller instead.
