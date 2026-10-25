# Backlog

What the owner has asked for and confirmed, with the details each request came with, so any
session can build it without the original conversation. **The build order is
[`PLAN.md`](../PLAN.md) §3**; this file holds the requests themselves.

- An item is **ticked** once it is merged *and* the owner has checked it on the phone. Ticking
  it also **deletes its section below**. The PR and git keep the details.
- A request is not permission to skip the usual rules: review, territory, migrations, both
  languages, and never losing data.
- `FEEDBACK.md` outranks everything here.

## Confirmed requests

- [x] 1. English/Spanish language selection in Settings. #127 and #129, checked in Walk 25
  (2026-10-18). Now in `docs/PRD.md` §4.8.
- [x] 2. Proper separation between the Movements title and Filters. #117, checked in Walk 22
  (2026-10-09).
- [x] 3. Category selection through a large popup, not a horizontal strip. #117, checked in
  Walk 22.
- [x] 4. Keypad visible only while the amount is active. #117, checked in Walk 22.
- [x] 5. Account popup with the last-used account preselected. #117, checked in Walk 22.
- [x] 6. Normal money (cash and bank) and invested money (brokers), kept apart, on Wallet and at
  the top of Home. #138, checked in Walk 29 (2026-10-18). Now in `docs/PRD.md` §4.2.
- [x] 7. Set an account's current balance, choosing the historical effect each time. #112, #120
  and #121, checked in Walks 23 and 24 (2026-10-18). Now in `docs/PRD.md` §4.2.
- [x] 8. Hide/archive groups from new-movement selectors without deleting them. #140, checked
  on the phone (G2–G4, 2026-10-25). Now in `docs/PRD.md` §4.9.
- [x] 9. Exclude broker accounts from new manual income/expense selection, but not transfers.
  #117 and #133 (D32), checked in Walks 22 and 27 (2026-10-18). Now in `docs/PRD.md` §4.2.
- [x] 10. Offer only broker accounts when choosing where a new investment is held. #117, checked
  in Walk 22.
- [x] 11. Only three account types: cash, bank and broker. #136, checked in Walk 28
  (2026-10-18). Now in `docs/PRD.md` §4.2.
- [x] 12. Capture recognises a subscription's charge instead of recording it twice (`DECISIONS.md`
  D35). #131, checked on 2026-10-19 (K6–K10). Now in `docs/PRD.md` §4.6.
- [ ] 13. The figure switches in Settings govern Home only; Wallet always shows every figure
  (`DECISIONS.md` D37).
- [ ] 14. Clearing every filter from the top of the Filters screen, not only from its foot.

## 13. Figure switches for Home only

**Request (2026-10-25):** turning off Cash and bank, Invested or Net worth in Settings should take
it off Home, and nothing else. Wallet is where the owner goes for the whole picture, so it always
shows all three.

- Wallet shows Cash and bank, Invested and Net worth whatever the switches say. Invested still
  opens the portfolio.
- Home follows the switches exactly as it does today, all three off included.
- The Settings section and its footer say *Home*, not *Wallet and Home*, in both languages.
- The stored switches keep their values; nothing migrates.

## 14. Clear all filters from the top

**Request (2026-10-25):** on Movements › Filters, *Show everything again* sits at the foot of the
form, so clearing filters means scrolling past every section.

- The way to clear every filter is reachable without scrolling, at the top of the Filters screen,
  and does the same as the button at the foot: every filter and the search go.
- It shows only while something is narrowing the list, like the *Show everything* row on the list
  itself.

## Acceptance for what is still to build

- **Every item:** migration and backup round-trip tests for anything stored, the gates in
  [`docs/testing.md`](testing.md), and a walk on the phone. A green JVM suite does not show that a
  gesture, a keyboard or the system language behaves on a device.

## Requested, not yet placed

Real requests the owner has not put in the build order yet.

- **Hide a rarely used tab** (2026-09-25). For example Wallet, which is opened rarely once the
  accounts exist. Nothing may become unreachable: a hidden tab needs another way in, such as a
  row in Settings. At least one tab stays visible, and hiding is undone the same way it was done.
- **What was saved or overspent, as a card on Reports** (2026-09-27). It would lead with one
  figure in words, *You saved 312,40 €*, possibly with a savings rate (*no income yet* rather
  than 0 %). Today Home leads with the month's figure, and Reports' income-and-expenses card has
  a *Saved* mode (#101). The Spanish *Balance* label reads as an account balance and should
  change.
- **Delete one Drive backup at a time** (2026-10-01), from *Restore from Drive*, with a
  confirmation. Today only retention removes copies. This adds a delete to each copy in the list;
  the daily backup and restore stay exactly as they are.
- **Resizable Reports cards** (D4). Reordering and hiding are built (#109).
- **A home-screen widget**, the last of PRD Phase 6. R8, the Baseline Profile and Macrobenchmark
  are built (#146).

## Engineering follow-ups

No owner request behind these; take one when it is next to related work.

- **`CaptureViewModel.moveTo` finds the app through the derived `uiState.value`**, where
  `requestStop` uses `editingPackage`. It cannot fail on the phone. Reading `editingPackage` would
  make the two consistent.
