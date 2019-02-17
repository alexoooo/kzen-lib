package tech.kzen.lib.platform


actual data class ClassName actual constructor(
        private val jvmClassName: String
) {
    actual fun get(): String {
        return jvmClassName
    }
}