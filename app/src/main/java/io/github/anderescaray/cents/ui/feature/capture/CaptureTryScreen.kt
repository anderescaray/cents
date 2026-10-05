package io.github.anderescaray.cents.ui.feature.capture

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.domain.usecase.CaptureParser
import io.github.anderescaray.cents.ui.components.CentsBackButton
import io.github.anderescaray.cents.ui.components.CentsLargeTitleScaffold
import io.github.anderescaray.cents.ui.components.CentsListRow
import io.github.anderescaray.cents.ui.components.CentsListSection
import io.github.anderescaray.cents.ui.components.CentsTextField
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme
import io.github.anderescaray.cents.ui.util.MoneyFormatter

/**
 * *Try a notification*: paste what a bank sent, title and text, and see what capture would make of
 * it before a real one arrives. Nothing is saved.
 */
@Composable
fun CaptureTryScreen(
    onBack: () -> Unit,
    viewModel: CaptureTryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val input by viewModel.input.collectAsStateWithLifecycle()
    CaptureTryContent(
        state = state,
        input = input,
        onBack = onBack,
        onTitleChange = viewModel::setTitle,
        onTextChange = viewModel::setText,
        onChoose = viewModel::choose,
    )
}

/** The screen without its view model, so a test can draw it from a state. */
@Composable
internal fun CaptureTryContent(
    state: CaptureTryUiState,
    input: CaptureTryInput = CaptureTryInput(),
    onBack: () -> Unit = {},
    onTitleChange: (String) -> Unit = {},
    onTextChange: (String) -> Unit = {},
    onChoose: (String) -> Unit = {},
) {
    CentsLargeTitleScaffold(
        title = stringResource(R.string.capture_try),
        bottomPadding = CentsDimens.sectionSpacing,
        leading = { CentsBackButton(label = stringResource(R.string.capture_title), onClick = onBack) },
    ) {
        if (state.isLoading) return@CentsLargeTitleScaffold
        appChoice(state, onChoose)
        // Title and text apart, as the notification has them: a title that repeats the amount is
        // read as the same payment only when it is known to be the title.
        item(key = "text") {
            CentsListSection(footer = stringResource(R.string.capture_try_text_footer)) {
                CentsTextField(
                    value = input.title,
                    onValueChange = onTitleChange,
                    placeholder = stringResource(R.string.capture_try_title_placeholder),
                    showSeparator = true,
                    modifier = Modifier.testTag(TRY_TITLE_TAG),
                )
                CentsTextField(
                    value = input.text,
                    onValueChange = onTextChange,
                    placeholder = stringResource(R.string.capture_try_placeholder),
                    multiline = true,
                    modifier = Modifier.testTag(TRY_FIELD_TAG),
                )
            }
        }
        state.reading?.let { reading -> result(state, reading) }
    }
}

/** Whose words read the text: each app has its own, so the answer depends on which. */
private fun LazyListScope.appChoice(
    state: CaptureTryUiState,
    onChoose: (String) -> Unit,
) {
    item(key = "apps") {
        if (state.apps.isEmpty()) {
            CentsListSection(footer = stringResource(R.string.capture_try_no_apps)) {}
        } else {
            CentsListSection(header = stringResource(R.string.capture_try_read_as)) {
                state.apps.forEachIndexed { index, app ->
                    CentsListRow(
                        title = app.label,
                        selected = app.packageName == state.app?.packageName,
                        showSeparator = index != state.apps.lastIndex,
                        onClick = { onChoose(app.packageName) },
                    )
                }
            }
        }
    }
}

/** What capture would do with it, and when nothing, why and what would change that. */
private fun LazyListScope.result(
    state: CaptureTryUiState,
    reading: CaptureParser.Reading,
) {
    item(key = "result") {
        val blocker = state.blocker
        val heading =
            when {
                state.records && reading is CaptureParser.Reading.Movement -> {
                    stringResource(
                        if (reading.word.kind == TransactionKind.EXPENSE) {
                            R.string.capture_try_records_expense
                        } else {
                            R.string.capture_try_records_income
                        },
                    )
                }

                reading is CaptureParser.Reading.Movement && state.otherCurrency -> {
                    stringResource(R.string.capture_try_ignored_currency)
                }

                // The text reads fine, but capture would not get as far as reading it.
                reading is CaptureParser.Reading.Movement && blocker != null -> {
                    stringResource(blocker.headingRes())
                }

                reading is CaptureParser.Reading.NoWord -> {
                    stringResource(R.string.capture_try_ignored_no_word)
                }

                reading is CaptureParser.Reading.NoAmount -> {
                    stringResource(R.string.capture_try_ignored_no_amount)
                }

                else -> {
                    stringResource(R.string.capture_try_ignored_several)
                }
            }
        CentsListSection(
            header = stringResource(R.string.capture_try_result),
            footer = footerFor(state, reading),
        ) {
            ResultRows(state, reading, heading)
        }
    }
}

