package io.github.anderescaray.cents.ui.feature.reports

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.anderescaray.cents.domain.model.Account
import io.github.anderescaray.cents.domain.model.AccountType
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.ui.theme.CentsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Reports › Customize: which account the tab is about, and the order of its charts. */
@RunWith(RobolectricTestRunner::class)
class ReportsCustomizeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `two accounts are offered, along with the way back to all of them`() {
        setContent(accounts = twoAccounts())

        composeRule.onNodeWithText("All accounts").assertExists()
        composeRule.onNodeWithText("Current").assertExists()
        composeRule.onNodeWithText("Savings").assertExists()
    }

    @Test
    fun `no accounts to choose between means no account section`() {
        setContent(accounts = emptyList())

        composeRule.onNodeWithText("All accounts").assertDoesNotExist()
    }

    @Test
    fun `choosing an account is reported, and so is choosing all of them again`() {
        val chosen = mutableListOf<Long?>()
        setContent(accounts = twoAccounts(), accountId = CURRENT_ID, onSelectAccount = { chosen += it })

        composeRule.onNodeWithText("Savings").performClick()
        composeRule.onNodeWithText("All accounts").performClick()

        // Null is a real answer: it is how the tab goes back to every account.
        assertEquals(listOf(SAVINGS_ID, null), chosen)
    }

    @Test
    fun `an archived account is offered, and says that it is archived`() {
        setContent(accounts = twoAccounts() + account(OLD_ID, "Old card", archived = true))

        composeRule.onNodeWithText("Old card (archived)").assertExists()
    }

    @Test
    fun `the order of charts is folded into one row until it is opened`() {
        setContent(accounts = emptyList())

        composeRule.onNodeWithText("Order of charts").assertExists()
        composeRule.onNodeWithText("7 of 7 shown").assertExists()
        composeRule.onNodeWithText("Balance over time").assertDoesNotExist()

        composeRule.onNodeWithText("Order of charts").performClick()

        composeRule.onNodeWithText("Balance over time").assertExists()
    }

    private fun setContent(
        accounts: List<Account>,
        accountId: Long? = null,
        onSelectAccount: (Long?) -> Unit = {},
    ) {
        composeRule.setContent {
            CentsTheme {
                ReportsCustomizeContent(
                    state = ReportLayoutUiState(loaded = true),
                    onBack = {},
                    onMove = { _, _ -> },
                    onSetVisible = { _, _ -> },
                    onReset = {},
                    accounts = accounts,
                    accountId = accountId,
                    onSelectAccount = onSelectAccount,
                )
            }
        }
    }

    private fun twoAccounts() = listOf(account(CURRENT_ID, "Current"), account(SAVINGS_ID, "Savings"))

    private fun account(
        id: Long,
        name: String,
        archived: Boolean = false,
    ) = Account(
        id = id,
        name = name,
        type = AccountType.BANK,
        currency = "EUR",
        openingBalance = Money.zero("EUR"),
        colorArgb = 0,
        archived = archived,
        sortOrder = 0,
    )

    private companion object {
        const val CURRENT_ID = 1L
        const val SAVINGS_ID = 2L
        const val OLD_ID = 3L
    }
}
