package com.example.apktest.game.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.utils.ScreenUtils
import com.badlogic.gdx.utils.viewport.ExtendViewport
import com.example.apktest.game.core.GameEngine
import com.example.apktest.game.core.GameStatus
import com.example.apktest.game.core.Maze
import com.example.apktest.game.core.Maze.Companion.WALL_MASKS

class MazeRenderer {
    private val camera = OrthographicCamera()
    private var viewport = ExtendViewport(20f, 30f, camera)
    private val shapes = ShapeRenderer()
    private val batch = SpriteBatch()
    private val font = BitmapFont()

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

        drawHud(engine)
    }

    fun dispose() {
        shapes.dispose()
        batch.dispose()
        font.dispose()
    }

    private fun updateViewportForMaze(maze: Maze) {
        if (maze.width == cachedWidth && maze.height == cachedHeight) return
        cachedWidth = maze.width
        cachedHeight = maze.height
        viewport = ExtendViewport(maze.width.toFloat(), maze.height.toFloat() + HUD_HEIGHT, camera)
        viewport.update(Gdx.graphics.width, Gdx.graphics.height, true)
    }

    private fun drawMaze(engine: GameEngine) {
        val maze = engine.maze
        shapes.begin(ShapeRenderer.ShapeType.Filled)

        shapes.color = Color(0.12f, 0.12f, 0.16f, 1f)
        shapes.rect(0f, 0f, maze.width.toFloat(), maze.height.toFloat())

        shapes.color = Color(0.2f, 0.8f, 0.3f, 1f)
        shapes.rect(maze.exit.x + 0.2f, maze.exit.y + 0.2f, 0.6f, 0.6f)
        shapes.end()

        shapes.begin(ShapeRenderer.ShapeType.Line)
        shapes.color = Color.WHITE

        for (y in 0 until maze.height) {
            for (x in 0 until maze.width) {
                val pos = com.example.apktest.game.core.GridPos(x, y)
                if (maze.hasWall(pos, com.example.apktest.game.core.Direction.NORTH)) {
                    shapes.line(x.toFloat(), (y + 1).toFloat(), (x + 1).toFloat(), (y + 1).toFloat())
                }
                if (maze.hasWall(pos, com.example.apktest.game.core.Direction.SOUTH)) {
                    shapes.line(x.toFloat(), y.toFloat(), (x + 1).toFloat(), y.toFloat())
                }
                if (maze.hasWall(pos, com.example.apktest.game.core.Direction.WEST)) {
                    shapes.line(x.toFloat(), y.toFloat(), x.toFloat(), (y + 1).toFloat())
                }
                if (maze.hasWall(pos, com.example.apktest.game.core.Direction.EAST)) {
                    shapes.line((x + 1).toFloat(), y.toFloat(), (x + 1).toFloat(), (y + 1).toFloat())
                }
            }
        }
        shapes.end()
    }

    private fun drawEntities(engine: GameEngine) {
        shapes.begin(ShapeRenderer.ShapeType.Filled)

        val player = engine.player
        shapes.color = Color(0.25f, 0.5f, 1f, 1f)
        shapes.circle(player.position.x + 0.5f, player.position.y + 0.5f, 0.28f, 24)

        shapes.color = Color(0.9f, 0.25f, 0.25f, 1f)
        engine.npcs.forEach { npc ->
            shapes.circle(npc.position.x + 0.5f, npc.position.y + 0.5f, 0.24f, 20)
        }

        shapes.end()
    }

    private fun drawHud(engine: GameEngine) {
        val hud = engine.hudState()

        batch.projectionMatrix = camera.combined
        batch.begin()
        font.color = Color.WHITE

        val status = when (hud.status) {
            GameStatus.RUNNING -> "Running"
            GameStatus.PAUSED -> "Paused"
            GameStatus.WIN -> "You Win"
            GameStatus.LOSE -> "Caught"
        }

        val yTop = engine.maze.height + HUD_HEIGHT - 0.35f
        font.draw(batch, "Status: $status", 0.3f, yTop)
        font.draw(batch, "Difficulty: ${hud.difficultyName}", 3.8f, yTop)
        font.draw(batch, "Player: ${hud.playerPolicyLabel}", 8.6f, yTop)
        font.draw(batch, "NPC: ${hud.npcPolicyLabel}", 13.7f, yTop)
        font.draw(batch, "Time: ${TIME_FORMAT_SPEC.format(hud.elapsedSeconds)}s", 0.3f, yTop - 0.55f)
        font.draw(batch, "Steps: ${hud.steps}", 3.2f, yTop - 0.55f)
        font.draw(batch, "Spd P:${hud.playerSpeed} N:${hud.npcSpeed}", 5.8f, yTop - 0.55f)

        batch.end()
    }

    companion object {
        private const val HUD_HEIGHT = 2f
        private const val TIME_FORMAT_SPEC = "%.1f"
    }
}
