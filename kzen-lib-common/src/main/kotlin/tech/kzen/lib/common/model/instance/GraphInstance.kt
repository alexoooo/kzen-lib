package tech.kzen.lib.common.model.instance

import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.locate.ObjectLocationMap


data class GraphInstance(
        val objects: ObjectLocationMap<ObjectInstance>)
{
//    fun names(): Set<ObjectName> =
//            objects.names()


    val keys: Set<ObjectLocation>
        get() = objects.values.keys


    val size: Int
        get() = objects.size


    fun containsKey(objectLocation: ObjectLocation): Boolean {
        return objects.containsKey(objectLocation)
    }


    operator fun get(objectLocation: ObjectLocation): ObjectInstance? {
        return objects[objectLocation]
    }


//    fun find(name: ObjectName): Any? {
//        return objects.find(name)
//    }


    fun put(objectLocation: ObjectLocation, instance: ObjectInstance): GraphInstance {
        return GraphInstance(objects.put(objectLocation, instance))
    }
}