package io.github.anderescaray.cents.data.repository

import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.account
import io.github.anderescaray.cents.data.local.createTestDatabase
import io.github.anderescaray.cents.data.local.transaction
import io.github.anderescaray.cents.domain.model.AppWords
import io.github.anderescaray.cents.domain.model.CaptureOutcome
import io.github.anderescaray.cents.domain.model.CaptureOutcome.IgnoreReason
import io.github.anderescaray.cents.domain.model.CaptureSettings
import io.github.anderescaray.cents.domain.model.CaptureSource
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.domain.repository.CaptureSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * One notification in, one movement or nothing out (`DECISIONS.md` D29), against a real database:
 * what is written, where, and what is not written twice.
 */
@RunWith(RobolectricTestRunner::class)
class CaptureRecorderTest {
    private lateinit var database: CentsDatabase
    private lateinit var settings: FakeCaptureSettings
    private var bankAccount = 0L
    private var cardAccount = 0L

    @Before
    fun setUp() =
        runTest {
            database = createTestDatabase()
            bankAccount = database.accountDao().insert(account(name = "Bank"))
            cardAccount = database.accountDao().insert(account(name = "Card"))
            settings =
                FakeCaptureSettings(
                    CaptureSettings(
                        enabled = true,
                        sources =
                            listOf(
                                CaptureSource(BANK_APP, "My bank", bankAccount),
                                CaptureSource(WALLET_APP, "Wallet", cardAccount),
                            ),
                    ),
                )
        }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `a payment from a chosen app is written to its account, uncategorized`() =
        runTest {
            val outcome = recorder().record(BANK_APP, "Compra en CAFÉ CENTRAL por 23,40 €", AT_10)

            val id = (outcome as CaptureOutcome.Recorded).transactionId
            val row = requireNotNull(database.transactionDao().findById(id))
            assertEquals(bankAccount, row.accountId)
            assertNull(row.categoryId)
            assertEquals(TransactionKind.EXPENSE, row.kind)
            assertEquals(2_340L, row.amountMinor)
            assertEquals("EUR", row.currency)
            assertEquals(LocalDate.of(2026, 9, 29), row.date)
            assertEquals("Compra en CAFÉ CENTRAL por 23,40 €", row.note)
            assertEquals(Money(row.amountMinor, row.currency), outcome.amount)
            assertEquals(row.kind, outcome.kind)
            assertEquals("Bank", outcome.accountName)
        }

    @Test
    fun `each app is read with its own words, not another app's`() =
        runTest {
            // The bank says "nuevo gasto"; the wallet keeps the starting words.
            settings.setWords(TransactionKind.EXPENSE, listOf("nuevo gasto"), BANK_APP)
            val recorder = recorder()

            val bank = recorder.record(BANK_APP, "Nuevo gasto de 12,00 € en Mercadona", AT_10)
            val bankOldWord = recorder.record(BANK_APP, "Compra de 30,00 €", AT_10.plusSeconds(120))
            val wallet = recorder.record(WALLET_APP, "Nuevo gasto de 7,00 €", AT_10.plusSeconds(240))
            val walletOldWord = recorder.record(WALLET_APP, "Compra de 8,00 €", AT_10.plusSeconds(360))

            assertTrue(bank is CaptureOutcome.Recorded)
            assertEquals(CaptureOutcome.Ignored(IgnoreReason.NO_WORD), bankOldWord)
            assertEquals(CaptureOutcome.Ignored(IgnoreReason.NO_WORD), wallet)
            assertTrue(walletOldWord is CaptureOutcome.Recorded)
        }

    @Test
    fun `money arriving is written as income`() =
        runTest {
            val outcome = recorder().record(BANK_APP, "Has recibido 120,05 € de Ana", AT_10)

            val row =
                requireNotNull(database.transactionDao().findById((outcome as CaptureOutcome.Recorded).transactionId))
            assertEquals(TransactionKind.INCOME, row.kind)
            assertEquals(12_005L, row.amountMinor)
        }

    @Test
    fun `the same payment in the same minute is written once, whichever app says it`() =
        runTest {
            val recorder = recorder()
            recorder.record(BANK_APP, "Compra por 23,40 €", AT_10)

            // Wallet announces the same payment 40 seconds later, over a minute boundary.
            val again = recorder.record(WALLET_APP, "Pago de 23,40 € con Wallet", AT_10.plusSeconds(40))
            // Two minutes later it is another coffee.
            val later = recorder.record(BANK_APP, "Compra por 23,40 €", AT_10.plusSeconds(120))

            assertEquals(CaptureOutcome.Duplicate, again)
            assertTrue(later is CaptureOutcome.Recorded)
            assertEquals(2, movements())
        }

    @Test
    fun `a different amount in the same minute is a different payment`() =
        runTest {
            val recorder = recorder()
            recorder.record(BANK_APP, "Compra por 23,40 €", AT_10)
            recorder.record(BANK_APP, "Compra por 4,10 €", AT_10.plusSeconds(10))

            assertEquals(2, movements())
        }

    @Test
    fun `a movement that was not captured does not make a payment a duplicate`() =
        runTest {
            // Typed by hand, restored, or written by a subscription in that same minute.
            database.transactionDao().insert(
                transaction(bankAccount, amountMinor = 2_340, date = LocalDate.of(2026, 9, 29)),
            )

            val outcome = recorder().record(BANK_APP, "Compra por 23,40 €", AT_10)

            assertTrue(outcome is CaptureOutcome.Recorded)
            assertEquals(2, movements())
        }

