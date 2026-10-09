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

- [ ] 1. English/Spanish language selection in Settings.
- [x] 2. Proper separation between the Movements title and Filters. #117, checked in Walk 22
  (2026-10-09).
- [x] 3. Category selection through a large popup, not a horizontal strip. #117, checked in
  Walk 22.
- [x] 4. Keypad visible only while the amount is active. #117, checked in Walk 22.
- [x] 5. Account popup with the last-used account preselected. #117, checked in Walk 22.
- [ ] 6. Normal money (cash and bank) and invested money (brokers), kept apart, on Wallet and at
  the top of Home. **Reshaped 2026-10-09**; its open choices are `DECISIONS.md` D34.
- [ ] 7. Set an account's current balance, choosing the historical effect each time. **Merged in
  #112**, with its review's fixes in #120 and #121. Phone check: the Walk 21 notes and Walk 23
  are still to come.
- [ ] 8. Hide/archive groups from new-movement selectors without deleting them.
- [ ] 9. Exclude broker accounts from new manual income/expense selection, but not transfers.
  **Merged in #117** for the movement form, and checked in Walk 22. Subscriptions and capture
  wait on `DECISIONS.md` D32.
- [x] 10. Offer only broker accounts when choosing where a new investment is held. #117, checked
  in Walk 22.
- [ ] 11. Only three account types: cash, bank and broker (2026-10-09). Its open choice is
  `DECISIONS.md` D34.

## 1. App language

**Request:** choose English or Spanish from inside Settings, independently of the phone language.

- Add a persistent language preference. Offer **System default**, **English** and **Español**;
  following the system is the proposed initial behavior.
- Apply the choice consistently to the app's screens, dialogs, labels, formatting and notifications,
  including capture confirmations. User-entered names and notes are not translated or rewritten.
- Use Android's per-app language support with a tested compatibility path for the app's supported
  Android versions. Do not assume adding a settings row or changing a global locale is sufficient.
- Preserve navigation and in-progress form values through any activity recreation.
- **Language must not change money.** Keep stored ISO currencies and financial calculations intact,
  including the currency chosen for a newly created account. The existing `deviceCurrency()` helper
  uses `Locale.getDefault()`, so this must be addressed explicitly rather than assuming English
  means USD or Spanish means EUR.
- Use the existing English/Spanish resources and feature-owned resource pairs for new strings.
  Any new compatibility dependency needs justification and a dedicated, serial catalog change.

## 6. Normal money and invested money, apart, on Wallet and Home

**Request (2026-10-09), replacing this item's first wording** (liquid money, investments and total
net worth on Home): see at a glance how much is **normal money**, in cash and bank accounts, and
how much is **invested**, in broker accounts, on Wallet and at the top of Home. Today Wallet's *In
accounts* adds every active account, brokers included, and net worth adds the holdings on top of
that. The owner wants the brokers' part kept out of the cash-and-bank figure, not summed with it.

Two figures, clearly distinguished:

1. **Normal money:** the balances of the active cash and bank accounts.
2. **Invested:** what the broker accounts hold, which is their cash plus their holdings at the
   latest typed prices.

- **Whether a total of the two is still shown is `DECISIONS.md` D34.** The default is no total,
  as the owner asked. That amends D19, which asked for the parts and the sum.
- **One formula for both screens.** Change `netWorthOf` and `NetWorth`
  (`domain/usecase/BuildNetWorth.kt`) and have Wallet and Home read them. Do not write a second
  formula that can disagree with Wallet.
- **Money moves between the figures, it is never counted twice.** A transfer from a bank to a
  broker moves money from normal to invested. A buy turns a broker's cash into a holding, so it
  never touches normal money.
- A holding still in a non-broker account (history that item 10 keeps) counts as invested.
- Missing prices stay explicit: never invent a value or show an unknown as zero. These are typed
  valuations, not live prices.
- Archived accounts are named, not added, as today.
- Updates to movements, transfers, balances and prices update Home and Wallet reactively.
- **On Home the two figures sit at the top**, above the month. Keep them distinct from the month's
  saved or overspent figure, and replace neither. This does not authorize another navigation or
  visual redesign.
- Until item 11 lands, every account that is not a broker counts as normal money, so this item
  does not have to wait for it.

