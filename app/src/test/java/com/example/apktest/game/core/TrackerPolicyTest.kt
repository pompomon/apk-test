package com.example.apktest.game.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

class TrackerPolicyTest {
    private val policy = PatrolGuardPolicy()

    @Test
    fun mixedRosterUsesPerNpcModifierWithOneSharedPolicyAndContext() {
        val context = context(
            player = GridPos(5, 1),
            adventurers = listOf(Adventurer(0, GridPos(1, 2)))
        )
        val normal = guard(null)
        val tracker = guard(EliteNpcModifier.TRACKER)

        assertEquals(Direction.NORTH, policy.nextMove(normal, context))
        assertEquals(GridPos(1, 2), normal.lastKnownPlayerPos)
        assertEquals(Direction.EAST, policy.nextMove(tracker, context))
        assertEquals(context.player.position, tracker.lastKnownPlayerPos)
        val nextNormal = guard(null)
        assertEquals(Direction.NORTH, policy.nextMove(nextNormal, context))
        assertEquals(normal.lastKnownPlayerPos, nextNormal.lastKnownPlayerPos)
        assertEquals(4, context.visionRange)
    }

    @Test
    fun trackerManhattanAcquisitionRangeIsExactlyBasePlusTwo() {
        for (distance in listOf(4, 5, 6, 7)) {
            for (modifier in listOf(null, EliteNpcModifier.TRACKER)) {
                val npc = guard(modifier)
                val context = context(player = GridPos(1 + distance, 1))
                policy.nextMove(npc, context)
                val inRange = distance <= 4 + (modifier?.visionRangeBonus ?: 0)
                assertEquals("distance=$distance modifier=$modifier",
                    if (inRange) NpcState.CHASE else NpcState.PATROL, npc.state)
                assertEquals(if (inRange) context.player.position else null, npc.lastKnownPlayerPos)
            }
        }
        val diagonal = guard(EliteNpcModifier.TRACKER)
        policy.nextMove(diagonal, context(player = GridPos(5, 5)))
        assertEquals(NpcState.PATROL, diagonal.state)
    }

    @Test
    fun invisiblePlayerFallsBackToVisibleAdventurerWithLowestIdOnPathTie() {
        val lowerId = Adventurer(2, GridPos(1, 3))
        val higherId = Adventurer(7, GridPos(3, 1))
        for (adventurers in listOf(listOf(higherId, lowerId), listOf(lowerId, higherId))) {
            val npc = guard(EliteNpcModifier.TRACKER)
            val context = context(
                player = GridPos(2, 1),
                adventurers = listOf(Adventurer(0, GridPos(1, 2))) + adventurers
            ).copy(playerVisible = false, invisibleAdventurerIds = setOf(0))
            assertEquals(Direction.NORTH, policy.nextMove(npc, context))
            assertEquals(lowerId.position, npc.lastKnownPlayerPos)
        }
    }

    @Test
    fun fallbackAdventurersAreFilteredByExpandedRangeBeforePathRanking() {
        val near = Adventurer(8, GridPos(1, 3))
        val fartherLowId = Adventurer(0, GridPos(1, 6))
        val outOfRange = Adventurer(1, GridPos(8, 1))
        val context = context(
            player = GridPos(9, 9),
            adventurers = listOf(outOfRange, fartherLowId, near)
        )
        val npc = guard(EliteNpcModifier.TRACKER)
        policy.nextMove(npc, context)
        assertEquals(near.position, npc.lastKnownPlayerPos)
        val boundary = guard(EliteNpcModifier.TRACKER)
        policy.nextMove(boundary, context.copy(adventurers = listOf(Adventurer(3, GridPos(7, 1)))))
        assertEquals(GridPos(7, 1), boundary.lastKnownPlayerPos)
        val outside = guard(EliteNpcModifier.TRACKER)
        policy.nextMove(outside, context.copy(adventurers = listOf(outOfRange)))
        assertNull(outside.lastKnownPlayerPos)
    }

