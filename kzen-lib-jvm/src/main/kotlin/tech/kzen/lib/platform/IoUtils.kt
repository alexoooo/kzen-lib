package tech.kzen.lib.platform

import java.nio.charset.StandardCharsets

actual object IoUtils {
    actual fun utf8ToString(bytes: ByteArray): String {
        return String(bytes, StandardCharsets.UTF_8)
    }


    actual fun stringToUtf8(utf8: String): ByteArray {
        return utf8.toByteArray(StandardCharsets.UTF_8)
    }
}