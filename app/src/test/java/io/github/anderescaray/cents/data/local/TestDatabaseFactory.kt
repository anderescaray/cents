package io.github.anderescaray.cents.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.anderescaray.cents.data.local.entity.AccountEntity
import io.github.anderescaray.cents.data.local.entity.BudgetEntity
import io.github.anderescaray.cents.data.local.entity.CategoryEntity
import io.github.anderescaray.cents.data.local.entity.GroupEntity
import io.github.anderescaray.cents.data.local.entity.HoldingEntity
import io.github.anderescaray.cents.data.local.entity.PriceQuoteEntity
import io.github.anderescaray.cents.data.local.entity.RecurringRuleEntity
import io.github.anderescaray.cents.data.local.entity.TradeEntity
import io.github.anderescaray.cents.data.local.entity.TransactionEntity
import io.github.anderescaray.cents.domain.model.AccountType
import io.github.anderescaray.cents.domain.model.AssetClass
import io.github.anderescaray.cents.domain.model.CategoryKind
import io.github.anderescaray.cents.domain.model.Frequency
import io.github.anderescaray.cents.domain.model.Quantity
import io.github.anderescaray.cents.domain.model.QuoteSource
import io.github.anderescaray.cents.domain.model.RuleMode
import io.github.anderescaray.cents.domain.model.TradeSide
import io.github.anderescaray.cents.domain.model.TransactionKind
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.Executor

/**
 * An in-memory database, discarded when the test ends.
 *
 * **Every query runs on the thread that asks for it.** With Room's own executors, a view model
 * under test resumed on Room's threads, because the tests' `Dispatchers.Main` is unconfined, and
 * two of its collectors could run at once: a race the phone never has, where everything is on the
 * main thread. Run in place, a write, the invalidation it causes and every flow it wakes happen in
 * order on the test thread, as they would on the phone's one main thread. Robolectric's test
 * thread is the main looper, hence `allowMainThreadQueries`; `TestDatabaseFactoryTest` makes sure no
 * DAO method could block there.
 *
 * One difference from the phone remains: here a write's flows emit before the write returns, and
 * on the phone a moment after. A test waits for the state it asserts (`awaitStateThat`), and never
 * relies on the order.
 */
fun createTestDatabase(): CentsDatabase =
    Room
        .inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            CentsDatabase::class.java,
        ).setQueryExecutor(RunInPlace)
        .setTransactionExecutor(RunInPlace)
        .allowMainThreadQueries()
        .build()

private val RunInPlace = Executor { task -> task.run() }

internal val FIXED_INSTANT: Instant = Instant.parse("2026-08-18T10:00:00Z")

fun account(
    name: String = "Checking",
    currency: String = "EUR",
    openingBalanceMinor: Long = 0,
    type: AccountType = AccountType.BANK,
    archived: Boolean = false,
) = AccountEntity(
    name = name,
    type = type,
    currency = currency,
    openingBalanceMinor = openingBalanceMinor,
    colorArgb = 0xFF00796B.toInt(),
    archived = archived,
    createdAt = FIXED_INSTANT,
)

fun category(
    name: String = "Groceries",
    kind: CategoryKind = CategoryKind.EXPENSE,
    parentId: Long? = null,
    archived: Boolean = false,
    sortOrder: Int = 0,
) = CategoryEntity(
    name = name,
    kind = kind,
    colorArgb = 0xFFEF6C00.toInt(),
    parentId = parentId,
    archived = archived,
    sortOrder = sortOrder,
    createdAt = FIXED_INSTANT,
)

