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

        // Render the exit as a wooden door sprite centered on the exit cell.
        PixelSpriteRenderer.draw(
            shapes = shapes,
            pattern = Sprites.exitDoor,
            palette = Sprites.doorPalette(),
            centerX = mazeOriginX + maze.exit.x + 0.5f,
            centerY = maze.exit.y + 0.5f,
            size = 0.9f
        )
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
        PixelSpriteRenderer.draw(
            shapes = shapes,
            pattern = Sprites.hero,
            palette = Sprites.heroPalette(),
            centerX = mazeOriginX + player.position.x + 0.5f,
            centerY = player.position.y + 0.5f,
            size = 0.78f
        )

        engine.npcs.forEach { npc ->
            PixelSpriteRenderer.draw(
                shapes = shapes,
                pattern = Sprites.monster,
                palette = Sprites.monsterPalette(),
                centerX = mazeOriginX + npc.position.x + 0.5f,
                centerY = npc.position.y + 0.5f,
                size = 0.72f
            )
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
        // so the per-frame draw path performs no Color/List allocations. Sharing
        // the data via PowerUpIcons keeps the legend UI and the game in sync
        // when power-ups are added or restyled.
        private val palettes: Array<Color> = Array(PowerUpType.entries.size) { i ->
            PowerUpIcons.gdxColorFor(PowerUpType.entries[i])
        }

        private val patterns: Array<Array<String>> = Array(PowerUpType.entries.size) { i ->
            PowerUpIcons.patternFor(PowerUpType.entries[i])
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
