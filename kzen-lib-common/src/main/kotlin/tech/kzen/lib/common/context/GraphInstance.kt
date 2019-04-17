package tech.kzen.lib.common.context

import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.locate.ObjectLocationMap


data class GraphInstance(
        val objects: ObjectLocationMap<Any>)
{
//    fun names(): Set<ObjectName> =
//            objects.names()


    fun containsKey(objectLocation: ObjectLocation): Boolean {
        return objects.containsKey(objectLocation)
    }


    operator fun get(objectLocation: ObjectLocation): Any? {
        return objects.get(objectLocation)
    }


//    fun find(name: ObjectName): Any? {
//        return objects.find(name)
//    }


    fun put(objectLocation: ObjectLocation, instance: Any): GraphInstance {
        return GraphInstance(objects.put(objectLocation, instance))
    }
}