package tech.kzen.lib.common.model.structure.metadata

import tech.kzen.lib.common.model.attribute.AttributeNameMap
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.Digestible


data class ObjectMetadata(
    val attributes: AttributeNameMap<AttributeMetadata>
):
    Digestible
{
    override fun digest(sink: Digest.Sink) {
        sink.addDigestibleOrderedMap(attributes.values)
    }
}