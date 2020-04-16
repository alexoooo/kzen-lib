package tech.kzen.lib.common.util

import tech.kzen.lib.platform.IoUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals


class DigestTest {
    @Test
    fun emptyDigest() {
        assertNotEquals(
                Digest.Builder().addInt(0).digest(),
                Digest.Builder().digest()
        )
    }

    @Test
    fun zeroIsDistinctFromEmpty() {
        assertNotEquals(
                Digest.zero,
                Digest.Builder().digest()
        )
    }


    @Test
    fun zeroIsDistinctFromZeroDigest() {
        assertNotEquals(
                Digest.zero,
                Digest.Builder().addInt(0).digest()
        )
    }


    @Test
    fun digestSimpleValue() {
        assertEquals(
                Digest(-1025110241, -1673702306, -104695459, 1543258038),
                digest("foo"))
    }


    @Test
    fun digestByteArray() {
        val plaintext = "foo"

        val direct = Digest.ofUtf8(plaintext)

        val streaming = Digest.Builder()
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
            Digest.ofBytes(IoUtils.utf8Encode(value))
}
