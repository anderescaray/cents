package io.github.anderescaray.cents.ui.feature.accounts

import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.account
import io.github.anderescaray.cents.data.local.createTestDatabase
import io.github.anderescaray.cents.data.repository.RoomAccountRepository
import io.github.anderescaray.cents.data.repository.RoomPortfolioRepository
import io.github.anderescaray.cents.ui.theme.CentsTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * The portfolio has no tab since #109, and the Invested tile is drawn only beside a net worth. With
 * every account archived there is none, and the portfolio must still be one tap away.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class WalletPortfolioRowTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var database: CentsDatabase
    private val clock = Clock.fixed(Instant.parse("2026-10-05T10:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        database = createTestDatabase()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `with every account archived, the portfolio is still reachable`() {
        runBlocking { database.accountDao().insert(account(name = "Broker", archived = true)) }
        var opened = 0
        composeRule.setContent {
            CentsTheme {
                AccountsScreen(
                    onNewAccount = {},
                    onOpenAccount = {},
                    onOpenInvestments = { opened++ },
                    viewModel =
                        AccountsViewModel(
                            RoomAccountRepository(database.accountDao(), database.transactionDao(), database, clock),
                            RoomPortfolioRepository(
                                database.holdingDao(),
                                database.tradeDao(),
                                database.priceQuoteDao(),
                            ),
                        ),
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasTestTag(PORTFOLIO_ROW_TAG)).fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodes(hasScrollAction()).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag(PORTFOLIO_ROW_TAG))
        composeRule.onNodeWithTag(PORTFOLIO_ROW_TAG).performClick()

        assertEquals(1, opened)
    }
}
