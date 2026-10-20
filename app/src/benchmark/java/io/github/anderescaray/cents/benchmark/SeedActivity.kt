package io.github.anderescaray.cents.benchmark

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.github.anderescaray.cents.domain.repository.BackupRepository
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * Replaces whatever the benchmark build holds with [BenchmarkLedger], then closes.
 *
 * Started by the benchmarks before they measure (`am start -W -n <app>/.benchmark.SeedActivity`).
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
            backups.restore(BenchmarkLedger.build(LocalDate.now()))
            finish()
        }
    }
}
