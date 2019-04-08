package tech.kzen.lib.common.model

import tech.kzen.lib.common.model.locate.ObjectReference
import tech.kzen.lib.common.model.obj.ObjectName
import kotlin.test.Test
import kotlin.test.assertEquals


class ObjectReferenceTest {
    @Test
    fun parseWeirdName() {
        val weirdName = ObjectName("/")
        val literal = ObjectReference(weirdName, null, null)
        assertEquals("\\/", literal.asString())
        assertEquals(literal, ObjectReference.parse(literal.asString()))
    }
}