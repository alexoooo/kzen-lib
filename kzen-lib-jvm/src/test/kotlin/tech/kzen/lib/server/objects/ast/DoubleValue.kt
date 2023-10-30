package tech.kzen.lib.server.objects.ast

import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.server.objects.ast.suffix.DoubleValueSuffix


@Reflect
class DoubleValue(
    private val value: Double
): DoubleExpression {
    init {
        // NB: used to add an import that starts with this class name to test codegen
        DoubleValueSuffix
    }

    override fun evaluate(): Double {
        return value
    }
}