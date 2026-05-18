package tech.kzen.lib.common.model.structure.metadata.tag

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue


class ObjectTagSetTest {
    private val a = ObjectTag("a")
    private val b = ObjectTag("b")
    private val c = ObjectTag("c")


    @Test
    fun emptyIsEmpty() {
        assertTrue(ObjectTagSet.empty.isEmpty())
        assertFalse(ObjectTagSet.empty.contains(a))
    }


    @Test
    fun ofVarargContainsAll() {
        val tags = ObjectTagSet.of(a, b)
        assertFalse(tags.isEmpty())
        assertTrue(tags.contains(a))
        assertTrue(tags.contains(b))
        assertFalse(tags.contains(c))
    }


    @Test
    fun unionIsOrderIndependent() {
        val left = ObjectTagSet.of(a, b)
        val right = ObjectTagSet.of(b, c)

        val merged = left.union(right)

        assertTrue(merged.contains(a))
        assertTrue(merged.contains(b))
        assertTrue(merged.contains(c))
        assertEquals(3, merged.values.size)
        assertEquals(merged, right.union(left))
    }


    @Test
    fun unionWithEmptyShortCircuits() {
        val tags = ObjectTagSet.of(a)
        assertEquals(tags, tags.union(ObjectTagSet.empty))
        assertEquals(tags, ObjectTagSet.empty.union(tags))
    }


    @Test
    fun digestIsOrderIndependent() {
        val ab = ObjectTagSet.of(a, b)
        val ba = ObjectTagSet.of(b, a)
        assertEquals(ab.digest(), ba.digest())
    }


    @Test
    fun equalsIsContentBased() {
        assertEquals(ObjectTagSet.of(a, b), ObjectTagSet.of(b, a))
        assertEquals(ObjectTagSet.of(a, a), ObjectTagSet.of(a))
    }
}
