package io.github.anderescaray.cents.data.local.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import io.github.anderescaray.cents.domain.model.CaptureSettings
import io.github.anderescaray.cents.domain.model.CaptureSource
import io.github.anderescaray.cents.domain.model.TransactionKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Capture's settings as the owner left them (`DECISIONS.md` D29), against a real DataStore on a
 * real file: the defaults, the difference between a list never edited and one emptied, and the
 * apps with their accounts.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DataStoreCaptureSettingsRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `a new install has capture off, its notifications on, and the default words`() =
        runTest {
            val settings = DataStoreCaptureSettingsRepository(store()).observe().first()

            assertFalse(settings.enabled)
            // The owner asked to be told about every automatic movement (2026-10-05).
            assertTrue(settings.notifyOnCapture)
            assertTrue(settings.sources.isEmpty())
            assertEquals(CaptureSettings.DEFAULT_EXPENSE_WORDS, settings.expenseWords)
            assertEquals(CaptureSettings.DEFAULT_INCOME_WORDS, settings.incomeWords)
        }

    @Test
    fun `an app is tied to one account, and choosing it again moves it`() =
        runTest {
            val repository = DataStoreCaptureSettingsRepository(store())

            repository.putSource(CaptureSource("es.example.bank", "My | bank", accountId = 1))
            repository.putSource(CaptureSource("es.example.bank", "My | bank", accountId = 2))
            repository.putSource(CaptureSource("com.wallet", "Wallet", accountId = 3))
            repository.removeSource("com.wallet")

            // The label holds the separator on purpose: it still reads back whole.
            assertEquals(
                listOf(CaptureSource("es.example.bank", "My | bank", accountId = 2)),
                repository.observe().first().sources,
            )
        }

    @Test
    fun `edited words replace the defaults, and an emptied list stays empty`() =
        runTest {
            val repository = DataStoreCaptureSettingsRepository(store())

            repository.setWords(TransactionKind.INCOME, listOf(" bizum de ", "ingreso", ""))
            repository.setWords(TransactionKind.EXPENSE, emptyList())

            val settings = repository.observe().first()
            assertEquals(listOf("bizum de", "ingreso"), settings.incomeWords)
            assertEquals(emptyList<String>(), settings.expenseWords)
        }

    @Test
    fun `putting the words back restores the defaults for that kind only`() =
        runTest {
            val repository = DataStoreCaptureSettingsRepository(store())
            repository.setWords(TransactionKind.INCOME, listOf("bizum de"))
            repository.setWords(TransactionKind.EXPENSE, listOf("cobro"))

            repository.resetWords(TransactionKind.EXPENSE)

            val settings = repository.observe().first()
            assertEquals(CaptureSettings.DEFAULT_EXPENSE_WORDS, settings.expenseWords)
            assertEquals(listOf("bizum de"), settings.incomeWords)
        }

    @Test
    fun `an app's own words are kept apart from another's, and go when the app goes`() =
        runTest {
            val repository = DataStoreCaptureSettingsRepository(store())
            repository.putSource(CaptureSource("es.bank", "My bank", 1))
            repository.putSource(CaptureSource("com.wallet", "Wallet", 1))

            repository.setWords(TransactionKind.EXPENSE, listOf("nuevo gasto"), "es.bank")

            val edited = repository.observe().first()
            assertEquals(listOf("nuevo gasto"), edited.wordsFor("es.bank", TransactionKind.EXPENSE))
            assertEquals(CaptureSettings.DEFAULT_EXPENSE_WORDS, edited.wordsFor("com.wallet", TransactionKind.EXPENSE))
            assertEquals(CaptureSettings.DEFAULT_INCOME_WORDS, edited.wordsFor("es.bank", TransactionKind.INCOME))

            repository.removeSource("es.bank")
            repository.putSource(CaptureSource("es.bank", "My bank", 1))

            assertEquals(
                CaptureSettings.DEFAULT_EXPENSE_WORDS,
                repository.observe().first().wordsFor("es.bank", TransactionKind.EXPENSE),
            )
        }

    @Test
    fun `putting an app's words back gives it the starting words again`() =
        runTest {
            val repository = DataStoreCaptureSettingsRepository(store())
            repository.setWords(TransactionKind.INCOME, listOf("bizum recibido"), "es.bank")

            repository.resetWords(TransactionKind.INCOME, "es.bank")

            val settings = repository.observe().first()
            assertEquals(CaptureSettings.DEFAULT_INCOME_WORDS, settings.wordsFor("es.bank", TransactionKind.INCOME))
        }

    @Test
    fun `the switch is remembered`() =
        runTest {
            val repository = DataStoreCaptureSettingsRepository(store())

            repository.setEnabled(true)

            assertTrue(repository.observe().first().enabled)
        }

    @Test
    fun `confirmation notifications are remembered independently of capture`() =
        runTest {
            val store = store()
            val repository = DataStoreCaptureSettingsRepository(store)

            repository.setNotifyOnCapture(true)

            val remembered = DataStoreCaptureSettingsRepository(store).observe().first()
            assertTrue(remembered.notifyOnCapture)
            assertFalse(remembered.enabled)

            repository.setEnabled(true)
            repository.setNotifyOnCapture(false)

            val disabled = repository.observe().first()
            assertFalse(disabled.notifyOnCapture)
            assertTrue(disabled.enabled)
        }

    private fun TestScope.store(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(coroutineContext + Job()),
            produceFile = { File(folder.newFolder(), "cents.preferences_pb") },
        )
}
