package tech.kzen.lib.common.util


object IoUtils {
    fun utf8ToString(bytes: ByteArray): String {
        // import kotlinx.serialization.stringFromUtf8Bytes
        // val document = stringFromUtf8Bytes(body)

        // https://stackoverflow.com/a/49468129/1941359
        return bytes.joinToString("") {"${it.toChar()}"}
    }

    fun stringToUtf8(utf8: String): ByteArray {
        val bytes = ByteArray(utf8.length)

        for (i in 0 until utf8.length) {
            val asChar = utf8[i]
            val asByte = asChar.toByte()

            if (asByte.toInt() != asChar.toInt()) {
                throw UnsupportedOperationException("Non-ASCII not supported (yet): $utf8")
            }

            bytes[i] = asChar.toByte()
        }

        return bytes
    }
}