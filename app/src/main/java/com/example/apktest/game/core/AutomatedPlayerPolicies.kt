package com.example.apktest.game.core

fun automatedPlayerPolicies(): List<PlayerPolicyType> =
    PlayerPolicyType.entries.filter { it != PlayerPolicyType.MANUAL }

fun automatedPlayerPolicies(unlocked: Collection<PlayerPolicyType>): List<PlayerPolicyType> =
    automatedPlayerPolicies().filter { it in unlocked }
