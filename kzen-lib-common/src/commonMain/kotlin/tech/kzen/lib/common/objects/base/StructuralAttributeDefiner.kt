package tech.kzen.lib.common.objects.base

import tech.kzen.lib.common.api.AttributeDefiner
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.definition.*
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.model.structure.notation.AttributeNotation
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.platform.ClassNames


@Reflect
object StructuralAttributeDefiner: AttributeDefiner
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun define(
        objectLocation: ObjectLocation,
        attributeName: AttributeName,
        graphStructure: GraphStructure,
        partialGraphDefinition: GraphDefinition,
        partialGraphInstance: GraphInstance
    ): AttributeDefinitionAttempt {
        val objectNotation = graphStructure.graphNotation.coalesce[objectLocation]
            ?: return AttributeDefinitionAttempt.failure("Unknown object notation: $objectLocation")

        val attributeNotation = objectNotation.attributes.map[attributeName]
            ?: graphStructure.graphNotation.firstAttribute(
                objectLocation, attributeName.asAttributePath())
            ?: return AttributeDefinitionAttempt.failure("Unknown attribute: $objectLocation - $attributeName")

        val objectMetadata = graphStructure.graphMetadata.objectMetadata[objectLocation]
            ?: return AttributeDefinitionAttempt.failure("Unknown object metadata: $objectLocation")

        val attributeMetadata = objectMetadata.attributes.map[attributeName]

        val typeMetadata = attributeMetadata?.type
            ?: TypeMetadata.any

        return defineRecursively(attributeNotation, typeMetadata)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun defineRecursively(
        attributeNotation: AttributeNotation,
        typeMetadata: TypeMetadata
    ): AttributeDefinitionAttempt {
        return when (attributeNotation) {
            is ScalarAttributeNotation ->
                defineScalar(attributeNotation, typeMetadata)

            is ListAttributeNotation ->
                defineList(attributeNotation, typeMetadata)

            is MapAttributeNotation ->
                defineMap(attributeNotation, typeMetadata)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun defineScalar(
        attributeNotation: ScalarAttributeNotation,
        typeMetadata: TypeMetadata
    ):
        AttributeDefinitionAttempt
    {
        val className = typeMetadata.className

        if (className == ClassNames.kotlinString) {
            return AttributeDefinitionAttempt.success(
                ValueAttributeDefinition(attributeNotation.value))
        }

        if (className == ClassNames.kotlinBoolean) {
            if (attributeNotation.value == "true") {
                return AttributeDefinitionAttempt.success(
                    ValueAttributeDefinition(true))
            }
            else if (attributeNotation.value == "false") {
                return AttributeDefinitionAttempt.success(
                    ValueAttributeDefinition(false))
            }
            return AttributeDefinitionAttempt.failure(
                "Boolean expected: ${attributeNotation.value}")
        }

        if (className == ClassNames.kotlinInt) {
            val value = attributeNotation.value.toIntOrNull()
            return value
                ?.let { AttributeDefinitionAttempt.success(ValueAttributeDefinition(it)) }
                ?: AttributeDefinitionAttempt.failure("Integer expected: ${attributeNotation.value}")
        }

        if (className == ClassNames.kotlinLong) {
            val value = attributeNotation.value.toLongOrNull()
            return value
                ?.let { AttributeDefinitionAttempt.success(ValueAttributeDefinition(it)) }
                ?: AttributeDefinitionAttempt.failure("Long expected: ${attributeNotation.value}")
        }

        if (className == ClassNames.kotlinDouble) {
            val value = attributeNotation.value.toDoubleOrNull()
            return value
                ?.let { AttributeDefinitionAttempt.success(ValueAttributeDefinition(it)) }
                ?: AttributeDefinitionAttempt.failure("Number expected: ${attributeNotation.value}")
        }

        return AttributeDefinitionAttempt.success(
            ReferenceAttributeDefinition(
                ObjectReference.parse(attributeNotation.value),
                false,
                typeMetadata.nullable))
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun defineList(
        attributeNotation: ListAttributeNotation,
        typeMetadata: TypeMetadata
    ):
        AttributeDefinitionAttempt
    {
        val listGeneric = typeMetadata.generics[0]

        val definitions = mutableListOf<AttributeDefinition>()
        for (value in attributeNotation.values) {
            @Suppress("MoveVariableDeclarationIntoWhen", "RedundantSuppression")
            val definitionAttempt = defineRecursively(value, listGeneric)

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
        attributeNotation: MapAttributeNotation,
        typeMetadata: TypeMetadata
    ):
        AttributeDefinitionAttempt
    {
        val keyGeneric = typeMetadata.generics[0]
        require(keyGeneric.className == ClassNames.kotlinString)
        val valueGeneric = typeMetadata.generics[1]

        val definitions = mutableMapOf<String, AttributeDefinition>()
        for (entry in attributeNotation.map) {
            @Suppress("MoveVariableDeclarationIntoWhen", "RedundantSuppression")
            val definitionAttempt = defineRecursively(entry.value, valueGeneric)

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