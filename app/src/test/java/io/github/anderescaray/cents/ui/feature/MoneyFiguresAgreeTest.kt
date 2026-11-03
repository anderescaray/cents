package io.github.anderescaray.cents.ui.feature

import app.cash.turbine.test
import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.account
import io.github.anderescaray.cents.data.local.createTestDatabase
import io.github.anderescaray.cents.data.local.holding
import io.github.anderescaray.cents.data.local.portfolioRepository
import io.github.anderescaray.cents.data.local.tradeCashWriter
import io.github.anderescaray.cents.data.local.transaction
import io.github.anderescaray.cents.data.repository.RoomAccountRepository
import io.github.anderescaray.cents.data.repository.RoomBalanceRepository
import io.github.anderescaray.cents.data.repository.RoomHoldingValueRepository
import io.github.anderescaray.cents.data.repository.RoomRecurringRuleRepository
import io.github.anderescaray.cents.data.repository.RoomTradeRepository
import io.github.anderescaray.cents.data.repository.RoomTransactionRepository
import io.github.anderescaray.cents.data.repository.RoomTransferRepository
import io.github.anderescaray.cents.data.repository.reports.RoomSpendingByCategoryRepository
import io.github.anderescaray.cents.domain.model.AccountType
import io.github.anderescaray.cents.domain.model.BalanceChange
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.MoneyFigure
import io.github.anderescaray.cents.domain.model.MoneyFigures
import io.github.anderescaray.cents.domain.model.NetWorth
import io.github.anderescaray.cents.domain.model.Trade
import io.github.anderescaray.cents.domain.model.TradeSide
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.domain.model.Transfer
import io.github.anderescaray.cents.ui.feature.accounts.AccountsViewModel
import io.github.anderescaray.cents.ui.feature.home.HomeViewModel
import io.github.anderescaray.cents.ui.filter.TransactionFilterStore
import io.github.anderescaray.cents.ui.util.Today
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Wallet and the top of Home show the same figures, from the same formula, after every kind of
 * change that moves money: an expense, an income, a transfer to a broker, a purchase, a typed
 * price and a balance correction. The switches in Settings reach Home; Wallet does not read them
 * (`DECISIONS.md` D37).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MoneyFiguresAgreeTest {
    private lateinit var database: CentsDatabase
    private val viewModels = ViewModelKeeper()
    private val figures = FakeMoneyFigures()
    private lateinit var wallet: AccountsViewModel
    private lateinit var home: HomeViewModel

    private val clock = Clock.fixed(Instant.parse("2026-10-16T10:00:00Z"), ZoneOffset.UTC)
    private val today = LocalDate.of(2026, 10, 16)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        database = createTestDatabase()
        val accounts = RoomAccountRepository(database.accountDao(), database.transactionDao(), database, clock)
        val portfolio = portfolioRepository(database, clock)
        wallet = viewModels.keep(AccountsViewModel(accounts, portfolio))
        home =
            viewModels.keep(
                HomeViewModel(
                    accounts = accounts,
                    transactions = RoomTransactionRepository(database.transactionDao(), database, clock),
                    spending = RoomSpendingByCategoryRepository(database.transactionDao()),
                    rules =
                        RoomRecurringRuleRepository(
                            database.recurringRuleDao(),
                            database.ruleReminderDao(),
                            database,
                            clock,
                        ),
                    portfolio = portfolio,
                    figures = figures,
                    filters = TransactionFilterStore(),
                    today = Today(clock),
                ),
            )
    }

    @After
    fun tearDown() {
        try {
            viewModels.cancelAll()
            if (::database.isInitialized) database.close()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `the two screens agree after every change that moves money`() =
        runTest {
            val checking = database.accountDao().insert(account(name = "Checking", openingBalanceMinor = 100_000))
            val broker = database.accountDao().insert(account(name = "Broker", type = AccountType.BROKER))
            bothRead(normal = 100_000, invested = 0)

            database.transactionDao().insert(transaction(accountId = checking, amountMinor = 2_550, date = today))
            bothRead(normal = 97_450, invested = 0)

            database.transactionDao().insert(
                transaction(accountId = checking, kind = TransactionKind.INCOME, amountMinor = 5_000, date = today),
            )
            bothRead(normal = 102_450, invested = 0)

            RoomTransferRepository(database.transactionDao(), database, clock).saveTransfer(
                Transfer(
                    id = Transfer.NEW,
                    fromAccountId = checking,
                    toAccountId = broker,
                    amount = Money(50_000, "EUR"),
                    fee = Money.zero("EUR"),
                    date = today,
                    note = null,
                ),
            )
            bothRead(normal = 52_450, invested = 50_000)

            // 200 € of the broker's cash becomes an asset, worth what was paid until a value is
            // typed (D40): the invested figure does not move.
            val holdingId = database.holdingDao().insert(holding(accountId = broker))
            val cash = tradeCashWriter(database, clock)
            RoomTradeRepository(database.tradeDao(), database.transactionDao(), cash, database, clock)
                .addTrade(
                    Trade(
                        id = 0L,
                        holdingId = holdingId,
                        side = TradeSide.BUY,
                        amount = Money(20_000, "EUR"),
                        date = today,
                        note = null,
                    ),
                )
            bothRead(normal = 52_450, invested = 50_000)

            RoomHoldingValueRepository(database.holdingValueDao(), database.holdingDao(), cash, database, clock)
                .setValue(holdingId, today, Money(24_000, "EUR"))
            bothRead(normal = 52_450, invested = 54_000)

            RoomBalanceRepository(database, clock)
                .setBalance(checking, Money(60_000, "EUR"), BalanceChange.CORRECT_FROM_TODAY, today)
            bothRead(normal = 60_000, invested = 54_000)
        }

    @Test
    fun `the choice made in Settings reaches Home`() =
        runTest {
            database.accountDao().insert(account(name = "Checking", openingBalanceMinor = 100_000))
            val chosen = MoneyFigures(normal = false, invested = false, total = true)

            figures.set(MoneyFigure.NORMAL, on = false)
            figures.set(MoneyFigure.INVESTED, on = false)
            figures.set(MoneyFigure.TOTAL, on = true)

            home.uiState.test {
                awaitStateThat { it.figures == chosen }
                cancelAndIgnoreRemainingEvents()
            }
        }

    /** Waits for each screen to show these figures, and checks the two hold the same [NetWorth]. */
    private suspend fun bothRead(
        normal: Long,
        invested: Long?,
    ) {
        val expected = { worth: NetWorth? ->
            worth?.normal == Money(normal, "EUR") && worth.invested == invested?.let { Money(it, "EUR") }
        }
        var onWallet: NetWorth? = null
        var onHome: NetWorth? = null
        wallet.uiState.test {
            onWallet = awaitStateThat { expected(it.netWorth) }.netWorth
            cancelAndIgnoreRemainingEvents()
        }
        home.uiState.test {
            onHome = awaitStateThat { expected(it.netWorth) }.netWorth
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(onWallet, onHome)
    }
}
