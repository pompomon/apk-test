package com.example.apktest.game.core

/** Stable catalogue metadata; Android owns localized labels and descriptions. */
enum class EliteNpcModifier(
    val id: String,
    val accentRgb: Triple<Float, Float, Float>
) {
    TRACKER("tracker", Triple(0.25f, 0.95f, 0.95f));

    fun supports(policy: NpcPolicyType): Boolean = when (this) {
        TRACKER -> policy == NpcPolicyType.PATROL_GUARD
    }

    val visionRangeBonus: Int
        get() = when (this) {
            TRACKER -> 2
        }

    companion object {
        fun fromId(id: String): EliteNpcModifier? = entries.firstOrNull { it.id == id }
    }
}

/** Ordered by spawn id, never by the current position of an NPC. */
data class NpcSpawnSpec(
    val policyType: NpcPolicyType,
    val eliteModifier: EliteNpcModifier? = null
) {
    init {
        require(eliteModifier == null || eliteModifier.supports(policyType)) {
            "Elite modifier is incompatible with NPC policy"
        }
    }
}
