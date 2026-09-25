package io.github.anderescaray.cents.ui.feature

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.anderescaray.cents.domain.model.Group
import io.github.anderescaray.cents.domain.model.GroupSummary
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.ui.feature.groups.GROUP_MOVEMENTS_TAG
import io.github.anderescaray.cents.ui.feature.groups.GroupDetailContent
import io.github.anderescaray.cents.ui.feature.groups.GroupDetailUiState
import io.github.anderescaray.cents.ui.feature.groups.GroupsContent
import io.github.anderescaray.cents.ui.feature.groups.GroupsUiState
import io.github.anderescaray.cents.ui.theme.CentsTheme
import io.github.anderescaray.cents.ui.util.MoneyFormatter
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/** What the two group screens say, drawn from a state rather than from a database. */
@RunWith(RobolectricTestRunner::class)
class GroupScreensTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `the list says what each group cost or earned, in words`() {
        composeRule.setContent {
            CentsTheme {
                GroupsContent(
                    state =
                        GroupsUiState(
                            groups =
                                listOf(
                                    summary("Lisbon trip", income = 9_000, expenses = 24_000),
                                    summary("Photos", 120_000, 5_000),
                                ),
                            isLoading = false,
                        ),
                    onBack = {},
                    onNewGroup = {},
                    onOpenGroup = {},
                )
            }
        }

        composeRule.onNodeWithText("Cost you ${MoneyFormatter.format(Money(15_000, "EUR"))}").assertExists()
        composeRule.onNodeWithText("Earned you ${MoneyFormatter.format(Money(115_000, "EUR"))}").assertExists()
    }

    @Test
    fun `an empty list says what a group is for`() {
        composeRule.setContent {
            CentsTheme {
                GroupsContent(state = GroupsUiState(isLoading = false), onBack = {}, onNewGroup = {}, onOpenGroup = {})
            }
        }

        composeRule.onNodeWithText("No groups yet").assertExists()
    }

    @Test
    fun `a group's screen leads with the answer and opens its movements`() {
        var opened = 0
        composeRule.setContent {
            CentsTheme {
                GroupDetailContent(
                    state =
                        GroupDetailUiState(
                            summary = summary("Lisbon trip", income = 0, expenses = 24_000),
                            isLoading = false,
                        ),
                    onBack = {},
                    onEdit = {},
                    onShowAll = { opened++ },
                    onShowSlice = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText("Cost you ${MoneyFormatter.format(Money(24_000, "EUR"))}").assertExists()
        composeRule.onNodeWithTag(GROUP_MOVEMENTS_TAG).performClick()
        assertEquals(1, opened)
    }

    private fun summary(
        name: String,
        income: Long,
        expenses: Long,
    ) = GroupSummary(
        group = Group(id = name.hashCode().toLong(), name = name),
        income = Money(income, "EUR"),
        expenses = Money(expenses, "EUR"),
        movementCount = 3,
        firstDate = LocalDate.of(2026, 10, 2),
        lastDate = LocalDate.of(2026, 10, 6),
    )
}
