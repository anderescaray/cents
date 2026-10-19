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
- [ ] 8. Hide/archive groups from new-movement selectors without deleting them. **Merged in
  #140**, with Room v13. Phone check: `docs/device-check.md` G2–G4.
- [x] 9. Exclude broker accounts from new manual income/expense selection, but not transfers.
  #117 and #133 (D32), checked in Walks 22 and 27 (2026-10-18). Now in `docs/PRD.md` §4.2.
- [x] 10. Offer only broker accounts when choosing where a new investment is held. #117, checked
  in Walk 22.
- [x] 11. Only three account types: cash, bank and broker. #136, checked in Walk 28
  (2026-10-18). Now in `docs/PRD.md` §4.2.
- [x] 12. Capture recognises a subscription's charge instead of recording it twice (`DECISIONS.md`
  D35). #131, checked on 2026-10-19 (K6–K10). Now in `docs/PRD.md` §4.6.

## 8. Group visibility without deletion

**Request:** an old trip, such as June 2026, should not keep appearing when recording movements in
December, but its history must remain available.

**Merged in #140** (2026-10-18). This section goes once G2–G4 pass.

- Add a reversible **Show when adding movements** control, or equivalent **Archive / Reactivate**
  actions, for a group.
- Hide inactive/archived groups from new-movement choices, without deleting the group or changing
  any of its existing movements, totals, reports or budget effects.
- Keep archived groups accessible from group management and historical searches/filters, with a
  clear way to reactivate them.
- When editing an old movement, retain and display its already-assigned archived group. Saving
  a note or amount must not silently remove that group because it is absent from new-entry choices.
- This is a manual availability control, not automatic date-based archiving. Group dates remain
  hints for ordering unless the owner separately requests a different rule.
- Persist the flag with the group and carry it in backups. Existing groups and older backups
  should default to available. Ship the Room migration and round-trip tests with the feature.

What was settled while building, each a line the owner can change:

- **Archive and Reactivate are in the group's editor**, beside Delete, and archiving saves whatever
  else was changed on the form. Archived groups are listed apart, below the others, and a group's
  own screen says it is archived and how to bring it back.
- **Editing a movement already recorded offers every group**, archived ones last and marked
  *(archived)*: a forgotten dinner can still be put in the June trip. Only a new movement leaves
  them out.

## Acceptance for what is still to build

- **Groups:** archiving a June trip hides it from December's choices but keeps June's movements
  and totals. Editing an old movement keeps its group, and reactivating the group restores it.
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
  confirmation. Today only retention removes copies.
- **Resizable Reports cards** (D4). Reordering and hiding are built (#109).
- **PRD Phase 6:** a home-screen widget, and the release measurements `CLAUDE.md` §5 asks for
  before shipping: R8, a Baseline Profile, and Macrobenchmark on the Pixel.

## Engineering follow-ups

No owner request behind these; take one when it is next to related work.

- **`CaptureViewModel.moveTo` finds the app through the derived `uiState.value`**, where
  `requestStop` uses `editingPackage`. It cannot fail on the phone. Reading `editingPackage` would
  make the two consistent.
