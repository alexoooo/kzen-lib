package tech.kzen.lib.common.model

import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.locate.ObjectLocationMap
import tech.kzen.lib.common.model.locate.ObjectReference
import tech.kzen.lib.platform.collect.persistentMapOf
import kotlin.test.Test
import kotlin.test.assertEquals


class ObjectLocationTest {
    @Test
    fun locateByName() {
        val location = ObjectLocation.parse("main/main.yaml#/foo")

        val data = ObjectLocationMap(persistentMapOf(
                location to "foo"
        ))

        val located = data.locate(ObjectReference.parse("foo"))

        assertEquals(location, located)
    }


    @Test
    fun locateByAbsoluteReference() {
        val location = ObjectLocation.parse("main/main.yaml#/foo")

        val data = ObjectLocationMap(persistentMapOf(
                location to "foo"
        ))

        val located = data.locate(location.toReference())

        assertEquals(location, located)
    }


    @Test
    fun locateWeirdName() {
        val location = ObjectLocation.parse("main/main.yaml#/main.attr/\\")

        val data = ObjectLocationMap(persistentMapOf(
                location to "foo"
        ))

        val located = data.locate(ObjectReference.parse("main.attr/\\"))

        assertEquals(location, located)
    }


    @Test
    fun extractParent() {
        val location = ObjectLocation.parse("main/main.yaml#main.steps/If.then/Text")

        assertEquals(
                ObjectLocation.parse("main/main.yaml#main.steps/If"),
                location.parent()
        )
    }
}