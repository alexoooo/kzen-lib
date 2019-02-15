package tech.kzen.lib.common.util

import tech.kzen.lib.platform.IoUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals


class DigestTest {
    @Test
    fun emptyDigest() {
        assertNotEquals(
                Digest.Streaming().addInt(0).digest(),
                Digest.Streaming().digest()
        )
    }

    @Test
    fun zeroIsDistinctFromEmpty() {
        assertNotEquals(
                Digest.zero,
                Digest.Streaming().digest()
        )
    }


    @Test
    fun zeroIsDistinctFromZeroDigest() {
        assertNotEquals(
                Digest.zero,
                Digest.Streaming().addInt(0).digest()
        )
    }


    @Test
    fun digestSimpleValue() {
        assertEquals(
                Digest(-1589863858, 1740563592, 714421289, -1869687948),
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
            Digest.ofXoShiRo256StarStar(IoUtils.utf8Encode(value))
}
