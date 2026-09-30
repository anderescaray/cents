package io.github.anderescaray.cents.ui.feature

import androidx.lifecycle.SavedStateHandle
import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.account
import io.github.anderescaray.cents.data.local.createTestDatabase
import io.github.anderescaray.cents.data.repository.RoomAccountRepository
import io.github.anderescaray.cents.domain.model.CaptureSettings
import io.github.anderescaray.cents.domain.model.CaptureSource
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.domain.repository.CaptureSettingsRepository
import io.github.anderescaray.cents.ui.capture.InstalledApp
import io.github.anderescaray.cents.ui.feature.capture.CaptureAppsViewModel
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

            viewModel.edit(BANK_APP)
            assertEquals(
                BANK_APP,
                viewModel.uiState.value.editing
                    ?.source
                    ?.packageName,
            )
            viewModel.moveTo(card)

            assertEquals(listOf(CaptureSource(BANK_APP, "My bank", card)), settings.state.value.sources)
            assertNull(viewModel.uiState.value.editing)

            viewModel.edit(BANK_APP)
            viewModel.remove()
            assertTrue(
                settings.state.value.sources
                    .isEmpty(),
            )
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

            picker.setQuery("SANTE")
            assertEquals(
                listOf("Banco Santé"),
                picker.uiState.value.apps
                    .map { it.label },
            )
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
            assertEquals(banco, picker.uiState.value.choosing)
            picker.assign(bank)

            assertEquals(listOf(CaptureSource(BANK_APP, "Banco Santé", bank)), settings.state.value.sources)
            assertTrue(picker.uiState.value.finished)
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

            words.requestReset()
            words.confirmReset()
            assertEquals(CaptureSettings.DEFAULT_EXPENSE_WORDS, settings.state.value.expenseWords)
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
                        InstalledApp("com.example.games", "Games"),
                        InstalledApp(WALLET_APP, "Wallet"),
                    )
                },
            ),
        )

    private fun words(kind: TransactionKind) =
        viewModels.keep(CaptureWordsViewModel(settings, SavedStateHandle(mapOf("kind" to kind))))

    private class FakeCaptureSettings : CaptureSettingsRepository {
        val state = MutableStateFlow(CaptureSettings())

        override fun observe(): Flow<CaptureSettings> = state

        override suspend fun setEnabled(enabled: Boolean) {
            state.value = state.value.copy(enabled = enabled)
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
        ) {
            state.value =
                when (kind) {
                    TransactionKind.EXPENSE -> state.value.copy(expenseWords = words)
                    TransactionKind.INCOME -> state.value.copy(incomeWords = words)
                }
        }

        override suspend fun resetWords(kind: TransactionKind) {
            setWords(
                kind,
                when (kind) {
                    TransactionKind.EXPENSE -> CaptureSettings.DEFAULT_EXPENSE_WORDS
                    TransactionKind.INCOME -> CaptureSettings.DEFAULT_INCOME_WORDS
                },
            )
        }
    }

    private companion object {
        const val BANK_APP = "es.example.bank"
        const val WALLET_APP = "com.google.android.apps.walletnfcrel"
    }
}
