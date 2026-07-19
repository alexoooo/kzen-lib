package tech.kzen.lib.common.model

import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectLocationMap
import tech.kzen.lib.common.model.location.ObjectLocationSet
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.location.ObjectReferenceHost
import tech.kzen.lib.platform.collect.persistentMapOf
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue


/**
 * A failed lookup names the near misses (same object name, different document or nesting) instead of dumping
 *  every document path in the graph.
 */
class LocateErrorMessageTest {
    //-----------------------------------------------------------------------------------------------------------------
    private fun locator(vararg locations: String): ObjectLocationSet.Locator {
        val builder = ObjectLocationSet.Locator()
        builder.addAll(locations.map { ObjectLocation.parse(it) })
        return builder
    }


    private fun message(block: () -> Unit): String {
        val error = assertFailsWith<IllegalArgumentException> { block() }
        return error.message ?: ""
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun sameNameInOtherDocumentsIsListed() {
        val locator = locator(
            "test/a.yaml#Foo",
            "test/b.yaml#Foo",
            "test/unrelated.yaml#Bar")

        val message = message {
            locator.locate(ObjectReference.parse("test/missing.yaml#Foo"))
        }

        assertTrue("test/a.yaml#Foo" in message, message)
        assertTrue("test/b.yaml#Foo" in message, message)
        assertTrue("unrelated.yaml" !in message, message)
    }


    @Test
    fun sameDocumentDifferentNestingIsListedFirst() {
        val locator = locator(
            "test/other.yaml#main.things/Foo",
            "test/a.yaml#main.steps/Foo")

        val message = message {
            locator.locate(
                ObjectReference.parse("Foo"),
                ObjectReferenceHost.ofLocation(ObjectLocation.parse("test/a.yaml#main")))
        }

        val nested = message.indexOf("test/a.yaml#main.steps/Foo")
        val elsewhere = message.indexOf("test/other.yaml#main.things/Foo")

        assertTrue(nested != -1, message)
        assertTrue(elsewhere != -1, message)
        assertTrue(nested < elsewhere, message)
    }


    @Test
    fun unknownNameReportsTheTotalCount() {
        val locator = locator(
            "test/a.yaml#Foo",
            "test/b.yaml#Bar")

        val message = message {
            locator.locate(ObjectReference.parse("Baz"))
        }

        assertTrue("no object named" in message, message)
        assertTrue("Baz" in message, message)
        assertTrue("2 objects" in message, message)
    }


    @Test
    fun objectLocationMapReportsTheSameWay() {
        val map = ObjectLocationMap(persistentMapOf(
            ObjectLocation.parse("test/a.yaml#Foo") to "foo"))

        val message = message {
            map.locate(ObjectReference.parse("test/missing.yaml#Foo"))
        }

        assertTrue("Missing: " in message, message)
        assertTrue("test/a.yaml#Foo" in message, message)
    }
}
