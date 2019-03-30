package tech.kzen.lib.common.structure.metadata.read

import tech.kzen.lib.common.api.model.*
import tech.kzen.lib.common.structure.metadata.model.AttributeMetadata
import tech.kzen.lib.common.structure.metadata.model.GraphMetadata
import tech.kzen.lib.common.structure.metadata.model.ObjectMetadata
import tech.kzen.lib.common.structure.metadata.model.TypeMetadata
import tech.kzen.lib.common.structure.notation.NotationConventions
import tech.kzen.lib.common.structure.notation.model.AttributeNotation
import tech.kzen.lib.common.structure.notation.model.GraphNotation
import tech.kzen.lib.common.structure.notation.model.MapAttributeNotation
import tech.kzen.lib.common.structure.notation.model.ScalarAttributeNotation
import tech.kzen.lib.platform.ClassName


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
        val builder = mutableMapOf<AttributeName, AttributeMetadata>()

        val inheritanceChain = projectNotation.inheritanceChain(objectLocation)

        for (superLocation in inheritanceChain) {
            val metaAttribute = projectNotation
                    .directAttribute(superLocation, NotationConventions.metaAttribute)
                    as? MapAttributeNotation
                    ?: continue

            for (e in metaAttribute.values) {
                val attributeMetadata = readAttribute(objectLocation, e.value, projectNotation)
                builder[AttributeName(e.key.asString())] = attributeMetadata
            }
        }

        return ObjectMetadata(builder)
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
                ?: MapAttributeNotation.empty

        val classNotation = metadataAttribute(
                NotationConventions.classAttribute, inheritanceParentLocation, attributeMap, notationTree)
        val className = ((classNotation as? ScalarAttributeNotation)?.value)
                ?.let { ClassName(it) }
                ?: throw IllegalArgumentException("Unknown class: $host - $attributeNotation")

        val definerReference = attributeMap
                .values[NotationConventions.definerSegment]
                ?.asString()

        val creatorReference = attributeMap
                .values[NotationConventions.creatorSegment]
                ?.asString()

        val genericsNotation = attributeMap.values[NotationConventions.ofSegment]


        val genericsNames: List<TypeMetadata> =
                when (genericsNotation) {
                    null ->
                        listOf()

                    is ScalarAttributeNotation -> {
                        val value = genericsNotation.value
                        val reference = ObjectReference.parse(value)
                        val objectLocation = notationTree.coalesce.locate(host, reference)
                        val genericClassName = ClassName(
                                notationTree.getString(objectLocation, NotationConventions.classAttribute))

                        listOf(TypeMetadata(genericClassName, listOf()))
                    }

                    else ->
                        TODO()
                }

        val typeMetadata = TypeMetadata(
                className,
                genericsNames)

        return AttributeMetadata(
                attributeMap,
                typeMetadata,
                definerReference?.let { ObjectReference.parse(it) },
                creatorReference?.let { ObjectReference.parse(it) })
    }


    private fun attributeInheritanceParent(
            attributeNotation: AttributeNotation
    ): String? {
        return when (attributeNotation) {
            is ScalarAttributeNotation -> {
//                check(attributeNotation.value is String) {
//                    "Inline '${NotationConventions.isKey}' must be String: $attributeNotation"
//                }

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
            attributePath: AttributePath,
            inheritanceParent: ObjectLocation?,
            attributeMap: MapAttributeNotation,
            projectNotation: GraphNotation
    ): AttributeNotation? {
        val paramNotation = attributeMap.get(attributePath)

        return if (paramNotation == null && inheritanceParent != null) {
            projectNotation.transitiveAttribute(
                    inheritanceParent, attributePath)
        }
        else {
            paramNotation
        }

//        val className = (classNotation as? ScalarParameterNotation)?.value
    }
}