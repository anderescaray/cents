package io.github.anderescaray.cents.benchmark

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.github.anderescaray.cents.domain.repository.BackupRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject

/**
 * Replaces whatever the benchmark build holds with [BenchmarkLedger], says so in the log, and
 * closes.
 *
 * Started by the benchmarks before they measure. The log line is the signal they wait for, and
 * the only one: an activity that has gone could equally have crashed, and a benchmark measured
 * against a ledger that never arrived would look fine and mean nothing.
 *
 * It exists only in the benchmark builds, which install under their own application id, so the
 * owner's own data is never anywhere near it.
 */
@AndroidEntryPoint
class SeedActivity : ComponentActivity() {
    @Inject
    lateinit var backups: BackupRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            // Not cancelled with the activity: a restore stopped halfway rolls back, silently.
            val outcome =
                withContext(NonCancellable) {
                    runCatching { backups.restore(BenchmarkLedger.build(LocalDate.now())) }
                }
            outcome
                .onSuccess { Log.i(TAG, SEEDED) }
                .onFailure { Log.e(TAG, FAILED, it) }
            finish()
        }
    }

    private companion object {
        // The benchmarks in :baselineprofile read these from logcat, by the same strings.
        const val TAG = "CentsSeed"
        const val SEEDED = "seeded"
        const val FAILED = "failed"
    }
}
