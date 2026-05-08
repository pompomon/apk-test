package com.example.apktest.game.core

import com.example.apktest.game.ui.HudState
import kotlin.random.Random

class GameEngine(
    difficultyPreset: DifficultyPreset = DifficultyPresets.MEDIUM,
    seed: Long = System.currentTimeMillis()
) {
    var difficulty: DifficultyPreset = difficultyPreset
        private set

    var playerPolicyType: PlayerPolicyType = PlayerPolicyType.MANUAL
        private set
    var npcPolicyType: NpcPolicyType = NpcPolicyType.DIRECT_CHASE
        private set

    var status: GameStatus = GameStatus.RUNNING
        private set

    var elapsedSeconds: Float = 0f
        private set
    var steps: Int = 0
        private set

    var maze: Maze = MazeGenerator.generate(difficulty.mazeWidth, difficulty.mazeHeight, seed)
        private set
    var navigator: MazeNavigator = MazeNavigator(maze)
        private set
    var player: Player = Player(maze.start)
        private set
    var npcs: MutableList<Npc> = mutableListOf()
        private set

    private var random = Random(seed)
    private var playerPolicy: PlayerPolicy = PolicyFactory.player(playerPolicyType)
    private var npcPolicy: NpcPolicy = PolicyFactory.npc(npcPolicyType)

    private var playerAccumulator = 0f
    private var npcAccumulator = 0f
    private val manualQueue = ArrayDeque<Direction>()

    init {
        spawnNpcs()
    }

    fun restart(seed: Long = System.currentTimeMillis()) {
        maze = MazeGenerator.generate(difficulty.mazeWidth, difficulty.mazeHeight, seed)
        navigator = MazeNavigator(maze)
        player = Player(maze.start)
        npcs = mutableListOf()
        random = Random(seed)
        status = GameStatus.RUNNING
        elapsedSeconds = 0f
        steps = 0
        playerAccumulator = 0f
        npcAccumulator = 0f
        manualQueue.clear()
        playerPolicy.reset()
        npcPolicy.reset()
        spawnNpcs()
    }

    fun setDifficulty(newDifficulty: DifficultyPreset) {
        difficulty = newDifficulty
        restart()
    }

    fun setPlayerPolicy(type: PlayerPolicyType) {
        playerPolicyType = type
        playerPolicy = PolicyFactory.player(type)
        manualQueue.clear()
        playerPolicy.reset()
    }

    fun setNpcPolicy(type: NpcPolicyType) {
        npcPolicyType = type
        npcPolicy = PolicyFactory.npc(type)
        npcPolicy.reset()
    }

    fun queueManualMove(direction: Direction) {
        manualQueue.addLast(direction)
    }

    fun togglePause() {
        status = when (status) {
            GameStatus.RUNNING -> GameStatus.PAUSED
            GameStatus.PAUSED -> GameStatus.RUNNING
            else -> status
        }
    }

    fun update(deltaSeconds: Float) {
        if (status != GameStatus.RUNNING) return

        elapsedSeconds += deltaSeconds
        playerAccumulator += deltaSeconds
        npcAccumulator += deltaSeconds

        val playerInterval = 1f / difficulty.playerMovesPerSecond
        while (playerAccumulator >= playerInterval && status == GameStatus.RUNNING) {
            playerAccumulator -= playerInterval
            updatePlayer()
            evaluateEndConditions()
        }

        val npcInterval = 1f / difficulty.npcMovesPerSecond
        while (npcAccumulator >= npcInterval && status == GameStatus.RUNNING) {
            npcAccumulator -= npcInterval
            updateNpcs()
            evaluateEndConditions()
        }
    }

    fun hudState(): HudState = HudState(
        status = status,
        elapsedSeconds = elapsedSeconds,
        steps = steps,
        difficultyName = difficulty.name,
        playerPolicyLabel = playerPolicyType.label,
        npcPolicyLabel = npcPolicyType.label,
        playerSpeed = difficulty.playerMovesPerSecond,
        npcSpeed = difficulty.npcMovesPerSecond
    )

    private fun updatePlayer() {
        val requestedDirection = when (playerPolicyType) {
            PlayerPolicyType.MANUAL -> manualQueue.removeFirstOrNull()
            else -> playerPolicy.nextMove(
                PlayerPolicyContext(
                    maze = maze,
                    navigator = navigator,
                    player = player,
                    exit = maze.exit,
                    npcs = npcs
                )
            )
        } ?: return

        if (!maze.canMove(player.position, requestedDirection)) return

        player.position = player.position.moved(requestedDirection)
        player.facing = requestedDirection
        steps += 1
    }

    private fun updateNpcs() {
        val context = NpcPolicyContext(
            maze = maze,
            navigator = navigator,
            player = player,
            visionRange = difficulty.npcVisionRange
        )

        npcs.forEach { npc ->
            val direction = npcPolicy.nextMove(npc, context) ?: return@forEach
            if (!maze.canMove(npc.position, direction)) return@forEach

            npc.position = npc.position.moved(direction)
            npc.facing = direction
        }
    }

    private fun evaluateEndConditions() {
        if (player.position == maze.exit) {
            status = GameStatus.WIN
            return
        }

        if (npcs.any { it.position == player.position }) {
            status = GameStatus.LOSE
        }
    }

    private fun spawnNpcs() {
        val occupied = mutableSetOf(maze.start, maze.exit)
        repeat(difficulty.npcCount) { index ->
            var candidate = randomCell()
            while (candidate in occupied) {
                candidate = randomCell()
            }
            occupied += candidate

            val route = patrolRouteFrom(candidate)
            npcs += Npc(
                id = index,
                position = candidate,
                patrolRoute = route
            )
        }
    }

    private fun randomCell(): GridPos {
        return GridPos(
            x = random.nextInt(maze.width),
            y = random.nextInt(maze.height)
        )
    }

    private fun patrolRouteFrom(origin: GridPos): List<GridPos> {
        val neighbors = maze.neighbors(origin)
        if (neighbors.isEmpty()) return listOf(origin)
        return listOf(origin) + neighbors.take(MAX_EXTRA_PATROL_WAYPOINTS)
    }

    companion object {
        private const val MAX_EXTRA_PATROL_WAYPOINTS = 2
    }
}
