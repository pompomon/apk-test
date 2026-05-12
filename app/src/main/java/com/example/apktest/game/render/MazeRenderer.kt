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

        // Floor — slightly darker than before so the brown/green walls pop.
        shapes.color.set(0.08f, 0.08f, 0.10f, 1f)
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

        // Walls are shared between adjacent cells; only render NORTH+WEST per cell
        // and add the matching outer SOUTH/EAST boundary segments to avoid drawing
        // each shared edge twice per frame. Each segment is a thin brick strip
        // textured via WallTextures, with a deterministic moss/bush variant.
        for (y in 0 until maze.height) {
            for (x in 0 until maze.width) {
                if (maze.hasWall(x, y, Direction.NORTH)) {
                    drawHorizontalWall(x, y + 1, WallTextures.variantIndex(x, y, 0))
                }
                if (maze.hasWall(x, y, Direction.WEST)) {
                    drawVerticalWall(x, y, WallTextures.variantIndex(x, y, 1))
                }
                if (y == 0 && maze.hasWall(x, y, Direction.SOUTH)) {
                    drawHorizontalWall(x, 0, WallTextures.variantIndex(x, y, 2))
                }
                if (x == maze.width - 1 && maze.hasWall(x, y, Direction.EAST)) {
                    drawVerticalWall(x + 1, y, WallTextures.variantIndex(x, y, 3))
                }
            }
        }
        shapes.end()
    }

    private fun drawHorizontalWall(cellX: Int, wallY: Int, variantIndex: Int) {
        // Wall runs horizontally along the bottom edge `wallY` of width 1 cell.
        // Centered vertically on wallY with thickness WALL_THICKNESS.
        val pattern = WallTextures.variants[variantIndex]
        val palette = WallTextures.palette
        val rows = pattern.size
        val cols = pattern[0].length
        val originX = mazeOriginX + cellX.toFloat()
        val originY = wallY.toFloat() - WALL_THICKNESS / 2f
        val pixelWidth = 1f / cols
        val pixelHeight = WALL_THICKNESS / rows
        for (row in 0 until rows) {
            val rowPattern = pattern[row]
            for (col in 0 until cols) {
                val ch = rowPattern[col]
                if (ch == ' ' || ch == '0') continue
                val color = palette[ch] ?: continue
                shapes.color = color
                shapes.rect(
                    originX + col * pixelWidth,
                    originY + (rows - row - 1) * pixelHeight,
                    pixelWidth,
                    pixelHeight
                )
            }
        }
    }

    private fun drawVerticalWall(wallX: Int, cellY: Int, variantIndex: Int) {
        // Wall runs vertically along the left edge `wallX` of height 1 cell.
        // Same patterns as horizontal walls but transposed so the brick rows run
        // along the wall length (which is now the Y axis).
        val pattern = WallTextures.variants[variantIndex]
        val palette = WallTextures.palette
        val rows = pattern.size       // brick "depth" → maps to wall thickness (X)
        val cols = pattern[0].length  // brick "length" → maps to wall length (Y)
        val originX = wallX.toFloat() + mazeOriginX - WALL_THICKNESS / 2f
        val originY = cellY.toFloat()
        val pixelWidth = WALL_THICKNESS / rows
        val pixelHeight = 1f / cols
        for (row in 0 until rows) {
            val rowPattern = pattern[row]
            for (col in 0 until cols) {
                val ch = rowPattern[col]
                if (ch == ' ' || ch == '0') continue
                val color = palette[ch] ?: continue
                shapes.color = color
                // Patterns are listed top-down; for a vertical wall, the "top"
                // of the brick (row 0) should be along the outer side. Place
                // row 0 at the larger X so highlights point outward (purely
                // cosmetic; consistent across the maze).
                shapes.rect(
                    originX + (rows - row - 1) * pixelWidth,
                    originY + col * pixelHeight,
                    pixelWidth,
                    pixelHeight
                )
            }
        }
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

    private companion object {
        // Wall thickness as fraction of a cell (world units).
        private const val WALL_THICKNESS = 0.18f
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
