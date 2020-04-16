package tech.kzen.lib.common.model.structure.metadata

import tech.kzen.lib.common.model.attribute.AttributeNameMap


data class ObjectMetadata(
        val attributes: AttributeNameMap<AttributeMetadata>)