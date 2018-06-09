package tech.kzen.lib.common.notation.read.flat.parser

import tech.kzen.lib.common.notation.model.PackageNotation


interface NotationParser {
    fun parse(body: ByteArray): PackageNotation

    fun deparse(notation: PackageNotation, previousBody: ByteArray): ByteArray
}
