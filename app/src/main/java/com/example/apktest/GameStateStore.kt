package com.example.apktest

import android.content.Context
import com.example.apktest.game.core.GameEngineSnapshot

/**
 * Tiny SharedPreferences-backed persistence layer for [GameEngineSnapshot].
 * Storing the whole snapshot in a single JSON string means we never have to
 * migrate individual fields independently — bumping
 * [GameEngineSnapshot.SCHEMA_VERSION] in code transparently invalidates any
 * stale payload via the version check in [GameEngineSnapshot.fromJson].
 */
class GameStateStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(snapshot: GameEngineSnapshot) {
        prefs.edit().putString(KEY_STATE_JSON, snapshot.toJson()).apply()
    }

    /**
     * Like [save] but uses [android.content.SharedPreferences.Editor.commit]
     * so the snapshot is guaranteed to be flushed to disk before this call
     * returns. Use this from explicit "Pause & Exit" flows where the
     * activity is about to be finished and a deferred [apply] write could
     * be lost. The caller is responsible for invoking this off the UI
     * thread when possible — [commit] performs synchronous disk I/O.
     */
    fun saveBlocking(snapshot: GameEngineSnapshot): Boolean {
        return prefs.edit().putString(KEY_STATE_JSON, snapshot.toJson()).commit()
    }

    fun load(): GameEngineSnapshot? {
        val json = prefs.getString(KEY_STATE_JSON, null) ?: return null
        val snapshot = GameEngineSnapshot.fromJson(json)
        if (snapshot == null) {
            // Stale / unreadable payload (e.g., schema bump). Drop it so we
            // don't keep re-reading the bad blob.
            clear()
        }
        return snapshot
    }

    /**
     * Returns the raw saved JSON string without the cost of re-serialising,
     * but only after validating the payload via [GameEngineSnapshot.fromJson]
     * so unreadable/stale snapshots are dropped consistently with [load]
     * (a corrupt blob would otherwise keep "Resume" enabled and silently
     * fall through to a default new run in the fragment).
     */
    fun loadRawJson(): String? {
        val json = prefs.getString(KEY_STATE_JSON, null) ?: return null
        if (GameEngineSnapshot.fromJson(json) == null) {
            clear()
            return null
        }
        return json
    }

    fun hasSavedState(): Boolean = load() != null

    fun clear() {
        prefs.edit().remove(KEY_STATE_JSON).apply()
    }

    companion object {
        private const val PREFS_NAME = "maze_state"
        private const val KEY_STATE_JSON = "state_json"
    }
}
