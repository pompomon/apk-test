package com.example.apktest.game.core

data class Player(
    var position: GridPos,
    var facing: Direction = Direction.EAST,
    var animationFrame: Int = 0,
    var lastMoveAtSeconds: Float = 0f
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
    var lastMoveAtSeconds: Float = 0f
)

enum class GameStatus {
    RUNNING,
    PAUSED,
    WIN,
    LOSE
}
