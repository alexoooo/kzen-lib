package tech.kzen.lib.common.objects.general

import tech.kzen.lib.common.api.AttributeDefiner
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.definition.*
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.location.AttributeLocation
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.model.structure.notation.AttributeNotation
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.objects.base.StructuralAttributeDefiner
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.platform.ClassNames


@Reflect
object WeakAttributeDefiner: AttributeDefiner {
    //-----------------------------------------------------------------------------------------------------------------
//    val objectName = ObjectName("Nominal")


    //-----------------------------------------------------------------------------------------------------------------
    override fun define(
        objectLocation: ObjectLocation,
        attributeName: AttributeName,
        graphStructure: GraphStructure,
        partialGraphDefinition: GraphDefinition,
        partialGraphInstance: GraphInstance
    ): AttributeDefinitionAttempt {
        val objectNotation = graphStructure.graphNotation.coalesce[objectLocation]!!

        val attributeNotation = objectNotation.attributes.values[attributeName]
            ?: graphStructure.graphNotation.firstAttribute(
                objectLocation, attributeName.asAttributePath())
            ?: return AttributeDefinitionAttempt.failure(
                "Unknown attribute: $objectLocation - $attributeName")

        val typeMetadata = graphStructure
            .graphMetadata
            .objectMetadata[objectLocation]
            ?.attributes
            ?.get(attributeName)
            ?.type
            ?: TypeMetadata.any

        return defineRecursively(
            AttributeLocation(objectLocation, AttributePath.ofName(attributeName)),
            attributeNotation,
            typeMetadata)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun defineRecursively(
        attributeLocation: AttributeLocation,
        attributeNotation: AttributeNotation,
        typeMetadata: TypeMetadata
    ): AttributeDefinitionAttempt {
        return when (attributeNotation) {
            is ScalarAttributeNotation ->
                defineScalar(attributeLocation, attributeNotation, typeMetadata)

            is ListAttributeNotation ->
                defineList(attributeLocation, attributeNotation, typeMetadata)

            is MapAttributeNotation ->
                defineMap(attributeLocation, attributeNotation, typeMetadata)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun defineScalar(
        attributeLocation: AttributeLocation,
        scalarAttributeNotation: ScalarAttributeNotation,
        typeMetadata: TypeMetadata
    ): AttributeDefinitionAttempt {
        val attributeNullable = typeMetadata.nullable

        val objectReference = scalarAttributeNotation.asString().let { ObjectReference.parse(it) }

        if (objectReference.isEmpty()) {
            if (attributeNullable) {
                return AttributeDefinitionAttempt.success(
                    ReferenceAttributeDefinition(
                        null,
                        weak = true,
                        nullable = true))
            }

            return AttributeDefinitionAttempt.failure("Empty object reference - $attributeLocation")
        }

        return AttributeDefinitionAttempt.success(
            ReferenceAttributeDefinition(
                objectReference,
                weak = true,
                nullable = attributeNullable))
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun defineList(
        attributeLocation: AttributeLocation,
        attributeNotation: ListAttributeNotation,
        typeMetadata: TypeMetadata
    ): AttributeDefinitionAttempt {
        val definitions = mutableListOf<AttributeDefinition>()
        val itemType = typeMetadata.generics[0]

        for ((i, itemAttributeNotation) in attributeNotation.values.withIndex()) {
            val itemLocation = attributeLocation.nest(AttributeSegment.ofIndex(i))

            @Suppress("MoveVariableDeclarationIntoWhen", "RedundantSuppression")
            val definitionAttempt = defineRecursively(itemLocation, itemAttributeNotation, itemType)

            when (definitionAttempt) {
                is AttributeDefinitionSuccess -> {
                    definitions.add(definitionAttempt.value)
                }

                is AttributeDefinitionFailure -> {
                    return definitionAttempt
                }
            }
        }

        return AttributeDefinitionAttempt.success(
            ListAttributeDefinition(definitions))
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun defineMap(
        attributeLocation: AttributeLocation,
        attributeNotation: MapAttributeNotation,
        typeMetadata: TypeMetadata
    ):
        AttributeDefinitionAttempt
    {
        val keyGeneric = typeMetadata.generics[0]
        require(keyGeneric.className == ClassNames.kotlinString)
        val valueGeneric = typeMetadata.generics[1]

        val definitions = mutableMapOf<String, AttributeDefinition>()
        for (entry in attributeNotation.values) {
            val entryLocation = attributeLocation.nest(entry.key)

            @Suppress("MoveVariableDeclarationIntoWhen", "RedundantSuppression")
            val definitionAttempt = defineRecursively(entryLocation, entry.value, valueGeneric)

            when (definitionAttempt) {
                is AttributeDefinitionSuccess -> {
                    definitions[entry.key.asKey()] = definitionAttempt.value
                }

                is AttributeDefinitionFailure -> {
                    return definitionAttempt
                }
            }
        }

        return AttributeDefinitionAttempt.success(
            MapAttributeDefinition(definitions))
    }
}