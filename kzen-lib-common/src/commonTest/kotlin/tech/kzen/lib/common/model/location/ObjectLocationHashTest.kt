package tech.kzen.lib.common.model.location

import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.document.DocumentName
import tech.kzen.lib.platform.collect.persistentMapOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame


// The location types cache their hash (hot map keys); equal values must still hash equally however they were built
class ObjectLocationHashTest {
    private val nested = "main/main.yaml#main.steps/If.then/Text"


    @Test
    fun separatelyParsedEqualLocationsHashEqually() {
        val first = ObjectLocation.parse(nested)
        val second = ObjectLocation.parse(nested)
        assertNotSame(first, second)

        // Hash the first before the second exists in the cache, then again once both are cached
        val firstHash = first.hashCode()
        assertEquals(firstHash, second.hashCode())
        assertEquals(firstHash, first.hashCode())

        assertEquals(first.documentPath.hashCode(), second.documentPath.hashCode())
        assertEquals(first.documentPath.nesting.hashCode(), second.documentPath.nesting.hashCode())
        assertEquals(first.objectPath.hashCode(), second.objectPath.hashCode())
        assertEquals(first.objectPath.nesting.hashCode(), second.objectPath.nesting.hashCode())
        assertEquals(
            first.objectPath.nesting.segments.first().hashCode(),
            second.objectPath.nesting.segments.first().hashCode())
    }


    @Test
    fun derivedCopiesHashLikeFreshlyBuiltEquals() {
        val original = ObjectLocation.parse(nested)
        original.hashCode()

        val renamed = original.documentPath.withName(DocumentName("other"))
        assertEquals(ObjectLocation.parse("main/other.yaml#main.steps/If.then/Text").documentPath, renamed)
        assertEquals(
            ObjectLocation.parse("main/other.yaml#main.steps/If.then/Text").documentPath.hashCode(),
            renamed.hashCode())

        val nestedAttribute = AttributePath.parse("input.a").nest(AttributeSegment.ofKey("b"))
        assertEquals(AttributePath.parse("input.a.b"), nestedAttribute)
        assertEquals(AttributePath.parse("input.a.b").hashCode(), nestedAttribute.hashCode())
        assertEquals(AttributePath.parse("input.a.b").nesting.hashCode(), nestedAttribute.nesting.hashCode())
    }


    @Test
    fun equalKeyFindsItsEntryInAPersistentMap() {
        val stored = ObjectLocation.parse(nested)
        stored.hashCode()
        val map = persistentMapOf(stored to "text")

        assertEquals("text", map[ObjectLocation.parse(nested)])
    }
}
