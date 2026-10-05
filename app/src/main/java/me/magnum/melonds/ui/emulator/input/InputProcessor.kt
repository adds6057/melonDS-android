package me.magnum.melonds.ui.emulator.input

import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import me.magnum.melonds.domain.model.ControllerConfiguration
import me.magnum.melonds.domain.model.Input
import me.magnum.melonds.domain.model.InputConfig
import kotlin.math.absoluteValue

class InputProcessor(private val controllerConfiguration: ControllerConfiguration, private val systemInputListener: IInputListener, private val frontendInputListener: IInputListener) : INativeInputListener {

    private val axisStates: Map<Axis, AxisState>

    private val handler = Handler(Looper.getMainLooper())
    private val comboEngine: HotkeyComboEngine? = controllerConfiguration.getKeyCombos().takeIf { it.isNotEmpty() }?.let { keyCombos ->
        HotkeyComboEngine(
            combos = keyCombos.map { HotkeyComboEngine.Combo(it.first, it.second) },
            scheduler = object : HotkeyComboEngine.Scheduler {
                override fun postDelayed(delayMs: Long, action: () -> Unit): HotkeyComboEngine.Cancellable {
                    val runnable = Runnable(action)
                    handler.postDelayed(runnable, delayMs)
                    return HotkeyComboEngine.Cancellable { handler.removeCallbacks(runnable) }
                }
            },
            listener = object : HotkeyComboEngine.Listener {
                override fun onForwardKeyPressed(keyCode: Int) {
                    dispatchKey(keyCode, KeyEvent.ACTION_DOWN)
                }

                override fun onForwardKeyReleased(keyCode: Int) {
                    dispatchKey(keyCode, KeyEvent.ACTION_UP)
                }

                override fun onHotkeyPressed(input: Input) {
                    frontendInputListener.onKeyPress(input)
                }

                override fun onHotkeyReleased(input: Input) {
                    frontendInputListener.onKeyReleased(input)
                }
            },
        )
    }

    init {
        val axis = controllerConfiguration.inputMapper.flatMap { inputConfig ->
            listOf(inputConfig.assignment, inputConfig.altAssignment)
        }.mapNotNull { assignment ->
            (assignment as? InputConfig.Assignment.Axis)?.let {
                Axis(it.deviceId, it.axisCode, it.direction)
            }
        }

        axisStates = axis.associateWith { AxisState(0f, false) }
    }

    /**
     * Must be called when this processor is discarded so that pending combo operations are cancelled and no key is left stuck in the game.
     */
    fun release() {
        comboEngine?.cancelAll()
    }

    override fun onKeyEvent(keyEvent: KeyEvent): Boolean {
        val engine = comboEngine
        if (engine != null) {
            val handledByCombo = when (keyEvent.action) {
                KeyEvent.ACTION_DOWN -> engine.onKeyDown(keyEvent.keyCode)
                KeyEvent.ACTION_UP -> engine.onKeyUp(keyEvent.keyCode)
                else -> false
            }
            if (handledByCombo) {
                return true
            }
        }

        return dispatchKey(keyEvent.keyCode, keyEvent.action)
    }

    private fun dispatchKey(keyCode: Int, action: Int): Boolean {
        val input = controllerConfiguration.keyToInput(keyCode) ?: return false
        val listener = if (input.isSystemInput) systemInputListener else frontendInputListener
        when (action) {
            KeyEvent.ACTION_DOWN -> {
                listener.onKeyPress(input)
                return true
            }
            KeyEvent.ACTION_UP -> {
                listener.onKeyReleased(input)
                return true
            }
        }
        return false
    }

    override fun onMotionEvent(motionEvent: MotionEvent): Boolean {
        if (motionEvent.isFromSource(InputDevice.SOURCE_CLASS_JOYSTICK)) {
            val deviceAxis = axisStates.filterKeys { it.deviceId == null || it.deviceId == motionEvent.deviceId }
            deviceAxis.forEach {
                val axis = it.key
                val axisState = it.value

                val newValue = motionEvent.getAxisValue(axis.axisCode)
                val clampedValue = when (axis.direction) {
                    InputConfig.Assignment.Axis.Direction.POSITIVE -> newValue.coerceAtLeast(0f)
                    InputConfig.Assignment.Axis.Direction.NEGATIVE -> newValue.coerceAtMost(0f)
                }

                if (axisState.shouldToggleFor(newValue = clampedValue)) {
                    controllerConfiguration.axisToInput(axis.axisCode, axis.direction)?.let { input ->
                        if (axisState.active) {
                            axisState.active = false
                            if (input.isSystemInput) {
                                systemInputListener.onKeyReleased(input)
                            } else {
                                frontendInputListener.onKeyReleased(input)
                            }
                        } else {
                            axisState.active = true
                            if (input.isSystemInput) {
                                systemInputListener.onKeyPress(input)
                            } else {
                                frontendInputListener.onKeyPress(input)
                            }
                        }
                    }
                }
                axisState.value = clampedValue
            }
            return deviceAxis.isNotEmpty()
        } else {
            return false
        }
    }

    private data class Axis(
        val deviceId: Int?,
        val axisCode: Int,
        val direction: InputConfig.Assignment.Axis.Direction,
    )

    private data class AxisState(
        var value: Float,
        var active: Boolean,
    ) {
        fun shouldToggleFor(newValue: Float): Boolean {
            return if (active) {
                newValue.absoluteValue < 0.5f
            } else {
                newValue.absoluteValue >= 0.5f
            }
        }
    }
}