package me.magnum.melonds.impl.dtos.input

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.magnum.melonds.domain.model.Input
import me.magnum.melonds.domain.model.InputConfig

@Serializable
data class InputConfigDto(
    @SerialName("input") val input: Input,
    @SerialName("assignment") val assignment: AssignmentDto,
    @SerialName("altAssignment") val altAssignment: AssignmentDto = AssignmentDto.None,
) {

    @Serializable
    sealed class AssignmentDto {
        @SerialName("deviceId") abstract val deviceId: Int?

        @Serializable
        @SerialName("none")
        data object None : AssignmentDto() {
            override val deviceId: Int? = null
        }

        @Serializable
        @SerialName("key")
        class Key(
            override val deviceId: Int?,
            @SerialName("keyCode") val keyCode: Int,
        ) : AssignmentDto()

        @Serializable
        @SerialName("keyCombo")
        class KeyCombo(
            override val deviceId: Int?,
            @SerialName("keyCodes") val keyCodes: List<Int>,
        ) : AssignmentDto()

        @Serializable
        @SerialName("axis")
        class Axis(
            override val deviceId: Int?,
            @SerialName("axisCode") val axisCode: Int,
            @SerialName("direction") val direction: InputConfig.Assignment.Axis.Direction,
        ) : AssignmentDto()
    }

    companion object {
        fun fromInputConfig(inputConfig: InputConfig): InputConfigDto {
            return InputConfigDto(
                input = inputConfig.input,
                assignment = assignmentToDto(inputConfig.assignment),
                altAssignment = assignmentToDto(inputConfig.altAssignment),
            )
        }

        private fun assignmentToDto(assignment: InputConfig.Assignment): AssignmentDto {
            return when (assignment) {
                is InputConfig.Assignment.None -> AssignmentDto.None
                is InputConfig.Assignment.Key -> AssignmentDto.Key(assignment.deviceId, assignment.keyCode)
                is InputConfig.Assignment.KeyCombo -> AssignmentDto.KeyCombo(assignment.deviceId, assignment.keyCodes.toList())
                is InputConfig.Assignment.Axis -> AssignmentDto.Axis(assignment.deviceId, assignment.axisCode, assignment.direction)
            }
        }
    }

    fun toInputConfig(): InputConfig {
        return InputConfig(
            input = input,
            assignment = assignmentFromDto(assignment),
            altAssignment = assignmentFromDto(altAssignment),
        )
    }

    private fun assignmentFromDto(dto: AssignmentDto): InputConfig.Assignment {
        return when (dto) {
            is AssignmentDto.None -> InputConfig.Assignment.None
            is AssignmentDto.Key -> InputConfig.Assignment.Key(dto.deviceId, dto.keyCode)
            is AssignmentDto.KeyCombo -> InputConfig.Assignment.KeyCombo(dto.deviceId, dto.keyCodes.toSet())
            is AssignmentDto.Axis -> InputConfig.Assignment.Axis(dto.deviceId, dto.axisCode, dto.direction)
        }
    }
}