## 7. Set the current account balance

**Request:** enter the real account balance when movements were missed, without inventing an expense
or income to explain the difference.

**Confirmed choice: offer both modes every time the balance is changed.** This is not a one-time
or global preference. Show the current balance, the requested new balance and the selected mode
before saving.

| Mode | Effect |
|---|---|
| **Preserve past balances** | Apply a dated balance correction from today, separate from income and expense transactions. Earlier balance-chart values are not shifted by this operation. |
| **Rebase opening balance** | Adjust the opening balance so the current balance reaches the requested value. Clearly warn that historical balance charts also shift. |

Implementation constraints:

- Compute `difference = requested balance - current computed balance`, including any previous
  corrections. For the dated mode, record that difference separately; for rebasing, add it to
  the opening balance. Repeated changes and switching between modes must both work.
- Calculate and write atomically so a concurrent capture cannot leave the account at the wrong
  balance. Keep money as `Long` minor units with its actual currency.
- Neither mode creates a fake income/expense or changes spending, income, budgets or savings
  statistics. Neither deletes existing movements. Future movements still change the balance normally.
- Account balances, Wallet, Home and balance-over-time queries must agree about corrections.
  Do not store a display-only override or a second mutable balance that drifts from the ledger.
- Correcting broker cash must not alter holdings, units, prices or investment profit/loss.
- Keep **opening balance** and **set current balance** clearly distinguished in the UI.
- Ship the data-model change with its migration, exported schema, migration tests and full-backup
  support. Cover restore and account removal/reassignment so corrections cannot become orphaned
  or silently disappear from the calculated balance.

## 8. Group visibility without deletion

**Request:** an old trip, such as June 2026, should not keep appearing when recording movements in
December, but its history must remain available.

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

## 9. Broker account eligibility

**Request:** broker accounts are not choices for ordinary new manual expenses/incomes; they remain
choices for transfers.

- Filter both the account popup and its default/remembered selection, not just what is drawn.
- Preserve brokers in transfers, investment operations, and Wallet's and Home's totals, where
  they count as invested money (item 6).
- Preserve existing broker-linked movements when editing history; do not silently reassign them.
- Do not turn a picker restriction into removal of broker accounts or rejection of internal
  investment cash movements. Audit affected entry points and make the intended scope explicit.

## 11. Three account types: cash, bank and broker

**Request (2026-10-09):** an account is **Cash**, **Bank** or **Broker**, and nothing else. *Card*,
*Savings* and *Other* go.

- **An account of a removed type is retyped, never lost.** Which type it becomes is
  `DECISIONS.md` D34 (default: Bank). Its movements, balance, corrections and history stay as
  they are.
- **The type is stored by name** and read with `AccountType.valueOf` (`Converters.kt`). Dropping
  the three constants without rewriting the stored rows would crash on the first read. Ship a Room
  migration (v11) that rewrites them, with its migration test and exported schema.
- **A backup made before this still restores.** Reading it maps the old names the same way
  (`BackupMappers.kt`), with a round-trip test.
- The account editor offers the three types. The three unused strings leave both `values/` and
  `values-es/`.
- What already keeps brokers apart (items 9 and 10, D32) is unchanged.
- A Room migration, so it runs alone, like item 8.

## Acceptance for what is still to build

- **Language:** the choice survives relaunch and reaches notifications. It leaves stored
  currencies, balances, dates, capture's words and anything the owner typed exactly as they were.
- **Normal and invested money:** Home and Wallet agree after an income, an expense, a transfer, a
  trade, a price update and either balance correction. A transfer from a bank to a broker moves
  money from one figure to the other, a buy leaves normal money alone, and unpriced holdings are
  explicit.
- **Account types:** a Card, Savings or Other account comes through the migration and through a
  restore of an older backup as the D34 type, with the same balance and movements.
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

- **Pin the test database's query context to the test thread**, which would close the Room races
  in view-model tests at the root ([`docs/testing.md`](testing.md)). It touches every Room test.
- **`CaptureViewModel.moveTo` finds the app through the derived `uiState.value`**, where
  `requestStop` uses `editingPackage`. It cannot fail on the phone. Reading `editingPackage` would
  make the two consistent.
