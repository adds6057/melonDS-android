package me.magnum.melonds.ui.emulator.input

import me.magnum.melonds.domain.model.Input
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HotkeyComboEngineTest {

    private class FakeScheduler : HotkeyComboEngine.Scheduler {
        private class Task(val at: Long, val action: () -> Unit) {
            var cancelled = false
        }

        private var now = 0L
        private val tasks = mutableListOf<Task>()

        override fun postDelayed(delayMs: Long, action: () -> Unit): HotkeyComboEngine.Cancellable {
            val task = Task(now + delayMs, action)
            tasks.add(task)
            return HotkeyComboEngine.Cancellable { task.cancelled = true }
        }

        fun advance(ms: Long) {
            val target = now + ms
            while (true) {
                val next = tasks.filter { !it.cancelled && it.at <= target }.minByOrNull { it.at } ?: break
                now = next.at
                tasks.remove(next)
                next.action()
            }
            now = target
        }
    }

    private class Recorder : HotkeyComboEngine.Listener {
        val log = mutableListOf<String>()
        override fun onForwardKeyPressed(keyCode: Int) { log.add("down($keyCode)") }
        override fun onForwardKeyReleased(keyCode: Int) { log.add("up($keyCode)") }
        override fun onHotkeyPressed(input: Input) { log.add("hot+${input.name}") }
        override fun onHotkeyReleased(input: Input) { log.add("hot-${input.name}") }
    }

    private val scheduler = FakeScheduler()
    private val recorder = Recorder()
    private val engine = HotkeyComboEngine(
        combos = listOf(
            HotkeyComboEngine.Combo(Input.QUICK_SAVE, setOf(SELECT, A)),
            HotkeyComboEngine.Combo(Input.FAST_FORWARD, setOf(START, R1)),
        ),
        scheduler = scheduler,
        listener = recorder,
    )

    @Test
    fun comboPressedWithinWindowDoesNotLeakKeysToGame() {
        assertTrue(engine.onKeyDown(SELECT))
        scheduler.advance(30)
        assertTrue(engine.onKeyDown(A))
        scheduler.advance(200)
        assertTrue(engine.onKeyUp(A))
        assertTrue(engine.onKeyUp(SELECT))
        scheduler.advance(200)

        assertEquals(listOf("hot+QUICK_SAVE", "hot-QUICK_SAVE"), recorder.log)
    }

    @Test
    fun keyHeldLongerThanWindowIsReleasedWhenComboCompletes() {
        engine.onKeyDown(SELECT)
        scheduler.advance(500)
        engine.onKeyDown(A)
        engine.onKeyUp(A)
        engine.onKeyUp(SELECT)

        assertEquals(listOf("down($SELECT)", "up($SELECT)", "hot+QUICK_SAVE", "hot-QUICK_SAVE"), recorder.log)
    }

    @Test
    fun comboKeyPressedAloneIsForwardedToGame() {
        engine.onKeyDown(SELECT)
        scheduler.advance(100)
        engine.onKeyUp(SELECT)

        assertEquals(listOf("down($SELECT)", "up($SELECT)"), recorder.log)
    }

    @Test
    fun quickTapOfComboKeyIsStillSeenByGame() {
        engine.onKeyDown(SELECT)
        scheduler.advance(20)
        engine.onKeyUp(SELECT)
        assertEquals(listOf("down($SELECT)"), recorder.log)

        scheduler.advance(100)
        assertEquals(listOf("down($SELECT)", "up($SELECT)"), recorder.log)
    }

    @Test
    fun unrelatedKeysAreNotConsumed() {
        assertFalse(engine.onKeyDown(OTHER))
        assertFalse(engine.onKeyUp(OTHER))
        assertEquals(emptyList<String>(), recorder.log)
    }

    @Test
    fun comboCanBeTriggeredRepeatedly() {
        repeat(2) {
            engine.onKeyDown(START)
            engine.onKeyDown(R1)
            engine.onKeyUp(R1)
            engine.onKeyUp(START)
        }
        scheduler.advance(300)

        assertEquals(listOf("hot+FAST_FORWARD", "hot-FAST_FORWARD", "hot+FAST_FORWARD", "hot-FAST_FORWARD"), recorder.log)
    }

    @Test
    fun cancelAllReleasesForwardedKeys() {
        engine.onKeyDown(SELECT)
        scheduler.advance(100)
        engine.onKeyDown(R1)
        engine.cancelAll()
        scheduler.advance(500)

        assertEquals(listOf("down($SELECT)", "up($SELECT)"), recorder.log)
    }

    private companion object {
        const val SELECT = 1
        const val A = 2
        const val START = 3
        const val R1 = 4
        const val OTHER = 5
    }
}
