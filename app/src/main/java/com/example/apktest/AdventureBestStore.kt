package com.example.apktest

import android.content.Context
import java.util.Locale

/**
 * Result returned from [AdventureBestStore.recordCompletedRun].
 *
 * @property previousBestSeconds The previous best time for this difficulty,
 *   or `null` if this was the first completed run.
 * @property currentTimeSeconds The time just recorded.
 * @property isNewBest `true` if [currentTimeSeconds] is now the best time.
 */
data class BestTimeResult(
    val previousBestSeconds: Float?,
    val currentTimeSeconds: Float,
    val isNewBest: Boolean
)

/**
 * Persists the best completed Adventure run time per difficulty using
 * [android.content.SharedPreferences]. Active run stats live in
 * [AdventureStateStore]; this store is kept separate so personal bests
 * survive across multiple Adventure runs and are never cleared by
 * [AdventureStateStore.clear].
 *
 * Only completed (won) runs update the best time — lost runs must never
 * call [recordCompletedRun].
 */
class AdventureBestStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Returns the best completed time in seconds for [difficultyName],
     * or `null` if no run has been completed for that difficulty yet.
     */
    fun bestTimeSeconds(difficultyName: String): Float? {
        val key = prefKey(difficultyName)
        return if (prefs.contains(key)) prefs.getFloat(key, Float.MAX_VALUE) else null
    }

    /**
     * Records a completed run time for [difficultyName]. Replaces the
     * stored best only if [totalElapsedSeconds] is strictly faster (lower).
     * The first completed run always becomes the best.
     *
     * Returns a [BestTimeResult] describing what changed.
     */
    fun recordCompletedRun(
        difficultyName: String,
        totalElapsedSeconds: Float
    ): BestTimeResult {
        val key = prefKey(difficultyName)
        val previous = if (prefs.contains(key)) prefs.getFloat(key, Float.MAX_VALUE) else null
        val isNewBest = previous == null || totalElapsedSeconds < previous
        if (isNewBest) {
            prefs.edit().putFloat(key, totalElapsedSeconds).apply()
        }
        return BestTimeResult(
            previousBestSeconds = previous,
            currentTimeSeconds = totalElapsedSeconds,
            isNewBest = isNewBest
        )
    }

    companion object {
        internal const val PREFS_NAME = "adventure_bests"

        /** Safe SharedPreferences key derived from the difficulty name. */
        internal fun prefKey(difficultyName: String): String =
            "best_time_${difficultyName.lowercase(Locale.US)}"
    }
}
