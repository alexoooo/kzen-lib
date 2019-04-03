package tech.kzen.lib.common.structure.metadata.read

import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributeNameMap
import tech.kzen.lib.common.structure.metadata.model.AttributeMetadata
import tech.kzen.lib.common.structure.metadata.model.ObjectMetadata
import tech.kzen.lib.common.structure.notation.model.MapAttributeNotation
import tech.kzen.lib.platform.ClassName
import tech.kzen.lib.platform.Mirror


class MirrorMetadataReader {
    private object Constants {
        val emptyParameterMetadata = AttributeMetadata(
                MapAttributeNotation.empty,
                null,
                null,
                null)
    }

    fun read(className: ClassName): ObjectMetadata {
        val parameterNames = Mirror.constructorArgumentNames(className)

        val attributes = mutableMapOf<AttributeName, AttributeMetadata>()

        for (parameterName in parameterNames) {
            attributes[AttributeName(parameterName)] = Constants.emptyParameterMetadata
        }

        return ObjectMetadata(AttributeNameMap(attributes))
    }
}