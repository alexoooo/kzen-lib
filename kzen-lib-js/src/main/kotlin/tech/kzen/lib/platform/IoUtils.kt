package tech.kzen.lib.platform

import org.khronos.webgl.Uint8Array


actual object IoUtils {
    @Suppress("UNUSED_VARIABLE")
    actual fun utf8ToString(bytes: ByteArray): String {
        // https://stackoverflow.com/a/17192845/1941359
        val uintArray = Uint8Array(bytes.toTypedArray())
        val encodedString = js("String.fromCharCode.apply(null, uintArray)")
        val decodedString = js("decodeURIComponent(escape(encodedString))")
        return decodedString as String
    }


    actual fun stringToUtf8(utf8: String): ByteArray {
        // from kotlinx.serialization String.toUtf8Bytes
        val blck = js("unescape(encodeURIComponent(utf8))")
        return (blck as String).toList().map { it.toByte() }.toByteArray()
    }
}