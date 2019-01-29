package tech.kzen.lib.common.metadata.read

import tech.kzen.lib.common.api.model.*
import tech.kzen.lib.common.metadata.model.AttributeMetadata
import tech.kzen.lib.common.metadata.model.GraphMetadata
import tech.kzen.lib.common.metadata.model.ObjectMetadata
import tech.kzen.lib.common.metadata.model.TypeMetadata
import tech.kzen.lib.common.notation.NotationConventions
import tech.kzen.lib.common.notation.model.AttributeNotation
import tech.kzen.lib.common.notation.model.GraphNotation
import tech.kzen.lib.common.notation.model.MapAttributeNotation
import tech.kzen.lib.common.notation.model.ScalarAttributeNotation


class NotationMetadataReader(
//        private val mirrorMetadataReader: MirrorMetadataReader
) {
    fun read(graphNotation: GraphNotation): GraphMetadata {
        val builder = mutableMapOf<ObjectLocation, ObjectMetadata>()

        for (objectLocation in graphNotation.objectLocations) {
            val objectMetadata = readObject(objectLocation, graphNotation)

            builder[objectLocation] = objectMetadata
        }

        return GraphMetadata(ObjectMap(builder))
    }


    private fun readObject(
            objectLocation: ObjectLocation,
            projectNotation: GraphNotation
    ): ObjectMetadata {
        val metaAttribute =
                projectNotation.transitiveAttribute(objectLocation, NotationConventions.metaAttribute)
                ?: return ObjectMetadata(mapOf())

        val metaMapAttribute = metaAttribute as MapAttributeNotation

        val attributes = mutableMapOf<AttributeName, AttributeMetadata>()

        for (e in metaMapAttribute.values) {
            val attributeMetadata = readAttribute(objectLocation, e.value, projectNotation)

            attributes[AttributeName(e.key.asString())] = attributeMetadata
        }

        return ObjectMetadata(
//                className,
                attributes)
    }


    private fun readAttribute(
            host: ObjectLocation,
            attributeNotation: AttributeNotation,
            notationTree: GraphNotation
    ): AttributeMetadata {
        val inheritanceParent: String? =
                attributeInheritanceParent(attributeNotation)

        val inheritanceParentLocation =
                if (inheritanceParent == null) {
                    null
                }
                else {
                    notationTree.coalesce.locate(host, ObjectReference.parse(inheritanceParent))
                }

        val attributeMap = attributeNotation as? MapAttributeNotation

        val classNotation = metadataAttribute(
                NotationConventions.classAttribute, inheritanceParentLocation, attributeMap, notationTree)
        val className = (classNotation as? ScalarAttributeNotation)?.value as? String
        checkNotNull(className) { "Unknown class: $host - $attributeNotation" }

//        val valueNotation = metadataParameter(
//                "class", inheritanceParent, parameterMap, projectMetadata)

        val definerNotation = metadataAttribute(
                NotationConventions.byAttribute, inheritanceParentLocation, attributeMap, notationTree)
        val definerName = (definerNotation as? ScalarAttributeNotation)?.value as? String

        val creatorNotation = metadataAttribute(
                NotationConventions.usingAttribute, inheritanceParentLocation, attributeMap, notationTree)
        val creatorName = (creatorNotation as? ScalarAttributeNotation)?.value as? String
//        check(creatorName != null) { "Unknown creator class: $parameterNotation" }

        val genericsNotation = metadataAttribute(
                NotationConventions.ofAttribute, inheritanceParentLocation, attributeMap, notationTree)

        val genericsNames: List<TypeMetadata> =
                when (genericsNotation) {
                    null ->
                        listOf()

                    is ScalarAttributeNotation -> {
                        val value = genericsNotation.value as String
                        val reference = ObjectReference.parse(value)
                        val objectLocation = notationTree.coalesce.locate(host, reference)
                        val genericClassName = notationTree.getString(objectLocation, NotationConventions.classAttribute)

                        listOf(TypeMetadata(genericClassName, listOf()))
                    }

                    else ->
                        TODO()
                }

        val typeMetadata = TypeMetadata(
                className,
                genericsNames)

        return AttributeMetadata(
                typeMetadata, /*valueNotation,*/ definerName, creatorName)
    }


    private fun attributeInheritanceParent(
            attributeNotation: AttributeNotation
    ): String? {
        return when (attributeNotation) {
            is ScalarAttributeNotation -> {
                check(attributeNotation.value is String) {
                    "Inline '${NotationConventions.isKey}' must be String: $attributeNotation"
                }

                attributeNotation.value
            }

            is MapAttributeNotation -> {
                attributeNotation
                        .values[NotationConventions.isSegment]
                        ?.asString()
            }

            else -> null
        }
    }


    private fun metadataAttribute(
            notationPath: AttributePath,
            inheritanceParent: ObjectLocation?,
            attributeMap: MapAttributeNotation?,
            projectNotation: GraphNotation
    ): AttributeNotation? {
        val paramNotation = attributeMap?.get(notationPath)

        return if (paramNotation == null && inheritanceParent != null) {
            projectNotation.transitiveAttribute(
                    inheritanceParent, notationPath)
        }
        else {
            paramNotation
        }

//        val className = (classNotation as? ScalarParameterNotation)?.value
    }
}