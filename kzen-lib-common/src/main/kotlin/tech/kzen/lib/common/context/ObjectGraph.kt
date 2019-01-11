package tech.kzen.lib.common.context

import tech.kzen.lib.common.api.model.ObjectMap


data class ObjectGraph(
        val objects: ObjectMap<Any>)
{
//    fun names(): Set<ObjectName> =
//            objects.names()
//
//
//    fun get(name: ObjectName): Any {
//        return objects.get(name)
//    }
//
//
//    fun find(name: ObjectName): Any? {
//        return objects.find(name)
//    }
}