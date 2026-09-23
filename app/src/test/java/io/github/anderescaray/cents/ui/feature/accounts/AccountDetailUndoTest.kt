package io.github.anderescaray.cents.ui.feature.accounts

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.Transaction
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.ui.theme.CentsTheme
import io.github.anderescaray.cents.ui.undo.DeletedTransaction
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class AccountDetailUndoTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var undone = 0

    @Test
    fun `a deleted movement is offered back on the account, and Undo asks for it once`() {
        setContent(AccountDetailUiState(isLoading = false, undoableDeletion = DeletedTransaction.Movement(movement(1))))

        composeRule.onNodeWithText("Movement deleted").assertExists()
        composeRule.onNodeWithText("Undo").performClick()

        assertEquals(1, undone)
    }

    @Test
    fun `a deleted transfer is named as one`() {
        val legs = listOf(movement(1, transferId = 7), movement(2, transferId = 7))
        setContent(AccountDetailUiState(isLoading = false, undoableDeletion = DeletedTransaction.Transfer(legs)))

        composeRule.onNodeWithText("Transfer deleted").assertExists()
    }

    @Test
    fun `nothing to undo shows no banner`() {
        setContent(AccountDetailUiState(isLoading = false))

        composeRule.onNodeWithText("Undo").assertDoesNotExist()
    }

    private fun setContent(state: AccountDetailUiState) {
        composeRule.setContent {
            CentsTheme {
                AccountDetailContent(
                    state = state,
                    onBack = {},
                    onEdit = {},
                    onOpenTransaction = {},
                    onOpenTransfer = {},
                    onOpenTrade = { _, _ -> },
                    onUndo = { undone++ },
                )
            }
        }
    }

    private fun movement(
        id: Long,
        transferId: Long? = null,
    ) = Transaction(
        id = id,
        accountId = 1,
        categoryId = null,
        kind = TransactionKind.EXPENSE,
        amount = Money(1_000, "EUR"),
        date = LocalDate.of(2026, 8, 18),
        note = null,
        transferId = transferId,
    )
}
