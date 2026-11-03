package io.github.anderescaray.cents.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import io.github.anderescaray.cents.ui.util.formatLocale
import java.text.DecimalFormatSymbols

/** How large the cents and the currency are beside the whole units of a headline figure. */
private const val MINOR_SCALE = 0.6f

/** How strongly they are drawn: the same colour, quieter. */
private const val MINOR_ALPHA = 0.55f

/**
 * A formatted amount with its whole units as the headline and the rest — the cents and the
 * currency sign — smaller and quieter beside them: *1.634*`,40 €`. The eye reads the figure that
 * matters first; nothing is dropped, so the amount is still exact (`CLAUDE.md` rule 1).
 *
 * Only for a headline figure. In a list every digit is the same size, so columns stay aligned.
 */
fun headlineMoney(
    text: String,
    fontSize: TextUnit,
    color: Color,
    decimalSeparator: Char = DecimalFormatSymbols.getInstance(formatLocale()).monetaryDecimalSeparator,
): AnnotatedString {
    val whole = wholeUnits(text, decimalSeparator) ?: return AnnotatedString(text)
    val minor =
        SpanStyle(
            fontSize = if (fontSize.isSpecified) fontSize * MINOR_SCALE else TextUnit.Unspecified,
            color = color.copy(alpha = color.alpha * MINOR_ALPHA),
        )
    return buildAnnotatedString {
        withStyle(minor) { append(text.substring(0, whole.first)) }
        append(text.substring(whole.first, whole.last + 1))
        withStyle(minor) { append(text.substring(whole.last + 1)) }
    }
}

/**
 * Where the whole units are in [text]: from a leading sign or the first digit to the last digit
 * before the decimal separator — or to the last digit, for a currency with no cents. Null when
 * there is no digit at all.
 */
internal fun wholeUnits(
    text: String,
    decimalSeparator: Char,
): IntRange? {
    val firstDigit = text.indexOfFirst(Char::isDigit)
    if (firstDigit < 0) return null
    val separator =
        text.indices.lastOrNull { i ->
            text[i] == decimalSeparator && i > firstDigit && text.getOrNull(i + 1)?.isDigit() == true
        }
    val end = (separator ?: text.length) - 1
    val lastDigit = (end downTo firstDigit).first { text[it].isDigit() }
    // A sign at the very start belongs to the number, not to the currency.
    val start = if (firstDigit > 0 && text[0] in SIGNS) 0 else firstDigit
    return start..lastDigit
}

private const val SIGNS = "−-+"
