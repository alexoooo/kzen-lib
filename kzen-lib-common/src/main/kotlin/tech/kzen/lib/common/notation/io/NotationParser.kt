package tech.kzen.lib.common.notation.io

import tech.kzen.lib.common.notation.model.AttributeNotation
import tech.kzen.lib.common.notation.model.BundleNotation
import tech.kzen.lib.common.notation.model.ObjectNotation


interface NotationParser {
    fun parseBundle(body: ByteArray): BundleNotation

    fun parseObject(value: String): ObjectNotation

    fun parseAttribute(value: String): AttributeNotation


    fun deparseBundle(notation: BundleNotation, previousBody: ByteArray): ByteArray

    fun deparseObject(objectNotation: ObjectNotation): String

    fun deparseAttribute(attributeNotation: AttributeNotation): String
}
