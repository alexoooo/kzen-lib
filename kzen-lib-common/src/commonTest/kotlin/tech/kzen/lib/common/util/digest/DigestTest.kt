package tech.kzen.lib.common.util.digest

import tech.kzen.lib.platform.IoUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals


class DigestTest {
    @Test
    fun zeroIsDistinctFromEmpty() {
        assertNotEquals(
            Digest.zero,
            Digest.Builder().digest())
    }


    @Test
    fun zeroIsDistinctFromZeroDigest() {
        assertNotEquals(
            Digest.zero,
            Digest.Builder().addInt(0).digest())
    }


    @Test
    fun intDigestEqualsGoingThroughBuilder() {
        assertEquals(
            Digest.ofInt(42),
            Digest.Builder().addInt(42).digest())
    }


    @Test
    fun digestSimpleValue() {
        assertEquals(
            Digest(-117490369, -847733214, 1215846144, -644985839),
            digest("foo"))
    }


    @Test
    fun encodeSimpleValue() {
        assertEquals(
            "-3g1gm1_-p8eoeu_147glo0_-j73cvf",
            Digest(-117490369, -847733214, 1215846144, -644985839).asString())
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
    fun digestByteArrayWithHighAndNegativeBytes() {
        val bytes = byteArrayOf(0, 1, -1, 127, -128, 42, 7)

        assertEquals(
            Digest.ofBytes(bytes),
            Digest.build { addBytes(bytes) })
    }


    @Test
    fun unorderedCollectionIgnoresOrder() {
        assertEquals(
            Digest.build { addUnorderedCollection(listOf("foo", "bar")) { addUtf8(it) } },
            Digest.build { addUnorderedCollection(listOf("bar", "foo")) { addUtf8(it) } })
    }


    @Test
    fun unorderedCollectionDiffersFromOrdered() {
        val list = listOf("foo", "bar")

        assertNotEquals(
            Digest.build { addCollection(list) { addUtf8(it) } },
            Digest.build { addUnorderedCollection(list) { addUtf8(it) } })
    }


    @Test
    fun unorderedDigestibleVariantsAgree() {
        val foo = Digest.ofUtf8("foo")
        val bar = Digest.ofUtf8("bar")
        val expected = Digest.build { addDigestibleUnorderedList(listOf(foo, bar)) }

        assertEquals(expected, Digest.build { addDigestibleUnorderedList(listOf(bar, foo)) })
        assertEquals(expected, Digest.build { addDigestibleUnorderedSet(setOf(foo, bar)) })
        assertEquals(expected, Digest.build { addUnorderedCollection(listOf(foo, bar)) { addDigestible(it) } })
    }


    @Test
    fun encodeDecode() {
        val digest = digest("foo")
        val encoding = digest.asString()
        val decoded = Digest.parse(encoding)
        assertEquals(digest, decoded)
    }


    @Test
    fun digestStringList() {
        val list = listOf("foo", "bar")

        val indirect = Digest.build {
            addInt(list.size)
            list.forEach { addUtf8(it) }
        }

        val direct = Digest.build {
            addCollection(list) { addUtf8(it) }
        }

        assertEquals(indirect, direct)
    }


    private fun digest(value: String): Digest =
            Digest.ofBytes(IoUtils.utf8Encode(value))
}
