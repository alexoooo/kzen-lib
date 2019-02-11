package tech.kzen.lib.common.definition

import tech.kzen.lib.common.api.model.ObjectLocation


data class ObjectDefinitionAttempt(
        val value: ObjectDefinition?,
        val missingObjects: Set<ObjectLocation>,
        val errorMessage: String?
) {
    companion object {
        fun success(definition: ObjectDefinition) =
                ObjectDefinitionAttempt(
                        definition,
                        setOf(),
                        null)

        fun missingObjectsFailure(missingObjects: Set<ObjectLocation>) =
                ObjectDefinitionAttempt(
                        null,
                        missingObjects,
                        "Missing objects: $missingObjects")

        fun failure(error: String) =
                ObjectDefinitionAttempt(
                        null,
                        setOf(),
                        error)
    }


    fun isError() =
            value == null
//            errorMessage != null ||
//            missingObjects.isNotEmpty()
}
