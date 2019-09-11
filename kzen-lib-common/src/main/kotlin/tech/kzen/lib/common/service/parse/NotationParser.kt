package tech.kzen.lib.common.service.parse

import tech.kzen.lib.common.model.obj.ObjectPathMap
import tech.kzen.lib.common.model.structure.notation.AttributeNotation
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.model.structure.notation.ObjectNotation


interface NotationParser {
    fun parseDocumentObjects(document: String): ObjectPathMap<ObjectNotation>

    fun parseObject(value: String): ObjectNotation

    fun parseAttribute(value: String): AttributeNotation


    fun unparseDocument(notation: DocumentNotation, previousDocument: String): String

    fun unparseObject(objectNotation: ObjectNotation): String

    fun unparseAttribute(attributeNotation: AttributeNotation): String
}
