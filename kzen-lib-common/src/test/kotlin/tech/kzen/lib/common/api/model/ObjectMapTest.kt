package tech.kzen.lib.common.api.model

import kotlin.test.Test
import kotlin.test.assertEquals


class ObjectMapTest {
    @Test
    fun locateByName() {
        val location = ObjectLocation.parse("main/main.yaml#/foo")

        val data = ObjectMap(mapOf(
                location to "foo"
        ))

        val located = data.locate(ObjectReference.parse("foo"))

        assertEquals(location, located)
    }


    @Test
    fun locateByAbsoluteReference() {
        val location = ObjectLocation.parse("main/main.yaml#/foo")

        val data = ObjectMap(mapOf(
                location to "foo"
        ))

        val located = data.locate(location.toReference())

        assertEquals(location, located)
    }
}