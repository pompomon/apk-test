package com.example.apktest.game.core

data class Player(
    var position: GridPos,
    var facing: Direction = Direction.EAST,
    var animationFrame: Int = 0,
    // Sentinel "never moved": NEGATIVE_INFINITY ensures the renderer's
    // `elapsedSeconds - lastMoveAtSeconds > IDLE_THRESHOLD` check is true at
    // t=0, so newly-spawned entities start in the idle frame rather than
    // accidentally being drawn mid-step.
    var lastMoveAtSeconds: Float = Float.NEGATIVE_INFINITY
)

enum class NpcState {
    PATROL,
    CHASE,
    SEARCH
}

data class Npc(
    val id: Int,
    var position: GridPos,
    var facing: Direction = Direction.WEST,
    var state: NpcState = NpcState.PATROL,
    val patrolRoute: List<GridPos> = emptyList(),
    var patrolIndex: Int = 0,
    var lastKnownPlayerPos: GridPos? = null,
    var searchTicksRemaining: Int = 0,
    var animationFrame: Int = 0,
    // See Player.lastMoveAtSeconds for the sentinel rationale.
    var lastMoveAtSeconds: Float = Float.NEGATIVE_INFINITY
)

enum class GameStatus {
    RUNNING,
    PAUSED,
    WIN,
    LOSE
}
