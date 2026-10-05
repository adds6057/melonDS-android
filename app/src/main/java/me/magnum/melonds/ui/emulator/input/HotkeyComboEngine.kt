package me.magnum.melonds.ui.emulator.input

import me.magnum.melonds.domain.model.Input

/**
 * Detects hotkey key combinations (e.g. SELECT + A) from a stream of physical key events.
 *
 * Keys that take part in at least one combo are not forwarded to the game immediately. Instead, they are held back for [windowMs] so that, if
 * the other key(s) of a combo are pressed in that time, the combo can fire without the game ever seeing the keys. If no combo is completed,
 * the held back key is forwarded normally. If a combo is completed after a key was already forwarded (e.g. the user held SELECT for a while
 * before pressing A), the forwarded key is released and further events from the combo keys are swallowed until they are released.
 *
 * This class has no Android dependencies so that it can be tested on the JVM. It must always be used from a single thread.
 */
class HotkeyComboEngine(
    combos: List<Combo>,
    private val scheduler: Scheduler,
    private val listener: Listener,
    private val windowMs: Long = DEFAULT_WINDOW_MS,
    private val tapReleaseDelayMs: Long = DEFAULT_TAP_RELEASE_DELAY_MS,
) {
    companion object {
        const val DEFAULT_WINDOW_MS = 70L
        const val DEFAULT_TAP_RELEASE_DELAY_MS = 40L
    }

    data class Combo(val input: Input, val keys: Set<Int>)

    fun interface Cancellable {
        fun cancel()
    }

    interface Scheduler {
        fun postDelayed(delayMs: Long, action: () -> Unit): Cancellable
    }

    interface Listener {
        /** A key that is not (or is no longer) part of a combo must be treated as a normal key press. */
        fun onForwardKeyPressed(keyCode: Int)
        fun onForwardKeyReleased(keyCode: Int)
        fun onHotkeyPressed(input: Input)
        fun onHotkeyReleased(input: Input)
    }

    private val combos = combos.filter { it.keys.size >= 2 }
    private val comboKeys = this.combos.flatMapTo(HashSet()) { it.keys }

    private val pressedKeys = HashSet<Int>()
    private val pendingKeys = HashMap<Int, Cancellable>()
    private val forwardedKeys = HashSet<Int>()
    private val suppressedKeys = HashSet<Int>()
    private val activeCombos = ArrayList<Combo>()

    /**
     * Notifies a key press. Returns true if the event was handled by the engine (held back, swallowed or used to trigger a combo), or false
     * if the key is unrelated to any combo and should be processed normally.
     */
    fun onKeyDown(keyCode: Int): Boolean {
        if (!pressedKeys.add(keyCode)) {
            // Auto-repeat of a key that is already down
            return keyCode in pendingKeys || keyCode in suppressedKeys
        }

        val triggered = combos
            .filter { keyCode in it.keys && pressedKeys.containsAll(it.keys) && it !in activeCombos }
            .maxByOrNull { it.keys.size }

        if (triggered != null) {
            triggered.keys.forEach { key ->
                pendingKeys.remove(key)?.cancel()
                if (forwardedKeys.remove(key)) {
                    listener.onForwardKeyReleased(key)
                }
                suppressedKeys.add(key)
            }
            activeCombos.add(triggered)
            listener.onHotkeyPressed(triggered.input)
            return true
        }

        if (keyCode in suppressedKeys) {
            return true
        }

        if (keyCode in comboKeys) {
            pendingKeys[keyCode] = scheduler.postDelayed(windowMs) {
                pendingKeys.remove(keyCode)
                if (keyCode in pressedKeys) {
                    forwardedKeys.add(keyCode)
                    listener.onForwardKeyPressed(keyCode)
                }
            }
            return true
        }

        return false
    }

    /**
     * Notifies a key release. Returns true if the event was handled by the engine, or false if it should be processed normally.
     */
    fun onKeyUp(keyCode: Int): Boolean {
        pressedKeys.remove(keyCode)

        val releasedCombos = activeCombos.filter { keyCode in it.keys }
        releasedCombos.forEach {
            activeCombos.remove(it)
            listener.onHotkeyReleased(it.input)
        }

        if (suppressedKeys.remove(keyCode)) {
            return true
        }

        val pending = pendingKeys.remove(keyCode)
        if (pending != null) {
            // The key was tapped before the combo window ended. Forward a press and, after a short delay, the release so that the game has
            // a chance of seeing the press (the emulator only polls input once per frame)
            pending.cancel()
            listener.onForwardKeyPressed(keyCode)
            scheduler.postDelayed(tapReleaseDelayMs) {
                if (keyCode !in pressedKeys && keyCode !in pendingKeys && keyCode !in forwardedKeys) {
                    listener.onForwardKeyReleased(keyCode)
                }
            }
            return true
        }

        if (forwardedKeys.remove(keyCode)) {
            listener.onForwardKeyReleased(keyCode)
            return true
        }

        return false
    }

    /**
     * Cancels any pending operations and releases any key that is currently being held in the game. Must be called when the engine is
     * discarded.
     */
    fun cancelAll() {
        pendingKeys.values.forEach { it.cancel() }
        pendingKeys.clear()
        forwardedKeys.toList().forEach { listener.onForwardKeyReleased(it) }
        forwardedKeys.clear()
        suppressedKeys.clear()
        activeCombos.clear()
        pressedKeys.clear()
    }
}
