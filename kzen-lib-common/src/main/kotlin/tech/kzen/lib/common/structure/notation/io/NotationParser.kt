package tech.kzen.lib.common.structure.notation.io

import tech.kzen.lib.common.model.obj.ObjectPathMap
import tech.kzen.lib.common.structure.notation.model.AttributeNotation
import tech.kzen.lib.common.structure.notation.model.DocumentNotation
import tech.kzen.lib.common.structure.notation.model.ObjectNotation


interface NotationParser {
    // TODO: use String for parsing/de-parsing DocumentNotation?
    fun parseDocumentObjects(body: ByteArray): ObjectPathMap<ObjectNotation>

    fun parseObject(value: String): ObjectNotation

    fun parseAttribute(value: String): AttributeNotation


    fun deparseDocument(notation: DocumentNotation, previousBody: ByteArray): ByteArray

    fun deparseObject(objectNotation: ObjectNotation): String

    fun deparseAttribute(attributeNotation: AttributeNotation): String
}
