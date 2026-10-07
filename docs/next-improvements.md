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
- [ ] 2. Proper separation between the Movements title and Filters. **Merged in #117**; phone
  check in Walk 22.
- [ ] 3. Category selection through a large popup, not a horizontal strip. **Merged in #117**;
  phone check in Walk 22.
- [ ] 4. Keypad visible only while the amount is active. **Merged in #117**; phone check in
  Walk 22.
- [ ] 5. Account popup with the last-used account preselected. **Merged in #117**; phone check
  in Walk 22.
- [ ] 6. Liquid money, investments and total net worth on Home.
- [ ] 7. Set an account's current balance, choosing the historical effect each time. **Merged in
  #112**; phone check in Walk 21. Its review's findings are `fix/balance-adjustments-review`.
- [ ] 8. Hide/archive groups from new-movement selectors without deleting them.
- [ ] 9. Exclude broker accounts from new manual income/expense selection, but not transfers.
  **Merged in #117** for the movement form; phone check in Walk 22. Subscriptions and capture
  wait on `DECISIONS.md` D32.
- [ ] 10. Offer only broker accounts when choosing where a new investment is held. **Merged in
  #117**; phone check in Walk 22.

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

## 2. Movements title and Filters

**Request:** the title and filter action are too close together.

- Give the title and actions distinct layout space, with adequate gutters and touch targets.
- Check both the expanded header and its compact/scrolled state, including the active-filter count.
- Keep the title legible without Filters or the add action crowding it. Use an adaptive layout,
  not a spacing value that only happens to fit one device or one language.
- Verify English, Spanish, large system fonts, phone widths and the Honor tablet.

## 3. Category popup

**Confirmed clarification:** this request concerns the form for adding/editing an expense or income,
not merely the To categorize inbox. Do not require horizontal scrolling through category chips.

- The main form shows one compact, tappable disclosure row: **Category**, or **Category: X** after
  selection. Its chevron indicates that it opens the selector.
- Tapping the row opens a large popup/sheet, expandable to most or all of the screen when needed.
- Show choices vertically, with search for long lists, readable names and clear selection state.
  Do not leave every category expanded in the main form.
- Selecting a category applies it, closes the popup and updates the row immediately. Selecting the
  already-selected category keeps it selected; clearing it should be an explicit action.
- Offer categories of the current income/expense kind. Preserve existing support for creating a
  category and for saving uncategorized movements; this request does not make category mandatory.
- Opening the popup also hides the amount keypad, in the same gesture.

## 4. Keypad behavior

**Confirmed choice:** a new movement starts with the amount active and the keypad visible.

- Tapping a category, account, date, group, note or a blank area outside amount editing hides it.
- Tapping the keypad's own keys does **not** hide it: those keys are part of amount editing.
- Tapping the amount brings it back. Showing or hiding it never clears or changes the entered value.
- A picker-opening tap must both hide the keypad and open the picker; do not consume that tap just
  to dismiss the keypad. Do not stack the custom keypad with the system text keyboard.
- Opening an existing movement to read/edit it should not force the keypad to appear before the
  amount is activated. Check dismissal, navigation, rotation and restoration of the form.

## 5. Account popup and remembered choice

**Confirmed choice:** use the same compact-row/popup interaction as categories, with the last-used
account already selected. The owner explicitly withdrew the earlier request to investigate the
"two taps" behavior separately; replacing the interaction is the task.

- Show the selected account in one row. Tapping it opens the available accounts; choosing one
  applies it and closes the popup without an additional confirmation.
- Persist the last-used account between sessions. The proposed rule is to update it after a new
  manual income/expense is successfully saved, not merely after a selection in a cancelled form.
- Transfers, investment trades and background capture should not overwrite this manual-entry
  preference. Saving the preference must not cause a successfully recorded movement to be retried.
- Revalidate the remembered account: it must exist, be active and not be a broker. Handle deleted,
  archived, restored or retyped accounts safely rather than keeping a stale selection.
- Fall back to an eligible account when necessary. If none exists, offer to create one instead of
  leaving a blank, unusable selector or silently selecting a broker.

## 6. Net worth on Home

**Request:** Home should show the money the owner has, in addition to the Wallet view.

Add a card with three clearly distinguished values:

1. **Liquid money / in accounts.**
2. **Investments**, valued at their latest entered prices.
3. **Total net worth**, the two together.

- Reuse the Wallet/Accounts calculation and eligibility rules, including existing warnings about
  excluded balances. `netWorthOf` and `NetWorth` already express this split; do not create a second
  formula that can disagree with Wallet.
- Broker cash still belongs in account balances. Buying an investment spends cash, so never add
  both that spent cash and the holding's value as if they were separate assets.
- Missing investment prices must remain explicit; do not invent a value or present an unknown as
  zero. These are manually entered valuations, not live market prices.
- Updates to movements, transfers, balances and prices should update Home reactively.
- Keep net worth distinct from the month's saved/overspent figure. Do not replace one with the
  other or remove the existing Wallet totals.
- This request does not authorize another complete navigation or visual redesign.

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
- Preserve brokers in transfers, investment operations, Wallet and Home's cash/net-worth totals.
- Preserve existing broker-linked movements when editing history; do not silently reassign them.
- Do not turn a picker restriction into removal of broker accounts or rejection of internal
  investment cash movements. Audit affected entry points and make the intended scope explicit.

## 10. Investments only in broker accounts

**Request (2026-10-05):** the mirror of item 9. Just as a broker account is not offered for an
ordinary income or expense, an account that is **not** a broker is not offered for an investment.

- A new holding's account choice (`HoldingEditViewModel`) offers broker accounts only, so its
  trades never spend or return a current account's cash.
- Trades take their holding's account, so restricting the holding is what restricts the cash.
  Check the other entry points that create a holding or a trade and apply the same rule.
- **Existing history is kept.** A holding already in a non-broker account keeps it, is still shown
  and editable, and its account stays displayed when the form opens; nothing is reassigned
  silently. The same rule as item 9's broker-linked movements and item 8's archived groups.
- **No broker account yet:** offer to create one rather than leaving an empty, unusable picker or
  silently choosing another kind of account.
- Archived broker accounts are not offered for a new holding, as today.
- Wallet, net worth, the portfolio and transfers into and out of broker accounts are unchanged.

## Acceptance for what is still to build

- **Language:** the choice survives relaunch and reaches notifications. It leaves stored
  currencies, balances, dates, capture's words and anything the owner typed exactly as they were.
- **Home net worth:** Home and Wallet agree after an income, an expense, a transfer, a trade, a
  price update and either balance correction. Unpriced investments are explicit, and a transfer
  does not inflate net worth.
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
