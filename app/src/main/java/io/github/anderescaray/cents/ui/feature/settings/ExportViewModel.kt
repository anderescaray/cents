package io.github.anderescaray.cents.ui.feature.settings

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.anderescaray.cents.data.export.BackupWriter
import io.github.anderescaray.cents.data.export.DocumentWriter
import io.github.anderescaray.cents.data.export.ExportFormat
import io.github.anderescaray.cents.domain.repository.BackupRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

/** How far the export has got, which is the whole of what this screen has to say. */
enum class ExportStep {
    /** Nothing is happening. */
    IDLE,

    /** The owner is being asked which of the two files they want. */
    CHOOSING,

    /** They chose, and the system file picker is what happens next. */
    PICKING,

    /** Reading the database and writing the file. */
    WRITING,

    /** It is on the phone, wherever they put it. */
    DONE,

    /**
     * It is not.
     *
     * A failed export says so rather than saying nothing: the one thing worse than having no
     * backup is believing you have one.
     */
    FAILED,
}

@Immutable
data class ExportUiState(
    val step: ExportStep = ExportStep.IDLE,
    /** Chosen once [ExportStep.PICKING] is reached, and what the picker is opened for. */
    val format: ExportFormat? = null,
    /** What the picker offers as a name; the owner may change it. */
    val suggestedName: String = "",
    /** How many rows went into the file, so the screen can say something checkable. */
    val rowsWritten: Int = 0,
) {
    val isBusy: Boolean get() = step == ExportStep.WRITING
}

/**
 * Writing the app out to a file the owner keeps (`DECISIONS.md` D15, PRD §4.7).
 *
 * The sequence is deliberately two steps rather than one. **Which file** is a question about
 * what the export is for — the whole app, or the movements in a spreadsheet — and **where it
 * goes** is the system's own picker, which is what lets this app write a file without asking
 * for permission to write files. Neither question can answer the other, so neither is skipped.
 *
 * Nothing here leaves the phone. There is no account, no upload and no third party: that is the
 * point of doing the manual export first and the automatic backup afterwards.
 */
@HiltViewModel
class ExportViewModel
    @Inject
    constructor(
        private val backupRepository: BackupRepository,
        private val documentWriter: DocumentWriter,
        private val clock: Clock,
    ) : ViewModel() {
        private val state = MutableStateFlow(ExportUiState())
        val uiState: StateFlow<ExportUiState> = state.asStateFlow()

        fun start() = state.update { ExportUiState(step = ExportStep.CHOOSING) }

        fun cancel() {
            state.value = ExportUiState()
        }

        /** Records the choice and hands over to the picker the screen launches. */
        fun choose(format: ExportFormat) =
            state.update {
                it.copy(
                    step = ExportStep.PICKING,
                    format = format,
                    suggestedName = BackupWriter.fileName(format, clock.instant()),
                )
            }

        /**
         * The picker came back with somewhere to write, or with nothing.
         *
         * A null [uri] is the owner backing out, which is not a failure and must not be
         * reported as one — it is the most common way this flow ends.
         */
        fun writeTo(uri: Uri?) {
            val format = state.value.format
            if (uri == null || format == null) {
                cancel()
                return
            }
            state.update { it.copy(step = ExportStep.WRITING) }
            viewModelScope.launch {
                val written =
                    runCatching {
                        val backup = backupRepository.snapshot()
                        val text =
                            when (format) {
                                ExportFormat.JSON -> BackupWriter.toJson(backup)
                                ExportFormat.CSV -> BackupWriter.toCsv(backup)
                            }
                        documentWriter.write(uri, text)
                        backup.rowCount
                    }
                state.update {
                    it.copy(
                        step = if (written.isSuccess) ExportStep.DONE else ExportStep.FAILED,
                        rowsWritten = written.getOrDefault(0),
                    )
                }
            }
        }

        fun acknowledge() = cancel()
    }
