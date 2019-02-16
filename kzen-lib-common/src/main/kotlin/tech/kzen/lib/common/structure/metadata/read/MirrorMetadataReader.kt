package tech.kzen.lib.common.structure.metadata.read

import tech.kzen.lib.common.api.model.AttributeName
import tech.kzen.lib.common.structure.metadata.model.AttributeMetadata
import tech.kzen.lib.common.structure.metadata.model.ObjectMetadata
import tech.kzen.lib.platform.ClassName
import tech.kzen.lib.platform.Mirror


class MirrorMetadataReader {
    private object Constants {
        val emptyParameterMetadata = AttributeMetadata(
                null, null, null)
    }

    fun read(className: ClassName): ObjectMetadata {
        val parameterNames = Mirror.constructorArgumentNames(className)

        val parameters = mutableMapOf<AttributeName, AttributeMetadata>()

        for (parameterName in parameterNames) {
            parameters[AttributeName(parameterName)] = Constants.emptyParameterMetadata
        }

        return ObjectMetadata(/*className,*/ parameters)
    }
}