package tech.kzen.lib.common.model.definition

import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.structure.metadata.ObjectMetadata


data class ObjectDefinitionReference(
    val objectReference: ObjectReference,
    val attributePath: AttributePath?
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun ofAttribute(objectReference: ObjectReference, attributePath: AttributePath): ObjectDefinitionReference {
            return ObjectDefinitionReference(objectReference, attributePath)
        }

        fun ofCreatorRelated(objectReference: ObjectReference): ObjectDefinitionReference {
            return ObjectDefinitionReference(objectReference, null)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun isNullable(hostObjectMetadata: ObjectMetadata): Boolean {
        val attributePath = attributePath
            ?: return false

        val attributeTypeMetadata = hostObjectMetadata.attributes[attributePath.attribute]?.type
            ?: return false

        return attributeTypeMetadata.nullable
    }
}
