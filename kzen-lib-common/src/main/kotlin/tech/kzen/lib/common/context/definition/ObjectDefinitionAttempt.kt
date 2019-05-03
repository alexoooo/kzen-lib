package tech.kzen.lib.common.context.definition

import tech.kzen.lib.common.model.locate.ObjectLocationSet


data class ObjectDefinitionAttempt(
        val value: ObjectDefinition?,
        val missingObjects: ObjectLocationSet,
        val errorMessage: String?
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun success(definition: ObjectDefinition) =
                ObjectDefinitionAttempt(
                        definition,
                        ObjectLocationSet.empty,
                        null)

        fun missingObjectsFailure(missingObjects: ObjectLocationSet) =
                ObjectDefinitionAttempt(
                        null,
                        missingObjects,
                        "Missing objects: $missingObjects")

        fun failure(error: String) =
                ObjectDefinitionAttempt(
                        null,
                        ObjectLocationSet.empty,
                        error)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun isError(): Boolean {
        return value == null
    }
}
