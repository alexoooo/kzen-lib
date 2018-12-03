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
            projectMetadata: ProjectNotation
    ): ObjectMetadata {
        val metaParameter =
                projectMetadata.transitiveParameter(objectName, metadataKey)
                        ?: return ObjectMetadata(mapOf())

        val metaMapParameter = metaParameter as MapParameterNotation

        val parameters = mutableMapOf<String, ParameterMetadata>()

        for (e in metaMapParameter.values) {
            val parameterMetadata = readParameter(e.value, projectMetadata)

            parameters[e.key] = parameterMetadata
        }

        return ObjectMetadata(
//                className,
                parameters)
    }


    private fun readParameter(
            parameterNotation: ParameterNotation,
            projectMetadata: ProjectNotation
    ): ParameterMetadata {
        val inheritanceParent: String? =
                parameterInheritanceParent(parameterNotation)

        val parameterMap = parameterNotation as? MapParameterNotation

        val classNotation = metadataParameter(
                "class", inheritanceParent, parameterMap, projectMetadata)
        val className = (classNotation as? ScalarParameterNotation)?.value as? String

//        val valueNotation = metadataParameter(
//                "class", inheritanceParent, parameterMap, projectMetadata)

        val definerNotation = metadataParameter(
                "by", inheritanceParent, parameterMap, projectMetadata)
        val definerName = (definerNotation as? ScalarParameterNotation)?.value as? String

        val creatorNotation = metadataParameter(
                "using", inheritanceParent, parameterMap, projectMetadata)
        val creatorName = (creatorNotation as? ScalarParameterNotation)?.value as? String

        val typeMetadata = TypeMetadata(
                className!!,
                listOf())

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
            projectMetadata: ProjectNotation
    ): ParameterNotation? {
        val paramNotation = parameterMap?.values?.get(parameterName)

        return if (paramNotation == null && inheritanceParent != null) {
            projectMetadata.transitiveParameter(inheritanceParent, parameterName)
        }
        else {
            paramNotation
        }

//        val className = (classNotation as? ScalarParameterNotation)?.value
    }
}