package tech.kzen.lib.common.structure.metadata.model

import tech.kzen.lib.common.model.attribute.AttributeNameMap


data class ObjectMetadata(
        val attributes: AttributeNameMap<AttributeMetadata>)