    @Test
    fun `nothing is kept when capture is off, the app was not chosen, or the text says nothing`() =
        runTest {
            val notChosen = recorder().record("com.example.game", "Compra por 23,40 €", AT_10)
            val noWord = recorder().record(BANK_APP, "Ofertas: hasta 30,00 € de descuento", AT_10)
            val noAmount = recorder().record(BANK_APP, "Compra aprobada", AT_10)
            settings.value = settings.value.copy(enabled = false)
            val off = recorder().record(BANK_APP, "Compra por 23,40 €", AT_10)

            assertEquals(CaptureOutcome.Ignored(IgnoreReason.NOT_CHOSEN), notChosen)
            assertEquals(CaptureOutcome.Ignored(IgnoreReason.NO_WORD), noWord)
            assertEquals(CaptureOutcome.Ignored(IgnoreReason.NO_AMOUNT), noAmount)
            assertEquals(CaptureOutcome.Ignored(IgnoreReason.OFF), off)
            assertEquals(0, movements())
        }

    @Test
    fun `an amount in another currency, or an account that is gone, keeps nothing`() =
        runTest {
            val dollars = recorder().record(BANK_APP, "Payment of USD 12.00", AT_10)
            database.accountDao().setArchived(cardAccount, archived = true)
            val archived = recorder().record(WALLET_APP, "Pago de 9,99 €", AT_10)

            assertEquals(CaptureOutcome.Ignored(IgnoreReason.OTHER_CURRENCY), dollars)
            assertEquals(CaptureOutcome.Ignored(IgnoreReason.NO_ACCOUNT), archived)
            assertEquals(0, movements())
        }

    @Test
    fun `ambiguous amounts masked numbers and unmarked decimals write nothing`() =
        runTest {
            val recorder = recorder()
            listOf(
                "Paid with card ****7146 EUR",
                "Payment at 10.30 with card ****7146",
                "Pago de 23,40 €. Saldo disponible: 1.502,10 €",
                "Ingreso 23,40",
            ).forEach { text ->
                assertEquals(CaptureOutcome.Ignored(IgnoreReason.NO_AMOUNT), recorder.record(BANK_APP, text, AT_10))
            }
            assertEquals(0, movements())
        }

    @Test
    fun `a masked card before the currency never changes the saved amount`() =
        runTest {
            val outcome = recorder().record(BANK_APP, "Paid with card ****7146 EUR 23", AT_10)

            val recorded = outcome as CaptureOutcome.Recorded
            val row = requireNotNull(database.transactionDao().findById(recorded.transactionId))
            assertEquals(2_300L, row.amountMinor)
            assertEquals(Money(2_300, "EUR"), recorded.amount)
        }

    private fun recorder() =
        RoomCaptureRecorder(
            settings = settings,
            accountDao = database.accountDao(),
            transactionDao = database.transactionDao(),
            clock = Clock.fixed(AT_10, ZoneOffset.UTC),
        )

    private suspend fun movements(): Int =
        database
            .transactionDao()
            .observeAll()
            .first()
            .size

    private class FakeCaptureSettings(
        initial: CaptureSettings,
    ) : CaptureSettingsRepository {
        private val state = MutableStateFlow(initial)
        var value: CaptureSettings
            get() = state.value
            set(next) {
                state.value = next
            }

        override fun observe(): Flow<CaptureSettings> = state

        override suspend fun setEnabled(enabled: Boolean) {
            value = value.copy(enabled = enabled)
        }

        override suspend fun setNotifyOnCapture(enabled: Boolean) {
            value = value.copy(notifyOnCapture = enabled)
        }

        override suspend fun putSource(source: CaptureSource) {
            value = value.copy(sources = value.sources.filterNot { it.packageName == source.packageName } + source)
        }

        override suspend fun removeSource(packageName: String) {
            value = value.copy(sources = value.sources.filterNot { it.packageName == packageName })
        }

        override suspend fun setWords(
            kind: TransactionKind,
            words: List<String>,
            packageName: String?,
        ) {
            value =
                if (packageName != null) {
                    val own = value.appWords[packageName] ?: AppWords()
                    val changed =
                        when (kind) {
                            TransactionKind.EXPENSE -> own.copy(expense = words)
                            TransactionKind.INCOME -> own.copy(income = words)
                        }
                    value.copy(appWords = value.appWords + (packageName to changed))
                } else {
                    when (kind) {
                        TransactionKind.EXPENSE -> value.copy(expenseWords = words)
                        TransactionKind.INCOME -> value.copy(incomeWords = words)
                    }
                }
        }

        override suspend fun resetWords(
            kind: TransactionKind,
            packageName: String?,
        ) {
            if (packageName != null) {
                val own = value.appWords[packageName] ?: return
                val cleared =
                    when (kind) {
                        TransactionKind.EXPENSE -> own.copy(expense = null)
                        TransactionKind.INCOME -> own.copy(income = null)
                    }
                value = value.copy(appWords = value.appWords + (packageName to cleared))
                return
            }
            setWords(
                kind,
                when (kind) {
                    TransactionKind.EXPENSE -> CaptureSettings.DEFAULT_EXPENSE_WORDS
                    TransactionKind.INCOME -> CaptureSettings.DEFAULT_INCOME_WORDS
                },
                null,
            )
        }
    }

    private companion object {
        const val BANK_APP = "es.example.bank"
        const val WALLET_APP = "com.google.android.apps.walletnfcrel"
        val AT_10: Instant = Instant.parse("2026-09-29T10:00:30Z")
    }
}
