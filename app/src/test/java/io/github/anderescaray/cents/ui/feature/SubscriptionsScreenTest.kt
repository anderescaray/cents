package io.github.anderescaray.cents.ui.feature

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import io.github.anderescaray.cents.domain.model.Frequency
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.RecurringRule
import io.github.anderescaray.cents.domain.model.RuleMode
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.domain.usecase.FixedChargeIncomeShare
import io.github.anderescaray.cents.domain.usecase.IncomeShareUnavailableReason
import io.github.anderescaray.cents.domain.usecase.SubscriptionTotals
import io.github.anderescaray.cents.ui.feature.recurring.COMMITTED_TOTAL_TAG
import io.github.anderescaray.cents.ui.feature.recurring.INCOME_SHARE_TAG
import io.github.anderescaray.cents.ui.feature.recurring.SubscriptionRow
import io.github.anderescaray.cents.ui.feature.recurring.SubscriptionsScreen
import io.github.anderescaray.cents.ui.feature.recurring.SubscriptionsUiState
import io.github.anderescaray.cents.ui.feature.recurring.formatIncomeShare
import io.github.anderescaray.cents.ui.theme.CentsTheme
import io.github.anderescaray.cents.ui.util.MoneyFormatter
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class SubscriptionsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `both figures are labelled and formatted on the subscriptions screen`() {
        val totals = totals()
        showing(totals)

        compose
            .onNodeWithTag(COMMITTED_TOTAL_TAG)
            .performScrollTo()
            .assertTextContains("Committed this month")
            .assertTextContains(MoneyFormatter.format(requireNotNull(totals.committedThisMonth)))
        compose
            .onNodeWithTag(INCOME_SHARE_TAG)
            .performScrollTo()
            .assertTextContains("Share of income going to fixed charges")
            .assertTextContains(formatIncomeShare(requireNotNull(totals.incomeShare)))
    }

    @Test
    fun `variable amounts are identified as estimates beside both figures`() {
        val totals = totals()
        showing(
            totals.copy(
                committedIncludesEstimates = true,
                incomeShare = requireNotNull(totals.incomeShare).copy(includesEstimates = true),
            ),
        )

        compose.onNodeWithText("Includes estimated amounts.", substring = true).performScrollTo().assertIsDisplayed()
        compose
            .onNodeWithText("including variable-amount estimates.", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `without income rules a dash is explained instead of showing zero percent`() {
        showing(
            totals().copy(
                incomeShare = null,
                incomeShareUnavailableReason = IncomeShareUnavailableReason.NO_INCOME_RULES,
            ),
        )

        compose.onNodeWithTag(INCOME_SHARE_TAG).performScrollTo().assertTextContains("—")
        compose
            .onNodeWithText(
                "Add an active income rule to calculate this share.",
            ).performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `a zero monthly income has its own explanation`() {
        showing(
            totals().copy(
                incomeShare = null,
                incomeShareUnavailableReason = IncomeShareUnavailableReason.ZERO_MONTHLY_INCOME,
            ),
        )

        compose
            .onNodeWithText("Monthly income must be greater than zero to calculate this share.")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `incompatible currencies explain why the share is missing`() {
        showing(
            totals().copy(
                incomeShare = null,
                incomeShareUnavailableReason = IncomeShareUnavailableReason.MIXED_CURRENCIES,
            ),
        )

        compose
            .onNodeWithText("Income and expense rules use different currencies, so this share cannot be calculated.")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "es")
    fun `the new labels and missing income explanation have Spanish translations`() {
        showing(
            totals().copy(
                incomeShare = null,
                incomeShareUnavailableReason = IncomeShareUnavailableReason.NO_INCOME_RULES,
            ),
        )

        compose.onNodeWithTag(COMMITTED_TOTAL_TAG).performScrollTo().assertTextContains("Comprometido este mes")
        compose
            .onNodeWithTag(INCOME_SHARE_TAG)
            .performScrollTo()
            .assertTextContains("Parte de los ingresos destinada a gastos fijos")
        compose
            .onNodeWithText("Añade una regla de ingresos activa para calcular este porcentaje.")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `loading does not briefly claim there are no income rules`() {
        showing(totals(), loading = true)

        compose.onNodeWithTag(INCOME_SHARE_TAG).assertDoesNotExist()
        compose.onNodeWithTag(COMMITTED_TOTAL_TAG).assertDoesNotExist()
    }

    private fun showing(
        totals: SubscriptionTotals,
        loading: Boolean = false,
    ) {
        val state =
            SubscriptionsUiState(
                active = listOf(SubscriptionRow(rule(), LocalDate.of(2026, 9, 30))),
                today = LocalDate.of(2026, 9, 17),
                totals = totals,
                isLoading = loading,
            )
        compose.setContent {
            CentsTheme {
                SubscriptionsScreen(
                    state = state,
                    onBack = {},
                    onNewRule = {},
                    onOpenPending = {},
                    onEditRule = {},
                    onSetActive = { _, _ -> },
                )
            }
        }
    }

    private fun totals() =
        SubscriptionTotals(
            monthlyOutgoing = Money(40_000, "EUR"),
            yearlyOutgoing = Money(480_000, "EUR"),
            committedThisMonth = Money(25_000, "EUR"),
            incomeShare = FixedChargeIncomeShare(Money(40_000, "EUR"), Money(200_000, "EUR")),
            incomeShareUnavailableReason = null,
        )

    private fun rule() =
        RecurringRule(
            id = 1,
            name = "Rent",
            accountId = 1,
            categoryId = null,
            kind = TransactionKind.EXPENSE,
            amount = Money(40_000, "EUR"),
            frequency = Frequency.MONTHLY,
            intervalDays = null,
            startDate = LocalDate.of(2026, 9, 30),
            endDate = null,
            mode = RuleMode.AUTOMATIC,
            active = true,
            lastGenerated = null,
        )
}
