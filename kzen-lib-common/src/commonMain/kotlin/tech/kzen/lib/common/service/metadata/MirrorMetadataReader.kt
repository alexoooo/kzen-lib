package tech.kzen.lib.common.service.metadata

import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributeNameMap
import tech.kzen.lib.common.model.structure.metadata.AttributeMetadata
import tech.kzen.lib.common.model.structure.metadata.ObjectMetadata
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.reflect.GlobalMirror
import tech.kzen.lib.platform.ClassName
import tech.kzen.lib.platform.collect.toPersistentMap


// TODO
class MirrorMetadataReader {
    private object Constants {
        val emptyParameterMetadata = AttributeMetadata(
                MapAttributeNotation.empty,
                null,
                null,
                null)
    }


    fun read(className: ClassName): ObjectMetadata {
        val parameterNames = GlobalMirror.constructorArgumentNames(className)

        val attributes = mutableMapOf<AttributeName, AttributeMetadata>()

        for (parameterName in parameterNames) {
            attributes[AttributeName(parameterName)] = Constants.emptyParameterMetadata
        }

        return ObjectMetadata(AttributeNameMap(attributes.toPersistentMap()))
    }
}