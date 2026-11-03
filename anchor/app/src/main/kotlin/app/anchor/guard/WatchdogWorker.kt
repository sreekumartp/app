package app.anchor.guard

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.anchor.graph
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * NFR-04: OEM battery managers (HyperOS, ColorOS, Funtouch…) kill background services. Every
 * 15 minutes this restarts the guard if needed and logs how long protection was down.
 */
class WatchdogWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val g = applicationContext.graph
        if (!GuardService.running) {
            val last = g.secure.heartbeatMs
            val now = System.currentTimeMillis()
            if (last > 0 && now - last > GAP_THRESHOLD_MS) {
                val fmt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                g.secure.logTamper("gap", "Protection was off from ${fmt.format(Date(last))} to ${fmt.format(Date(now))}")
            }
            GuardService.start(applicationContext)
        }
        return Result.success()
    }

    companion object {
        private const val NAME = "anchor-watchdog"
        private const val GAP_THRESHOLD_MS = 3 * 60_000L

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<WatchdogWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