fun transaction(
    accountId: Long,
    categoryId: Long? = null,
    kind: TransactionKind = TransactionKind.EXPENSE,
    amountMinor: Long = 1_000,
    currency: String = "EUR",
    date: LocalDate = LocalDate.of(2026, 8, 18),
    note: String? = null,
    /** Set on both legs of a transfer, and null for an ordinary movement. */
    transferId: Long? = null,
    /** Set on the cash a purchase took out of the account, or a sale put back. */
    tradeId: Long? = null,
    /** Set on a movement a recurring rule generated rather than one that was typed. */
    ruleId: Long? = null,
    /** Set on a movement that belongs to an occasion: a trip, a freelance job. */
    groupId: Long? = null,
) = TransactionEntity(
    accountId = accountId,
    categoryId = categoryId,
    kind = kind,
    amountMinor = amountMinor,
    currency = currency,
    date = date,
    note = note,
    transferId = transferId,
    tradeId = tradeId,
    ruleId = ruleId,
    groupId = groupId,
    createdAt = FIXED_INSTANT,
    updatedAt = FIXED_INSTANT,
)

fun holding(
    accountId: Long,
    symbol: String = "NVDA",
    name: String = "NVIDIA",
    assetClass: AssetClass = AssetClass.STOCK,
    currency: String = "EUR",
    note: String? = null,
) = HoldingEntity(
    accountId = accountId,
    assetClass = assetClass,
    symbol = symbol,
    name = name,
    currency = currency,
    note = note,
    createdAt = FIXED_INSTANT,
    updatedAt = FIXED_INSTANT,
)

fun trade(
    holdingId: Long,
    side: TradeSide = TradeSide.BUY,
    /** Whole units, which is what most tests want; a fractional one passes the scaled value. */
    units: Long = 1,
    quantityScaled: Long = units * Quantity.SCALE,
    pricePerUnitMinor: Long = 10_000,
    feeMinor: Long = 0,
    currency: String = "EUR",
    date: LocalDate = LocalDate.of(2026, 8, 18),
) = TradeEntity(
    holdingId = holdingId,
    side = side,
    quantityScaled = quantityScaled,
    pricePerUnitMinor = pricePerUnitMinor,
    feeMinor = feeMinor,
    currency = currency,
    date = date,
    createdAt = FIXED_INSTANT,
)

fun quote(
    symbol: String = "NVDA",
    priceMinor: Long = 12_000,
    currency: String = "EUR",
    asOf: Instant = FIXED_INSTANT,
    source: QuoteSource = QuoteSource.MANUAL,
) = PriceQuoteEntity(
    symbol = symbol,
    priceMinor = priceMinor,
    currency = currency,
    asOf = asOf,
    source = source,
)

fun recurringRule(
    accountId: Long,
    name: String = "Netflix",
    categoryId: Long? = null,
    kind: TransactionKind = TransactionKind.EXPENSE,
    amountMinor: Long = 1_299,
    currency: String = "EUR",
    frequency: Frequency = Frequency.MONTHLY,
    intervalDays: Int? = null,
    startDate: LocalDate = LocalDate.of(2026, 1, 15),
    endDate: LocalDate? = null,
    mode: RuleMode = RuleMode.AUTOMATIC,
    active: Boolean = true,
    lastGenerated: LocalDate? = null,
) = RecurringRuleEntity(
    name = name,
    accountId = accountId,
    categoryId = categoryId,
    kind = kind,
    amountMinor = amountMinor,
    currency = currency,
    frequency = frequency,
    intervalDays = intervalDays,
    startDate = startDate,
    endDate = endDate,
    mode = mode,
    active = active,
    lastGenerated = lastGenerated,
    createdAt = FIXED_INSTANT,
)

fun budget(
    categoryId: Long,
    amountMinor: Long = 30_000,
    currency: String = "EUR",
) = BudgetEntity(
    categoryId = categoryId,
    amountMinor = amountMinor,
    currency = currency,
    createdAt = Instant.parse("2026-09-23T10:00:00Z"),
)

fun group(
    name: String = "Lisbon trip",
    startDate: LocalDate? = null,
    endDate: LocalDate? = null,
) = GroupEntity(
    name = name,
    startDate = startDate,
    endDate = endDate,
    createdAt = Instant.parse("2026-09-25T10:00:00Z"),
)
