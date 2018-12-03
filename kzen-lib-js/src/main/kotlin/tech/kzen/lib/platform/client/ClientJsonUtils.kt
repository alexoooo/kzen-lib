package tech.kzen.lib.platform.client

import kotlin.js.Json



object ClientJsonUtils {
    fun toList(jsonList: Array<Json>): List<Any> {
        return jsonList.map(this::toMap)
    }


    fun toMap(json: Json): Map<String, Any> {
        val map = mutableMapOf<String, Any>()

        for (key in ClientObjectUtils.getOwnPropertyNames(json)) {
            map[key] = toValue(json[key]!!)
        }

        return map
    }


    private fun toValue(value: Any): Any {
        @Suppress("UNCHECKED_CAST", "UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
        return when (value) {
            is String -> value
            is Number -> value
            is Boolean -> value

            is Array<*> ->
                toList(value as Array<Json>)

            else ->
                toMap(value as Json)
        }
    }
}