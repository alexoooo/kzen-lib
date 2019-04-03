package tech.kzen.lib.common.context

import tech.kzen.lib.common.model.locate.ObjectLocationMap


data class GraphInstance(
        val objects: ObjectLocationMap<Any>)
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