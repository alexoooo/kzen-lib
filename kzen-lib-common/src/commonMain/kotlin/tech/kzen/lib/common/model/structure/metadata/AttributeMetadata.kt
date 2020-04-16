package tech.kzen.lib.common.model.structure.metadata

import tech.kzen.lib.common.model.locate.ObjectReference
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation


data class AttributeMetadata(
        val attributeMetadataNotation: MapAttributeNotation,
        val type: TypeMetadata?,
        val definerReference: ObjectReference?,
        val creatorReference: ObjectReference?
)
