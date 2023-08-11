package tech.kzen.lib.common.model.instance

import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectLocationMap


// TODO: add lifecycle methods for postConstruct and preDestroy?
data class GraphInstance(
        val objectInstances: ObjectLocationMap<ObjectInstance>)
{
    //-----------------------------------------------------------------------------------------------------------------
    val keys: Set<ObjectLocation>
        get() = objectInstances.values.keys


    val size: Int
        get() = objectInstances.size


    operator fun contains(objectLocation: ObjectLocation): Boolean {
        return objectLocation in objectInstances
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