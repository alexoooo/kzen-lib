package tech.kzen.lib.common.metadata.read

import tech.kzen.lib.common.metadata.model.GraphMetadata
import tech.kzen.lib.common.metadata.model.ObjectMetadata
import tech.kzen.lib.common.metadata.model.AttributeMetadata
import tech.kzen.lib.common.metadata.model.TypeMetadata
import tech.kzen.lib.common.notation.NotationConventions
import tech.kzen.lib.common.notation.model.*
import tech.kzen.lib.common.api.model.*


class NotationMetadataReader(
//        private val mirrorMetadataReader: MirrorMetadataReader
) {
    fun read(notationTree: NotationTree): GraphMetadata {
        val builder = mutableMapOf<ObjectLocation, ObjectMetadata>()

        for (objectLocation in notationTree.objectLocations) {
            val objectMetadata = readObject(objectLocation, notationTree)

            builder[objectLocation] = objectMetadata
        }

        return GraphMetadata(ObjectMap(builder))
    }


    private fun readObject(
            objectLocation: ObjectLocation,
            projectNotation: NotationTree
    ): ObjectMetadata {
        val metaParameter =
                projectNotation.transitiveParameter(objectLocation, NotationConventions.metaAttribute)
                ?: return ObjectMetadata(mapOf())

        val metaMapParameter = metaParameter as MapAttributeNotation

        val parameters = mutableMapOf<AttributeName, AttributeMetadata>()

        for (e in metaMapParameter.values) {
            val parameterMetadata = readParameter(objectLocation, e.value, projectNotation)

            parameters[AttributeName(e.key.asString())] = parameterMetadata
        }

        return ObjectMetadata(
//                className,
                parameters)
    }


    private fun readParameter(
            host: ObjectLocation,
            attributeNotation: AttributeNotation,
            notationTree: NotationTree
    ): AttributeMetadata {
        val inheritanceParent: String? =
                parameterInheritanceParent(attributeNotation)

        val inheritanceParentLocation =
                if (inheritanceParent == null) {
                    null
                }
                else {
                    notationTree.coalesce.locate(host, ObjectReference.parse(inheritanceParent))
                }

        val parameterMap = attributeNotation as? MapAttributeNotation

        val classNotation = metadataParameter(
                NotationConventions.classAttribute, inheritanceParentLocation, parameterMap, notationTree)
        val className = (classNotation as? ScalarAttributeNotation)?.value as? String
        checkNotNull(className) { "Unknown class: $host - $attributeNotation" }

//        val valueNotation = metadataParameter(
//                "class", inheritanceParent, parameterMap, projectMetadata)

        val definerNotation = metadataParameter(
                NotationConventions.byAttribute, inheritanceParentLocation, parameterMap, notationTree)
        val definerName = (definerNotation as? ScalarAttributeNotation)?.value as? String

        val creatorNotation = metadataParameter(
                NotationConventions.usingAttribute, inheritanceParentLocation, parameterMap, notationTree)
        val creatorName = (creatorNotation as? ScalarAttributeNotation)?.value as? String
//        check(creatorName != null) { "Unknown creator class: $parameterNotation" }

        val genericsNotation = metadataParameter(
                NotationConventions.ofAttribute, inheritanceParentLocation, parameterMap, notationTree)

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


    private fun parameterInheritanceParent(
            parameterNotation: AttributeNotation
    ): String? {
        return when (parameterNotation) {
            is ScalarAttributeNotation -> {
                check(parameterNotation.value is String) {
                    "Inline '${NotationConventions.isKey}' must be String: $parameterNotation"
                }

                parameterNotation.value
            }

            is MapAttributeNotation -> {
                parameterNotation
                        .values[NotationConventions.isSegment]
                        ?.asString()
            }

            else -> null
        }
    }


    private fun metadataParameter(
            notationPath: AttributePath,
            inheritanceParent: ObjectLocation?,
            parameterMap: MapAttributeNotation?,
            projectNotation: NotationTree
    ): AttributeNotation? {
        val paramNotation = parameterMap?.get(notationPath)

        return if (paramNotation == null && inheritanceParent != null) {
            projectNotation.transitiveParameter(
                    inheritanceParent, notationPath)
        }
        else {
            paramNotation
        }

//        val className = (classNotation as? ScalarParameterNotation)?.value
    }
}