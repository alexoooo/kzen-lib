package tech.kzen.lib.common.model.structure.metadata

import tech.kzen.lib.common.model.attribute.AttributeNameMap
import tech.kzen.lib.common.model.structure.metadata.tag.ObjectTagSet
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible


data class ObjectMetadata(
    val attributes: AttributeNameMap<AttributeMetadata>,
    val tags: ObjectTagSet
):
    Digestible
{
    override fun digest(sink: Digest.Sink) {
        sink.addDigestibleOrderedMap(attributes.map)
        tags.digest(sink)
    }
}
