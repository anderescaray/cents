package io.github.anderescaray.cents.ui.feature.settings

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import io.github.anderescaray.cents.ui.language.AppLanguage
import io.github.anderescaray.cents.ui.theme.CentsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Settings › Language: last in Settings, one row with the current answer, chosen from a sheet. */
@RunWith(RobolectricTestRunner::class)
class LanguageSettingTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `the language is the very last thing in Settings`() {
        composeRule.setContent { CentsTheme { SettingsContent(onOpenCategories = {}) } }

        val list = composeRule.onNode(hasScrollAction())
        val items = list.fetchSemanticsNode().config[SemanticsProperties.CollectionInfo].rowCount
        list.performScrollToIndex(items - 1)

        composeRule.onNodeWithTag("language").assertIsDisplayed()
    }

    @Test
    fun `the row says which language the app is in`() {
        composeRule.setContent {
            CentsTheme { SettingsContent(onOpenCategories = {}, language = AppLanguage.SPANISH) }
        }

        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Language"))
        composeRule.onNodeWithText("Español").assertIsDisplayed()
    }

    @Test
    fun `each language is offered by its own name, and the phone's too`() {
        val chosen = mutableListOf<AppLanguage>()
        composeRule.setContent {
            CentsTheme { SettingsContent(onOpenCategories = {}, onChooseLanguage = { chosen += it }) }
        }

        openSheet()
        composeRule.onNodeWithText("English").performClick()
        openSheet()
        composeRule.onNodeWithText("Español").performClick()

        assertEquals(listOf(AppLanguage.ENGLISH, AppLanguage.SPANISH), chosen)
    }

    @Test
    fun `cancelling chooses nothing`() {
        val chosen = mutableListOf<AppLanguage>()
        composeRule.setContent {
            CentsTheme { SettingsContent(onOpenCategories = {}, onChooseLanguage = { chosen += it }) }
        }

        openSheet()
        composeRule.onNodeWithText("Cancel").performClick()

        composeRule.onNodeWithText("Choose the language Cents is shown in.").assertDoesNotExist()
        assertEquals(emptyList<AppLanguage>(), chosen)
    }

    private fun openSheet() {
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Language"))
        composeRule.onNodeWithTag("language").performClick()
        composeRule.onNodeWithText("Choose the language Cents is shown in.").assertIsDisplayed()
    }
}
