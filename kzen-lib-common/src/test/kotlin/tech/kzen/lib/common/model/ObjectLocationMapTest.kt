package tech.kzen.lib.common.model

import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.locate.ObjectLocationMap
import tech.kzen.lib.common.model.locate.ObjectReference
import kotlin.test.Test
import kotlin.test.assertEquals


class ObjectLocationMapTest {
    @Test
    fun locateByName() {
        val location = ObjectLocation.parse("main/main.yaml#/foo")

        val data = ObjectLocationMap(mapOf(
                location to "foo"
        ))

        val located = data.locate(ObjectReference.parse("foo"))

        assertEquals(location, located)
    }


    @Test
    fun locateByAbsoluteReference() {
        val location = ObjectLocation.parse("main/main.yaml#/foo")

        val data = ObjectLocationMap(mapOf(
                location to "foo"
        ))

        val located = data.locate(location.toReference())

        assertEquals(location, located)
    }
}