package com.example.apktest

import android.content.Context
import com.example.apktest.game.core.AdventureRunStateSnapshot

/**
 * Sibling of [GameStateStore] for [AdventureRunStateSnapshot]. Uses a
 * separate SharedPreferences file so a saved Adventure run never appears
 * as a single-maze "Resume" on the existing start menu, and vice versa.
 *
 * Bumping [AdventureRunStateSnapshot.SCHEMA_VERSION] in code transparently
 * invalidates any stale payload via the version check in
 * [AdventureRunStateSnapshot.fromJson].
 */
class AdventureStateStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(snapshot: AdventureRunStateSnapshot) {
        prefs.edit().putString(KEY_STATE_JSON, snapshot.toJson()).apply()
    }

    /**
     * Like [save] but uses [android.content.SharedPreferences.Editor.commit]
     * so the snapshot is guaranteed to be on disk before this call
     * returns. Use this from Pause &amp; Exit flows where the activity is
     * about to be finished and a deferred [apply] write could be lost.
     * Caller is responsible for invoking this off the UI thread.
     */
    fun saveBlocking(snapshot: AdventureRunStateSnapshot): Boolean {
        return prefs.edit().putString(KEY_STATE_JSON, snapshot.toJson()).commit()
    }

    /**
     * Returns the parsed snapshot, or `null` if no payload exists or the
     * payload is unreadable / stale (schema bump, corruption). Stale
     * blobs are dropped from preferences as a side effect.
     */
    fun load(): AdventureRunStateSnapshot? {
        val json = prefs.getString(KEY_STATE_JSON, null) ?: return null
        val snapshot = AdventureRunStateSnapshot.fromJson(json)
        if (snapshot == null) {
            clear()
        }
        return snapshot
    }

    /**
     * Cheap presence check — only verifies the snapshot key exists,
     * without parsing the JSON payload. Suitable for UI affordances
     * (e.g., enabling "Resume Adventure") that should not pay the
     * JSON-parse cost on the UI thread. Validation happens lazily in
     * [load].
     */
    fun hasRawState(): Boolean = prefs.contains(KEY_STATE_JSON)

    fun clear() {
        prefs.edit().remove(KEY_STATE_JSON).apply()
    }

    /** Commit()-based clear; mirrors [GameStateStore.clearBlocking]. */
    fun clearBlocking(): Boolean {
        return prefs.edit().remove(KEY_STATE_JSON).commit()
    }

    companion object {
        private const val PREFS_NAME = "adventure_state"
        private const val KEY_STATE_JSON = "state_json"
    }
}
