package com.example.apktest.game.core

/** Stable, seed-free effect payload emitted only when a perk changes gameplay. */
data class RunPerkEffectEvent(
    val perkId: RunPerkId,
    val affectedSystem: String,
    val amount: Int
)
