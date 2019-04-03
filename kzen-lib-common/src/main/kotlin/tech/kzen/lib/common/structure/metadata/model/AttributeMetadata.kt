package tech.kzen.lib.common.structure.metadata.model

import tech.kzen.lib.common.model.locate.ObjectReference
import tech.kzen.lib.common.structure.notation.model.MapAttributeNotation


data class AttributeMetadata(
        val attributeMetadataNotation: MapAttributeNotation,
        val type: TypeMetadata?,
        val definerReference: ObjectReference?,
        val creatorReference: ObjectReference?
)
