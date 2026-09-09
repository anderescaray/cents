package io.github.anderescaray.cents.ui.feature

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.anderescaray.cents.domain.model.AssetClass
import io.github.anderescaray.cents.domain.model.Holding
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.PortfolioTotal
import io.github.anderescaray.cents.domain.model.Position
import io.github.anderescaray.cents.domain.model.PriceQuote
import io.github.anderescaray.cents.domain.model.Quantity
import io.github.anderescaray.cents.domain.model.QuoteSource
import io.github.anderescaray.cents.ui.feature.investments.ADD_HOLDING_TAG
import io.github.anderescaray.cents.ui.feature.investments.PortfolioGroup
import io.github.anderescaray.cents.ui.feature.investments.PortfolioScreen
import io.github.anderescaray.cents.ui.feature.investments.PortfolioUiState
import io.github.anderescaray.cents.ui.theme.CentsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

/**
 * What the portfolio actually puts on screen — including the two things it is most tempting to
 * get wrong: a holding with no price must say so rather than read as worth nothing, and a
 * heading must only appear when something is held under it.
 */
@RunWith(RobolectricTestRunner::class)
class PortfolioScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a held position shows what it is worth, under the heading for its kind`() {
        showing(
            PortfolioUiState(
                loading = false,
                hasAccount = true,
                groups = listOf(PortfolioGroup(AssetClass.STOCK, listOf(position(priced = true)))),
                totals = listOf(total()),
            ),
        )

        compose.onNodeWithText("Stocks", ignoreCase = true).assertIsDisplayed()
        compose.onNodeWithText("NVIDIA").assertIsDisplayed()
        compose.onNodeWithText("Crypto", ignoreCase = true).assertDoesNotExist()
    }

    @Test
    fun `a holding with no price says so rather than showing nothing`() {
        showing(
            PortfolioUiState(
                loading = false,
                hasAccount = true,
                groups = listOf(PortfolioGroup(AssetClass.STOCK, listOf(position(priced = false)))),
            ),
        )

        compose.onNodeWithText("No price yet").assertIsDisplayed()
    }

    @Test
    fun `tapping a holding opens that holding`() {
        var opened: Long? = null
        showing(
            state =
                PortfolioUiState(
                    loading = false,
                    hasAccount = true,
                    groups = listOf(PortfolioGroup(AssetClass.STOCK, listOf(position(priced = true)))),
                ),
            onOpenHolding = { opened = it },
        )

        compose.onNodeWithText("NVIDIA").performClick()

        assertEquals(7L, opened)
    }

    @Test
    fun `a totals card with nothing priced says so instead of showing zero`() {
        showing(
            PortfolioUiState(
                loading = false,
                hasAccount = true,
                groups = listOf(PortfolioGroup(AssetClass.STOCK, listOf(position(priced = false)))),
                totals =
                    listOf(
                        PortfolioTotal(
                            currency = "EUR",
                            marketValue = null,
                            costBasis = Money(20_000, "EUR"),
                            pricedCostBasis = Money(0, "EUR"),
                            unpricedCount = 1,
                        ),
                    ),
            ),
        )

        compose.onNodeWithText("Value: 0,00 €").assertDoesNotExist()
        compose.onNodeWithText("One of these has no price yet and is left out of the total.").assertIsDisplayed()
    }

    @Test
    fun `with no account there is nothing to add a holding to, so nothing offers it`() {
        showing(PortfolioUiState(loading = false, hasAccount = false))

        compose
            .onNodeWithText(
                "An investment needs an account to live in. Create one in Accounts first, then come back.",
            ).assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithTag(ADD_HOLDING_TAG).fetchSemanticsNodes().size)
    }

    private fun showing(
        state: PortfolioUiState,
        onOpenHolding: (Long) -> Unit = {},
    ) {
        compose.setContent {
            CentsTheme {
                PortfolioScreen(state = state, onAddHolding = {}, onOpenHolding = onOpenHolding)
            }
        }
    }

    private fun position(priced: Boolean) =
        Position(
            holding =
                Holding(
                    id = 7,
                    accountId = 1,
                    assetClass = AssetClass.STOCK,
                    symbol = "NVDA",
                    name = "NVIDIA",
                    currency = "EUR",
                    note = null,
                ),
            quantity = Quantity.ofUnits(2),
            costBasis = Money(20_000, "EUR"),
            realisedPnl = Money(0, "EUR"),
            quote =
                if (priced) {
                    PriceQuote(
                        symbol = "NVDA",
                        price = Money(12_500, "EUR"),
                        asOf = Instant.parse("2026-09-08T00:00:00Z"),
                        source = QuoteSource.MANUAL,
                    )
                } else {
                    null
                },
        )

    private fun total() =
        PortfolioTotal(
            currency = "EUR",
            marketValue = Money(25_000, "EUR"),
            costBasis = Money(20_000, "EUR"),
            pricedCostBasis = Money(20_000, "EUR"),
            unpricedCount = 0,
        )
}
