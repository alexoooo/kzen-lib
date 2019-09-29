package tech.kzen.lib.common.service.parse

import tech.kzen.lib.common.model.structure.notation.AttributeNotation
import tech.kzen.lib.common.model.structure.notation.DocumentObjectNotation
import tech.kzen.lib.common.model.structure.notation.ObjectNotation


interface NotationParser {
    fun parseDocumentObjects(document: String): DocumentObjectNotation

    fun parseObject(value: String): ObjectNotation

    fun parseAttribute(value: String): AttributeNotation


    fun unparseDocument(notation: DocumentObjectNotation, previousDocument: String): String

    fun unparseObject(objectNotation: ObjectNotation): String

    fun unparseAttribute(attributeNotation: AttributeNotation): String
}
