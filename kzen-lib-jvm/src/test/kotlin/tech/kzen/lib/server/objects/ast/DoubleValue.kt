package tech.kzen.lib.server.objects.ast

import tech.kzen.lib.common.reflect.Reflect


@Reflect
class DoubleValue(
        private val value: Double
): DoubleExpression {
    override fun evaluate(): Double {
        return value
    }
}