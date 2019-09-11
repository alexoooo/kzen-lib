package tech.kzen.lib.common.model.definition


data class AttributeDefinitionAttempt(
        val value: AttributeDefinition?,
        val errorMessage: String?
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun success(definition: AttributeDefinition) =
                AttributeDefinitionAttempt(
                        definition,
                        null)

        fun failure(error: String) =
                AttributeDefinitionAttempt(
                        null,
                        error)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun isError(): Boolean {
        return value == null
    }
}