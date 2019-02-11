package tech.kzen.lib.platform

import org.khronos.webgl.Uint8Array


actual object IoUtils {
//    private const val asciiDigits = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"


    actual fun utf8Encode(utf8: String): ByteArray {
        // from kotlinx.serialization String.toUtf8Bytes
        val blck = js("unescape(encodeURIComponent(utf8))") as String
        return blck.toList().map { it.toByte() }.toByteArray()
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
//        println("^^^^ uintArray: $uintArray")

        val encodedBytes = js("String.fromCharCode.apply(null, uintArray)")

        val encodedBase64 = js("btoa(encodedBytes)") as String

        return encodedBase64

//        val builder = StringBuilder()
//        for (i in 0 until encodedBase64.length) {
//            val digit = encodedBase64[i].toInt()
//            println("^^^^^ digit: $digit - ${encodedBase64[i]}")
//
//            builder.append(asciiDigits[digit])
//        }
//        return builder.toString()
    }


    @Suppress("UNUSED_VARIABLE")
    actual fun base64Decode(base64: String): ByteArray {
        val decodedString = js("atob(base64)") as String

        val builder = ByteArray(decodedString.length)
        for (i in 0 until decodedString.length) {
            builder[i] = decodedString[i].toByte()
//            println("- $i - ${decodedString[i].toByte()}")
        }

        return builder
//        val decodedArray = js("Uint8Array.from(decodedString, function (c) { c.charCodeAt(0); })") as Uint8Array
//        println("^^^^ decodedArray: $decodedArray")
//
//        val decodedBytes = ByteArray(decodedArray.length) { decodedArray[it] }
//
//        // Uint8Array.from(atob(s), c => c.charCodeAt(0))
//
//        println("^^^^ encodedByteArray: $decodedBytes")
//        return decodedBytes

//        val encodedByteArray = js("decodeURIComponent(escape(atob(base64)))") as String
//        println("^^^^ encodedByteArray: $encodedByteArray")
//
//        return encodedByteArray.toList().map { it.toByte() }.toByteArray()
    }
}