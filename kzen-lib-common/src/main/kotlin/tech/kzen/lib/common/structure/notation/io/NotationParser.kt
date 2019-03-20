package tech.kzen.lib.common.structure.notation.io

import tech.kzen.lib.common.structure.notation.model.AttributeNotation
import tech.kzen.lib.common.structure.notation.model.DocumentNotation
import tech.kzen.lib.common.structure.notation.model.ObjectNotation


interface NotationParser {
    fun parseDocument(body: ByteArray): DocumentNotation

    fun parseObject(value: String): ObjectNotation

    fun parseAttribute(value: String): AttributeNotation


    fun deparseDocument(notation: DocumentNotation, previousBody: ByteArray): ByteArray

    fun deparseObject(objectNotation: ObjectNotation): String

    fun deparseAttribute(attributeNotation: AttributeNotation): String
}
