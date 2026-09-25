package io.github.anderescaray.cents.ui.feature.settings

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.anderescaray.cents.data.export.BackupParse
import io.github.anderescaray.cents.data.export.BackupReader
import io.github.anderescaray.cents.data.export.DocumentReader
import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.domain.model.Backup
import io.github.anderescaray.cents.domain.repository.BackupRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject

/** How far the restore has got. */
enum class RestoreStep {
    IDLE,

    /** The system file picker is what happens next. */
    PICKING,

    /** Reading the file and working out what is in it. */
    READING,

    /**
     * The file is readable, and this is the last moment before everything currently recorded
     * goes. What is in the file and what is about to be replaced are both on screen.
     */
    CONFIRMING,

    WRITING,

    DONE,

    /** The file could not be used, and [RestoreUiState.problem] says which way. */
    REFUSED,

    /** The file was fine and the write was not. Nothing changed. */
    FAILED,

    /**
     * The file was fine, but what is recorded now could not be read, so the confirmation could not
     * say what it would replace. Nothing changed, and nothing was written.
     */
    CURRENT_UNREADABLE,
}

/** Why a file was turned away, in the owner's terms rather than the parser's. */
enum class RestoreProblem {
    NOT_A_BACKUP,
    TOO_NEW,
    DAMAGED,
}

@Immutable
data class RestoreUiState(
    val step: RestoreStep = RestoreStep.IDLE,
    /** What the chosen file holds, once it has been read. */
    val incoming: RestoreSummary? = null,
    /**
     * What is recorded right now, which is what the restore would replace. Only meaningful while
     * [RestoreStep.CONFIRMING]: a confirmation is never offered without it.
     */
    val current: RestoreSummary = RestoreSummary.NOTHING,
    val problem: RestoreProblem? = null,
    /** Set when [RestoreProblem.TOO_NEW], so the message can name both versions. */
    val fileVersion: Int = 0,
) {
    val isBusy: Boolean get() = step == RestoreStep.READING || step == RestoreStep.WRITING
}

/** How much is in a backup, or in the app: enough to compare the two at a glance. */
@Immutable
data class RestoreSummary(
    val accounts: Int,
    val movements: Int,
    val holdings: Int,
    /** Counted so that restoring a file from before backups carried them says what it drops. */
    val subscriptions: Int,
    val exportedAt: Instant?,
    /**
     * Configuration rather than money, so they are named apart and only when there are any: a
     * restore replaces them like everything else, and the alert must not let them go unsaid.
     */
    val budgets: Int = 0,
    val groups: Int = 0,
) {
    val isEmpty: Boolean
        get() = accounts == 0 && movements == 0 && holdings == 0 && subscriptions == 0 && budgets == 0 && groups == 0

    val hasBudgetsOrGroups: Boolean get() = budgets > 0 || groups > 0

    companion object {
        val NOTHING =
            RestoreSummary(accounts = 0, movements = 0, holdings = 0, subscriptions = 0, exportedAt = null)
    }
}

/**
 * Putting a backup back.
 *
 * **A restore replaces everything** (`DECISIONS.md` D21). That makes it the most destructive
 * thing this app can do — more so than deleting an account with its movements, which at least
 * only loses one account — so the sequence is built to be interrupted: pick a file, be told what
 * is in it *and* what is about to be replaced, and only then confirm.
 *
 * The file is read before the question is asked, and that ordering is the point. A confirmation
 * shown first would be asking the owner to agree to lose everything on the strength of a file
 * nobody has looked at yet; asking after means the alert can say *"12 accounts and 300 movements
 * are about to replace what you have"* and be checkable.
 */
