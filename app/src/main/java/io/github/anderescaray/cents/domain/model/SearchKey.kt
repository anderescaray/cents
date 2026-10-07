package io.github.anderescaray.cents.domain.model

import java.text.Normalizer
import java.util.Locale

/**
 * Text as search compares it: accents stripped, case folded, surrounding space dropped. One rule
 * for every search box in the app — the movement list and the pickers — so *cafe* finds *Café*
 * in both or in neither.
 *
 * The owner writes in Spanish, where "cafe" and "Café" are the same word typed two ways, and where
 * "Ñ" is a capital SQLite's `LIKE` does not know is one. Decomposing (NFD) and dropping the marks
 * is what makes both sides meet; ñ decomposes to n plus a tilde, so "nora" finds "Ñora" too — the
 * cost of one rule for every accent, and a search that finds too much is the lesser failure.
 */
fun String.searchKey(): String =
    Normalizer
        .normalize(trim(), Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")
        .lowercase(Locale.ROOT)

private val COMBINING_MARKS = Regex("\\p{Mn}+")
