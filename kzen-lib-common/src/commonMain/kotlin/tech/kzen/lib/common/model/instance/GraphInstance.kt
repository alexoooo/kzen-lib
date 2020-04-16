package tech.kzen.lib.common.model.instance

import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.locate.ObjectLocationMap


data class GraphInstance(
        val objectInstances: ObjectLocationMap<ObjectInstance>)
{
    //-----------------------------------------------------------------------------------------------------------------
    val keys: Set<ObjectLocation>
        get() = objectInstances.values.keys


    val size: Int
        get() = objectInstances.size


    fun containsKey(objectLocation: ObjectLocation): Boolean {
        return objectInstances.containsKey(objectLocation)
    }


    operator fun get(objectLocation: ObjectLocation): ObjectInstance? {
        return objectInstances[objectLocation]
    }


//    fun find(name: ObjectName): Any? {
//        return objects.find(name)
//    }


    fun put(objectLocation: ObjectLocation, instance: ObjectInstance): GraphInstance {
        return GraphInstance(objectInstances.put(objectLocation, instance))
    }
}