@HiltViewModel
class RestoreViewModel
    @Inject
    constructor(
        private val backupRepository: BackupRepository,
        private val documentReader: DocumentReader,
    ) : ViewModel() {
        private val state = MutableStateFlow(RestoreUiState())
        val uiState: StateFlow<RestoreUiState> = state.asStateFlow()

        private var reading: Job? = null

        fun start() {
            if (state.value.isBusy) return
            cancel()
            state.value = RestoreUiState(step = RestoreStep.PICKING)
        }

        fun cancel() {
            if (state.value.step == RestoreStep.WRITING) return
            reading?.cancel()
            pending = null
            state.value = RestoreUiState()
        }

        /** The picker came back, with a file or with nothing. */
        fun readFrom(uri: Uri?) {
            if (uri == null) cancel() else read { documentReader.read(uri) }
        }

        fun readFromDrive(text: String) = read { text }

        private fun read(source: suspend () -> String) {
            if (state.value.step == RestoreStep.WRITING) return
            reading?.cancel()
            pending = null
            state.value = RestoreUiState(step = RestoreStep.READING)
            reading =
                viewModelScope.launch {
                    val parsed =
                        try {
                            val text = source()
                            withContext(Dispatchers.Default) { BackupReader.parse(text, CentsDatabase.VERSION) }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            refuse(RestoreProblem.DAMAGED)
                            return@launch
                        }
                    accept(parsed)
                }
        }

        private suspend fun accept(parsed: BackupParse) {
            when (parsed) {
                is BackupParse.Restorable -> {
                    val current =
                        try {
                            currentlyRecorded()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            // Not FAILED: that says a write failed, and nothing was written.
                            state.update { it.copy(step = RestoreStep.CURRENT_UNREADABLE) }
                            return
                        }
                    offer(parsed.backup, current)
                }

                BackupParse.NotABackup -> {
                    refuse(RestoreProblem.NOT_A_BACKUP)
                }

                is BackupParse.TooNew -> {
                    state.update {
                        it.copy(
                            step = RestoreStep.REFUSED,
                            problem = RestoreProblem.TOO_NEW,
                            fileVersion = parsed.fileVersion,
                        )
                    }
                }

                is BackupParse.Damaged -> {
                    refuse(RestoreProblem.DAMAGED)
                }
            }
        }

        /**
         * Writes it, replacing everything.
         *
         * [pending] is held here rather than in the state, because the state is what the screen
         * renders and a whole backup is not something a screen has any use for.
         */
        fun confirm() {
            if (state.value.step != RestoreStep.CONFIRMING) return
            val backup = pending ?: return
            pending = null
            state.update { it.copy(step = RestoreStep.WRITING) }
            viewModelScope.launch {
                try {
                    backupRepository.restore(backup)
                    state.update { it.copy(step = RestoreStep.DONE) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    state.update { it.copy(step = RestoreStep.FAILED) }
                }
            }
        }

        fun acknowledge() = cancel()

        private var pending: Backup? = null

        /**
         * What is recorded right now, read **on the way to the question rather than alongside
         * it**.
         *
         * Reading it when the flow starts would race the owner: pick a file quickly enough and
         * the confirmation arrives before the answer does, and the alert then has to leave out
         * the half that says what is about to be lost. That is the half the owner most needs,
         * so it is fetched where its absence cannot be a matter of timing.
         */
        private suspend fun currentlyRecorded(): RestoreSummary = backupRepository.snapshot().summarise()

        private fun offer(
            backup: Backup,
            current: RestoreSummary,
        ) {
            pending = backup
            state.update {
                it.copy(step = RestoreStep.CONFIRMING, incoming = backup.summarise(), current = current)
            }
        }

        private fun refuse(problem: RestoreProblem) {
            pending = null
            state.update { it.copy(step = RestoreStep.REFUSED, problem = problem) }
        }
    }

private fun Backup.summarise() =
    RestoreSummary(
        accounts = accounts.size,
        movements = transactions.size,
        holdings = holdings.size,
        subscriptions = rules.size,
        budgets = budgets.size,
        groups = groups.size,
        exportedAt = exportedAt.takeIf { it != Instant.EPOCH },
    )
