package tech.kzen.lib.common.model.definition


sealed class AttributeDefinitionAttempt {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun success(definition: AttributeDefinition): AttributeDefinitionSuccess {
            return AttributeDefinitionSuccess(definition)
        }

        fun failure(error: String): AttributeDefinitionFailure {
            return AttributeDefinitionFailure(error)
        }
    }
}


data class AttributeDefinitionSuccess(
    val value: AttributeDefinition
): AttributeDefinitionAttempt()


data class AttributeDefinitionFailure(
    val errorMessage: String
): AttributeDefinitionAttempt()