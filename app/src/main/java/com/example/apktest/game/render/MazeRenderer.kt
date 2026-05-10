package com.example.apktest.game.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.utils.ScreenUtils
import com.badlogic.gdx.utils.viewport.ExtendViewport
import com.example.apktest.game.core.Direction
import com.example.apktest.game.core.GameEngine
import com.example.apktest.game.core.Maze
import com.example.apktest.game.core.PowerUpType

class MazeRenderer {
    private val camera = OrthographicCamera()
    private var viewport = ExtendViewport(20f, 30f, camera)
    private val shapesDelegate = lazy(LazyThreadSafetyMode.NONE) { ShapeRenderer() }
    private val shapes: ShapeRenderer by shapesDelegate

    private var cachedWidth = -1
    private var cachedHeight = -1
    private var mazeOriginX = 0f

    fun resize(width: Int, height: Int) {
        viewport.update(width, height, true)
    }

    fun render(engine: GameEngine) {
        updateViewportForMaze(engine.maze)
        mazeOriginX = (viewport.worldWidth - engine.maze.width.toFloat()) / 2f

        ScreenUtils.clear(0.08f, 0.08f, 0.1f, 1f)
        viewport.apply(true)
        shapes.projectionMatrix = camera.combined

        drawMaze(engine)
        drawPowerUps(engine)
        drawEntities(engine)
    }

    fun dispose() {
        if (shapesDelegate.isInitialized()) {
            shapes.dispose()
        }
    }

    private fun updateViewportForMaze(maze: Maze) {
        if (maze.width == cachedWidth && maze.height == cachedHeight) return
        cachedWidth = maze.width
        cachedHeight = maze.height
        viewport = ExtendViewport(maze.width.toFloat(), maze.height.toFloat(), camera)
        viewport.update(Gdx.graphics.width, Gdx.graphics.height, true)
    }

    private fun drawMaze(engine: GameEngine) {
        val maze = engine.maze
        shapes.begin(ShapeRenderer.ShapeType.Filled)

        shapes.color.set(0.12f, 0.12f, 0.16f, 1f)
        shapes.rect(mazeOriginX, 0f, maze.width.toFloat(), maze.height.toFloat())

        shapes.color.set(0.2f, 0.8f, 0.3f, 1f)
        shapes.rect(mazeOriginX + maze.exit.x + 0.2f, maze.exit.y + 0.2f, 0.6f, 0.6f)
        shapes.end()

        shapes.begin(ShapeRenderer.ShapeType.Line)
        shapes.color = Color.WHITE

        // Walls are shared between adjacent cells; only render NORTH+WEST per cell
        // and add the matching outer SOUTH/EAST boundary segments to avoid drawing
        // each shared edge twice per frame.
        for (y in 0 until maze.height) {
            for (x in 0 until maze.width) {
                if (maze.hasWall(x, y, Direction.NORTH)) {
                    shapes.line(
                        mazeOriginX + x.toFloat(),
                        (y + 1).toFloat(),
                        mazeOriginX + (x + 1).toFloat(),
                        (y + 1).toFloat()
                    )
                }
                if (maze.hasWall(x, y, Direction.WEST)) {
                    shapes.line(
                        mazeOriginX + x.toFloat(),
                        y.toFloat(),
                        mazeOriginX + x.toFloat(),
                        (y + 1).toFloat()
                    )
                }
                if (y == 0 && maze.hasWall(x, y, Direction.SOUTH)) {
                    shapes.line(
                        mazeOriginX + x.toFloat(),
                        0f,
                        mazeOriginX + (x + 1).toFloat(),
                        0f
                    )
                }
                if (x == maze.width - 1 && maze.hasWall(x, y, Direction.EAST)) {
                    shapes.line(
                        mazeOriginX + (x + 1).toFloat(),
                        y.toFloat(),
                        mazeOriginX + (x + 1).toFloat(),
                        (y + 1).toFloat()
                    )
                }
            }
        }
        shapes.end()
    }

