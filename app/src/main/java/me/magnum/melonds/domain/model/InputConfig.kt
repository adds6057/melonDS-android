package me.magnum.melonds.domain.model

data class InputConfig(
    val input: Input,
    val assignment: Assignment = Assignment.None,
    val altAssignment: Assignment = Assignment.None,
) {

    sealed class Assignment(open val deviceId: Int?) {
        data object None : Assignment(null)
        data class Key(override val deviceId: Int?, val keyCode: Int) : Assignment(deviceId)

        /**
         * Assignment that requires multiple keys to be held down at the same time (e.g. SELECT + A). Only meaningful for
         * frontend inputs (hotkeys), since system inputs are always mapped to a single key.
         */
        data class KeyCombo(override val deviceId: Int?, val keyCodes: Set<Int>) : Assignment(deviceId)
        data class Axis(override val deviceId: Int?, val axisCode: Int, val direction: Direction) : Assignment(deviceId) {
            enum class Direction {
                POSITIVE,
                NEGATIVE,
            }
        }
    }

    fun hasKeyAssigned(): Boolean {
        return assignment != Assignment.None || altAssignment != Assignment.None
    }
}