package tech.kzen.lib.common.util.digest

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue


class DigestCacheTest {
    @Test
    fun emptyCache() {
        val cache = DigestCache<String>(1)
        assertEquals(1, cache.size)
        cache.clear()
        assertEquals(1, cache.size)
    }


    @Test
    fun singleElement() {
        val cache = DigestCache<String>(1)
        cache.put(Digest.ofUtf8("foo"), "foo")
        assertEquals("foo", cache.get(Digest.ofUtf8("foo")))
    }


    @Test
    fun clearSingleElement() {
        val cache = DigestCache<String>(1)
        cache.put(Digest.ofUtf8("foo"), "foo")
        cache.clear()
        assertNull(cache.get(Digest.ofUtf8("foo")))
    }


    @Test
    fun twoIntoOne() {
        val cache = DigestCache<String>(1)
        cache.put(Digest.ofUtf8("foo"), "foo")
        cache.put(Digest.ofUtf8("bar"), "bar")
        assertTrue(cache.get(Digest.ofUtf8("foo")) == null)
        assertEquals("bar", cache.get(Digest.ofUtf8("bar")))
    }


    @Test
    fun twoDownToOne() {
        val cache = DigestCache<String>(2)
        cache.put(Digest.ofUtf8("foo"), "foo")
        cache.put(Digest.ofUtf8("bar"), "bar")
        cache.size = 1
        assertTrue(cache.get(Digest.ofUtf8("foo")) == null)
        assertEquals("bar", cache.get(Digest.ofUtf8("bar")))
    }
}