package io.github.anderescaray.cents.ui.feature

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.account
import io.github.anderescaray.cents.data.local.createTestDatabase
import io.github.anderescaray.cents.data.local.holding
import io.github.anderescaray.cents.data.repository.RoomAccountRepository
import io.github.anderescaray.cents.data.repository.RoomHoldingRepository
import io.github.anderescaray.cents.domain.model.AccountType
import io.github.anderescaray.cents.domain.model.AssetClass
import io.github.anderescaray.cents.ui.feature.investments.HoldingEditViewModel
import io.github.anderescaray.cents.ui.navigation.Destination
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HoldingEditViewModelTest {
    private lateinit var database: CentsDatabase
    private val viewModels = ViewModelKeeper()
    private var brokerId: Long = 0

    private val clock = Clock.fixed(Instant.parse("2026-09-09T10:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher())
            database = createTestDatabase()
            database.accountDao().insert(account(name = "Checking", type = AccountType.BANK))
            brokerId = database.accountDao().insert(account(name = "Broker", type = AccountType.BROKER))
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
    fun `a new holding starts on the broker account, since that is where investments live`() =
        runTest {
            val viewModel = editorFor()

            viewModel.uiState.test {
                val state = awaitStateThat { it.accounts.isNotEmpty() }

                assertEquals(brokerId, state.accountId)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `the symbol is upper-cased, because a price is stored against it`() =
        runTest {
            val viewModel = editorFor()
            viewModel.onSymbolChanged("nvda")
            viewModel.onNameChanged("NVIDIA")

            viewModel.uiState.test {
                assertEquals("NVDA", awaitStateThat { it.symbol.isNotEmpty() }.symbol)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `holding the same symbol twice in one account is refused before the database refuses it`() =
        runTest {
            database.holdingDao().insert(holding(brokerId, symbol = "NVDA"))

            val viewModel = editorFor()
            viewModel.onNameChanged("NVIDIA again")
            viewModel.onSymbolChanged("NVDA")

            viewModel.uiState.test {
                val state = awaitStateThat { it.duplicateOf != null }

                assertFalse("saving would split one position in two", state.canSave)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `the same symbol in a different account is allowed`() =
        runTest {
            val other = database.accountDao().insert(account(name = "Second broker", type = AccountType.BROKER))
            database.holdingDao().insert(holding(other, symbol = "NVDA"))

            val viewModel = editorFor()
            viewModel.onNameChanged("NVIDIA")
            viewModel.onSymbolChanged("NVDA")

            viewModel.uiState.test {
                // The wait names the condition the assertion is about (`PLAN.md` C4): waiting
                // only for the symbol would pass while the account list was still loading, and
                // canSave would then be false for a reason that has nothing to do with the test.
                val state = awaitStateThat { it.symbol == "NVDA" && it.name.isNotEmpty() && it.accountId != null }

                assertTrue(state.canSave)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `saving writes the holding into the chosen account, in that account's currency`() =
        runTest {
            val viewModel = editorFor()
            viewModel.onSymbolChanged("btc")
            viewModel.onNameChanged("Bitcoin")
            viewModel.onAssetClassChanged(AssetClass.CRYPTO)

            viewModel.uiState.test {
                awaitStateThat { it.canSave }
                viewModel.save()
                // `save()` launches into the view model's scope and the write lands on Room's
                // executor, so reading the database on the next line races it. `finished` is
                // set after the write returns, which is what makes this deterministic rather
                // than usually-fast-enough.
                awaitStateThat { it.finished }
                cancelAndIgnoreRemainingEvents()
            }

            val saved =
                database
                    .holdingDao()
                    .observeAll()
                    .first()
                    .single()
            assertEquals("BTC", saved.symbol)
            assertEquals(AssetClass.CRYPTO, saved.assetClass)
            assertEquals(brokerId, saved.accountId)
            assertEquals("EUR", saved.currency)
        }

    @Test
    fun `a holding in an archived account can still be edited, and keeps its currency`() =
        runTest {
            // Archiving an account does not delete what is held in it. The editor used to list
            // only active accounts, so no row matched, Save was enabled with nothing selected,
            // and the currency fell through to a hard-coded default (rule 2).
            val archived = database.accountDao().insert(account(name = "Old broker", archived = true))
            val id = database.holdingDao().insert(holding(archived, symbol = "NVDA"))

            val viewModel = editorFor(holdingId = id)

            viewModel.uiState.test {
                val state = awaitStateThat { it.accounts.isNotEmpty() && it.symbol == "NVDA" }

                assertEquals(archived, state.accountId)
                assertNotNull("the account it is in has to be in the list", state.account)
                assertTrue(state.canSave)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a write the database refuses is reported rather than escaping`() =
        runTest {
            // The duplicate check is a courtesy: the row it looked for can appear between the
            // check and the write. What must not happen is the constraint violation escaping
            // viewModelScope, which takes the app with it.
            val viewModel = editorFor()
            viewModel.onSymbolChanged("NVDA")
            viewModel.onNameChanged("NVIDIA")

            viewModel.uiState.test {
                awaitStateThat { it.canSave }
                cancelAndIgnoreRemainingEvents()
            }

            database.holdingDao().insert(holding(brokerId, symbol = "NVDA"))
            viewModel.save()

            viewModel.uiState.test {
                val state = awaitStateThat { !it.saving }

                assertTrue("the refusal is shown", state.writeFailed)
                assertFalse("and nothing pretends it was saved", state.finished)
                cancelAndIgnoreRemainingEvents()
            }
        }

    private fun editorFor(holdingId: Long = Destination.HoldingEdit.NEW): HoldingEditViewModel =
        viewModels.keep(
            HoldingEditViewModel(
                RoomHoldingRepository(database.holdingDao(), database.transactionDao(), database, clock),
                RoomAccountRepository(database.accountDao(), database.transactionDao(), database, clock),
                SavedStateHandle(mapOf("holdingId" to holdingId)),
            ),
        )
}
