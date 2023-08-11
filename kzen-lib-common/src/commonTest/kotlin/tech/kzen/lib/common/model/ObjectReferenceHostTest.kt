package tech.kzen.lib.common.model

import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectReferenceHost
import tech.kzen.lib.common.model.obj.ObjectPath
import kotlin.test.Test
import kotlin.test.assertEquals


class ObjectReferenceHostTest {
    @Test
    fun stringFormat() {
        val instance = ObjectReferenceHost(
            DocumentPath.parse("auto-js/main.yaml"),
            ObjectPath.parse("root"),
            null)
        assertEquals("auto-js/main.yaml#root", instance.toString())
    }
}