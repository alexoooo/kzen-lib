package tech.kzen.lib.common.definition


data class ObjectDefinitionAttempt(
//        val project: ObjectGraphDefinition,
        val value: ObjectDefinition?,
        val missingObjects: Set<String>,
        val errorMessage: String?
) {
    companion object {
        fun success(definition: ObjectDefinition) =
                ObjectDefinitionAttempt(
                        definition,
                        setOf(),
                        null)

        fun missingObjectsFailure(missingObjects: Set<String>) =
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
