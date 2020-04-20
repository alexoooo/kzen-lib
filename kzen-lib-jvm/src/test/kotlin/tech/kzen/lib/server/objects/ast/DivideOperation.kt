package tech.kzen.lib.server.objects.ast

import tech.kzen.lib.common.reflect.Reflect


@Reflect
class DivideOperation(
        private val dividend: DoubleExpression,
        private val divisor: DoubleExpression
): DoubleExpression {
    override fun evaluate(): Double {
        return dividend.evaluate() / divisor.evaluate()
    }
}