/** The word a reading found, if it found one. */
private val CaptureParser.Reading.word: String?
    get() =
        when (this) {
            is CaptureParser.Reading.Movement -> word.word
            is CaptureParser.Reading.NoAmount -> word.word
            is CaptureParser.Reading.SeveralAmounts -> word.word
            CaptureParser.Reading.NoWord -> null
        }

/** The amounts a reading found: the one, the several, or none. */
private val CaptureParser.Reading.amounts: List<CaptureParser.Amount>
    get() =
        when (this) {
            is CaptureParser.Reading.Movement -> listOf(amount)
            is CaptureParser.Reading.SeveralAmounts -> amounts
            else -> emptyList()
        }

@Composable
private fun ResultRows(
    state: CaptureTryUiState,
    reading: CaptureParser.Reading,
    heading: String,
) {
    val word = reading.word
    val amounts = reading.amounts
    val records = state.records
    // Signed and coloured only when it would be recorded; otherwise it is just what was found.
    val expense = records && (reading as CaptureParser.Reading.Movement).word.kind == TransactionKind.EXPENSE
    val amountColor =
        when {
            !records -> CentsTheme.colors.secondaryLabel
            expense -> CentsTheme.colors.destructiveText
            else -> CentsTheme.colors.positiveText
        }
    CentsListRow(
        title = heading,
        showSeparator = word != null || amounts.isNotEmpty(),
        modifier = Modifier.testTag(TRY_RESULT_TAG),
    )
    if (word != null) {
        CentsListRow(
            title = stringResource(R.string.capture_try_word),
            value = word,
            showSeparator = amounts.isNotEmpty(),
        )
    }
    val accountRow = records && state.accountName != null
    amounts.forEachIndexed { index, amount ->
        val money = Money(amount.minorUnits, amount.currency)
        CentsListRow(
            title = stringResource(R.string.capture_try_amount),
            value = MoneyFormatter.format(if (expense) -money else money),
            valueColor = amountColor,
            showSeparator = index != amounts.lastIndex || accountRow,
        )
    }
    if (accountRow) {
        CentsListRow(
            title = stringResource(R.string.capture_try_account),
            value = state.accountName,
            showSeparator = false,
        )
    }
}

@Composable
private fun footerFor(
    state: CaptureTryUiState,
    reading: CaptureParser.Reading,
): String =
    when {
        reading is CaptureParser.Reading.Movement && state.otherCurrency -> {
            stringResource(
                R.string.capture_try_currency_footer,
                reading.amount.currency,
                state.accountCurrency.orEmpty(),
            )
        }

        reading is CaptureParser.Reading.Movement && state.blocker != null -> {
            stringResource(requireNotNull(state.blocker).footerRes())
        }

        reading is CaptureParser.Reading.Movement -> {
            stringResource(R.string.capture_try_records_footer)
        }

        reading is CaptureParser.Reading.NoWord -> {
            stringResource(R.string.capture_try_no_word_footer)
        }

        reading is CaptureParser.Reading.NoAmount -> {
            stringResource(R.string.capture_try_no_amount_footer)
        }

        else -> {
            stringResource(R.string.capture_try_several_footer)
        }
    }

private fun CaptureTryBlocker.headingRes(): Int =
    when (this) {
        CaptureTryBlocker.OFF -> R.string.capture_try_ignored_off
        CaptureTryBlocker.NO_APP -> R.string.capture_try_ignored_no_app
        CaptureTryBlocker.NO_ACCOUNT -> R.string.capture_try_ignored_no_account
    }

private fun CaptureTryBlocker.footerRes(): Int =
    when (this) {
        CaptureTryBlocker.OFF -> R.string.capture_try_off_footer
        CaptureTryBlocker.NO_APP -> R.string.capture_try_no_app_footer
        CaptureTryBlocker.NO_ACCOUNT -> R.string.capture_try_no_account_footer
    }

internal const val TRY_TITLE_TAG = "captureTryTitle"
internal const val TRY_FIELD_TAG = "captureTryField"
internal const val TRY_RESULT_TAG = "captureTryResult"
