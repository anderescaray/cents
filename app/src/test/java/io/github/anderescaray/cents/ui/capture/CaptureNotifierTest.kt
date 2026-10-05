package io.github.anderescaray.cents.ui.capture

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import io.github.anderescaray.cents.MainActivity
import io.github.anderescaray.cents.domain.model.CaptureOutcome
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.ui.util.MoneyFormatter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class CaptureNotifierTest {
    private lateinit var context: Application
    private lateinit var manager: NotificationManager
    private lateinit var notifier: CaptureNotifier

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        manager = context.getSystemService(NotificationManager::class.java)
        notifier = CaptureNotifier(context)
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        shadowOf(manager).setNotificationsEnabled(true)
    }

    @After
    fun tearDown() {
        manager.cancelAll()
    }

    @Test
    fun `a recorded expense confirms the exact amount and account and opens its movement`() {
        notifier.notify(recorded(), enabled = true)

        val posted = manager.activeNotifications.single()
        val notification = posted.notification
        assertEquals(CaptureNotifier.CHANNEL_ID, notification.channelId)
        assertEquals("Expense added", notification.title())
        assertTrue(notification.body().contains(MoneyFormatter.format(Money(-2_300, "EUR"), withSign = true)))
        assertTrue(notification.body().contains("Bank"))
        assertTrue(notification.flags and Notification.FLAG_AUTO_CANCEL != 0)
        assertTrue(notification.contentIntent.isImmutable)
        val intent = shadowOf(notification.contentIntent).savedIntent
        assertEquals(MainActivity::class.java.name, intent.component?.className)
        assertEquals(1L, intent.getLongExtra(CaptureNotifier.EXTRA_OPEN_CAPTURED, 0L))
    }

    @Test
    fun `income is described as income and has a positive amount`() {
        notifier.notify(recorded().copy(kind = TransactionKind.INCOME), enabled = true)

        val notification = manager.activeNotifications.single().notification
        assertEquals("Income added", notification.title())
        assertTrue(notification.body().contains(MoneyFormatter.format(Money(2_300, "EUR"), withSign = true)))
    }

    @Test
    fun `the notification is localized in Spanish`() {
        val configuration = android.content.res.Configuration(context.resources.configuration)
        configuration.setLocale(java.util.Locale.forLanguageTag("es"))
        val spanish = context.createConfigurationContext(configuration)

        CaptureNotifier(spanish).notify(recorded(), enabled = true)

        assertEquals(
            "Gasto añadido",
            manager.activeNotifications
                .single()
                .notification
                .title(),
        )
    }

    @Test
    fun `disabled confirmations duplicates and ignored notifications stay silent`() {
        notifier.notify(recorded(), enabled = false)
        notifier.notify(CaptureOutcome.Duplicate, enabled = true)
        CaptureOutcome.IgnoreReason.entries.forEach {
            notifier.notify(CaptureOutcome.Ignored(it), enabled = true)
        }

        assertTrue(manager.activeNotifications.isEmpty())
    }

    @Test
    fun `Android notification permission is required`() {
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        notifier.notify(recorded(), enabled = true)

        assertFalse(CaptureNotifier.allowed(context))
        assertTrue(manager.activeNotifications.isEmpty())
    }

    @Test
    fun `the app wide notification switch is respected`() {
        shadowOf(manager).setNotificationsEnabled(false)

        notifier.notify(recorded(), enabled = true)

        assertFalse(CaptureNotifier.allowed(context))
        assertTrue(manager.activeNotifications.isEmpty())
    }

    @Test
    fun `a blocked confirmation channel stays blocked`() {
        manager.createNotificationChannel(
            NotificationChannel(CaptureNotifier.CHANNEL_ID, "Captured movements", NotificationManager.IMPORTANCE_NONE),
        )

        notifier.notify(recorded(), enabled = true)

        assertFalse(CaptureNotifier.allowed(context))
        assertTrue(manager.activeNotifications.isEmpty())
    }

    @Test
    fun `different movements have independent notifications and immutable destinations`() {
        notifier.notify(recorded(), enabled = true)
        notifier.notify(recorded().copy(transactionId = 2), enabled = true)

        val ids =
            manager.activeNotifications.map {
                shadowOf(
                    it.notification.contentIntent,
                ).savedIntent.getLongExtra(CaptureNotifier.EXTRA_OPEN_CAPTURED, 0L)
            }
        assertEquals(setOf(1L, 2L), ids.toSet())
        assertEquals(2, manager.activeNotifications.size)
    }

    @Test
    fun `posting the same confirmation again replaces it without alerting again`() {
        notifier.notify(recorded(), enabled = true)
        notifier.notify(recorded(), enabled = true)

        val notification = manager.activeNotifications.single().notification
        assertTrue(notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
    }

    @Test
    fun `lock screen public content contains no amount or account`() {
        notifier.notify(recorded(), enabled = true)

        val notification = manager.activeNotifications.single().notification
        assertEquals(Notification.VISIBILITY_PRIVATE, notification.visibility)
        val publicVersion = requireNotNull(notification.publicVersion)
        assertEquals("Cents", publicVersion.title())
        assertEquals("A movement was added automatically.", publicVersion.body())
        assertFalse(publicVersion.body().contains("Bank"))
    }

    @Test
    @Config(sdk = [32])
    fun `older Android versions do not require the runtime notification permission`() {
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        notifier.notify(recorded(), enabled = true)

        assertEquals(1, manager.activeNotifications.size)
    }

    private fun recorded() =
        CaptureOutcome.Recorded(
            transactionId = 1,
            amount = Money(2_300, "EUR"),
            kind = TransactionKind.EXPENSE,
            accountName = "Bank",
        )

    private fun Notification.title(): String = extras.getCharSequence(Notification.EXTRA_TITLE).toString()

    private fun Notification.body(): String = extras.getCharSequence(Notification.EXTRA_TEXT).toString()
}
