package tech.kzen.lib.common.model.structure.metadata

import tech.kzen.lib.common.model.attribute.AttributeNameMap
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible


data class ObjectMetadata(
    val attributes: AttributeNameMap<AttributeMetadata>
):
    Digestible
{
    override fun digest(sink: Digest.Sink) {
        sink.addDigestibleOrderedMap(attributes.map)
    }
}