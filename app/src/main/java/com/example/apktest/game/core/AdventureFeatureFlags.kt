package com.example.apktest.game.core

/**
 * Defaults for planned Adventure features. Routes can also be opted into per run.
 *
 * Disabling generation must stop new content only. Once later phases
 * add persisted effects, compatible effects already committed to a run must
 * still be honored so a rollback cannot silently change an in-progress maze.
 */
internal object AdventureFeatureFlags {
    const val ROUTE_EVENTS_ENABLED = false
    const val ELITE_NPC_MODIFIERS_ENABLED = false
    const val RUN_BUILD_PERKS_ENABLED = false
}
