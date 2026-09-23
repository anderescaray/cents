package io.github.anderescaray.cents.ui.feature.settings

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.data.export.BackupWriter
import io.github.anderescaray.cents.domain.model.DriveBackup
import io.github.anderescaray.cents.ui.backup.DriveBackupSession
import io.github.anderescaray.cents.ui.backup.FakeDriveAuthorization
import io.github.anderescaray.cents.ui.backup.FakeDriveBackupScheduler
import io.github.anderescaray.cents.ui.backup.FakeDriveBackupStorage
import io.github.anderescaray.cents.ui.backup.MemoryDriveBackupRepository
import io.github.anderescaray.cents.ui.feature.ViewModelKeeper
import io.github.anderescaray.cents.ui.theme.CentsTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DriveRestoreScreenTest {
    @get:Rule
    val composeRule = createComposeRule()
    private val viewModels = ViewModelKeeper()
    private val repository = SettingsBackupRepository()
    private val incoming = backupWithAccounts("Savings", "Broker")
    private val file = DriveBackup("chosen", "cents-backup-2026-09-22T031200.json", backupTestClock.instant(), 2048)
    private lateinit var context: Context
    private lateinit var restore: RestoreViewModel
    private lateinit var drive: DriveBackupViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        context = ApplicationProvider.getApplicationContext()
        restore = viewModels.keep(RestoreViewModel(repository, SettingsDocumentReader(context)))
        val preferences = MemoryDriveBackupRepository()
        val storage =
            FakeDriveBackupStorage().also {
                it.files = listOf(file)
                it.contents = BackupWriter.toJson(incoming)
            }
        drive =
            viewModels.keep(
                DriveBackupViewModel(
                    preferences,
                    DriveBackupSession(FakeDriveAuthorization(), storage, preferences, backupTestClock),
                    FakeDriveBackupScheduler(),
                ),
            )
        composeRule.setContent {
            val driveState by drive.uiState.collectAsState()
            val restoreState by restore.uiState.collectAsState()
            CentsTheme {
                SettingsContent(
                    onOpenCategories = {},
                    onResetHomeLayout = {},
                    drive = driveState,
                    driveActions =
                        DriveBackupActions(
                            onRestore = drive::showBackups,
                            onChooseBackup = { drive.chooseBackup(it, restore::readFromDrive) },
                            onCloseBackups = drive::closeBackups,
                        ),
                    restore = restoreState,
                    restoreActions =
                        RestoreActions(
                            onConfirm = restore::confirm,
                            onCancel = restore::cancel,
                            onAcknowledge = restore::acknowledge,
                        ),
                )
            }
        }
    }

    @After
    fun tearDown() {
        try {
            viewModels.cancelAll()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `choosing a Drive copy reaches the existing confirmation with both halves and can be cancelled`() {
        openDriveCopy()

        composeRule.onNodeWithText(context.getString(R.string.restore_confirm_title)).assertExists()
        composeRule
            .onNodeWithText(
                context.getString(R.string.restore_confirm_incoming, 2, 0, 0, 0),
                substring = true,
            ).assertExists()
        composeRule
            .onNodeWithText(
                context.getString(R.string.restore_confirm_replaces, 1, 0, 0, 0),
                substring = true,
            ).assertExists()
        assertEquals(0, repository.restores)

        composeRule.onNodeWithText(context.getString(R.string.action_cancel)).performClick()
        assertEquals(0, repository.restores)
        assertEquals(listOf("Checking"), repository.current.accounts.map { it.name })
        composeRule.onNodeWithText(context.getString(R.string.restore_confirm_title)).assertDoesNotExist()
    }

    @Test
    fun `the same confirmation is the only action that replaces the current ledger`() {
        openDriveCopy()
        assertEquals(0, repository.restores)

        composeRule.onNodeWithText(context.getString(R.string.restore_confirm_action)).performClick()

        composeRule.onNodeWithText(context.getString(R.string.restore_done_title)).assertExists()
        assertEquals(1, repository.restores)
        assertEquals(incoming, repository.current)
    }

    private fun openDriveCopy() {
        val label = context.getString(R.string.drive_backup_restore)
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(label))
        composeRule.onNodeWithText(label).performClick()
        composeRule.onNodeWithText(context.getString(R.string.drive_backup_list_title)).assertExists()
        composeRule.onNodeWithText(backupDateTime(context, file.createdAt)).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { restore.uiState.value.step == RestoreStep.CONFIRMING }
    }
}
