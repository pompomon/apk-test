package com.example.apktest.game.core

class Maze(
    val width: Int,
    val height: Int,
    private val cells: IntArray,
    val start: GridPos,
    val exit: GridPos
) {
    init {
        require(width > 1 && height > 1)
        require(cells.size == width * height)
    }

    fun inBounds(pos: GridPos): Boolean = pos.x in 0 until width && pos.y in 0 until height

    fun hasWall(pos: GridPos, direction: Direction): Boolean {
        val mask = WALL_MASKS.getValue(direction)
        return cells[indexOf(pos)] and mask != 0
    }

    fun hasWall(x: Int, y: Int, direction: Direction): Boolean {
        require(x in 0 until width && y in 0 until height) {
            "Position ($x, $y) is out of maze bounds (${width}x${height})"
        }
        val mask = WALL_MASKS.getValue(direction)
        return cells[y * width + x] and mask != 0
    }

    fun canMove(pos: GridPos, direction: Direction): Boolean {
        val next = pos.moved(direction)
        return inBounds(next) && !hasWall(pos, direction)
    }

    fun neighbors(pos: GridPos): List<GridPos> {
        return Direction.entries
            .filter { canMove(pos, it) }
            .map { pos.moved(it) }
    }

    fun removeWall(pos: GridPos, direction: Direction) {
        val next = pos.moved(direction)
        if (!inBounds(next)) return

        val currentMask = WALL_MASKS.getValue(direction)
        val oppositeMask = WALL_MASKS.getValue(direction.opposite())

        val currentIndex = indexOf(pos)
        val nextIndex = indexOf(next)

        cells[currentIndex] = cells[currentIndex] and currentMask.inv()
        cells[nextIndex] = cells[nextIndex] and oppositeMask.inv()
    }

    fun copyCells(): IntArray = cells.copyOf()

    private fun indexOf(pos: GridPos): Int = pos.y * width + pos.x

    companion object {
        const val WALL_NORTH = 1
        const val WALL_EAST = 1 shl 1
        const val WALL_SOUTH = 1 shl 2
        const val WALL_WEST = 1 shl 3
        const val ALL_WALLS = WALL_NORTH or WALL_EAST or WALL_SOUTH or WALL_WEST

        val WALL_MASKS = mapOf(
            Direction.NORTH to WALL_NORTH,
            Direction.EAST to WALL_EAST,
            Direction.SOUTH to WALL_SOUTH,
            Direction.WEST to WALL_WEST
        )

        fun openGrid(width: Int, height: Int): Maze {
            val cells = IntArray(width * height) { 0 }
            return Maze(width, height, cells, GridPos(0, 0), GridPos(width - 1, height - 1))
        }
    }
}
