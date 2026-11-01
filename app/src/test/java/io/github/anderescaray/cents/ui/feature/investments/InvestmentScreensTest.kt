package io.github.anderescaray.cents.ui.feature.investments

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import io.github.anderescaray.cents.domain.model.Account
import io.github.anderescaray.cents.domain.model.AccountType
import io.github.anderescaray.cents.domain.model.AssetClass
import io.github.anderescaray.cents.domain.model.Holding
import io.github.anderescaray.cents.domain.model.HoldingValue
import io.github.anderescaray.cents.domain.model.InvestmentAccount
import io.github.anderescaray.cents.domain.model.InvestmentOverview
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.Trade
import io.github.anderescaray.cents.domain.model.TradeSide
import io.github.anderescaray.cents.domain.usecase.positionOf
import io.github.anderescaray.cents.ui.theme.CentsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** Invested, one broker account and one asset, rendered from their state (`DECISIONS.md` D40). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class InvestmentScreensTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val day = LocalDate.of(2026, 10, 5)
    private val broker = Account(2, "Trade Republic", AccountType.BROKER, "EUR", Money.zero("EUR"), 0, false, 0)
    private val fund = Holding(5, broker.id, AssetClass.INDEX_FUND, "World fund", "EUR", null)
    private val gold = Holding(6, broker.id, AssetClass.ETF, "Gold", "EUR", null)

    /** World fund: 1 000 € in, worth 1 200 €. Gold: 500 € in, sold for 600 €. */
    private val positions =
        listOf(
            positionOf(
                fund,
                listOf(buy(1, fund, 100_000)),
                listOf(HoldingValue(1, fund.id, day.plusDays(7), eur(120_000))),
            ),
            positionOf(
                gold,
                listOf(
                    buy(2, gold, 50_000),
                    Trade(3, gold.id, TradeSide.SELL, eur(60_000), day.plusDays(14), null, sellsAll = true),
                ),
                emptyList(),
            ),
        )
    private val account = InvestmentAccount(broker, cash = eur(30_000), positions = positions)

    @Test
    fun `Invested says what was put in, what it is worth, and each broker's total`() {
        var opened: Long? = null
        composeRule.setContent {
            CentsTheme {
                InvestmentsContent(
                    state = InvestmentsUiState(loading = false, overview = InvestmentOverview("EUR", listOf(account))),
                    onOpenAccount = { opened = it },
                )
            }
        }

        composeRule.onNodeWithText("Put in").assertIsDisplayed()
        composeRule.onNodeWithText("+€200.00 · +20.0%").assertIsDisplayed()
        composeRule.onNodeWithText("+€100.00").assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Trade Republic"))
        composeRule.onNodeWithText("Cash €300.00 · Invested €1,200.00").assertIsDisplayed()
        composeRule.onNodeWithTag(ACCOUNT_ROW_TAG).performClick()
        assertEquals(broker.id, opened)
    }

    @Test
    fun `with no broker yet, Invested offers to make one`() {
        var asked = false
        composeRule.setContent {
            CentsTheme {
                InvestmentsContent(
                    state = InvestmentsUiState(loading = false),
                    onCreateBrokerAccount = { asked = true },
                )
            }
        }

        composeRule.onNodeWithText("Create a broker account").performClick()
        assertEquals(true, asked)
    }

    @Test
    fun `an account lists its open assets and its closed operations with their result`() {
        composeRule.setContent {
            CentsTheme {
                InvestmentAccountContent(
                    state = InvestmentAccountUiState(loading = false, account = account),
                )
            }
        }

        composeRule.onNodeWithTag(CASH_TAG).assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("World fund"))
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Together: +€100.00"))
        composeRule.onNodeWithTag(SALE_ROW_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("+€100.00 · +20.0%").assertIsDisplayed()
    }

    @Test
    fun `an asset's history is newest first, and tapping a sale opens it`() {
        var openedTrade: Pair<Long, Long>? = null
        composeRule.setContent {
            CentsTheme {
                AssetContent(
                    state = AssetUiState(loading = false, position = positions[1], accountName = broker.name),
                    actions = AssetActions(onOpenTrade = { holding, trade -> openedTrade = holding to trade }),
                )
            }
        }

        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Sold everything"))
        composeRule.onNodeWithText("Sold everything").performClick()
        assertEquals(gold.id to 3L, openedTrade)
        composeRule.onNodeWithTag(SELL_TAG).assertDoesNotExist()
    }

    private fun buy(
        id: Long,
        holding: Holding,
        minor: Long,
    ) = Trade(id, holding.id, TradeSide.BUY, eur(minor), day, null)

    private fun eur(minor: Long) = Money(minor, "EUR")
}
