package tech.kzen.lib.common.metadata.read

import tech.kzen.lib.common.metadata.model.ObjectMetadata
import tech.kzen.lib.common.metadata.model.ParameterMetadata
import tech.kzen.lib.platform.Mirror


class MirrorMetadataReader {
    private object Constants {
        val emptyParameterMetadata = ParameterMetadata(
                null, null, null, null)
    }

    fun read(className: String): ObjectMetadata {
        val parameterNames = Mirror.constructorArgumentNames(className)

        val parameters = mutableMapOf<String, ParameterMetadata>()

        for (parameterName in parameterNames) {
            parameters[parameterName] = Constants.emptyParameterMetadata
        }

        return ObjectMetadata(/*className,*/ parameters)
    }
}