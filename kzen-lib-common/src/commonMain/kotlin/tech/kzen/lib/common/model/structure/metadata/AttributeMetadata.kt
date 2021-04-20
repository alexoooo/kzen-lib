package tech.kzen.lib.common.model.structure.metadata

import tech.kzen.lib.common.model.locate.ObjectReference
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
    override fun digest(builder: Digest.Builder) {
        builder.addDigestible(attributeMetadataNotation)
        builder.addDigestibleNullable(type)
        builder.addDigestibleNullable(definerReference)
        builder.addDigestibleNullable(creatorReference)
    }
}
