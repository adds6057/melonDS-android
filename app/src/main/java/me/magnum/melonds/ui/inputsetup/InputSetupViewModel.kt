package me.magnum.melonds.ui.inputsetup

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import me.magnum.melonds.domain.model.ControllerConfiguration
import me.magnum.melonds.domain.model.Input
import me.magnum.melonds.domain.model.InputConfig
import me.magnum.melonds.domain.repositories.SettingsRepository
import me.magnum.melonds.utils.EventSharedFlow
import javax.inject.Inject

@HiltViewModel
class InputSetupViewModel @Inject constructor(private val settingsRepository: SettingsRepository) : ViewModel() {

    private val _inputConfig = MutableStateFlow(settingsRepository.getControllerConfiguration().inputMapper)
    val inputConfiguration = _inputConfig.asStateFlow()

    private val _inputUnderAssignment = MutableStateFlow<Input?>(null)
    val inputUnderAssignment = _inputUnderAssignment.asStateFlow()

    private val _onInputAssignedEvent = EventSharedFlow<Input>()
    val onInputAssignedEvent = _onInputAssignedEvent.asSharedFlow()

    // Keys used while capturing a key combination for a frontend input (hotkey)
    private val heldKeys = HashSet<Int>()
    private val capturedComboKeys = LinkedHashSet<Int>()

    fun startInputAssignment(input: Input) {
        resetComboCapture()
        _inputUnderAssignment.value = input
    }

    fun stopInputAssignment() {
        resetComboCapture()
        _inputUnderAssignment.value = null
    }

    /**
     * Whether a key combination is currently being captured (at least one key is being held down).
     */
    fun isCapturingKeyCombo(): Boolean {
        return heldKeys.isNotEmpty()
    }

    /**
     * Handles a key press while an input is being assigned. System inputs are assigned immediately to the first key that is pressed. Frontend
     * inputs (hotkeys) wait until all the pressed keys are released, which allows multiple keys to be captured as a combination.
     */
    fun onAssignmentKeyDown(key: Int) {
        val input = _inputUnderAssignment.value ?: return
        if (input.isSystemInput) {
            updateInputAssignedKey(key)
        } else {
            heldKeys.add(key)
            capturedComboKeys.add(key)
        }
    }

    fun onAssignmentKeyUp(key: Int) {
        val input = _inputUnderAssignment.value ?: return
        if (input.isSystemInput) {
            return
        }

        heldKeys.remove(key)
        if (heldKeys.isEmpty() && capturedComboKeys.isNotEmpty()) {
            val keys = capturedComboKeys.toSet()
            resetComboCapture()
            if (keys.size == 1) {
                updateInputAssignedKey(keys.first())
            } else {
                updateInputAssignedKeyCombo(keys)
            }
        }
    }

    private fun resetComboCapture() {
        heldKeys.clear()
        capturedComboKeys.clear()
    }

    private fun updateInputAssignedKeyCombo(keys: Set<Int>) {
        val inputUnderAssignment = _inputUnderAssignment.value ?: return
        setInputAssignment(inputUnderAssignment, InputConfig.Assignment.KeyCombo(null, keys))
        focusOnNextInput(inputUnderAssignment)
    }

    fun updateInputAssignedKey(key: Int) {
        val inputUnderAssignment = _inputUnderAssignment.value ?: return
        val inputType = InputConfig.Assignment.Key(null, key)
        setInputAssignment(inputUnderAssignment, inputType)
        focusOnNextInput(inputUnderAssignment)
    }

    fun updateInputAssignedAxis(axis: Int, direction: InputConfig.Assignment.Axis.Direction) {
        val inputUnderAssignment = _inputUnderAssignment.value ?: return
        val inputType = InputConfig.Assignment.Axis(null, axis, direction)
        setInputAssignment(inputUnderAssignment, inputType)
        focusOnNextInput(inputUnderAssignment)
    }

    fun clearInputAssignment(input: Input) {
        setInputAssignment(input, InputConfig.Assignment.None)
        _inputUnderAssignment.value = null
    }

    private fun setInputAssignment(input: Input, assignment: InputConfig.Assignment) {
        val inputIndex = _inputConfig.value.indexOfFirst { it.input == input }
        if (inputIndex >= 0) {
            _inputConfig.update { config ->
                config.toMutableList().apply {
                    val current = this[inputIndex]
                    var primary = current.assignment
                    var secondary = current.altAssignment
                    if (assignment == InputConfig.Assignment.None) {
                        primary = InputConfig.Assignment.None
                        secondary = InputConfig.Assignment.None
                    } else if (primary == InputConfig.Assignment.None || primary == assignment) {
                        primary = assignment
                    } else if (secondary == InputConfig.Assignment.None || secondary == assignment) {
                        secondary = assignment
                    } else {
                        secondary = assignment
                    }
                    this[inputIndex] = current.copy(assignment = primary, altAssignment = secondary)
                }.also {
                    onConfigsChanged(it)
                }
            }
        }
        resetComboCapture()
        _inputUnderAssignment.value = null
    }

    private fun onConfigsChanged(newConfig: List<InputConfig>) {
        val currentConfiguration = ControllerConfiguration(newConfig)
        settingsRepository.setControllerConfiguration(currentConfiguration)
    }

    private fun focusOnNextInput(currentInput: Input) {
        val currentInputIndex = _inputConfig.value.indexOfFirst { it.input == currentInput }
        val nextInput = _inputConfig.value.getOrNull(currentInputIndex + 1)
        if (nextInput != null) {
            _onInputAssignedEvent.tryEmit(nextInput.input)
        }
    }
}