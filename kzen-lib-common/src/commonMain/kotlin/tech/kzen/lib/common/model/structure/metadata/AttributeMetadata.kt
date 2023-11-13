package tech.kzen.lib.common.model.structure.metadata

import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.Digestible


data class AttributeMetadata(
    val attributeMetadataNotation: MapAttributeNotation,
    val type: TypeMetadata?,
    val definerReference: ObjectReference?,
    val creatorReference: ObjectReference?
):
    Digestible
{
    override fun digest(sink: Digest.Sink) {
        sink.addDigestible(attributeMetadataNotation)
        sink.addDigestibleNullable(type)
        sink.addDigestibleNullable(definerReference)
        sink.addDigestibleNullable(creatorReference)
    }
}
