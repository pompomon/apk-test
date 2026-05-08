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

class MazeRenderer {
    private val camera = OrthographicCamera()
    private var viewport = ExtendViewport(20f, 30f, camera)
    private val shapesDelegate = lazy(LazyThreadSafetyMode.NONE) { ShapeRenderer() }
    private val shapes: ShapeRenderer by shapesDelegate

    private var cachedWidth = -1
    private var cachedHeight = -1

    fun resize(width: Int, height: Int) {
        viewport.update(width, height, true)
    }

    fun render(engine: GameEngine) {
        updateViewportForMaze(engine.maze)

        ScreenUtils.clear(0.08f, 0.08f, 0.1f, 1f)
        viewport.apply(true)
        shapes.projectionMatrix = camera.combined

        drawMaze(engine)
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
        shapes.rect(0f, 0f, maze.width.toFloat(), maze.height.toFloat())

        shapes.color.set(0.2f, 0.8f, 0.3f, 1f)
        shapes.rect(maze.exit.x + 0.2f, maze.exit.y + 0.2f, 0.6f, 0.6f)
        shapes.end()

        shapes.begin(ShapeRenderer.ShapeType.Line)
        shapes.color = Color.WHITE

        // Walls are shared between adjacent cells; only render NORTH+WEST per cell
        // and add the matching outer SOUTH/EAST boundary segments to avoid drawing
        // each shared edge twice per frame.
        for (y in 0 until maze.height) {
            for (x in 0 until maze.width) {
                if (maze.hasWall(x, y, Direction.NORTH)) {
                    shapes.line(x.toFloat(), (y + 1).toFloat(), (x + 1).toFloat(), (y + 1).toFloat())
                }
                if (maze.hasWall(x, y, Direction.WEST)) {
                    shapes.line(x.toFloat(), y.toFloat(), x.toFloat(), (y + 1).toFloat())
                }
                if (y == 0 && maze.hasWall(x, y, Direction.SOUTH)) {
                    shapes.line(x.toFloat(), 0f, (x + 1).toFloat(), 0f)
                }
                if (x == maze.width - 1 && maze.hasWall(x, y, Direction.EAST)) {
                    shapes.line((x + 1).toFloat(), y.toFloat(), (x + 1).toFloat(), (y + 1).toFloat())
                }
            }
        }
        shapes.end()
    }

    private fun drawEntities(engine: GameEngine) {
        shapes.begin(ShapeRenderer.ShapeType.Filled)

        val player = engine.player
        shapes.color.set(0.25f, 0.5f, 1f, 1f)
        shapes.circle(player.position.x + 0.5f, player.position.y + 0.5f, 0.28f, 24)

        shapes.color.set(0.9f, 0.25f, 0.25f, 1f)
        engine.npcs.forEach { npc ->
            shapes.circle(npc.position.x + 0.5f, npc.position.y + 0.5f, 0.24f, 20)
        }

        shapes.end()
    }
}
