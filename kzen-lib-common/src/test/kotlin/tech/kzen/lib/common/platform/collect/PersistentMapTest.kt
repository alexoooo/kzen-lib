package tech.kzen.lib.common.platform.collect

import tech.kzen.lib.platform.collect.PersistentMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue


class PersistentMapTest {
    @Test
    fun initialEmptyShouldHaveSizeZero() {
        assertEquals(0, PersistentMap<Any, Any>().size)
    }


    @Test
    fun singleShouldHaveSizeOne() {
        val empty = PersistentMap<String, Int>()
        val single = empty.put("foo", 1)
        assertNotEquals(empty, single)
        assertEquals(single.size, 1)
        assertTrue("foo" in single)
        assertEquals(1, single["foo"])
    }


    @Test
    fun putShouldOverride() {
        val empty = PersistentMap<String, Int>()
        val single = empty.put("foo", 1)
        val override = single.put("foo", 2)
        assertEquals(override.size, 1)
        assertTrue("foo" in override)
        assertEquals(2, override["foo"])
    }


    @Test
    fun removeShouldDelete() {
        val empty = PersistentMap<String, Int>()
        val single = empty.put("foo", 1)
        val removed = single.remove("foo")
        assertEquals(removed, empty)
    }


    @Test
    fun insertionOrderIsPreserved() {
        var builder = PersistentMap<Int, Int>()
        for (i in 0 .. 100) {
            builder = builder.put(i, i)
        }

        for ((index, entry) in builder.entries.withIndex()) {
            assertEquals(index, entry.key, "Entries order")
        }

        builder = builder.put(0, -1)
        assertEquals(0, builder.keys.first(), "Override key order")
        assertEquals(-1, builder.values.first(), "Override value order")
    }
}