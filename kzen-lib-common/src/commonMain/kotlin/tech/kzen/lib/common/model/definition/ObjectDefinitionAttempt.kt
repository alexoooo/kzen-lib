package tech.kzen.lib.common.model.definition

import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
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
                partialDefinition: ObjectDefinition,
                attributeFailures: Map<AttributePath, AttributeDefinitionFailure> = mapOf()
        ): ObjectDefinitionFailure {
            return ObjectDefinitionFailure(
                    partialDefinition,
                    missingObjects,
                    errorMessage,
                    attributeErrors,
                    attributeFailures)
        }


        fun failure(
                errorMessage: String,
                attributeErrors: Map<AttributeName, String>,
                partialDefinition: ObjectDefinition?,
                attributeFailures: Map<AttributePath, AttributeDefinitionFailure> = mapOf()
        ): ObjectDefinitionFailure {
            return ObjectDefinitionFailure(
                    partialDefinition,
                    ObjectLocationSet.empty,
                    errorMessage,
                    attributeErrors,
                    attributeFailures)
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
        val attributeErrors: Map<AttributeName, String>,

        /**
         * Machine-readable sibling of [attributeErrors], keyed by [AttributePath] because transitive
         *  causes live at nested paths (e.g. `addends.0`). Never a replacement — [attributeErrors] stays
         *  the display surface.
         */
        val attributeFailures: Map<AttributePath, AttributeDefinitionFailure> = mapOf()
): ObjectDefinitionAttempt()
