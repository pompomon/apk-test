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

    fun hasSavedState(): Boolean = prefs.contains(KEY_STATE_JSON)

    fun clear() {
        prefs.edit().remove(KEY_STATE_JSON).apply()
    }

    companion object {
        private const val PREFS_NAME = "maze_state"
        private const val KEY_STATE_JSON = "state_json"
    }
}
