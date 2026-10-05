package io.github.anderescaray.cents.ui.feature

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import io.github.anderescaray.cents.domain.model.CaptureSource
import io.github.anderescaray.cents.domain.model.CaptureWordList
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.domain.usecase.CaptureParser
import io.github.anderescaray.cents.ui.feature.capture.CAPTURE_NOTIFY_SWITCH_TAG
import io.github.anderescaray.cents.ui.feature.capture.CaptureActions
import io.github.anderescaray.cents.ui.feature.capture.CaptureContent
import io.github.anderescaray.cents.ui.feature.capture.CaptureSourceRow
import io.github.anderescaray.cents.ui.feature.capture.CaptureTryContent
import io.github.anderescaray.cents.ui.feature.capture.CaptureTryUiState
import io.github.anderescaray.cents.ui.feature.capture.CaptureUiState
import io.github.anderescaray.cents.ui.feature.capture.CaptureWordsActions
import io.github.anderescaray.cents.ui.feature.capture.CaptureWordsContent
import io.github.anderescaray.cents.ui.feature.capture.CaptureWordsUiState
import io.github.anderescaray.cents.ui.feature.capture.IGNORE_WORDS_ROW_TAG
import io.github.anderescaray.cents.ui.feature.capture.TRY_RESULT_TAG
import io.github.anderescaray.cents.ui.feature.capture.TRY_ROW_TAG
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
    fun `each app has its own card, and its lists open that app's words`() {
        val opened = mutableListOf<Any?>()
        composeRule.setContent {
            CentsTheme {
                CaptureContent(
                    state =
                        CaptureUiState(
                            sources =
                                listOf(
                                    CaptureSourceRow(CaptureSource("es.bank", "My bank", 1), "Bank", 10, 8),
                                    CaptureSourceRow(CaptureSource("com.wallet", "Wallet", 1), "Bank", 3, 2),
                                ),
                            isLoading = false,
                        ),
                    accessGranted = true,
                    actions =
                        CaptureActions(
                            onEditApp = { opened += it },
                            onOpenWords = { kind, app -> opened.add(listOf(kind, app)) },
                        ),
                )
            }
        }

        scrollTo("MY BANK")
        composeRule.onAllNodesWithText("Account")[0].performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onAllNodesWithText("Income words")[0].performSemanticsAction(SemanticsActions.OnClick)
        scrollTo("WALLET")
        composeRule.onAllNodesWithText("Expense words")[1].performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(
            listOf("es.bank", listOf(TransactionKind.INCOME, "es.bank"), listOf(TransactionKind.EXPENSE, "com.wallet")),
            opened,
        )
    }

    @Test
    fun `capture settings lead to the ignore words and to trying a notification`() {
        var ignore = 0
        var tried = 0
        composeRule.setContent {
            CentsTheme {
                CaptureContent(
                    state = CaptureUiState(isLoading = false, ignoreWordCount = 4),
                    accessGranted = true,
                    actions = CaptureActions(onOpenIgnoreWords = { ignore++ }, onTry = { tried++ }),
                )
            }
        }

        composeRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag(TRY_ROW_TAG))
        composeRule.onNodeWithTag(IGNORE_WORDS_ROW_TAG).performClick()
        composeRule.onNodeWithTag(TRY_ROW_TAG).performClick()

        assertEquals(1, ignore)
        assertEquals(1, tried)
    }

    @Test
    fun `a tried notification with a balance beside the payment says what to do`() {
        val pago = CaptureParser.WordMatch(TransactionKind.EXPENSE, "pago")
        composeRule.setContent {
            CentsTheme {
                CaptureTryContent(
                    state =
                        CaptureTryUiState(
                            text = "Pago de 23,40 € y 1,50 €",
                            reading =
                                CaptureParser.Reading.SeveralAmounts(
                                    pago,
                                    listOf(CaptureParser.Amount(2_340, "EUR"), CaptureParser.Amount(150, "EUR")),
                                ),
                            isLoading = false,
                        ),
                )
            }
        }

        composeRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag(TRY_RESULT_TAG))
        composeRule.onNodeWithText("Ignored: more than one amount").assertExists()
        composeRule
            .onNodeWithText("Which one is the payment is not guessed", substring = true)
            .assertExists()
    }

    @Test
    fun `a tried notification that would be recorded names the word and the account`() {
        composeRule.setContent {
            CentsTheme {
                CaptureTryContent(
                    state =
                        CaptureTryUiState(
                            text = "Pago de 23,40 €",
                            accountName = "Bank",
                            accountCurrency = "EUR",
                            reading =
                                CaptureParser.Reading.Movement(
                                    CaptureParser.WordMatch(TransactionKind.EXPENSE, "pago"),
                                    CaptureParser.Amount(2_340, "EUR"),
                                ),
                            isLoading = false,
                        ),
                )
            }
        }

        composeRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag(TRY_RESULT_TAG))
        composeRule.onNodeWithText("Would record an expense").assertExists()
        composeRule.onNodeWithText("pago").assertExists()
        composeRule.onNodeWithText("Bank").assertExists()
    }

    @Test
    fun `removing a word asks first`() {
        var removed = 0
        composeRule.setContent {
            CentsTheme {
                CaptureWordsContent(
                    state =
                        CaptureWordsUiState(
                            list = CaptureWordList.EXPENSE,
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

    @Test
    fun `confirmation notifications can be switched on and off from capture settings`() {
        val state = mutableStateOf(CaptureUiState(isLoading = false))
        val changes = mutableListOf<Boolean>()
        composeRule.setContent {
            CentsTheme {
                CaptureContent(
                    state = state.value,
                    accessGranted = true,
                    confirmationsAllowed = true,
                    actions =
                        CaptureActions(
                            onNotifyOnCaptureChanged = {
                                changes += it
                                state.value = state.value.copy(notifyOnCapture = it)
                            },
                        ),
                )
            }
        }

        scrollTo("Notify after capture")
        composeRule.onNodeWithTag(CAPTURE_NOTIFY_SWITCH_TAG).assertIsOff().performClick()
        composeRule.onNodeWithTag(CAPTURE_NOTIFY_SWITCH_TAG).assertIsOn().performClick()
        composeRule.onNodeWithTag(CAPTURE_NOTIFY_SWITCH_TAG).assertIsOff()
        assertEquals(listOf(true, false), changes)
        assertEquals(false, state.value.enabled)
    }

    @Test
    fun `blocked confirmations explain why and offer Android notification settings`() {
        var opened = 0
        composeRule.setContent {
            CentsTheme {
                CaptureContent(
                    state = CaptureUiState(notifyOnCapture = true, isLoading = false),
                    accessGranted = true,
                    confirmationsAllowed = false,
                    actions = CaptureActions(onOpenNotificationSettings = { opened++ }),
                )
            }
        }

        scrollTo("Confirmation notifications are blocked")
        composeRule.onNodeWithText("Confirmation notifications are blocked").performClick()
        assertEquals(1, opened)
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
