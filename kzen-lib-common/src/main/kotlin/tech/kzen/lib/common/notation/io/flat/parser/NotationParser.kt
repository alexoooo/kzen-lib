package tech.kzen.lib.common.notation.io.flat.parser

import tech.kzen.lib.common.notation.model.PackageNotation
import tech.kzen.lib.common.notation.model.ParameterNotation


interface NotationParser {
    fun parse(body: ByteArray): PackageNotation

    fun parseParameter(value: String): ParameterNotation

    fun deparse(notation: PackageNotation, previousBody: ByteArray): ByteArray
}
