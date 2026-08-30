package com.example.apktest.game.core

/**
 * Internal rollout gates for planned Adventure features.
 *
 * Disabling a gate must stop new content generation only. Once later phases
 * add persisted effects, compatible effects already committed to a run must
 * still be honored so a rollback cannot silently change an in-progress maze.
 */
internal object AdventureFeatureFlags {
    const val ROUTE_EVENTS_ENABLED = false
    const val ELITE_NPC_MODIFIERS_ENABLED = false
    const val RUN_BUILD_PERKS_ENABLED = false
}
