package tech.kzen.lib.common.service.metadata

import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributeNameMap
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.locate.ObjectLocationMap
import tech.kzen.lib.common.model.locate.ObjectReference
import tech.kzen.lib.common.model.locate.ObjectReferenceHost
import tech.kzen.lib.common.model.structure.metadata.AttributeMetadata
import tech.kzen.lib.common.model.structure.metadata.GraphMetadata
import tech.kzen.lib.common.model.structure.metadata.ObjectMetadata
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.model.structure.notation.AttributeNotation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.service.notation.NotationConventions
import tech.kzen.lib.platform.ClassName
import tech.kzen.lib.platform.ClassNames
import tech.kzen.lib.platform.collect.persistentMapOf
import tech.kzen.lib.platform.collect.toPersistentMap


class NotationMetadataReader(
//        private val mirrorMetadataReader: MirrorMetadataReader
) {
    fun read(graphNotation: GraphNotation): GraphMetadata {
        val builder = mutableMapOf<ObjectLocation, ObjectMetadata>()

        for (objectLocation in graphNotation.objectLocations) {
            val objectMetadata = readObject(objectLocation, graphNotation)

            builder[objectLocation] = objectMetadata
        }

        return GraphMetadata(ObjectLocationMap(builder.toPersistentMap()))
    }


    private fun readObject(
            objectLocation: ObjectLocation,
            graphNotation: GraphNotation
    ): ObjectMetadata {
        val allAttributes = mutableSetOf<AttributeName>()

        val builder = mutableMapOf<AttributeName, AttributeMetadata>()

        val inheritanceChain = graphNotation.inheritanceChain(objectLocation)

        val objectReferenceHost = ObjectReferenceHost.ofLocation(objectLocation)

        for (superLocation in inheritanceChain) {
            val superNotation = graphNotation.coalesce[superLocation]
                    ?: continue

            allAttributes.addAll(superNotation.attributes.values.keys)

            val metaAttribute = superNotation.get(NotationConventions.metaAttributePath)
                    as? MapAttributeNotation
                    ?: continue

            for (e in metaAttribute.values) {
                val attributeMetadata = readAttribute(e.value, objectReferenceHost, graphNotation)
                builder[AttributeName(e.key.asString())] = attributeMetadata
            }
        }

        for (attributeName in allAttributes) {
            if (attributeName in builder ||
                    NotationConventions.isSpecial(attributeName)) {
                continue
            }

            inferMetadata(objectLocation, attributeName, objectReferenceHost, graphNotation)?.let {
                builder[attributeName] = it
            }
        }

        return ObjectMetadata(AttributeNameMap(builder.toPersistentMap()))
    }


    private fun inferMetadata(
            objectLocation: ObjectLocation,
            attributeName: AttributeName,
            objectReferenceHost: ObjectReferenceHost,
            graphNotation: GraphNotation
    ): AttributeMetadata? {
        val attributeNotation = graphNotation
                .transitiveAttribute(objectLocation, AttributePath.ofName(attributeName))

        if (attributeNotation is ScalarAttributeNotation) {
            val isValue = try {
                val reference = ObjectReference.parse(attributeNotation.value)
                graphNotation.coalesce.locate(reference, objectReferenceHost)
                attributeNotation.value
            }
            catch (t: Throwable) {
//                "String"
                return null
            }

            val isLocation = graphNotation.coalesce.locate(
                    ObjectReference.parse(isValue), objectReferenceHost)
            val isClass = graphNotation
                    .transitiveAttribute(isLocation, NotationConventions.classAttributePath)
                    ?.asString()
                    ?: ClassNames.kotlinAny.get()

            return AttributeMetadata(
                    MapAttributeNotation(persistentMapOf(
                            NotationConventions.isAttributeSegment to ScalarAttributeNotation(isValue))),
                    TypeMetadata(ClassName(isClass), listOf()),
                    null,
                    null)
        }

        return null
    }


    private fun readAttribute(
            attributeNotation: AttributeNotation,
            host: ObjectReferenceHost,
            graphNotation: GraphNotation
    ): AttributeMetadata {
        val inheritanceParent: String? =
                attributeInheritanceParent(attributeNotation)

        val inheritanceParentLocation =
                if (inheritanceParent == null) {
                    null
                }
                else {
                    graphNotation.coalesce.locate(ObjectReference.parse(inheritanceParent), host)
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

        @Suppress("MoveVariableDeclarationIntoWhen")
        val genericsNotation = attributeMap.values[NotationConventions.ofAttributeSegment]

        val genericsNames: List<TypeMetadata> =
                when (genericsNotation) {
                    null ->
                        listOf()

                    is ScalarAttributeNotation -> {
                        val value = genericsNotation.value
                        val reference = ObjectReference.parse(value)
                        val objectLocation = graphNotation.coalesce.locate(reference, host)
                        val genericClassName = ClassName(
                                graphNotation.getString(objectLocation, NotationConventions.classAttributePath))

                        listOf(TypeMetadata(genericClassName, listOf()))
                    }

                    else ->
                        TODO()
                }

        val typeMetadata = TypeMetadata(
                className, genericsNames)

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