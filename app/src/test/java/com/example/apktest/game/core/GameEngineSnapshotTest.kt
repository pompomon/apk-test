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

    /**
     * Restoring a snapshot whose `difficultyName` doesn't match any known
     * preset must be rejected: `DifficultyPresets.byName` falls back to
     * `MEDIUM` for unknown names, which would regenerate a
     * differently-sized maze and could place persisted entities out of
     * bounds. `fromJson` should return `null` so callers
     * (e.g., `GameStateStore.load`) drop the corrupted blob instead of
     * falling through to a silent restore with a mismatched preset.
     */
    @Test
    fun fromJson_returnsNullForUnknownDifficultyName() {
        val original = GameEngine(DifficultyPresets.MEDIUM, seed)
        val json = original.snapshot().toJson()
        val tampered = json.replace("\"${DifficultyPresets.MEDIUM.name}\"", "\"NotARealDifficulty\"")
        assertEquals(null, GameEngineSnapshot.fromJson(tampered))
    }

    /**
     * Coordinates outside the preset's maze bounds (corrupted snapshot)
     * would later crash the engine via out-of-bounds `Maze.hasWall`
     * calls. `fromJson` should reject the blob so callers drop it.
     */
    @Test
    fun fromJson_returnsNullForOutOfBoundsPlayer() {
        val original = GameEngine(DifficultyPresets.MEDIUM, seed)
        val snap = original.snapshot()
        val w = DifficultyPresets.MEDIUM.mazeWidth
        val h = DifficultyPresets.MEDIUM.mazeHeight
        val outOfBounds = snap.copy(
            player = snap.player.copy(x = w + 5, y = h + 5)
        )
        assertEquals(null, GameEngineSnapshot.fromJson(outOfBounds.toJson()))
    }

    /**
     * `GameEngine.restore` itself defensively validates the snapshot so
     * programmatically-constructed (not just deserialized) invalid
     * snapshots also fail loudly instead of corrupting engine state.
     */
    @Test(expected = IllegalArgumentException::class)
    fun restore_rejectsUnknownDifficulty() {
        val original = GameEngine(DifficultyPresets.MEDIUM, seed)
        val snap = original.snapshot().copy(difficultyName = "NotARealDifficulty")
        GameEngine(DifficultyPresets.MEDIUM, seed + 7).restore(snap)
    }

    @Test(expected = IllegalArgumentException::class)
    fun restore_rejectsOutOfBoundsCoordinates() {
        val original = GameEngine(DifficultyPresets.MEDIUM, seed)
        val snap = original.snapshot()
        val bad = snap.copy(
            player = snap.player.copy(
                x = DifficultyPresets.MEDIUM.mazeWidth + 10,
                y = DifficultyPresets.MEDIUM.mazeHeight + 10
            )
        )
        GameEngine(DifficultyPresets.MEDIUM, seed + 7).restore(bad)
    }

    /**
     * `removedWalls` entries are persisted coordinates too — a corrupted
     * blob with an out-of-bounds removed-wall cell would later crash
     * `GameEngine.restore` when it tries to re-apply the wall removal
     * via `Maze.removeWall`. `isWithinBounds` must reject these.
     */
    @Test(expected = IllegalArgumentException::class)
    fun restore_rejectsOutOfBoundsRemovedWalls() {
        val original = GameEngine(DifficultyPresets.MEDIUM, seed)
        val snap = original.snapshot()
        val bad = snap.copy(
            removedWalls = listOf(
                GameEngineSnapshot.RemovedWallSnapshot(
                    x = DifficultyPresets.MEDIUM.mazeWidth + 10,
                    y = DifficultyPresets.MEDIUM.mazeHeight + 10,
                    direction = Direction.NORTH
                )
            )
        )
        GameEngine(DifficultyPresets.MEDIUM, seed + 7).restore(bad)
    }

    /**
     * `MazeGenerator.generate` rounds maze dimensions up to the next even
     * number, so for a preset with odd `mazeWidth`/`mazeHeight` the
     * actual generated maze is one cell wider/taller than the preset.
     * `isWithinBounds` must validate against those rounded-up bounds so
     * a snapshot whose coordinates fall inside the *actual* maze (but
     * outside the raw preset dimensions) is not incorrectly rejected.
     */
    @Test
    fun isWithinBounds_acceptsCoordinatesInRoundedUpMaze() {
        val oddPreset = DifficultyPresets.MEDIUM.copy(
            name = "OddPreset",
            mazeWidth = 17,
            mazeHeight = 27
        )
        val original = GameEngine(DifficultyPresets.MEDIUM, seed)
        val snap = original.snapshot().copy(
            difficultyName = oddPreset.name,
            // x = 17, y = 27 are outside the raw preset bounds but inside
            // the rounded-up (18 x 28) maze that MazeGenerator produces.
            player = original.snapshot().player.copy(x = 17, y = 27)
        )
        assertEquals(true, snap.isWithinBounds(oddPreset))
    }

    /**
     * Gameplay can permanently mutate the maze (e.g., a BLAST power-up
     * removing the four walls around the player). A save/resume round-
     * trip must preserve those mutations: a freshly-regenerated baseline
     * maze from the seed alone would restore the original walls and
     * could trap or free entities differently than the player saw.
     * Removes a wall, snapshots, restores into a fresh engine, and
     * asserts the restored maze's wall state matches cell-for-cell.
     */
    @Test
    fun roundTrip_preservesRemovedWalls() {
        val original = GameEngine(DifficultyPresets.MEDIUM, seed)
        // Find any interior wall and remove it so the baseline (regenerated
        // from the seed) and the mutated maze differ — exercising the
        // removedWalls round-trip rather than the no-mutation fast path.
        val maze = original.maze
        var removed: Pair<GridPos, Direction>? = null
        outer@ for (y in 0 until maze.height) {
            for (x in 0 until maze.width) {
                val pos = GridPos(x, y)
                for (dir in listOf(Direction.EAST, Direction.NORTH)) {
                    val next = pos.moved(dir)
                    if (maze.inBounds(next) && maze.hasWall(pos, dir)) {
                        maze.removeWall(pos, dir)
                        removed = pos to dir
                        break@outer
                    }
                }
            }
        }
        // The MEDIUM maze always has at least one interior wall to remove.
        assertEquals(false, removed == null)
        val (pos, dir) = removed!!
        // Confirm the mutation took effect on both shared sides before snapshotting.
        assertEquals(false, original.maze.hasWall(pos, dir))
        assertEquals(false, original.maze.hasWall(pos.moved(dir), dir.opposite()))

        val snap = original.snapshot()
        val other = GameEngine(DifficultyPresets.MEDIUM, seed + 7)
        other.restore(snap)

        // The restored maze must mirror the mutated original cell-for-cell,
        // including the removed wall (and its opposite side), so a resumed
        // game cannot resurrect walls the player already destroyed.
        assertEquals(false, other.maze.hasWall(pos, dir))
        assertEquals(false, other.maze.hasWall(pos.moved(dir), dir.opposite()))
        for (y in 0 until original.maze.height) {
            for (x in 0 until original.maze.width) {
                for (d in Direction.entries) {
                    assertEquals(
                        "wall mismatch at ($x,$y,$d) after removeWall + restore",
                        original.maze.hasWall(x, y, d),
                        other.maze.hasWall(x, y, d)
                    )
                }
            }
        }
    }
}
