package tech.kzen.lib.common.notation.io

import tech.kzen.lib.common.notation.model.ObjectNotation
import tech.kzen.lib.common.notation.model.PackageNotation
import tech.kzen.lib.common.notation.model.ParameterNotation


interface NotationParser {
    fun parsePackage(body: ByteArray): PackageNotation

    fun parseObject(value: String): ObjectNotation

    fun parseParameter(value: String): ParameterNotation


    fun deparsePackage(notation: PackageNotation, previousBody: ByteArray): ByteArray

    fun deparseObject(objectNotation: ObjectNotation): String

    fun deparseParameter(parameterNotation: ParameterNotation): String
}
