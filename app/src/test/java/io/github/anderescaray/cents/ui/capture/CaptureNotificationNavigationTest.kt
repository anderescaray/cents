package io.github.anderescaray.cents.ui.capture

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CaptureNotificationNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `a tapped capture opens its movement once, and a second tap opens the next`() {
        val requested = mutableStateOf<Long?>(7L)
        val opened = mutableListOf<Long>()
        var consumed = 0
        composeRule.setContent {
            OpenCapturedFromNotification(
                transactionId = requested.value,
                onOpenTransaction = { opened += it },
                onOpened = {
                    consumed++
                    requested.value = null
                },
            )
        }

        composeRule.runOnIdle {
            assertEquals(listOf(7L), opened)
            assertEquals(1, consumed)
            requested.value = 8L
        }
        composeRule.runOnIdle {
            assertEquals(listOf(7L, 8L), opened)
            assertEquals(2, consumed)
        }
    }

    @Test
    fun `a normal launch opens no captured movement`() {
        val opened = mutableListOf<Long>()
        var consumed = false
        composeRule.setContent {
            OpenCapturedFromNotification(
                transactionId = null,
                onOpenTransaction = { opened += it },
                onOpened = { consumed = true },
            )
        }

        composeRule.runOnIdle {
            assertTrue(opened.isEmpty())
            assertEquals(false, consumed)
        }
    }
}
