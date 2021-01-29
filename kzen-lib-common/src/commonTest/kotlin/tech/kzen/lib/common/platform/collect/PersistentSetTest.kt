package tech.kzen.lib.common.platform.collect

import tech.kzen.lib.platform.collect.PersistentSet
import tech.kzen.lib.platform.collect.persistentSetOf
import kotlin.test.Test
import kotlin.test.assertEquals


class PersistentSetTest {
    @Test
    fun initialEmptyShouldHaveSizeZero() {
        assertEquals(0, PersistentSet<String>().size)
        assertEquals(0, persistentSetOf<String>().size)
    }


    @Test
    fun singleElementShouldHaveSizeOne() {
        val empty = PersistentSet<String>()
        val single = empty.add("foo")
        assertEquals(1, single.size)
        assertEquals("foo", single.single())
    }


    @Test
    fun removeItem() {
        val empty = persistentSetOf<String>()
        val twoElement = empty.add("foo").add("bar")
        val removed = twoElement.remove("foo")
        assertEquals(1, removed.size)
        assertEquals("bar", removed.single())
    }
}