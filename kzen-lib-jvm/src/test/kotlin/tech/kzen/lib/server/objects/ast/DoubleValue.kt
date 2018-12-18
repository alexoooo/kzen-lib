package tech.kzen.lib.server.objects.ast


class DoubleValue(
        private val value: Double
): DoubleExpression {
    override fun evaluate(): Double {
        return value
    }
}