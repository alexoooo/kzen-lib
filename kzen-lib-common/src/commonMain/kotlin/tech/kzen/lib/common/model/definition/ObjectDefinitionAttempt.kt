package tech.kzen.lib.common.model.definition

import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.location.ObjectLocationSet


sealed class ObjectDefinitionAttempt {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun success(definition: ObjectDefinition): ObjectDefinitionSuccess {
            return ObjectDefinitionSuccess(definition)
        }


        fun missingObjectsFailure(
                errorMessage: String,
                attributeErrors: Map<AttributeName, String>,
                missingObjects: ObjectLocationSet,
                partialDefinition: ObjectDefinition
        ): ObjectDefinitionFailure {
            return ObjectDefinitionFailure(
                    partialDefinition,
                    missingObjects,
                    errorMessage,
                    attributeErrors)
        }


        fun failure(
                errorMessage: String,
                attributeErrors: Map<AttributeName, String>,
                partialDefinition: ObjectDefinition?
        ): ObjectDefinitionFailure {
            return ObjectDefinitionFailure(
                    partialDefinition,
                    ObjectLocationSet.empty,
                    errorMessage,
                    attributeErrors)
        }
    }
}


data class ObjectDefinitionSuccess(
        val value: ObjectDefinition
): ObjectDefinitionAttempt()


data class ObjectDefinitionFailure(
        val partial: ObjectDefinition?,
        val missingObjects: ObjectLocationSet,
        val errorMessage: String,
        val attributeErrors: Map<AttributeName, String>
): ObjectDefinitionAttempt()