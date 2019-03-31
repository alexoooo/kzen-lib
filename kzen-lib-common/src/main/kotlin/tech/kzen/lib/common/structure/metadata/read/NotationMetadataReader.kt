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
import tech.kzen.lib.platform.ClassNames


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
            graphNotation: GraphNotation
    ): ObjectMetadata {
        val allAttributes = mutableSetOf<AttributeName>()

        val builder = mutableMapOf<AttributeName, AttributeMetadata>()

        val inheritanceChain = graphNotation.inheritanceChain(objectLocation)

        for (superLocation in inheritanceChain) {
            val superNotation = graphNotation.coalesce.get(superLocation)

            allAttributes.addAll(superNotation.attributes.keys)

            val metaAttribute = superNotation.get(NotationConventions.metaAttributePath)
                    as? MapAttributeNotation
                    ?: continue

            for (e in metaAttribute.values) {
                val attributeMetadata = readAttribute(objectLocation, e.value, graphNotation)
                builder[AttributeName(e.key.asString())] = attributeMetadata
            }
        }

        for (attributeName in allAttributes) {
            if (attributeName in builder ||
                    NotationConventions.isSpecial(attributeName)) {
                continue
            }

            inferMetadata(objectLocation, attributeName, graphNotation)?.let {
                builder[attributeName] = it
            }
        }

        return ObjectMetadata(builder)
    }


    private fun inferMetadata(
            objectLocation: ObjectLocation,
            attributeName: AttributeName,
            graphNotation: GraphNotation
    ): AttributeMetadata? {
        val attributeNotation = graphNotation
                .transitiveAttribute(objectLocation, AttributePath.ofAttribute(attributeName))

        if (attributeNotation is ScalarAttributeNotation) {
            val isValue = try {
                val reference = ObjectReference.parse(attributeNotation.value)
                graphNotation.coalesce.locate(objectLocation, reference)
                attributeNotation.value
            }
            catch (t: Throwable) {
//                "String"
                return null
            }

            val isLocation = graphNotation.coalesce.locate(objectLocation, ObjectReference.parse(isValue))
            val isClass = graphNotation
                    .transitiveAttribute(isLocation, NotationConventions.classAttributePath)
                    ?.asString()
                    ?: ClassNames.kotlinAny.get()

            return AttributeMetadata(
                    MapAttributeNotation(mapOf(
                            NotationConventions.isAttributeSegment to ScalarAttributeNotation(isValue))),
                    TypeMetadata(ClassName(isClass), listOf()),
                    null,
                    null)
        }

        return null
    }


    private fun readAttribute(
            host: ObjectLocation,
            attributeNotation: AttributeNotation,
            graphNotation: GraphNotation
    ): AttributeMetadata {
        val inheritanceParent: String? =
                attributeInheritanceParent(attributeNotation)

        val inheritanceParentLocation =
                if (inheritanceParent == null) {
                    null
                }
                else {
                    graphNotation.coalesce.locate(host, ObjectReference.parse(inheritanceParent))
                }

        val attributeMap = attributeNotation as? MapAttributeNotation
                ?: MapAttributeNotation.empty

        val classNotation = metadataAttribute(
                NotationConventions.classAttributePath, inheritanceParentLocation, attributeMap, graphNotation)
        val className = ((classNotation as? ScalarAttributeNotation)?.value)
                ?.let { ClassName(it) }
                ?: throw IllegalArgumentException("Unknown class: $host - $attributeNotation")

        val definerReference = attributeMap
                .values[NotationConventions.definerAttributeSegment]
                ?.asString()

        val creatorReference = attributeMap
                .values[NotationConventions.creatorAttributeSegment]
                ?.asString()

        val genericsNotation = attributeMap.values[NotationConventions.ofAttributeSegment]


        val genericsNames: List<TypeMetadata> =
                when (genericsNotation) {
                    null ->
                        listOf()

                    is ScalarAttributeNotation -> {
                        val value = genericsNotation.value
                        val reference = ObjectReference.parse(value)
                        val objectLocation = graphNotation.coalesce.locate(host, reference)
                        val genericClassName = ClassName(
                                graphNotation.getString(objectLocation, NotationConventions.classAttributePath))

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
                        .values[NotationConventions.isAttributeSegment]
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