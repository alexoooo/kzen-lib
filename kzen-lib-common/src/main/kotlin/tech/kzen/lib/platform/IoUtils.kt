package tech.kzen.lib.platform


expect object IoUtils {
    fun utf8ToString(bytes: ByteArray): String
//    fun utf8ToString(bytes: ByteArray): String {
//        // import kotlinx.serialization.stringFromUtf8Bytes
//        // val document = stringFromUtf8Bytes(body)
//
//        // https://stackoverflow.com/a/49468129/1941359
//        return bytes.joinToString("") {"${it.toChar()}"}
//    }

    fun stringToUtf8(utf8: String): ByteArray
//    fun stringToUtf8(utf8: String): ByteArray {
//        val bytes = ByteArray(utf8.length)
//        var next = 0
//
//        for (i in 0 until utf8.length) {
//            val asChar = utf8[i]
//            val asByte = asChar.toByte()
//
//            if (asByte.toInt() != asChar.toInt()) {
//                // TODO
//                //throw UnsupportedOperationException("Non-ASCII not supported (yet) at $i: $utf8")
//            }
//            else {
//                bytes[next] = asChar.toByte()
//            }
//
//            next++
//        }
//
//        return bytes
//    }
}