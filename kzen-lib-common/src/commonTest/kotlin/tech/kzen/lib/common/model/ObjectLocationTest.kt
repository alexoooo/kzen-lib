package tech.kzen.lib.common.model

import tech.kzen.lib.common.model.document.DocumentName
import tech.kzen.lib.common.model.document.DocumentNesting
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.document.DocumentSegment
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.locate.ObjectLocationMap
import tech.kzen.lib.common.model.locate.ObjectReference
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.obj.ObjectNesting
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.platform.collect.persistentListOf
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


    @Test
    fun parseDir() {
        val location = ObjectLocation.parse("main/Visual Target/~main.yaml#main")

        assertEquals(
                ObjectPath(ObjectName("main"), ObjectNesting.root),
                location.objectPath)

        assertEquals(
                DocumentPath(
                        DocumentName("Visual Target"),
                        DocumentNesting(persistentListOf(
                                DocumentSegment("main"))),
                        true),
                location.documentPath)
    }
}