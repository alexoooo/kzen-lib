package tech.kzen.lib.common.platform.collect

import tech.kzen.lib.platform.collect.PersistentList
import kotlin.test.Test
import kotlin.test.assertEquals


class PersistentListTest {
    @Test
    fun initialEmptyShouldHaveSizeZero() {
        assertEquals(PersistentList<String>().size, 0)
    }


    @Test
    fun singleOneElementShouldHaveSizeOne() {
        val empty = PersistentList<String>()
        val single = empty.add("foo")
        assertEquals(single.size, 1)
        assertEquals("foo", single[0])
    }
}