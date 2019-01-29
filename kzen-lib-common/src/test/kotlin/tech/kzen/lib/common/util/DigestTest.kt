package tech.kzen.lib.common.util

import tech.kzen.lib.platform.IoUtils
import kotlin.test.Test
import kotlin.test.assertEquals


class DigestTest {
    @Test
    fun digestSimpleValue() {
        assertEquals(
                Digest(557618767, -406919543, 714421289, -1863393420),
                digest("foo"))
    }


    @Test
    fun digestByteArray() {
        val plaintext = "foo"

        val direct = Digest.ofXoShiRo256StarStar(plaintext)

        val streaming = Digest.Streaming()
        streaming.addUtf8(plaintext)
        val indirect = streaming.digest()

        assertEquals(direct, indirect)
    }


    @Test
    fun encodeDecode() {
        val digest = digest("foo")
        val encoding = digest.asString()
        val decoded = Digest.parse(encoding)
        assertEquals(digest, decoded)
    }


    private fun digest(value: String): Digest =
            Digest.ofXoShiRo256StarStar(IoUtils.stringToUtf8(value))
}
