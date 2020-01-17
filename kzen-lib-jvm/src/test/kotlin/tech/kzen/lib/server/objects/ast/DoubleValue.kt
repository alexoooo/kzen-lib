package tech.kzen.lib.server.objects.ast


@Suppress("unused")
class DoubleValue(
        private val value: Double
): DoubleExpression {
    override fun evaluate(): Double {
        return value
    }
}