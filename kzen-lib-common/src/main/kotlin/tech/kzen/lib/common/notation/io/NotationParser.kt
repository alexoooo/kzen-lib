package tech.kzen.lib.common.notation.io

import tech.kzen.lib.common.notation.model.ObjectNotation
import tech.kzen.lib.common.notation.model.BundleNotation
import tech.kzen.lib.common.notation.model.AttributeNotation


interface NotationParser {
    fun parseBundle(body: ByteArray): BundleNotation

    fun parseObject(value: String): ObjectNotation

    fun parseParameter(value: String): AttributeNotation


    fun deparsePackage(notation: BundleNotation, previousBody: ByteArray): ByteArray

    fun deparseObject(objectNotation: ObjectNotation): String

    fun deparseParameter(parameterNotation: AttributeNotation): String
}
