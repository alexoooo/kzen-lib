package tech.kzen.lib.common.util

import tech.kzen.lib.platform.IoUtils
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class IoUtilsBase64Test {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun empty() {
        encodeAndDecode(ByteArray(0))
    }


    @Test
    fun simpleString() {
        encodeAndDecode(byteArrayOf('f'.toByte(), 'o'.toByte(), 'o'.toByte()))
    }


    @Test
    fun zeroByte() {
        encodeAndDecode(byteArrayOf(0))
    }


    @Test
    fun unicodeString() {
        encodeAndDecode(byteArrayOf('~'.toByte(), '§'.toByte(), '·'.toByte()))
    }


    @Test
    fun randomString() {
        val random = Random.nextBytes(Random.nextInt(0, 128))
        encodeAndDecode(random)
    }


    @Test
    fun encodeSpecificText() {
        checkEncodeToString("Kotlin is awesome", "S290bGluIGlzIGF3ZXNvbWU=")
    }


    @Test
    fun encodeLargeBlob() {
        val blob = Random.nextBytes(1_000_000)
        encodeAndDecode(blob)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun encodeAndDecode(byteArray: ByteArray) {
        val base64Encoded = IoUtils.base64Encode(byteArray)

        val decodedBack = IoUtils.base64Decode(base64Encoded)
        assertTrue(byteArray contentEquals decodedBack,
                "$byteArray vs $decodedBack")
    }


    private fun checkEncodeToString(input: String, expectedOutput: String) {
        assertEquals(expectedOutput, IoUtils.base64Encode(asciiToByteArray(input)))
    }


    private fun asciiToByteArray(ascii: String): ByteArray {
        return ByteArray(ascii.length) { ascii[it].toByte() }
    }
}
