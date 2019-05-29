package tech.kzen.lib.common.context.definition


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