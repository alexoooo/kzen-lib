package tech.kzen.lib.server.objects.ast


@Suppress("unused")
class DivideOperation(
        private val dividend: DoubleExpression,
        private val divisor: DoubleExpression
): DoubleExpression {
    override fun evaluate(): Double {
        return dividend.evaluate() / divisor.evaluate()
    }
}