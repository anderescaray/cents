package io.github.anderescaray.cents.ui.feature

import androidx.lifecycle.SavedStateHandle
import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.account
import io.github.anderescaray.cents.data.local.createTestDatabase
import io.github.anderescaray.cents.data.repository.RoomAccountRepository
import io.github.anderescaray.cents.domain.model.AccountType
import io.github.anderescaray.cents.domain.model.AppWords
import io.github.anderescaray.cents.domain.model.CaptureSettings
import io.github.anderescaray.cents.domain.model.CaptureSource
import io.github.anderescaray.cents.domain.model.CaptureWordList
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.domain.repository.CaptureSettingsRepository
import io.github.anderescaray.cents.domain.usecase.CaptureParser
import io.github.anderescaray.cents.ui.capture.InstalledApp
import io.github.anderescaray.cents.ui.feature.capture.CaptureAppsViewModel
import io.github.anderescaray.cents.ui.feature.capture.CaptureTryBlocker
import io.github.anderescaray.cents.ui.feature.capture.CaptureTryViewModel
import io.github.anderescaray.cents.ui.feature.capture.CaptureViewModel
import io.github.anderescaray.cents.ui.feature.capture.CaptureWordsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * Settings › Capture (`DECISIONS.md` D29): the apps with their accounts, the picker that adds one,
 * and the two word lists, against real accounts in Room.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CaptureViewModelsTest {
    private lateinit var database: CentsDatabase
    private lateinit var accounts: RoomAccountRepository
    private val settings = FakeCaptureSettings()
    private val viewModels = ViewModelKeeper()
    private val clock = Clock.fixed(Instant.parse("2026-09-30T10:00:00Z"), ZoneOffset.UTC)
    private var bank = 0L
    private var card = 0L

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        database = createTestDatabase()
        accounts = RoomAccountRepository(database.accountDao(), database.transactionDao(), database, clock)
        runBlocking {
            bank = database.accountDao().insert(account(name = "Bank"))
            card = database.accountDao().insert(account(name = "Card"))
        }
    }

    @After
    fun tearDown() {
        try {
            viewModels.cancelAll()
            if (::database.isInitialized) database.close()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `each app names the account it writes to, and says so when that account is gone`() =
        runTest {
            val archived = database.accountDao().insert(account(name = "Old", archived = true))
            settings.state.value =
                CaptureSettings(
                    sources =
                        listOf(
                            CaptureSource(BANK_APP, "My bank", bank),
                            CaptureSource(WALLET_APP, "Wallet", archived),
                        ),
                )

            val state = capture().uiState.first { !it.isLoading && it.accounts.size == 2 }

            assertEquals("Bank", state.sources[0].accountName)
            // Capture keeps nothing from it (NO_ACCOUNT), so the screen must not look set up.
            assertNull(state.sources[1].accountName)
            assertTrue(state.canAddApp)
        }

    @Test
    fun `an app moves to another account, or stops being read`() =
        runTest {
            settings.state.value = CaptureSettings(sources = listOf(CaptureSource(BANK_APP, "My bank", bank)))
            val viewModel = capture()
            viewModel.uiState.first { it.accounts.size == 2 }

            // Each step waits for the whole state it should leave behind instead of reading
            // `value`. The accounts come from Room, and under the test's unconfined Main the
            // combine can be running on Room's thread when an action lands, so the state may
            // follow a moment later. This file failed CI three runs in a row on it (2026-10-04).
            viewModel.edit(BANK_APP)
            assertEquals(
                BANK_APP,
                viewModel.uiState
                    .first { it.editing != null }
                    .editing
                    ?.source
                    ?.packageName,
            )
            viewModel.moveTo(card)

            assertEquals(listOf(CaptureSource(BANK_APP, "My bank", card)), settings.state.value.sources)
            // Saved, and the sheet closed. The wait names both, since either can land first.
            viewModel.uiState.first { it.editing == null && it.sources.singleOrNull()?.accountName == "Card" }

            // Stopping asks first, and cancelling keeps the app.
            viewModel.edit(BANK_APP)
            viewModel.requestStop()
            val asking = viewModel.uiState.first { it.stopping != null && it.editing == null }
            assertEquals(
                BANK_APP,
                asking.stopping
                    ?.source
                    ?.packageName,
            )
            viewModel.cancelStop()
            assertEquals(1, settings.state.value.sources.size)

            viewModel.edit(BANK_APP)
            viewModel.requestStop()
            viewModel.confirmStop()
            assertEquals(0, settings.state.value.sources.size)
        }

    @Test
    fun `a broker is not offered to tie an app to, and an app already in one keeps it`() =
        runTest {
            val broker = database.accountDao().insert(account(name = "Broker", type = AccountType.BROKER))
            settings.state.value = CaptureSettings(sources = listOf(CaptureSource(BANK_APP, "My bank", broker)))

            val state = capture().uiState.first { !it.isLoading && it.accounts.isNotEmpty() }
            val chosen =
                picker()
                    .uiState
                    .first { !it.isLoading && it.accounts.isNotEmpty() }

            // Capture writes ordinary incomes and expenses, and those do not go in a broker (D32).
            assertEquals(listOf(bank, card), state.accounts.map { it.id })
            assertEquals(listOf(bank, card), chosen.accounts.map { it.id })
            assertEquals("Broker", state.sources.single().accountName)
        }

    @Test
    fun `with no account there is nothing to tie an app to`() =
        runTest {
            database.accountDao().setArchived(bank, archived = true)
            database.accountDao().setArchived(card, archived = true)

            val state = capture().uiState.first { !it.isLoading && it.accounts.isEmpty() }

            assertFalse(state.canAddApp)
        }

    @Test
    fun `the switch is the setting`() =
        runTest {
            val viewModel = capture()

            viewModel.setEnabled(true)

            assertTrue(settings.state.value.enabled)
            assertTrue(viewModel.uiState.first { !it.isLoading }.enabled)
        }

    @Test
    fun `the picker leaves out chosen apps, and finds the rest ignoring case and accents`() =
        runTest {
            settings.state.value = CaptureSettings(sources = listOf(CaptureSource(WALLET_APP, "Wallet", card)))
            val picker = picker()

            val all = picker.uiState.first { !it.isLoading }
            assertEquals(listOf("Banco Santé", "Games"), all.apps.map { it.label })

            assertEquals(listOf("Banco Santé"), picker.search("SANTE"))

            // A package name is searched too, whatever its case.
            assertEquals(listOf("Games"), picker.search("Example.GAMES"))
        }

    @Test
    fun `choosing an app asks for its account, then saves the pair and closes`() =
        runTest {
            val picker = picker()
            val banco =
                picker.uiState
                    .first { !it.isLoading && it.accounts.size == 2 }
                    .apps
                    .first()

            picker.choose(banco)
            assertEquals(banco, picker.uiState.first { it.choosing != null }.choosing)
            picker.assign(bank)

            assertEquals(listOf(CaptureSource(BANK_APP, "Banco Santé", bank)), settings.state.value.sources)
            assertNull(picker.uiState.first { it.finished }.choosing)
        }

    @Test
    fun `a word is added trimmed, and one the parser would read as the same is refused`() =
        runTest {
            val words = words(TransactionKind.INCOME)
            words.uiState.first { !it.isLoading }

            words.setDraft("  bizum de  ")
            words.add()
            assertEquals(CaptureSettings.DEFAULT_INCOME_WORDS + "bizum de", settings.state.value.incomeWords)
            assertEquals("", words.uiState.value.draft)

            // "nómina" is already there: the parser folds case and accents, so this is no new word.
            words.setDraft("NOMINA")
            assertTrue(words.uiState.value.draftIsDuplicate)
            words.add()
            assertEquals(CaptureSettings.DEFAULT_INCOME_WORDS + "bizum de", settings.state.value.incomeWords)
            assertEquals(CaptureSettings.DEFAULT_EXPENSE_WORDS, settings.state.value.expenseWords)
        }

    @Test
    fun `a word is removed only once confirmed, and the defaults come back on request`() =
        runTest {
            val words = words(TransactionKind.EXPENSE)
            assertTrue(words.uiState.first { !it.isLoading }.isDefault)

            words.requestRemove("pago")
            words.cancelRemove()
            assertEquals(CaptureSettings.DEFAULT_EXPENSE_WORDS, settings.state.value.expenseWords)

            words.requestRemove("pago")
            words.confirmRemove()
            assertFalse("pago" in settings.state.value.expenseWords)
            assertFalse(words.uiState.value.isDefault)

            // The same words back, in another order, are the defaults: nothing left to restore.
            settings.state.value = settings.state.value.copy(expenseWords = DEFAULT_EXPENSE_SORTED)
            assertTrue(words.uiState.first { "pago" in it.words }.isDefault)

            words.requestRemove("pago")
            words.confirmRemove()
            words.requestReset()
            words.confirmReset()
            assertEquals(CaptureSettings.DEFAULT_EXPENSE_WORDS, settings.state.value.expenseWords)
        }

    @Test
    fun `the confirmation switch updates its setting and leaves capture unchanged`() =
        runTest {
            val viewModel = capture()

            viewModel.setNotifyOnCapture(true)

            val enabled = viewModel.uiState.first { !it.isLoading && it.notifyOnCapture }
            assertTrue(settings.state.value.notifyOnCapture)
            assertFalse(enabled.enabled)

            viewModel.setEnabled(true)
            viewModel.setNotifyOnCapture(false)

            val disabled = viewModel.uiState.first { !it.isLoading && it.enabled && !it.notifyOnCapture }
            assertFalse(settings.state.value.notifyOnCapture)
            assertTrue(disabled.enabled)
        }

    private fun capture() = viewModels.keep(CaptureViewModel(settings, accounts))

    private fun picker() =
        viewModels.keep(
            CaptureAppsViewModel(
                settings = settings,
                accounts = accounts,
                installed = {
                    listOf(
                        InstalledApp(BANK_APP, "Banco Santé"),
                        InstalledApp("com.Example.games", "Games"),
                        InstalledApp(WALLET_APP, "Wallet"),
                    )
                },
            ),
        )

    /**
     * Types [query] and reads the list it narrows to, once that list has caught up with it.
     *
     * Only for a query that differs from the current one: the wait recognises the new list by its
     * query, so a repeated query cannot tell the new list from the one before it.
     */
    private suspend fun CaptureAppsViewModel.search(query: String): List<String> {
        setQuery(query)
        return uiState.first { it.query == query }.apps.map { it.label }
    }

    @Test
    fun `the ignore words are edited and put back like any other list`() =
        runTest {
            val ignore =
                viewModels.keep(
                    CaptureWordsViewModel(settings, SavedStateHandle(mapOf("list" to CaptureWordList.IGNORE))),
                )
            assertEquals(
                CaptureSettings.DEFAULT_IGNORE_WORDS,
                ignore.uiState.first { !it.isLoading }.words,
            )

            ignore.setDraft("pendiente")
            ignore.uiState.first { it.draft == "pendiente" }
            ignore.add()
            ignore.uiState.first { "pendiente" in it.words }
            assertTrue("pendiente" in settings.state.value.ignoreWords)
            // The expense and income words are not touched by it.
            assertEquals(CaptureSettings.DEFAULT_EXPENSE_WORDS, settings.state.value.expenseWords)

            ignore.requestReset()
            ignore.uiState.first { it.confirmingReset }
            ignore.confirmReset()
            ignore.uiState.first { it.isDefault }
            assertEquals(CaptureSettings.DEFAULT_IGNORE_WORDS, settings.state.value.ignoreWords)
        }

    @Test
    fun `trying a notification reads it with the chosen app's words and checks its account's currency`() =
        runTest {
            val savings = database.accountDao().insert(account(name = "Savings"))
            settings.state.value =
                CaptureSettings(
                    enabled = true,
                    sources = listOf(CaptureSource(BANK_APP, "My bank", savings)),
                    appWords = mapOf(BANK_APP to AppWords(expense = listOf("cobro"))),
                )
            val trying =
                viewModels.keep(
                    CaptureTryViewModel(settings, accounts),
                )
            trying.uiState.first { !it.isLoading && it.app != null }

            trying.setText("Cobro de 23,40 € en tienda. Saldo 1.000 €")
            val records = trying.uiState.first { it.reading != null }
            assertTrue("nothing stands in the way", records.records)
            assertEquals(
                CaptureParser.Reading.Movement(
                    CaptureParser.WordMatch(TransactionKind.EXPENSE, "cobro"),
                    CaptureParser.Amount(2_340, "EUR"),
                ),
                records.reading,
            )
            assertEquals("Savings", records.accountName)
            assertFalse(records.otherCurrency)

            trying.setText("Cobro de 23,40 USD")
            assertTrue(trying.uiState.first { it.otherCurrency }.otherCurrency)
        }

    @Test
    fun `trying a notification reads a title the way capture does, and says what would stop capture`() =
        runTest {
            val savings = database.accountDao().insert(account(name = "Savings"))
            settings.state.value = CaptureSettings(sources = listOf(CaptureSource(BANK_APP, "My bank", savings)))
            val trying = viewModels.keep(CaptureTryViewModel(settings, accounts))
            trying.uiState.first { !it.isLoading && it.app != null }

            // The title repeats the body's amount: one payment, as the listener reads it.
            trying.setTitle("23,40 €")
            trying.setText("Compra por 23,40 € en Amazon")
            val reading = trying.uiState.first { it.reading is CaptureParser.Reading.Movement }
            // But capture is off, so it would not be recorded, and the screen must not say it would.
            assertEquals(CaptureTryBlocker.OFF, reading.blocker)
            assertFalse(reading.records)

            settings.state.value = settings.state.value.copy(enabled = true)
            assertTrue(trying.uiState.first { it.blocker == null }.records)

            // An archived account is one the recorder skips.
            database.accountDao().setArchived(savings, archived = true)
            assertEquals(CaptureTryBlocker.NO_ACCOUNT, trying.uiState.first { it.blocker != null }.blocker)
        }

    private fun words(kind: TransactionKind) =
        viewModels.keep(CaptureWordsViewModel(settings, SavedStateHandle(mapOf("list" to CaptureWordList.of(kind)))))

    private class FakeCaptureSettings : CaptureSettingsRepository {
        val state = MutableStateFlow(CaptureSettings())

        override fun observe(): Flow<CaptureSettings> = state

        override suspend fun setEnabled(enabled: Boolean) {
            state.value = state.value.copy(enabled = enabled)
        }

        override suspend fun setNotifyOnCapture(enabled: Boolean) {
            state.value = state.value.copy(notifyOnCapture = enabled)
        }

        override suspend fun putSource(source: CaptureSource) {
            val others = state.value.sources.filterNot { it.packageName == source.packageName }
            state.value = state.value.copy(sources = others + source)
        }

        override suspend fun removeSource(packageName: String) {
            state.value = state.value.copy(sources = state.value.sources.filterNot { it.packageName == packageName })
        }

        override suspend fun setWords(
            kind: TransactionKind,
            words: List<String>,
            packageName: String?,
        ) {
            state.value =
                if (packageName != null) {
                    val own = state.value.appWords[packageName] ?: AppWords()
                    val changed =
                        when (kind) {
                            TransactionKind.EXPENSE -> own.copy(expense = words)
                            TransactionKind.INCOME -> own.copy(income = words)
                        }
                    state.value.copy(appWords = state.value.appWords + (packageName to changed))
                } else {
                    when (kind) {
                        TransactionKind.EXPENSE -> state.value.copy(expenseWords = words)
                        TransactionKind.INCOME -> state.value.copy(incomeWords = words)
                    }
                }
        }

        override suspend fun resetWords(
            kind: TransactionKind,
            packageName: String?,
        ) {
            if (packageName != null) {
                val own = state.value.appWords[packageName] ?: return
                val cleared =
                    when (kind) {
                        TransactionKind.EXPENSE -> own.copy(expense = null)
                        TransactionKind.INCOME -> own.copy(income = null)
                    }
                state.value = state.value.copy(appWords = state.value.appWords + (packageName to cleared))
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

        override suspend fun setIgnoreWords(words: List<String>) {
            state.value = state.value.copy(ignoreWords = words)
        }

        override suspend fun resetIgnoreWords() {
            state.value = state.value.copy(ignoreWords = CaptureSettings.DEFAULT_IGNORE_WORDS)
        }
    }

    private companion object {
        const val BANK_APP = "es.example.bank"
        const val WALLET_APP = "com.google.android.apps.walletnfcrel"
        val DEFAULT_EXPENSE_SORTED = CaptureSettings.DEFAULT_EXPENSE_WORDS.sorted()
    }
}
