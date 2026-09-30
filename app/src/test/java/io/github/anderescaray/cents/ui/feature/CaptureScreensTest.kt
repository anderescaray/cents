package io.github.anderescaray.cents.ui.feature

import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import io.github.anderescaray.cents.domain.model.CaptureSource
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.ui.feature.capture.CaptureActions
import io.github.anderescaray.cents.ui.feature.capture.CaptureContent
import io.github.anderescaray.cents.ui.feature.capture.CaptureSourceRow
import io.github.anderescaray.cents.ui.feature.capture.CaptureUiState
import io.github.anderescaray.cents.ui.feature.capture.CaptureWordsActions
import io.github.anderescaray.cents.ui.feature.capture.CaptureWordsContent
import io.github.anderescaray.cents.ui.feature.capture.CaptureWordsUiState
import io.github.anderescaray.cents.ui.theme.CentsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** What Settings › Capture says, drawn from a state: nothing here needs a phone's permission. */
@RunWith(RobolectricTestRunner::class)
class CaptureScreensTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `an app whose account is gone, and access not granted, are both said aloud`() {
        composeRule.setContent {
            CentsTheme {
                CaptureContent(
                    state =
                        CaptureUiState(
                            sources = listOf(CaptureSourceRow(CaptureSource("es.bank", "My bank", 9), null)),
                            isLoading = false,
                        ),
                    accessGranted = false,
                    actions = CaptureActions(),
                )
            }
        }

        composeRule.onNodeWithText("Not allowed").assertExists()
        scrollTo("Its account is gone, so nothing is recorded")
        // No account to tie a new app to, so the row says what to do first.
        scrollTo("Create an account first")
    }

    @Test
    fun `tapping an app opens it, and tapping a list opens that kind's words`() {
        val opened = mutableListOf<Any>()
        composeRule.setContent {
            CentsTheme {
                CaptureContent(
                    state =
                        CaptureUiState(
                            sources = listOf(CaptureSourceRow(CaptureSource("es.bank", "My bank", 1), "Bank")),
                            isLoading = false,
                        ),
                    accessGranted = true,
                    actions = CaptureActions(onEditApp = { opened += it }, onOpenWords = { opened += it }),
                )
            }
        }

        scrollTo("Into Bank")
        composeRule.onNodeWithText("My bank").performClick()
        scrollTo("Income words")
        composeRule.onNodeWithText("Income words").performClick()

        assertEquals(listOf("es.bank", TransactionKind.INCOME), opened)
    }

    @Test
    fun `removing a word asks first`() {
        var removed = 0
        composeRule.setContent {
            CentsTheme {
                CaptureWordsContent(
                    state =
                        CaptureWordsUiState(
                            kind = TransactionKind.EXPENSE,
                            words = listOf("pago"),
                            removing = "pago",
                            isLoading = false,
                        ),
                    actions = CaptureWordsActions(onConfirmRemove = { removed++ }),
                )
            }
        }

        composeRule.onNodeWithText("Remove “pago”?").assertExists()
        composeRule.onNodeWithText("Remove").performClick()

        assertEquals(1, removed)
    }

    /**
     * Scrolled to rather than assumed on screen: the screen is a lazy list, and a row below the
     * test's window is not composed until it is scrolled into view.
     */
    private fun scrollTo(text: String) {
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(text))
        composeRule.onNodeWithText(text).assertExists()
    }
}
