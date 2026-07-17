package tech.kzen.lib.common.exec.task.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tech.kzen.lib.common.exec.ExecutionFailure
import tech.kzen.lib.common.exec.ExecutionRequest
import tech.kzen.lib.common.exec.ExecutionResult
import tech.kzen.lib.common.exec.ExecutionSuccess
import tech.kzen.lib.common.model.location.ObjectLocation


// SER4: kotlinx wire codec. @SerialName preserves the short wire keys. `request` / `partialResult` /
// `finalResult` encode via their SER2 serializers — the concrete-subtype ExecutionSuccessSerializer for
// `partialResult` (see ExecutionValueSerialization), the sealed-base ExecutionResultSerializer for
// `finalResult`. Both nullable results carry NO default, so a null encodes as explicit JSON null (like the
// old codec's null map value, which the JSON transport dropped either way).
@Serializable
data class TaskModel(
    @SerialName("id")
    val taskId: TaskId,
    @SerialName("location")
    val taskLocation: ObjectLocation,
    val request: ExecutionRequest,
    val state: TaskState,
    @SerialName("partial")
    val partialResult: ExecutionSuccess?,
    @SerialName("result")
    val finalResult: ExecutionResult?
) {
    //-----------------------------------------------------------------------------------------------------------------
    fun finalOrPartialResult(): ExecutionResult {
        return finalResult ?: partialResult!!
    }


    fun errorMessage(): String? {
        return when (finalResult) {
            is ExecutionFailure -> finalResult.errorMessage
            else -> null
        }
    }


    fun taskProgress(): TaskProgress? {
        val result = (finalResult ?: partialResult) as? ExecutionSuccess
            ?: return null

        @Suppress("UNCHECKED_CAST")
        val resultDetail = result.detail.get()
            ?: return null

        return TaskProgress(resultDetail)
    }
}