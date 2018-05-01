package tech.kzen.lib.common.util

object IoUtils {
    fun utf8ToString(bytes: ByteArray): String {
        // import kotlinx.serialization.stringFromUtf8Bytes
        // val document = stringFromUtf8Bytes(body)

        // https://stackoverflow.com/a/49468129/1941359
        return bytes.joinToString("") {"${it.toChar()}"}
    }
}