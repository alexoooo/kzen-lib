package tech.kzen.lib.platform

import org.khronos.webgl.Uint8Array


@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual object IoUtils {
    actual fun utf8Encode(utf8: String): ByteArray {
        // from kotlinx.serialization String.toUtf8Bytes
        val blck = js("unescape(encodeURIComponent(utf8))") as String
        return blck.toList().map { it.code.toByte() }.toByteArray()
    }


    @Suppress("UNUSED_VARIABLE")
    actual fun utf8Decode(bytes: ByteArray): String {
        // https://stackoverflow.com/a/17192845/1941359
        val uintArray = Uint8Array(bytes.toTypedArray())
        val encodedBytes = js("String.fromCharCode.apply(null, uintArray)")
        val decodedString = js("decodeURIComponent(escape(encodedBytes))")
        return decodedString as String
    }


    @Suppress("UNUSED_VARIABLE")
    actual fun base64Encode(bytes: ByteArray): String {
        // https://stackoverflow.com/a/53221307/1941359
        val uintArray = Uint8Array(bytes.toTypedArray())

        // https://stackoverflow.com/a/49124600/1941359
        val encodedBytes = js("uintArray.reduce(" +
                "function (data, byte) { return data + String.fromCharCode(byte); }," +
                "'')")

        val encodedBase64 = js("btoa(encodedBytes)") as String

        return encodedBase64
    }


    @Suppress("UNUSED_VARIABLE")
    actual fun base64Decode(base64: String): ByteArray {
        val decodedString = js("atob(base64)") as String

        val builder = ByteArray(decodedString.length)
        for (i in decodedString.indices) {
            builder[i] = decodedString[i].code.toByte()
        }
        return builder
    }
}