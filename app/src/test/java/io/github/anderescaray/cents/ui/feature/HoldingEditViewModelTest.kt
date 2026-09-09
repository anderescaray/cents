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
                cancelAndIgnoreRemainingEvents()
            }
            viewModel.save()

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

    private fun editorFor(holdingId: Long = Destination.HoldingEdit.NEW): HoldingEditViewModel =
        viewModels.keep(
            HoldingEditViewModel(
                RoomHoldingRepository(database.holdingDao(), clock),
                RoomAccountRepository(database.accountDao(), database.transactionDao(), database, clock),
                SavedStateHandle(mapOf("holdingId" to holdingId)),
            ),
        )
}
