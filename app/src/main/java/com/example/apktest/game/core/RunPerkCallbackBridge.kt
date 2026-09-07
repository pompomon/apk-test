package com.example.apktest.game.core

/** Observers may be replaced off-thread; delivery and snapshots stay on the simulation thread. */
internal class RunPerkCallbackBridge(private val engine: GameEngine) {
    private class Callbacks(
        val onConsumed: ((GameEngineSnapshot) -> Unit)?,
        val onEffectApplied: ((RunPerkEffectEvent) -> Unit)?
    ) {
        val token = Any()
    }

    @Volatile private var callbacks: Callbacks? = null
    @Volatile private var disposed = false
    private val callbackLock = Any()
    private var notifiedToken: Any? = null
    private var notifiedPerk: RunPerkId? = null
    private var notifiedSeed: Long? = null
    private val pendingStartupEffects = ArrayDeque<RunPerkEffectEvent>()

    init {
        engine.onRunPerkEffectApplied = { event ->
            synchronized(callbackLock) {
                if (!disposed) callbacks?.onEffectApplied?.invoke(event)
            }
        }
    }

    /** Callbacks run on GL: enqueue main-thread work rather than waiting for it. */
    fun setCallbacks(
        onConsumed: ((GameEngineSnapshot) -> Unit)?,
        onEffectApplied: ((RunPerkEffectEvent) -> Unit)?
    ) {
        synchronized(callbackLock) {
            if (disposed) return
            callbacks = if (onConsumed == null && onEffectApplied == null) null else {
                Callbacks(onConsumed, onEffectApplied)
            }
        }
    }

    fun onMazeStarted() {
        pendingStartupEffects.clear()
        val effects = engine.runPerkEffects
        if (effects.quickFeetStacks > 0) {
            pendingStartupEffects.add(
                RunPerkEffectEvent(RunPerkId.QUICK_FEET, "player_speed_percent", effects.quickFeetStacks * 5)
            )
        }
        if (effects.pocketMagnetStacks > 0) {
            pendingStartupEffects.add(
                RunPerkEffectEvent(RunPerkId.POCKET_MAGNET, "magnet_radius_cells", effects.magnetRadiusBonus)
            )
        }
        dispatch()
    }

    fun onRestored() {
        pendingStartupEffects.clear()
        notifiedToken = null
        notifiedPerk = null
        notifiedSeed = null
        dispatch()
    }

    /** No snapshot allocation until a new pending marker or observer needs a notification. */
    fun dispatch() {
        if (disposed) return
        val observer = callbacks ?: return
        val onEffect = observer.onEffectApplied
        if (onEffect != null) {
            while (pendingStartupEffects.isNotEmpty()) {
                synchronized(callbackLock) {
                    if (observer !== callbacks || disposed) return
                    onEffect(pendingStartupEffects.removeFirst())
                }
            }
        }
        val perk = engine.pendingConsumedRunPerk
        if (perk == null) {
            notifiedToken = null
            notifiedPerk = null
            notifiedSeed = null
            return
        }
        val onConsumed = observer.onConsumed ?: return
        if (observer !== callbacks || disposed) return
        if (notifiedToken === observer.token && notifiedPerk == perk && notifiedSeed == engine.currentSeed) return
        val snapshot = engine.snapshot()
        synchronized(callbackLock) {
            if (observer !== callbacks || disposed) return
            notifiedToken = observer.token
            notifiedPerk = perk
            notifiedSeed = engine.currentSeed
            onConsumed(snapshot)
        }
    }

    fun dispose() {
        synchronized(callbackLock) {
            disposed = true
            callbacks = null
        }
    }
}
