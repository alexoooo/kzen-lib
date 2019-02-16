package tech.kzen.lib.common.structure.metadata.model

import tech.kzen.lib.common.api.model.AttributeName


data class ObjectMetadata(
        val attributes: Map<AttributeName, AttributeMetadata>)