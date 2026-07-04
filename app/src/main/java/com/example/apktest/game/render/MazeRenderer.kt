package com.example.apktest.game.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.utils.ScreenUtils
import com.badlogic.gdx.utils.viewport.ExtendViewport
import com.example.apktest.game.core.Direction
import com.example.apktest.game.core.GameEngine
import com.example.apktest.game.core.Maze
import com.example.apktest.game.core.PowerUpEffectKind
import com.example.apktest.game.core.PowerUpType
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

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

    // Static floor geometry cache. Keyed by maze identity (size); revision
    // is not needed because BLAST removes walls but never changes which cells
    // exist. The floor pattern is a 4×4 (`FloorTextures.TILE_SIZE`) tile
    // dominated by `FloorTextures.base` (12 of the 16 pixels per tile), so
    // the base layer is drawn as a single maze-sized rect and only the
    // accent / highlight pattern pixels (4 of the 16 per tile) are stored
    // here, grouped by colour to keep `shapes.color` reassignments to one
    // per group (instead of one per rect) and to cut per-frame `shapes.rect`
    // calls by ~4× (W×H×4 non-base rects + 1 base rect vs the previous
    // W×H×16 rects), where W/H are the generated maze dimensions (after
    // `MazeGenerator` rounds the preset width/height up to even). Stored in
    // maze-local coords
    // so origin shifts (viewport resize) require no rebuild.
    private var cachedFloorMaze: Maze? = null
    private var floorAccentX: FloatArray = FloatArray(0)
    private var floorAccentY: FloatArray = FloatArray(0)
    private var floorAccentCount: Int = 0
    private var floorHighlightX: FloatArray = FloatArray(0)
    private var floorHighlightY: FloatArray = FloatArray(0)
    private var floorHighlightCount: Int = 0
    private var floorPixelSize: Float = 0f
    private val activePlayerTintColors: Array<Color> = Array(TIMED_POWER_UP_COUNT) { Color.WHITE }

    fun resize(width: Int, height: Int) {
        viewport.update(width, height, true)
    }

    fun render(engine: GameEngine) {
        updateViewportForMaze(engine.maze)
        mazeOriginX = (viewport.worldWidth - engine.maze.width.toFloat()) / 2f
        mazeOriginY = (viewport.worldHeight - engine.maze.height.toFloat()) / 2f

        // Letterbox colour: pick something close to the floor base so any
        // viewport bars look intentional rather than like a black void.
        ScreenUtils.clear(0.06f, 0.07f, 0.10f, 1f)
        viewport.apply(true)
        shapes.projectionMatrix = camera.combined

        drawMaze(engine)
        drawPowerUps(engine)
        drawEntities(engine)
        drawCountdownOverlay(engine)
        drawEndOverlay(engine)
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
        if (cachedFloorMaze !== maze) {
            rebuildFloorGeometry(maze)
            cachedFloorMaze = maze
        }

        shapes.begin(ShapeRenderer.ShapeType.Filled)

        // Floor base: one maze-sized rect (covers the dominant base colour
        // for every cell). Accent/highlight pattern pixels are overlaid in
        // a single colour-set + run per group below, so the per-frame
        // floor cost is `1 + 2 + accentCount + highlightCount` rects with
        // only 3 `shapes.color` assignments total (down from one per
        // pattern pixel under the previous per-rect colour scheme).
        val ox = mazeOriginX
        val oy = mazeOriginY
        shapes.color = FloorTextures.base
        shapes.rect(ox, oy, maze.width.toFloat(), maze.height.toFloat())

        val pixelSize = floorPixelSize
        val accentCount = floorAccentCount
        if (accentCount > 0) {
            shapes.color = FloorTextures.accent
            val axs = floorAccentX
            val ays = floorAccentY
            for (i in 0 until accentCount) {
                shapes.rect(ox + axs[i], oy + ays[i], pixelSize, pixelSize)
            }
        }
        val highlightCount = floorHighlightCount
        if (highlightCount > 0) {
            shapes.color = FloorTextures.highlight
            val hxs = floorHighlightX
            val hys = floorHighlightY
            for (i in 0 until highlightCount) {
                shapes.rect(ox + hxs[i], oy + hys[i], pixelSize, pixelSize)
            }
        }
        val mazeTintType = engine.npcMazeTintType
        val exitCenterX = mazeOriginX + maze.exit.x + 0.5f
        val exitCenterY = mazeOriginY + maze.exit.y + 0.5f
        // The optional maze tint and the exit glow are both translucent and
        // need GL blending. Draw them as a single blended segment inside this
        // open Filled batch (flush + enable blend + draw + flush + disable)
        // instead of ending/reopening the batch for each, so floor, overlays,
        // exit, and walls all render in one ShapeRenderer batch per frame.
        drawBlendedMazeOverlays(
            tintType = mazeTintType,
            x = ox,
            y = oy,
            width = maze.width.toFloat(),
            height = maze.height.toFloat(),
            exitCenterX = exitCenterX,
            exitCenterY = exitCenterY
        )

        // Render the exit as a neon portal sprite centered on the exit cell.
        PixelSpriteRenderer.draw(
            shapes = shapes,
            pattern = Sprites.exitDoor,
            palette = Sprites.doorPalette(),
            centerX = exitCenterX,
            centerY = exitCenterY,
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
        for (i in 0 until count) {
            shapes.color = cs[i]!!
            shapes.rect(ox + xs[i], oy + ys[i], ws[i], hs[i])
        }
        shapes.end()
    }

    /**
     * Build per-pixel floor geometry for every cell of [maze]. Each cell is
     * 1 world unit wide and tiled with the [FloorTextures] pattern; since
     * every cell uses the same tile, the pattern is seamless across cell
     * borders by construction. Only the non-base pattern pixels are
     * stored — the base colour is rendered as a single maze-sized rect at
     * draw time — and the accent / highlight rects are grouped into
     * separate arrays so the draw loop changes [ShapeRenderer.color] just
     * once per group. Stored in maze-local coords so viewport resize
     * doesn't trigger a rebuild.
     */
    private fun rebuildFloorGeometry(maze: Maze) {
        val tile = FloorTextures.TILE_SIZE
        val pixelSize = 1f / tile

        // Tally non-base pixels per tile to size the per-colour arrays
        // exactly (no copy-on-trim).
        var accentPerTile = 0
        var highlightPerTile = 0
        for (row in 0 until tile) {
            val rowColors = FloorTextures.pixelColors[row]
            for (col in 0 until tile) {
                when (rowColors[col]) {
                    FloorTextures.accent -> accentPerTile++
                    FloorTextures.highlight -> highlightPerTile++
                    // base pixels are covered by the maze-sized base rect.
                }
            }
        }
        val cellCount = maze.width * maze.height
        val accentX = FloatArray(cellCount * accentPerTile)
        val accentY = FloatArray(cellCount * accentPerTile)
        val highlightX = FloatArray(cellCount * highlightPerTile)
        val highlightY = FloatArray(cellCount * highlightPerTile)
        var ai = 0
        var hi = 0
        for (cellY in 0 until maze.height) {
            for (cellX in 0 until maze.width) {
                for (row in 0 until tile) {
                    // row 0 is the top of the pattern (top of cell). Y-up
                    // means top is at the larger world Y.
                    val py = cellY.toFloat() + (tile - row - 1) * pixelSize
                    val rowColors = FloorTextures.pixelColors[row]
                    for (col in 0 until tile) {
                        val px = cellX.toFloat() + col * pixelSize
                        when (rowColors[col]) {
                            FloorTextures.accent -> {
                                accentX[ai] = px
                                accentY[ai] = py
                                ai++
                            }
                            FloorTextures.highlight -> {
                                highlightX[hi] = px
                                highlightY[hi] = py
                                hi++
                            }
                            // base pixels are covered by the maze-sized base rect.
                        }
                    }
                }
            }
        }
        floorAccentX = accentX
        floorAccentY = accentY
        floorAccentCount = ai
        floorHighlightX = highlightX
        floorHighlightY = highlightY
        floorHighlightCount = hi
        floorPixelSize = pixelSize
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
        val playerTintCount = collectActivePlayerTintColors(engine)
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
            size = 0.78f,
            tintColors = activePlayerTintColors,
            tintCount = playerTintCount
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
                palette = Sprites.monsterPaletteFor(npc.policyType),
                centerX = mazeOriginX + npc.position.x + 0.5f,
                centerY = mazeOriginY + npc.position.y + 0.5f,
                size = 0.72f
            )
        }

        shapes.end()
    }

    private fun collectActivePlayerTintColors(engine: GameEngine): Int {
        var count = 0
        for (type in PLAYER_TINT_TYPES) {
            if (engine.isPlayerPowerUpTintActive(type)) {
                activePlayerTintColors[count] = POWER_UP_TINT_COLORS[type.ordinal]
                count++
            }
        }
        return count
    }

    /**
     * Draws the translucent maze-tint overlay (when active) and the exit glow
     * as a single blended segment inside the caller's already-open Filled
     * batch. The opaque geometry buffered so far is flushed first (with
     * blending still disabled), then GL blending is enabled for the translucent
     * shapes and flushed, then disabled again — so the caller can keep
     * appending opaque exit/wall geometry to the same batch without any nested
     * begin/end. This avoids the per-frame batch churn of ending and reopening
     * the ShapeRenderer once per translucent overlay.
     */
    private fun drawBlendedMazeOverlays(
        tintType: PowerUpType?,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        exitCenterX: Float,
        exitCenterY: Float
    ) {
        // Flush the opaque floor verts before enabling blending so they are
        // rendered with the correct (blend-disabled) GL state.
        shapes.flush()
        try {
            Gdx.gl.glEnable(GL20.GL_BLEND)
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
            if (tintType != null) {
                val tint = POWER_UP_TINT_COLORS[tintType.ordinal]
                shapes.setColor(tint.r, tint.g, tint.b, PowerUpTinting.MAZE_TINT_ALPHA)
                shapes.rect(x, y, width, height)
            }
            shapes.setColor(
                EXIT_GLOW_OUTER_COLOR.r,
                EXIT_GLOW_OUTER_COLOR.g,
                EXIT_GLOW_OUTER_COLOR.b,
                EXIT_GLOW_OUTER_ALPHA
            )
            shapes.circle(exitCenterX, exitCenterY, EXIT_GLOW_OUTER_RADIUS, EXIT_GLOW_SEGMENTS)
            shapes.setColor(
                EXIT_GLOW_INNER_COLOR.r,
                EXIT_GLOW_INNER_COLOR.g,
                EXIT_GLOW_INNER_COLOR.b,
                EXIT_GLOW_INNER_ALPHA
            )
            shapes.circle(exitCenterX, exitCenterY, EXIT_GLOW_INNER_RADIUS, EXIT_GLOW_SEGMENTS)
            // Flush the translucent verts while blending is still enabled.
            shapes.flush()
        } finally {
            Gdx.gl.glDisable(GL20.GL_BLEND)
        }
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

    /**
     * Draws the pre-game 3 / 2 / 1 / GO! glyph centered on the viewport
     * while the engine reports a positive countdown. Drawn last so it
     * overlays sprites/walls; a dark backdrop guarantees legibility on top
     * of the floor pattern.
     */
    private fun drawCountdownOverlay(engine: GameEngine) {
        val remaining = engine.countdownRemainingSeconds
        val goFlash = engine.goFlashRemainingSeconds
        if (remaining <= 0f && goFlash <= 0f) return
        val text = if (remaining > 0f) countdownLabel(remaining) else "GO!"
        val glyphSize = (viewport.worldWidth.coerceAtMost(viewport.worldHeight)) * 0.18f
        val glyphGap = glyphSize * 0.18f
        val cx = viewport.worldWidth / 2f
        val cy = viewport.worldHeight / 2f
        drawBackdrop(cx, cy, glyphSize, glyphGap, text)

        shapes.begin(ShapeRenderer.ShapeType.Filled)
        drawCountdownExitArrow(engine, cx, cy, glyphSize, remaining, goFlash)
        PixelTextRenderer.drawCentered(
            shapes = shapes,
            text = text,
            centerX = cx,
            centerY = cy,
            glyphSize = glyphSize,
            glyphGap = glyphGap,
            fallbackColor = COUNTDOWN_COLOR
        )
        shapes.end()
    }

    private fun drawCountdownExitArrow(
        engine: GameEngine,
        centerX: Float,
        centerY: Float,
        glyphSize: Float,
        remaining: Float,
        goFlash: Float
    ) {
        val exitX = mazeOriginX + engine.maze.exit.x + 0.5f
        val exitY = mazeOriginY + engine.maze.exit.y + 0.5f
        val rawDx = exitX - centerX
        val rawDy = exitY - centerY
        val distance = sqrt(rawDx * rawDx + rawDy * rawDy)
        val dirX: Float
        val dirY: Float
        if (distance > COUNTDOWN_ARROW_DIRECTION_EPSILON) {
            dirX = rawDx / distance
            dirY = rawDy / distance
        } else {
            dirX = 0f
            dirY = 1f
        }

        val animationTimeSeconds = if (remaining > 0f) {
            GameEngine.COUNTDOWN_DEFAULT_SECONDS - remaining
        } else {
            GameEngine.COUNTDOWN_DEFAULT_SECONDS +
                (GameEngine.COUNTDOWN_GO_FLASH_SECONDS - goFlash)
        }
        val bobbingPhase = animationTimeSeconds * COUNTDOWN_ARROW_BOB_RADIANS_PER_SECOND
        val bobbingOffset = sin(bobbingPhase) * glyphSize * COUNTDOWN_ARROW_BOB_DISTANCE_FACTOR
        val baseDistance = glyphSize * COUNTDOWN_ARROW_DISTANCE_FACTOR + bobbingOffset
        val arrowCenterX = (centerX + dirX * baseDistance).coerceIn(
            COUNTDOWN_ARROW_VIEWPORT_PADDING,
            viewport.worldWidth - COUNTDOWN_ARROW_VIEWPORT_PADDING
        )
        val arrowCenterY = (centerY + dirY * baseDistance).coerceIn(
            COUNTDOWN_ARROW_VIEWPORT_PADDING,
            viewport.worldHeight - COUNTDOWN_ARROW_VIEWPORT_PADDING
        )
        val arrowLength = glyphSize * COUNTDOWN_ARROW_LENGTH_FACTOR
        val headLength = arrowLength * COUNTDOWN_ARROW_HEAD_LENGTH_FACTOR
        val shaftLength = arrowLength - headLength
        val shaftWidth = glyphSize * COUNTDOWN_ARROW_SHAFT_WIDTH_FACTOR
        val headWidth = glyphSize * COUNTDOWN_ARROW_HEAD_WIDTH_FACTOR
        val perpX = -dirY
        val perpY = dirX
        val tipX = arrowCenterX + dirX * arrowLength * 0.5f
        val tipY = arrowCenterY + dirY * arrowLength * 0.5f
        val headBaseX = tipX - dirX * headLength
        val headBaseY = tipY - dirY * headLength
        val tailX = headBaseX - dirX * shaftLength
        val tailY = headBaseY - dirY * shaftLength
        val halfShaft = shaftWidth * 0.5f
        val halfHead = headWidth * 0.5f

        shapes.color = COUNTDOWN_ARROW_SHADOW_COLOR
        drawArrow(
            tipX + COUNTDOWN_ARROW_SHADOW_OFFSET,
            tipY - COUNTDOWN_ARROW_SHADOW_OFFSET,
            headBaseX + COUNTDOWN_ARROW_SHADOW_OFFSET,
            headBaseY - COUNTDOWN_ARROW_SHADOW_OFFSET,
            tailX + COUNTDOWN_ARROW_SHADOW_OFFSET,
            tailY - COUNTDOWN_ARROW_SHADOW_OFFSET,
            perpX,
            perpY,
            halfShaft,
            halfHead
        )
        shapes.color = COUNTDOWN_ARROW_COLOR
        drawArrow(tipX, tipY, headBaseX, headBaseY, tailX, tailY, perpX, perpY, halfShaft, halfHead)
    }

    private fun drawArrow(
        tipX: Float,
        tipY: Float,
        headBaseX: Float,
        headBaseY: Float,
        tailX: Float,
        tailY: Float,
        perpX: Float,
        perpY: Float,
        halfShaft: Float,
        halfHead: Float
    ) {
        // The arrow is three filled triangles: two form the shaft rectangle,
        // and one forms the head. `perpX` / `perpY` is the unit vector
        // perpendicular to the arrow direction, used to expand center-line
        // points into left/right edges without allocating helper objects.
        val shaftLeftTailX = tailX + perpX * halfShaft
        val shaftLeftTailY = tailY + perpY * halfShaft
        val shaftRightTailX = tailX - perpX * halfShaft
        val shaftRightTailY = tailY - perpY * halfShaft
        val shaftLeftHeadX = headBaseX + perpX * halfShaft
        val shaftLeftHeadY = headBaseY + perpY * halfShaft
        val shaftRightHeadX = headBaseX - perpX * halfShaft
        val shaftRightHeadY = headBaseY - perpY * halfShaft
        shapes.triangle(
            shaftLeftTailX,
            shaftLeftTailY,
            shaftRightTailX,
            shaftRightTailY,
            shaftLeftHeadX,
            shaftLeftHeadY
        )
        shapes.triangle(
            shaftRightTailX,
            shaftRightTailY,
            shaftRightHeadX,
            shaftRightHeadY,
            shaftLeftHeadX,
            shaftLeftHeadY
        )
        shapes.triangle(
            tipX,
            tipY,
            headBaseX + perpX * halfHead,
            headBaseY + perpY * halfHead,
            headBaseX - perpX * halfHead,
            headBaseY - perpY * halfHead
        )
    }

    private fun countdownLabel(remaining: Float): String = when {
        remaining > 2f -> "3"
        remaining > 1f -> "2"
        else -> "1"
    }

    /**
     * Draws the end-of-game overlay ("YOU WIN!" with bright colours / "GAME
     * OVER" with dark colours) centered on the viewport.
     */
    private fun drawEndOverlay(engine: GameEngine) {
        val (text, palette) = when (engine.status) {
            com.example.apktest.game.core.GameStatus.WIN -> "YOU WIN!" to WIN_PALETTE
            com.example.apktest.game.core.GameStatus.LOSE -> "GAME OVER" to LOSE_PALETTE
            else -> return
        }
        val glyphSize = (viewport.worldWidth.coerceAtMost(viewport.worldHeight)) * 0.10f
        val glyphGap = glyphSize * 0.20f
        val cx = viewport.worldWidth / 2f
        val cy = viewport.worldHeight / 2f
        drawBackdrop(cx, cy, glyphSize, glyphGap, text)

        shapes.begin(ShapeRenderer.ShapeType.Filled)
        PixelTextRenderer.drawCentered(
            shapes = shapes,
            text = text,
            centerX = cx,
            centerY = cy,
            glyphSize = glyphSize,
            glyphGap = glyphGap,
            fallbackColor = palette[0],
            colorForIndex = { i -> palette[i % palette.size] }
        )
        shapes.end()
    }

    /**
     * Opaque dark rect painted behind overlay text so the message stays
     * legible regardless of the floor/wall colours underneath.
     */
    private fun drawBackdrop(
        centerX: Float,
        centerY: Float,
        glyphSize: Float,
        glyphGap: Float,
        text: String
    ) {
        val textWidth = PixelTextRenderer.textWidth(text, glyphSize, glyphGap)
        val padX = glyphSize * 0.5f
        val padY = glyphSize * 0.4f
        val rectW = textWidth + 2f * padX
        val rectH = glyphSize + 2f * padY
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        shapes.color = OVERLAY_BACKDROP_COLOR
        shapes.rect(centerX - rectW / 2f, centerY - rectH / 2f, rectW, rectH)
        shapes.end()
    }

    private companion object {
        // Wall thickness as fraction of a cell (world units).
        private const val WALL_THICKNESS = 0.18f

        // Upper-bound pixel count per wall segment (4 rows * 8 cols brick tile).
        private const val WALL_PATTERN_PIXELS_PER_SEGMENT = 32

        private val COUNTDOWN_COLOR = Color(1f, 0.95f, 0.5f, 1f)
        private val COUNTDOWN_ARROW_COLOR = Color(0.00f, 1.00f, 0.95f, 1f)
        private val COUNTDOWN_ARROW_SHADOW_COLOR = Color(0.02f, 0.02f, 0.08f, 1f)
        private val OVERLAY_BACKDROP_COLOR = Color(0.02f, 0.02f, 0.04f, 1f)
        private val EXIT_GLOW_OUTER_COLOR = Color(0.00f, 0.95f, 1.00f, 1f)
        private val EXIT_GLOW_INNER_COLOR = Color(1.00f, 0.15f, 0.95f, 1f)
        private const val EXIT_GLOW_OUTER_ALPHA = 0.30f
        private const val EXIT_GLOW_INNER_ALPHA = 0.48f
        private const val EXIT_GLOW_OUTER_RADIUS = 0.82f
        private const val EXIT_GLOW_INNER_RADIUS = 0.58f
        private const val EXIT_GLOW_SEGMENTS = 24
        private const val COUNTDOWN_ARROW_DISTANCE_FACTOR = 1.18f
        private const val COUNTDOWN_ARROW_BOB_DISTANCE_FACTOR = 0.18f
        private const val COUNTDOWN_ARROW_LENGTH_FACTOR = 0.90f
        private const val COUNTDOWN_ARROW_HEAD_LENGTH_FACTOR = 0.38f
        private const val COUNTDOWN_ARROW_SHAFT_WIDTH_FACTOR = 0.16f
        private const val COUNTDOWN_ARROW_HEAD_WIDTH_FACTOR = 0.46f
        private const val COUNTDOWN_ARROW_VIEWPORT_PADDING = 0.55f
        private const val COUNTDOWN_ARROW_SHADOW_OFFSET = 0.06f
        /** Prevents divide-by-zero when the countdown center overlaps the exit. */
        private const val COUNTDOWN_ARROW_DIRECTION_EPSILON = 0.0001f
        /** 4π radians/sec = two full bobbing cycles per second. */
        private val COUNTDOWN_ARROW_BOB_RADIANS_PER_SECOND = (4.0 * PI).toFloat()
        private val POWER_UP_TINT_COLORS: Array<Color> = Array(PowerUpType.entries.size) { i ->
            PowerUpIcons.gdxColorFor(PowerUpType.entries[i])
        }
        private val PLAYER_TINT_TYPES: Array<PowerUpType> =
            PowerUpType.entries.filter { playerTintEligible(it) }.toTypedArray()
        private val TIMED_POWER_UP_COUNT: Int = PLAYER_TINT_TYPES.size

        private fun playerTintEligible(type: PowerUpType): Boolean = when (type) {
            PowerUpType.INVISIBILITY -> true
            PowerUpType.TELEPORT -> false
            PowerUpType.SPEED_UP -> true
            PowerUpType.FREEZE -> true
            PowerUpType.SHIELD -> true
            PowerUpType.SLOW_TIME -> true
            PowerUpType.MAGNET -> true
            PowerUpType.BLAST -> false
            PowerUpType.GHOST_MODE -> true
        }.also { eligible ->
            if (eligible) {
                check(type.metadata.kind == PowerUpEffectKind.TIMED) {
                    "Player tint power-up must be timed: $type"
                }
            }
        }

        // Bright cycle for WIN — vivid yellow / lime / cyan / magenta.
        private val WIN_PALETTE = arrayOf(
            Color(1f, 0.95f, 0.20f, 1f),
            Color(0.40f, 1f, 0.40f, 1f),
            Color(0.30f, 0.90f, 1f, 1f),
            Color(1f, 0.40f, 0.90f, 1f)
        )

        // Dark cycle for LOSE — muted blood-red / purple / slate / forest.
        private val LOSE_PALETTE = arrayOf(
            Color(0.50f, 0.05f, 0.05f, 1f),
            Color(0.25f, 0.05f, 0.35f, 1f),
            Color(0.15f, 0.15f, 0.20f, 1f),
            Color(0.10f, 0.30f, 0.15f, 1f)
        )
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
