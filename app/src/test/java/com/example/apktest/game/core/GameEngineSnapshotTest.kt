package com.example.apktest.game.core

import org.junit.Assert.assertEquals
import org.junit.Test

class GameEngineSnapshotTest {
    private val seed = 4321L

    /**
     * A snapshot taken mid-game and applied to a freshly-constructed engine
     * (built with a different seed) must reproduce the observable state of
     * the original: same maze layout (because the seed is captured), same
     * player position/facing, same NPC roster, same score/timer, same
     * status. Uses a real difficulty preset so [GameEngine.restore]'s
     * `DifficultyPresets.byName` lookup succeeds.
     */
    @Test
    fun roundTrip_yieldsEqualObservableState() {
        val original = GameEngine(DifficultyPresets.MEDIUM, seed)
        original.setPlayerPolicy(PlayerPolicyType.BFS_EXIT)
        // Run a few player ticks so positions diverge from spawn.
        repeat(20) { original.update(0.2f) }
        val snap = original.snapshot()

        val other = GameEngine(DifficultyPresets.MEDIUM, seed + 7)
        other.restore(snap)

        assertEquals(original.maze.width, other.maze.width)
        assertEquals(original.maze.height, other.maze.height)
        assertEquals(original.maze.start, other.maze.start)
        assertEquals(original.maze.exit, other.maze.exit)
        assertEquals(original.player.position, other.player.position)
        assertEquals(original.player.facing, other.player.facing)
        assertEquals(
            original.npcs.map { it.position to it.facing },
            other.npcs.map { it.position to it.facing }
        )
        assertEquals(original.steps, other.steps)
        assertEquals(original.elapsedSeconds, other.elapsedSeconds, 0.0001f)
        assertEquals(original.status, other.status)
        assertEquals(original.playerPolicyType, other.playerPolicyType)
        assertEquals(original.npcPolicyType, other.npcPolicyType)
        assertEquals(original.difficulty.name, other.difficulty.name)
        // Same maze identity isn't preserved (restore creates a new Maze),
        // but the seed is so the wall layout matches cell-for-cell.
        for (y in 0 until original.maze.height) {
            for (x in 0 until original.maze.width) {
                for (dir in Direction.entries) {
                    assertEquals(
                        "wall mismatch at ($x,$y,$dir)",
                        original.maze.hasWall(x, y, dir),
                        other.maze.hasWall(x, y, dir)
                    )
                }
            }
        }
    }

    /**
     * Restoring preserves the set of spawned power-ups (type + position) so
     * the player isn't surprised by missing pickups after a resume.
     */
    @Test
    fun roundTrip_preservesPowerUps() {
        val original = GameEngine(DifficultyPresets.MEDIUM, seed)
        val snap = original.snapshot()
        val other = GameEngine(DifficultyPresets.MEDIUM, seed + 7)
        other.restore(snap)
        assertEquals(
            original.spawnedPowerUps.map { it.type to it.position }.toSet(),
            other.spawnedPowerUps.map { it.type to it.position }.toSet()
        )
    }
}
