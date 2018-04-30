package tech.kzen.lib.common.context


data class ObjectGraph(
        private val objects: Map<String, Any>)
{
    fun names(): Set<String> =
            objects.keys

    fun get(name: String):Any =
            objects[name]!!

    fun find(name: String): Any? =
            objects[name]
}