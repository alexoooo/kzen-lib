package tech.kzen.lib.common.service.metadata

import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributeNameMap
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectLocationMap
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.location.ObjectReferenceHost
import tech.kzen.lib.common.model.structure.metadata.AttributeMetadata
import tech.kzen.lib.common.model.structure.metadata.GraphMetadata
import tech.kzen.lib.common.model.structure.metadata.ObjectMetadata
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.model.structure.notation.*
import tech.kzen.lib.common.service.notation.NotationConventions
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.DigestCache
import tech.kzen.lib.platform.ClassName
import tech.kzen.lib.platform.ClassNames
import tech.kzen.lib.platform.collect.persistentMapOf
import tech.kzen.lib.platform.collect.toPersistentMap


class NotationMetadataReader(
//        private val mirrorMetadataReader: MirrorMetadataReader
) {
    //-----------------------------------------------------------------------------------------------------------------
    private val objectMetadataCache = DigestCache<ObjectMetadata>(1024)

    private var graphMetadataCacheDigest = Digest.empty
    private var graphMetadataCache: GraphMetadata? = null


    //-----------------------------------------------------------------------------------------------------------------
    fun read(graphNotation: GraphNotation): GraphMetadata {
        if (graphMetadataCacheDigest == graphNotation.digest()) {
            return graphMetadataCache!!
        }

        if (objectMetadataCache.size < graphNotation.objectLocations.size) {
            objectMetadataCache.size = (graphNotation.objectLocations.size * 1.2 + 1).toInt()
        }

        val builder = mutableMapOf<ObjectLocation, ObjectMetadata>()

        for (objectLocation in graphNotation.objectLocations) {
            val objectMetadata = readObject(objectLocation, graphNotation)

            builder[objectLocation] = objectMetadata
        }

        val graphMetadata = GraphMetadata(ObjectLocationMap(builder.toPersistentMap()))

        graphMetadataCacheDigest = graphNotation.digest()
        graphMetadataCache = graphMetadata

        return graphMetadata
    }


    private fun readObject(
        objectLocation: ObjectLocation,
        graphNotation: GraphNotation
    ): ObjectMetadata {
//        if (objectLocation.objectPath.name.value == "StringHolderRef") {
//            println("fooo")
//        }

        val objectReferenceHost = ObjectReferenceHost.ofLocation(objectLocation)
        val inheritanceChain = graphNotation.inheritanceChain(objectLocation)
        val objectNotations = graphNotation.coalesce

        val metadataDigest = metadataDigest(
                objectLocation, objectReferenceHost, inheritanceChain, graphNotation, objectNotations)

        val cached = objectMetadataCache.get(metadataDigest)
        if (cached != null) {
            return cached
        }

        val objectMetadata = readObjectImpl(
                objectLocation, objectReferenceHost, inheritanceChain, graphNotation)

        objectMetadataCache.put(metadataDigest, objectMetadata)

        return objectMetadata
    }


    private fun readObjectImpl(
        objectLocation: ObjectLocation,
        objectReferenceHost: ObjectReferenceHost,
        inheritanceChain: List<ObjectLocation>,
        graphNotation: GraphNotation
    ): ObjectMetadata {
        val allAttributes = mutableSetOf<AttributeName>()

        val builder = mutableMapOf<AttributeName, AttributeMetadata>()

        for (superLocation in inheritanceChain) {
            val superNotation = graphNotation.coalesce[superLocation]
                ?: continue

            allAttributes.addAll(superNotation.attributes.values.keys)

            val metaAttribute = superNotation.get(NotationConventions.metaAttributePath)
                as? MapAttributeNotation
                ?: continue

            for (e in metaAttribute.values) {
                if (e.key == NotationConventions.refAttributeSegment) {
                    continue
                }

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


    private fun metadataDigest(
        objectLocation: ObjectLocation,
        objectReferenceHost: ObjectReferenceHost,
        inheritanceChain: List<ObjectLocation>,
        graphNotation: GraphNotation,
        objectNotations: ObjectLocationMap<ObjectNotation>
    ): Digest {
        val metadataDependencies = metadataDependencies(
            objectLocation, objectReferenceHost, inheritanceChain, graphNotation, objectNotations)

        val builder = Digest.UnorderedCombiner()

        for (dependencyObjectLocation in metadataDependencies) {
            val dependencyNotation = objectNotations[dependencyObjectLocation]
                ?: continue

            if (dependencyObjectLocation == objectLocation) {
                // NB: factor in inferred metadata
                builder.add(dependencyNotation.digest())
            }
            else {
                val dependencyMetaAttribute = dependencyNotation
                    .get(NotationConventions.metaAttributePath)
                    as? MapAttributeNotation
                    ?: continue

                builder.add(dependencyMetaAttribute.digest())
            }
        }

        return builder.combine()
    }


    private fun metadataDependencies(
        objectLocation: ObjectLocation,
        objectReferenceHost: ObjectReferenceHost,
        inheritanceChain: List<ObjectLocation>,
        graphNotation: GraphNotation,
        objectNotations: ObjectLocationMap<ObjectNotation>
    ): Set<ObjectLocation> {
        val builder = mutableSetOf<ObjectLocation>()

        builder.add(objectLocation)

        for (superLocation in inheritanceChain) {
            val superNotation = objectNotations[superLocation]
                ?: continue

            val metaAttribute = superNotation
                .get(NotationConventions.metaAttributePath)
                as? MapAttributeNotation
                ?: continue

            builder.add(superLocation)

            for (e in metaAttribute.values) {
                if (e.key == NotationConventions.refAttributeSegment) {
                    continue
                }

                val inheritanceParent: String =
                    attributeInheritanceParent(e.value)
                    ?: continue

                val inheritanceParentLocation = objectNotations
                    .locate(ObjectReference.parse(inheritanceParent), objectReferenceHost)

                val attributeInheritanceChain = graphNotation
                    .inheritanceChain(inheritanceParentLocation)

                builder.addAll(attributeInheritanceChain)
            }
        }

        return builder
    }


    private fun inferMetadata(
        objectLocation: ObjectLocation,
        attributeName: AttributeName,
        objectReferenceHost: ObjectReferenceHost,
        graphNotation: GraphNotation
    ): AttributeMetadata? {
        val attributeNotation = graphNotation
            .firstAttribute(objectLocation, AttributePath.ofName(attributeName))

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
                    .firstAttribute(isLocation, NotationConventions.classAttributePath)
                    ?.asString()
                    ?: ClassNames.kotlinAny.get()

            // TODO: add support for Nominal definer if constructor argument is ObjectLocation

            return AttributeMetadata(
                MapAttributeNotation(persistentMapOf(
                    NotationConventions.isAttributeSegment to ScalarAttributeNotation(isValue))),
                TypeMetadata(ClassName(isClass), listOf(), false),
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

        val directAttributeMap = attributeNotation as? MapAttributeNotation
            ?: MapAttributeNotation.empty

        val refAttributeMap = inheritanceParentLocation
            ?.let { resolveMetadataRef(it, graphNotation) }
            ?: MapAttributeNotation.empty

        val mergedAttributeMap = refAttributeMap.values.putAll(directAttributeMap.values)
        val augmentedMergedAttributeMap =
            if (inheritanceParent != null) {
                mergedAttributeMap.put(
                    NotationConventions.isAttributeSegment, ScalarAttributeNotation(inheritanceParent))
            }
            else {
                mergedAttributeMap
            }
        val mergedAttributeNotation = MapAttributeNotation(augmentedMergedAttributeMap)

//        val classNotation = metadataAttribute(
//            NotationConventions.classAttributePath, inheritanceParentLocation, attributeMap, graphNotation)
//        val className = ((classNotation as? ScalarAttributeNotation)?.value)
//            ?.let { ClassName(it) }
//            ?: throw IllegalArgumentException("Unknown class: $host - $attributeNotation")

        val definerReference = mergedAttributeNotation
            .values[NotationConventions.definerAttributeSegment]
            ?.asString()

        val creatorReference = mergedAttributeNotation
            .values[NotationConventions.creatorAttributeSegment]
            ?.asString()

//        val nullable = attributeMap
//            .values[NotationConventions.nullableAttributeSegment]
//            ?.asBoolean()
//            ?: false
//
//
//        val genericsNotation = attributeMap.values[NotationConventions.ofAttributeSegment]
//
//        val generics: List<TypeMetadata> =
//            if (genericsNotation == null) {
//                listOf()
//            }
//            else {
//                readGenerics(genericsNotation, host, graphNotation)
//            }
//
//        val typeMetadata = TypeMetadata(
//            className, generics, nullable)

        val typeMetadata = readAttributeType(mergedAttributeNotation, host, graphNotation)

        return AttributeMetadata(
            mergedAttributeNotation,
            typeMetadata,
            definerReference?.let { ObjectReference.parse(it) },
            creatorReference?.let { ObjectReference.parse(it) })
    }


    private fun readAttributeType(
        typeNotation: AttributeNotation,
        host: ObjectReferenceHost,
        graphNotation: GraphNotation
    ): TypeMetadata {
        return when (typeNotation) {
            is ScalarAttributeNotation -> {
                val value = typeNotation.value
                val reference = ObjectReference.parse(value)
                val objectLocation = graphNotation.coalesce.locate(reference, host)
                val genericClassName = ClassName(
                    graphNotation.getString(objectLocation, NotationConventions.classAttributePath)
                )

                TypeMetadata(genericClassName, listOf(), false)
            }

            is MapAttributeNotation -> {
                val inheritanceParent: String = attributeInheritanceParent(typeNotation)
                    ?: throw IllegalArgumentException("Unknown type: $host - $typeNotation")

                val inheritanceParentLocation =
                    graphNotation.coalesce.locate(ObjectReference.parse(inheritanceParent), host)

                val classNotation = metadataAttribute(
                    NotationConventions.classAttributePath, inheritanceParentLocation, typeNotation, graphNotation)
                val className = ((classNotation as? ScalarAttributeNotation)?.value)
                    ?.let { ClassName(it) }
                    ?: throw IllegalArgumentException("Unknown class: $host - $typeNotation")

                val nullable = typeNotation
                    .values[NotationConventions.nullableAttributeSegment]
                    ?.asBoolean()
                    ?: false

                val nestedGenericsNotation = typeNotation.values[NotationConventions.ofAttributeSegment]
                val nestedGenerics: List<TypeMetadata> =
                    if (nestedGenericsNotation == null) {
                        listOf()
                    }
                    else {
                        readAttributeTypeGenerics(nestedGenericsNotation, host, graphNotation)
                    }

                TypeMetadata(className, nestedGenerics, nullable)
            }

            else ->
                throw IllegalArgumentException("Single type expected: $host - $typeNotation")
        }
    }


    private fun readAttributeTypeGenerics(
        genericsNotation: AttributeNotation,
        host: ObjectReferenceHost,
        graphNotation: GraphNotation
    ): List<TypeMetadata> {
        return when (genericsNotation) {
            is ScalarAttributeNotation -> {
                val single = readAttributeType(genericsNotation, host, graphNotation)
                listOf(single)
            }

            is MapAttributeNotation -> {
                val single = readAttributeType(genericsNotation, host, graphNotation)
                listOf(single)
            }

            is ListAttributeNotation -> {
                val builder = mutableListOf<TypeMetadata>()
                for (parameter in genericsNotation.values) {
                    val nested = readAttributeType(parameter, host, graphNotation)
                    builder.add(nested)
                }
                return builder
            }
        }
    }


    private fun resolveMetadataRef(
        objectLocation: ObjectLocation,
        graphNotation: GraphNotation
    ): MapAttributeNotation {
        return graphNotation.firstAttribute(objectLocation, NotationConventions.refAttributePath)
                as? MapAttributeNotation
                ?: MapAttributeNotation.empty
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
        val attributeNotation = attributeMap.get(attributePath.toNesting())

        return if (attributeNotation == null && inheritanceParent != null) {
            projectNotation.firstAttribute(
                inheritanceParent, attributePath)
        }
        else {
            attributeNotation
        }
    }
}