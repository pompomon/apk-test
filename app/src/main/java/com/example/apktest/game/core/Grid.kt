package com.example.apktest.game.core

data class GridPos(val x: Int, val y: Int) {
    fun moved(direction: Direction): GridPos = GridPos(x + direction.dx, y + direction.dy)
}

enum class Direction(val dx: Int, val dy: Int) {
    NORTH(0, -1),
    EAST(1, 0),
    SOUTH(0, 1),
    WEST(-1, 0);

    fun left(): Direction = values()[(ordinal + 3) % values().size]
    fun right(): Direction = values()[(ordinal + 1) % values().size]
    fun opposite(): Direction = values()[(ordinal + 2) % values().size]

    companion object {
        fun fromDelta(dx: Int, dy: Int): Direction? = values().firstOrNull { it.dx == dx && it.dy == dy }
    }
}
