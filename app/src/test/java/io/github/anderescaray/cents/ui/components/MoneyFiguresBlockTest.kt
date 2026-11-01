package io.github.anderescaray.cents.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.MoneyFigures
import io.github.anderescaray.cents.domain.model.NetWorth
import io.github.anderescaray.cents.ui.theme.CentsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The figures the block draws: exactly the ones it is given (`DECISIONS.md` D34). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class MoneyFiguresBlockTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `before any choice, cash and bank and invested show, and no total`() {
        show(MoneyFigures())

        composeRule.onNodeWithTag(NORMAL_FIGURE_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(INVESTED_FIGURE_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(TOTAL_FIGURE_TAG).assertDoesNotExist()
    }

    @Test
    fun `cash and bank with the total, and invested left out`() {
        show(MoneyFigures(normal = true, invested = false, total = true))

        composeRule.onNodeWithTag(NORMAL_FIGURE_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(INVESTED_FIGURE_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(TOTAL_FIGURE_TAG).assertIsDisplayed()
    }

    @Test
    fun `with none chosen, nothing is drawn`() {
        show(MoneyFigures(normal = false, invested = false, total = false))

        composeRule.onNodeWithTag(NORMAL_FIGURE_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(INVESTED_FIGURE_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(TOTAL_FIGURE_TAG).assertDoesNotExist()
    }

    @Test
    fun `money in an archived account is said`() {
        show(MoneyFigures(), WORTH.copy(inArchivedAccounts = Money(2_000, "EUR")))

        composeRule
            .onNodeWithText("sits in archived accounts and is not counted above.", substring = true)
            .assertIsDisplayed()
    }

    private fun show(
        figures: MoneyFigures,
        worth: NetWorth = WORTH,
    ) {
        composeRule.setContent {
            CentsTheme {
                MoneyFiguresBlock(
                    netWorth = worth,
                    figures = figures,
                    totalStyle = CentsTheme.typography.display,
                )
            }
        }
    }

    private companion object {
        val WORTH =
            NetWorth(
                currency = "EUR",
                normal = Money(60_000, "EUR"),
                invested = Money(40_000, "EUR"),
                inArchivedAccounts = Money.zero("EUR"),
            )
    }
}
