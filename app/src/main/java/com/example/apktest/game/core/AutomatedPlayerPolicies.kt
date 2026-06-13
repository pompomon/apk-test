package com.example.apktest.game.core

fun automatedPlayerPolicies(): List<PlayerPolicyType> =
    PlayerPolicyType.entries.filter { it != PlayerPolicyType.MANUAL }

fun automatedPlayerPolicies(unlocked: Collection<PlayerPolicyType>): List<PlayerPolicyType> =
    automatedPlayerPolicies().filter { it in unlocked }

/**
 * Static Adventure policy-award order, generated offline from the JVM policy
 * ranking harness and checked in so runtime unlock choices are deterministic
 * and cheap. The benchmark uses the survival-first ordering documented in
 * `docs/player-policy-ranking.md`: success rate first, then fastest successful
 * elapsed time within the same success tier.
 */
fun adventureAwardPlayerPolicyRanking(): List<PlayerPolicyType> = ADVENTURE_AWARD_PLAYER_POLICY_RANKING

private val ADVENTURE_AWARD_PLAYER_POLICY_RANKING = listOf(
    PlayerPolicyType.BFS_EXIT,
    PlayerPolicyType.ASTAR_EXIT,
    PlayerPolicyType.PLEDGE,
    PlayerPolicyType.FLEE_TO_EXIT,
    PlayerPolicyType.WALL_LEFT
)