    @Test
    fun trackerNeverNewlyAcquiresUnreachablePlayerButNormalGuardRetainsLegacyFallback() {
        val maze = disconnectedMaze()
        val context = context(maze = maze, player = GridPos(3, 1))
        val tracker = guard(EliteNpcModifier.TRACKER)
        assertEquals(Direction.NORTH, policy.nextMove(tracker, context))
        assertEquals(NpcState.PATROL, tracker.state)
        assertNull(tracker.lastKnownPlayerPos)

        val normal = guard(null)
        assertNull(policy.nextMove(normal, context))
        assertEquals(NpcState.SEARCH, normal.state)
        assertEquals(context.player.position, normal.lastKnownPlayerPos)
    }

    @Test
    fun unreachablePlayerFallsBackToReachableAdventurerAndIgnoresUnreachableAdventurers() {
        val reachable = Adventurer(4, GridPos(1, 2))
        val context = context(
            maze = disconnectedMaze(),
            player = GridPos(3, 1),
            adventurers = listOf(Adventurer(0, GridPos(2, 1)), reachable)
        )
        val npc = guard(EliteNpcModifier.TRACKER)
        assertEquals(Direction.NORTH, policy.nextMove(npc, context))
        assertEquals(reachable.position, npc.lastKnownPlayerPos)
    }

    @Test
    fun invisibleRunnersDoNotReplaceLastKnownTargetOrResetSearch() {
        val npc = guard(EliteNpcModifier.TRACKER)
        val context = context(player = GridPos(3, 1))
        policy.nextMove(npc, context)
        val lastKnown = npc.lastKnownPlayerPos
        val hidden = context.copy(
            player = Player(GridPos(2, 1)),
            playerVisible = false,
            adventurers = listOf(Adventurer(0, GridPos(1, 2))),
            invisibleAdventurerIds = setOf(0)
        )
        policy.nextMove(npc, hidden)
        assertEquals(lastKnown, npc.lastKnownPlayerPos)
        npc.position = lastKnown!!
        policy.nextMove(npc, hidden)
        assertEquals(NpcState.SEARCH, npc.state)
        repeat(5) { policy.nextMove(npc, hidden) }
        assertEquals(NpcState.PATROL, npc.state)
        assertNull(npc.lastKnownPlayerPos)
    }

    @Test
    fun frozenTrackerDoesNotMoveAcquireOrAdvanceSearchState() {
        val npc = guard(EliteNpcModifier.TRACKER).copy(
            state = NpcState.SEARCH,
            searchTicksRemaining = 3,
            lastKnownPlayerPos = GridPos(2, 1)
        )
        val before = npc.copy()
        assertNull(policy.nextMove(npc, context().copy(npcsFrozen = true)))
        assertEquals(before, npc)
    }

    @Test
    fun modifierDoesNotChangeOtherPoliciesEvenOnMalformedDirectNpc() {
        val context = context(
            player = GridPos(5, 1),
            adventurers = listOf(Adventurer(0, GridPos(1, 2)))
        )
        for (type in listOf(NpcPolicyType.DIRECT_CHASE, NpcPolicyType.PREDICTIVE_CHASE)) {
            val normal = guard(null).copy(policyType = type)
            val malformed = normal.copy(eliteModifier = EliteNpcModifier.TRACKER)
            assertEquals(
                PolicyFactory.npc(type, Random(1)).nextMove(normal, context),
                PolicyFactory.npc(type, Random(1)).nextMove(malformed, context)
            )
        }
    }

    private fun guard(modifier: EliteNpcModifier?) = Npc(
        id = 0,
        position = GridPos(1, 1),
        patrolRoute = listOf(GridPos(1, 1), GridPos(1, 2)),
        policyType = NpcPolicyType.PATROL_GUARD,
        eliteModifier = modifier
    )

    private fun context(
        maze: Maze = Maze.openGrid(10, 10),
        player: GridPos = GridPos(5, 1),
        adventurers: List<Adventurer> = emptyList()
    ) = NpcPolicyContext(
        maze, MazeNavigator(maze), Player(player), 4,
        playerVisible = true, npcsFrozen = false, adventurers = adventurers
    )

    private fun disconnectedMaze() = Maze(
        width = 5, height = 5, cells = IntArray(25) { Maze.ALL_WALLS },
        start = GridPos(0, 0), exit = GridPos(4, 4)
    ).apply { removeWall(GridPos(1, 1), Direction.NORTH) }
}
