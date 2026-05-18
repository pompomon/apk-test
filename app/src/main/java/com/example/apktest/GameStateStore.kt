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

    /**
     * Returns the parsed snapshot, or `null` if no payload exists or the
     * payload is unreadable/stale. Stale blobs (e.g., post-schema bump)
     * are dropped from preferences as a side effect so they don't keep
     * "Resume" enabled or fall through to a default new run.
     */
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
     * Cheap presence check — only verifies that a snapshot key exists in
     * preferences, without parsing/validating the JSON payload. Suitable
     * for UI affordances (e.g., enabling a "Resume" button on the start
     * menu) that should not pay the JSON-parse cost on the UI thread.
     *
     * The payload is validated and stale blobs are dropped lazily by
     * [load] when the user actually requests a resume.
     */
    fun hasRawState(): Boolean = prefs.contains(KEY_STATE_JSON)

    fun clear() {
        prefs.edit().remove(KEY_STATE_JSON).apply()
    }

    /**
     * Like [clear] but uses [android.content.SharedPreferences.Editor.commit]
     * so the removal is guaranteed to be flushed to disk before this call
     * returns. Use this from terminal-state ("Pause & Exit" / WIN / LOSE)
     * flows where the activity is about to be finished and a deferred
     * [apply] write could be lost — otherwise Resume could resurrect an
     * already-finished game on next launch. The caller is responsible
     * for invoking this off the UI thread when possible.
     */
    fun clearBlocking(): Boolean {
        return prefs.edit().remove(KEY_STATE_JSON).commit()
    }

    companion object {
        private const val PREFS_NAME = "maze_state"
        private const val KEY_STATE_JSON = "state_json"
    }
}
