package com.example.apktest.game.core

import org.junit.Assert.assertFalse
import org.junit.Test

class AdventureFeatureFlagsTest {

    @Test
    fun productionDefaults_disablePlannedAdventureFeatures() {
        assertFalse(AdventureFeatureFlags.ROUTE_EVENTS_ENABLED)
        assertFalse(AdventureFeatureFlags.ELITE_NPC_MODIFIERS_ENABLED)
        assertFalse(AdventureFeatureFlags.RUN_BUILD_PERKS_ENABLED)
    }
}
