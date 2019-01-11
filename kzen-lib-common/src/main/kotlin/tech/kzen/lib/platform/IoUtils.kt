package tech.kzen.lib.platform


expect object IoUtils {
    fun utf8ToString(bytes: ByteArray): String

    fun stringToUtf8(utf8: String): ByteArray
}