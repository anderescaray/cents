# Next improvements — owner-confirmed requirements

- **Recorded:** 2026-10-04.
- **Status:** planned, not implemented by this documentation change.
- **Purpose:** preserve the owner's requested changes and clarifications so a new agent can
  continue without the original conversation. Read this before choosing the next implementation
  task, alongside `CLAUDE.md`, `FEEDBACK.md`, `DECISIONS.md` and `PLAN.md`.

These are product requests, not a claim that the features already exist or blanket permission to
merge code. Keep the usual review, territory, migration, localization and data-preservation rules.
The implementation sequence below is the proposed split, not permission to start all branches at once.

## Before resuming implementation

1. **Check the actual UI baseline.** At the time this plan was recorded, `main` ended at #108 and
   [UI rework PR #109](https://github.com/anderescaray/cents/pull/109), branch `feat/ui-rework`, was
   open. That PR introduces the Home/Movements/Reports/Wallet shell, horizontal category chips and
   per-app capture words. Its description says it was installed on the Pixel 11 Pro. This explains
   why the owner's feedback can differ from the older form and bubble launcher on `main`.
   Check the PR's current status; do not assume this documentation merges or approves it. Apply
   the requested improvements to the agreed, integrated UI, not to an obsolete screen.
2. **Preserve the separate capture work.** A local branch, `fix/capture-safety-and-feedback`, held
   uncommitted changes for safer amount parsing, optional capture confirmations and removal of
   the app lock when this document was written. Inspect Git and PR status before assuming those
   changes are merged. Review and reconcile them with the UI rework separately, including its
   per-app word lists. Do not discard that work or sweep it into a documentation commit.
3. Start implementation branches from the latest `origin/main` after their dependencies land.
   Do not stack them on an unmerged branch. Give each task an explicit write territory.
4. New phone failures in `FEEDBACK.md` still take priority. Otherwise use this backlog rather than
   assuming an older roadmap paragraph contains all of the owner's requests.

## Completion checklist

Only mark an item complete after it is implemented, reviewed and merged, with the relevant tests
and device checks recorded. Add the implementing PR when updating its status.

- [ ] 1. English/Spanish language selection in Settings.
- [ ] 2. Proper separation between the Transactions title and Filters.
- [ ] 3. Category selection through a large popup, not a horizontal strip.
- [ ] 4. Keypad visible only while the amount is active.
- [ ] 5. Account popup with the last-used account preselected.
- [ ] 6. Liquid money, investments and total net worth on Home.
- [ ] 7. Set an account's current balance, choosing the historical effect each time. **Built
  and merged in #112 (2026-10-05)**; the phone check is still to do, so it stays unticked.
- [ ] 8. Hide/archive groups from new-movement selectors without deleting them.
- [ ] 9. Exclude broker accounts from new manual income/expense selection, but not transfers.

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

## 2. Transactions title and Filters

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

## Proposed implementation sequence

First resolve the baseline and pending work described above. Then use one focused PR per branch:

| Order | Proposed branch | Scope |
|---|---|---|
| 1 | `fix/transaction-entry-ux` | Items 2, 3, 4, 5 and 9: header spacing, popup selectors, keypad focus, last account and broker eligibility. |
| 2 | `feat/app-language` | Item 1: persistent language selection, compatible locale handling, notifications and currency safeguards. |
| 3 | `feat/home-net-worth` | Item 6: the reusable Home wealth summary, using Wallet's calculation. |
| 4 | `feat/account-balance-adjustments` | Item 7: both per-operation modes, account UI, consistent balance queries, migration and backup support. **Merged as #112, ahead of its turn, at the owner's request.** |
| 5 | `feat/group-archive` | Item 8: group availability, selectors, historical preservation, migration and backup support. |

- Keep shared-component work coordinated. Do not run two form/picker branches against the same files.
- Room changes, design-system changes and dependency-catalog changes run serially under the existing
  global-lock rules. In particular, land the balance migration before starting the group migration;
  determine the actual version numbers from the then-current `main`.
- No new bank API, price provider, full UI rewrite or capture-deduplication redesign is bundled here.
- Local capture/lock work is a separate delivery, not an implemented item in this checklist.

## Verification and acceptance

- English and Spanish selection survives relaunch, affects app notifications and leaves stored
  currencies, balances, dates, parser settings and user-entered text intact.
- Many categories and long names remain usable without horizontal scrolling. Selection closes the
  popup; cancelling preserves the previous choice; income/expense choices never mix.
- A picker opens on the first gesture while the keypad is visible. Outside taps hide the keypad,
  keypad taps keep it visible, and no entered amount or note is lost.
- The account default survives restart and remains valid after archive/delete/type changes or
  restore. A broker-only account list does not make a new ordinary movement silently use a broker.
- The Transactions title, Filters and add action remain separated in both languages, collapsed
  and expanded, at large font sizes and on phone/tablet layouts.
- Home and Wallet agree after income, expense, transfer, trade, price update and either balance
  correction mode. Unpriced investments are explicit and a transfer does not inflate net worth.
- Test both balance modes with positive, zero and negative targets, previous corrections, later
  movements and concurrent writes. Income/expense reports and budgets remain unchanged; only the
  selected mode's intended historical-balance effect occurs.
- Archiving a June trip hides it from new December choices but keeps the June movements and totals.
  Editing an old movement preserves its group, and reactivation restores availability.
- Migration and backup/restore tests cover the new fields/records and backward compatibility.
- Run the project's format, static-analysis, unit-test and debug-build gates, inspect the actual
  test reports, and perform the relevant device checks. A passing JVM suite alone is not a claim
  that picker gestures, keyboard transitions or system-language behavior were verified on a phone.

## Requested on 2026-10-05, after this list was written

- **A notification each time capture records a movement.** The owner asked whether the app
  announces an automatic expense or income, and wants it to. `main` does not: capture records
  silently. The uncommitted `fix/capture-safety-and-feedback` work builds it as a *Notify after
  capture* switch in Settings › Capture: *Expense added*, the signed amount and the account, on
  its own channel, private on the lock screen, opening the movement when tapped, and silent for
  ignored or duplicate notifications. **It is off by default there; the owner wants it on.** It
  ships with that work, after #109.
- **Amount detection the owner can check and adjust.** On `main`, a masked card number written
  right before a currency marker (`tarjeta ****7146 €23,00`) is read as the amount, 7.146 €. The
  same uncommitted work fixes that: masks removed first, a currency required, and two amounts
  ignored rather than guessed. The owner also asked for detection to be editable. Two shapes were
  offered, and the choice is not made yet: a *Try a notification* box in Settings › Capture that
  shows what a pasted text would record and why, and editable *ignore* words (*saldo*,
  *disponible*) so an amount after one is never taken as the payment.

## Handoff rule

Keep this file as the durable record of these requests. When work merges or the owner changes a
choice, update the relevant status and link the implementing PR in an authorized documentation
change. If the file is outside an implementation task's write territory, report the update for the
coordinator instead of editing shared documentation in parallel. Do not mark the backlog complete
because its documentation was merged.
