# Known limitations

What Cents deliberately does not do, or does in a way that can surprise, so that none of it is
mistaken for a bug. Each entry is still true today. When one stops being true, delete it; git
keeps the history.

Answered product questions live in [`DECISIONS.md`](../DECISIONS.md), and bugs in
[`FEEDBACK.md`](../FEEDBACK.md). This page is for neither: it covers the edges of what was built
on purpose.

## Money and currency

- **One currency, and no conversion** (D2, D12, D18). The device's currency is used, and `Money`
  refuses to add two currencies as a guard rail. Where two somehow coexist, the app shows no
  total rather than an approximate one. A holding tracks a euro listing (`NVD.DE`, not `NVDA`),
  so nothing needs converting.
- **A quote stored in a currency the holding does not use reads as *no price*.** That can only
  happen on an install from before D2.

## Accounts

- **Deleting an account together with its movements cannot be undone.** It asks twice and names
  how many go. Moving the movements to another account is the path that keeps everything.
- **Moving movements only offers active accounts in the same currency.** An archived account is
  not a destination. If it is the only one, the option shows as disabled, with the reason.
- **A transfer moved into its own other side stays a transfer.** Deleting an account by moving
  its movements can leave both legs in one account, reading *Transfer to Checking* in Checking.
  It changes no balance and no report, and the move sheet warns about it before it runs.
- **An account that holds investments cannot be deleted at all.** A holding cannot be moved to
  another account.
- **An account a subscription records into cannot be deleted** until the rule is deleted. A rule
  with charges behind it has to be stopped first.
- **Net worth counts active accounts only.** Money in an archived account is named in the
  footer, not added.

## Movements, search and filters

- **The undo window is six seconds and held in memory.** Closing the app forgets the deletion.
  An undone movement comes back with a new internal `createdAt`, and nothing reads it.
- **Filters, the Reports account and the watched trend category are forgotten when the app
  closes.** Remembering any of them would be a saved-views feature, not a flag.
- **A filter narrows the Movements list only.** An account's own screen ignores it.
- **The amount filter is a text field in minor units** (2500 means 25,00). Its footer reads the
  range back in money.
- **A weekday filter is computed per row with `strftime`.** No index covers it. The date range
  still narrows first. Revisit only if a long history makes the filtered list slow.
- **The weekend is Saturday and Sunday.** Both shipped languages agree. A right-to-left
  translation would also have to mirror the swipe-to-delete, which assumes left to right.
- **Two swiped rows can be open at once.** A tap closes either one.

## Reports

- **The category trend reads outside the selected period, on purpose.** A trend needs history,
  so it shows the twelve periods ending with the selected one. The period decides the bucket
  size.
- **The weekday chart is expenses only.**
- **A report built from two flows can show one frame of a half-updated answer.** The balance
  line and the period comparison each `combine` two queries. One write that touches both can
  render as counted twice, or not at all, for a frame. Making it atomic would cost the comparison
  its index.
- **Narrowing a report to one account does not use `index_transactions_accountId_date`.** The
  query uses `:accountId IS NULL OR t.accountId = :accountId`, and that is not sargable. Two
  queries per report would have to be kept saying the same thing. Split it first if Reports is
  ever slow on a long history.
- **Any change to any account re-runs every chart query.** It is bounded work on a handful of
  rows.

## Investments

- **Prices are typed** (D20, D25). No symbol is checked against a market, and a symbol alone is
  ambiguous (`NVD` is NVIDIA in Frankfurt and a Thai developer in Bangkok). If a provider ever
  comes, a holding needs a MIC code beside its symbol.
- **A holding corrected down to zero units without a sale is neither open nor closed.** It has no
  quantity and no realised result, so neither list shows it.
- **The portfolio is rebuilt in Kotlin whenever anything changes.** That cost grows with the
  number of trades, not with time. If it ever gets slow, query each holding's totals and fold
  only where a sale exists.

## Subscriptions and reminders

- **Due charges are written when the app comes to the front, or when a rule is saved.** A phone
  left untouched for a week records the rent when it is next opened, dated correctly.
- **A reminder arrives when the daily job runs**, booked around 09:00. Doze and battery saver can
  move it by hours.
- **Turning notifications off in the system keeps the reminders stored**, so turning them back
  on restores them. The editor says when nothing will be shown.
- **A variable bill with no reminder is only seen on the Subscriptions screen.**
- **Only the oldest waiting occurrence of each rule can be answered.** The watermark is one date.
- **Skipping a waiting occurrence cannot be undone from that screen.** The confirmation is the
  protection.
- **Editing a rule changes what it writes next, never what it already wrote.** Past charges are
  edited as ordinary movements.
- **Monthly and yearly figures are estimates over a 365-day year.** A weekly charge counts 52.14
  times a year.
- **A form left open across midnight keeps the date it opened with.** The screens that stay
  open follow the day (`ui/util/Today`).

## Backup, restore and devices

- **A restore replaces everything, with no undo** (D21). The confirmation names what arrives and
  what goes, and says to export first.
- **A backup carries the ledger, not the settings.** Capture's apps and words, the Reports order,
  the Drive switch and the remembered account live in DataStore and are not restored.
- **Two devices are not two copies of the app.** Drive is a backup (D22), not sync. One device
  records, and the other gets the data by restoring.
- **The CSV export is movements only.** The JSON has everything.
- **Capture ties an app to an account by id, and a restore keeps ids.** An old backup restored
  after an account was deleted and recreated could send captures to a different account.
  Settings › Capture shows each app's account by name. The "same minute" memory lives in the
  process, so a duplicate that arrives across a restart is not caught.

## Data made by older versions

Nothing is backfilled, because that would mean inventing rows the owner never saw:

- Installs from before `feat/categories` and `feat/account-deletion` still hold the seeded
  categories and the *Cash* account.
- Trades from before Room v4 have no cash movement until they are saved again.
- Trades from before #73 have a cash movement with no note until they are saved again.
- Drive copies from before #72 carry no subscriptions. Restoring one empties them.
- Categories are flat (D3). The `parentId` and `iconKey` columns exist and are unused.
