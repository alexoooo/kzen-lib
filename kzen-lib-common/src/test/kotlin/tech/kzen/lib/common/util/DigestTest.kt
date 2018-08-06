package tech.kzen.lib.common.util

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
    fun encodeDecode() {
        val digest = digest("foo")
        val encoding = digest.encode()
        val decoded = Digest.decode(encoding)
        assertEquals(digest, decoded)
    }

    private fun digest(value: String): Digest =
            Digest.ofXoShiRo256StarStar(IoUtils.stringToUtf8(value))
}