    private fun drawEntities(engine: GameEngine) {
        shapes.begin(ShapeRenderer.ShapeType.Filled)

        val player = engine.player
        shapes.color.set(0.25f, 0.5f, 1f, 1f)
        shapes.circle(mazeOriginX + player.position.x + 0.5f, player.position.y + 0.5f, 0.28f, 24)

        shapes.color.set(0.9f, 0.25f, 0.25f, 1f)
        engine.npcs.forEach { npc ->
            shapes.circle(mazeOriginX + npc.position.x + 0.5f, npc.position.y + 0.5f, 0.24f, 20)
        }

        shapes.end()
    }

    private fun drawPowerUps(engine: GameEngine) {
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        engine.spawnedPowerUpsView.forEach { pickup ->
            PixelPowerUpIconRenderer.draw(
                shapes = shapes,
                type = pickup.type,
                x = mazeOriginX + pickup.position.x + 0.5f,
                y = pickup.position.y + 0.5f,
                size = 0.54f
            )
        }
        shapes.end()
    }

    private object PixelPowerUpIconRenderer {
        private const val GRID_SIZE = 5

        private val darkOutline = Color(0.05f, 0.05f, 0.08f, 1f)

        // Palettes and patterns are pre-built once per type (indexed by ordinal)
        // so the per-frame draw path performs no Color/List allocations.
        // The exhaustive when() during initialization still forces the compiler
        // to flag any newly added PowerUpType.
        private val palettes: Array<Color> = Array(PowerUpType.entries.size) { i ->
            colorFor(PowerUpType.entries[i])
        }

        private val patterns: Array<Array<String>> = Array(PowerUpType.entries.size) { i ->
            patternRowsFor(PowerUpType.entries[i])
        }

        private fun colorFor(type: PowerUpType): Color = when (type) {
            PowerUpType.INVISIBILITY -> Color(0.68f, 0.5f, 0.96f, 1f)
            PowerUpType.TELEPORT -> Color(0.25f, 0.86f, 0.96f, 1f)
            PowerUpType.SPEED_UP -> Color(1f, 0.91f, 0.3f, 1f)
            PowerUpType.FREEZE -> Color(0.63f, 0.9f, 1f, 1f)
            PowerUpType.BLAST -> Color(1f, 0.45f, 0.2f, 1f)
        }

        private fun patternRowsFor(type: PowerUpType): Array<String> = when (type) {
            PowerUpType.INVISIBILITY -> arrayOf(
                "00100",
                "01110",
                "11111",
                "01110",
                "00100"
            )
            PowerUpType.TELEPORT -> arrayOf(
                "11111",
                "10001",
                "10101",
                "10001",
                "11111"
            )
            PowerUpType.SPEED_UP -> arrayOf(
                "00110",
                "01110",
                "11111",
                "01110",
                "00110"
            )
            PowerUpType.FREEZE -> arrayOf(
                "10001",
                "01110",
                "11111",
                "01110",
                "10001"
            )
            PowerUpType.BLAST -> arrayOf(
                "10101",
                "11011",
                "11111",
                "11011",
                "10101"
            )
        }

        fun draw(shapes: ShapeRenderer, type: PowerUpType, x: Float, y: Float, size: Float) {
            val pattern = patterns[type.ordinal]
            val color = palettes[type.ordinal]
            val pixelSize = size / GRID_SIZE
            val originX = x - size / 2f
            val originY = y - size / 2f

            shapes.color = darkOutline
            val outlinePadding = pixelSize * OUTLINE_PADDING_FACTOR
            shapes.rect(
                originX - outlinePadding,
                originY - outlinePadding,
                size + 2f * outlinePadding,
                size + 2f * outlinePadding
            )

            for (row in 0 until GRID_SIZE) {
                val rowPattern = pattern[row]
                for (col in 0 until GRID_SIZE) {
                    if (rowPattern[col] == '1') {
                        shapes.color = color
                        shapes.rect(
                            originX + col * pixelSize,
                            originY + (GRID_SIZE - row - 1) * pixelSize,
                            pixelSize,
                            pixelSize
                        )
                    }
                }
            }
        }

        private const val OUTLINE_PADDING_FACTOR = 0.5f
    }
}
