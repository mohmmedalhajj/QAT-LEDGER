package com.qatledger.licensing

import android.content.Context
import android.os.SystemClock
import java.io.File

/**
 * Offline time-rollback detector for time-limited subscriptions.
 *
 * Technical Transparency (Offline Limitation):
 * Without an external trusted time server, an offline app compares:
 *   1) Wall-clock time (`System.currentTimeMillis()`) against the persisted highest observed wall-clock
 *      watermark and payload `issuedAtEpochMs`.
 *   2) Elapsed monotonic uptime (`SystemClock.elapsedRealtime()`) delta to detect clock rewinds even
 *      when an app restarts quickly after changing the system clock backwards beyond allowed skew.
 * It handles clock tampering strictly by disabling PRO entitlements without touching customer accounting DBs.
 */
class TrustedTimeGuard(
    private val context: Context,
    private val wallClockProvider: () -> Long = { System.currentTimeMillis() },
    private val elapsedRealtimeProvider: () -> Long = { SystemClock.elapsedRealtime() }
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val guardFile = File(context.noBackupFilesDir, GUARD_FILE)

    data class TimeCheckOutcome(
        val isTampered: Boolean,
        val currentWallClockMs: Long,
        val highWaterWallClockMs: Long,
        val detailMessage: String? = null
    )

    fun evaluateAndRecord(
        issuedAtEpochMs: Long,
        maxSkewSeconds: Long
    ): TimeCheckOutcome {
        val nowWall = wallClockProvider()
        val nowElapsed = elapsedRealtimeProvider()
        val allowedSkewMs = maxSkewSeconds.coerceAtLeast(60L) * 1000L

        val storedHighWaterWall = maxOf(
            prefs.getLong(KEY_HIGH_WATER_WALL, 0L),
            readFileHighWater()
        )
        val prevSampleWall = prefs.getLong(KEY_LAST_SAMPLE_WALL, 0L)
        val prevSampleElapsed = prefs.getLong(KEY_LAST_SAMPLE_ELAPSED, 0L)

        // Expected minimum wall clock if elapsedRealtime advanced since last run (same boot session)
        val expectedMinWallSameBoot = if (prevSampleWall > 0L && prevSampleElapsed > 0L && nowElapsed >= prevSampleElapsed) {
            val deltaElapsed = nowElapsed - prevSampleElapsed
            prevSampleWall + deltaElapsed - allowedSkewMs
        } else {
            0L
        }

        val rollbackAgainstIssued = nowWall + allowedSkewMs < issuedAtEpochMs
        val rollbackAgainstHighWater = storedHighWaterWall > 0L && (nowWall + allowedSkewMs < storedHighWaterWall)
        val rollbackAgainstMonotonic = expectedMinWallSameBoot > 0L && nowWall < expectedMinWallSameBoot

        if (rollbackAgainstIssued || rollbackAgainstHighWater || rollbackAgainstMonotonic) {
            prefs.edit().putBoolean(KEY_TAMPER_FLAG, true).apply()
            val effectiveClock = maxOf(storedHighWaterWall, nowWall, expectedMinWallSameBoot)
            return TimeCheckOutcome(
                isTampered = true,
                currentWallClockMs = effectiveClock,
                highWaterWallClockMs = storedHighWaterWall,
                detailMessage = "تم اكتشاف إرجاع ساعة الجهاز للخلف مقارنة بآخر وقت موثق"
            )
        }

        // Advance monotonic high-water mark (never allow it to decrease)
        val nextHighWater = maxOf(storedHighWaterWall, nowWall, issuedAtEpochMs)
        persistHighWater(nextHighWater, nowWall, nowElapsed)

        // Wait until clock recovers past recorded high-water before clearing tamper flag
        val wasTampered = prefs.getBoolean(KEY_TAMPER_FLAG, false)
        if (wasTampered && nowWall >= storedHighWaterWall) {
            prefs.edit().putBoolean(KEY_TAMPER_FLAG, false).apply()
        }

        return TimeCheckOutcome(
            isTampered = false,
            currentWallClockMs = nowWall,
            highWaterWallClockMs = nextHighWater
        )
    }

    fun resetGuardForTestsOnly() {
        prefs.edit().clear().apply()
        runCatching { guardFile.delete() }
    }

    private fun readFileHighWater(): Long {
        return runCatching {
            if (guardFile.exists()) guardFile.readText().trim().toLongOrNull() ?: 0L else 0L
        }.getOrDefault(0L)
    }

    private fun persistHighWater(highWaterWall: Long, sampleWall: Long, sampleElapsed: Long) {
        prefs.edit()
            .putLong(KEY_HIGH_WATER_WALL, highWaterWall)
            .putLong(KEY_LAST_SAMPLE_WALL, sampleWall)
            .putLong(KEY_LAST_SAMPLE_ELAPSED, sampleElapsed)
            .apply()
        runCatching {
            guardFile.parentFile?.mkdirs()
            guardFile.writeText(highWaterWall.toString())
        }
    }

    companion object {
        private const val PREFS_NAME = "qat_license_time_guard"
        private const val GUARD_FILE = "qat_time_highwater.dat"
        private const val KEY_HIGH_WATER_WALL = "hw_wall_ms"
        private const val KEY_LAST_SAMPLE_WALL = "last_sample_wall_ms"
        private const val KEY_LAST_SAMPLE_ELAPSED = "last_sample_elapsed_ms"
        private const val KEY_TAMPER_FLAG = "tamper_detected"
    }
}
