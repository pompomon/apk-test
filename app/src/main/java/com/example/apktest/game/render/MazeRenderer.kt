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
    private var mazeOriginY = 0f

    // Static wall geometry cache, rebuilt only when the active Maze changes.
    // Each pixel of the brick pattern is a rect in maze-local coords (x is
    // relative to the maze's left edge, mazeOriginX is added at draw time).
    // This keeps per-frame work to O(wallPixels) shapes.rect calls with no
    // map lookups, no allocations, and no character-by-character branching.
    private var cachedWallMaze: Maze? = null
    private var cachedWallRevision: Int = -1
    private var wallRectX: FloatArray = FloatArray(0)
    private var wallRectY: FloatArray = FloatArray(0)
    private var wallRectW: FloatArray = FloatArray(0)
    private var wallRectH: FloatArray = FloatArray(0)
    private var wallRectColor: Array<Color?> = emptyArray()
    private var wallRectCount: Int = 0

    fun resize(width: Int, height: Int) {
        viewport.update(width, height, true)
    }

    fun render(engine: GameEngine) {
        updateViewportForMaze(engine.maze)
        mazeOriginX = (viewport.worldWidth - engine.maze.width.toFloat()) / 2f
        mazeOriginY = (viewport.worldHeight - engine.maze.height.toFloat()) / 2f

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
        if (cachedWallMaze !== maze || cachedWallRevision != maze.revision) {
            rebuildWallGeometry(maze)
            cachedWallMaze = maze
            cachedWallRevision = maze.revision
        }

        shapes.begin(ShapeRenderer.ShapeType.Filled)

        // Floor — slightly darker than before so the brown/green walls pop.
        shapes.color.set(0.08f, 0.08f, 0.10f, 1f)
        shapes.rect(mazeOriginX, mazeOriginY, maze.width.toFloat(), maze.height.toFloat())

        // Render the exit as a wooden door sprite centered on the exit cell.
        PixelSpriteRenderer.draw(
            shapes = shapes,
            pattern = Sprites.exitDoor,
            palette = Sprites.doorPalette(),
            centerX = mazeOriginX + maze.exit.x + 0.5f,
            centerY = mazeOriginY + maze.exit.y + 0.5f,
            size = 0.9f
        )

        // Walls: replay the precomputed brick rects. mazeOriginX/Y are added on
        // the fly because the viewport (and therefore the origin) can shift with
        // screen size while the geometry stays valid.
        val count = wallRectCount
        val xs = wallRectX
        val ys = wallRectY
        val ws = wallRectW
        val hs = wallRectH
        val cs = wallRectColor
        val ox = mazeOriginX
        val oy = mazeOriginY
        for (i in 0 until count) {
            shapes.color = cs[i]!!
            shapes.rect(ox + xs[i], oy + ys[i], ws[i], hs[i])
        }
        shapes.end()
    }

    /**
     * Walks every wall edge and expands its brick pattern into a flat list of
     * coloured rects, stored in maze-local coordinates. Called whenever the
     * active [Maze] instance changes (start of game / restart) **or** its
     * [Maze.revision] counter advances after an in-place wall mutation (e.g.
     * the BLAST power-up calling [Maze.removeWall]). The per-frame draw loop
     * pays no pattern-decoding cost while the cache is hot.
     */
    private fun rebuildWallGeometry(maze: Maze) {
        // Upper-bound the scratch arrays so the append loop is branch-free
        // (each cell can contribute up to 4 segments * rows*cols pixels), then
        // trim to the actually-used length so the steady-state cache only
        // holds the pixels we'll iterate every frame.
        val capacity = maze.width * maze.height * 4 * WALL_PATTERN_PIXELS_PER_SEGMENT
        val xs = FloatArray(capacity)
        val ys = FloatArray(capacity)
        val ws = FloatArray(capacity)
        val hs = FloatArray(capacity)
        val cs = arrayOfNulls<Color>(capacity)
        var n = 0

        for (y in 0 until maze.height) {
            for (x in 0 until maze.width) {
                if (maze.hasWall(x, y, Direction.NORTH)) {
                    n = appendHorizontalWall(
                        xs, ys, ws, hs, cs, n,
                        cellX = x, wallY = y + 1,
                        variantIndex = WallTextures.variantIndex(x, y, WallTextures.DIR_NORTH)
                    )
                }
                if (maze.hasWall(x, y, Direction.WEST)) {
                    n = appendVerticalWall(
                        xs, ys, ws, hs, cs, n,
                        wallX = x, cellY = y,
                        variantIndex = WallTextures.variantIndex(x, y, WallTextures.DIR_WEST)
                    )
                }
                if (y == 0 && maze.hasWall(x, y, Direction.SOUTH)) {
                    n = appendHorizontalWall(
                        xs, ys, ws, hs, cs, n,
                        cellX = x, wallY = 0,
                        variantIndex = WallTextures.variantIndex(x, y, WallTextures.DIR_SOUTH)
                    )
                }
                if (x == maze.width - 1 && maze.hasWall(x, y, Direction.EAST)) {
                    n = appendVerticalWall(
                        xs, ys, ws, hs, cs, n,
                        wallX = x + 1, cellY = y,
                        variantIndex = WallTextures.variantIndex(x, y, WallTextures.DIR_EAST)
                    )
                }
            }
        }

        // Trim to actual size so the cache doesn't permanently hold the
        // upper-bound slack from sparse mazes.
        wallRectX = xs.copyOf(n)
        wallRectY = ys.copyOf(n)
        wallRectW = ws.copyOf(n)
        wallRectH = hs.copyOf(n)
        wallRectColor = cs.copyOf(n)
        wallRectCount = n
    }

    private fun appendHorizontalWall(
        xs: FloatArray, ys: FloatArray, ws: FloatArray, hs: FloatArray,
        cs: Array<Color?>, startIndex: Int,
        cellX: Int, wallY: Int, variantIndex: Int
    ): Int {
        val colors = WallTextures.variantColors[variantIndex]
        val rows = colors.size
        val cols = colors[0].size
        val originX = cellX.toFloat()
        val originY = wallY.toFloat() - WALL_THICKNESS / 2f
        val pixelWidth = 1f / cols
        val pixelHeight = WALL_THICKNESS / rows
        var n = startIndex
        for (row in 0 until rows) {
            val rowColors = colors[row]
            val py = originY + (rows - row - 1) * pixelHeight
            for (col in 0 until cols) {
                val color = rowColors[col] ?: continue
                xs[n] = originX + col * pixelWidth
                ys[n] = py
                ws[n] = pixelWidth
                hs[n] = pixelHeight
                cs[n] = color
                n++
            }
        }
        return n
    }

    private fun appendVerticalWall(
        xs: FloatArray, ys: FloatArray, ws: FloatArray, hs: FloatArray,
        cs: Array<Color?>, startIndex: Int,
        wallX: Int, cellY: Int, variantIndex: Int
    ): Int {
        val colors = WallTextures.variantColors[variantIndex]
        val rows = colors.size       // brick "depth" → maps to wall thickness (X)
        val cols = colors[0].size    // brick "length" → maps to wall length (Y)
        val originX = wallX.toFloat() - WALL_THICKNESS / 2f
        val originY = cellY.toFloat()
        val pixelWidth = WALL_THICKNESS / rows
        val pixelHeight = 1f / cols
        var n = startIndex
        for (row in 0 until rows) {
            val rowColors = colors[row]
            val px = originX + (rows - row - 1) * pixelWidth
            for (col in 0 until cols) {
                val color = rowColors[col] ?: continue
                xs[n] = px
                ys[n] = originY + col * pixelHeight
                ws[n] = pixelWidth
                hs[n] = pixelHeight
                cs[n] = color
                n++
            }
        }
        return n
    }

    private fun drawEntities(engine: GameEngine) {
        shapes.begin(ShapeRenderer.ShapeType.Filled)

        val player = engine.player
        val playerPattern = pickFrame(
            frames = Sprites.heroFrames,
            elapsedSeconds = engine.elapsedSeconds,
            lastMoveAtSeconds = player.lastMoveAtSeconds,
            animationFrame = player.animationFrame
        )
        PixelSpriteRenderer.draw(
            shapes = shapes,
            pattern = playerPattern,
            palette = Sprites.heroPalette(),
            centerX = mazeOriginX + player.position.x + 0.5f,
            centerY = mazeOriginY + player.position.y + 0.5f,
            size = 0.78f
        )

        engine.npcs.forEach { npc ->
            val npcPattern = pickFrame(
                frames = Sprites.monsterFrames,
                elapsedSeconds = engine.elapsedSeconds,
                lastMoveAtSeconds = npc.lastMoveAtSeconds,
                animationFrame = npc.animationFrame
            )
            PixelSpriteRenderer.draw(
                shapes = shapes,
                pattern = npcPattern,
                palette = Sprites.monsterPalette(),
                centerX = mazeOriginX + npc.position.x + 0.5f,
                centerY = mazeOriginY + npc.position.y + 0.5f,
                size = 0.72f
            )
        }

        shapes.end()
    }

    /**
     * Pick the active animation frame. Entities that haven't moved in
     * [GameEngine.ANIMATION_IDLE_THRESHOLD_SECONDS] are drawn as the idle
     * frame; otherwise the renderer alternates between the two step frames
     * based on the entity's [animationFrame] counter (which only advances on
     * successful moves) so the legs actually swap between consecutive steps.
     */
    private fun pickFrame(
        frames: Array<Array<String>>,
        elapsedSeconds: Float,
        lastMoveAtSeconds: Float,
        animationFrame: Int
    ): Array<String> {
        if (elapsedSeconds - lastMoveAtSeconds > GameEngine.ANIMATION_IDLE_THRESHOLD_SECONDS) {
            return frames[0]
        }
        return frames[1 + (animationFrame % 2)]
    }

    private fun drawPowerUps(engine: GameEngine) {
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        engine.spawnedPowerUpsView.forEach { pickup ->
            PixelPowerUpIconRenderer.draw(
                shapes = shapes,
                type = pickup.type,
                x = mazeOriginX + pickup.position.x + 0.5f,
                y = mazeOriginY + pickup.position.y + 0.5f,
                size = 0.54f
            )
        }
        shapes.end()
    }

    private companion object {
        // Wall thickness as fraction of a cell (world units).
        private const val WALL_THICKNESS = 0.18f

        // Upper-bound pixel count per wall segment (4 rows * 8 cols brick tile).
        private const val WALL_PATTERN_PIXELS_PER_SEGMENT = 32
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
