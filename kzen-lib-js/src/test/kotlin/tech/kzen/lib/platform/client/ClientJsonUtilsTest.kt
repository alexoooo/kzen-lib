package tech.kzen.lib.platform.client

import kotlin.js.Json
import kotlin.test.Test
import kotlin.test.assertEquals


class ClientJsonUtilsTest {
    @Test
    fun listOfString() {
        val listJson = """["foo", "bar"]"""

        val parsed = JSON.parse<Array<*>>(listJson)

        val asList = ClientJsonUtils.toList(parsed)

        assertEquals(listOf("foo", "bar"), asList)
    }


    @Test
    fun mapOfStrings() {
        val mapJson = """{"foo": "bar"}"""

        val parsed = JSON.parse<Json>(mapJson)

        val asMap = ClientJsonUtils.toMap(parsed)

        assertEquals(mapOf("foo" to "bar"), asMap)
    }


    @Test
    fun mapOfNull() {
        val nullMapJson = """{"foo": null}"""

        val parsed = JSON.parse<Json>(nullMapJson)

        val asMap = ClientJsonUtils.toMap(parsed)

        assertEquals(mapOf("foo" to null), asMap)
    }
}