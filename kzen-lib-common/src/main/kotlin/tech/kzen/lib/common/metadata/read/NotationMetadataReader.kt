package tech.kzen.lib.common.metadata.read

import tech.kzen.lib.common.metadata.model.GraphMetadata
import tech.kzen.lib.common.metadata.model.ObjectMetadata
import tech.kzen.lib.common.metadata.model.ParameterMetadata
import tech.kzen.lib.common.metadata.model.TypeMetadata
import tech.kzen.lib.common.notation.model.*


class NotationMetadataReader(
//        private val mirrorMetadataReader: MirrorMetadataReader
) {
    companion object {
        private const val metadataKey = "meta"
    }


    fun read(projectMetadata: ProjectNotation): GraphMetadata {
        val builder = mutableMapOf<String, ObjectMetadata>()

        for (objectName in projectMetadata.objectNames) {
            val objectMetadata = readObject(objectName, projectMetadata)

            builder[objectName] = objectMetadata
        }

        return GraphMetadata(builder)
    }


    private fun readObject(
            objectName: String,
            projectNotation: ProjectNotation
    ): ObjectMetadata {
        val metaParameter =
                projectNotation.transitiveParameter(objectName, metadataKey)
                        ?: return ObjectMetadata(mapOf())

        val metaMapParameter = metaParameter as MapParameterNotation

        val parameters = mutableMapOf<String, ParameterMetadata>()

        for (e in metaMapParameter.values) {
            val parameterMetadata = readParameter(e.value, projectNotation)

            parameters[e.key] = parameterMetadata
        }

        return ObjectMetadata(
//                className,
                parameters)
    }


    private fun readParameter(
            parameterNotation: ParameterNotation,
            projectNotation: ProjectNotation
    ): ParameterMetadata {
        val inheritanceParent: String? =
                parameterInheritanceParent(parameterNotation)

        val parameterMap = parameterNotation as? MapParameterNotation

        val classNotation = metadataParameter(
                "class", inheritanceParent, parameterMap, projectNotation)
        val className = (classNotation as? ScalarParameterNotation)?.value as? String
//        checkNotNull(className) { "Unknown class: $parameterNotation" }

//        val valueNotation = metadataParameter(
//                "class", inheritanceParent, parameterMap, projectMetadata)

        val definerNotation = metadataParameter(
                "by", inheritanceParent, parameterMap, projectNotation)
        val definerName = (definerNotation as? ScalarParameterNotation)?.value as? String

        val creatorNotation = metadataParameter(
                "using", inheritanceParent, parameterMap, projectNotation)
        val creatorName = (creatorNotation as? ScalarParameterNotation)?.value as? String
//        check(creatorName != null) { "Unknown creator class: $parameterNotation" }

        val genericsNotation = metadataParameter(
                "of", inheritanceParent, parameterMap, projectNotation)

        val genericsNames: List<TypeMetadata> =
                when (genericsNotation) {
                    null ->
                        listOf()

                    is ScalarParameterNotation -> {
                        val value = genericsNotation.value as String
                        val genericClassName = projectNotation.getString(value, "class")

                        listOf(TypeMetadata(genericClassName, listOf()))
                    }

                    else ->
                        TODO()
                }

        val typeMetadata = TypeMetadata(
                className!!,
                genericsNames)

        return ParameterMetadata(
                typeMetadata, /*valueNotation,*/ definerName, creatorName)
    }


    private fun parameterInheritanceParent(
            parameterNotation: ParameterNotation
    ): String? {
        return when (parameterNotation) {
            is ScalarParameterNotation -> {
                check(parameterNotation.value is String) {
                    "Inline '${ParameterConventions.isParameter}' must be String: $parameterNotation"
                }

                parameterNotation.value
            }

            is MapParameterNotation -> {
                parameterNotation
                        .values[ParameterConventions.isParameter]
                        ?.asString()
            }

            else -> null
        }
    }


    private fun metadataParameter(
            parameterName: String,
            inheritanceParent: String?,
            parameterMap: MapParameterNotation?,
            projectNotation: ProjectNotation
    ): ParameterNotation? {
        val paramNotation = parameterMap?.values?.get(parameterName)

        return if (paramNotation == null && inheritanceParent != null) {
            projectNotation.transitiveParameter(inheritanceParent, parameterName)
        }
        else {
            paramNotation
        }

//        val className = (classNotation as? ScalarParameterNotation)?.value
    }
}